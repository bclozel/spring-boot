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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.util.Assert;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Abstract base class for error {@link HandlerFunction} implementations.
 *
 * @author Brian Clozel
 * @since 1.3.0
 * @see ErrorAttributes
 */
public abstract class AbstractErrorHandlerFunctions {

	private final ErrorAttributes errorAttributes;

	private final List<ErrorViewResolver> errorViewResolvers;

	public AbstractErrorHandlerFunctions(ErrorAttributes errorAttributes) {
		this(errorAttributes, null);
	}

	public AbstractErrorHandlerFunctions(ErrorAttributes errorAttributes, List<ErrorViewResolver> errorViewResolvers) {
		Assert.notNull(errorAttributes, "ErrorAttributes must not be null");
		this.errorAttributes = errorAttributes;
		this.errorViewResolvers = sortErrorViewResolvers(errorViewResolvers);
	}

	private List<ErrorViewResolver> sortErrorViewResolvers(List<ErrorViewResolver> resolvers) {
		List<ErrorViewResolver> sorted = new ArrayList<>();
		if (resolvers != null) {
			sorted.addAll(resolvers);
			AnnotationAwareOrderComparator.sortIfNecessary(sorted);
		}
		return sorted;
	}

	protected ProblemDetail getProblemDetail(ServerRequest request, ErrorAttributeOptions options) {
		return this.errorAttributes.asProblemDetail(request, options);
	}

	protected Optional<Throwable> getError(ServerRequest request) {
		return this.errorAttributes.getError(request);
	}

	protected boolean getBooleanParameter(ServerRequest request, String parameterName) {
		String parameter = request.param(parameterName).orElse("false");
		return !"false".equalsIgnoreCase(parameter);
	}

	/**
	 * Resolve any specific error views. By default this method delegates to
	 * {@link ErrorViewResolver ErrorViewResolvers}.
	 * @param request the request
	 * @param status the HTTP status
	 * @param model the suggested model
	 * @return a specific {@link ModelAndView} or {@code null} if the default should be
	 * used
	 */
	protected ModelAndView resolveErrorView(ServerRequest request, HttpStatusCode status, Map<String, Object> model) {
		for (ErrorViewResolver resolver : this.errorViewResolvers) {
			ModelAndView modelAndView = resolver.resolveErrorView(request.servletRequest(), status, model);
			if (modelAndView != null) {
				return modelAndView;
			}
		}
		return null;
	}

}
