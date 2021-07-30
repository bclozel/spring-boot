/*
 * Copyright 2012-2021 the original author or authors.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.ApiVersion;
import org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver;
import org.springframework.boot.actuate.endpoint.web.EndpointMapping;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.Link;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * A custom {@link HandlerMapping} that makes web endpoints available over HTTP using
 * Spring MVC.
 *
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @author Brian Clozel
 * @since 2.0.0
 */
public class WebMvcEndpointHandlerMapping extends AbstractWebMvcEndpointFunctionMapping {

	private final EndpointLinksResolver linksResolver;

	/**
	 * Creates a new {@code WebMvcEndpointHandlerMapping} instance that provides mappings
	 * for the given endpoints.
	 * @param endpointMapping the base mapping for all endpoints
	 * @param endpoints the web endpoints
	 * @param endpointMediaTypes media types consumed and produced by the endpoints
	 * @param corsConfiguration the CORS configuration for the endpoints or {@code null}
	 * @param linksResolver resolver for determining links to available endpoints
	 * @param shouldRegisterLinksMapping whether the links endpoint should be registered
	 */
	public WebMvcEndpointHandlerMapping(EndpointMapping endpointMapping, Collection<ExposableWebEndpoint> endpoints,
			EndpointMediaTypes endpointMediaTypes, CorsConfiguration corsConfiguration,
			EndpointLinksResolver linksResolver, boolean shouldRegisterLinksMapping) {
		super(endpointMapping, endpoints, endpointMediaTypes, shouldRegisterLinksMapping, corsConfiguration);
		// TODO configure message converters
		setMessageConverters(Arrays.asList(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(),
				new ResourceRegionHttpMessageConverter(), new ResourceHttpMessageConverter(),
				new ByteArrayHttpMessageConverter()));
		this.linksResolver = linksResolver;
	}

	@Override
	protected LinksHandler getLinksHandler(EndpointMediaTypes endpointMediaTypes) {
		return new WebMvcLinksHandler(endpointMediaTypes);
	}

	/**
	 * Handler for root endpoint providing links.
	 */
	class WebMvcLinksHandler implements LinksHandler {

		private final MediaType DEFAULT_MEDIATYPE = MediaType
				.parseMediaType(ApiVersion.LATEST.getProducedMimeType().toString());

		private final MediaType[] supportedMediaTypes;

		WebMvcLinksHandler(EndpointMediaTypes endpointMediaTypes) {
			this.supportedMediaTypes = endpointMediaTypes.getProduced().stream().map(MediaType::parseMediaType)
					.toArray(MediaType[]::new);
		}

		@Override
		public ServerResponse handle(ServerRequest request) throws Exception {
			Map<String, Map<String, Link>> links = Collections.singletonMap("_links",
					WebMvcEndpointHandlerMapping.this.linksResolver.resolveLinks(request.uri().toString()));
			MediaType responseMediaType = selectResponseMediaType(request);
			return ServerResponse.ok().contentType(responseMediaType).body(links);
		}

		private MediaType selectResponseMediaType(ServerRequest request) {
			List<MediaType> accept = request.headers().accept();
			accept.sort(MediaType.QUALITY_VALUE_COMPARATOR);
			for (MediaType accepted : accept) {
				for (MediaType supported : this.supportedMediaTypes) {
					if (accepted.isCompatibleWith(supported)) {
						return supported;
					}
				}
			}
			return this.DEFAULT_MEDIATYPE;
		}

	}

}
