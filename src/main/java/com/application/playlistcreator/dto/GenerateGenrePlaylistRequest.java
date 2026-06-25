package com.application.playlistcreator.dto;

import java.util.List;

public record GenerateGenrePlaylistRequest(
		String genre,
		Integer artistLimit,
		List<String> selectedArtistNames,
		Integer tracksPerArtist,
		List<SelectedTrackRequest> selectedTracks,
		String playlistName,
		String playlistDescription) {
}
