package com.application.playlistcreator.model;

import java.util.List;

public record SetlistSelection(
		ArtistCandidate artist,
		List<ConcertSetlist> sourceSetlists,
		List<CandidateSong> recentSongs) {
}
