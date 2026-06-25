package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.service.SimilarArtistsPlaylistService.SourceArtistSearchResult;

public record PreviewSimilarSourceArtistsResponse(
		String query,
		List<Artist> artists,
		int artistCount) {

	public static PreviewSimilarSourceArtistsResponse from(SourceArtistSearchResult result) {
		return new PreviewSimilarSourceArtistsResponse(
				result.query(),
				result.artists().stream().map(Artist::from).toList(),
				result.artists().size());
	}

	public record Artist(String id, String name, String spotifyUrl) {

		static Artist from(SourceArtistSearchResult.Artist result) {
			return new Artist(result.id(), result.name(), result.spotifyUrl());
		}
	}
}
