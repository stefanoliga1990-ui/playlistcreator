package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.service.DiscoverNewMusicService.DiscoveryArtist;
import com.application.playlistcreator.service.DiscoverNewMusicService.SimilarArtistsResult;

public record PreviewDiscoverSimilarArtistsResponse(
		List<Artist> artists,
		int artistCount,
		String warning) {

	public static PreviewDiscoverSimilarArtistsResponse from(SimilarArtistsResult result) {
		return new PreviewDiscoverSimilarArtistsResponse(
				result.artists().stream().map(Artist::from).toList(),
				result.artists().size(),
				result.warning());
	}

	public record Artist(
			String id,
			String name,
			String spotifyUrl,
			List<String> basedOnArtists) {

		static Artist from(DiscoveryArtist artist) {
			return new Artist(
					artist.id(),
					artist.name(),
					artist.spotifyUrl(),
					artist.basedOnArtists());
		}
	}
}
