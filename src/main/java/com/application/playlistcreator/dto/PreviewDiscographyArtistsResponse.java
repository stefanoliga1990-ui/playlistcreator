package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.service.DiscographyPlaylistService.ArtistSearchResult;

public record PreviewDiscographyArtistsResponse(
		String query,
		List<Artist> artists,
		int artistCount) {

	public static PreviewDiscographyArtistsResponse from(ArtistSearchResult result) {
		return new PreviewDiscographyArtistsResponse(
				result.query(),
				result.artists().stream().map(Artist::from).toList(),
				result.artists().size());
	}

	public record Artist(
			String id,
			String name,
			int popularity,
			int followers,
			String spotifyUrl) {

		static Artist from(ArtistSearchResult.Artist result) {
			return new Artist(
					result.id(),
					result.name(),
					result.popularity(),
					result.followers(),
					result.spotifyUrl());
		}
	}
}
