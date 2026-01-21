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

import java.time.Clock;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfigurationTests.OrderedControllerAdviceBeansConfiguration.HighestOrderedControllerAdvice;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfigurationTests.OrderedControllerAdviceBeansConfiguration.LowestOrderedControllerAdvice;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.handler.DispatcherServletWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ErrorMvcAutoConfiguration}.
 *
 * @author Brian Clozel
 * @author Scott Frederick
 */
@ExtendWith(OutputCaptureExtension.class)
class ErrorMvcAutoConfigurationTests {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(DispatcherServletAutoConfiguration.class, ErrorMvcAutoConfiguration.class));

	@Test
	void renderContainsViewWithExceptionDetails() {
		this.contextRunner.run((context) -> {
			View errorView = context.getBean("error", View.class);
			ErrorAttributes errorAttributes = context.getBean(ErrorAttributes.class);
			DispatcherServletWebRequest webRequest = createWebRequest(new IllegalStateException("Exception message"),
					false);
			HttpServletResponse response = webRequest.getResponse();
			assertThat(response).isNotNull();
			errorView.render(errorAttributes.getErrorAttributes(webRequest, withAllOptions()), webRequest.getRequest(),
					response);
			assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
			String responseString = ((MockHttpServletResponse) response).getContentAsString();
			assertThat(responseString).contains(
					"<p>This application has no explicit mapping for /error, so you are seeing this as a fallback.</p>")
				.contains("<div>Exception message</div>")
				.contains("<div style='white-space:pre-wrap;'>java.lang.IllegalStateException");
		});
	}

	@Test
	void renderCanUseJavaTimeTypeAsTimestamp() { // gh-23256
		this.contextRunner.run((context) -> {
			View errorView = context.getBean("error", View.class);
			ErrorAttributes errorAttributes = context.getBean(ErrorAttributes.class);
			DispatcherServletWebRequest webRequest = createWebRequest(new IllegalStateException("Exception message"),
					false);
			Map<String, @Nullable Object> attributes = errorAttributes.getErrorAttributes(webRequest, withAllOptions());
			attributes.put("timestamp", Clock.systemUTC().instant());
			HttpServletResponse response = webRequest.getResponse();
			assertThat(response).isNotNull();
			errorView.render(attributes, webRequest.getRequest(), response);
			assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
			String responseString = ((MockHttpServletResponse) response).getContentAsString();
			assertThat(responseString).contains("This application has no explicit mapping for /error");
		});
	}

	@Test
	void renderWhenAlreadyCommittedLogsMessage(CapturedOutput output) {
		this.contextRunner.run((context) -> {
			View errorView = context.getBean("error", View.class);
			ErrorAttributes errorAttributes = context.getBean(ErrorAttributes.class);
			DispatcherServletWebRequest webRequest = createWebRequest(new IllegalStateException("Exception message"),
					true);
			HttpServletResponse response = webRequest.getResponse();
			assertThat(response).isNotNull();
			errorView.render(errorAttributes.getErrorAttributes(webRequest, withAllOptions()), webRequest.getRequest(),
					response);
			assertThat(output).contains("Cannot render error page for request [/path] "
					+ "and exception [Exception message] as the response has "
					+ "already been committed. As a result, the response may have the wrong status code.");
		});
	}

	@Test
	void problemDetailsDisabledByDefault() {
		this.contextRunner.run((context) -> assertThat(context).doesNotHaveBean(DefaultExceptionHandler.class));
	}

	@Test
	void problemDetailsEnabledAddsExceptionHandler() {
		this.contextRunner.withPropertyValues("spring.mvc.problemdetails.enabled:true")
			.run((context) -> assertThat(context).hasSingleBean(DefaultExceptionHandler.class));
	}

	@Test
	void problemDetailsExceptionHandlerDoesNotPreventProxying() {
		this.contextRunner.withUserConfiguration(AopConfiguration.class)
			.withBean(ExceptionHandlerInterceptor.class)
			.withPropertyValues("spring.mvc.problemdetails.enabled:true")
			.run((context) -> assertThat(context).getBean(DefaultExceptionHandler.class)
				.matches(AopUtils::isCglibProxy));
	}

	@Test
	void problemDetailsBacksOffWhenExceptionHandler() {
		this.contextRunner.withPropertyValues("spring.mvc.problemdetails.enabled:true")
			.withUserConfiguration(CustomExceptionHandlerConfiguration.class)
			.run((context) -> assertThat(context).doesNotHaveBean(DefaultExceptionHandler.class)
				.hasSingleBean(CustomExceptionHandler.class));
	}

	@Test
	void problemDetailsExceptionHandlerIsOrderedAt0() {
		this.contextRunner.withPropertyValues("spring.mvc.problemdetails.enabled:true")
			.withUserConfiguration(OrderedControllerAdviceBeansConfiguration.class)
			.run((context) -> assertThat(
					ControllerAdviceBean.findAnnotatedBeans(context).stream().map(ControllerAdviceBean::getBeanType))
				.asInstanceOf(InstanceOfAssertFactories.list(Class.class))
				.containsExactly(HighestOrderedControllerAdvice.class, DefaultExceptionHandler.class,
						OrderedControllerAdviceBeansConfiguration.LowestOrderedControllerAdvice.class));
	}

	private DispatcherServletWebRequest createWebRequest(Exception ex, boolean committed) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/path");
		MockHttpServletResponse response = new MockHttpServletResponse();
		DispatcherServletWebRequest webRequest = new DispatcherServletWebRequest(request, response);
		webRequest.setAttribute("jakarta.servlet.error.exception", ex, RequestAttributes.SCOPE_REQUEST);
		webRequest.setAttribute("jakarta.servlet.error.request_uri", "/path", RequestAttributes.SCOPE_REQUEST);
		response.setCommitted(committed);
		response.setOutputStreamAccessAllowed(!committed);
		response.setWriterAccessAllowed(!committed);
		return webRequest;
	}

	private ErrorAttributeOptions withAllOptions() {
		return ErrorAttributeOptions.of(Include.values());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAspectJAutoProxy(proxyTargetClass = true)
	static class AopConfiguration {

	}

	@Aspect
	static class ExceptionHandlerInterceptor {

		@AfterReturning(pointcut = "@annotation(org.springframework.web.bind.annotation.ExceptionHandler)",
				returning = "returnValue")
		void exceptionHandlerIntercept(JoinPoint joinPoint, Object returnValue) {
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomExceptionHandlerConfiguration {

		@Bean
		CustomExceptionHandler customExceptionHandler() {
			return new CustomExceptionHandler();
		}

	}

	@ControllerAdvice
	static class CustomExceptionHandler extends ResponseEntityExceptionHandler {

	}

	@Configuration(proxyBeanMethods = false)
	@Import({ LowestOrderedControllerAdvice.class, HighestOrderedControllerAdvice.class })
	static class OrderedControllerAdviceBeansConfiguration {

		@ControllerAdvice
		@Order
		static class LowestOrderedControllerAdvice {

		}

		@ControllerAdvice
		@Order(Ordered.HIGHEST_PRECEDENCE)
		static class HighestOrderedControllerAdvice {

		}

	}

}
