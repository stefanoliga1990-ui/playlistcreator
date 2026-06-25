package com.application.playlistcreator.client.spotify;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.application.playlistcreator.config.PlaylistCreatorProperties;
import com.application.playlistcreator.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class SpotifyApiClient {

	private static final Logger log = LoggerFactory.getLogger(SpotifyApiClient.class);

	private final RestClient restClient;
	private final PlaylistCreatorProperties.Spotify properties;

	public SpotifyApiClient(RestClient.Builder restClientBuilder, PlaylistCreatorProperties properties) {
		this.properties = properties.spotify();
		this.restClient = restClientBuilder
				.baseUrl(properties.spotify().apiBaseUrl())
				.build();
	}

	public CurrentUser getCurrentUser(String accessToken) {
		try {
			log.info("Calling Spotify current user endpoint.");
			return restClient.get()
					.uri("/me")
					.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
					.retrieve()
					.body(CurrentUser.class);
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Spotify current user request", ex), ex);
		}
	}

	public SearchTracksResponse searchTracks(String accessToken, String query) {
		try {
			log.info("Calling Spotify search endpoint. query={}, market={}", query, properties.market());
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/search")
							.queryParam("q", query)
							.queryParam("type", "track")
							.queryParam("market", properties.market())
							.queryParam("limit", 10)
							.build())
					.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
					.retrieve()
					.body(SearchTracksResponse.class);
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Spotify track search", ex), ex);
		}
	}

	public SearchArtistsResponse searchArtists(String accessToken, String artistName) {
		try {
			log.info("Calling Spotify artist search endpoint. artistName={}", artistName);
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/search")
							.queryParam("q", artistName)
							.queryParam("type", "artist")
							.queryParam("market", properties.market())
							.queryParam("limit", 10)
							.build())
					.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
					.retrieve()
					.body(SearchArtistsResponse.class);
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Spotify artist search", ex), ex);
		}
	}

	public TopTracksPage getCurrentUserTopTracks(
			String accessToken,
			String timeRange,
			int limit,
			int offset) {
		try {
			log.info("Calling Spotify current user top tracks endpoint. timeRange={}, limit={}, offset={}",
					timeRange, limit, offset);
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/me/top/tracks")
							.queryParam("time_range", timeRange)
							.queryParam("limit", limit)
							.queryParam("offset", offset)
							.build())
					.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
					.retrieve()
					.body(TopTracksPage.class);
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Spotify current user top tracks search", ex), ex);
		}
	}

	public TopArtistsPage getCurrentUserTopArtists(
			String accessToken,
			String timeRange,
			int limit,
			int offset) {
		try {
			log.info("Calling Spotify current user top artists endpoint. timeRange={}, limit={}, offset={}",
					timeRange, limit, offset);
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/me/top/artists")
							.queryParam("time_range", timeRange)
							.queryParam("limit", limit)
							.queryParam("offset", offset)
							.build())
					.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
					.retrieve()
					.body(TopArtistsPage.class);
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Spotify current user top artists search", ex), ex);
		}
	}

	public RecentlyPlayedResponse getRecentlyPlayedTracks(String accessToken, int limit) {
		try {
			log.info("Calling Spotify recently played endpoint. limit={}", limit);
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/me/player/recently-played")
							.queryParam("limit", limit)
							.build())
					.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
					.retrieve()
					.body(RecentlyPlayedResponse.class);
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Spotify recently played tracks search", ex), ex);
		}
	}

	public SavedTracksPage getSavedTracks(String accessToken, int limit, int offset) {
		try {
			log.info("Calling Spotify saved tracks endpoint. limit={}, offset={}", limit, offset);
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/me/tracks")
							.queryParam("market", properties.market())
							.queryParam("limit", limit)
							.queryParam("offset", offset)
							.build())
					.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
					.retrieve()
					.body(SavedTracksPage.class);
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Spotify saved tracks search", ex), ex);
		}
	}

	public AlbumsPage getArtistAlbums(String accessToken, String artistId, int limit, int offset) {
		try {
			log.info("Calling Spotify artist albums endpoint. artistId={}, limit={}, offset={}",
					artistId, limit, offset);
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/artists/{artistId}/albums")
							.queryParam("include_groups", "album")
							.queryParam("market", properties.market())
							.queryParam("limit", limit)
							.queryParam("offset", offset)
							.build(artistId))
					.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
					.retrieve()
					.body(AlbumsPage.class);
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Spotify artist albums search", ex), ex);
		}
	}

	public AlbumTracksPage getAlbumTracks(String accessToken, String albumId, int limit, int offset) {
		try {
			log.info("Calling Spotify album tracks endpoint. albumId={}, limit={}, offset={}",
					albumId, limit, offset);
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/albums/{albumId}/tracks")
							.queryParam("market", properties.market())
							.queryParam("limit", limit)
							.queryParam("offset", offset)
							.build(albumId))
					.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
					.retrieve()
					.body(AlbumTracksPage.class);
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Spotify album tracks search", ex), ex);
		}
	}

	public Playlist createPlaylist(String accessToken, String name, String description,
			boolean publicPlaylist) {
		try {
			log.info("Calling Spotify create playlist endpoint. name={}, publicPlaylist={}",
					name, publicPlaylist);
			return restClient.post()
					.uri("/me/playlists")
					.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of(
							"name", name,
							"description", description,
							"public", publicPlaylist))
					.retrieve()
					.body(Playlist.class);
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Spotify playlist creation", ex), ex);
		}
	}

	public void updatePlaylistVisibility(String accessToken, String playlistId, boolean publicPlaylist) {
		try {
			log.info("Calling Spotify change playlist details endpoint. playlistId={}, publicPlaylist={}",
					playlistId, publicPlaylist);
			restClient.put()
					.uri("/playlists/{playlistId}", playlistId)
					.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("public", publicPlaylist))
					.retrieve()
					.toBodilessEntity();
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Spotify playlist visibility update", ex), ex);
		}
	}

	public void addItemsToPlaylist(String accessToken, String playlistId, List<String> uris) {
		try {
			log.info("Calling Spotify add playlist items endpoint. playlistId={}, items={}", playlistId, uris.size());
			restClient.post()
					.uri("/playlists/{playlistId}/items", playlistId)
					.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("uris", uris))
					.retrieve()
					.toBodilessEntity();
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Spotify add playlist items", ex), ex);
		}
	}

	private String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}

	private String toFailureMessage(String operation, RestClientResponseException ex) {
		String responseBody = ex.getResponseBodyAsString();
		if (responseBody != null && !responseBody.isBlank()) {
			return operation + " failed: " + ex.getStatusCode() + " - " + responseBody;
		}
		return operation + " failed: " + ex.getStatusCode();
	}

	public record CurrentUser(String id, String display_name, String email) {
	}

	public record SearchTracksResponse(Tracks tracks) {
	}

	public record SearchArtistsResponse(Artists artists) {
	}

	public record Artists(List<SpotifyArtist> items) {
	}

	public record SpotifyArtist(
			String id,
			String name,
			Integer popularity,
			Followers followers,
			Map<String, String> external_urls) {
	}

	public record Followers(Integer total) {
	}

	public record AlbumsPage(
			List<SimplifiedAlbum> items,
			Integer total,
			Integer limit,
			Integer offset,
			String next) {
	}

	public record SimplifiedAlbum(
			String id,
			String name,
			String album_type,
			String album_group,
			String release_date,
			String release_date_precision,
			Integer total_tracks,
			List<Artist> artists,
			Map<String, String> external_urls) {
	}

	public record AlbumTracksPage(
			List<SimplifiedTrack> items,
			Integer total,
			Integer limit,
			Integer offset,
			String next) {
	}

	public record TopTracksPage(
			List<Track> items,
			Integer total,
			Integer limit,
			Integer offset,
			String next) {
	}

	public record TopArtistsPage(
			List<SpotifyArtist> items,
			Integer total,
			Integer limit,
			Integer offset,
			String next) {
	}

	public record RecentlyPlayedResponse(List<PlayHistory> items) {
	}

	public record PlayHistory(Track track, String played_at) {
	}

	public record SavedTracksPage(
			List<SavedTrack> items,
			Integer total,
			Integer limit,
			Integer offset,
			String next) {
	}

	public record SavedTrack(String added_at, Track track) {
	}

	public record SimplifiedTrack(
			String id,
			String name,
			String uri,
			Integer track_number,
			Integer disc_number,
			Boolean is_playable,
			List<Artist> artists) {
	}

	public record Tracks(List<Track> items) {
	}

	public record Track(
			String id,
			String name,
			String uri,
			Boolean is_playable,
			Integer popularity,
			List<Artist> artists,
			Album album) {
	}

	public record Artist(String id, String name) {
	}

	public record Album(
			String id,
			String name,
			String album_type) {
	}

	public record Playlist(
			String id,
			String name,
			String uri,
			@JsonProperty("public") Boolean publicPlaylist,
			Map<String, String> external_urls) {
	}
}

