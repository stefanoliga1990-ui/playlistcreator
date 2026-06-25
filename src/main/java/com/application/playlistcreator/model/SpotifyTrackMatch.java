package com.application.playlistcreator.model;

import java.util.List;

public record SpotifyTrackMatch(
		CandidateSong song,
		String spotifyTrackName,
		List<String> spotifyArtists,
		String uri,
		int score,
		MatchStatus status) {

	public enum MatchStatus {
		MATCHED,
		UNCERTAIN,
		NOT_FOUND
	}
}
