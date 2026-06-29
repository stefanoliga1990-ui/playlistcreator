package com.application.playlistcreator.config;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionConfigurationValidatorTest {

	@Test
	void acceptsCompleteProductionConfiguration() {
		ProductionConfigurationValidator validator = new ProductionConfigurationValidator(properties(
				"setlist-key",
				"spotify-client",
				"spotify-secret",
				"https://playlist.example.com/login/oauth2/code/spotify",
				"lastfm-key"));

		assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
	}

	@Test
	void rejectsMissingRequiredEnvironmentVariables() {
		ProductionConfigurationValidator validator = new ProductionConfigurationValidator(properties(
				"",
				"spotify-client",
				"",
				"https://playlist.example.com/login/oauth2/code/spotify",
				"lastfm-key"));

		assertThatThrownBy(validator::afterPropertiesSet)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("SETLISTFM_API_KEY")
				.hasMessageContaining("SPOTIFY_CLIENT_SECRET");
	}

	@Test
	void rejectsInsecureSpotifyRedirectUri() {
		ProductionConfigurationValidator validator = new ProductionConfigurationValidator(properties(
				"setlist-key",
				"spotify-client",
				"spotify-secret",
				"http://playlist.example.com/login/oauth2/code/spotify",
				"lastfm-key"));

		assertThatThrownBy(validator::afterPropertiesSet)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("SPOTIFY_REDIRECT_URI must be a valid absolute HTTPS URI");
	}

	private PlaylistCreatorProperties properties(
			String setlistApiKey,
			String spotifyClientId,
			String spotifyClientSecret,
			String spotifyRedirectUri,
			String lastFmApiKey) {
		return new PlaylistCreatorProperties(
				new PlaylistCreatorProperties.SetlistFm("https://api.setlist.fm/rest", setlistApiKey, "it", 5, 6, 6),
				new PlaylistCreatorProperties.Spotify(
						"https://api.spotify.com/v1",
						"https://accounts.spotify.com",
						spotifyClientId,
						spotifyClientSecret,
						spotifyRedirectUri,
						"playlist-modify-public",
						"IT",
						true),
				new PlaylistCreatorProperties.LastFm("https://ws.audioscrobbler.com/2.0", lastFmApiKey, ""),
				new PlaylistCreatorProperties.GenrePlaylist(15, 3, 25, 10),
				new PlaylistCreatorProperties.HttpClient(
						Duration.ofSeconds(10),
						Duration.ofSeconds(90),
						2,
						Duration.ofMillis(500),
						Duration.ofSeconds(10),
						50,
						5,
						10,
						Duration.ofSeconds(30)));
	}
}
