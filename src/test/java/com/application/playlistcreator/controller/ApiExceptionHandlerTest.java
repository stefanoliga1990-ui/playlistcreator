package com.application.playlistcreator.controller;

import com.application.playlistcreator.exception.ExternalApiRateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

	@Test
	void returnsRateLimitStatusAndRetryAfterHeader() {
		ApiExceptionHandler handler = new ApiExceptionHandler();

		var response = handler.handleExternalApiRateLimit(new ExternalApiRateLimitException(
				"Spotify has temporarily reached its request limit.", 12L, null));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("12");
		assertThat(response.getBody().type()).isEqualTo("warning");
	}
}
