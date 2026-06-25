package com.application.playlistcreator.dto;

import java.util.List;

public record GenerateDiscoverPlaylistRequest(
		List<SelectedArtistRequest> sourceArtists,
		List<SelectedArtistRequest> selectedSimilarArtists,
		List<SelectedTrackRequest> selectedTracks,
		String playlistName,
		String playlistDescription) {
}
