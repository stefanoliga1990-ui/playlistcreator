package com.application.playlistcreator.controller;

import com.application.playlistcreator.exception.ExternalApiException;
import com.application.playlistcreator.exception.ExternalApiUnavailableException;
import com.application.playlistcreator.exception.NoRecentSetlistsException;
import com.application.playlistcreator.exception.SpotifyAuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(SpotifyAuthenticationException.class)
	public ResponseEntity<ApiError> handleSpotifyAuthentication(SpotifyAuthenticationException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(ex.getMessage()));
	}

	@ExceptionHandler(NoRecentSetlistsException.class)
	public ResponseEntity<ApiError> handleNoRecentSetlists(NoRecentSetlistsException ex) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
				.body(new ApiError(ex.getMessage(), "warning"));
	}

	@ExceptionHandler(ExternalApiException.class)
	public ResponseEntity<ApiError> handleExternalApi(ExternalApiException ex) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiError(ex.getMessage()));
	}

	@ExceptionHandler(ExternalApiUnavailableException.class)
	public ResponseEntity<ApiError> handleExternalApiUnavailable(ExternalApiUnavailableException ex) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(ex.getMessage()));
	}

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<ApiError> handleRuntime(RuntimeException ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(ex.getMessage()));
	}

	public record ApiError(String message, String type) {

		public ApiError(String message) {
			this(message, "error");
		}
	}
}
