package com.application.playlistcreator.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.application.playlistcreator.exception.SpotifyAuthenticationException;
import com.application.playlistcreator.service.SpotifyOAuthService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiSpotifySessionFilterTest {

	private final SpotifyOAuthService spotifyOAuthService = mock(SpotifyOAuthService.class);
	private final ApiSpotifySessionFilter filter = new ApiSpotifySessionFilter(spotifyOAuthService);
	private final FilterChain filterChain = mock(FilterChain.class);

	@Test
	void rejectsApiRequestWithoutSpotifySession() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/playlists/preview");
		MockHttpServletResponse response = new MockHttpServletResponse();
		doThrow(new SpotifyAuthenticationException("Spotify login required"))
				.when(spotifyOAuthService).getValidToken(request.getSession());

		filter.doFilter(request, response, filterChain);

		assertEquals(401, response.getStatus());
		assertTrue(response.getContentAsString().contains("Spotify login required"));
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void allowsApiRequestWithSpotifySession() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/playlists/preview");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		verify(spotifyOAuthService).getValidToken(request.getSession());
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void leavesPublicResourcesOutsideSpotifySessionCheck() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		verify(spotifyOAuthService, never()).getValidToken(request.getSession());
		verify(filterChain).doFilter(request, response);
	}
}
