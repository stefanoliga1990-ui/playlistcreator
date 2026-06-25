package com.application.playlistcreator.service;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.application.playlistcreator.client.lastfm.LastFmClient;
import com.application.playlistcreator.client.lastfm.LastFmClient.Artist;
import com.application.playlistcreator.client.lastfm.LastFmClient.Tag;
import com.application.playlistcreator.client.lastfm.LastFmClient.TagInfo;
import com.application.playlistcreator.client.lastfm.LastFmClient.TagInfoResponse;
import com.application.playlistcreator.client.lastfm.LastFmClient.Track;
import com.application.playlistcreator.client.spotify.SpotifyApiClient;
import com.application.playlistcreator.config.PlaylistCreatorProperties;
import com.application.playlistcreator.dto.SelectedTrackRequest;
import com.application.playlistcreator.exception.ExternalApiException;
import com.application.playlistcreator.model.GenreArtistCandidate;
import com.application.playlistcreator.model.GenrePlaylistSelection;
import com.application.playlistcreator.model.GenreTrackCandidate;
import com.application.playlistcreator.model.GenreTrackMatch;
import com.application.playlistcreator.model.SpotifyTrackMatch;
import com.application.playlistcreator.model.SpotifyTrackMatch.MatchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GenrePlaylistService {

	private static final Logger log = LoggerFactory.getLogger(GenrePlaylistService.class);
	private static final Duration CACHE_TTL = Duration.ofMinutes(30);
	private static final Duration ARTIST_TAGS_CACHE_TTL = Duration.ofHours(24);
	private static final int ARTIST_CANDIDATE_MULTIPLIER = 4;
	private static final int MAX_GENRE_TAG_RANK = 3;

	private final LastFmClient lastFmClient;
	private final GenreTagMatcher genreTagMatcher;
	private final SpotifyTrackMatchingService spotifyTrackMatchingService;
	private final SpotifyApiClient spotifyApiClient;
	private final SongNormalizer songNormalizer;
	private final PlaylistCreatorProperties.GenrePlaylist genreProperties;
	private final Map<String, CachedArtists> artistsCache = new ConcurrentHashMap<>();
	private final Map<String, CachedTopTags> artistTopTagsCache = new ConcurrentHashMap<>();
	private final Map<String, CachedSelection> selectionCache = new ConcurrentHashMap<>();

	public GenrePlaylistService(LastFmClient lastFmClient,
			GenreTagMatcher genreTagMatcher,
			SpotifyTrackMatchingService spotifyTrackMatchingService,
			SpotifyApiClient spotifyApiClient,
			SongNormalizer songNormalizer,
			PlaylistCreatorProperties properties) {
		this.lastFmClient = lastFmClient;
		this.genreTagMatcher = genreTagMatcher;
		this.spotifyTrackMatchingService = spotifyTrackMatchingService;
		this.spotifyApiClient = spotifyApiClient;
		this.songNormalizer = songNormalizer;
		this.genreProperties = properties.genrePlaylist();
	}

	public GenreArtistSearchResult findTopArtists(String genre, Integer artistLimit) {
		String normalizedGenre = validateGenre(genre);
		int limit = validateLimit(artistLimit, genreProperties.defaultArtistLimit(), genreProperties.maxArtistLimit(),
				"artistLimit");
		String genreKey = genreTagMatcher.normalize(normalizedGenre);
		String cacheKey = genreKey + ":" + limit;
		CachedArtists cachedArtists = artistsCache.get(cacheKey);
		if (cachedArtists != null && cachedArtists.isValid()) {
			log.info("Using cached filtered Last.fm genre artists. genre={}, requestedLimit={}, artists={}",
					normalizedGenre, limit, cachedArtists.result().artists().size());
			return cachedArtists.result();
		}

		TagInfoResponse tagInfoResponse = resolveGenreTag(normalizedGenre);
		if (tagInfoResponse == null || tagInfoResponse.tag() == null
				|| tagInfoResponse.tag().name() == null || tagInfoResponse.tag().name().isBlank()) {
			throw new ExternalApiException("No Last.fm tag found for genre: " + normalizedGenre);
		}
		String validatedGenre = tagInfoResponse.tag().name().trim();
		int candidateLimit = limit * ARTIST_CANDIDATE_MULTIPLIER;
		log.info("Last.fm genre tag validated. requestedGenre={}, validatedGenre={}, totalTaggings={}, reach={}",
				normalizedGenre, validatedGenre, tagInfoResponse.tag().total(), tagInfoResponse.tag().reach());
		log.info("Searching Last.fm top artist candidates. genre={}, requestedLimit={}, candidateLimit={}",
				validatedGenre, limit, candidateLimit);
		var response = lastFmClient.getTopArtists(validatedGenre, candidateLimit);
		List<Artist> artists = response != null && response.topartists() != null && response.topartists().artist() != null
				? response.topartists().artist()
				: List.of();
		List<GenreArtistCandidate> candidates = new ArrayList<>();
		int checkedCandidates = 0;
		for (int index = 0; index < artists.size(); index++) {
			Artist artist = artists.get(index);
			if (artist.name() == null || artist.name().isBlank()) {
				continue;
			}
			checkedCandidates++;
			List<Tag> topTags;
			try {
				topTags = getCachedArtistTopTags(artist);
			}
			catch (ExternalApiException ex) {
				if (isRateLimitError(ex)) {
					throw ex;
				}
				log.warn("Skipping Last.fm artist because top tags could not be retrieved. genre={}, artist={}, candidateRank={}, reason={}",
						validatedGenre, artist.name(), index + 1, ex.getMessage());
				continue;
			}
			GenreTagMatcher.MatchResult match = genreTagMatcher.evaluate(
					validatedGenre,
					topTags,
					MAX_GENRE_TAG_RANK);
			if (!match.accepted()) {
				log.info("Discarding Last.fm artist with marginal genre association. genre={}, artist={}, candidateRank={}, consideredTags={}",
						validatedGenre, artist.name(), index + 1, match.consideredTags());
				continue;
			}
			candidates.add(new GenreArtistCandidate(
					artist.name(),
					artist.mbid(),
					artist.url(),
					parseLong(artist.listeners()),
					candidates.size() + 1));
			log.info("Accepted Last.fm genre artist. genre={}, artist={}, candidateRank={}, genreTagRank={}, finalRank={}",
					validatedGenre, artist.name(), index + 1, match.genreRank(), candidates.size());
			if (candidates.size() == limit) {
				break;
			}
		}
		if (candidates.isEmpty()) {
			log.warn("No strongly associated Last.fm artists found for genre. genre={}, checkedCandidates={}",
					validatedGenre, checkedCandidates);
			throw new ExternalApiException("No strongly associated Last.fm artists found for genre: " + validatedGenre);
		}
		String warning = candidates.size() < limit
				? "Non sono stati trovati " + limit + " artisti fortemente associati al genere "
						+ toDisplayGenre(validatedGenre) + ". Verranno mostrati " + candidates.size() + " artisti."
				: null;
		GenreArtistSearchResult result = new GenreArtistSearchResult(
				validatedGenre,
				List.copyOf(candidates),
				limit,
				checkedCandidates,
				warning);
		artistsCache.put(cacheKey, new CachedArtists(result, Instant.now()));
		log.info("Filtered Last.fm top artists ready. genre={}, requestedLimit={}, checkedCandidates={}, acceptedArtists={}, warning={}",
				validatedGenre, limit, checkedCandidates, candidates.size(), warning != null);
		return result;
	}

	public GenrePlaylistSelection findTopTracks(
			String genre,
			Integer artistLimit,
			List<String> selectedArtistNames,
			Integer tracksPerArtist) {
		String normalizedGenre = validateGenre(genre);
		int artistsLimit = validateLimit(artistLimit, genreProperties.defaultArtistLimit(), genreProperties.maxArtistLimit(),
				"artistLimit");
		int trackLimit = validateLimit(tracksPerArtist, genreProperties.defaultTracksPerArtist(),
				genreProperties.maxTracksPerArtist(), "tracksPerArtist");
		List<GenreArtistCandidate> availableArtists = findTopArtists(normalizedGenre, artistsLimit).artists();
		List<GenreArtistCandidate> artists = filterSelectedArtists(availableArtists, selectedArtistNames);
		String cacheKey = normalizedGenre.toLowerCase() + ":" + artistsLimit + ":"
				+ artists.stream().map(GenreArtistCandidate::name).sorted().reduce("", (a, b) -> a + "," + b)
				+ ":" + trackLimit;
		CachedSelection cachedSelection = selectionCache.get(cacheKey);
		if (cachedSelection != null && cachedSelection.isValid()) {
			log.info("Using cached Last.fm genre tracks. genre={}, artists={}, tracksPerArtist={}, tracks={}",
					normalizedGenre, artistsLimit, trackLimit, cachedSelection.selection().tracks().size());
			return cachedSelection.selection();
		}

		List<GenreTrackCandidate> tracks = new ArrayList<>();
		for (GenreArtistCandidate artist : artists) {
			log.info("Searching Last.fm top tracks for genre artist. genre={}, artist={}, artistRank={}, limit={}",
					normalizedGenre, artist.name(), artist.rank(), trackLimit);
			var response = lastFmClient.getArtistTopTracks(artist.name(), trackLimit);
			List<Track> artistTracks = response != null && response.toptracks() != null && response.toptracks().track() != null
					? response.toptracks().track()
					: List.of();
			for (int index = 0; index < artistTracks.size(); index++) {
				Track track = artistTracks.get(index);
				if (track.name() == null || track.name().isBlank()) {
					continue;
				}
				tracks.add(new GenreTrackCandidate(
						artist.name(),
						track.name(),
						songNormalizer.normalizeTitle(track.name()),
						track.url(),
						parseLong(track.listeners()),
						parseLong(track.playcount()),
						artist.rank(),
						index + 1));
			}
		}
		List<GenreTrackCandidate> deduplicatedTracks = deduplicateTracks(tracks);
		if (deduplicatedTracks.isEmpty()) {
			log.warn("No Last.fm tracks found for genre. genre={}, artists={}, tracksPerArtist={}",
					normalizedGenre, artistsLimit, trackLimit);
			throw new ExternalApiException("No Last.fm tracks found for genre: " + normalizedGenre);
		}
		GenrePlaylistSelection selection = new GenrePlaylistSelection(normalizedGenre, artists, deduplicatedTracks);
		selectionCache.put(cacheKey, new CachedSelection(selection, Instant.now()));
		log.info("Last.fm genre tracks ready. genre={}, artists={}, requestedTracksPerArtist={}, tracks={}",
				normalizedGenre, artists.size(), trackLimit, deduplicatedTracks.size());
		return selection;
	}

	public GenreGenerationResult generatePlaylist(
			String accessToken,
			String genre,
			Integer artistLimit,
			List<String> selectedArtistNames,
			Integer tracksPerArtist,
			List<SelectedTrackRequest> selectedTracks,
			String playlistName,
			String playlistDescription) {
		log.info("Starting genre playlist generation. genre={}, artistLimit={}, tracksPerArtist={}, playlistName={}",
				genre, artistLimit, tracksPerArtist, playlistName);
		GenrePlaylistSelection fullSelection = findTopTracks(
				genre, artistLimit, selectedArtistNames, tracksPerArtist);
		List<GenreTrackCandidate> filteredTracks = filterSelectedTracks(
				fullSelection.tracks(), selectedTracks);
		GenrePlaylistSelection selection = new GenrePlaylistSelection(
				fullSelection.genre(),
				fullSelection.artists(),
				filteredTracks);
		List<GenreTrackMatch> matches = spotifyTrackMatchingService.matchGenreTracks(accessToken, selection.tracks());
		List<String> matchedUris = matches.stream()
				.map(GenreTrackMatch::spotifyMatch)
				.filter(match -> match.status() == MatchStatus.MATCHED)
				.map(SpotifyTrackMatch::uri)
				.distinct()
				.toList();
		log.info("Spotify matching ready for genre playlist creation. genre={}, tracks={}, matchedUris={}",
				selection.genre(), selection.tracks().size(), matchedUris.size());

		var user = spotifyApiClient.getCurrentUser(accessToken);
		log.info("Spotify current user resolved for genre playlist. userId={}, displayName={}, email={}",
				user.id(), user.display_name(), user.email());
		boolean publicFlag = true;
		String displayGenre = toDisplayGenre(selection.genre());
		String finalPlaylistName = playlistName != null && !playlistName.isBlank()
				? playlistName
				: displayGenre + " - Best Of";
		String description = playlistDescription != null && !playlistDescription.isBlank()
				? playlistDescription
				: "Playlist generated by playlistcreator from Last.fm " + displayGenre + " charts";
		var playlist = spotifyApiClient.createPlaylist(accessToken, finalPlaylistName, description, publicFlag);
		log.info("Spotify genre playlist created. playlistId={}, playlistName={}, requestedPublic={}, returnedPublic={}, url={}",
				playlist.id(), playlist.name(), publicFlag, playlist.publicPlaylist(),
				playlist.external_urls() != null ? playlist.external_urls().get("spotify") : null);
		spotifyApiClient.updatePlaylistVisibility(accessToken, playlist.id(), publicFlag);
		log.info("Spotify genre playlist visibility enforced. playlistId={}, publicPlaylist={}",
				playlist.id(), publicFlag);
		for (List<String> batch : batches(matchedUris, 100)) {
			log.info("Adding Spotify genre tracks batch. playlistId={}, batchSize={}", playlist.id(), batch.size());
			spotifyApiClient.addItemsToPlaylist(accessToken, playlist.id(), batch);
		}
		log.info("Genre playlist generation finished. playlistId={}, addedTracks={}", playlist.id(), matchedUris.size());
		return new GenreGenerationResult(selection, matches, playlist.id(), playlist.name(),
				playlist.external_urls() != null ? playlist.external_urls().get("spotify") : null,
				matchedUris.size());
	}

	private List<GenreArtistCandidate> filterSelectedArtists(
			List<GenreArtistCandidate> artists,
			List<String> selectedArtistNames) {
		if (selectedArtistNames == null || selectedArtistNames.isEmpty()) {
			throw new ExternalApiException("Select at least one artist");
		}
		Set<String> selected = new LinkedHashSet<>();
		selectedArtistNames.stream()
				.filter(name -> name != null && !name.isBlank())
				.map(songNormalizer::normalizeArtist)
				.forEach(selected::add);
		List<GenreArtistCandidate> filtered = artists.stream()
				.filter(artist -> selected.contains(songNormalizer.normalizeArtist(artist.name())))
				.toList();
		if (filtered.isEmpty()) {
			throw new ExternalApiException("None of the selected artists is available");
		}
		return filtered;
	}

	private List<GenreTrackCandidate> filterSelectedTracks(
			List<GenreTrackCandidate> tracks,
			List<SelectedTrackRequest> selectedTracks) {
		if (selectedTracks == null || selectedTracks.isEmpty()) {
			throw new ExternalApiException("Select at least one track");
		}
		Set<String> selected = selectedTracks.stream()
				.filter(track -> track != null && track.artistName() != null && track.title() != null)
				.map(track -> trackKey(track.artistName(), track.title()))
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		List<GenreTrackCandidate> filtered = tracks.stream()
				.filter(track -> selected.contains(trackKey(track.artistName(), track.title())))
				.toList();
		if (filtered.isEmpty()) {
			throw new ExternalApiException("None of the selected tracks is available");
		}
		return filtered;
	}

	private String trackKey(String artistName, String title) {
		return songNormalizer.normalizeArtist(artistName) + ":" + songNormalizer.normalizeTitle(title);
	}

	private String validateGenre(String genre) {
		if (genre == null || genre.isBlank()) {
			throw new ExternalApiException("genre is required");
		}
		return genre.trim();
	}

	private TagInfoResponse resolveGenreTag(String requestedGenre) {
		String normalized = genreTagMatcher.normalize(requestedGenre);
		if (!normalized.contains(" ")) {
			return lastFmClient.getTagInfo(requestedGenre);
		}

		String spacedVariant = normalized;
		String hyphenatedVariant = normalized.replace(' ', '-');
		TagInfoResponse spacedInfo = getExactTagInfoIfAvailable(spacedVariant);
		TagInfoResponse hyphenatedInfo = getExactTagInfoIfAvailable(hyphenatedVariant);

		if (!hasValidTag(spacedInfo) && !hasValidTag(hyphenatedInfo)) {
			log.info("No exact Last.fm tag variant found. requestedGenre={}; trying autocorrect fallback",
					requestedGenre);
			return lastFmClient.getTagInfo(requestedGenre);
		}
		if (!hasValidTag(spacedInfo)) {
			logResolvedGenreTag(requestedGenre, hyphenatedInfo.tag(), null);
			return hyphenatedInfo;
		}
		if (!hasValidTag(hyphenatedInfo)) {
			logResolvedGenreTag(requestedGenre, spacedInfo.tag(), null);
			return spacedInfo;
		}

		TagInfo selectedTag = selectMostEstablishedTag(spacedInfo.tag(), hyphenatedInfo.tag());
		TagInfoResponse selectedResponse = selectedTag == spacedInfo.tag() ? spacedInfo : hyphenatedInfo;
		TagInfo discardedTag = selectedTag == spacedInfo.tag() ? hyphenatedInfo.tag() : spacedInfo.tag();
		logResolvedGenreTag(requestedGenre, selectedTag, discardedTag);
		return selectedResponse;
	}

	private TagInfoResponse getExactTagInfoIfAvailable(String genre) {
		try {
			return lastFmClient.getTagInfoExact(genre);
		}
		catch (ExternalApiException ex) {
			if (isRateLimitError(ex)) {
				throw ex;
			}
			log.info("Exact Last.fm tag variant is not available. genre={}, reason={}", genre, ex.getMessage());
			return null;
		}
	}

	private boolean hasValidTag(TagInfoResponse response) {
		return response != null
				&& response.tag() != null
				&& response.tag().name() != null
				&& !response.tag().name().isBlank();
	}

	private TagInfo selectMostEstablishedTag(TagInfo first, TagInfo second) {
		long firstTaggings = parseLong(first.total());
		long secondTaggings = parseLong(second.total());
		long firstReach = parseLong(first.reach());
		long secondReach = parseLong(second.reach());

		if (firstTaggings >= secondTaggings && firstReach >= secondReach
				&& (firstTaggings > secondTaggings || firstReach > secondReach)) {
			return first;
		}
		if (secondTaggings >= firstTaggings && secondReach >= firstReach
				&& (secondTaggings > firstTaggings || secondReach > firstReach)) {
			return second;
		}

		BigInteger firstScore = BigInteger.valueOf(firstTaggings).multiply(BigInteger.valueOf(firstReach));
		BigInteger secondScore = BigInteger.valueOf(secondTaggings).multiply(BigInteger.valueOf(secondReach));
		int scoreComparison = firstScore.compareTo(secondScore);
		if (scoreComparison != 0) {
			return scoreComparison > 0 ? first : second;
		}
		if (firstReach != secondReach) {
			return firstReach > secondReach ? first : second;
		}
		if (firstTaggings != secondTaggings) {
			return firstTaggings > secondTaggings ? first : second;
		}
		return first;
	}

	private void logResolvedGenreTag(String requestedGenre, TagInfo selectedTag, TagInfo discardedTag) {
		log.info("Last.fm genre tag variant resolved. requestedGenre={}, selectedTag={}, selectedTaggings={}, selectedReach={}, discardedTag={}, discardedTaggings={}, discardedReach={}",
				requestedGenre,
				selectedTag.name(),
				selectedTag.total(),
				selectedTag.reach(),
				discardedTag != null ? discardedTag.name() : null,
				discardedTag != null ? discardedTag.total() : null,
				discardedTag != null ? discardedTag.reach() : null);
	}

	private int validateLimit(Integer value, int defaultValue, int maxValue, String fieldName) {
		int limit = value != null ? value : defaultValue;
		if (limit < 1) {
			throw new ExternalApiException(fieldName + " must be greater than zero");
		}
		if (limit > maxValue) {
			throw new ExternalApiException(fieldName + " must be less than or equal to " + maxValue);
		}
		return limit;
	}

	private List<GenreTrackCandidate> deduplicateTracks(List<GenreTrackCandidate> tracks) {
		Map<String, GenreTrackCandidate> deduplicated = new LinkedHashMap<>();
		for (GenreTrackCandidate track : tracks) {
			String key = songNormalizer.normalizeArtist(track.artistName()) + ":" + track.normalizedTitle();
			deduplicated.putIfAbsent(key, track);
		}
		return new ArrayList<>(deduplicated.values());
	}

	private List<List<String>> batches(List<String> values, int batchSize) {
		List<List<String>> batches = new ArrayList<>();
		for (int start = 0; start < values.size(); start += batchSize) {
			batches.add(values.subList(start, Math.min(start + batchSize, values.size())));
		}
		return batches;
	}

	private long parseLong(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		try {
			return Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			return 0;
		}
	}

	private List<Tag> getCachedArtistTopTags(Artist artist) {
		String cacheKey = artist.mbid() != null && !artist.mbid().isBlank()
				? "mbid:" + artist.mbid()
				: "artist:" + genreTagMatcher.normalize(artist.name());
		CachedTopTags cachedTopTags = artistTopTagsCache.get(cacheKey);
		if (cachedTopTags != null && cachedTopTags.isValid()) {
			log.info("Using cached Last.fm artist top tags. artist={}, tags={}",
					artist.name(), cachedTopTags.tags().size());
			return cachedTopTags.tags();
		}
		var response = lastFmClient.getArtistTopTags(artist.name(), artist.mbid());
		List<Tag> tags = response != null && response.toptags() != null && response.toptags().tag() != null
				? List.copyOf(response.toptags().tag())
				: List.of();
		artistTopTagsCache.put(cacheKey, new CachedTopTags(tags, Instant.now()));
		return tags;
	}

	private boolean isRateLimitError(ExternalApiException ex) {
		return ex.getMessage() != null && ex.getMessage().contains("Last.fm error 29");
	}

	private String toDisplayGenre(String genre) {
		if (genre == null || genre.isBlank()) {
			return "Genre";
		}
		String[] tokens = genre.trim().split("\\s+");
		List<String> displayTokens = new ArrayList<>();
		for (String token : tokens) {
			if (token.isBlank()) {
				continue;
			}
			displayTokens.add(token.substring(0, 1).toUpperCase() + token.substring(1).toLowerCase());
		}
		return String.join(" ", displayTokens);
	}

	private record CachedArtists(GenreArtistSearchResult result, Instant createdAt) {

		boolean isValid() {
			return createdAt.plus(CACHE_TTL).isAfter(Instant.now());
		}
	}

	private record CachedTopTags(List<Tag> tags, Instant createdAt) {

		boolean isValid() {
			return createdAt.plus(ARTIST_TAGS_CACHE_TTL).isAfter(Instant.now());
		}
	}

	private record CachedSelection(GenrePlaylistSelection selection, Instant createdAt) {

		boolean isValid() {
			return createdAt.plus(CACHE_TTL).isAfter(Instant.now());
		}
	}

	public record GenreGenerationResult(
			GenrePlaylistSelection selection,
			List<GenreTrackMatch> matches,
			String playlistId,
			String playlistName,
			String playlistUrl,
			int addedTracksCount) {
	}

	public record GenreArtistSearchResult(
			String genre,
			List<GenreArtistCandidate> artists,
			int requestedArtistCount,
			int checkedCandidateCount,
			String warning) {
	}
}
