package com.application.playlistcreator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.application.playlistcreator.client.lastfm.LastFmClient;
import com.application.playlistcreator.client.lastfm.LastFmClient.TopTracks;
import com.application.playlistcreator.client.lastfm.LastFmClient.TopTracksResponse;
import com.application.playlistcreator.client.spotify.SpotifyApiClient;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.AlbumTracksPage;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.AlbumsPage;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.Artist;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.Artists;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.SearchArtistsResponse;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.SimplifiedAlbum;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.SimplifiedTrack;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.SpotifyArtist;
import org.junit.jupiter.api.Test;

class DiscographyPlaylistServiceTest {

	@Test
	void filtersLiveAndCollectionsAndConsolidatesEditionsChronologically() {
		SpotifyApiClient spotify = mock(SpotifyApiClient.class);
		LastFmClient lastFm = mock(LastFmClient.class);
		when(spotify.searchArtists("token", "Test Artist"))
				.thenReturn(new SearchArtistsResponse(new Artists(List.of(
						new SpotifyArtist("artist-1", "Test Artist", 80, null, Map.of())))));
		when(spotify.getArtistAlbums("token", "artist-1", 10, 0))
				.thenReturn(new AlbumsPage(List.of(
						album("a2", "Second Album", "2002-05-10", 10),
						album("live", "Live at Somewhere", "2003", 12),
						album("hits", "Greatest Hits", "2004", 15),
						album("a1-deluxe", "First Album - Deluxe Edition", "2020", 20),
						album("a1", "First Album", "1999-02-01", 11)),
						5, 10, 0, null));
		DiscographyPlaylistService service = new DiscographyPlaylistService(
				spotify, lastFm, new SongNormalizer());

		var result = service.findAlbums("token", "Test Artist");

		assertThat(result.albums()).extracting(album -> album.id())
				.containsExactly("a1", "a2");
		assertThat(result.filteredAlbumCount()).isEqualTo(3);
	}

	@Test
	void acceptsAlbumWhenDeprecatedAlbumGroupIsMissing() {
		SpotifyApiClient spotify = mock(SpotifyApiClient.class);
		LastFmClient lastFm = mock(LastFmClient.class);
		when(spotify.searchArtists("token", "Test Artist"))
				.thenReturn(new SearchArtistsResponse(new Artists(List.of(
						new SpotifyArtist("artist-1", "Test Artist", 80, null, Map.of())))));
		when(spotify.getArtistAlbums("token", "artist-1", 10, 0))
				.thenReturn(new AlbumsPage(List.of(
						new SimplifiedAlbum(
								"a1", "First Album", "album", null, "1999", "year", 10,
								List.of(new Artist("artist-1", "Test Artist")), Map.of())),
						1, 10, 0, null));
		DiscographyPlaylistService service = new DiscographyPlaylistService(
				spotify, lastFm, new SongNormalizer());

		var result = service.findAlbums("token", "Test Artist");

		assertThat(result.albums()).extracting(album -> album.id()).containsExactly("a1");
	}

	@Test
	void ranksEachAlbumWithLastFmPlaycountAndAlbumOrderFallback() {
		SpotifyApiClient spotify = mock(SpotifyApiClient.class);
		LastFmClient lastFm = mock(LastFmClient.class);
		when(spotify.searchArtists("token", "Test Artist"))
				.thenReturn(new SearchArtistsResponse(new Artists(List.of(
						new SpotifyArtist("artist-1", "Test Artist", 80, null, Map.of())))));
		when(spotify.getArtistAlbums("token", "artist-1", 10, 0))
				.thenReturn(new AlbumsPage(List.of(album("a1", "First Album", "1999", 3)),
						1, 10, 0, null));
		when(spotify.getAlbumTracks("token", "a1", 50, 0))
				.thenReturn(new AlbumTracksPage(List.of(
						simplifiedTrack("t1", "Track One", 1),
						simplifiedTrack("t2", "Track Two", 2),
						simplifiedTrack("t3", "Track Three", 3)),
						3, 50, 0, null));
		when(lastFm.getArtistTopTracks("Test Artist", 1000))
				.thenReturn(new TopTracksResponse(new TopTracks(List.of(
						lastFmTrack("Track One", "5000"),
						lastFmTrack("Track Three", "1000"))), null, null));
		DiscographyPlaylistService service = new DiscographyPlaylistService(
				spotify, lastFm, new SongNormalizer());
		var albums = service.findAlbums("token", "Test Artist");

		var result = service.selectTracks(
				"token",
				albums.artistId(),
				albums.artistName(),
				List.of("a1"),
				2);

		assertThat(result.tracks()).extracting(track -> track.title())
				.containsExactly("Track One", "Track Three");
		assertThat(result.tracks()).extracting(track -> track.rankingSource())
				.containsExactly("LAST_FM", "LAST_FM");
	}

	private SimplifiedAlbum album(String id, String name, String releaseDate, int tracks) {
		return new SimplifiedAlbum(
				id, name, "album", "album", releaseDate, "day", tracks,
				List.of(new Artist("artist-1", "Test Artist")), Map.of());
	}

	private SimplifiedTrack simplifiedTrack(String id, String name, int trackNumber) {
		return new SimplifiedTrack(
				id, name, "spotify:track:" + id, trackNumber, 1, true,
				List.of(new Artist("artist-1", "Test Artist")));
	}

	private LastFmClient.Track lastFmTrack(String name, String playcount) {
		return new LastFmClient.Track(name, null, "100", playcount, null);
	}
}
