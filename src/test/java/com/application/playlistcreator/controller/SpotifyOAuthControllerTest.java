package com.application.playlistcreator.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.application.playlistcreator.service.SpotifyOAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class SpotifyOAuthControllerTest {

	@Test
	void rotatesSessionIdAfterSuccessfulSpotifyCallback() {
		SpotifyOAuthService spotifyOAuthService = mock(SpotifyOAuthService.class);
		SpotifyOAuthController controller = new SpotifyOAuthController(spotifyOAuthService);
		MockHttpSession session = new MockHttpSession();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setSession(session);
		String previousSessionId = session.getId();

		var response = controller.callback("code", "state", null, request, session);

		verify(spotifyOAuthService).handleCallback("code", "state", session);
		assertNotEquals(previousSessionId, session.getId());
		assertEquals(HttpStatus.FOUND, response.getStatusCode());
		assertEquals("/?spotifyLogin=success", response.getHeaders().getLocation().toString());
	}
}
