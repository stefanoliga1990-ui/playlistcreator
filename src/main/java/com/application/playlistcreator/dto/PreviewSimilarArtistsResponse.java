package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.model.SimilarArtistCandidate;
import com.application.playlistcreator.service.SimilarArtistsPlaylistService.ArtistSearchResult;

public record PreviewSimilarArtistsResponse(
		String sourceArtistName,
		List<SimilarArtist> artists,
		int artistCount,
		int checkedCandidateCount,
		String warning) {

	public static PreviewSimilarArtistsResponse from(ArtistSearchResult result) {
		return new PreviewSimilarArtistsResponse(
				result.sourceArtistName(),
				result.artists().stream().map(SimilarArtist::from).toList(),
				result.artists().size(),
				result.checkedCandidateCount(),
				result.warning());
	}

	public record SimilarArtist(
			String name,
			String musicBrainzId,
			String lastFmUrl,
			double directSimilarity,
			double reciprocalSimilarity,
			double tagSimilarity,
			int similarityScore,
			List<String> topTags,
			int rank) {

		static SimilarArtist from(SimilarArtistCandidate artist) {
			return new SimilarArtist(
					artist.name(),
					artist.musicBrainzId(),
					artist.lastFmUrl(),
					artist.directSimilarity(),
					artist.reciprocalSimilarity(),
					artist.tagSimilarity(),
					artist.similarityScore(),
					artist.topTags(),
					artist.rank());
		}
	}
}
