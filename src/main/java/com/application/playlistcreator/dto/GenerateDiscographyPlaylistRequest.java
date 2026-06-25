package com.application.playlistcreator.dto;

import java.util.List;

public record GenerateDiscographyPlaylistRequest(
		String artistId,
		String artistName,
		List<String> includedAlbumIds,
		Integer tracksPerAlbum,
		List<SelectedTrackRequest> selectedTracks,
		String playlistName,
		String playlistDescription) {
}
