package com.application.playlistcreator.model;

public record GenreTrackCandidate(
		String artistName,
		String title,
		String normalizedTitle,
		String lastFmUrl,
		long listeners,
		long playcount,
		int artistRank,
		int trackRank) {
}
