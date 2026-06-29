package com.application.playlistcreator.config;

import java.io.IOException;

import com.application.playlistcreator.exception.SpotifyAuthenticationException;
import com.application.playlistcreator.service.SpotifyOAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiSpotifySessionFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(ApiSpotifySessionFilter.class);

	private final SpotifyOAuthService spotifyOAuthService;

	public ApiSpotifySessionFilter(SpotifyOAuthService spotifyOAuthService) {
		this.spotifyOAuthService = spotifyOAuthService;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		try {
			spotifyOAuthService.getValidToken(request.getSession());
			filterChain.doFilter(request, response);
		}
		catch (SpotifyAuthenticationException ex) {
			log.warn("Rejected unauthenticated API request. method={}, path={}, sessionId={}",
					request.getMethod(), request.getRequestURI(), request.getSession().getId());
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter().write("{\"message\":\"Spotify login required.\",\"type\":\"error\"}");
		}
	}
}
