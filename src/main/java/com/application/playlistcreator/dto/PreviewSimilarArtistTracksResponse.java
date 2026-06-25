package com.application.playlistcreator.dto;

import java.util.List;

import com.application.playlistcreator.model.GenreTrackCandidate;
import com.application.playlistcreator.model.SimilarArtistCandidate;
import com.application.playlistcreator.model.SimilarArtistsPlaylistSelection;

public record PreviewSimilarArtistTracksResponse(
		String sourceArtistName,
		List<Artist> artists,
		int artistCount,
		List<Track> tracks,
		int trackCount,
		int tracksPerArtist,
		String warning) {

	public static PreviewSimilarArtistTracksResponse from(SimilarArtistsPlaylistSelection selection) {
		return new PreviewSimilarArtistTracksResponse(
				selection.sourceArtistName(),
				selection.artists().stream().map(Artist::from).toList(),
				selection.artists().size(),
				selection.tracks().stream().map(Track::from).toList(),
				selection.tracks().size(),
				selection.tracksPerArtist(),
				selection.warning());
	}

	public record Artist(String name, int rank, int similarityScore) {
		static Artist from(SimilarArtistCandidate artist) {
			return new Artist(artist.name(), artist.rank(), artist.similarityScore());
		}
	}

	public record Track(
			String artistName,
			String title,
			String lastFmUrl,
			long listeners,
			long playcount,
			int artistRank,
			int trackRank) {
		static Track from(GenreTrackCandidate track) {
			return new Track(
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
