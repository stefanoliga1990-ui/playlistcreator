package com.application.playlistcreator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.application.playlistcreator.client.lastfm.LastFmClient;
import com.application.playlistcreator.client.lastfm.LastFmClient.SimilarArtist;
import com.application.playlistcreator.client.lastfm.LastFmClient.SimilarArtists;
import com.application.playlistcreator.client.lastfm.LastFmClient.SimilarArtistsAttributes;
import com.application.playlistcreator.client.lastfm.LastFmClient.SimilarArtistsResponse;
import com.application.playlistcreator.client.lastfm.LastFmClient.Tag;
import com.application.playlistcreator.client.lastfm.LastFmClient.TopTags;
import com.application.playlistcreator.client.lastfm.LastFmClient.TopTagsResponse;
import com.application.playlistcreator.client.lastfm.LastFmClient.TopTracks;
import com.application.playlistcreator.client.lastfm.LastFmClient.TopTracksResponse;
import com.application.playlistcreator.client.spotify.SpotifyApiClient;
import org.junit.jupiter.api.Test;

class SimilarArtistsPlaylistServiceTest {

	@Test
	void combinesDirectReciprocalAndTagSimilarityAndRanksStrongestCandidates() {
		LastFmClient lastFm = mock(LastFmClient.class);
		when(lastFm.getSimilarArtists("Source Artist", 30))
				.thenReturn(similarResponse("Source Artist", List.of(
						similar("Mutual Artist", "0.90"),
						similar("One Way Artist", "0.95"),
						similar("Tagged Artist", "0.70"))));
		when(lastFm.getSimilarArtists("Source Artist", 1))
				.thenReturn(similarResponse("Source Artist", List.of(similar("Mutual Artist", "0.90"))));
		when(lastFm.getSimilarArtists("Mutual Artist", 50))
				.thenReturn(similarResponse("Mutual Artist", List.of(similar("Source Artist", "0.80"))));
		when(lastFm.getSimilarArtists("One Way Artist", 50))
				.thenReturn(similarResponse("One Way Artist", List.of()));
		when(lastFm.getSimilarArtists("Tagged Artist", 50))
				.thenReturn(similarResponse("Tagged Artist", List.of(similar("Source Artist", "0.90"))));
		when(lastFm.getArtistTopTags("Source Artist", null))
				.thenReturn(tags("post-punk", "new wave", "alternative"));
		when(lastFm.getArtistTopTags("Mutual Artist", "mbid-Mutual Artist"))
				.thenReturn(tags("post-punk", "new wave", "alternative"));
		when(lastFm.getArtistTopTags("One Way Artist", "mbid-One Way Artist"))
				.thenReturn(tags("rock", "indie", "british"));
		when(lastFm.getArtistTopTags("Tagged Artist", "mbid-Tagged Artist"))
				.thenReturn(tags("post-punk", "new wave", "alternative"));
		SimilarArtistsPlaylistService service = service(lastFm);

		var result = service.findSimilarArtists("Source Artist");

		assertThat(result.artists()).extracting(artist -> artist.name())
				.containsExactly("Mutual Artist", "Tagged Artist");
		assertThat(result.artists()).extracting(artist -> artist.rank())
				.containsExactly(1, 2);
		assertThat(result.artists().get(0).similarityScore())
				.isGreaterThan(result.artists().get(1).similarityScore());
		assertThat(result.warning()).isEqualTo("Last.fm ha restituito solo 2 artisti simili validi");
	}

	@Test
	void loadsTracksOnlyForArtistsSelectedByTheUser() {
		LastFmClient lastFm = mock(LastFmClient.class);
		when(lastFm.getSimilarArtists("Source Artist", 30))
				.thenReturn(similarResponse("Source Artist", List.of(
						similar("Artist One", "0.90"),
						similar("Artist Two", "0.80"))));
		when(lastFm.getSimilarArtists("Source Artist", 1))
				.thenReturn(similarResponse("Source Artist", List.of(similar("Artist One", "0.90"))));
		when(lastFm.getSimilarArtists("Artist One", 50))
				.thenReturn(similarResponse("Artist One", List.of(similar("Source Artist", "0.70"))));
		when(lastFm.getSimilarArtists("Artist Two", 50))
				.thenReturn(similarResponse("Artist Two", List.of(similar("Source Artist", "0.60"))));
		when(lastFm.getArtistTopTags("Source Artist", null)).thenReturn(tags("rock"));
		when(lastFm.getArtistTopTags("Artist One", "mbid-Artist One")).thenReturn(tags("rock"));
		when(lastFm.getArtistTopTags("Artist Two", "mbid-Artist Two")).thenReturn(tags("rock"));
		when(lastFm.getArtistTopTracks("Artist Two", 2))
				.thenReturn(new TopTracksResponse(
						new TopTracks(List.of(
								track("Song One", "1000"),
								track("Song Two", "900"))),
						null,
						null));
		SimilarArtistsPlaylistService service = service(lastFm);

		var result = service.findTopTracks("Source Artist", List.of("Artist Two"), 2);

		assertThat(result.artists()).extracting(artist -> artist.name()).containsExactly("Artist Two");
		assertThat(result.tracks()).extracting(track -> track.artistName())
				.containsOnly("Artist Two");
		assertThat(result.tracks()).extracting(track -> track.title())
				.containsExactly("Song One", "Song Two");
		verify(lastFm).getArtistTopTracks("Artist Two", 2);
	}

	@Test
	void doesNotWarnWhenAtLeastFiveValidArtistsAreAvailable() {
		LastFmClient lastFm = mock(LastFmClient.class);
		List<SimilarArtist> candidates = java.util.stream.IntStream.rangeClosed(1, 5)
				.mapToObj(index -> similar("Artist " + index, "0.90"))
				.toList();
		when(lastFm.getSimilarArtists("Source Artist", 30))
				.thenReturn(similarResponse("Source Artist", candidates));
		when(lastFm.getSimilarArtists("Source Artist", 1))
				.thenReturn(similarResponse("Source Artist", List.of(candidates.get(0))));
		when(lastFm.getArtistTopTags("Source Artist", null)).thenReturn(tags("rock"));
		for (SimilarArtist candidate : candidates) {
			when(lastFm.getSimilarArtists(candidate.name(), 50))
					.thenReturn(similarResponse(candidate.name(), List.of(similar("Source Artist", "0.80"))));
			when(lastFm.getArtistTopTags(candidate.name(), candidate.mbid())).thenReturn(tags("rock"));
		}
		SimilarArtistsPlaylistService service = service(lastFm);

		var result = service.findSimilarArtists("Source Artist");

		assertThat(result.artists()).hasSize(5);
		assertThat(result.warning()).isNull();
	}

	private SimilarArtistsPlaylistService service(LastFmClient lastFm) {
		return new SimilarArtistsPlaylistService(
				lastFm,
				mock(SpotifyTrackMatchingService.class),
				mock(SpotifyApiClient.class),
				new SongNormalizer());
	}

	private SimilarArtist similar(String name, String match) {
		return new SimilarArtist(name, "mbid-" + name, match, "https://last.fm/music/" + name);
	}

	private SimilarArtistsResponse similarResponse(String source, List<SimilarArtist> artists) {
		return new SimilarArtistsResponse(
				new SimilarArtists(artists, new SimilarArtistsAttributes(source)),
				null,
				null);
	}

	private TopTagsResponse tags(String... names) {
		return new TopTagsResponse(
				new TopTags(List.of(names).stream()
						.map(name -> new Tag(name, "100", null))
						.toList()),
				null,
				null);
	}

	private LastFmClient.Track track(String name, String listeners) {
		return new LastFmClient.Track(name, null, listeners, listeners, null);
	}
}
