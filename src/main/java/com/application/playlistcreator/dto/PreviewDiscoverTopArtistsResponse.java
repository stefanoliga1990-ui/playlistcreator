package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.service.DiscoverNewMusicService.TopArtist;
import com.application.playlistcreator.service.DiscoverNewMusicService.TopArtistsResult;

public record PreviewDiscoverTopArtistsResponse(
		List<Artist> artists,
		int artistCount,
		String warning) {

	public static PreviewDiscoverTopArtistsResponse from(TopArtistsResult result) {
		return new PreviewDiscoverTopArtistsResponse(
				result.artists().stream().map(Artist::from).toList(),
				result.artists().size(),
				result.warning());
	}

	public record Artist(
			int rank,
			String id,
			String name,
			String spotifyUrl) {

		static Artist from(TopArtist artist) {
			return new Artist(
					artist.rank(),
					artist.id(),
					artist.name(),
					artist.spotifyUrl());
		}
	}
}
