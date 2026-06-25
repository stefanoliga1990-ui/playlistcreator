package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.service.DiscographyPlaylistService.GenerationResult;

public record GenerateDiscographyPlaylistResponse(
		String artistName,
		int albumCount,
		int trackCount,
		Playlist playlist,
		int addedTracksCount,
		List<String> excludedTracks) {

	public static GenerateDiscographyPlaylistResponse from(GenerationResult result) {
		return new GenerateDiscographyPlaylistResponse(
				result.selection().artistName(),
				result.selection().albums().size(),
				result.selection().tracks().size(),
				new Playlist(result.playlistId(), result.playlistName(), result.playlistUrl()),
				result.addedTracksCount(),
				List.of());
	}

	public record Playlist(String id, String name, String spotifyUrl) {
	}
}
