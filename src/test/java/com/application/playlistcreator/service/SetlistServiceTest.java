package com.application.playlistcreator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.application.playlistcreator.client.setlistfm.SetlistFmClient;
import com.application.playlistcreator.config.PlaylistCreatorProperties;
import com.application.playlistcreator.exception.NoRecentSetlistsException;
import com.application.playlistcreator.exception.SetlistFmArtistNotFoundException;
import org.junit.jupiter.api.Test;

class SetlistServiceTest {

	private static final DateTimeFormatter SETLIST_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	@Test
	void onlyUsesSetlistsFromTheLastSixMonths() {
		SetlistFmClient client = clientWithSetlists(List.of(
				setlist("recent-1", LocalDate.now().minusDays(10)),
				setlist("recent-2", LocalDate.now().minusMonths(2)),
				setlist("recent-3", LocalDate.now().minusMonths(5)),
				setlist("old", LocalDate.now().minusMonths(6).minusDays(1))));
		SetlistService service = new SetlistService(client, properties(), new SongNormalizer());

		var selection = service.selectProbableSongs("Test Artist");

		assertThat(selection.sourceSetlists())
				.extracting(setlist -> setlist.id())
				.containsExactly("recent-1", "recent-2", "recent-3");
	}

	@Test
	void reportsWhenNoSetlistsExistInTheLastSixMonths() {
		SetlistFmClient client = clientWithSetlists(List.of(
				setlist("old", LocalDate.now().minusMonths(6).minusDays(1))));
		SetlistService service = new SetlistService(client, properties(), new SongNormalizer());

		assertThatThrownBy(() -> service.selectProbableSongs("Test Artist"))
				.isInstanceOf(NoRecentSetlistsException.class)
				.hasMessage("No setlists were found for Test Artist in the last 6 months.");
	}

	@Test
	void usesTheOnlyValidRecentSetlistWhenFewerThanThreeAreAvailable() {
		SetlistFmClient client = clientWithSetlists(List.of(
				setlist("recent-1", LocalDate.now().minusDays(10))));
		SetlistService service = new SetlistService(client, properties(), new SongNormalizer());

		var selection = service.selectProbableSongs("Test Artist");

		assertThat(selection.sourceSetlists())
				.extracting(setlist -> setlist.id())
				.containsExactly("recent-1");
	}

	@Test
	void usesTwoValidRecentSetlistsWhenFewerThanThreeAreAvailable() {
		SetlistFmClient client = clientWithSetlists(List.of(
				setlist("recent-1", LocalDate.now().minusDays(10)),
				setlist("recent-2", LocalDate.now().minusMonths(2))));
		SetlistService service = new SetlistService(client, properties(), new SongNormalizer());

		var selection = service.selectProbableSongs("Test Artist");

		assertThat(selection.sourceSetlists())
				.extracting(setlist -> setlist.id())
				.containsExactly("recent-1", "recent-2");
	}

	@Test
	void keepsAlreadyLoadedSetlistsWhenTheYearFallbackReturnsNotFound() {
		SetlistFmClient client = clientWithSetlists(List.of(
				setlist("recent-1", LocalDate.now().minusDays(10))));
		when(client.searchSetlistsByYear(org.mockito.ArgumentMatchers.eq("mbid"), anyInt(), anyInt()))
				.thenThrow(new SetlistFmArtistNotFoundException());
		SetlistService service = new SetlistService(client, properties(), new SongNormalizer());

		var selection = service.selectProbableSongs("Test Artist");

		assertThat(selection.sourceSetlists())
				.extracting(setlist -> setlist.id())
				.containsExactly("recent-1");
	}

	@Test
	void reportsWhenArtistIsNotPresentOnSetlistFm() {
		SetlistFmClient client = mock(SetlistFmClient.class);
		when(client.searchArtists("Unknown Artist", 1))
				.thenThrow(new SetlistFmArtistNotFoundException());
		SetlistService service = new SetlistService(client, properties(), new SongNormalizer());

		assertThatThrownBy(() -> service.selectProbableSongs("Unknown Artist"))
				.isInstanceOf(SetlistFmArtistNotFoundException.class)
				.hasMessage("Artist not found on setlist.fm");
	}

	private SetlistFmClient clientWithSetlists(List<SetlistFmClient.Setlist> setlists) {
		SetlistFmClient client = mock(SetlistFmClient.class);
		when(client.searchArtists("Test Artist", 1))
				.thenReturn(new SetlistFmClient.ArtistSearchResponse(
						1,
						20,
						1,
						List.of(new SetlistFmClient.Artist(
								"mbid", "Test Artist", "Test Artist", null, "url"))));
		when(client.getArtistSetlists("mbid", 1))
				.thenReturn(new SetlistFmClient.SetlistsResponse(
						setlists.size(), 20, 1, setlists));
		when(client.searchSetlistsByYear(org.mockito.ArgumentMatchers.eq("mbid"), anyInt(), anyInt()))
				.thenReturn(new SetlistFmClient.SetlistsResponse(0, 20, 1, List.of()));
		return client;
	}

	private SetlistFmClient.Setlist setlist(String id, LocalDate eventDate) {
		List<SetlistFmClient.Song> songs = List.of(
				song("Song 1"),
				song("Song 2"),
				song("Song 3"),
				song("Song 4"),
				song("Song 5"),
				song("Song 6"));
		return new SetlistFmClient.Setlist(
				id,
				null,
				eventDate.format(SETLIST_DATE_FORMAT),
				null,
				null,
				null,
				null,
				new SetlistFmClient.Sets(List.of(new SetlistFmClient.Set(null, null, songs))),
				"url/" + id);
	}

	private SetlistFmClient.Song song(String title) {
		return new SetlistFmClient.Song(title, false, null, null, null);
	}

	private PlaylistCreatorProperties properties() {
		return new PlaylistCreatorProperties(
				new PlaylistCreatorProperties.SetlistFm("", "", "", 1, 6, 6),
				null,
				null,
				null);
	}
}
