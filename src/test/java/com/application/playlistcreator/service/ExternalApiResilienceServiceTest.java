package com.application.playlistcreator.service;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import com.application.playlistcreator.config.PlaylistCreatorProperties;
import com.application.playlistcreator.exception.ExternalApiUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import static com.application.playlistcreator.service.ExternalApiResilienceService.Provider.SPOTIFY_API;
import static com.application.playlistcreator.service.ExternalApiResilienceService.Provider.SETLIST_FM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalApiResilienceServiceTest {

	@Test
	void retriesTransientConnectionResetForReads() {
		ExternalApiResilienceService service = service(2, 5);
		AtomicInteger calls = new AtomicInteger();

		String result = service.executeRead(SPOTIFY_API, "test read", () -> {
			if (calls.incrementAndGet() == 1) {
				throw new ResourceAccessException("I/O error", new java.net.SocketException("Connection reset"));
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(calls).hasValue(2);
	}

	@Test
	void doesNotRetryTimeouts() {
		ExternalApiResilienceService service = service(2, 5);
		AtomicInteger calls = new AtomicInteger();

		assertThatThrownBy(() -> service.executeRead(SPOTIFY_API, "slow read", () -> {
			calls.incrementAndGet();
			throw new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));
		}))
				.isInstanceOf(ExternalApiUnavailableException.class)
				.hasMessageContaining("taking too long");
		assertThat(calls).hasValue(1);
	}

	@Test
	void doesNotRetryWritesAfterAmbiguousConnectionFailure() {
		ExternalApiResilienceService service = service(2, 5);
		AtomicInteger calls = new AtomicInteger();

		assertThatThrownBy(() -> service.executeWrite(SPOTIFY_API, "playlist creation", () -> {
			calls.incrementAndGet();
			throw new ResourceAccessException("I/O error", new java.net.SocketException("Connection reset"));
		}))
				.isInstanceOf(ExternalApiUnavailableException.class);
		assertThat(calls).hasValue(1);
	}

	@Test
	void retriesTemporaryServerErrorsForReads() {
		ExternalApiResilienceService service = service(2, 5);
		AtomicInteger calls = new AtomicInteger();

		String result = service.executeRead(SPOTIFY_API, "test read", () -> {
			if (calls.incrementAndGet() == 1) {
				throw HttpServerErrorException.create(
						HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", HttpHeaders.EMPTY, new byte[0], null);
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(calls).hasValue(2);
	}

	@Test
	void honorsShortRetryAfterForReads() {
		ExternalApiResilienceService service = service(2, 5);
		AtomicInteger calls = new AtomicInteger();
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.RETRY_AFTER, "0");

		String result = service.executeRead(SPOTIFY_API, "rate limited read", () -> {
			if (calls.incrementAndGet() == 1) {
				throw org.springframework.web.client.HttpClientErrorException.create(
						HttpStatus.TOO_MANY_REQUESTS, "Rate limit", headers, new byte[0], StandardCharsets.UTF_8);
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(calls).hasValue(2);
	}

	@Test
	void retriesSetlistRateLimitOnceWhenRetryAfterIsMissing() {
		ExternalApiResilienceService service = service(2, 5);
		AtomicInteger calls = new AtomicInteger();

		String result = service.executeRead(SETLIST_FM, "setlist read", () -> {
			if (calls.incrementAndGet() == 1) {
				throw org.springframework.web.client.HttpClientErrorException.create(
						HttpStatus.TOO_MANY_REQUESTS, "Rate limit", HttpHeaders.EMPTY,
						new byte[0], StandardCharsets.UTF_8);
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(calls).hasValue(2);
	}

	@Test
	void parsesRetryAfterHttpDate() {
		Instant now = Instant.parse("2026-06-28T12:00:00Z");
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.RETRY_AFTER, ZonedDateTime.ofInstant(now.plusSeconds(30), ZoneOffset.UTC)
				.format(DateTimeFormatter.RFC_1123_DATE_TIME));

		assertThat(ExternalApiResilienceService.parseRetryAfter(headers, now))
				.contains(Duration.ofSeconds(30));
	}

	@Test
	void opensCircuitAfterRepeatedTemporaryFailures() {
		ExternalApiResilienceService service = service(1, 2);
		AtomicInteger calls = new AtomicInteger();
		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> service.executeRead(SPOTIFY_API, "failing read", () -> {
				calls.incrementAndGet();
				throw new ResourceAccessException("I/O error", new java.net.SocketException("Connection reset"));
			})).isInstanceOf(ExternalApiUnavailableException.class);
		}

		assertThatThrownBy(() -> service.executeRead(SPOTIFY_API, "blocked read", () -> {
			calls.incrementAndGet();
			return "unexpected";
		}))
				.isInstanceOf(ExternalApiUnavailableException.class)
				.hasMessageContaining("temporarily paused");
		assertThat(calls).hasValue(2);
	}

	private ExternalApiResilienceService service(int maxReadAttempts, int circuitMinimumCalls) {
		return new ExternalApiResilienceService(new PlaylistCreatorProperties(
				new PlaylistCreatorProperties.SetlistFm("https://setlist.test", "key", "it", 5, 6, 6),
				new PlaylistCreatorProperties.Spotify(
						"https://spotify.test", "https://accounts.test", "id", "secret",
						"https://app.test/callback", "scope", "IT", true),
				new PlaylistCreatorProperties.LastFm("https://lastfm.test", "key", ""),
				new PlaylistCreatorProperties.GenrePlaylist(15, 3, 25, 10),
				new PlaylistCreatorProperties.HttpClient(
						Duration.ofSeconds(10),
						Duration.ofSeconds(90),
						maxReadAttempts,
						Duration.ZERO,
						Duration.ofSeconds(10),
						50,
						circuitMinimumCalls,
						Math.max(2, circuitMinimumCalls),
						Duration.ofSeconds(30))));
	}
}
