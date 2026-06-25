package com.application.playlistcreator.model;

import java.util.List;

public record SimilarArtistCandidate(
		String name,
		String musicBrainzId,
		String lastFmUrl,
		double directSimilarity,
		double reciprocalSimilarity,
		double tagSimilarity,
		int similarityScore,
		List<String> topTags,
		int rank) {
}
