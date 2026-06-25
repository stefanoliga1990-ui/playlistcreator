package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.model.GenreTrackMatch;
import com.application.playlistcreator.model.SpotifyTrackMatch.MatchStatus;
import com.application.playlistcreator.service.SimilarArtistsPlaylistService.GenerationResult;

public record GenerateSimilarArtistsPlaylistResponse(
		String sourceArtistName,
		int artistCount,
		int trackCount,
		Playlist playlist,
		List<TrackMatch> trackMatches,
		int addedTracksCount,
		int uncertainTracksCount,
		int notFoundTracksCount,
		String warning,
		List<String> excludedTracks) {

	public static GenerateSimilarArtistsPlaylistResponse from(GenerationResult result) {
		int uncertainTracks = (int) result.matches().stream()
				.filter(match -> match.spotifyMatch().status() == MatchStatus.UNCERTAIN)
				.count();
		int notFoundTracks = (int) result.matches().stream()
				.filter(match -> match.spotifyMatch().status() == MatchStatus.NOT_FOUND)
				.count();
		return new GenerateSimilarArtistsPlaylistResponse(
				result.selection().sourceArtistName(),
				result.selection().artists().size(),
				result.selection().tracks().size(),
				new Playlist(result.playlistId(), result.playlistName(), result.playlistUrl()),
				result.matches().stream().map(TrackMatch::from).toList(),
				result.addedTracksCount(),
				uncertainTracks,
				notFoundTracks,
				result.selection().warning(),
				excludedTrackTitles(result.matches()));
	}

	private static List<String> excludedTrackTitles(List<GenreTrackMatch> matches) {
		java.util.Set<String> acceptedUris = new java.util.LinkedHashSet<>();
		java.util.Set<String> excludedTitles = new java.util.LinkedHashSet<>();
		for (GenreTrackMatch match : matches) {
			if (match.spotifyMatch().status() != MatchStatus.MATCHED
					|| match.spotifyMatch().uri() == null
					|| !acceptedUris.add(match.spotifyMatch().uri())) {
				excludedTitles.add(match.track().artistName() + " - " + match.track().title());
			}
		}
		return List.copyOf(excludedTitles);
	}

	public record Playlist(String id, String name, String spotifyUrl) {
	}

	public record TrackMatch(
			String artistName,
			String title,
			String spotifyTrackName,
			List<String> spotifyArtists,
			String uri,
			int score,
			MatchStatus status) {
		static TrackMatch from(GenreTrackMatch match) {
			return new TrackMatch(
					match.track().artistName(),
					match.track().title(),
					match.spotifyMatch().spotifyTrackName(),
					match.spotifyMatch().spotifyArtists(),
					match.spotifyMatch().uri(),
					match.spotifyMatch().score(),
					match.spotifyMatch().status());
		}
	}
}
