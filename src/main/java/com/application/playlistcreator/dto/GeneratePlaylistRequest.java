package com.application.playlistcreator.dto;

import java.util.List;

public record GeneratePlaylistRequest(
		String artistMbid,
		String artistName,
		List<SelectedTrackRequest> selectedTracks,
		String playlistName,
		String playlistDescription) {
}
