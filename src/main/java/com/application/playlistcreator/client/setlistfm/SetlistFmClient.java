package com.application.playlistcreator.client.setlistfm;

import java.util.List;

import com.application.playlistcreator.config.PlaylistCreatorProperties;
import com.application.playlistcreator.exception.ExternalApiException;
import com.application.playlistcreator.exception.SetlistFmArtistNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class SetlistFmClient {

	private final RestClient restClient;
	private final PlaylistCreatorProperties.SetlistFm properties;

	public SetlistFmClient(RestClient.Builder restClientBuilder, PlaylistCreatorProperties properties) {
		this.properties = properties.setlistfm();
		this.restClient = restClientBuilder
				.baseUrl(this.properties.baseUrl())
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, this.properties.acceptLanguage())
				.defaultHeader("x-api-key", this.properties.apiKey())
				.build();
	}

	public ArtistSearchResponse searchArtists(String artistName, int page) {
		assertApiKeyConfigured();
		try {
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/1.0/search/artists")
							.queryParam("artistName", artistName)
							.queryParam("sort", "relevance")
							.queryParam("p", page)
							.build())
					.retrieve()
					.body(ArtistSearchResponse.class);
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() == 404) {
				throw new SetlistFmArtistNotFoundException();
			}
			throw new ExternalApiException(toFailureMessage("setlist.fm artist search", ex), ex);
		}
	}

	public SetlistsResponse getArtistSetlists(String musicBrainzId, int page) {
		assertApiKeyConfigured();
		try {
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/1.0/artist/{mbid}/setlists")
							.queryParam("p", page)
							.build(musicBrainzId))
					.retrieve()
					.body(SetlistsResponse.class);
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() == 404) {
				throw new SetlistFmArtistNotFoundException();
			}
			throw new ExternalApiException(toFailureMessage("setlist.fm artist setlists search", ex), ex);
		}
	}

	public SetlistsResponse searchSetlistsByYear(String musicBrainzId, int year, int page) {
		assertApiKeyConfigured();
		try {
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/1.0/search/setlists")
							.queryParam("artistMbid", musicBrainzId)
							.queryParam("year", year)
							.queryParam("p", page)
							.build())
					.retrieve()
					.body(SetlistsResponse.class);
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() == 404) {
				throw new SetlistFmArtistNotFoundException();
			}
			throw new ExternalApiException(toFailureMessage("setlist.fm setlists by year search", ex), ex);
		}
	}

	private void assertApiKeyConfigured() {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			throw new ExternalApiException("SETLISTFM_API_KEY is not configured");
		}
	}

	private String toFailureMessage(String operation, RestClientResponseException ex) {
		String retryAfter = ex.getResponseHeaders() != null ? ex.getResponseHeaders().getFirst("Retry-After") : null;
		if (ex.getStatusCode().value() == 429) {
			return retryAfter != null && !retryAfter.isBlank()
					? operation + " failed: 429 TOO_MANY_REQUESTS. Retry after " + retryAfter + " seconds."
					: operation + " failed: 429 TOO_MANY_REQUESTS. Wait a bit before retrying.";
		}
		return operation + " failed: " + ex.getStatusCode();
	}

	public record ArtistSearchResponse(
			Integer total,
			Integer itemsPerPage,
			Integer page,
			List<Artist> artist) {
	}

	public record SetlistsResponse(
			Integer total,
			Integer itemsPerPage,
			Integer page,
			List<Setlist> setlist) {
	}

	public record Artist(
			String mbid,
			String name,
			String sortName,
			String disambiguation,
			String url) {
	}

	public record Setlist(
			String id,
			String versionId,
			String eventDate,
			String lastUpdated,
			Artist artist,
			Venue venue,
			Tour tour,
			Sets sets,
			String url) {
	}

	public record Tour(String name) {
	}

	public record Venue(
			String name,
			City city) {
	}

	public record City(
			String name,
			Country country) {
	}

	public record Country(
			String code,
			String name) {
	}

	public record Sets(List<Set> set) {
	}

	public record Set(
			String name,
			Integer encore,
			List<Song> song) {
	}

	public record Song(
			String name,
			Boolean tape,
			String info,
			Artist cover,
			Artist with) {
	}
}

