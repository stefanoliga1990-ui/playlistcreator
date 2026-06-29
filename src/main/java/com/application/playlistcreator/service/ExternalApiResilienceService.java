package com.application.playlistcreator.service;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.application.playlistcreator.config.PlaylistCreatorProperties;
import com.application.playlistcreator.exception.ExternalApiRateLimitException;
import com.application.playlistcreator.exception.ExternalApiUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ExternalApiResilienceService {

	private static final Logger log = LoggerFactory.getLogger(ExternalApiResilienceService.class);
	private static final Duration SETLIST_FM_RATE_LIMIT_FALLBACK_DELAY = Duration.ofSeconds(1);

	private final PlaylistCreatorProperties.HttpClient properties;
	private final Map<Provider, CircuitBreaker> circuitBreakers;

	public ExternalApiResilienceService(PlaylistCreatorProperties properties) {
		this.properties = properties.httpClient();
		this.circuitBreakers = createCircuitBreakers(this.properties);
	}

	public <T> T executeRead(Provider provider, String operation, Supplier<T> request) {
		return executeWithCircuitBreaker(provider, operation,
				() -> executeAttempts(provider, operation, request, properties.maxReadAttempts()));
	}

	public <T> T executeWrite(Provider provider, String operation, Supplier<T> request) {
		return executeWithCircuitBreaker(provider, operation,
				() -> executeAttempts(provider, operation, request, 1));
	}

	private <T> T executeWithCircuitBreaker(Provider provider, String operation, Supplier<T> request) {
		try {
			return circuitBreakers.get(provider).executeSupplier(request);
		}
		catch (CallNotPermittedException ex) {
			log.warn("External API circuit is open. provider={}, operation={}", provider, operation);
			throw new ExternalApiUnavailableException(
					provider.displayName + " is temporarily paused after repeated errors. Please try again in about "
							+ properties.circuitOpenDuration().toSeconds() + " seconds.",
					ex);
		}
	}

	private <T> T executeAttempts(Provider provider, String operation, Supplier<T> request, int maxAttempts) {
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				return request.get();
			}
			catch (RestClientResponseException ex) {
				if (ex.getStatusCode().value() == 429) {
					Duration retryAfter = parseRetryAfter(ex.getResponseHeaders(), Instant.now()).orElse(null);
					Duration retryDelay = retryAfter != null
							? retryAfter
							: provider == Provider.SETLIST_FM ? SETLIST_FM_RATE_LIMIT_FALLBACK_DELAY : null;
					if (attempt < maxAttempts && isShortRetry(retryDelay)) {
						log.warn("External API rate limit reached. Retrying after controlled delay. provider={}, operation={}, delayMs={}, attempt={}/{}",
								provider, operation, retryDelay.toMillis(), attempt, maxAttempts);
						waitBeforeRetry(provider, retryDelay, ex);
						continue;
					}
					throw rateLimitException(provider, retryAfter, ex);
				}
				if (ex.getStatusCode().is5xxServerError()) {
					if (attempt < maxAttempts) {
						log.warn("External API server error. Retrying read once. provider={}, operation={}, status={}, attempt={}/{}",
								provider, operation, ex.getStatusCode(), attempt, maxAttempts);
						waitBeforeRetry(provider, properties.defaultRetryDelay(), ex);
						continue;
					}
					throw unavailable(provider, "returned a temporary server error", ex);
				}
				throw ex;
			}
			catch (ResourceAccessException ex) {
				if (isTimeout(ex)) {
					log.warn("External API request timed out. No retry attempted. provider={}, operation={}",
							provider, operation, ex);
					throw unavailable(provider, "is taking too long to respond", ex);
				}
				if (isRetryableConnectionFailure(ex) && attempt < maxAttempts) {
					log.warn("External API connection interrupted. Retrying read once. provider={}, operation={}, attempt={}/{}",
							provider, operation, attempt, maxAttempts, ex);
					waitBeforeRetry(provider, properties.defaultRetryDelay(), ex);
					continue;
				}
				throw unavailable(provider, "is temporarily unreachable", ex);
			}
		}
		throw new IllegalStateException("Unreachable external API retry state");
	}

	private boolean isShortRetry(Duration retryAfter) {
		return retryAfter != null && !retryAfter.isNegative()
				&& retryAfter.compareTo(properties.maxRetryAfter()) <= 0;
	}

	private ExternalApiRateLimitException rateLimitException(
			Provider provider, Duration retryAfter, RestClientResponseException cause) {
		Long retryAfterSeconds = retryAfter != null ? Math.max(1, retryAfter.toSeconds()) : null;
		String waitMessage = retryAfterSeconds != null
				? " Please try again in about " + retryAfterSeconds + " seconds."
				: " Please wait a little before trying again.";
		log.warn("External API rate limit not retried. provider={}, retryAfterSeconds={}",
				provider, retryAfterSeconds);
		return new ExternalApiRateLimitException(
				provider.displayName + " has temporarily reached its request limit." + waitMessage,
				retryAfterSeconds,
				cause);
	}

	private ExternalApiUnavailableException unavailable(Provider provider, String reason, Throwable cause) {
		return new ExternalApiUnavailableException(
				provider.displayName + " " + reason + ". Please try again in a few seconds.",
				cause);
	}

	private void waitBeforeRetry(Provider provider, Duration delay, Throwable cause) {
		try {
			Thread.sleep(delay.toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw unavailable(provider, "is temporarily unavailable", cause);
		}
	}

	private Map<Provider, CircuitBreaker> createCircuitBreakers(PlaylistCreatorProperties.HttpClient properties) {
		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
				.failureRateThreshold(properties.circuitFailureRateThreshold())
				.minimumNumberOfCalls(properties.circuitMinimumCalls())
				.slidingWindowSize(properties.circuitWindowSize())
				.permittedNumberOfCallsInHalfOpenState(2)
				.waitDurationInOpenState(properties.circuitOpenDuration())
				.recordException(ex -> ex instanceof ExternalApiUnavailableException)
				.build();
		Map<Provider, CircuitBreaker> breakers = new EnumMap<>(Provider.class);
		for (Provider provider : Provider.values()) {
			CircuitBreaker breaker = CircuitBreaker.of(provider.name().toLowerCase(Locale.ROOT), config);
			breaker.getEventPublisher().onStateTransition(event -> log.warn(
					"External API circuit state changed. provider={}, transition={}", provider, event.getStateTransition()));
			breakers.put(provider, breaker);
		}
		return breakers;
	}

	static Optional<Duration> parseRetryAfter(HttpHeaders headers, Instant now) {
		if (headers == null) {
			return Optional.empty();
		}
		String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim()))));
		}
		catch (NumberFormatException ignored) {
			try {
				Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
				return Optional.of(Duration.between(now, retryAt).isNegative()
						? Duration.ZERO
						: Duration.between(now, retryAt));
			}
			catch (DateTimeParseException invalidDate) {
				return Optional.empty();
			}
		}
	}

	static boolean isRetryableConnectionFailure(Throwable throwable) {
		for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
			String message = cause.getMessage();
			if (message == null) {
				continue;
			}
			String normalized = message.toLowerCase(Locale.ROOT);
			if (normalized.contains("connection reset")
					|| normalized.contains("connection aborted")
					|| normalized.contains("premature end")
					|| normalized.contains("unexpected end of file")) {
				return true;
			}
		}
		return false;
	}

	static boolean isTimeout(Throwable throwable) {
		for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
			if (cause instanceof SocketTimeoutException || cause instanceof HttpConnectTimeoutException) {
				return true;
			}
			String message = cause.getMessage();
			if (message != null && message.toLowerCase(Locale.ROOT).contains("timed out")) {
				return true;
			}
		}
		return false;
	}

	public enum Provider {
		SPOTIFY_API("Spotify"),
		SPOTIFY_ACCOUNTS("Spotify login"),
		LAST_FM("Last.fm"),
		SETLIST_FM("setlist.fm");

		private final String displayName;

		Provider(String displayName) {
			this.displayName = displayName;
		}
	}
}
