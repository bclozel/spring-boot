/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.boot.webmvc.autoconfigure.error;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.web.error.Error;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * {@code @ControllerAdvice} annotated {@link ResponseEntityExceptionHandler} that is
 * auto-configured for problem details support and HTML rendering of errors.
 *
 * @author Brian Clozel
 * @since 4.1.0
 */
@ControllerAdvice
public class DefaultExceptionHandler extends AbstractViewRenderingExceptionHandler {

	private final ErrorProperties errorProperties;

	public DefaultExceptionHandler(ErrorProperties error, List<ErrorViewResolver> errorViewResolvers) {
		super(errorViewResolvers);
		this.errorProperties = error;
	}

	public ErrorProperties getErrorProperties() {
		return this.errorProperties;
	}

	@Override
	protected ProblemDetail resolveProblemDetail(Exception ex, NativeWebRequest request) {
		ProblemDetail problemDetail = null;
		// MethodArgumentNotValidException is an ErrorResponse
		// we need to resolve it before MVC can
		if (ex instanceof MethodArgumentNotValidException manvEx) {
			problemDetail = methodArgumentNotValidException(manvEx, request);
		}
		else {
			// delegate to MVC problem detail resolution
			problemDetail = super.resolveProblemDetail(ex, request);
		}
		// if not resolved by MVC, catch all for Spring Boot
		if (problemDetail == null) {
			problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
			problemDetail.setTitle(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
			addStatus(problemDetail, ex);
			ErrorAttributeOptions options = getErrorAttributeOptions(request);
			addErrorMessage(problemDetail, ex, options);
		}
		// add further properties to resolved problem detail
		updateProblemDetail(problemDetail, ex, request);
		return problemDetail;
	}

	@ExceptionHandler(exception = Exception.class)
	public final ResponseEntity<Object> handleExceptions(Exception ex, NativeWebRequest request) throws Exception {

		ProblemDetail problemDetail = resolveProblemDetail(ex, request);
		return createResponseEntity(ex, problemDetail, request);
	}

	@ExceptionHandler(exception = Exception.class, produces = MediaType.TEXT_HTML_VALUE)
	public final @Nullable ModelAndView handleExceptionsAsHtml(Exception ex, NativeWebRequest request,
			HttpServletResponse response) throws Exception {

		ProblemDetail problemDetail = resolveProblemDetail(ex, request);
		ModelMap model = new ModelMap();
		model.put("problemDetail", problemDetail);
		HttpStatusCode statusCode = HttpStatusCode.valueOf(problemDetail.getStatus());
		response.setStatus(statusCode.value());
		ModelAndView modelAndView = resolveErrorView(request, statusCode, model);
		return (modelAndView != null) ? modelAndView : new ModelAndView("error", model);
	}

	protected @Nullable ProblemDetail methodArgumentNotValidException(MethodArgumentNotValidException ex,
			WebRequest request) {
		String message = "Validation failed for object='%s'. Error count: %s"
			.formatted(ex.getBindingResult().getObjectName(), ex.getBindingResult().getAllErrors().size());
		ErrorAttributeOptions options = getErrorAttributeOptions(request);
		ProblemDetail problemDetail = ex.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
		if (options.isIncluded(Include.MESSAGE)) {
			problemDetail.setDetail(message);
		}
		if (options.isIncluded(Include.BINDING_ERRORS)) {
			problemDetail.setProperty("errors", ex.getAllErrors());
		}
		return problemDetail;
	}

	@Override
	protected @Nullable ProblemDetail methodValidationException(MethodValidationException ex, WebRequest request) {
		String message = "Validation failed for method='%s'. Error count: %s".formatted(ex.getMethod(),
				ex.getAllErrors().size());
		ErrorAttributeOptions options = getErrorAttributeOptions(request);
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Validation failed");
		if (options.isIncluded(Include.MESSAGE)) {
			problemDetail.setDetail(message);
		}
		if (options.isIncluded(Include.BINDING_ERRORS)) {
			problemDetail.setProperty("errors", Error.wrapIfNecessary(ex.getAllErrors()));
		}
		return problemDetail;
	}

	protected void updateProblemDetail(ProblemDetail problemDetail, Throwable ex, NativeWebRequest request) {
		ErrorAttributeOptions options = getErrorAttributeOptions(request);
		problemDetail.setProperty("timestamp", LocalDateTime.now());
		addErrorDetails(problemDetail, ex, options);
		addPath(problemDetail, request, options);
	}

	private void addErrorDetails(ProblemDetail problemDetail, Throwable error, ErrorAttributeOptions options) {
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

	private void addStackTrace(ProblemDetail problemDetail, Throwable error) {
		StringWriter stackTrace = new StringWriter();
		error.printStackTrace(new PrintWriter(stackTrace));
		stackTrace.flush();
		problemDetail.setProperty("trace", stackTrace.toString());
	}

	private void addErrorMessage(ProblemDetail problemDetail, Throwable error, ErrorAttributeOptions options) {
		if (options.isIncluded(Include.MESSAGE)) {
			String detail = Objects.requireNonNullElse(resolveDetail(error), "No message available");
			problemDetail.setDetail(detail);
		}
	}

	private @Nullable String resolveDetail(Throwable error) {
		if (StringUtils.hasLength(error.getMessage())) {
			return error.getMessage();
		}
		if (error instanceof ResponseStatusException rse && StringUtils.hasLength(rse.getReason())) {
			return rse.getReason();
		}
		else {
			ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(error.getClass(),
					ResponseStatus.class);
			if (responseStatus != null && StringUtils.hasLength(responseStatus.reason())) {
				return responseStatus.reason();
			}
		}
		return null;
	}

	private void addPath(ProblemDetail problemDetail, NativeWebRequest request, ErrorAttributeOptions options) {
		if (options.isIncluded(Include.PATH)) {
			HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
			if (servletRequest != null) {
				problemDetail.setProperty("path", servletRequest.getRequestURI());
			}
		}
	}

	private void addStatus(ProblemDetail problemDetail, Throwable error) {
		HttpStatusCode statusCode = resolveStatus(error);
		if (statusCode != null) {
			problemDetail.setStatus(statusCode.value());
			try {
				problemDetail.setTitle(HttpStatus.valueOf(statusCode.value()).getReasonPhrase());
			}
			catch (Exception ex) {
				// Unable to obtain a reason
				problemDetail.setTitle("Http Status " + statusCode);
			}
		}
	}

	private @Nullable HttpStatusCode resolveStatus(Throwable error) {
		if (error instanceof ResponseStatusException rse) {
			return rse.getStatusCode();
		}
		ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(error.getClass(),
				ResponseStatus.class);
		if (responseStatus != null) {
			return responseStatus.code();
		}
		return null;
	}

	protected ErrorAttributeOptions getErrorAttributeOptions(WebRequest request) {
		ErrorAttributeOptions options = ErrorAttributeOptions.defaults();
		if (this.errorProperties.isIncludeException()) {
			options = options.including(Include.EXCEPTION);
		}
		if (isIncludeStackTrace(request)) {
			options = options.including(Include.STACK_TRACE);
		}
		if (isIncludeMessage(request)) {
			options = options.including(Include.MESSAGE);
		}
		if (isIncludeBindingErrors(request)) {
			options = options.including(Include.BINDING_ERRORS);
		}
		options = isIncludePath(request) ? options.including(Include.PATH) : options.excluding(Include.PATH);
		return options;
	}

	/**
	 * Determine if the stacktrace attribute should be included.
	 * @param request the source request
	 * @return if the stacktrace attribute should be included
	 */
	protected boolean isIncludeStackTrace(WebRequest request) {
		return switch (getErrorProperties().getIncludeStacktrace()) {
			case ALWAYS -> true;
			case ON_PARAM -> getTraceParameter(request);
			case NEVER -> false;
		};
	}

	/**
	 * Determine if the message attribute should be included.
	 * @param request the source request
	 * @return if the message attribute should be included
	 */
	protected boolean isIncludeMessage(WebRequest request) {
		return switch (getErrorProperties().getIncludeMessage()) {
			case ALWAYS -> true;
			case ON_PARAM -> getMessageParameter(request);
			case NEVER -> false;
		};
	}

	/**
	 * Determine if the errors attribute should be included.
	 * @param request the source request
	 * @return if the errors attribute should be included
	 */
	protected boolean isIncludeBindingErrors(WebRequest request) {
		return switch (getErrorProperties().getIncludeBindingErrors()) {
			case ALWAYS -> true;
			case ON_PARAM -> getErrorsParameter(request);
			case NEVER -> false;
		};
	}

	/**
	 * Determine if the path attribute should be included.
	 * @param request the source request
	 * @return if the path attribute should be included
	 */
	protected boolean isIncludePath(WebRequest request) {
		return switch (getErrorProperties().getIncludePath()) {
			case ALWAYS -> true;
			case ON_PARAM -> getPathParameter(request);
			case NEVER -> false;
		};
	}

	private boolean getTraceParameter(WebRequest request) {
		return getBooleanParameter(request, "trace");
	}

	private boolean getMessageParameter(WebRequest request) {
		return getBooleanParameter(request, "message");
	}

	private boolean getErrorsParameter(WebRequest request) {
		return getBooleanParameter(request, "errors");
	}

	private boolean getPathParameter(WebRequest request) {
		return getBooleanParameter(request, "path");
	}

	private boolean getBooleanParameter(WebRequest request, String parameterName) {
		String parameter = request.getParameter(parameterName);
		if (parameter == null) {
			return false;
		}
		return !"false".equalsIgnoreCase(parameter);
	}

}
