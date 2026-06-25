package com.application.playlistcreator.model;

import java.io.Serializable;
import java.time.Instant;

public record SpotifyUserToken(
		String accessToken,
		String refreshToken,
		Instant expiresAt) implements Serializable {

	public boolean isExpired() {
		return expiresAt == null || Instant.now().isAfter(expiresAt.minusSeconds(60));
	}
}
