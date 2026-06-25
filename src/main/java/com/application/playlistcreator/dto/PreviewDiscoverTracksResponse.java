package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.service.DiscoverNewMusicService.DiscoveryTrack;
import com.application.playlistcreator.service.DiscoverNewMusicService.TracksResult;

public record PreviewDiscoverTracksResponse(
		List<Track> tracks,
		int trackCount,
		int artistCount,
		String warning) {

	public static PreviewDiscoverTracksResponse from(TracksResult result) {
		return new PreviewDiscoverTracksResponse(
				result.tracks().stream().map(Track::from).toList(),
				result.tracks().size(),
				result.similarArtists().size(),
				result.warning());
	}

	public record Track(
			String id,
			String title,
			String artistName,
			String uri,
			int matchScore,
			boolean knownArtist,
			List<String> basedOnArtists) {

		static Track from(DiscoveryTrack track) {
			return new Track(
					track.id(),
					track.title(),
					track.artistName(),
					track.uri(),
					track.matchScore(),
					track.knownArtist(),
					track.basedOnArtists());
		}
	}
}
