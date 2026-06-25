package com.application.playlistcreator.dto;

import java.util.List;

public record PreviewDiscoverTracksRequest(
		List<SelectedArtistRequest> sourceArtists,
		List<SelectedArtistRequest> selectedSimilarArtists) {
}
