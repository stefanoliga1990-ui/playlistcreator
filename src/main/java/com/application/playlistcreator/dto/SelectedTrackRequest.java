package com.application.playlistcreator.dto;

public record SelectedTrackRequest(
		String id,
		String artistName,
		String title) {
}
