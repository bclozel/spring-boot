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

import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import reactor.core.publisher.Mono;

import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

/**
 * {@link WebFilter} that writes the current Trace context information in a
 * {@code "traceresponse"} HTTP response header.
 *
 * @author Brian Clozel
 * @since 3.5.0
 * @see <a href=
 * "https://w3c.github.io/trace-context/#trace-context-http-response-headers-format">Trace
 * Context W3C spec</a>
 */
public class TraceResponseObservationWebFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		ServerHttpResponse response = exchange.getResponse();
		response.beforeCommit(() -> {
			ServerRequestObservationContext.findCurrent(exchange.getAttributes()).ifPresent((observationContext) -> {
				TracingContext tracingContext = observationContext.get(TracingContext.class);
				if (tracingContext != null) {
					Span currentSpan = tracingContext.getSpan();
					if (currentSpan != null && !currentSpan.isNoop()) {
						response.getHeaders().add("traceresponse", createTraceResponseHeader(currentSpan));
					}
				}
			});
			return Mono.empty();
		});
		return chain.filter(exchange);
	}

	private static String createTraceResponseHeader(Span currentSpan) {
		StringBuilder traceresponse = new StringBuilder();
		// version
		traceresponse.append("00-");
		// trace-id
		traceresponse.append(currentSpan.context().traceId());
		traceresponse.append("-");
		// child-id
		traceresponse.append(currentSpan.context().spanId());
		traceresponse.append("-");
		// trace-flags
		traceresponse.append("000000");
		if (currentSpan.context().parentId() != null) {
			traceresponse.append("1");
		}
		else {
			traceresponse.append("0");
		}
		if (Boolean.TRUE.equals(currentSpan.context().sampled())) {
			traceresponse.append("1");
		}
		else {
			traceresponse.append("0");
		}
		return traceresponse.toString();
	}

}
