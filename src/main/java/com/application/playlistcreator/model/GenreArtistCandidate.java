package com.application.playlistcreator.model;

public record GenreArtistCandidate(
		String name,
		String musicBrainzId,
		String lastFmUrl,
		long listeners,
		int rank) {
}
