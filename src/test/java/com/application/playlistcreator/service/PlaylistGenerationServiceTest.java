package com.application.playlistcreator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.application.playlistcreator.client.spotify.SpotifyApiClient;
import com.application.playlistcreator.dto.SelectedTrackRequest;
import com.application.playlistcreator.dto.GeneratePlaylistResponse;
import com.application.playlistcreator.model.ArtistCandidate;
import com.application.playlistcreator.model.CandidateSong;
import com.application.playlistcreator.model.SetlistSelection;
import com.application.playlistcreator.model.SpotifyTrackMatch;
import com.application.playlistcreator.model.SpotifyTrackMatch.MatchStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlaylistGenerationServiceTest {

	@Test
	void matchesAndAddsOnlyTracksSelectedByTheUser() {
		SetlistService setlistService = mock(SetlistService.class);
		SpotifyTrackMatchingService matchingService = mock(SpotifyTrackMatchingService.class);
		SpotifyApiClient spotify = mock(SpotifyApiClient.class);
		CandidateSong first = song("Song One", 1);
		CandidateSong second = song("Song Two", 2);
		when(setlistService.selectProbableSongs(null, "Test Artist"))
				.thenReturn(new SetlistSelection(
						new ArtistCandidate("mbid", "Test Artist", "Test Artist", null, "url"),
						List.of(),
						List.of(first, second)));
		when(matchingService.matchTracks(
				org.mockito.ArgumentMatchers.eq("token"),
				org.mockito.ArgumentMatchers.eq("Test Artist"),
				anyList()))
				.thenReturn(List.of(new SpotifyTrackMatch(
						second, "Song Two", List.of("Test Artist"),
						"spotify:track:2", 90, MatchStatus.MATCHED)));
		when(spotify.getCurrentUser("token"))
				.thenReturn(new SpotifyApiClient.CurrentUser("user", "User", null));
		when(spotify.createPlaylist("token", "Playlist", "Description", true))
				.thenReturn(new SpotifyApiClient.Playlist(
						"playlist", "Playlist", "spotify:playlist:1", true,
						Map.of("spotify", "https://open.spotify.com/playlist/1")));
		PlaylistGenerationService service = new PlaylistGenerationService(
				setlistService, matchingService, spotify, new SongNormalizer());

		var result = service.generatePlaylist(
				"token",
				"Test Artist",
				"Playlist",
				"Description",
				List.of(new SelectedTrackRequest(null, "Test Artist", "Song Two")));

		ArgumentCaptor<List<CandidateSong>> songs = ArgumentCaptor.forClass(List.class);
		verify(matchingService).matchTracks(
				org.mockito.ArgumentMatchers.eq("token"),
				org.mockito.ArgumentMatchers.eq("Test Artist"),
				songs.capture());
		assertThat(songs.getValue()).extracting(CandidateSong::title).containsExactly("Song Two");
		verify(spotify).addItemsToPlaylist("token", "playlist", List.of("spotify:track:2"));
		assertThat(result.recentSongs()).extracting(song -> song.title()).containsExactly("Song Two");
	}

	@Test
	void reportsTracksWithoutReliableSpotifyMatchAsExcluded() {
		CandidateSong matchedSong = song("Matched Song", 1);
		CandidateSong uncertainSong = song("Uncertain Song", 2);
		SetlistSelection selection = new SetlistSelection(
				new ArtistCandidate("mbid", "Test Artist", "Test Artist", null, "url"),
				List.of(),
				List.of(matchedSong, uncertainSong));
		List<SpotifyTrackMatch> matches = List.of(
				new SpotifyTrackMatch(
						matchedSong, "Matched Song", List.of("Test Artist"),
						"spotify:track:1", 90, MatchStatus.MATCHED),
				new SpotifyTrackMatch(
						uncertainSong, "Uncertain Song - Remastered", List.of("Test Artist"),
						"spotify:track:2", 75, MatchStatus.UNCERTAIN));

		var response = GeneratePlaylistResponse.from(
				selection, matches, "playlist", "Playlist", "url", 1);

		assertThat(response.excludedTracks()).containsExactly("Uncertain Song");
	}

	private CandidateSong song(String title, int position) {
		return new CandidateSong(title, title.toLowerCase(), position, position, false, null);
	}
}
