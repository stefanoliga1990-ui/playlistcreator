package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.model.GenreArtistCandidate;
import com.application.playlistcreator.model.GenrePlaylistSelection;
import com.application.playlistcreator.model.GenreTrackCandidate;
import com.application.playlistcreator.model.GenreTrackMatch;
import com.application.playlistcreator.model.SpotifyTrackMatch.MatchStatus;
import com.application.playlistcreator.service.GenrePlaylistService;

public record GenerateGenrePlaylistResponse(
		String genre,
		List<GenreArtist> artists,
		int artistCount,
		List<GenreTrack> tracks,
		int trackCount,
		Playlist playlist,
		List<TrackMatch> trackMatches,
		int addedTracksCount,
		int uncertainTracksCount,
		int notFoundTracksCount,
		List<String> excludedTracks) {

	public static GenerateGenrePlaylistResponse from(GenrePlaylistService.GenreGenerationResult result) {
		GenrePlaylistSelection selection = result.selection();
		int uncertainTracksCount = (int) result.matches().stream()
				.filter(match -> match.spotifyMatch().status() == MatchStatus.UNCERTAIN)
				.count();
		int notFoundTracksCount = (int) result.matches().stream()
				.filter(match -> match.spotifyMatch().status() == MatchStatus.NOT_FOUND)
				.count();
		return new GenerateGenrePlaylistResponse(
				selection.genre(),
				selection.artists().stream().map(GenreArtist::from).toList(),
				selection.artists().size(),
				selection.tracks().stream().map(GenreTrack::from).toList(),
				selection.tracks().size(),
				new Playlist(result.playlistId(), result.playlistName(), result.playlistUrl()),
				result.matches().stream().map(TrackMatch::from).toList(),
				result.addedTracksCount(),
				uncertainTracksCount,
				notFoundTracksCount,
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

	public record GenreArtist(
			String name,
			String musicBrainzId,
			String lastFmUrl,
			long listeners,
			int rank) {

		static GenreArtist from(GenreArtistCandidate artist) {
			return new GenreArtist(
					artist.name(),
					artist.musicBrainzId(),
					artist.lastFmUrl(),
					artist.listeners(),
					artist.rank());
		}
	}

	public record GenreTrack(
			String artistName,
			String title,
			String lastFmUrl,
			long listeners,
			long playcount,
			int artistRank,
			int trackRank) {

		static GenreTrack from(GenreTrackCandidate track) {
			return new GenreTrack(
					track.artistName(),
					track.title(),
					track.lastFmUrl(),
					track.listeners(),
					track.playcount(),
					track.artistRank(),
					track.trackRank());
		}
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
