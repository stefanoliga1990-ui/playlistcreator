package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.model.DiscographyAlbum;
import com.application.playlistcreator.model.DiscographyTrack;
import com.application.playlistcreator.service.DiscographyPlaylistService.AlbumTrackSelection;
import com.application.playlistcreator.service.DiscographyPlaylistService.TrackSelectionResult;

public record PreviewDiscographyTracksResponse(
		String artistId,
		String artistName,
		List<Album> albums,
		int albumCount,
		int trackCount,
		int tracksPerAlbum) {

	public static PreviewDiscographyTracksResponse from(TrackSelectionResult result) {
		return new PreviewDiscographyTracksResponse(
				result.artistId(),
				result.artistName(),
				result.albums().stream().map(Album::from).toList(),
				result.albums().size(),
				result.tracks().size(),
				result.tracksPerAlbum());
	}

	public record Album(
			String id,
			String name,
			int releaseYear,
			List<Track> tracks) {
		static Album from(AlbumTrackSelection selection) {
			DiscographyAlbum album = selection.album();
			return new Album(album.id(), album.name(), album.releaseYear(),
					selection.tracks().stream().map(Track::from).toList());
		}
	}

	public record Track(
			String id,
			String title,
			String uri,
			int selectedRank,
			long lastFmPlaycount,
			String rankingSource) {
		static Track from(DiscographyTrack track) {
			return new Track(track.id(), track.title(), track.uri(), track.selectedRank(),
					track.lastFmPlaycount(), track.rankingSource());
		}
	}
}
