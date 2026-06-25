package com.application.playlistcreator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.application.playlistcreator.client.lastfm.LastFmClient;
import com.application.playlistcreator.client.lastfm.LastFmClient.Artist;
import com.application.playlistcreator.client.lastfm.LastFmClient.Tag;
import com.application.playlistcreator.client.lastfm.LastFmClient.TagInfo;
import com.application.playlistcreator.client.lastfm.LastFmClient.TagInfoResponse;
import com.application.playlistcreator.client.lastfm.LastFmClient.TopArtists;
import com.application.playlistcreator.client.lastfm.LastFmClient.TopArtistsResponse;
import com.application.playlistcreator.client.lastfm.LastFmClient.TopTags;
import com.application.playlistcreator.client.lastfm.LastFmClient.TopTagsResponse;
import com.application.playlistcreator.client.spotify.SpotifyApiClient;
import com.application.playlistcreator.config.PlaylistCreatorProperties;
import org.junit.jupiter.api.Test;

class GenrePlaylistServiceTest {

	@Test
	void filtersCandidatesAndReturnsWarningWhenStrongMatchesAreInsufficient() {
		LastFmClient lastFmClient = mock(LastFmClient.class);
		when(lastFmClient.getTagInfo("Post Punk"))
				.thenReturn(new TagInfoResponse(new TagInfo("post-punk", "1000", "500"), null, null));
		when(lastFmClient.getTopArtists("post-punk", 12))
				.thenReturn(new TopArtistsResponse(new TopArtists(List.of(
						artist("Artist One", "mbid-1"),
						artist("Artist Two", "mbid-2"),
						artist("Artist Three", "mbid-3"))), null, null));
		when(lastFmClient.getArtistTopTags("Artist One", "mbid-1"))
				.thenReturn(topTags("post-punk", "alternative", "indie"));
		when(lastFmClient.getArtistTopTags("Artist Two", "mbid-2"))
				.thenReturn(topTags("alternative", "indie", "new wave", "post-punk"));
		when(lastFmClient.getArtistTopTags("Artist Three", "mbid-3"))
				.thenReturn(topTags("alternative", "new wave", "post punk"));

		GenrePlaylistService service = new GenrePlaylistService(
				lastFmClient,
				new GenreTagMatcher(),
				mock(SpotifyTrackMatchingService.class),
				mock(SpotifyApiClient.class),
				mock(SongNormalizer.class),
				properties());

		var result = service.findTopArtists("Post Punk", 3);

		assertThat(result.artists()).extracting(artist -> artist.name())
				.containsExactly("Artist One", "Artist Three");
		assertThat(result.artists()).extracting(artist -> artist.rank())
				.containsExactly(1, 2);
		assertThat(result.requestedArtistCount()).isEqualTo(3);
		assertThat(result.checkedCandidateCount()).isEqualTo(3);
		assertThat(result.warning()).contains("Non sono stati trovati 3 artisti")
				.contains("Verranno mostrati 2 artisti");
		verify(lastFmClient).getTopArtists("post-punk", 12);
	}

	private Artist artist(String name, String mbid) {
		return new Artist(name, mbid, "https://last.fm/music/" + name, "1000");
	}

	private TopTagsResponse topTags(String... names) {
		return new TopTagsResponse(
				new TopTags(List.of(names).stream().map(name -> new Tag(name, "100", null)).toList()),
				null,
				null);
	}

	private PlaylistCreatorProperties properties() {
		return new PlaylistCreatorProperties(
				null,
				null,
				null,
				new PlaylistCreatorProperties.GenrePlaylist(15, 3, 25, 10));
	}
}
