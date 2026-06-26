package com.application.playlistcreator.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.application.playlistcreator.client.lastfm.LastFmClient;
import com.application.playlistcreator.client.spotify.SpotifyApiClient;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.SimplifiedAlbum;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.SimplifiedTrack;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.SpotifyArtist;
import com.application.playlistcreator.dto.SelectedTrackRequest;
import com.application.playlistcreator.exception.ExternalApiException;
import com.application.playlistcreator.model.DiscographyAlbum;
import com.application.playlistcreator.model.DiscographyTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DiscographyPlaylistService {

	private static final Logger log = LoggerFactory.getLogger(DiscographyPlaylistService.class);
	private static final Duration CACHE_TTL = Duration.ofMinutes(30);
	private static final int SPOTIFY_ALBUM_PAGE_SIZE = 10;
	private static final int SPOTIFY_TRACK_PAGE_SIZE = 50;
	private static final int LASTFM_TRACK_LIMIT = 1000;
	private static final Pattern EDITION_SUFFIX = Pattern.compile(
			"(?i)\\s*[\\[(\\-:]\\s*(?:deluxe|remaster(?:ed)?|anniversary|expanded|special|bonus|legacy|super deluxe|collector'?s).*");
	private static final List<String> EXCLUDED_ALBUM_TERMS = List.of(
			"greatest hits",
			"best of",
			"the best",
			"collection",
			"anthology",
			"essential",
			"singles collection",
			"complete collection",
			"rarities",
			"remixes",
			"box set",
			"in concert",
			"live at",
			"live in",
			"live from",
			"unplugged");

	private final SpotifyApiClient spotifyApiClient;
	private final LastFmClient lastFmClient;
	private final SongNormalizer songNormalizer;
	private final Map<String, CachedAlbumSearch> albumCache = new ConcurrentHashMap<>();
	private final Map<String, CachedTrackSelection> trackCache = new ConcurrentHashMap<>();

	public DiscographyPlaylistService(
			SpotifyApiClient spotifyApiClient,
			LastFmClient lastFmClient,
			SongNormalizer songNormalizer) {
		this.spotifyApiClient = spotifyApiClient;
		this.lastFmClient = lastFmClient;
		this.songNormalizer = songNormalizer;
	}

	public AlbumSearchResult findAlbums(String accessToken, String artistName) {
		if (artistName == null || artistName.isBlank()) {
			throw new ExternalApiException("artistName is required");
		}
		SpotifyArtist artist = resolveArtist(accessToken, artistName.trim());
		return findAlbums(accessToken, artist.id(), artist.name());
	}

	public ArtistSearchResult findArtists(String accessToken, String artistName) {
		if (artistName == null || artistName.isBlank()) {
			throw new ExternalApiException("artistName is required");
		}
		String query = artistName.trim();
		String normalizedQuery = songNormalizer.normalizeArtist(query);
		var response = spotifyApiClient.searchArtists(accessToken, query);
		List<SpotifyArtist> spotifyArtists = response != null
				&& response.artists() != null
				&& response.artists().items() != null
						? response.artists().items()
						: List.of();
		List<ArtistSearchResult.Artist> artists = spotifyArtists.stream()
				.filter(artist -> artist != null
						&& artist.id() != null
						&& artist.name() != null
						&& songNormalizer.normalizeArtist(artist.name()).contains(normalizedQuery))
				.sorted(Comparator
						.comparingInt((SpotifyArtist artist) -> safeInt(artist.popularity())).reversed()
						.thenComparing(SpotifyArtist::name, String.CASE_INSENSITIVE_ORDER))
				.map(artist -> new ArtistSearchResult.Artist(
						artist.id(),
						artist.name(),
						safeInt(artist.popularity()),
						artist.followers() != null ? safeInt(artist.followers().total()) : 0,
						artist.external_urls() != null ? artist.external_urls().get("spotify") : null))
				.toList();
		if (artists.isEmpty()) {
			throw new ExternalApiException("No Spotify artist found for: " + query);
		}
		log.info("Spotify artists ready for discography selection. query={}, spotifyCandidates={}, matchingArtists={}",
				query, spotifyArtists.size(), artists.size());
		return new ArtistSearchResult(query, artists);
	}

	public AlbumSearchResult findAlbums(
			String accessToken,
			String artistId,
			String artistName) {
		if (artistId == null || artistId.isBlank()) {
			throw new ExternalApiException("artistId is required");
		}
		if (artistName == null || artistName.isBlank()) {
			throw new ExternalApiException("artistName is required");
		}
		SpotifyArtist artist = new SpotifyArtist(artistId, artistName.trim(), null, null, null);
		CachedAlbumSearch cached = albumCache.get(artist.id());
		if (cached != null && cached.isValid()) {
			log.info("Using cached Spotify discography. artist={}, artistId={}, albums={}",
					artist.name(), artist.id(), cached.result().albums().size());
			return cached.result();
		}

		List<SimplifiedAlbum> rawAlbums = loadArtistAlbums(accessToken, artist.id());
		List<DiscographyAlbum> albums = filterAndDeduplicateAlbums(rawAlbums);
		if (albums.isEmpty()) {
			throw new ExternalApiException("No eligible studio albums found for artist " + artist.name());
		}
		AlbumSearchResult result = new AlbumSearchResult(
				artist.id(),
				artist.name(),
				artist.external_urls() != null ? artist.external_urls().get("spotify") : null,
				albums,
				rawAlbums.size() - albums.size());
		albumCache.put(artist.id(), new CachedAlbumSearch(result, Instant.now()));
		log.info("Spotify discography ready. artist={}, artistId={}, rawAlbums={}, eligibleAlbums={}, filteredAlbums={}",
				artist.name(), artist.id(), rawAlbums.size(), albums.size(), result.filteredAlbumCount());
		return result;
	}

	public TrackSelectionResult selectTracks(
			String accessToken,
			String artistId,
			String artistName,
			List<String> includedAlbumIds,
			Integer tracksPerAlbum) {
		int trackLimit = validateTracksPerAlbum(tracksPerAlbum);
		List<DiscographyAlbum> albums = resolveSelectedAlbums(
				accessToken,
				artistId,
				artistName,
				includedAlbumIds);
		String cacheKey = selectionCacheKey(artistId, albums, trackLimit);
		CachedTrackSelection cached = trackCache.get(cacheKey);
		if (cached != null && cached.isValid()) {
			log.info("Using cached discography track selection. artist={}, albums={}, tracksPerAlbum={}, tracks={}",
					artistName, albums.size(), trackLimit, cached.result().tracks().size());
			return cached.result();
		}

		Map<String, Long> lastFmPlaycounts = loadLastFmPlaycounts(artistName);
		Set<String> selectedTitles = new LinkedHashSet<>();
		List<DiscographyTrack> selectedTracks = new ArrayList<>();
		List<AlbumTrackSelection> albumSelections = new ArrayList<>();
		for (DiscographyAlbum album : albums) {
			List<SimplifiedTrack> albumTracks = loadAlbumTracks(accessToken, album.id());
			List<RankedTrack> rankedTracks = albumTracks.stream()
					.filter(track -> track.id() != null && track.uri() != null && track.name() != null)
					.map(track -> rankTrack(track, lastFmPlaycounts))
					.sorted(Comparator.comparingLong(RankedTrack::lastFmPlaycount).reversed()
							.thenComparingInt(track -> safeInt(track.track().track_number())))
					.toList();

			List<DiscographyTrack> albumSelectedTracks = new ArrayList<>();
			for (RankedTrack ranked : rankedTracks) {
				String normalizedTitle = songNormalizer.normalizeTitle(ranked.track().name());
				if (!selectedTitles.add(normalizedTitle)) {
					continue;
				}
				DiscographyTrack selected = new DiscographyTrack(
						album.id(),
						album.name(),
						album.releaseYear(),
						ranked.track().id(),
						ranked.track().name(),
						ranked.track().uri(),
						safeInt(ranked.track().track_number()),
						albumSelectedTracks.size() + 1,
						ranked.lastFmPlaycount(),
						ranked.lastFmPlaycount() > 0 ? "LAST_FM" : "ALBUM_ORDER");
				albumSelectedTracks.add(selected);
				selectedTracks.add(selected);
				if (albumSelectedTracks.size() == trackLimit) {
					break;
				}
			}
			albumSelections.add(new AlbumTrackSelection(album, List.copyOf(albumSelectedTracks)));
			log.info("Discography album tracks selected. artist={}, album={}, year={}, availableTracks={}, selectedTracks={}",
					artistName, album.name(), album.releaseYear(), albumTracks.size(), albumSelectedTracks.size());
		}
		if (selectedTracks.isEmpty()) {
			throw new ExternalApiException("No Spotify tracks found for the selected albums");
		}
		TrackSelectionResult result = new TrackSelectionResult(
				artistId,
				artistName,
				List.copyOf(albumSelections),
				List.copyOf(selectedTracks),
				trackLimit);
		trackCache.put(cacheKey, new CachedTrackSelection(result, Instant.now()));
		log.info("Discography track selection ready. artist={}, albums={}, tracksPerAlbum={}, tracks={}",
				artistName, albums.size(), trackLimit, selectedTracks.size());
		return result;
	}

	public GenerationResult generatePlaylist(
			String accessToken,
			String artistId,
			String artistName,
			List<String> includedAlbumIds,
			Integer tracksPerAlbum,
			List<SelectedTrackRequest> selectedTracks,
			String playlistName,
			String playlistDescription) {
		TrackSelectionResult fullSelection = selectTracks(
				accessToken,
				artistId,
				artistName,
				includedAlbumIds,
				tracksPerAlbum);
		TrackSelectionResult selection = filterSelectedTracks(fullSelection, selectedTracks);
		String finalName = playlistName != null && !playlistName.isBlank()
				? playlistName.trim()
				: selection.artistName() + " - Discografia essenziale";
		String description = playlistDescription != null && !playlistDescription.isBlank()
				? playlistDescription.trim()
				: "Playlist generated by playlistcreator from the essential discography of " + selection.artistName();
		var user = spotifyApiClient.getCurrentUser(accessToken);
		log.info("Spotify current user resolved for discography playlist. userId={}, displayName={}",
				user.id(), user.display_name());
		var playlist = spotifyApiClient.createPlaylist(accessToken, finalName, description, true);
		spotifyApiClient.updatePlaylistVisibility(accessToken, playlist.id(), true);
		List<String> uris = selection.tracks().stream().map(DiscographyTrack::uri).distinct().toList();
		for (List<String> batch : batches(uris, 100)) {
			spotifyApiClient.addItemsToPlaylist(accessToken, playlist.id(), batch);
		}
		log.info("Discography playlist generation finished. artist={}, playlistId={}, albums={}, addedTracks={}",
				selection.artistName(), playlist.id(), selection.albums().size(), uris.size());
		return new GenerationResult(
				selection,
				playlist.id(),
				playlist.name(),
				playlist.external_urls() != null ? playlist.external_urls().get("spotify") : null,
				uris.size());
	}

	private TrackSelectionResult filterSelectedTracks(
			TrackSelectionResult selection,
			List<SelectedTrackRequest> selectedTracks) {
		if (selectedTracks == null || selectedTracks.isEmpty()) {
			throw new ExternalApiException("Select at least one track");
		}
		Set<String> selectedIds = selectedTracks.stream()
				.filter(track -> track != null && track.id() != null && !track.id().isBlank())
				.map(SelectedTrackRequest::id)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		List<DiscographyTrack> tracks = selection.tracks().stream()
				.filter(track -> selectedIds.contains(track.id()))
				.toList();
		if (tracks.isEmpty()) {
			throw new ExternalApiException("None of the selected tracks is available");
		}
		Set<String> retainedIds = tracks.stream().map(DiscographyTrack::id).collect(java.util.stream.Collectors.toSet());
		List<AlbumTrackSelection> albums = selection.albums().stream()
				.map(albumSelection -> new AlbumTrackSelection(
						albumSelection.album(),
						albumSelection.tracks().stream()
								.filter(track -> retainedIds.contains(track.id()))
								.toList()))
				.filter(albumSelection -> !albumSelection.tracks().isEmpty())
				.toList();
		return new TrackSelectionResult(
				selection.artistId(),
				selection.artistName(),
				albums,
				tracks,
				selection.tracksPerAlbum());
	}

	private SpotifyArtist resolveArtist(String accessToken, String artistName) {
		var response = spotifyApiClient.searchArtists(accessToken, artistName);
		List<SpotifyArtist> artists = response != null && response.artists() != null
				&& response.artists().items() != null ? response.artists().items() : List.of();
		if (artists.isEmpty()) {
			throw new ExternalApiException("No Spotify artist found for: " + artistName);
		}
		String requested = songNormalizer.normalizeArtist(artistName);
		SpotifyArtist resolved = artists.stream()
				.max(Comparator.comparingInt(artist -> artistScore(requested, artist)))
				.orElseThrow();
		log.info("Spotify artist resolved for discography. requested={}, resolved={}, artistId={}, candidates={}",
				artistName, resolved.name(), resolved.id(), artists.size());
		return resolved;
	}

	private int artistScore(String requested, SpotifyArtist artist) {
		String candidate = songNormalizer.normalizeArtist(artist.name());
		int score = safeInt(artist.popularity());
		if (candidate.equals(requested)) {
			score += 1000;
		}
		else if (candidate.startsWith(requested) || requested.startsWith(candidate)) {
			score += 300;
		}
		return score;
	}

	private List<SimplifiedAlbum> loadArtistAlbums(String accessToken, String artistId) {
		List<SimplifiedAlbum> albums = new ArrayList<>();
		int offset = 0;
		while (true) {
			var page = spotifyApiClient.getArtistAlbums(accessToken, artistId, SPOTIFY_ALBUM_PAGE_SIZE, offset);
			List<SimplifiedAlbum> items = page != null && page.items() != null ? page.items() : List.of();
			albums.addAll(items);
			offset += items.size();
			if (items.isEmpty() || page.next() == null || page.next().isBlank()) {
				break;
			}
		}
		return albums;
	}

	private List<DiscographyAlbum> filterAndDeduplicateAlbums(List<SimplifiedAlbum> rawAlbums) {
		Map<String, SimplifiedAlbum> deduplicated = new LinkedHashMap<>();
		int invalidAlbums = 0;
		int nonAlbumItems = 0;
		int excludedByTitle = 0;
		int duplicateEditions = 0;
		for (SimplifiedAlbum album : rawAlbums) {
			if (album == null || album.id() == null || album.name() == null) {
				invalidAlbums++;
				continue;
			}
			if (!"album".equalsIgnoreCase(album.album_type())) {
				nonAlbumItems++;
				continue;
			}
			if (hasExcludedAlbumTitle(album.name())) {
				excludedByTitle++;
				continue;
			}
			String key = normalizeAlbumTitle(album.name());
			SimplifiedAlbum existing = deduplicated.get(key);
			if (existing == null || compareAlbumEdition(album, existing) < 0) {
				if (existing != null) {
					duplicateEditions++;
				}
				deduplicated.put(key, album);
			}
			else {
				duplicateEditions++;
			}
		}
		List<DiscographyAlbum> albums = deduplicated.values().stream()
				.map(this::toDiscographyAlbum)
				.sorted(Comparator.comparing(DiscographyAlbum::releaseDate)
						.thenComparing(DiscographyAlbum::name, String.CASE_INSENSITIVE_ORDER))
				.toList();
		log.info("Spotify albums filtered. rawAlbums={}, eligibleAlbums={}, invalidAlbums={}, nonAlbumItems={}, excludedByTitle={}, duplicateEditions={}",
				rawAlbums.size(), albums.size(), invalidAlbums, nonAlbumItems, excludedByTitle, duplicateEditions);
		return albums;
	}

	boolean isEligibleAlbum(SimplifiedAlbum album) {
		if (album == null || album.id() == null || album.name() == null) {
			return false;
		}
		if (!"album".equalsIgnoreCase(album.album_type())) {
			return false;
		}
		return !hasExcludedAlbumTitle(album.name());
	}

	private boolean hasExcludedAlbumTitle(String albumName) {
		String normalized = songNormalizer.normalizeArtist(albumName);
		return EXCLUDED_ALBUM_TERMS.stream().anyMatch(normalized::contains);
	}

	String normalizeAlbumTitle(String title) {
		String withoutEdition = EDITION_SUFFIX.matcher(title == null ? "" : title).replaceFirst("");
		return songNormalizer.normalizeArtist(withoutEdition);
	}

	private int compareAlbumEdition(SimplifiedAlbum left, SimplifiedAlbum right) {
		int dateComparison = parseReleaseDate(left.release_date()).compareTo(parseReleaseDate(right.release_date()));
		if (dateComparison != 0) {
			return dateComparison;
		}
		return Integer.compare(editionPenalty(left.name()), editionPenalty(right.name()));
	}

	private int editionPenalty(String name) {
		String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
		int penalty = 0;
		for (String term : List.of("deluxe", "remaster", "anniversary", "expanded", "special edition", "bonus")) {
			if (normalized.contains(term)) {
				penalty++;
			}
		}
		return penalty;
	}

	private DiscographyAlbum toDiscographyAlbum(SimplifiedAlbum album) {
		LocalDate releaseDate = parseReleaseDate(album.release_date());
		return new DiscographyAlbum(
				album.id(),
				album.name(),
				releaseDate.toString(),
				releaseDate.getYear(),
				safeInt(album.total_tracks()),
				album.external_urls() != null ? album.external_urls().get("spotify") : null);
	}

	private LocalDate parseReleaseDate(String value) {
		if (value == null || value.isBlank()) {
			return LocalDate.of(9999, 12, 31);
		}
		try {
			if (value.length() == 4) {
				return Year.parse(value).atDay(1);
			}
			if (value.length() == 7) {
				return YearMonth.parse(value).atDay(1);
			}
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException ex) {
			return LocalDate.of(9999, 12, 31);
		}
	}

	private List<DiscographyAlbum> resolveSelectedAlbums(
			String accessToken,
			String artistId,
			String artistName,
			List<String> includedAlbumIds) {
		if (artistId == null || artistId.isBlank()) {
			throw new ExternalApiException("artistId is required");
		}
		if (includedAlbumIds == null || includedAlbumIds.isEmpty()) {
			throw new ExternalApiException("Select at least one album");
		}
		CachedAlbumSearch cached = albumCache.get(artistId);
		AlbumSearchResult search = cached != null && cached.isValid()
				? cached.result()
				: findAlbums(accessToken, artistId, artistName);
		Set<String> included = new LinkedHashSet<>(includedAlbumIds);
		List<DiscographyAlbum> albums = search.albums().stream()
				.filter(album -> included.contains(album.id()))
				.toList();
		if (albums.isEmpty()) {
			throw new ExternalApiException("None of the selected albums is available");
		}
		return albums;
	}

	private List<SimplifiedTrack> loadAlbumTracks(String accessToken, String albumId) {
		List<SimplifiedTrack> tracks = new ArrayList<>();
		int offset = 0;
		while (true) {
			var page = spotifyApiClient.getAlbumTracks(accessToken, albumId, SPOTIFY_TRACK_PAGE_SIZE, offset);
			List<SimplifiedTrack> items = page != null && page.items() != null ? page.items() : List.of();
			tracks.addAll(items);
			offset += items.size();
			if (items.isEmpty() || page.next() == null || page.next().isBlank()) {
				break;
			}
		}
		return tracks;
	}

	private Map<String, Long> loadLastFmPlaycounts(String artistName) {
		Map<String, Long> playcounts = new HashMap<>();
		var response = lastFmClient.getArtistTopTracks(artistName, LASTFM_TRACK_LIMIT);
		if (response == null || response.toptracks() == null || response.toptracks().track() == null) {
			return playcounts;
		}
		response.toptracks().track().forEach(track -> {
			if (track != null && track.name() != null) {
				String key = songNormalizer.normalizeTitle(track.name());
				playcounts.merge(key, parseLong(track.playcount()), Math::max);
			}
		});
		log.info("Last.fm artist ranking loaded for discography. artist={}, rankedTracks={}",
				artistName, playcounts.size());
		return playcounts;
	}

	private RankedTrack rankTrack(
			SimplifiedTrack track,
			Map<String, Long> lastFmPlaycounts) {
		return new RankedTrack(
				track,
				lastFmPlaycounts.getOrDefault(songNormalizer.normalizeTitle(track.name()), 0L));
	}

	private int validateTracksPerAlbum(Integer value) {
		int limit = value != null ? value : 2;
		if (limit < 1 || limit > 3) {
			throw new ExternalApiException("tracksPerAlbum must be between 1 and 3");
		}
		return limit;
	}

	private String selectionCacheKey(String artistId, List<DiscographyAlbum> albums, int tracksPerAlbum) {
		return artistId + ":" + albums.stream().map(DiscographyAlbum::id).sorted().reduce("", (a, b) -> a + "," + b)
				+ ":" + tracksPerAlbum;
	}

	private long parseLong(String value) {
		try {
			return value == null ? 0 : Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			return 0;
		}
	}

	private int safeInt(Integer value) {
		return value != null ? value : 0;
	}

	private <T> List<List<T>> batches(List<T> values, int batchSize) {
		List<List<T>> batches = new ArrayList<>();
		for (int start = 0; start < values.size(); start += batchSize) {
			batches.add(values.subList(start, Math.min(start + batchSize, values.size())));
		}
		return batches;
	}

	private record RankedTrack(SimplifiedTrack track, long lastFmPlaycount) {
	}

	private record CachedAlbumSearch(AlbumSearchResult result, Instant createdAt) {
		boolean isValid() {
			return createdAt.plus(CACHE_TTL).isAfter(Instant.now());
		}
	}

	private record CachedTrackSelection(TrackSelectionResult result, Instant createdAt) {
		boolean isValid() {
			return createdAt.plus(CACHE_TTL).isAfter(Instant.now());
		}
	}

	public record AlbumSearchResult(
			String artistId,
			String artistName,
			String artistSpotifyUrl,
			List<DiscographyAlbum> albums,
			int filteredAlbumCount) {
	}

	public record ArtistSearchResult(
			String query,
			List<Artist> artists) {

		public record Artist(
				String id,
				String name,
				int popularity,
				int followers,
				String spotifyUrl) {
		}
	}

	public record AlbumTrackSelection(DiscographyAlbum album, List<DiscographyTrack> tracks) {
	}

	public record TrackSelectionResult(
			String artistId,
			String artistName,
			List<AlbumTrackSelection> albums,
			List<DiscographyTrack> tracks,
			int tracksPerAlbum) {
	}

	public record GenerationResult(
			TrackSelectionResult selection,
			String playlistId,
			String playlistName,
			String playlistUrl,
			int addedTracksCount) {
	}
}
