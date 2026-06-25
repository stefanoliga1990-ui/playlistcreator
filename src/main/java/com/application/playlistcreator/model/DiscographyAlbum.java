package com.application.playlistcreator.model;

public record DiscographyAlbum(
		String id,
		String name,
		String releaseDate,
		int releaseYear,
		int totalTracks,
		String spotifyUrl) {
}
