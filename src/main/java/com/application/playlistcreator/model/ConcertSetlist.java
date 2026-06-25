package com.application.playlistcreator.model;

import java.time.LocalDate;
import java.util.List;

public record ConcertSetlist(
		String id,
		LocalDate eventDate,
		String url,
		String venueName,
		String cityName,
		String countryName,
		List<SetlistSong> songs) {
}
