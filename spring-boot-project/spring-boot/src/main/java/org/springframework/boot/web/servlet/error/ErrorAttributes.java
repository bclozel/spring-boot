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

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Provides access to error attributes which can be logged or presented to the user.
 *
 * @author Phillip Webb
 * @author Scott Frederick
 * @author Brian Clozel
 * @since 2.0.0
 * @see DefaultErrorAttributes
 */
public interface ErrorAttributes {

	/**
	 * Returns a {@link Map} of the error attributes. The map can be used as the model of
	 * an error page {@link ModelAndView}, or returned as a
	 * {@link ResponseBody @ResponseBody}.
	 * @param webRequest the source request
	 * @param options options for error attribute contents
	 * @return a map of error attributes
	 * @since 2.3.0
	 */
	default Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
		return Collections.emptyMap();
	}

	/**
	 * Returns a {@link ProblemDetail} instance describing the error. This can be used as
	 * a model entry of an error page {@link ModelAndView}, or wrapped in a
	 * {@link ErrorResponseException} and written as the response body.
	 * @param request the source request
	 * @param options options for error attribute contents
	 * @return a problem details instance describing the error
	 * @since 3.5.0
	 */
	default ProblemDetail asProblemDetail(ServerRequest request, ErrorAttributeOptions options) {
		ServletWebRequest webRequest = new ServletWebRequest(request.servletRequest());
		Map<String, Object> errorAttributes = getErrorAttributes(webRequest, options);
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		for (String name : errorAttributes.keySet()) {
			switch (name) {
				case "error":
					problemDetail.setTitle((String) errorAttributes.get(name));
					break;
				case "status":
					problemDetail.setStatus((Integer) errorAttributes.get(name));
					break;
				case "message":
					problemDetail.setDetail((String) errorAttributes.get(name));
					break;
				case "path":
					problemDetail.setInstance(URI.create((String) errorAttributes.get(name)));
				default:
					problemDetail.setProperty(name, errorAttributes.get(name));
			}
		}
		return problemDetail;
	}

	/**
	 * Return the underlying cause of the error or {@code null} if the error cannot be
	 * extracted.
	 * @param request the source request
	 * @return the {@link Exception} that caused the error or {@code null}
	 * @since 3.5.0
	 */
	Optional<Throwable> getError(ServerRequest request);

	/**
	 * Return the underlying cause of the error or {@code null} if the error cannot be
	 * extracted.
	 * @param webRequest the source request
	 * @return the {@link Exception} that caused the error or {@code null}
	 */
	Throwable getError(WebRequest webRequest);

}
