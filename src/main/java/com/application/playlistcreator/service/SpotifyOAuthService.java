package com.application.playlistcreator.service;

import java.net.URI;
import java.time.Instant;

import com.application.playlistcreator.client.spotify.SpotifyAccountsClient;
import com.application.playlistcreator.client.spotify.SpotifyAccountsClient.TokenResponse;
import com.application.playlistcreator.exception.SpotifyAuthenticationException;
import com.application.playlistcreator.model.SpotifyUserToken;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SpotifyOAuthService {

	private static final Logger log = LoggerFactory.getLogger(SpotifyOAuthService.class);

	private static final String SPOTIFY_STATE = "SPOTIFY_AUTH_STATE";
	private static final String SPOTIFY_TOKEN = "SPOTIFY_USER_TOKEN";

	private final SpotifyAccountsClient spotifyAccountsClient;

	public SpotifyOAuthService(SpotifyAccountsClient spotifyAccountsClient) {
		this.spotifyAccountsClient = spotifyAccountsClient;
	}

	public URI createAuthorizationUri(HttpSession session) {
		var authorizationRequest = spotifyAccountsClient.createAuthorizationRequest();
		session.setAttribute(SPOTIFY_STATE, authorizationRequest.state());
		log.info("Spotify OAuth state stored. sessionId={}", session.getId());
		return authorizationRequest.authorizationUri();
	}

	public void handleCallback(String code, String state, HttpSession session) {
		Object expectedState = session.getAttribute(SPOTIFY_STATE);
		if (expectedState == null || !expectedState.equals(state)) {
			log.warn("Invalid Spotify OAuth state. sessionId={}, expectedStatePresent={}, receivedStatePresent={}",
					session.getId(), expectedState != null, state != null && !state.isBlank());
			throw new SpotifyAuthenticationException("Invalid Spotify OAuth state");
		}
		TokenResponse tokenResponse = spotifyAccountsClient.exchangeCodeForToken(code);
		session.setAttribute(SPOTIFY_TOKEN, toUserToken(tokenResponse, null));
		session.removeAttribute(SPOTIFY_STATE);
		log.info("Spotify token stored in session. sessionId={}, expiresInSeconds={}, grantedScopes={}",
				session.getId(), tokenResponse.expires_in(), tokenResponse.scope());
	}

	public SpotifyUserToken getValidToken(HttpSession session) {
		Object tokenAttribute = session.getAttribute(SPOTIFY_TOKEN);
		if (!(tokenAttribute instanceof SpotifyUserToken token)) {
			log.warn("Spotify token missing from session. sessionId={}", session.getId());
			throw new SpotifyAuthenticationException("Spotify login required. Open /spotify/login first.");
		}
		if (!token.isExpired()) {
			log.debug("Spotify token still valid. sessionId={}", session.getId());
			return token;
		}
		if (token.refreshToken() == null || token.refreshToken().isBlank()) {
			log.warn("Spotify token expired and refresh token missing. sessionId={}", session.getId());
			throw new SpotifyAuthenticationException("Spotify token expired and no refresh token is available");
		}
		log.info("Refreshing Spotify access token. sessionId={}", session.getId());
		TokenResponse refreshed = spotifyAccountsClient.refreshToken(token.refreshToken());
		SpotifyUserToken refreshedToken = toUserToken(refreshed, token.refreshToken());
		session.setAttribute(SPOTIFY_TOKEN, refreshedToken);
		log.info("Spotify access token refreshed. sessionId={}, expiresInSeconds={}, grantedScopes={}",
				session.getId(), refreshed.expires_in(), refreshed.scope());
		return refreshedToken;
	}

	public boolean isLoggedIn(HttpSession session) {
		Object tokenAttribute = session.getAttribute(SPOTIFY_TOKEN);
		return tokenAttribute instanceof SpotifyUserToken token && !token.isExpired();
	}

	private SpotifyUserToken toUserToken(TokenResponse tokenResponse, String fallbackRefreshToken) {
		String refreshToken = tokenResponse.refresh_token() != null ? tokenResponse.refresh_token() : fallbackRefreshToken;
		return new SpotifyUserToken(
				tokenResponse.access_token(),
				refreshToken,
				Instant.now().plusSeconds(tokenResponse.expires_in()));
	}
}
