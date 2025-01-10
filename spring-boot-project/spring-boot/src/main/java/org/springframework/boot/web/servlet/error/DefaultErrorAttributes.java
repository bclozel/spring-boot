/*
 * Copyright 2012-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.web.servlet.error;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Default implementation of {@link ErrorAttributes}. Provides the following attributes
 * when possible:
 * <ul>
 * <li>timestamp - The time that the errors were extracted</li>
 * <li>status - The status code</li>
 * <li>error - The error reason</li>
 * <li>exception - The class name of the root exception (if configured)</li>
 * <li>message - The exception message (if configured)</li>
 * <li>errors - Any {@link ObjectError}s from a {@link BindingResult} or
 * {@link MethodValidationResult} exception (if configured)</li>
 * <li>trace - The exception stack trace (if configured)</li>
 * <li>path - The URL path when the exception was raised</li>
 * </ul>
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Stephane Nicoll
 * @author Vedran Pavic
 * @author Scott Frederick
 * @author Moritz Halbritter
 * @author Yanming Zhou
 * @author Brian Clozel
 * @since 2.0.0
 * @see ErrorAttributes
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DefaultErrorAttributes implements ErrorAttributes, HandlerExceptionResolver, Ordered {

	private static final String ERROR_INTERNAL_ATTRIBUTE = DefaultErrorAttributes.class.getName() + ".ERROR";

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		storeErrorAttributes(request, ex);
		return null;
	}

	private void storeErrorAttributes(HttpServletRequest request, Exception ex) {
		request.setAttribute(ERROR_INTERNAL_ATTRIBUTE, ex);
	}

	@Override
	public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
		Map<String, Object> errorAttributes = getErrorAttributes(webRequest, options.isIncluded(Include.STACK_TRACE));
		options.retainIncluded(errorAttributes);
		return errorAttributes;
	}

	private Map<String, Object> getErrorAttributes(WebRequest webRequest, boolean includeStackTrace) {
		Map<String, Object> errorAttributes = new LinkedHashMap<>();
		errorAttributes.put("timestamp", new Date());
		addStatus(errorAttributes, webRequest);
		addErrorDetails(errorAttributes, webRequest, includeStackTrace);
		addPath(errorAttributes, webRequest);
		return errorAttributes;
	}

	private void addStatus(Map<String, Object> errorAttributes, RequestAttributes requestAttributes) {
		Integer status = getAttribute(requestAttributes, RequestDispatcher.ERROR_STATUS_CODE);
		if (status == null) {
			errorAttributes.put("status", 999);
			errorAttributes.put("error", "None");
			return;
		}
		errorAttributes.put("status", status);
		try {
			errorAttributes.put("error", HttpStatus.valueOf(status).getReasonPhrase());
		}
		catch (Exception ex) {
			// Unable to obtain a reason
			errorAttributes.put("error", "Http Status " + status);
		}
	}

	private void addErrorDetails(Map<String, Object> errorAttributes, WebRequest webRequest,
			boolean includeStackTrace) {
		Throwable error = getError(webRequest);
		if (error != null) {
			while (error instanceof ServletException && error.getCause() != null) {
				error = error.getCause();
			}
			errorAttributes.put("exception", error.getClass().getName());
			if (includeStackTrace) {
				addStackTrace(errorAttributes, error);
			}
		}
		addErrorMessage(errorAttributes, webRequest, error);
	}

	private void addErrorMessage(Map<String, Object> errorAttributes, WebRequest webRequest, Throwable error) {
		BindingResult bindingResult = extractBindingResult(error);
		if (bindingResult != null) {
			addMessageAndErrorsFromBindingResult(errorAttributes, bindingResult);
		}
		else {
			MethodValidationResult methodValidationResult = extractMethodValidationResult(error);
			if (methodValidationResult != null) {
				addMessageAndErrorsFromMethodValidationResult(errorAttributes, methodValidationResult);
			}
			else {
				addExceptionErrorMessage(errorAttributes, webRequest, error);
			}
		}
	}

	private void addExceptionErrorMessage(Map<String, Object> errorAttributes, WebRequest webRequest, Throwable error) {
		errorAttributes.put("message", getMessage(webRequest, error));
	}

	/**
	 * Returns the message to be included as the value of the {@code message} error
	 * attribute. By default the returned message is the first of the following that is
	 * not empty:
	 * <ol>
	 * <li>Value of the {@link RequestDispatcher#ERROR_MESSAGE} request attribute.
	 * <li>Message of the given {@code error}.
	 * <li>{@code No message available}.
	 * </ol>
	 * @param webRequest current request
	 * @param error current error, if any
	 * @return message to include in the error attributes
	 * @since 2.4.0
	 */
	protected String getMessage(WebRequest webRequest, Throwable error) {
		Object message = getAttribute(webRequest, RequestDispatcher.ERROR_MESSAGE);
		if (!ObjectUtils.isEmpty(message)) {
			return message.toString();
		}
		if (error != null && StringUtils.hasLength(error.getMessage())) {
			return error.getMessage();
		}
		return "No message available";
	}

	private void addMessageAndErrorsFromBindingResult(Map<String, Object> errorAttributes, BindingResult result) {
		addMessageAndErrorsForValidationFailure(errorAttributes, "object='" + result.getObjectName() + "'",
				result.getAllErrors());
	}

	private void addMessageAndErrorsFromMethodValidationResult(Map<String, Object> errorAttributes,
			MethodValidationResult result) {
		List<ObjectError> errors = result.getAllErrors()
			.stream()
			.filter(ObjectError.class::isInstance)
			.map(ObjectError.class::cast)
			.toList();
		addMessageAndErrorsForValidationFailure(errorAttributes, "method='" + result.getMethod() + "'", errors);
	}

	private void addMessageAndErrorsForValidationFailure(Map<String, Object> errorAttributes, String validated,
			List<ObjectError> errors) {
		errorAttributes.put("message", "Validation failed for " + validated + ". Error count: " + errors.size());
		errorAttributes.put("errors", errors);
	}

	private BindingResult extractBindingResult(Throwable error) {
		if (error instanceof BindingResult bindingResult) {
			return bindingResult;
		}
		return null;
	}

	private MethodValidationResult extractMethodValidationResult(Throwable error) {
		if (error instanceof MethodValidationResult methodValidationResult) {
			return methodValidationResult;
		}
		return null;
	}

	private void addStackTrace(Map<String, Object> errorAttributes, Throwable error) {
		StringWriter stackTrace = new StringWriter();
		error.printStackTrace(new PrintWriter(stackTrace));
		stackTrace.flush();
		errorAttributes.put("trace", stackTrace.toString());
	}

	private void addPath(Map<String, Object> errorAttributes, RequestAttributes requestAttributes) {
		String path = getAttribute(requestAttributes, RequestDispatcher.ERROR_REQUEST_URI);
		if (path != null) {
			errorAttributes.put("path", path);
		}
	}

	@Override
	public ProblemDetail asProblemDetail(ServerRequest request, ErrorAttributeOptions options) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		problemDetail.setTitle("None");
		problemDetail.setProperty("timestamp", new Date());
		addStatus(problemDetail, request, options);
		addErrorDetails(problemDetail, request, options);
		if (options.isIncluded(Include.PATH)) {
			addPath(problemDetail, request);
		}
		return problemDetail;
	}

	private void addStatus(ProblemDetail problemDetail, ServerRequest request, ErrorAttributeOptions options) {
		Optional<Integer> status = getAttribute(request, RequestDispatcher.ERROR_STATUS_CODE);
		if (status.isPresent()) {
			HttpStatusCode statusCode = HttpStatusCode.valueOf(status.get());
			if (options.isIncluded(Include.STATUS)) {
				problemDetail.setStatus(statusCode.value());
			}
			if (options.isIncluded(Include.ERROR)) {
				if (statusCode instanceof HttpStatus httpStatus) {
					problemDetail.setTitle(httpStatus.getReasonPhrase());
				}
				else {
					problemDetail.setTitle("Http Status " + statusCode.value());
				}
			}
		}
	}

	private void addErrorDetails(ProblemDetail problemDetail, ServerRequest request, ErrorAttributeOptions options) {
		Throwable error = getError(request).orElse(null);
		if (error != null) {
			while (error instanceof ServletException && error.getCause() != null) {
				error = error.getCause();
			}
			if (options.isIncluded(Include.EXCEPTION)) {
				problemDetail.setProperty("exception", error.getClass().getName());
			}
			if (options.isIncluded(Include.STACK_TRACE)) {
				addStackTrace(problemDetail, error);
			}
		}
		addErrorMessage(problemDetail, request, error, options);
	}

	private void addStackTrace(ProblemDetail problemDetail, Throwable error) {
		StringWriter stackTrace = new StringWriter();
		error.printStackTrace(new PrintWriter(stackTrace));
		stackTrace.flush();
		problemDetail.setProperty("trace", stackTrace.toString());
	}

	private void addErrorMessage(ProblemDetail problemDetail, ServerRequest request, Throwable error,
			ErrorAttributeOptions options) {
		BindingResult bindingResult = extractBindingResult(error);
		if (bindingResult != null) {
			addMessageAndErrorsFromBindingResult(problemDetail, bindingResult, options);
		}
		else {
			MethodValidationResult methodValidationResult = extractMethodValidationResult(error);
			if (methodValidationResult != null) {
				addMessageAndErrorsFromMethodValidationResult(problemDetail, methodValidationResult, options);
			}
			else {
				if (options.isIncluded(Include.MESSAGE)) {
					problemDetail.setDetail(getMessage(new ServletWebRequest(request.servletRequest()), error));
				}
			}
		}
	}

	private void addMessageAndErrorsFromBindingResult(ProblemDetail problemDetail, BindingResult result,
			ErrorAttributeOptions options) {
		addMessageAndErrorsForValidationFailure(problemDetail, "object='" + result.getObjectName() + "'",
				result.getAllErrors(), options);
	}

	private void addMessageAndErrorsFromMethodValidationResult(ProblemDetail problemDetail,
			MethodValidationResult result, ErrorAttributeOptions options) {
		List<ObjectError> errors = result.getAllErrors()
			.stream()
			.filter(ObjectError.class::isInstance)
			.map(ObjectError.class::cast)
			.toList();
		addMessageAndErrorsForValidationFailure(problemDetail, "method='" + result.getMethod() + "'", errors, options);
	}

	private void addMessageAndErrorsForValidationFailure(ProblemDetail problemDetail, String validated,
			List<ObjectError> errors, ErrorAttributeOptions options) {
		if (options.isIncluded(Include.MESSAGE)) {
			problemDetail.setDetail("Validation failed for " + validated + ". Error count: " + errors.size());
		}
		if (options.isIncluded(Include.BINDING_ERRORS)) {
			problemDetail.setProperty("errors", errors);
		}
	}

	private void addPath(ProblemDetail problemDetail, ServerRequest request) {
		Optional<String> path = getAttribute(request, RequestDispatcher.ERROR_REQUEST_URI);
		path.ifPresent((p) -> problemDetail.setInstance(URI.create(p)));
	}

	@Override
	public Throwable getError(WebRequest webRequest) {
		Throwable exception = getAttribute(webRequest, ERROR_INTERNAL_ATTRIBUTE);
		if (exception == null) {
			exception = getAttribute(webRequest, RequestDispatcher.ERROR_EXCEPTION);
		}
		return exception;
	}

	@Override
	public Optional<Throwable> getError(ServerRequest request) {
		Optional<Throwable> exception = getAttribute(request, ERROR_INTERNAL_ATTRIBUTE);
		if (exception.isPresent()) {
			return exception;
		}
		return getAttribute(request, RequestDispatcher.ERROR_EXCEPTION);
	}

	@SuppressWarnings("unchecked")
	private <T> T getAttribute(RequestAttributes requestAttributes, String name) {
		return (T) requestAttributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
	}

	@SuppressWarnings("unchecked")
	private <T> Optional<T> getAttribute(ServerRequest request, String name) {
		return (Optional<T>) request.attribute(name);
	}

}
