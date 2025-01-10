/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.boot.web.reactive.error;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * Provides access to error attributes which can be logged or presented to the user.
 *
 * @author Brian Clozel
 * @author Scott Frederick
 * @since 2.0.0
 * @see DefaultErrorAttributes
 */
public interface ErrorAttributes {

	/**
	 * Return a {@link Map} of the error attributes. The map can be used as the model of
	 * an error page, or returned as a {@link ServerResponse} body.
	 * @param request the source request
	 * @param options options for error attribute contents
	 * @return a map of error attributes
	 */
	default Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
		return Collections.emptyMap();
	}

	/**
	 * Returns a {@link ProblemDetail} instance describing the error. This can be used as
	 * the model of an error page, or returned as a {@link ServerResponse} body.
	 * @param request the source request
	 * @param options options for error attribute contents
	 * @return a problem details instance describing the error
	 * @since 3.5.0
	 */
	default ProblemDetail asProblemDetail(ServerRequest request, ErrorAttributeOptions options) {
		Map<String, Object> errorAttributes = getErrorAttributes(request, options);
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
	 * @param request the source ServerRequest
	 * @return the {@link Exception} that caused the error or {@code null}
	 */
	Throwable getError(ServerRequest request);

	/**
	 * Store the given error information in the current {@link ServerWebExchange}.
	 * @param error the {@link Exception} that caused the error
	 * @param exchange the source exchange
	 */
	void storeErrorInformation(Throwable error, ServerWebExchange exchange);

}
