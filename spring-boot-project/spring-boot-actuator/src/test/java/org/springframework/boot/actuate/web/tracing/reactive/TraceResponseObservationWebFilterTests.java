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

package org.springframework.boot.actuate.web.tracing.reactive;

import java.time.Duration;

import io.micrometer.observation.Observation;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TraceResponseObservationWebFilter}.
 *
 * @author Brian Clozel
 */
class TraceResponseObservationWebFilterTests {

	TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	TraceResponseObservationWebFilter filter = new TraceResponseObservationWebFilter();

	@Test
	void shouldWriteTraceHeaderWhenCurrentTrace() {
		configureTracingSupport(new SimpleTracer());
		ObservedExchange observedExchange = createObservedExchange();
		observedExchange.observation()
			.observe(() -> this.filter.filter(observedExchange.exchange(), new TestWebFilterChain())
				.block(Duration.ofSeconds(2)));
		assertThat(observedExchange.exchange().getResponse().getHeaders().getFirst("traceresponse"))
			.matches("00-[0-9a-f]+-[0-9a-f]+-000000[01]{2}");
	}

	@Test
	void shouldWriteTraceHeaderSampledFlagWhenParentSpan() {
		configureTracingSupport(new SimpleTracer());
		ObservedExchange observedExchange = createObservedExchange();
		Observation parentObservation = Observation.createNotStarted("parent", this.observationRegistry);
		parentObservation.start();
		observedExchange.observation().parentObservation(parentObservation);
		observedExchange.observation()
			.observe(() -> this.filter.filter(observedExchange.exchange(), new TestWebFilterChain())
				.block(Duration.ofSeconds(2)));
		parentObservation.stop();
		assertThat(observedExchange.exchange().getResponse().getHeaders().getFirst("traceresponse"))
			.matches("00-[0-9a-f]+-[0-9a-f]+-00000010");
	}

	@Test
	void shouldNotWriteTraceHeaderWhenNoCurrentTrace() {
		configureTracingSupport(Tracer.NOOP);
		ObservedExchange observedExchange = createObservedExchange();
		observedExchange.observation()
			.observe(() -> this.filter.filter(observedExchange.exchange(), new TestWebFilterChain())
				.block(Duration.ofSeconds(2)));
		assertThat(observedExchange.exchange().getResponse().getHeaders().keySet()).doesNotContain("traceresponse");
	}

	void configureTracingSupport(Tracer tracer) {
		this.observationRegistry.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));
	}

	private ObservedExchange createObservedExchange() {
		MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);
		Observation observation = Observation.createNotStarted("http.server.requests",
				() -> new ServerRequestObservationContext(exchange.getRequest(), exchange.getResponse(),
						exchange.getRequest().getAttributes()),
				this.observationRegistry);
		exchange.getAttributes()
			.put(ServerRequestObservationContext.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, observation.getContext());
		return new ObservedExchange(exchange, observation);
	}

	private record ObservedExchange(MockServerWebExchange exchange, Observation observation) {
	}

	static class TestWebFilterChain implements WebFilterChain {

		@Override
		public Mono<Void> filter(ServerWebExchange exchange) {
			return exchange.getResponse().setComplete();
		}

	}

}
