package com.application.playlistcreator.dto;

import java.time.LocalDate;
import java.util.List;

import com.application.playlistcreator.model.CandidateSong;
import com.application.playlistcreator.model.ConcertSetlist;
import com.application.playlistcreator.model.SetlistSelection;

public record PreviewSetlistResponse(
		Artist artist,
		List<SourceSetlist> sourceSetlists,
		List<CommonSong> recentSongs,
		int recentSongsCount) {

	public static PreviewSetlistResponse from(SetlistSelection selection) {
		return new PreviewSetlistResponse(
				new Artist(selection.artist().musicBrainzId(), selection.artist().name(), selection.artist().url()),
				selection.sourceSetlists().stream().map(SourceSetlist::from).toList(),
				selection.recentSongs().stream().map(CommonSong::from).toList(),
				selection.recentSongs().size());
	}

	public record Artist(String musicBrainzId, String name, String setlistFmUrl) {
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
}
