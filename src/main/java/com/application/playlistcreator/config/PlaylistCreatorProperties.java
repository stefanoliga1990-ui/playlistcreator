package com.application.playlistcreator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "playlistcreator")
public record PlaylistCreatorProperties(
		SetlistFm setlistfm,
		Spotify spotify,
		LastFm lastfm,
		GenrePlaylist genrePlaylist) {

	public record SetlistFm(
			String baseUrl,
			String apiKey,
			String acceptLanguage,
			int maxPagesToScan,
			int minSongsPerSetlist) {
	}

	public record Spotify(
			String apiBaseUrl,
			String accountsBaseUrl,
			String clientId,
			String clientSecret,
			String redirectUri,
			String scopes,
			String market,
			boolean defaultPublicPlaylist) {
	}

	public record LastFm(
			String baseUrl,
			String apiKey,
			String sharedSecret) {
	}

	public record GenrePlaylist(
			int defaultArtistLimit,
			int defaultTracksPerArtist,
			int maxArtistLimit,
			int maxTracksPerArtist) {
	}
}
