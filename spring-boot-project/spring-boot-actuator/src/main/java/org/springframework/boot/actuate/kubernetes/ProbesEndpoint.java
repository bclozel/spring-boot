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

package org.springframework.boot.actuate.kubernetes;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.kubernetes.ApplicationStateProvider;
import org.springframework.boot.kubernetes.LivenessState;
import org.springframework.boot.kubernetes.ReadinessState;

/**
 * {@link Endpoint @Endpoint} to expose Kubernetes Probes.
 * <p>
 * This {@code Enpoint} relies on information given by the
 * {@link ApplicationStateProvider}.
 *
 * @author Brian Clozel
 * @since 2.3.0
 */
@Endpoint(id = "probes")
public class ProbesEndpoint {

	private static final String LIVENESS_PROBE = "liveness";

	private static final String READINESS_PROBE = "readiness";

	private final ApplicationStateProvider applicationStateProvider;

	public ProbesEndpoint(ApplicationStateProvider applicationStateProvider) {
		this.applicationStateProvider = applicationStateProvider;
	}

	@ReadOperation
	public ProbeReport probe(@Selector String name) {
		if (LIVENESS_PROBE.equals(name)) {
			return new ProbeReport(this.applicationStateProvider.getLivenessState());
		}
		else if (READINESS_PROBE.equals(name)) {
			return new ProbeReport(this.applicationStateProvider.getReadinessState());
		}
		return null;
	}

	/**
	 * The result of a call to the probe, primarily intended for serialization to JSON.
	 */
	public static class ProbeReport {

		private final Status status;

		public ProbeReport(LivenessState livenessState) {
			this.status = LivenessState.Status.LIVE.equals(livenessState.getStatus()) ? Status.SUCCESS : Status.FAILURE;
		}

		public ProbeReport(ReadinessState readinessState) {
			this.status = ReadinessState.Availability.READY.equals(readinessState.getAvailability()) ? Status.SUCCESS
					: Status.FAILURE;
		}

		public Status getStatus() {
			return this.status;
		}

		enum Status {

			SUCCESS, FAILURE

		}

	}

}
