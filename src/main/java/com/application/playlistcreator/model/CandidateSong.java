package com.application.playlistcreator.model;

public record CandidateSong(
		String title,
		String normalizedTitle,
		int latestPosition,
		double averagePosition,
		boolean cover,
		String coverArtist) {
}
