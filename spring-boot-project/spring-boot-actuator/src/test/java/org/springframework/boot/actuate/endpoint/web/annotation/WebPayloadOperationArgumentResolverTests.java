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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link WebPayloadOperationArgumentResolver}.
 */
class WebPayloadOperationArgumentResolverTests {

	@Test
	void shouldOnlySupportAnnotatedTypes() {
		WebPayloadOperationArgumentResolver resolver = new WebPayloadOperationArgumentResolver(Collections.emptyMap());
		assertThat(resolver.canResolve(NotPayload.class)).isFalse();
		assertThat(resolver.canResolve(ConstructorPayload.class)).isTrue();
		assertThat(resolver.canResolve(RecordPayload.class)).isTrue();
		assertThat(resolver.canResolve(ListPayload.class)).isTrue();
	}

	@Test
	void shouldRejectWhenInvalidPayload() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> resolve(Map.of("name", "payload", "project", "Spring Boot"), ConstructorPayload.class))
			.withMessage("Could not bind HTTP payload to operation argument: ConstructorPayload");
	}

	@Test
	void shouldBindConstructorPayload() {
		ConstructorPayload payload = resolve(Map.of("name", "payload", "project", "Spring Boot", "isSupported", "true"),
				ConstructorPayload.class);
		assertThat(payload.isSupported).isTrue();
		assertThat(payload.name).isEqualTo("payload");
		assertThat(payload.project).isEqualTo("Spring Boot");
	}

	@Test
	void shouldBindRecordPayload() {
		RecordPayload payload = resolve(Map.of("name", "payload", "project", "Spring Boot"), RecordPayload.class);
		assertThat(payload.name).isEqualTo("payload");
		assertThat(payload.project).isEqualTo("Spring Boot");
	}

	@Test
	void shouldBindListPropertyPayload() {
		ListPayload payload = resolve(Map.of("names", List.of("Spring", "Boot")), ListPayload.class);
		assertThat(payload.names).contains("Spring", "Boot");
	}

	private <T> T resolve(Map<String, Object> arguments, Class<T> type) {
		return new WebPayloadOperationArgumentResolver(arguments).resolve(type);
	}

	static class NotPayload {

	}

	@WebPayload
	record RecordPayload(String name, String project) {

	}

	@WebPayload
	static class ConstructorPayload {

		final String name;

		final String project;

		final boolean isSupported;

		ConstructorPayload(String name, String project, boolean isSupported) {
			this.name = name;
			this.project = project;
			this.isSupported = isSupported;
		}

	}

	@WebPayload
	static class ListPayload {

		private List<String> names;

		public List<String> getNames() {
			return this.names;
		}

		public void setNames(List<String> names) {
			this.names = names;
		}

	}

}
