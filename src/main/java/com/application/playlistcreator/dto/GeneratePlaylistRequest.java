package com.application.playlistcreator.dto;

import java.util.List;

public record GeneratePlaylistRequest(
		String artistName,
		List<SelectedTrackRequest> selectedTracks,
		String playlistName,
		String playlistDescription) {
}
