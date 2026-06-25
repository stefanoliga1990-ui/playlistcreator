package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.model.GenreArtistCandidate;
import com.application.playlistcreator.service.GenrePlaylistService.GenreArtistSearchResult;

public record PreviewGenreArtistsResponse(
		String genre,
		List<GenreArtist> artists,
		int artistCount,
		int requestedArtistCount,
		int checkedCandidateCount,
		String warning) {

	public static PreviewGenreArtistsResponse from(GenreArtistSearchResult result) {
		return new PreviewGenreArtistsResponse(
				result.genre(),
				result.artists().stream().map(GenreArtist::from).toList(),
				result.artists().size(),
				result.requestedArtistCount(),
				result.checkedCandidateCount(),
				result.warning());
	}

	public record GenreArtist(
			String name,
			String musicBrainzId,
			String lastFmUrl,
			long listeners,
			int rank) {

		static GenreArtist from(GenreArtistCandidate artist) {
			return new GenreArtist(
					artist.name(),
					artist.musicBrainzId(),
					artist.lastFmUrl(),
					artist.listeners(),
					artist.rank());
		}
	}
}
