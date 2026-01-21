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

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.WebUtils;

/**
 * A class with an {@code @ExceptionHandler} method that handles all Spring MVC raised
 * exceptions by returning a {@link ResponseEntity} with RFC 9457 formatted error details
 * in the body.
 *
 * <p>
 * Convenient as a base class of an {@link ControllerAdvice @ControllerAdvice} for global
 * exception handling in an application. Subclasses can override individual methods that
 * handle a specific exception, override {@link #resolveProblemDetail} to override the
 * transformation of exceptions into {@link ProblemDetail} instances, or
 * {@link #handleExceptionInternal} to override common handling of all exceptions.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 7.1.0
 */
public abstract class ProblemDetailsExceptionHandler implements MessageSourceAware {

	/**
	 * Log category to use when no mapped handler is found for a request.
	 * @see #pageNotFoundLogger
	 */
	public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

	/**
	 * Specific logger to use when no mapped handler is found for a request.
	 * @see #PAGE_NOT_FOUND_LOG_CATEGORY
	 */
	protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);

	/**
	 * Common logger for use in subclasses.
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	private @Nullable MessageSource messageSource;

	@Override
	public void setMessageSource(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	protected @Nullable MessageSource getMessageSource() {
		return this.messageSource;
	}

	/**
	 * Handle all exceptions raised within Spring MVC handling of the request.
	 * @param ex the exception to handle
	 * @param request the current request
	 * @return the handled error as a response entity
	 */
	@ExceptionHandler({ HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class,
			HttpMediaTypeNotAcceptableException.class, MissingPathVariableException.class,
			MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
			ServletRequestBindingException.class, MethodArgumentNotValidException.class,
			HandlerMethodValidationException.class, NoHandlerFoundException.class, NoResourceFoundException.class,
			AsyncRequestTimeoutException.class, ErrorResponseException.class, MaxUploadSizeExceededException.class,
			ConversionNotSupportedException.class, TypeMismatchException.class, HttpMessageNotReadableException.class,
			HttpMessageNotWritableException.class, MethodValidationException.class,
			AsyncRequestNotUsableException.class })
	public final @Nullable ResponseEntity<Object> handleException(Exception ex, NativeWebRequest request)
			throws Exception {

		if (ex instanceof HttpRequestMethodNotSupportedException) {
			pageNotFoundLogger.warn(ex.getMessage());
		}
		ProblemDetail problemDetail = resolveProblemDetail(ex, request);
		if (problemDetail != null) {
			return handleExceptionInternal(ex, problemDetail, request);
		}
		// Unknown exception, typically a wrapper with a common MVC exception as cause
		// (since @ExceptionHandler type declarations also match nested causes):
		// We only deal with top-level MVC exceptions here, so let's rethrow the given
		// exception for further processing through the HandlerExceptionResolver chain.
		throw ex;
	}

	/**
	 * Resolve the given exception as a {@link ProblemDetail} instance, to be rendered as
	 * a {@link ResponseEntity response body} or as a model entry for HTML views.
	 * @param ex the exception to handle
	 * @param request the current request
	 * @return the problem detail instance
	 */
	protected @Nullable ProblemDetail resolveProblemDetail(Exception ex, NativeWebRequest request) {

		if (ex instanceof ErrorResponse errorResponse) {
			return errorResponseAsProblemDetail(errorResponse, request);
		}
		// Lower level exceptions, and exceptions used symmetrically on client and server
		else if (ex instanceof ConversionNotSupportedException theEx) {
			return conversionNotSupported(theEx, request);
		}
		else if (ex instanceof TypeMismatchException theEx) {
			return typeMismatch(theEx, request);
		}
		else if (ex instanceof HttpMessageNotReadableException theEx) {
			return httpMessageNotReadable(theEx, request);
		}
		else if (ex instanceof HttpMessageNotWritableException theEx) {
			return httpMessageNotWritable(theEx, request);
		}
		else if (ex instanceof MethodValidationException theEx) {
			return methodValidationException(theEx, request);
		}
		else if (ex instanceof AsyncRequestNotUsableException theEx) {
			return asyncRequestNotUsableException(theEx, request);
		}
		return null;
	}

	/**
	 * Resolve the given {@link ErrorResponse} exception as a {@link ProblemDetail}
	 * instance, to be rendered as a {@link ResponseEntity response body} or as a model
	 * entry for HTML views.
	 * @param errorResponse the error response to handle
	 * @param request the current request
	 * @return the problem detail instance
	 */
	protected ProblemDetail errorResponseAsProblemDetail(ErrorResponse errorResponse, WebRequest request) {
		return errorResponse.updateAndGetBody(this.messageSource, LocaleContextHolder.getLocale());
	}

	/**
	 * Resolve a {@link ConversionNotSupportedException} as a {@link ProblemDetail}
	 * instance.
	 * <p>
	 * By default, this returns a {@link ProblemDetail} with a 500 HTTP status and a short
	 * detail message, and also looks up an override for the detail via
	 * {@link MessageSource}.
	 * @param ex the exception to handle
	 * @param request the current request
	 * @return a {@code ProblemDetail} for the response to use, or {@code null} if the
	 * exception cannot be handled.
	 */
	protected @Nullable ProblemDetail conversionNotSupported(ConversionNotSupportedException ex, WebRequest request) {
		Object[] args = { ex.getPropertyName(), ex.getValue() };
		String defaultDetail = "Failed to convert '%s' with value: '%s'".formatted(args[0], args[1]);
		return createProblemDetail(ex, HttpStatus.INTERNAL_SERVER_ERROR, defaultDetail, null, args, request);
	}

	/**
	 * Resolve a {@link TypeMismatchException} as a {@link ProblemDetail} instance.
	 * <p>
	 * By default, this returns a {@link ProblemDetail} with a 400 HTTP status and a short
	 * detail message, and also looks up an override for the detail via
	 * {@link MessageSource}.
	 * @param ex the exception to handle
	 * @param request the current request
	 * @return a {@code ProblemDetail} for the response to use, or {@code null} if the
	 * exception cannot be handled.
	 */
	protected @Nullable ProblemDetail typeMismatch(TypeMismatchException ex, WebRequest request) {

		Object[] args = { ex.getPropertyName(), ex.getValue(),
				(ex.getRequiredType() != null) ? ex.getRequiredType().getSimpleName() : "" };
		String defaultDetail = "Failed to convert '" + args[0] + "' with value: '" + args[1] + "'";
		String messageCode = ErrorResponse.getDefaultDetailMessageCode(TypeMismatchException.class, null);
		return createProblemDetail(ex, HttpStatus.BAD_REQUEST, defaultDetail, messageCode, args, request);
	}

	/**
	 * Resolve a {@link HttpMessageNotReadableException} as a {@link ProblemDetail}
	 * instance.
	 * <p>
	 * By default, this returns a {@link ProblemDetail} with a 400 HTTP status and a short
	 * detail message, and also looks up an override for the detail via
	 * {@link MessageSource}.
	 * @param ex the exception to handle
	 * @param request the current request
	 * @return a {@code ProblemDetail} for the response to use, or {@code null} if the
	 * exception cannot be handled.
	 */
	protected @Nullable ProblemDetail httpMessageNotReadable(HttpMessageNotReadableException ex, WebRequest request) {

		return createProblemDetail(ex, HttpStatus.BAD_REQUEST, "Failed to read request", null, null, request);
	}

	/**
	 * Resolve a {@link HttpMessageNotWritableException} as a {@link ProblemDetail}
	 * instance.
	 * <p>
	 * By default, this returns a {@link ProblemDetail} with a 500 HTTP status and a short
	 * detail message, and also looks up an override for the detail via
	 * {@link MessageSource}.
	 * @param ex the exception to handle
	 * @param request the current request
	 * @return a {@code ProblemDetail} for the response to use, or {@code null} if the
	 * exception cannot be handled.
	 */
	protected @Nullable ProblemDetail httpMessageNotWritable(HttpMessageNotWritableException ex, WebRequest request) {

		return createProblemDetail(ex, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to write request", null, null,
				request);
	}

	/**
	 * Resolve a {@link MethodValidationException} as a {@link ProblemDetail} instance.
	 * <p>
	 * By default, this returns a {@link ProblemDetail} with a 500 HTTP status and a short
	 * detail message, and also looks up an override for the detail via
	 * {@link MessageSource}.
	 * @param ex the exception to handle
	 * @param request the current request
	 * @return a {@code ProblemDetail} for the response to use, or {@code null} if the
	 * exception cannot be handled.
	 */
	protected @Nullable ProblemDetail methodValidationException(MethodValidationException ex, WebRequest request) {

		return createProblemDetail(ex, HttpStatus.INTERNAL_SERVER_ERROR, "Validation failed", null, null, request);
	}

	/**
	 * Resolve a {@link AsyncRequestNotUsableException} as a {@link ProblemDetail}
	 * instance.
	 * <p>
	 * By default, treturn {@code null} since the response is not usable.
	 * @param ex the exception to handle
	 * @param request the current request
	 * @return a {@code ProblemDetail} for the response to use, or {@code null} if the
	 * exception cannot be handled.
	 */
	protected @Nullable ProblemDetail asyncRequestNotUsableException(AsyncRequestNotUsableException ex,
			WebRequest request) {

		return null;
	}

	/**
	 * Convenience method to create a {@link ProblemDetail} for any exception that doesn't
	 * implement {@link ErrorResponse}, also performing a {@link MessageSource} lookup for
	 * the "detail" field.
	 * @param ex the exception being handled
	 * @param status the status to associate with the exception
	 * @param defaultDetail default value for the "detail" field
	 * @param detailMessageCode the code to use to look up the "detail" field through a
	 * {@code MessageSource}; if {@code null} then
	 * {@link ErrorResponse#getDefaultDetailMessageCode(Class, String)} is used to
	 * determine the default message code to use
	 * @param detailMessageArguments the arguments to go with the detailMessageCode
	 * @param request the current request
	 * @return the created {@code ProblemDetail} instance
	 */
	protected ProblemDetail createProblemDetail(Exception ex, HttpStatusCode status, String defaultDetail,
			@Nullable String detailMessageCode, Object @Nullable [] detailMessageArguments, WebRequest request) {

		ErrorResponse.Builder builder = ErrorResponse.builder(ex, status, defaultDetail);
		if (detailMessageCode != null) {
			builder.detailMessageCode(detailMessageCode);
		}
		if (detailMessageArguments != null) {
			builder.detailMessageArguments(detailMessageArguments);
		}
		return builder.build().updateAndGetBody(this.messageSource, LocaleContextHolder.getLocale());
	}

	/**
	 * Internal handler method that all others in this class delegate to, for common
	 * handling, and for the creation of a {@link ResponseEntity}.
	 * <p>
	 * The default implementation does the following:
	 * <ul>
	 * <li>return {@code null} if response is already committed
	 * <li>set the {@code "jakarta.servlet.error.exception"} request attribute if the
	 * response status is 500 (INTERNAL_SERVER_ERROR).
	 * <li>extract the {@link ErrorResponse#getBody() body} from {@link ErrorResponse}
	 * exceptions, if the {@code body} is {@code null}.
	 * </ul>
	 * @param ex the exception to handle
	 * @param problemDetail the RFC 9457 problem detail instance
	 * @param request the current request
	 * @return a {@code ResponseEntity} for the response to use, possibly {@code null}
	 * when the response is already committed
	 */
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex,
			@Nullable ProblemDetail problemDetail, WebRequest request) {

		if (request instanceof ServletWebRequest servletWebRequest) {
			HttpServletResponse response = servletWebRequest.getResponse();
			if (response != null && response.isCommitted()) {
				if (this.logger.isWarnEnabled()) {
					this.logger.warn("Response already committed. Ignoring: " + ex);
				}
				return null;
			}
		}
		if (problemDetail == null) {
			// TODO: this is never executed.
			request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, ex, WebRequest.SCOPE_REQUEST);
		}
		return createResponseEntity(ex, problemDetail, request);
	}

	/**
	 * Create the {@link ResponseEntity} to use from the given body, headers, and
	 * statusCode. Subclasses can override this method to inspect and possibly modify the
	 * body, headers, or statusCode, for example, to re-create an instance of
	 * {@link ProblemDetail} as an extension of {@link ProblemDetail}.
	 * @param ex the exception to handle
	 * @param problemDetail the RFC 9457 problem detail instance
	 * @param request the current request
	 * @return the {@code ResponseEntity} instance to use
	 */
	protected ResponseEntity<Object> createResponseEntity(Exception ex, @Nullable ProblemDetail problemDetail,
			WebRequest request) {

		HttpHeaders headers = (ex instanceof ErrorResponse errorResponse) ? errorResponse.getHeaders()
				: new HttpHeaders();
		int statusCode = (problemDetail != null) ? problemDetail.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR.value();
		return new ResponseEntity<>(problemDetail, headers, statusCode);
	}

}
