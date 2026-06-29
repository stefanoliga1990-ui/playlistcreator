package com.application.playlistcreator.service;

import java.util.List;

import com.application.playlistcreator.client.spotify.SpotifyApiClient;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.Album;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.Artist;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.SearchTracksResponse;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.Track;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.Tracks;
import com.application.playlistcreator.model.CandidateSong;
import com.application.playlistcreator.model.SpotifyTrackMatch.MatchStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpotifyTrackMatchingServiceTest {

	private final SpotifyApiClient spotifyApiClient = mock(SpotifyApiClient.class);
	private final SongNormalizer songNormalizer = new SongNormalizer();
	private final SpotifyTrackMatchingService service =
			new SpotifyTrackMatchingService(spotifyApiClient, songNormalizer);

	@Test
	void acceptsSpotifyFeaturingSuffixForSetlistTrack() {
		stubSearch(track(
				"Sciarponi (feat Sanguedecane)",
				0,
				List.of("Nu Genea", "Sanguedecane")));

		var match = service.matchTracks("token", "Nu Genea", List.of(song("Sciarponi"))).get(0);

		assertThat(match.status()).isEqualTo(MatchStatus.MATCHED);
		assertThat(match.spotifyTrackName()).isEqualTo("Sciarponi (feat Sanguedecane)");
	}

	@Test
	void acceptsSafeDashExtensionForSetlistTrack() {
		stubSearch(track("Miguel - Planet", 0, List.of("Nu Genea")));

		var match = service.matchTracks("token", "Nu Genea", List.of(song("Miguel"))).get(0);

		assertThat(match.status()).isEqualTo(MatchStatus.MATCHED);
		assertThat(match.spotifyTrackName()).isEqualTo("Miguel - Planet");
		assertThat(match.score()).isGreaterThanOrEqualTo(85);
	}

	@Test
	void rejectsRiskyExpansionForShortGenericTitle() {
		stubSearch(track("One More Time", 100, List.of("Metallica")));

		var match = service.matchTracks("token", "Metallica", List.of(song("One"))).get(0);

		assertThat(match.status()).isNotEqualTo(MatchStatus.MATCHED);
	}

	@Test
	void rejectsEvenSeparatedExpansionForShortGenericTitle() {
		stubSearch(track("One - More Time", 100, List.of("Metallica")));

		var match = service.matchTracks("token", "Metallica", List.of(song("One"))).get(0);

		assertThat(match.status()).isNotEqualTo(MatchStatus.MATCHED);
	}

	@Test
	void rejectsSafeLookingExtensionWhenPrimaryArtistDoesNotMatch() {
		stubSearch(track("Miguel - Planet", 100, List.of("Different Artist", "Nu Genea")));

		var match = service.matchTracks("token", "Nu Genea", List.of(song("Miguel"))).get(0);

		assertThat(match.status()).isNotEqualTo(MatchStatus.MATCHED);
	}

	@Test
	void rejectsLiveExtension() {
		stubSearch(new Track(
				"track-id",
				"Miguel - Planet (Live)",
				"spotify:track:track-id",
				true,
				100,
				List.of(new Artist("artist-0", "Nu Genea")),
				new Album("album-id", "Live at Test Venue", "album")));

		var match = service.matchTracks("token", "Nu Genea", List.of(song("Miguel"))).get(0);

		assertThat(match.status()).isNotEqualTo(MatchStatus.MATCHED);
	}

	private void stubSearch(Track track) {
		when(spotifyApiClient.searchTracks(anyString(), anyString()))
				.thenReturn(new SearchTracksResponse(new Tracks(List.of(track))));
	}

	private CandidateSong song(String title) {
		return new CandidateSong(title, songNormalizer.normalizeTitle(title), 1, 1, false, null);
	}

	private Track track(String title, int popularity, List<String> artists) {
		List<Artist> spotifyArtists = java.util.stream.IntStream.range(0, artists.size())
				.mapToObj(index -> new Artist("artist-" + index, artists.get(index)))
				.toList();
		return new Track(
				"track-id",
				title,
				"spotify:track:track-id",
				true,
				popularity,
				spotifyArtists,
				new Album("album-id", "Studio Album", "album"));
	}
}
