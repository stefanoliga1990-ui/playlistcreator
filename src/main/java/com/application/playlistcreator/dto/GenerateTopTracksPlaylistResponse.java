package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.service.TopTracksPlaylistService.GenerationResult;

public record GenerateTopTracksPlaylistResponse(
		int requestedTrackCount,
		int availableTrackCount,
		int months,
		Playlist playlist,
		int addedTracksCount,
		String warning,
		List<String> excludedTracks) {

	public static GenerateTopTracksPlaylistResponse from(GenerationResult result) {
		return new GenerateTopTracksPlaylistResponse(
				result.selection().requestedLimit(),
				result.selection().tracks().size(),
				result.selection().months(),
				new Playlist(result.playlistId(), result.playlistName(), result.playlistUrl()),
				result.addedTracksCount(),
				result.selection().warning(),
				List.of());
	}

	public record Playlist(String id, String name, String spotifyUrl) {
	}
}
