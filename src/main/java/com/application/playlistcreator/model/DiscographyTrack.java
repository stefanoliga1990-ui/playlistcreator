package com.application.playlistcreator.model;

public record DiscographyTrack(
		String albumId,
		String albumName,
		int albumYear,
		String id,
		String title,
		String uri,
		int albumTrackNumber,
		int selectedRank,
		long lastFmPlaycount,
		String rankingSource) {
}
