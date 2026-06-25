package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.service.TopTracksPlaylistService.SelectionResult;
import com.application.playlistcreator.service.TopTracksPlaylistService.TopTrack;

public record PreviewTopTracksResponse(
		int requestedTrackCount,
		int availableTrackCount,
		int months,
		String timeRange,
		List<Track> tracks,
		String warning) {

	public static PreviewTopTracksResponse from(SelectionResult result) {
		return new PreviewTopTracksResponse(
				result.requestedLimit(),
				result.tracks().size(),
				result.months(),
				result.timeRange(),
				result.tracks().stream().map(Track::from).toList(),
				result.warning());
	}

	public record Track(
			int rank,
			String id,
			String title,
			String uri,
			List<String> artists,
			String albumName) {
		static Track from(TopTrack track) {
			return new Track(
					track.rank(),
					track.id(),
					track.title(),
					track.uri(),
					track.artists(),
					track.albumName());
		}
	}
}
