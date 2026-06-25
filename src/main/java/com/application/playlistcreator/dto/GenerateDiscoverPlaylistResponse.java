package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.service.DiscoverNewMusicService.GenerationResult;

public record GenerateDiscoverPlaylistResponse(
		Playlist playlist,
		List<PreviewDiscoverTracksResponse.Track> tracks,
		int addedTracksCount,
		String warning) {

	public static GenerateDiscoverPlaylistResponse from(GenerationResult result) {
		return new GenerateDiscoverPlaylistResponse(
				new Playlist(
						result.playlistId(),
						result.playlistName(),
						result.playlistUrl()),
				result.selection().tracks().stream()
						.map(track -> new PreviewDiscoverTracksResponse.Track(
								track.id(),
								track.title(),
								track.artistName(),
								track.uri(),
								track.matchScore(),
								track.knownArtist(),
								track.basedOnArtists()))
						.toList(),
				result.addedTracksCount(),
				result.selection().warning());
	}

	public record Playlist(String id, String name, String spotifyUrl) {
	}
}
