package com.application.playlistcreator.dto;

import java.util.List;

public record GenerateSimilarArtistsPlaylistRequest(
		String sourceArtistName,
		List<String> selectedArtistNames,
		Integer tracksPerArtist,
		List<SelectedTrackRequest> selectedTracks,
		String playlistName,
		String playlistDescription) {
}
