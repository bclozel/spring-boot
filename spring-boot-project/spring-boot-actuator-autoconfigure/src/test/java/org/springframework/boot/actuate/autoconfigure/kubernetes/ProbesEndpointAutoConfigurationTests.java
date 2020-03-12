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

package org.springframework.boot.actuate.autoconfigure.kubernetes;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.kubernetes.ProbesEndpoint;
import org.springframework.boot.actuate.kubernetes.ProbesEndpointWebExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kubernetes.ApplicationStateProvider;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ProbesEndpointAutoConfiguration}
 *
 * @author Brian Clozel
 */
class ProbesEndpointAutoConfigurationTests {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withBean(ApplicationStateProvider.class, ApplicationStateProvider::new)
			.withPropertyValues("management.endpoints.web.exposure.include=probes")
			.withConfiguration(AutoConfigurations.of(ProbesEndpointAutoConfiguration.class));

	@Test
	void disabledWithoutKubernetes() {
		this.contextRunner.run((context) -> assertThat(context).doesNotHaveBean(ProbesEndpoint.class)
				.doesNotHaveBean(ProbesEndpointWebExtension.class));
	}

	@Test
	void enabledWithKubernetes() {
		this.contextRunner.withInitializer(enableKubernetes()).run((context) -> assertThat(context)
				.hasSingleBean(ProbesEndpoint.class).hasSingleBean(ProbesEndpointWebExtension.class));
	}

	private ApplicationContextInitializer<ConfigurableApplicationContext> enableKubernetes() {
		return (context) -> {
			Map<String, Object> environmentVariables = new HashMap<>();
			environmentVariables.put("KUBERNETES_SERVICE_HOST", "---");
			environmentVariables.put("KUBERNETES_SERVICE_PORT", "8080");
			ConfigurableEnvironment environment = context.getEnvironment();
			PropertySource<?> propertySource = new SystemEnvironmentPropertySource(
					StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environmentVariables);
			environment.getPropertySources().addFirst(propertySource);
		};
	}

}
