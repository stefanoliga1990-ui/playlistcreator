package com.application.playlistcreator.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedCacheFactoryTest {

	@Test
	void removesEntriesAfterTtl() {
		MutableTicker ticker = new MutableTicker();
		var cache = BoundedCacheFactory.<String, String>create(Duration.ofMinutes(10), 10, ticker);
		cache.put("artist", "result");

		ticker.advance(Duration.ofMinutes(11));
		cache.cleanUp();

		assertThat(cache.getIfPresent("artist")).isNull();
		assertThat(cache.estimatedSize()).isZero();
	}

	@Test
	void evictsEntriesBeyondMaximumSize() {
		var cache = BoundedCacheFactory.<String, String>create(
				Duration.ofHours(1), 2, Ticker.systemTicker());

		cache.put("one", "1");
		cache.put("two", "2");
		cache.put("three", "3");
		cache.cleanUp();

		assertThat(cache.estimatedSize()).isLessThanOrEqualTo(2);
	}

	private static final class MutableTicker implements Ticker {

		private final AtomicLong nanos = new AtomicLong();

		@Override
		public long read() {
			return nanos.get();
		}

		void advance(Duration duration) {
			nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(duration.toMillis()));
		}
	}
}
