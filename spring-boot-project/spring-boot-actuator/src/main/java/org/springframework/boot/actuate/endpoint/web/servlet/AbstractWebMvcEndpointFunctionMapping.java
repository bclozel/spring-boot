/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.boot.actuate.endpoint.web.servlet;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.springframework.boot.actuate.endpoint.ApiVersion;
import org.springframework.boot.actuate.endpoint.InvalidEndpointRequestException;
import org.springframework.boot.actuate.endpoint.InvocationContext;
import org.springframework.boot.actuate.endpoint.ProducibleOperationArgumentResolver;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.web.EndpointMapping;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.WebOperation;
import org.springframework.boot.actuate.endpoint.web.WebOperationRequestPredicate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.function.support.RouterFunctionMapping;

/**
 * A custom {@link RouterFunctionMapping} that makes {@link ExposableWebEndpoint web
 * endpoints} available over HTTP using Spring MVC functional handlers.
 *
 * @author Brian Clozel
 * @since 2.6.0
 */
public abstract class AbstractWebMvcEndpointFunctionMapping extends RouterFunctionMapping {

	static final MediaType DEFAULT_MEDIATYPE = MediaType
			.parseMediaType(ApiVersion.LATEST.getProducedMimeType().toString());

	private final EndpointMapping endpointMapping;

	private final Collection<ExposableWebEndpoint> endpoints;

	private final EndpointMediaTypes endpointMediaTypes;

	private final boolean shouldRegisterLinksMapping;

	private final CorsConfiguration corsConfiguration;

	public AbstractWebMvcEndpointFunctionMapping(EndpointMapping endpointMapping,
			Collection<ExposableWebEndpoint> endpoints, EndpointMediaTypes endpointMediaTypes,
			boolean shouldRegisterLinksMapping, CorsConfiguration corsConfiguration) {
		this.endpointMapping = endpointMapping;
		this.endpoints = endpoints;
		this.endpointMediaTypes = endpointMediaTypes;
		this.shouldRegisterLinksMapping = shouldRegisterLinksMapping;
		this.corsConfiguration = corsConfiguration;
		createRouterFunction();
		setOrder(-100);
	}

	private void createRouterFunction() {
		RouterFunctions.Builder root = RouterFunctions.route()
				.nest(RequestPredicates.path(this.endpointMapping.getPath()), (builder) -> {
					for (ExposableWebEndpoint endpoint : this.endpoints) {
						for (WebOperation operation : endpoint.getOperations()) {
							registerOperation(builder, endpoint, operation);
						}
					}
					if (this.shouldRegisterLinksMapping) {
						registerLinksMapping(builder);
					}
					builder.route(RequestPredicates.all(), (request) -> ServerResponse.notFound().build());
				});
		setRouterFunction(root.build());
	}

	private void registerOperation(RouterFunctions.Builder builder, ExposableWebEndpoint endpoint,
			WebOperation operation) {
		OperationHandler operationHandler = new FunctionalOperationHandler(operation);
		operationHandler = wrapServletWebOperation(endpoint, operation, operationHandler);
		RequestPredicate predicate = buildRequestPredicate(operation);
		builder.route(predicate, operationHandler);
	}

	private RequestPredicate buildRequestPredicate(WebOperation operation) {
		WebOperationRequestPredicate opPredicate = operation.getRequestPredicate();
		HttpMethod method = HttpMethod.resolve(opPredicate.getHttpMethod().name());
		MediaType[] produces = opPredicate.getProduces().stream().map(MediaType::parseMediaType)
				.toArray(MediaType[]::new);
		MediaType[] consumes = opPredicate.getConsumes().stream().map(MediaType::parseMediaType)
				.toArray(MediaType[]::new);
		RequestPredicate reqPredicate = RequestPredicates.method(method)
				.and(RequestPredicates.path(opPredicate.getPath()));
		if (produces.length > 0) {
			reqPredicate = reqPredicate.and(RequestPredicates.accept(produces));
		}
		if (consumes.length > 0) {
			reqPredicate = reqPredicate.and(RequestPredicates.contentType(consumes));
		}
		return reqPredicate;
	}

	protected static MediaType selectResponseMediaType(ServerRequest request, List<MediaType> supportedMediaTypes) {
		List<MediaType> accept = request.headers().accept();
		accept.sort(MediaType.QUALITY_VALUE_COMPARATOR);
		for (MediaType accepted : accept) {
			for (MediaType supported : supportedMediaTypes) {
				if (accepted.isCompatibleWith(supported)) {
					return supported;
				}
			}
		}
		return DEFAULT_MEDIATYPE;
	}

	/**
	 * Hook point that allows subclasses to wrap the {@link OperationHandler} before it's
	 * called. Allows additional features, such as security, to be added.
	 * @param endpoint the source endpoint
	 * @param operation the source operation
	 * @param operationHandler the functional web operation to wrap
	 * @return a wrapped functional web operation
	 */
	protected OperationHandler wrapServletWebOperation(ExposableWebEndpoint endpoint, WebOperation operation,
			OperationHandler operationHandler) {
		return operationHandler;
	}

	private void registerLinksMapping(RouterFunctions.Builder builder) {
		builder.route(RequestPredicates.method(HttpMethod.GET).and(RequestPredicates.path("")),
				getLinksHandler(this.endpointMediaTypes));
	}

	@Override
	protected boolean hasCorsConfigurationSource(Object handler) {
		return this.corsConfiguration != null;
	}

	@Override
	protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {
		return this.corsConfiguration;
	}

	/**
	 * Return the Handler providing actuator links at the root endpoint.
	 * @param endpointMediaTypes media types consumed and produced by the endpoints
	 * @return the links handler
	 */
	protected abstract LinksHandler getLinksHandler(EndpointMediaTypes endpointMediaTypes);

	/**
	 * Handler providing actuator links at the root endpoint.
	 */
	@FunctionalInterface
	protected interface LinksHandler extends HandlerFunction<ServerResponse> {

	}

	/**
	 * A functional web operation that can be handled by Spring MVC.
	 */
	@FunctionalInterface
	protected interface OperationHandler extends HandlerFunction<ServerResponse> {

	}

	static final class FunctionalOperationHandler implements OperationHandler {

		private final ParameterizedTypeReference<Map<String, String>> STRING_MAP = new ParameterizedTypeReference<Map<String, String>>() {
		};

		private final WebOperation operation;

		private final List<MediaType> supportedMediaTypes;

		FunctionalOperationHandler(WebOperation operation) {
			this.operation = operation;
			this.supportedMediaTypes = operation.getRequestPredicate().getProduces().stream()
					.map(MediaType::parseMediaType).collect(Collectors.toList());
		}

		@Override
		public ServerResponse handle(ServerRequest request) throws Exception {
			MediaType selectedMediaType = selectResponseMediaType(request, this.supportedMediaTypes);
			FunctionSecurityContext securityContext = new FunctionSecurityContext(request);
			Map<String, Object> arguments = getArguments(request);
			InvocationContext invocationContext = new InvocationContext(securityContext, arguments,
					new ProducibleOperationArgumentResolver(() -> request.headers().header("Accept")));
			try {
				return handleResult(this.operation.invoke(invocationContext), request, selectedMediaType);
			}
			catch (InvalidEndpointRequestException ex) {
				throw new BadOperationRequestException(ex.getReason());
			}
		}

		private Map<String, Object> getArguments(ServerRequest request) {
			Map<String, Object> arguments = new LinkedHashMap<>(request.pathVariables());
			arguments.putAll(request.params());
			String matchAllRemainingPathSegmentsVariable = this.operation.getRequestPredicate()
					.getMatchAllRemainingPathSegmentsVariable();
			if (matchAllRemainingPathSegmentsVariable != null) {
				arguments.put(matchAllRemainingPathSegmentsVariable,
						tokenizePathSegments((String) arguments.get(matchAllRemainingPathSegmentsVariable)));
			}
			try {
				if (HttpMethod.POST.equals(request.method())) {
					arguments.putAll(request.body(this.STRING_MAP));
				}
			}
			catch (Throwable ex) {
			}
			return arguments;
		}

		private String[] tokenizePathSegments(String path) {
			String[] segments = StringUtils.tokenizeToStringArray(path, AntPathMatcher.DEFAULT_PATH_SEPARATOR, false,
					true);
			for (int i = 0; i < segments.length; i++) {
				if (segments[i].contains("%")) {
					segments[i] = StringUtils.uriDecode(segments[i], StandardCharsets.UTF_8);
				}
			}
			return segments;
		}

		private ServerResponse handleResult(Object result, ServerRequest request, MediaType selectedMediaType) {
			if (result == null) {
				return (request.method() != HttpMethod.GET) ? ServerResponse.noContent().build()
						: ServerResponse.notFound().build();
			}
			int responseStatus = WebEndpointResponse.STATUS_OK;
			Object body = null;
			if (!(result instanceof WebEndpointResponse)) {
				body = result;
			}
			else {
				WebEndpointResponse<?> response = (WebEndpointResponse<?>) result;
				responseStatus = response.getStatus();
				body = response.getBody();
			}
			if (body == null) {
				return ServerResponse.status(responseStatus).build();
			}
			if (body instanceof Resource) {
				Resource resource = (Resource) body;
				List<HttpRange> httpRanges = request.headers().range();
				if (!httpRanges.isEmpty()) {
					return ServerResponse.status(HttpStatus.PARTIAL_CONTENT)
							.contentType(MediaType.APPLICATION_OCTET_STREAM)
							.body(HttpRange.toResourceRegions(httpRanges, resource),
									new ParameterizedTypeReference<List<ResourceRegion>>() {
									});
				}
			}
			return ServerResponse.status(responseStatus).contentType(selectedMediaType).body(body);
		}

	}

	@ResponseStatus(code = HttpStatus.BAD_REQUEST)
	private static class BadOperationRequestException extends RuntimeException {

		BadOperationRequestException(String message) {
			super(message);
		}

	}

	private static final class FunctionSecurityContext implements SecurityContext {

		private final ServerRequest request;

		FunctionSecurityContext(ServerRequest request) {
			this.request = request;
		}

		@Override
		public Principal getPrincipal() {
			return this.request.principal().orElse(null);
		}

		@Override
		public boolean isUserInRole(String role) {
			return this.request.servletRequest().isUserInRole(role);
		}

	}

}
