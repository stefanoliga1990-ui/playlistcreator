package com.application.playlistcreator.client.spotify;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;

class SpotifyApiClientTest {

	@Test
	void retriesConnectionResetFailures() {
		var exception = new ResourceAccessException(
				"I/O error on GET request",
				new java.net.SocketException("Connection reset"));

		assertThat(SpotifyApiClient.isRetryableConnectionFailure(exception)).isTrue();
		assertThat(SpotifyApiClient.isTimeout(exception)).isFalse();
	}

	@Test
	void doesNotRetrySlowRequestTimeouts() {
		var exception = new ResourceAccessException(
				"I/O error on GET request",
				new SocketTimeoutException("Read timed out"));

		assertThat(SpotifyApiClient.isRetryableConnectionFailure(exception)).isFalse();
		assertThat(SpotifyApiClient.isTimeout(exception)).isTrue();
	}
}
