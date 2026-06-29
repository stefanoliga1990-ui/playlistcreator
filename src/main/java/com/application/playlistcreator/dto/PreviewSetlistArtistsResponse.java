package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.service.SetlistService.ArtistSearchResult;

public record PreviewSetlistArtistsResponse(
		String query,
		List<Artist> artists,
		int artistCount) {

	public static PreviewSetlistArtistsResponse from(ArtistSearchResult result) {
		List<Artist> artists = result.artists().stream().map(Artist::from).toList();
		return new PreviewSetlistArtistsResponse(result.query(), artists, artists.size());
	}

	public record Artist(String musicBrainzId, String name, String disambiguation, String setlistFmUrl) {

		static Artist from(ArtistSearchResult.Artist artist) {
			return new Artist(
					artist.musicBrainzId(), artist.name(), artist.disambiguation(), artist.setlistFmUrl());
		}
	}
}
