package com.application.playlistcreator.controller;

import java.net.URI;
import java.util.Map;

import com.application.playlistcreator.service.SpotifyOAuthService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpotifyOAuthController {

	private static final Logger log = LoggerFactory.getLogger(SpotifyOAuthController.class);

	private final SpotifyOAuthService spotifyOAuthService;

	public SpotifyOAuthController(SpotifyOAuthService spotifyOAuthService) {
		this.spotifyOAuthService = spotifyOAuthService;
	}

	@GetMapping("/spotify/login")
	public ResponseEntity<Void> login(HttpSession session) {
		log.info("Starting Spotify login flow. sessionId={}", session.getId());
		URI authorizationUri = spotifyOAuthService.createAuthorizationUri(session);
		log.info("Redirecting user to Spotify authorization endpoint. sessionId={}", session.getId());
		return ResponseEntity.status(HttpStatus.FOUND).location(authorizationUri).build();
	}

	@GetMapping("/login/oauth2/code/spotify")
	public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String error,
			HttpSession session) {
		if (error != null && !error.isBlank()) {
			log.warn("Spotify OAuth callback returned error. sessionId={}, error={}", session.getId(), error);
			return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/?spotifyLogin=error")).build();
		}
		log.info("Handling Spotify OAuth callback. sessionId={}, hasCode={}, hasState={}",
				session.getId(), code != null && !code.isBlank(), state != null && !state.isBlank());
		spotifyOAuthService.handleCallback(code, state, session);
		log.info("Spotify OAuth completed. sessionId={}", session.getId());
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/?spotifyLogin=success")).build();
	}

	@GetMapping("/spotify/status")
	public Map<String, Object> status(HttpSession session) {
		boolean loggedIn = spotifyOAuthService.isLoggedIn(session);
		log.debug("Spotify status requested. sessionId={}, loggedIn={}", session.getId(), loggedIn);
		return Map.of("loggedIn", loggedIn);
	}

	@PostMapping("/spotify/logout")
	public Map<String, Object> logout(HttpSession session) {
		String sessionId = session.getId();
		session.invalidate();
		log.info("Spotify local logout completed. previousSessionId={}", sessionId);
		return Map.of("loggedIn", false);
	}
}
