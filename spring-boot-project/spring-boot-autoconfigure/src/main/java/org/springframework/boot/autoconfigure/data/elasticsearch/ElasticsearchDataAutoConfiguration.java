/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.boot.autoconfigure.data.elasticsearch;

import org.elasticsearch.client.Client;
import org.elasticsearch.client.RestHighLevelClient;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.elasticsearch.rest.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.config.ElasticsearchConfigurationSupport;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Spring Data's Elasticsearch
 * support.
 * <p>
 * Registers an {@link ElasticsearchTemplate} if no other bean of the same type is
 * configured.
 *
 * @author Brian Clozel
 * @author Artur Konczak
 * @author Mohsin Husen
 * @see EnableElasticsearchRepositories
 * @since 1.1.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ Client.class, EnableElasticsearchRepositories.class })
@AutoConfigureAfter({ ElasticsearchAutoConfiguration.class,
		RestClientAutoConfiguration.class })
public class ElasticsearchDataAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBean(Client.class)
	static class TransportClientConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public ElasticsearchTemplate elasticsearchTemplate(Client client,
				ElasticsearchConverter converter) {
			try {
				return new ElasticsearchTemplate(client, converter);
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}

		@Bean
		@ConditionalOnMissingBean
		public ElasticsearchConverter elasticsearchConverter(
				SimpleElasticsearchMappingContext mappingContext) {
			return new MappingElasticsearchConverter(mappingContext);
		}

		@Bean
		@ConditionalOnMissingBean
		public SimpleElasticsearchMappingContext mappingContext() {
			return new SimpleElasticsearchMappingContext();
		}

	}

	@Configuration
	@ConditionalOnClass(RestHighLevelClient.class)
	@ConditionalOnMissingBean(Client.class)
	@ConditionalOnBean(RestHighLevelClient.class)
	static class RestHighLevelClientAutoConfiguration
			extends ElasticsearchConfigurationSupport {

		@Bean
		@ConditionalOnMissingBean
		public ElasticsearchRestTemplate elasticsearchTemplate(
				RestHighLevelClient restHighLevelClient) {
			return new ElasticsearchRestTemplate(restHighLevelClient,
					elasticsearchConverter(), resultsMapper());
		}

	}

}
