package com.application.playlistcreator.dto;

import java.util.List;

public record PreviewDiscoverSimilarArtistsRequest(List<SelectedArtistRequest> selectedArtists) {
}
