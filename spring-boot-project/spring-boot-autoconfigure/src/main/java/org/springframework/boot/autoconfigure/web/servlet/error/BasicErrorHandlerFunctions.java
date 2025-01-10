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

package org.springframework.boot.autoconfigure.web.servlet.error;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;

import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.util.Assert;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Basic implementations for {@link HandlerFunction handler functions} and
 * {@link RequestPredicate request predicates }to be used for handling errors globally.
 * They can be used to create a custom {@link RouterFunction} that handles requests to the
 * error page.
 * <p>
 * More specific errors can be handled either using Spring MVC abstractions (e.g.
 * {@code @ExceptionHandler}) or by adding servlet
 * {@link AbstractServletWebServerFactory#setErrorPages server error pages}.
 *
 * @author Brian Clozel
 * @since 3.5.0
 * @see ErrorAttributes
 */
public class BasicErrorHandlerFunctions extends AbstractErrorHandlerFunctions {

	private final ErrorProperties errorProperties;

	/**
	 * Create a new {@link BasicErrorHandlerFunctions} instance.
	 * @param errorAttributes the error attributes
	 * @param errorProperties configuration properties
	 */
	public BasicErrorHandlerFunctions(ErrorAttributes errorAttributes, ErrorProperties errorProperties) {
		this(errorAttributes, errorProperties, Collections.emptyList());
	}

	/**
	 * Create a new {@link BasicErrorHandlerFunctions} instance.
	 * @param errorAttributes the error attributes
	 * @param errorProperties configuration properties
	 * @param errorViewResolvers error view resolvers
	 */
	public BasicErrorHandlerFunctions(ErrorAttributes errorAttributes, ErrorProperties errorProperties,
			List<ErrorViewResolver> errorViewResolvers) {
		super(errorAttributes, errorViewResolvers);
		Assert.notNull(errorProperties, "ErrorProperties must not be null");
		this.errorProperties = errorProperties;
	}

	/**
	 * Render an HTML error page using the "error" view template.
	 * @param request the server request
	 * @return the rendered error page
	 */
	public ServerResponse handleErrorHtml(ServerRequest request) {
		HttpStatusCode status = getStatus(request);
		ErrorAttributeOptions errorAttributeOptions = getErrorAttributeOptions(request, MediaType.TEXT_HTML);
		ProblemDetail problemDetail = getProblemDetail(request, errorAttributeOptions);
		Map<String, Object> model = Map.of("problem", problemDetail);
		ModelAndView mav = resolveErrorView(request, status, model);
		mav = (mav != null) ? mav : new ModelAndView("error", model);
		return ServerResponse.status(status).contentType(MediaType.TEXT_HTML).render(mav.getViewName(), mav.getModel());
	}

	/**
	 * Create a {@link HandlerFunction} that can be used to render an error as a
	 * {@link ProblemDetail} response with the given media type.
	 * @param mediaType the media type to use for rendering
	 * @return the handler function
	 */
	public HandlerFunction<ServerResponse> createProblemDetailErrorHandler(MediaType mediaType) {
		return (request) -> {
			HttpStatusCode status = getStatus(request);
			ProblemDetail body = getProblemDetail(request, getErrorAttributeOptions(request, mediaType));
			return ServerResponse.status(status).contentType(mediaType).body(body);
		};
	}

	/**
	 * A {@link RequestPredicate} that checks whether the expected HTTP response status
	 * for the error response will be {@code 204 "No Content"}.
	 * @return the request predicate
	 */
	public RequestPredicate isNoContentStatus() {
		return (request) -> HttpStatus.NO_CONTENT.equals(getStatus(request));
	}

	/**
	 * Render the error as an HTTP {@code 204 "No Content"} response.
	 * @param request the server request
	 * @return the error response
	 */
	public ServerResponse handleErrorNoContent(ServerRequest request) {
		return ServerResponse.noContent().build();
	}

	/**
	 * Return the expected HTTP status for this error response, stored as a request
	 * attribute.
	 * @param request the server request
	 * @return the error status code
	 */
	protected HttpStatusCode getStatus(ServerRequest request) {
		Integer status = (Integer) request.attribute(RequestDispatcher.ERROR_STATUS_CODE).orElse(500);
		return HttpStatusCode.valueOf(status);
	}

	/**
	 * Return the {@link ErrorAttributeOptions} for the given HTTP request and requested
	 * media type.
	 * <p>
	 * The default implementation relies on the {@link ErrorProperties} and request query
	 * parameters.
	 * @param request the server request
	 * @param mediaType the requested media type for the response
	 * @return the error attribute options
	 */
	protected ErrorAttributeOptions getErrorAttributeOptions(ServerRequest request, MediaType mediaType) {
		ErrorAttributeOptions options = ErrorAttributeOptions.defaults();
		if (this.errorProperties.isIncludeException()) {
			options = options.including(Include.EXCEPTION);
		}
		if (isIncludeStackTrace(request, mediaType)) {
			options = options.including(Include.STACK_TRACE);
		}
		if (isIncludeMessage(request, mediaType)) {
			options = options.including(Include.MESSAGE);
		}
		if (isIncludeBindingErrors(request, mediaType)) {
			options = options.including(Include.BINDING_ERRORS);
		}
		options = isIncludePath(request, mediaType) ? options.including(Include.PATH) : options.excluding(Include.PATH);
		return options;
	}

	/**
	 * Determine if the stacktrace attribute should be included.
	 * @param request the source request
	 * @param produces the media type produced (or {@code MediaType.ALL})
	 * @return if the stacktrace attribute should be included
	 */
	protected boolean isIncludeStackTrace(ServerRequest request, MediaType produces) {
		return switch (getErrorProperties().getIncludeStacktrace()) {
			case ALWAYS -> true;
			case ON_PARAM -> getBooleanParameter(request, "trace");
			case NEVER -> false;
		};
	}

	/**
	 * Determine if the message attribute should be included.
	 * @param request the source request
	 * @param produces the media type produced (or {@code MediaType.ALL})
	 * @return if the message attribute should be included
	 */
	protected boolean isIncludeMessage(ServerRequest request, MediaType produces) {
		return switch (getErrorProperties().getIncludeMessage()) {
			case ALWAYS -> true;
			case ON_PARAM -> getBooleanParameter(request, "message");
			case NEVER -> false;
		};
	}

	/**
	 * Determine if the errors attribute should be included.
	 * @param request the source request
	 * @param produces the media type produced (or {@code MediaType.ALL})
	 * @return if the errors attribute should be included
	 */
	protected boolean isIncludeBindingErrors(ServerRequest request, MediaType produces) {
		return switch (getErrorProperties().getIncludeBindingErrors()) {
			case ALWAYS -> true;
			case ON_PARAM -> getBooleanParameter(request, "errors");
			case NEVER -> false;
		};
	}

	/**
	 * Determine if the path attribute should be included.
	 * @param request the source request
	 * @param produces the media type produced (or {@code MediaType.ALL})
	 * @return if the path attribute should be included
	 */
	protected boolean isIncludePath(ServerRequest request, MediaType produces) {
		return switch (getErrorProperties().getIncludePath()) {
			case ALWAYS -> true;
			case ON_PARAM -> getBooleanParameter(request, "path");
			case NEVER -> false;
		};
	}

	/**
	 * Provide access to the error properties.
	 * @return the error properties
	 */
	protected ErrorProperties getErrorProperties() {
		return this.errorProperties;
	}

}
