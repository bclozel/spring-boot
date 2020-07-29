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

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.core.metrics.StartupStep;

/**
 * {@link StartupStep} implementation to be buffered by
 * {@link BufferingApplicationStartup}. Its processing time is recorded in
 * {@link TimeRecord}.
 *
 * @author Brian Clozel
 * @since 2.4.0
 */
public class BufferedStartupStep implements StartupStep {

	private final String name;

	private final long id;

	private final Long parentId;

	private final TimeRecord timeRecord;

	private final DefaultTags tags;

	private final Consumer<BufferedStartupStep> recorder;

	public BufferedStartupStep(long id, String name, Long parentId, Consumer<BufferedStartupStep> recorder) {
		this.id = id;
		this.parentId = parentId;
		this.timeRecord = new TimeRecord();
		this.tags = new DefaultTags();
		this.name = name;
		this.recorder = recorder;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public long getId() {
		return this.id;
	}

	@Override
	public Long getParentId() {
		return this.parentId;
	}

	/**
	 * Return the start and end times of its processing, as provided by
	 * {@link System#nanoTime()}.
	 * @return the time record information
	 */
	public TimeRecord getTimeRecord() {
		return this.timeRecord;
	}

	@Override
	public Tags tags() {
		return this.tags;
	}

	@Override
	public StartupStep tag(String key, String value) {
		this.tags.add(key, value);
		return this;
	}

	@Override
	public StartupStep tag(String key, Supplier<String> value) {
		this.tags.add(key, value);
		return this;
	}

	@Override
	public void end() {
		this.recorder.accept(this);
	}

	/**
	 * Time record associated with the current step.
	 */
	public static class TimeRecord {

		private long startTime;

		private long endTime;

		/**
		 * Return the {@link System#nanoTime() start time} for this step.
		 * @return the start time
		 */
		public long getStartTime() {
			return this.startTime;
		}

		void recordStartTime(long startTime) {
			this.startTime = startTime;
		}

		/**
		 * Return the {@link System#nanoTime() end time} for this step.
		 * @return the end time
		 */
		public long getEndTime() {
			return this.endTime;
		}

		void recordEndTime(long endTime) {
			this.endTime = endTime;
		}

	}

	static class DefaultTags implements Tags {

		private Tag[] tags = new Tag[0];

		void add(String key, String value) {
			Tag[] newTags = new Tag[this.tags.length + 1];
			System.arraycopy(this.tags, 0, newTags, 0, this.tags.length);
			newTags[newTags.length - 1] = new DefaultTag(key, value);
			this.tags = newTags;
		}

		void add(String key, Supplier<String> value) {
			add(key, value.get());
		}

		@Override
		public Iterator<Tag> iterator() {
			return new TagsIterator();
		}

		private class TagsIterator implements Iterator<Tag> {

			private int idx = 0;

			@Override
			public boolean hasNext() {
				return this.idx < DefaultTags.this.tags.length;
			}

			@Override
			public Tag next() {
				return DefaultTags.this.tags[this.idx++];
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException("tags are append only");
			}

		}

	}

	static class DefaultTag implements Tag {

		private final String key;

		private final String value;

		DefaultTag(String key, String value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public String getKey() {
			return this.key;
		}

		@Override
		public String getValue() {
			return this.value;
		}

	}

}
