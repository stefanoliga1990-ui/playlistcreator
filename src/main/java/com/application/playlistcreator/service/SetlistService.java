package com.application.playlistcreator.service;

import java.time.LocalDate;
import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.application.playlistcreator.client.setlistfm.SetlistFmClient;
import com.application.playlistcreator.client.setlistfm.SetlistFmClient.Artist;
import com.application.playlistcreator.client.setlistfm.SetlistFmClient.Setlist;
import com.application.playlistcreator.config.PlaylistCreatorProperties;
import com.application.playlistcreator.exception.NoRecentSetlistsException;
import com.application.playlistcreator.exception.SetlistFmArtistNotFoundException;
import com.application.playlistcreator.model.ArtistCandidate;
import com.application.playlistcreator.model.CandidateSong;
import com.application.playlistcreator.model.ConcertSetlist;
import com.application.playlistcreator.model.SetlistSelection;
import com.application.playlistcreator.model.SetlistSong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SetlistService {

	private static final Logger log = LoggerFactory.getLogger(SetlistService.class);

	private static final DateTimeFormatter SETLIST_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
	private static final Duration SELECTION_CACHE_TTL = Duration.ofMinutes(10);
	private static final List<String> NON_MUSICAL_TITLE_TOKENS = List.of(
			"doodle",
			"jam",
			"solo",
			"intro",
			"outro",
			"improvisation",
			"band introductions",
			"band introduction",
			"encore break");

	private final SetlistFmClient setlistFmClient;
	private final PlaylistCreatorProperties.SetlistFm properties;
	private final SongNormalizer songNormalizer;
	private final Map<String, CachedSelection> selectionCache = new ConcurrentHashMap<>();

	public SetlistService(SetlistFmClient setlistFmClient, PlaylistCreatorProperties properties,
			SongNormalizer songNormalizer) {
		this.setlistFmClient = setlistFmClient;
		this.properties = properties.setlistfm();
		this.songNormalizer = songNormalizer;
	}

	public SetlistSelection selectProbableSongs(String artistName) {
		String cacheKey = songNormalizer.normalizeArtist(artistName);
		CachedSelection cachedSelection = selectionCache.get(cacheKey);
		if (cachedSelection != null && cachedSelection.isValid()) {
			log.info("Using cached setlist selection. artistName={}, cacheKey={}, recentSongs={}",
					artistName, cacheKey, cachedSelection.selection().recentSongs().size());
			return cachedSelection.selection();
		}
		log.info("Building setlist selection. artistName={}, cacheKey={}", artistName, cacheKey);
		ArtistCandidate artist = findBestArtist(artistName);
		List<ConcertSetlist> validSetlists = loadValidRecentSetlists(artist.musicBrainzId());
		if (validSetlists.isEmpty()) {
			log.warn("No valid recent setlists found. artistName={}, resolvedArtist={}, maxAgeMonths={}",
					artistName, artist.name(), properties.maxAgeMonths());
			throw new NoRecentSetlistsException(
					"No setlists were found for " + artist.name() + " in the last "
							+ properties.maxAgeMonths() + " months.");
		}
		List<ConcertSetlist> selectedSetlists = validSetlists.stream().limit(3).toList();
		List<CandidateSong> recentSongs = findRecentSongs(selectedSetlists);
		SetlistSelection selection = new SetlistSelection(artist, selectedSetlists, recentSongs);
		selectionCache.put(cacheKey, new CachedSelection(selection, Instant.now()));
		log.info("Setlist selection built. artist={}, setlists={}, recentSongs={}",
				artist.name(), selectedSetlists.size(), recentSongs.size());
		return selection;
	}

	private ArtistCandidate findBestArtist(String artistName) {
		log.info("Searching setlist.fm artist. artistName={}", artistName);
		var response = setlistFmClient.searchArtists(artistName, 1);
		List<Artist> artists = response != null && response.artist() != null ? response.artist() : List.of();
		if (artists.isEmpty()) {
			log.warn("No setlist.fm artist found. artistName={}", artistName);
			throw new SetlistFmArtistNotFoundException();
		}
		String requested = songNormalizer.normalizeArtist(artistName);
		Artist bestArtist = artists.stream()
				.max(Comparator.comparingInt(artist -> artistScore(requested, artist)))
				.orElseThrow();
		log.info("setlist.fm artist resolved. requested={}, resolved={}, mbid={}, candidates={}",
				artistName, bestArtist.name(), bestArtist.mbid(), artists.size());
		return new ArtistCandidate(bestArtist.mbid(), bestArtist.name(), bestArtist.sortName(),
				bestArtist.disambiguation(), bestArtist.url());
	}

	private int artistScore(String requested, Artist artist) {
		String name = songNormalizer.normalizeArtist(artist.name());
		String sortName = songNormalizer.normalizeArtist(artist.sortName());
		if (requested.equals(name) || requested.equals(sortName)) {
			return 100;
		}
		if (name.contains(requested) || requested.contains(name)) {
			return 80;
		}
		return tokenOverlapScore(requested, name);
	}

	private List<ConcertSetlist> loadValidRecentSetlists(String musicBrainzId) {
		Map<String, ConcertSetlist> setlists = new LinkedHashMap<>();
		for (int page = 1; page <= properties.maxPagesToScan(); page++) {
			log.info("Loading artist setlists. mbid={}, page={}", musicBrainzId, page);
			var response = setlistFmClient.getArtistSetlists(musicBrainzId, page);
			addSetlists(setlists, response != null ? response.setlist() : List.of());
			List<ConcertSetlist> validSetlists = filterAndSortValidSetlists(setlists.values().stream().toList());
			log.info("Artist setlists page processed. mbid={}, page={}, totalLoaded={}, validSetlists={}",
					musicBrainzId, page, setlists.size(), validSetlists.size());
			if (validSetlists.size() >= 3) {
				return validSetlists;
			}
			if (isLastPage(response)) {
				break;
			}
		}
		List<ConcertSetlist> validSetlists = filterAndSortValidSetlists(setlists.values().stream().toList());
		if (validSetlists.size() >= 3) {
			return validSetlists;
		}
		log.info("Falling back to recent setlists by year. mbid={}, validSetlistsBeforeFallback={}",
				musicBrainzId, validSetlists.size());
		try {
			loadRecentSetlistsByYear(musicBrainzId, setlists);
		}
		catch (SetlistFmArtistNotFoundException ex) {
			if (validSetlists.isEmpty()) {
				throw ex;
			}
			log.info("Setlist year fallback returned no data; using already loaded valid setlists. mbid={}, validSetlists={}",
					musicBrainzId, validSetlists.size());
		}
		return filterAndSortValidSetlists(setlists.values().stream().toList());
	}

	private void loadRecentSetlistsByYear(String musicBrainzId, Map<String, ConcertSetlist> setlists) {
		LocalDate threshold = recentSetlistThreshold();
		Set<Integer> years = new LinkedHashSet<>();
		years.add(LocalDate.now().getYear());
		years.add(threshold.getYear());
		for (Integer year : years) {
			for (int page = 1; page <= properties.maxPagesToScan(); page++) {
				log.info("Loading setlists by year. mbid={}, year={}, page={}, threshold={}",
						musicBrainzId, year, page, threshold);
				var response = setlistFmClient.searchSetlistsByYear(musicBrainzId, year, page);
				List<Setlist> rawSetlists = response != null && response.setlist() != null ? response.setlist() : List.of();
				addSetlists(setlists, rawSetlists.stream()
						.filter(setlist -> parseDate(setlist.eventDate()).map(date -> !date.isBefore(threshold)).orElse(false))
						.toList());
				log.info("Setlists by year page processed. mbid={}, year={}, page={}, totalLoaded={}, validSetlists={}",
						musicBrainzId, year, page, setlists.size(),
						filterAndSortValidSetlists(setlists.values().stream().toList()).size());
				if (filterAndSortValidSetlists(setlists.values().stream().toList()).size() >= 3) {
					return;
				}
				if (isLastPage(response)) {
					break;
				}
			}
		}
	}

	private void addSetlists(Map<String, ConcertSetlist> setlists, List<Setlist> rawSetlists) {
		if (rawSetlists == null) {
			return;
		}
		for (Setlist rawSetlist : rawSetlists) {
			mapSetlist(rawSetlist).ifPresent(setlist -> setlists.putIfAbsent(setlist.id(), setlist));
		}
	}

	private boolean isLastPage(SetlistFmClient.SetlistsResponse response) {
		if (response == null || response.page() == null || response.itemsPerPage() == null || response.total() == null) {
			return false;
		}
		return response.page() * response.itemsPerPage() >= response.total();
	}

	private List<ConcertSetlist> filterAndSortValidSetlists(List<ConcertSetlist> setlists) {
		LocalDate threshold = recentSetlistThreshold();
		return setlists.stream()
				.filter(setlist -> !setlist.eventDate().isBefore(threshold))
				.filter(setlist -> setlist.songs().size() >= properties.minSongsPerSetlist())
				.sorted(Comparator.comparing(ConcertSetlist::eventDate).reversed())
				.toList();
	}

	private LocalDate recentSetlistThreshold() {
		return LocalDate.now().minusMonths(properties.maxAgeMonths());
	}

	private Optional<ConcertSetlist> mapSetlist(Setlist rawSetlist) {
		Optional<LocalDate> eventDate = parseDate(rawSetlist.eventDate());
		if (eventDate.isEmpty()) {
			return Optional.empty();
		}
		List<SetlistSong> songs = new ArrayList<>();
		if (rawSetlist.sets() != null && rawSetlist.sets().set() != null) {
			for (var set : rawSetlist.sets().set()) {
				if (set.song() == null) {
					continue;
				}
				for (var song : set.song()) {
					if (song.name() == null || song.name().isBlank() || Boolean.TRUE.equals(song.tape())) {
						continue;
					}
					String normalizedTitle = songNormalizer.normalizeTitle(song.name());
					if (normalizedTitle.isBlank()) {
						continue;
					}
					if (isNonMusicalSegment(song.name(), normalizedTitle)) {
						log.info("Skipping non-musical setlist segment. setlistId={}, title={}",
								rawSetlist.id(), song.name());
						continue;
					}
					songs.add(new SetlistSong(
							song.name(),
							normalizedTitle,
							songs.size() + 1,
							song.cover() != null,
							song.cover() != null ? song.cover().name() : null,
							song.with() != null ? song.with().name() : null,
							song.info()));
				}
			}
		}
		return Optional.of(new ConcertSetlist(
				rawSetlist.id(),
				eventDate.get(),
				rawSetlist.url(),
				rawSetlist.venue() != null ? rawSetlist.venue().name() : null,
				rawSetlist.venue() != null && rawSetlist.venue().city() != null ? rawSetlist.venue().city().name() : null,
				rawSetlist.venue() != null && rawSetlist.venue().city() != null
						&& rawSetlist.venue().city().country() != null ? rawSetlist.venue().city().country().name() : null,
				deduplicateSongs(songs)));
	}

	private List<SetlistSong> deduplicateSongs(List<SetlistSong> songs) {
		Map<String, SetlistSong> deduplicated = new LinkedHashMap<>();
		for (SetlistSong song : songs) {
			deduplicated.putIfAbsent(song.normalizedTitle(), song);
		}
		return new ArrayList<>(deduplicated.values());
	}

	private Optional<LocalDate> parseDate(String value) {
		try {
			return value != null ? Optional.of(LocalDate.parse(value, SETLIST_DATE_FORMAT)) : Optional.empty();
		}
		catch (DateTimeParseException ex) {
			return Optional.empty();
		}
	}

	private List<CandidateSong> findRecentSongs(List<ConcertSetlist> selectedSetlists) {
		Map<String, List<SetlistSong>> occurrencesByTitle = new LinkedHashMap<>();
		for (ConcertSetlist setlist : selectedSetlists) {
			for (SetlistSong song : setlist.songs()) {
				occurrencesByTitle.computeIfAbsent(song.normalizedTitle(), ignored -> new ArrayList<>()).add(song);
			}
		}
		return occurrencesByTitle.values().stream()
				.map(this::toCandidateSong)
				.toList();
	}

	private CandidateSong toCandidateSong(List<SetlistSong> occurrences) {
		SetlistSong latestSong = occurrences.get(0);
		double averagePosition = occurrences.stream()
				.mapToInt(SetlistSong::position)
				.average()
				.orElse(latestSong.position());
		Optional<SetlistSong> coverOccurrence = occurrences.stream()
				.filter(SetlistSong::cover)
				.findFirst();
		return new CandidateSong(
				latestSong.title(),
				latestSong.normalizedTitle(),
				latestSong.position(),
				averagePosition,
				coverOccurrence.isPresent(),
				coverOccurrence.map(SetlistSong::coverArtist).orElse(null));
	}

	private int tokenOverlapScore(String left, String right) {
		Set<String> leftTokens = splitTokens(left);
		Set<String> rightTokens = splitTokens(right);
		Map<String, Boolean> union = new HashMap<>();
		leftTokens.forEach(token -> union.put(token, Boolean.TRUE));
		rightTokens.forEach(token -> union.put(token, Boolean.TRUE));
		long intersection = leftTokens.stream().filter(rightTokens::contains).count();
		return union.isEmpty() ? 0 : (int) ((intersection * 100) / union.size());
	}

	private Set<String> splitTokens(String value) {
		if (value == null || value.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(value.split("\\s+"))
				.filter(token -> !token.isBlank())
				.collect(Collectors.toSet());
	}

	private boolean isNonMusicalSegment(String originalTitle, String normalizedTitle) {
		String normalizedOriginal = originalTitle == null ? "" : originalTitle.toLowerCase(Locale.ROOT);
		return NON_MUSICAL_TITLE_TOKENS.stream()
				.anyMatch(token -> normalizedTitle.contains(token) || normalizedOriginal.contains(token));
	}

	private record CachedSelection(SetlistSelection selection, Instant createdAt) {

		boolean isValid() {
			return createdAt.plus(SELECTION_CACHE_TTL).isAfter(Instant.now());
		}
	}
}
