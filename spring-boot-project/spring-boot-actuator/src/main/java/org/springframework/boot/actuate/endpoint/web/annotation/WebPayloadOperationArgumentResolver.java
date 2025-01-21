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

package org.springframework.boot.actuate.endpoint.web.annotation;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.boot.actuate.endpoint.OperationArgumentResolver;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DataBinder;

/**
 * An {@link OperationArgumentResolver} for types annotated with
 * {@link WebPayload @WebPayload}.
 * <p>
 * This will bind the HTTP request payload, deserialized as a {@code Map}, to the given
 * type using a {@link DataBinder}.
 *
 * @author Brian Clozel
 * @since 3.5.0
 */
public class WebPayloadOperationArgumentResolver implements OperationArgumentResolver {

	private static final Log logger = LogFactory.getLog(WebPayloadOperationArgumentResolver.class);

	private final Map<String, Object> arguments;

	private final Consumer<DataBinder> dataBinderConfigurer;

	/**
	 * Create a new {@link WebPayloadOperationArgumentResolver} instance.
	 * @param arguments request payload deserialized as a {@code Map}
	 * @param dataBinderConfigurer configurer for the {@link DataBinder} instance
	 */
	public WebPayloadOperationArgumentResolver(Map<String, Object> arguments,
			Consumer<DataBinder> dataBinderConfigurer) {
		this.arguments = arguments;
		this.dataBinderConfigurer = dataBinderConfigurer;
	}

	/**
	 * Create a new {@link WebPayloadOperationArgumentResolver} instance.
	 * @param arguments request payload deserialized as a {@code Map}
	 */
	public WebPayloadOperationArgumentResolver(Map<String, Object> arguments) {
		this(arguments, (dataBinder) -> {
		});
	}

	@Override
	public boolean canResolve(Class<?> type) {
		return AnnotatedElementUtils.hasAnnotation(type, WebPayload.class);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T resolve(Class<T> type) {
		DataBinder dataBinder = new DataBinder(null);
		dataBinder.setTargetType(ResolvableType.forClass(type));
		dataBinder.setConversionService(new DefaultFormattingConversionService());
		this.dataBinderConfigurer.accept(dataBinder);
		dataBinder.construct(new MapValueResolver(this.arguments));
		BindingResult bindingResult = dataBinder.getBindingResult();
		if (!bindingResult.hasErrors()) {
			dataBinder.bind(new MutablePropertyValues(this.arguments));
		}
		if (bindingResult.hasErrors()) {
			logger.error("Could not bind payload to " + type + ": " + bindingResult.getAllErrors());
			throw new IllegalArgumentException(
					"Could not bind HTTP payload to operation argument: " + type.getSimpleName());
		}
		return (T) bindingResult.getTarget();
	}

	private record MapValueResolver(Map<String, Object> arguments) implements DataBinder.ValueResolver {

		@Override
		public Object resolveValue(String name, Class<?> type) {
			return this.arguments.get(name);
		}

		@Override
		public Set<String> getNames() {
			return this.arguments.keySet();
		}
	}

}
