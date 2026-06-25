package com.application.playlistcreator.dto;

import java.util.List;

public record PreviewGenreTracksRequest(
		String genre,
		Integer artistLimit,
		List<String> selectedArtistNames,
		Integer tracksPerArtist) {
}
