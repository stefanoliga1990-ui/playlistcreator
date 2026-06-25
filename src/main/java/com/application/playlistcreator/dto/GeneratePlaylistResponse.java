package com.application.playlistcreator.dto;

import java.time.LocalDate;
import java.util.List;

import com.application.playlistcreator.model.CandidateSong;
import com.application.playlistcreator.model.ConcertSetlist;
import com.application.playlistcreator.model.SetlistSelection;
import com.application.playlistcreator.model.SpotifyTrackMatch;
import com.application.playlistcreator.model.SpotifyTrackMatch.MatchStatus;

public record GeneratePlaylistResponse(
		Artist artist,
		Playlist playlist,
		List<SourceSetlist> sourceSetlists,
		List<CommonSong> recentSongs,
		int recentSongsCount,
		List<TrackMatch> trackMatches,
		int addedTracksCount,
		int uncertainTracksCount,
		int notFoundTracksCount,
		List<String> excludedTracks) {

	public static GeneratePlaylistResponse from(SetlistSelection selection, List<SpotifyTrackMatch> matches,
			String playlistId, String playlistName, String playlistUrl, int addedTracksCount) {
		int uncertainTracksCount = (int) matches.stream().filter(match -> match.status() == MatchStatus.UNCERTAIN).count();
		int notFoundTracksCount = (int) matches.stream().filter(match -> match.status() == MatchStatus.NOT_FOUND).count();
		return new GeneratePlaylistResponse(
				new Artist(selection.artist().musicBrainzId(), selection.artist().name(), selection.artist().url()),
				new Playlist(playlistId, playlistName, playlistUrl),
				selection.sourceSetlists().stream().map(SourceSetlist::from).toList(),
				selection.recentSongs().stream().map(CommonSong::from).toList(),
				selection.recentSongs().size(),
				matches.stream().map(TrackMatch::from).toList(),
				addedTracksCount,
				uncertainTracksCount,
				notFoundTracksCount,
				matches.stream()
						.filter(match -> match.status() != MatchStatus.MATCHED)
						.map(match -> match.song().title())
						.distinct()
						.toList());
	}

	public record Artist(String musicBrainzId, String name, String setlistFmUrl) {
	}

	public record Playlist(String id, String name, String spotifyUrl) {
	}

	public record SourceSetlist(
			String id,
			LocalDate eventDate,
			String venueName,
			String cityName,
			String countryName,
			String url,
			int songCount) {

		static SourceSetlist from(ConcertSetlist setlist) {
			return new SourceSetlist(
					setlist.id(),
					setlist.eventDate(),
					setlist.venueName(),
					setlist.cityName(),
					setlist.countryName(),
					setlist.url(),
					setlist.songs().size());
		}
	}

	public record CommonSong(
			String title,
			int latestPosition,
			double averagePosition,
			boolean cover,
			String coverArtist) {

		static CommonSong from(CandidateSong song) {
			return new CommonSong(song.title(), song.latestPosition(), song.averagePosition(), song.cover(), song.coverArtist());
		}
	}

	public record TrackMatch(
			String title,
			String spotifyTrackName,
			List<String> spotifyArtists,
			String uri,
			int score,
			MatchStatus status) {

		static TrackMatch from(SpotifyTrackMatch match) {
			return new TrackMatch(
					match.song().title(),
					match.spotifyTrackName(),
					match.spotifyArtists(),
					match.uri(),
					match.score(),
					match.status());
		}
	}
}
