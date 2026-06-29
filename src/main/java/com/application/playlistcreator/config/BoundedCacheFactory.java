package com.application.playlistcreator.config;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

public final class BoundedCacheFactory {

	private BoundedCacheFactory() {
	}

	public static <K, V> Cache<K, V> create(Duration ttl, long maximumSize) {
		return create(ttl, maximumSize, Ticker.systemTicker());
	}

	static <K, V> Cache<K, V> create(Duration ttl, long maximumSize, Ticker ticker) {
		return Caffeine.newBuilder()
				.expireAfterWrite(ttl)
				.maximumSize(maximumSize)
				.ticker(ticker)
				.build();
	}
}
