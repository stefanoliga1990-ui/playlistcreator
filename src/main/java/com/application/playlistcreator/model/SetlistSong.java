package com.application.playlistcreator.model;

public record SetlistSong(
		String title,
		String normalizedTitle,
		int position,
		boolean cover,
		String coverArtist,
		String withArtist,
		String info) {
}
