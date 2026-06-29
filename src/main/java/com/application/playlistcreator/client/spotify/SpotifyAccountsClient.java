package com.application.playlistcreator.client.spotify;

import java.net.URI;
import java.util.UUID;

import com.application.playlistcreator.config.PlaylistCreatorProperties;
import com.application.playlistcreator.exception.SpotifyAuthenticationException;
import com.application.playlistcreator.service.ExternalApiResilienceService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import static com.application.playlistcreator.service.ExternalApiResilienceService.Provider.SPOTIFY_ACCOUNTS;

@Component
public class SpotifyAccountsClient {

	private final RestClient restClient;
	private final PlaylistCreatorProperties.Spotify properties;
	private final ExternalApiResilienceService resilienceService;

	public SpotifyAccountsClient(RestClient.Builder restClientBuilder, PlaylistCreatorProperties properties,
			ExternalApiResilienceService resilienceService) {
		this.properties = properties.spotify();
		this.resilienceService = resilienceService;
		this.restClient = restClientBuilder
				.baseUrl(this.properties.accountsBaseUrl())
				.build();
	}

	public AuthorizationRequest createAuthorizationRequest() {
		assertClientConfigured();
		String state = UUID.randomUUID().toString();
		URI authorizationUri = UriComponentsBuilder.fromUriString(properties.accountsBaseUrl())
				.path("/authorize")
				.queryParam("response_type", "code")
				.queryParam("client_id", properties.clientId())
				.queryParam("scope", properties.scopes())
				.queryParam("redirect_uri", properties.redirectUri())
				.queryParam("state", state)
				.build()
				.toUri();
		return new AuthorizationRequest(authorizationUri, state);
	}

	public TokenResponse exchangeCodeForToken(String code) {
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("grant_type", "authorization_code");
		body.add("code", code);
		body.add("redirect_uri", properties.redirectUri());
		return requestToken(body);
	}

	public TokenResponse refreshToken(String refreshToken) {
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("grant_type", "refresh_token");
		body.add("refresh_token", refreshToken);
		return requestToken(body);
	}

	private TokenResponse requestToken(MultiValueMap<String, String> body) {
		assertClientConfigured();
		try {
			return resilienceService.executeWrite(SPOTIFY_ACCOUNTS, "Spotify token request", () -> restClient.post()
					.uri("/api/token")
					.headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(body)
					.retrieve()
					.body(TokenResponse.class));
		}
		catch (RestClientResponseException ex) {
			throw new SpotifyAuthenticationException("Spotify token request failed: " + ex.getStatusCode());
		}
	}

	private void assertClientConfigured() {
		if (properties.clientId() == null || properties.clientId().isBlank()
				|| properties.clientSecret() == null || properties.clientSecret().isBlank()
				|| properties.redirectUri() == null || properties.redirectUri().isBlank()) {
			throw new SpotifyAuthenticationException("Spotify OAuth configuration is incomplete");
		}
	}

	public record AuthorizationRequest(URI authorizationUri, String state) {
	}

	public record TokenResponse(
			String access_token,
			String token_type,
			String scope,
			long expires_in,
			String refresh_token) {
	}
}

