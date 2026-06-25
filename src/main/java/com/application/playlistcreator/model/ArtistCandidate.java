package com.application.playlistcreator.model;

public record ArtistCandidate(
		String musicBrainzId,
		String name,
		String sortName,
		String disambiguation,
		String url) {
}
