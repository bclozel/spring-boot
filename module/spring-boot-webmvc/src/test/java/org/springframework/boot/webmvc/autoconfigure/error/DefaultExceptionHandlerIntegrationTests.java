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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.testsupport.classpath.resources.WithResource;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.AbstractView;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultExceptionHandler}.
 *
 * @author Brian Clozel
 */
@WithResource(name = "messages.properties", content = "bar.error.test.foo=my error message")
class DefaultExceptionHandlerIntegrationTests {

	private @Nullable ConfigurableApplicationContext context;

	@AfterEach
	void closeContext() {
		if (this.context != null) {
			this.context.close();
		}
	}

	private RestTestClient createClient() {
		assertThat(this.context).isNotNull();
		Integer port = this.context.getEnvironment().getProperty("local.server.port", Integer.class);
		assertThat(port).isNotNull();
		return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	private void load(String... arguments) {
		load(TestConfiguration.class, arguments);
	}

	private void load(Class<?> configuration, String... arguments) {
		List<String> args = new ArrayList<>();
		args.add("--server.port=0");
		args.add("--spring.mvc.problemdetails.enabled=true");
		if (arguments != null) {
			args.addAll(Arrays.asList(arguments));
		}
		this.context = SpringApplication.run(configuration, StringUtils.toStringArray(args));
	}

	private void assertProblemDetails(ResponseSpec responseSpec, String path, String status, String title,
			@Nullable Class<?> exception, @Nullable String message) {
		responseSpec.expectBody()
			.jsonPath("$.path")
			.isEqualTo(path)
			.jsonPath("$.status")
			.isEqualTo(status)
			.jsonPath("$.title")
			.isEqualTo(title);
		if (exception != null) {
			responseSpec.expectBody().jsonPath("$.exception").isEqualTo(exception.getName());
		}
		else {
			responseSpec.expectBody().jsonPath("$.exception").doesNotExist();
		}
		if (message != null) {
			responseSpec.expectBody().jsonPath("$.detail").isEqualTo(message);
		}
		else {
			responseSpec.expectBody().jsonPath("$.detail").doesNotExist();
		}

	}

	@Nested
	class MachineClientTests {

		@Test
		void testErrorDefault() {
			load();
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri("/?trace=true").exchange();
			assertProblemDetails(exchange, "/", "500", "Internal Server Error", null, null);
			exchange.expectBody().jsonPath("$.exception").doesNotExist();
			exchange.expectBody().jsonPath("$.trace").doesNotExist();
		}

		@Test
		void testErrorWithParamsTrue() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-stacktrace=on-param",
					"--spring.web.error.include-message=on-param");
			exceptionWithStackTraceAndMessage("?trace=true&message=true");
		}

		@Test
		void testErrorWithParamsFalse() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-stacktrace=on-param",
					"--spring.web.error.include-message=on-param");
			exceptionWithoutStackTraceAndMessage("?trace=false&message=false");
		}

		@Test
		void testErrorWithParamsAbsent() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-stacktrace=on-param",
					"--spring.web.error.include-message=on-param");
			exceptionWithoutStackTraceAndMessage("");
		}

		@Test
		void testErrorNeverParams() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-stacktrace=never",
					"--spring.web.error.include-message=never");
			exceptionWithoutStackTraceAndMessage("?trace=true&message=true");
		}

		@Test
		void testErrorAlwaysParams() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-stacktrace=always",
					"--spring.web.error.include-message=always");
			exceptionWithStackTraceAndMessage("?trace=false&message=false");
		}

		private void exceptionWithStackTraceAndMessage(String path) {
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri(path).exchange();

			assertProblemDetails(exchange, "/", "500", "Internal Server Error", IllegalStateException.class,
					"Expected!");
			exchange.expectBody().jsonPath("$.trace").isNotEmpty();
		}

		private void exceptionWithoutStackTraceAndMessage(String path) {
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri(path).exchange();
			assertProblemDetails(exchange, "/", "500", "Internal Server Error", IllegalStateException.class, null);
			exchange.expectBody().jsonPath("$.trace").doesNotExist();
		}

		@Test
		void testErrorAlwaysParamsWithoutMessage() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-message=always");
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri("/noMessage").exchange();
			assertProblemDetails(exchange, "/noMessage", "500", "Internal Server Error", IllegalStateException.class,
					"No message available");
		}

		@Test
		void testErrorForAnnotatedExceptionWithoutMessage() {
			load("--spring.web.error.include-exception=true");
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri("/annotated").exchange();
			assertProblemDetails(exchange, "/annotated", "400", "Bad Request",
					TestConfiguration.Errors.ExpectedException.class, null);
		}

		@Test
		void testErrorForAnnotatedExceptionWithMessage() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-message=always");
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri("/annotated").exchange();
			assertProblemDetails(exchange, "/annotated", "400", "Bad Request",
					TestConfiguration.Errors.ExpectedException.class, "Expected!");
		}

		@Test
		void testErrorForAnnotatedNoReasonExceptionWithoutMessage() {
			load("--spring.web.error.include-exception=true");
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri("/annotatedNoReason").exchange();
			assertProblemDetails(exchange, "/annotatedNoReason", "406", "Not Acceptable",
					TestConfiguration.Errors.NoReasonExpectedException.class, null);
		}

		@Test
		void testErrorForAnnotatedNoReasonExceptionWithMessage() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-message=always");
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri("/annotatedNoReason").exchange();
			assertProblemDetails(exchange, "/annotatedNoReason", "406", "Not Acceptable",
					TestConfiguration.Errors.NoReasonExpectedException.class, "Expected message");
		}

		@Test
		void testErrorForAnnotatedNoMessageExceptionWithMessage() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-message=always");
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri("/annotatedNoMessage").exchange();
			assertProblemDetails(exchange, "/annotatedNoMessage", "406", "Not Acceptable",
					TestConfiguration.Errors.NoReasonExpectedException.class, "No message available");
		}

		@Test
		void testBindingExceptionWithErrorsParamTrue() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-binding-errors=on-param");
			bindingExceptionWithErrors("?errors=true");
		}

		@Test
		void testBindingExceptionWithErrorsParamFalse() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-binding-errors=on-param");
			bindingExceptionWithoutErrors("?errors=false");
		}

		@Test
		void testBindingExceptionWithErrorsParamAbsent() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-binding-errors=on-param");
			bindingExceptionWithoutErrors("");
		}

		@Test
		void testBindingExceptionAlwaysErrors() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-binding-errors=always");
			bindingExceptionWithErrors("?errors=false");
		}

		@Test
		void testBindingExceptionNeverErrors() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-binding-errors=never");
			bindingExceptionWithoutErrors("?errors=true");
		}

		@Test
		void testBindingExceptionWithMessageParamTrue() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-message=on-param");
			bindingExceptionWithMessage("?message=true");
		}

		@Test
		void testBindingExceptionWithMessageParamFalse() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-message=on-param");
			bindingExceptionWithoutMessage("?message=false");
		}

		@Test
		void testBindingExceptionWithMessageParamAbsent() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-message=on-param");
			bindingExceptionWithoutMessage("");
		}

		@Test
		void testBindingExceptionAlwaysMessage() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-message=always");
			bindingExceptionWithMessage("?message=false");
		}

		@Test
		void testBindingExceptionNeverMessage() {
			load("--spring.web.error.include-exception=true", "--spring.web.error.include-message=never");
			bindingExceptionWithoutMessage("?message=true");
		}

		private void bindingExceptionWithErrors(String param) {
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri("/bind" + param).exchange();
			assertProblemDetails(exchange, "/bind", "400", "Bad Request", MethodArgumentNotValidException.class,
					"Invalid request content.");
			exchange.expectBody().jsonPath("$.errors").isNotEmpty();
		}

		private void bindingExceptionWithoutErrors(String param) {
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri("/bind" + param).exchange();
			assertProblemDetails(exchange, "/bind", "400", "Bad Request", MethodArgumentNotValidException.class,
					"Invalid request content.");
			exchange.expectBody().jsonPath("$.errors").doesNotExist();
		}

		private void bindingExceptionWithMessage(String param) {
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri("/bind" + param).exchange();
			assertProblemDetails(exchange, "/bind", "400", "Bad Request", MethodArgumentNotValidException.class,
					"Validation failed for object='test'. Error count: 1");
			exchange.expectBody().jsonPath("$.errors").doesNotExist();
		}

		private void bindingExceptionWithoutMessage(String param) {
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri("/bind" + param).exchange();
			assertProblemDetails(exchange, "/bind", "400", "Bad Request", MethodArgumentNotValidException.class,
					"Invalid request content.");
			exchange.expectBody().jsonPath("$.errors").doesNotExist();
		}

	}

	@Nested
	class BrowserTests {

		@Test
		void testErrorDefault() {
			load();
			RestTestClient client = createClient();
			ResponseSpec exchange = client.get().uri("/").accept(MediaType.TEXT_HTML).exchange();
			exchange.expectBody(String.class).isEqualTo("ERROR_BEAN");
		}

	}

	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@Documented
	@ImportAutoConfiguration({ TomcatServletWebServerAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
			WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
			ErrorMvcAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class,
			MessageSourceAutoConfiguration.class })
	private @interface MinimalWebConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@MinimalWebConfiguration
	public static class TestConfiguration {

		// For manual testing
		static void main(String[] args) {
			SpringApplication.run(TestConfiguration.class, args);
		}

		@Bean
		View error() {
			return new AbstractView() {

				@Override
				protected void renderMergedOutputModel(Map<String, Object> model, HttpServletRequest request,
						HttpServletResponse response) throws Exception {
					response.getWriter().write("ERROR_BEAN");
				}

			};
		}

		@RestController
		public static class Errors {

			public String getFoo() {
				return "foo";
			}

			@RequestMapping("/")
			String home() {
				throw new IllegalStateException("Expected!");
			}

			@RequestMapping("/noMessage")
			String noMessage() {
				throw new IllegalStateException();
			}

			@RequestMapping("/annotated")
			String annotated() {
				throw new ExpectedException();
			}

			@RequestMapping("/annotatedNoReason")
			String annotatedNoReason() {
				throw new NoReasonExpectedException("Expected message");
			}

			@RequestMapping("/annotatedNoMessage")
			String annotatedNoMessage() {
				throw new NoReasonExpectedException("");
			}

			@RequestMapping("/bind")
			String bind(@RequestAttribute(required = false) String foo) throws Exception {
				BindException error = new BindException(this, "test");
				error.rejectValue("foo", "bar.error");
				Method method = ReflectionUtils.findMethod(TestConfiguration.Errors.class, "bind", String.class);
				assertThat(method).isNotNull();
				Parameter fooParameter = method.getParameters()[0];
				throw new MethodArgumentNotValidException(MethodParameter.forParameter(fooParameter), error);
			}

			@PostMapping(path = "/bodyValidation", produces = "application/json")
			String bodyValidation(@Valid @RequestBody TestConfiguration.Errors.DummyBody body) {
				return body.content;
			}

			@RequestMapping(path = "/incompatibleType", produces = "text/plain")
			String incompatibleType() {
				throw new TestConfiguration.Errors.ExpectedException();
			}

			@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Expected!")
			@SuppressWarnings("serial")
			static class ExpectedException extends RuntimeException {

			}

			@ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
			@SuppressWarnings("serial")
			static class NoReasonExpectedException extends RuntimeException {

				NoReasonExpectedException(String message) {
					super(message);
				}

			}

			static class DummyBody {

				@NotNull
				@SuppressWarnings("NullAway.Init")
				private String content;

				String getContent() {
					return this.content;
				}

				void setContent(String content) {
					this.content = content;
				}

			}

		}

	}

}
