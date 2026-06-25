package com.application.playlistcreator.dto;

import java.util.List;

public record GenerateTopTracksPlaylistRequest(
		Integer trackLimit,
		Integer months,
		List<SelectedTrackRequest> selectedTracks,
		String playlistName,
		String playlistDescription) {
}
