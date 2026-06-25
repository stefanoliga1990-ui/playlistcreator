package com.application.playlistcreator.model;

import java.util.List;

public record SimilarArtistsPlaylistSelection(
		String sourceArtistName,
		List<SimilarArtistCandidate> artists,
		List<GenreTrackCandidate> tracks,
		int tracksPerArtist,
		String warning) {
}
