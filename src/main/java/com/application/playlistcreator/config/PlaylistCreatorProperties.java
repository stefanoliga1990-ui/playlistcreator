package com.application.playlistcreator.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "playlistcreator")
public record PlaylistCreatorProperties(
		SetlistFm setlistfm,
		Spotify spotify,
		LastFm lastfm,
		GenrePlaylist genrePlaylist,
		HttpClient httpClient) {

	public record SetlistFm(
			String baseUrl,
			String apiKey,
			String acceptLanguage,
			int maxPagesToScan,
			int minSongsPerSetlist,
			int maxAgeMonths) {
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

	public record HttpClient(
			Duration connectTimeout,
			Duration readTimeout,
			int maxReadAttempts,
			Duration defaultRetryDelay,
			Duration maxRetryAfter,
			float circuitFailureRateThreshold,
			int circuitMinimumCalls,
			int circuitWindowSize,
			Duration circuitOpenDuration) {
	}
}
