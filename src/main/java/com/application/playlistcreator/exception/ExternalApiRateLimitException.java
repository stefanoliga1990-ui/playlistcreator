package com.application.playlistcreator.exception;

public class ExternalApiRateLimitException extends ExternalApiUnavailableException {

	private final Long retryAfterSeconds;

	public ExternalApiRateLimitException(String message, Long retryAfterSeconds, Throwable cause) {
		super(message, cause);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public Long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
