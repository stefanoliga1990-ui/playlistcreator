package com.application.playlistcreator.client.lastfm;

import java.util.List;

import com.application.playlistcreator.config.PlaylistCreatorProperties;
import com.application.playlistcreator.exception.ExternalApiException;
import com.application.playlistcreator.exception.ExternalApiRateLimitException;
import com.application.playlistcreator.service.ExternalApiResilienceService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static com.application.playlistcreator.service.ExternalApiResilienceService.Provider.LAST_FM;

@Component
public class LastFmClient {

	private static final Logger log = LoggerFactory.getLogger(LastFmClient.class);

	private final RestClient restClient;
	private final PlaylistCreatorProperties.LastFm properties;
	private final ExternalApiResilienceService resilienceService;

	public LastFmClient(RestClient.Builder restClientBuilder, PlaylistCreatorProperties properties,
			ExternalApiResilienceService resilienceService) {
		this.properties = properties.lastfm();
		this.resilienceService = resilienceService;
		this.restClient = restClientBuilder
				.baseUrl(properties.lastfm().baseUrl())
				.build();
	}

	public TopArtistsResponse getTopArtists(String genre, int limit) {
		try {
			log.info("Calling Last.fm top artists endpoint. genre={}, limit={}", genre, limit);
			TopArtistsResponse response = resilienceService.executeRead(LAST_FM, "Last.fm top artists search", () -> restClient.get()
					.uri(uriBuilder -> uriBuilder
							.queryParam("method", "tag.gettopartists")
							.queryParam("tag", genre)
							.queryParam("limit", limit)
							.queryParam("api_key", properties.apiKey())
							.queryParam("format", "json")
							.build())
					.retrieve()
					.body(TopArtistsResponse.class));
			throwIfApiError("Last.fm top artists search", response != null ? response.error() : null,
					response != null ? response.message() : null);
			return response;
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Last.fm top artists search", ex), ex);
		}
	}

	public TagInfoResponse getTagInfo(String genre) {
		return getTagInfo(genre, true);
	}

	public TagInfoResponse getTagInfoExact(String genre) {
		return getTagInfo(genre, false);
	}

	private TagInfoResponse getTagInfo(String genre, boolean autocorrect) {
		try {
			log.info("Calling Last.fm tag info endpoint. genre={}, autocorrect={}", genre, autocorrect);
			TagInfoResponse response = resilienceService.executeRead(LAST_FM, "Last.fm tag info search", () -> restClient.get()
					.uri(uriBuilder -> uriBuilder
							.queryParam("method", "tag.getinfo")
							.queryParam("tag", genre)
							.queryParam("autocorrect", autocorrect ? 1 : 0)
							.queryParam("api_key", properties.apiKey())
							.queryParam("format", "json")
							.build())
					.retrieve()
					.body(TagInfoResponse.class));
			throwIfApiError("Last.fm tag info search", response != null ? response.error() : null,
					response != null ? response.message() : null);
			return response;
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Last.fm tag info search", ex), ex);
		}
	}

	public TopTagsResponse getArtistTopTags(String artistName, String musicBrainzId) {
		try {
			log.info("Calling Last.fm artist top tags endpoint. artistName={}, musicBrainzId={}",
					artistName, musicBrainzId);
			TopTagsResponse response = resilienceService.executeRead(LAST_FM, "Last.fm artist top tags search", () -> restClient.get()
					.uri(uriBuilder -> {
						var builder = uriBuilder
								.queryParam("method", "artist.gettoptags")
								.queryParam("autocorrect", 1)
								.queryParam("api_key", properties.apiKey())
								.queryParam("format", "json");
						if (musicBrainzId != null && !musicBrainzId.isBlank()) {
							builder.queryParam("mbid", musicBrainzId);
						}
						else {
							builder.queryParam("artist", artistName);
						}
						return builder.build();
					})
					.retrieve()
					.body(TopTagsResponse.class));
			throwIfApiError("Last.fm artist top tags search", response != null ? response.error() : null,
					response != null ? response.message() : null);
			return response;
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Last.fm artist top tags search", ex), ex);
		}
	}

	public SimilarArtistsResponse getSimilarArtists(String artistName, int limit) {
		try {
			log.info("Calling Last.fm similar artists endpoint. artistName={}, limit={}", artistName, limit);
			SimilarArtistsResponse response = resilienceService.executeRead(LAST_FM, "Last.fm similar artists search", () -> restClient.get()
					.uri(uriBuilder -> uriBuilder
							.queryParam("method", "artist.getsimilar")
							.queryParam("artist", artistName)
							.queryParam("limit", limit)
							.queryParam("autocorrect", 1)
							.queryParam("api_key", properties.apiKey())
							.queryParam("format", "json")
							.build())
					.retrieve()
					.body(SimilarArtistsResponse.class));
			throwIfApiError("Last.fm similar artists search", response != null ? response.error() : null,
					response != null ? response.message() : null);
			return response;
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Last.fm similar artists search", ex), ex);
		}
	}

	public TopTracksResponse getArtistTopTracks(String artistName, int limit) {
		return getArtistTopTracks(artistName, limit, 1);
	}

	public TopTracksResponse getArtistTopTracks(String artistName, int limit, int page) {
		try {
			log.info("Calling Last.fm artist top tracks endpoint. artistName={}, limit={}, page={}",
					artistName, limit, page);
			TopTracksResponse response = resilienceService.executeRead(LAST_FM, "Last.fm artist top tracks search", () -> restClient.get()
					.uri(uriBuilder -> uriBuilder
							.queryParam("method", "artist.gettoptracks")
							.queryParam("artist", artistName)
							.queryParam("limit", limit)
							.queryParam("page", page)
							.queryParam("autocorrect", 1)
							.queryParam("api_key", properties.apiKey())
							.queryParam("format", "json")
							.build())
					.retrieve()
					.body(TopTracksResponse.class));
			throwIfApiError("Last.fm artist top tracks search", response != null ? response.error() : null,
					response != null ? response.message() : null);
			return response;
		}
		catch (RestClientResponseException ex) {
			throw new ExternalApiException(toFailureMessage("Last.fm artist top tracks search", ex), ex);
		}
	}

	private void throwIfApiError(String operation, Integer errorCode, String message) {
		if (errorCode == null) {
			return;
		}
		if (errorCode == 29) {
			log.error("Last.fm rate limit exceeded. operation={}, errorCode={}, message={}",
					operation, errorCode, message);
			throw new ExternalApiRateLimitException(
					"Last.fm has temporarily reached its request limit. Please wait a little before trying again.",
					null,
					null);
		}
		else {
			log.error("Last.fm API error. operation={}, errorCode={}, message={}",
					operation, errorCode, message);
		}
		throw new ExternalApiException(operation + " failed: Last.fm error " + errorCode
				+ (message != null && !message.isBlank() ? " - " + message : ""));
	}

	private String toFailureMessage(String operation, RestClientResponseException ex) {
		String responseBody = ex.getResponseBodyAsString();
		if (ex.getStatusCode().value() == 429) {
			log.error("Last.fm HTTP rate limit exceeded. operation={}, status={}, responseBody={}",
					operation, ex.getStatusCode(), responseBody);
		}
		else {
			log.error("Last.fm HTTP error. operation={}, status={}, responseBody={}",
					operation, ex.getStatusCode(), responseBody);
		}
		if (responseBody != null && !responseBody.isBlank()) {
			return operation + " failed: " + ex.getStatusCode() + " - " + responseBody;
		}
		return operation + " failed: " + ex.getStatusCode();
	}

	public record TopArtistsResponse(TopArtists topartists, Integer error, String message) {
	}

	public record TopArtists(List<Artist> artist) {
	}

	public record Artist(
			String name,
			String mbid,
			String url,
			String listeners) {
	}

	public record TagInfoResponse(TagInfo tag, Integer error, String message) {
	}

	public record TagInfo(
			String name,
			String total,
			String reach) {
	}

	public record TopTagsResponse(TopTags toptags, Integer error, String message) {
	}

	public record TopTags(List<Tag> tag) {
	}

	public record Tag(
			String name,
			String count,
			String url) {
	}

	public record TopTracksResponse(TopTracks toptracks, Integer error, String message) {
	}

	public record SimilarArtistsResponse(
			SimilarArtists similarartists,
			Integer error,
			String message) {
	}

	public record SimilarArtists(
			List<SimilarArtist> artist,
			@JsonProperty("@attr") SimilarArtistsAttributes attributes) {
	}

	public record SimilarArtistsAttributes(String artist) {
	}

	public record SimilarArtist(
			String name,
			String mbid,
			String match,
			String url) {
	}

	public record TopTracks(List<Track> track) {
	}

	public record Track(
			String name,
			String url,
			String listeners,
			String playcount,
			TrackArtist artist) {
	}

	public record TrackArtist(
			String name,
			String mbid,
			String url) {
	}
}
