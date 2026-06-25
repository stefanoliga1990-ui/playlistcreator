package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.model.GenreArtistCandidate;
import com.application.playlistcreator.model.GenrePlaylistSelection;
import com.application.playlistcreator.model.GenreTrackCandidate;

public record PreviewGenreTracksResponse(
		String genre,
		List<GenreArtist> artists,
		int artistCount,
		List<GenreTrack> tracks,
		int trackCount) {

	public static PreviewGenreTracksResponse from(GenrePlaylistSelection selection) {
		return new PreviewGenreTracksResponse(
				selection.genre(),
				selection.artists().stream().map(GenreArtist::from).toList(),
				selection.artists().size(),
				selection.tracks().stream().map(GenreTrack::from).toList(),
				selection.tracks().size());
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
}
