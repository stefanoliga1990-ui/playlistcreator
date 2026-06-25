package com.application.playlistcreator.dto;

import java.util.List;

public record PreviewDiscographyTracksRequest(
		String artistId,
		String artistName,
		List<String> includedAlbumIds,
		Integer tracksPerAlbum) {
}
