package com.application.playlistcreator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.application.playlistcreator.client.spotify.SpotifyApiClient;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.Album;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.Artist;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.TopTracksPage;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.Track;
import com.application.playlistcreator.dto.SelectedTrackRequest;
import org.junit.jupiter.api.Test;

class TopTracksPlaylistServiceTest {

	@Test
	void loadsTwoSpotifyPagesWhenOneHundredTracksAreRequested() {
		SpotifyApiClient spotify = mock(SpotifyApiClient.class);
		when(spotify.getCurrentUserTopTracks("token", "medium_term", 50, 0))
				.thenReturn(page(tracks(1, 50), 100, 0, "next"));
		when(spotify.getCurrentUserTopTracks("token", "medium_term", 50, 50))
				.thenReturn(page(tracks(51, 100), 100, 50, null));
		TopTracksPlaylistService service = new TopTracksPlaylistService(spotify);

		var result = service.findTopTracks("token", 100, 6);

		assertThat(result.tracks()).hasSize(100);
		assertThat(result.warning()).isNull();
		assertThat(result.tracks().get(99).rank()).isEqualTo(100);
		verify(spotify).getCurrentUserTopTracks("token", "medium_term", 50, 0);
		verify(spotify).getCurrentUserTopTracks("token", "medium_term", 50, 50);
	}

	@Test
	void returnsAvailableTracksAndWarningWhenSpotifyHasFewerItems() {
		SpotifyApiClient spotify = mock(SpotifyApiClient.class);
		when(spotify.getCurrentUserTopTracks("token", "long_term", 25, 0))
				.thenReturn(page(tracks(1, 12), 12, 0, null));
		TopTracksPlaylistService service = new TopTracksPlaylistService(spotify);

		var result = service.findTopTracks("token", 25, 12);

		assertThat(result.tracks()).hasSize(12);
		assertThat(result.warning()).contains("12").contains("25");
		assertThat(result.timeRange()).isEqualTo("long_term");
	}

	@Test
	void mapsOneMonthToSpotifyShortTerm() {
		SpotifyApiClient spotify = mock(SpotifyApiClient.class);
		when(spotify.getCurrentUserTopTracks("token", "short_term", 10, 0))
				.thenReturn(page(tracks(1, 10), 10, 0, null));
		TopTracksPlaylistService service = new TopTracksPlaylistService(spotify);

		var result = service.findTopTracks("token", 10, 1);

		assertThat(result.timeRange()).isEqualTo("short_term");
		assertThat(result.warning()).isNull();
	}

	@Test
	void addsOnlySpotifyTracksSelectedByTheUser() {
		SpotifyApiClient spotify = mock(SpotifyApiClient.class);
		when(spotify.getCurrentUserTopTracks("token", "short_term", 10, 0))
				.thenReturn(page(tracks(1, 3), 3, 0, null));
		when(spotify.createPlaylist("token", "Playlist", "Description", true))
				.thenReturn(new SpotifyApiClient.Playlist(
						"playlist", "Playlist", "spotify:playlist:1", true,
						Map.of("spotify", "https://open.spotify.com/playlist/1")));
		TopTracksPlaylistService service = new TopTracksPlaylistService(spotify);

		var result = service.generatePlaylist(
				"token",
				10,
				1,
				List.of(new SelectedTrackRequest("track-2", "Artist 2", "Track 2")),
				"Playlist",
				"Description");

		verify(spotify).addItemsToPlaylist("token", "playlist", List.of("spotify:track:2"));
		assertThat(result.selection().tracks()).extracting(track -> track.id()).containsExactly("track-2");
	}

	private TopTracksPage page(List<Track> tracks, int total, int offset, String next) {
		return new TopTracksPage(tracks, total, tracks.size(), offset, next);
	}

	private List<Track> tracks(int start, int end) {
		return IntStream.rangeClosed(start, end)
				.mapToObj(this::track)
				.toList();
	}

	private Track track(int index) {
		return new Track(
				"track-" + index,
				"Track " + index,
				"spotify:track:" + index,
				true,
				null,
				List.of(new Artist("artist-" + index, "Artist " + index)),
				new Album("album-" + index, "Album " + index, "album"));
	}
}
