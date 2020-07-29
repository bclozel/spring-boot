/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.boot.context.metrics.buffering;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.metrics.StartupStep;

/**
 * Represent the collection of recorded {@link BufferedStartupStep steps} on a timeline.
 * Each {@link TimelineEvent} has a start and end time as well as a duration measured with
 * nanosecond precision.
 *
 * @author Brian Clozel
 * @since 2.4.0
 */
public class StartupTimeline {

	private final Instant startTime;

	private final List<TimelineEvent> recordedSteps;

	StartupTimeline(Instant startTime, long startNanoTime, Collection<BufferedStartupStep> events) {
		this.startTime = startTime;
		this.recordedSteps = events.stream().map((event) -> new TimelineEvent(event, startTime, startNanoTime))
				.collect(Collectors.toList());
	}

	/**
	 * Return the start time of this timeline.
	 * @return the start time
	 */
	public Instant getStartTime() {
		return this.startTime;
	}

	/**
	 * Return the events recorded.
	 * @return the events
	 */
	public List<TimelineEvent> getEvents() {
		return this.recordedSteps;
	}

	/**
	 * Event on the current {@link StartupTimeline}. Each event has a start/end time, a
	 * precise duration and the complete {@link StartupStep} information associated with
	 * it.
	 */
	public static class TimelineEvent {

		private final StartupStep startupStep;

		private final Instant startTime;

		private final Instant endTime;

		private final Duration duration;

		TimelineEvent(BufferedStartupStep startupStep, Instant startupDate, long startupNanoTime) {
			this.startupStep = startupStep;
			this.startTime = startupDate
					.plus(Duration.ofNanos(startupStep.getTimeRecord().getStartTime() - startupNanoTime));
			this.endTime = startupDate
					.plus(Duration.ofNanos(startupStep.getTimeRecord().getEndTime() - startupNanoTime));
			this.duration = Duration
					.ofNanos(startupStep.getTimeRecord().getEndTime() - startupStep.getTimeRecord().getStartTime());
		}

		/**
		 * Return the start time of this event.
		 * @return the start time
		 */
		public Instant getStartTime() {
			return this.startTime;
		}

		/**
		 * Return the end time of this event.
		 * @return the end time
		 */
		public Instant getEndTime() {
			return this.endTime;
		}

		/**
		 * Return the duration of this event, i.e. the processing time of the associated
		 * {@link StartupStep}.
		 * @return the event duration
		 */
		public long getDuration() {
			return this.duration.toMillis();
		}

		/**
		 * Return the {@link StartupStep} information for this event.
		 * @return the step information.
		 */
		public StartupStep getStep() {
			return this.startupStep;
		}

	}

}
