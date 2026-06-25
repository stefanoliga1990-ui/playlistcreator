package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.model.DiscographyAlbum;
import com.application.playlistcreator.service.DiscographyPlaylistService.AlbumSearchResult;

public record PreviewDiscographyAlbumsResponse(
		Artist artist,
		List<Album> albums,
		int albumCount,
		int filteredAlbumCount) {

	public static PreviewDiscographyAlbumsResponse from(AlbumSearchResult result) {
		return new PreviewDiscographyAlbumsResponse(
				new Artist(result.artistId(), result.artistName(), result.artistSpotifyUrl()),
				result.albums().stream().map(Album::from).toList(),
				result.albums().size(),
				result.filteredAlbumCount());
	}

	public record Artist(String id, String name, String spotifyUrl) {
	}

	public record Album(
			String id,
			String name,
			String releaseDate,
			int releaseYear,
			int totalTracks,
			String spotifyUrl) {
		static Album from(DiscographyAlbum album) {
			return new Album(album.id(), album.name(), album.releaseDate(), album.releaseYear(),
					album.totalTracks(), album.spotifyUrl());
		}
	}
}
