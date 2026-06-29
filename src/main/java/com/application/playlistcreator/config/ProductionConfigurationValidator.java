package com.application.playlistcreator.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("production")
public class ProductionConfigurationValidator implements InitializingBean {

	private final PlaylistCreatorProperties properties;

	public ProductionConfigurationValidator(PlaylistCreatorProperties properties) {
		this.properties = properties;
	}

	@Override
	public void afterPropertiesSet() {
		List<String> missingProperties = new ArrayList<>();
		requireText(properties.setlistfm().apiKey(), "SETLISTFM_API_KEY", missingProperties);
		requireText(properties.spotify().clientId(), "SPOTIFY_CLIENT_ID", missingProperties);
		requireText(properties.spotify().clientSecret(), "SPOTIFY_CLIENT_SECRET", missingProperties);
		requireText(properties.spotify().redirectUri(), "SPOTIFY_REDIRECT_URI", missingProperties);
		requireText(properties.lastfm().apiKey(), "LASTFM_API_KEY", missingProperties);

		if (!missingProperties.isEmpty()) {
			throw new IllegalStateException(
					"Missing required production environment variables: " + String.join(", ", missingProperties));
		}

		validateSpotifyRedirectUri(properties.spotify().redirectUri());
	}

	private void requireText(String value, String environmentVariable, List<String> missingProperties) {
		if (!StringUtils.hasText(value)) {
			missingProperties.add(environmentVariable);
		}
	}

	private void validateSpotifyRedirectUri(String redirectUri) {
		URI uri;
		try {
			uri = URI.create(redirectUri);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("SPOTIFY_REDIRECT_URI must be a valid absolute HTTPS URI", ex);
		}

		if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
			throw new IllegalStateException("SPOTIFY_REDIRECT_URI must be a valid absolute HTTPS URI");
		}
	}
}
