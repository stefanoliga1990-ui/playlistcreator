package com.application.playlistcreator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.application.playlistcreator.client.lastfm.LastFmClient;
import com.application.playlistcreator.client.lastfm.LastFmClient.SimilarArtist;
import com.application.playlistcreator.client.lastfm.LastFmClient.SimilarArtists;
import com.application.playlistcreator.client.lastfm.LastFmClient.SimilarArtistsAttributes;
import com.application.playlistcreator.client.lastfm.LastFmClient.SimilarArtistsResponse;
import com.application.playlistcreator.client.lastfm.LastFmClient.TopTracks;
import com.application.playlistcreator.client.lastfm.LastFmClient.TopTracksResponse;
import com.application.playlistcreator.client.spotify.SpotifyApiClient;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.Artist;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.Artists;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.CurrentUser;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.RecentlyPlayedResponse;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.SavedTracksPage;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.SearchArtistsResponse;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.SpotifyArtist;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.TopArtistsPage;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.TopTracksPage;
import com.application.playlistcreator.dto.SelectedArtistRequest;
import com.application.playlistcreator.model.CandidateSong;
import com.application.playlistcreator.model.GenreTrackMatch;
import com.application.playlistcreator.model.SpotifyTrackMatch;
import com.application.playlistcreator.model.SpotifyTrackMatch.MatchStatus;
import org.junit.jupiter.api.Test;

class DiscoverNewMusicServiceTest {

	@Test
	void reportsWhenSpotifyReturnsFewerThanTenTopArtists() {
		SpotifyApiClient spotify = mock(SpotifyApiClient.class);
		when(spotify.getCurrentUser("token"))
				.thenReturn(new CurrentUser("user", "User", null));
		when(spotify.getCurrentUserTopArtists("token", "medium_term", 10, 0))
				.thenReturn(new TopArtistsPage(
						List.of(spotifyArtist("source", "Source Artist")),
						1, 10, 0, null));
		DiscoverNewMusicService service = service(
				spotify, mock(LastFmClient.class), mock(SpotifyTrackMatchingService.class));

		var result = service.findTopArtists("token");

		assertThat(result.artists()).extracting(artist -> artist.name())
				.containsExactly("Source Artist");
		assertThat(result.warning()).contains("only 1 of the 10 requested artists");
	}

	@Test
	void excludesTracksAlreadyPresentInTheSpotifyProfile() {
		SpotifyApiClient spotify = mock(SpotifyApiClient.class);
		LastFmClient lastFm = mock(LastFmClient.class);
		SpotifyTrackMatchingService matching = mock(SpotifyTrackMatchingService.class);
		when(spotify.getCurrentUser("token"))
				.thenReturn(new CurrentUser("user", "User", null));
		when(spotify.getCurrentUserTopArtists("token", "medium_term", 10, 0))
				.thenReturn(new TopArtistsPage(
						List.of(spotifyArtist("source", "Source Artist")),
						1, 10, 0, null));
		for (String timeRange : List.of("short_term", "medium_term", "long_term")) {
			when(spotify.getCurrentUserTopArtists("token", timeRange, 50, 0))
					.thenReturn(new TopArtistsPage(List.of(), 0, 50, 0, null));
			when(spotify.getCurrentUserTopTracks("token", timeRange, 50, 0))
					.thenReturn(new TopTracksPage(
							"short_term".equals(timeRange)
									? List.of(spotifyTrack("known-track", "Known Song", "candidate", "Candidate Artist"))
									: List.of(),
							0, 50, 0, null));
		}
		when(spotify.getRecentlyPlayedTracks("token", 50))
				.thenReturn(new RecentlyPlayedResponse(List.of()));
		when(spotify.getSavedTracks("token", 50, 0))
				.thenReturn(new SavedTracksPage(List.of(), 0, 50, 0, null));
		when(lastFm.getSimilarArtists("Source Artist", 4))
				.thenReturn(new SimilarArtistsResponse(
						new SimilarArtists(
								List.of(new SimilarArtist(
										"Candidate Artist",
										"candidate-mbid",
										"0.9",
										"https://last.fm/candidate")),
								new SimilarArtistsAttributes("Source Artist")),
						null,
						null));
		when(spotify.searchArtists("token", "Candidate Artist"))
				.thenReturn(new SearchArtistsResponse(
						new Artists(List.of(spotifyArtist("candidate", "Candidate Artist")))));
		when(lastFm.getArtistTopTracks("Candidate Artist", 10))
				.thenReturn(new TopTracksResponse(
						new TopTracks(List.of(
								lastFmTrack("Known Song"),
								lastFmTrack("New Song One"),
								lastFmTrack("New Song Two"),
								lastFmTrack("New Song Three"))),
						null,
						null));
		when(matching.matchGenreTrack(
				eq("token"),
				org.mockito.ArgumentMatchers.any(
						com.application.playlistcreator.model.GenreTrackCandidate.class)))
				.thenAnswer(invocation -> {
					var track = (com.application.playlistcreator.model.GenreTrackCandidate) invocation.getArgument(1);
					int index = track.trackRank() - 1;
					String id = index == 0 ? "known-track" : "new-track-" + index;
					CandidateSong song = new CandidateSong(
							track.title(),
							track.normalizedTitle(),
							track.trackRank(),
							track.trackRank(),
							false,
							null);
					return new GenreTrackMatch(
							track,
							new SpotifyTrackMatch(
									song,
									track.title(),
									List.of(track.artistName()),
									"spotify:track:" + id,
									90,
									MatchStatus.MATCHED));
				});
		DiscoverNewMusicService service = service(spotify, lastFm, matching);

		var result = service.findNewTracks(
				"token",
				List.of(new SelectedArtistRequest("source", "Source Artist")),
				List.of(new SelectedArtistRequest("candidate", "Candidate Artist")));

		assertThat(result.tracks()).extracting(track -> track.id())
				.containsExactly("new-track-1", "new-track-2", "new-track-3")
				.doesNotContain("known-track");
		assertThat(result.warning()).contains("Spotify does not expose your complete listening history");
	}

	private DiscoverNewMusicService service(
			SpotifyApiClient spotify,
			LastFmClient lastFm,
			SpotifyTrackMatchingService matching) {
		return new DiscoverNewMusicService(
				spotify, lastFm, matching, new SongNormalizer());
	}

	private SpotifyArtist spotifyArtist(String id, String name) {
		return new SpotifyArtist(id, name, 80, null, Map.of());
	}

	private SpotifyApiClient.Track spotifyTrack(
			String id,
			String name,
			String artistId,
			String artistName) {
		return new SpotifyApiClient.Track(
				id,
				name,
				"spotify:track:" + id,
				true,
				80,
				List.of(new Artist(artistId, artistName)),
				new SpotifyApiClient.Album("album", "Album", "album"));
	}

	private LastFmClient.Track lastFmTrack(String name) {
		return new LastFmClient.Track(name, null, "1000", "5000", null);
	}
}
