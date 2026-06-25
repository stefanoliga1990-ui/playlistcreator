package com.application.playlistcreator.model;

import java.util.List;

public record GenrePlaylistSelection(
		String genre,
		List<GenreArtistCandidate> artists,
		List<GenreTrackCandidate> tracks) {
}
