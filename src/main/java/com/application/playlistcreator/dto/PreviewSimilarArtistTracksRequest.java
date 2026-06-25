package com.application.playlistcreator.dto;

import java.util.List;

public record PreviewSimilarArtistTracksRequest(
		String sourceArtistName,
		List<String> selectedArtistNames,
		Integer tracksPerArtist) {
}
