package com.application.playlistcreator.model;

public record GenreTrackMatch(
		GenreTrackCandidate track,
		SpotifyTrackMatch spotifyMatch) {
}
