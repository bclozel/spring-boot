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

package org.springframework.boot.web.reactive.error;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ErrorAttributes}.
 */
class ErrorAttributesTests {

	@Test
	void shouldReturnErrorAsTitle() {
		ProblemDetail problemDetails = asProblemDetails(Map.of("error", "test"));
		assertThat(problemDetails.getTitle()).isEqualTo("test");
	}

	@Test
	void shouldReturnStatusAsStatus() {
		ProblemDetail problemDetails = asProblemDetails(Map.of("status", 404));
		assertThat(problemDetails.getStatus()).isEqualTo(404);
	}

	@Test
	void shouldReturnMessageAsDetail() {
		ProblemDetail problemDetails = asProblemDetails(Map.of("message", "test message"));
		assertThat(problemDetails.getDetail()).isEqualTo("test message");
	}

	@Test
	void shouldReturnPathAsInstance() {
		ProblemDetail problemDetails = asProblemDetails(Map.of("path", "/test"));
		assertThat(problemDetails.getInstance().toString()).isEqualTo("/test");
	}

	@Test
	void shouldReturnOtherAsExtension() {
		ProblemDetail problemDetails = asProblemDetails(Map.of("exception", "IllegalStateException"));
		assertThat(problemDetails.getProperties()).containsEntry("exception", "IllegalStateException");
	}

	private ProblemDetail asProblemDetails(Map<String, Object> attributes) {
		TestErrorAttributes errorAttributes = new TestErrorAttributes(attributes);
		return errorAttributes.asProblemDetail(MockServerRequest.builder().build(), ErrorAttributeOptions.defaults());
	}

	static class TestErrorAttributes implements ErrorAttributes {

		final Map<String, Object> errorAttributes;

		TestErrorAttributes(Map<String, Object> errorAttributes) {
			this.errorAttributes = errorAttributes;
		}

		@Override
		public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
			return this.errorAttributes;
		}

		@Override
		public Throwable getError(ServerRequest request) {
			return null;
		}

		@Override
		public void storeErrorInformation(Throwable error, ServerWebExchange exchange) {

		}

	}

}
