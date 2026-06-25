package com.application.playlistcreator.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.application.playlistcreator.client.lastfm.LastFmClient;
import com.application.playlistcreator.client.lastfm.LastFmClient.SimilarArtist;
import com.application.playlistcreator.client.lastfm.LastFmClient.Tag;
import com.application.playlistcreator.client.lastfm.LastFmClient.Track;
import com.application.playlistcreator.client.spotify.SpotifyApiClient;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.SpotifyArtist;
import com.application.playlistcreator.dto.SelectedTrackRequest;
import com.application.playlistcreator.exception.ExternalApiException;
import com.application.playlistcreator.model.GenreTrackCandidate;
import com.application.playlistcreator.model.GenreTrackMatch;
import com.application.playlistcreator.model.SimilarArtistCandidate;
import com.application.playlistcreator.model.SimilarArtistsPlaylistSelection;
import com.application.playlistcreator.model.SpotifyTrackMatch.MatchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SimilarArtistsPlaylistService {

	private static final Logger log = LoggerFactory.getLogger(SimilarArtistsPlaylistService.class);
	private static final int RESULT_LIMIT = 10;
	private static final int CANDIDATE_LIMIT = 30;
	private static final int MIN_CANDIDATES_TO_EVALUATE = 15;
	private static final int RECIPROCAL_SEARCH_LIMIT = 50;
	private static final int TAG_LIMIT = 10;
	private static final int MIN_SIMILARITY_SCORE = 50;
	private static final Duration CACHE_TTL = Duration.ofHours(24);

	private final LastFmClient lastFmClient;
	private final SpotifyTrackMatchingService spotifyTrackMatchingService;
	private final SpotifyApiClient spotifyApiClient;
	private final SongNormalizer songNormalizer;
	private final Map<String, CachedArtists> artistsCache = new ConcurrentHashMap<>();
	private final Map<String, CachedTags> tagsCache = new ConcurrentHashMap<>();
	private final Map<String, CachedSimilarArtists> similarCache = new ConcurrentHashMap<>();
	private final Map<String, CachedSelection> selectionCache = new ConcurrentHashMap<>();

	public SimilarArtistsPlaylistService(
			LastFmClient lastFmClient,
			SpotifyTrackMatchingService spotifyTrackMatchingService,
			SpotifyApiClient spotifyApiClient,
			SongNormalizer songNormalizer) {
		this.lastFmClient = lastFmClient;
		this.spotifyTrackMatchingService = spotifyTrackMatchingService;
		this.spotifyApiClient = spotifyApiClient;
		this.songNormalizer = songNormalizer;
	}

	public ArtistSearchResult findSimilarArtists(String artistName) {
		String requestedArtist = validateArtistName(artistName);
		String cacheKey = songNormalizer.normalizeArtist(requestedArtist);
		CachedArtists cached = artistsCache.get(cacheKey);
		if (cached != null && cached.isValid()) {
			log.info("Using cached validated similar artists. artist={}, artists={}",
					requestedArtist, cached.result().artists().size());
			return cached.result();
		}

		List<SimilarArtist> directCandidates = getSimilarArtists(requestedArtist, CANDIDATE_LIMIT);
		if (directCandidates.isEmpty()) {
			throw new ExternalApiException("No similar artists found on Last.fm for: " + requestedArtist);
		}
		String resolvedArtist = resolveSourceArtist(requestedArtist);
		String normalizedSource = songNormalizer.normalizeArtist(resolvedArtist);
		List<Tag> sourceTags = getTopTags(resolvedArtist, null);
		List<SimilarArtistCandidate> scoredCandidates = new ArrayList<>();
		int evaluatedCandidates = 0;

		for (SimilarArtist candidate : directCandidates) {
			if (candidate == null || candidate.name() == null || candidate.name().isBlank()
					|| normalizedSource.equals(songNormalizer.normalizeArtist(candidate.name()))) {
				continue;
			}
			evaluatedCandidates++;
			double directSimilarity = clamp(parseDouble(candidate.match()));
			double reciprocalSimilarity;
			List<Tag> candidateTags;
			try {
				reciprocalSimilarity = findReciprocalSimilarity(candidate.name(), normalizedSource);
				candidateTags = getTopTags(candidate.name(), candidate.mbid());
			}
			catch (ExternalApiException ex) {
				if (isRateLimitError(ex)) {
					throw ex;
				}
				log.warn("Skipping similar artist candidate because validation failed. sourceArtist={}, candidate={}, reason={}",
						resolvedArtist, candidate.name(), ex.getMessage());
				continue;
			}
			double tagSimilarity = calculateTagSimilarity(sourceTags, candidateTags);
			int finalScore = (int) Math.round(
					directSimilarity * 50
							+ reciprocalSimilarity * 30
							+ tagSimilarity * 20);
			SimilarArtistCandidate scoredCandidate = new SimilarArtistCandidate(
					candidate.name(),
					candidate.mbid(),
					candidate.url(),
					directSimilarity,
					reciprocalSimilarity,
					tagSimilarity,
					finalScore,
					candidateTags.stream()
							.filter(tag -> tag != null && tag.name() != null)
							.limit(5)
							.map(Tag::name)
							.toList(),
					0);
			log.info("Similar artist evaluated. sourceArtist={}, candidate={}, directSimilarity={}, reciprocalSimilarity={}, tagSimilarity={}, score={}",
					resolvedArtist, candidate.name(), directSimilarity, reciprocalSimilarity, tagSimilarity, finalScore);
			if (finalScore >= MIN_SIMILARITY_SCORE) {
				scoredCandidates.add(scoredCandidate);
			}
			else {
				log.info("Discarding weak similar artist candidate. sourceArtist={}, candidate={}, score={}, minimumScore={}",
						resolvedArtist, candidate.name(), finalScore, MIN_SIMILARITY_SCORE);
			}
			if (evaluatedCandidates >= MIN_CANDIDATES_TO_EVALUATE
					&& scoredCandidates.size() >= RESULT_LIMIT) {
				break;
			}
		}

		List<SimilarArtistCandidate> artists = scoredCandidates.stream()
				.sorted(Comparator.comparingInt(SimilarArtistCandidate::similarityScore).reversed()
						.thenComparing(Comparator.comparingDouble(
								SimilarArtistCandidate::directSimilarity).reversed()))
				.limit(RESULT_LIMIT)
				.toList();
		artists = rerank(artists);
		if (artists.isEmpty()) {
			throw new ExternalApiException("No valid similar artists found on Last.fm for: " + resolvedArtist);
		}
		String warning = artists.size() < 5
				? "Last.fm ha restituito solo " + artists.size() + " artisti simili validi"
				: null;
		ArtistSearchResult result = new ArtistSearchResult(
				resolvedArtist,
				List.copyOf(artists),
				evaluatedCandidates,
				warning);
		artistsCache.put(cacheKey, new CachedArtists(result, Instant.now()));
		log.info("Validated similar artists ready. artist={}, candidates={}, artists={}, warning={}",
				resolvedArtist, evaluatedCandidates, artists.size(), warning != null);
		return result;
	}

	public SourceArtistSearchResult findSourceArtists(String accessToken, String artistName) {
		String query = validateArtistName(artistName);
		String normalizedQuery = songNormalizer.normalizeArtist(query);
		var response = spotifyApiClient.searchArtists(accessToken, query);
		List<SpotifyArtist> spotifyArtists = response != null
				&& response.artists() != null
				&& response.artists().items() != null
						? response.artists().items()
						: List.of();
		List<SourceArtistSearchResult.Artist> artists = spotifyArtists.stream()
				.filter(artist -> artist != null
						&& artist.id() != null
						&& artist.name() != null
						&& songNormalizer.normalizeArtist(artist.name()).contains(normalizedQuery))
				.sorted(Comparator
						.comparingInt((SpotifyArtist artist) -> artist.popularity() != null ? artist.popularity() : 0)
						.reversed()
						.thenComparing(SpotifyArtist::name, String.CASE_INSENSITIVE_ORDER))
				.map(artist -> new SourceArtistSearchResult.Artist(
						artist.id(),
						artist.name(),
						artist.external_urls() != null ? artist.external_urls().get("spotify") : null))
				.toList();
		if (artists.isEmpty()) {
			throw new ExternalApiException("Nessun artista Spotify trovato per: " + query);
		}
		log.info("Spotify source artists ready for similar artist search. query={}, spotifyCandidates={}, matchingArtists={}",
				query, spotifyArtists.size(), artists.size());
		return new SourceArtistSearchResult(query, artists);
	}

	public SimilarArtistsPlaylistSelection findTopTracks(
			String sourceArtistName,
			List<String> selectedArtistNames,
			Integer tracksPerArtist) {
		String sourceArtist = validateArtistName(sourceArtistName);
		int trackLimit = validateTracksPerArtist(tracksPerArtist);
		ArtistSearchResult search = findSimilarArtists(sourceArtist);
		Set<String> selectedNames = normalizeSelectedArtists(selectedArtistNames);
		List<SimilarArtistCandidate> selectedArtists = search.artists().stream()
				.filter(artist -> selectedNames.contains(songNormalizer.normalizeArtist(artist.name())))
				.toList();
		if (selectedArtists.isEmpty()) {
			throw new ExternalApiException("Select at least one similar artist");
		}
		String cacheKey = selectionCacheKey(search.sourceArtistName(), selectedArtists, trackLimit);
		CachedSelection cached = selectionCache.get(cacheKey);
		if (cached != null && cached.isValid()) {
			log.info("Using cached similar artists tracks. sourceArtist={}, artists={}, tracksPerArtist={}",
					search.sourceArtistName(), selectedArtists.size(), trackLimit);
			return cached.selection();
		}

		List<GenreTrackCandidate> tracks = new ArrayList<>();
		List<String> incompleteArtists = new ArrayList<>();
		for (SimilarArtistCandidate artist : selectedArtists) {
			var response = lastFmClient.getArtistTopTracks(artist.name(), trackLimit);
			List<Track> artistTracks = response != null && response.toptracks() != null
					&& response.toptracks().track() != null ? response.toptracks().track() : List.of();
			int acceptedTracks = 0;
			for (Track track : artistTracks) {
				if (track == null || track.name() == null || track.name().isBlank()) {
					continue;
				}
				acceptedTracks++;
				tracks.add(new GenreTrackCandidate(
						artist.name(),
						track.name(),
						songNormalizer.normalizeTitle(track.name()),
						track.url(),
						parseLong(track.listeners()),
						parseLong(track.playcount()),
						artist.rank(),
						acceptedTracks));
				if (acceptedTracks == trackLimit) {
					break;
				}
			}
			if (acceptedTracks < trackLimit) {
				incompleteArtists.add(artist.name() + " (" + acceptedTracks + "/" + trackLimit + ")");
			}
			log.info("Similar artist top tracks loaded. sourceArtist={}, artist={}, requestedTracks={}, availableTracks={}",
					search.sourceArtistName(), artist.name(), trackLimit, acceptedTracks);
		}
		List<GenreTrackCandidate> deduplicatedTracks = deduplicateTracks(tracks);
		if (deduplicatedTracks.isEmpty()) {
			throw new ExternalApiException("No Last.fm tracks found for the selected similar artists");
		}
		String warning = incompleteArtists.isEmpty()
				? null
				: "Per alcuni artisti Last.fm ha restituito meno di " + trackLimit
						+ " brani: " + String.join(", ", incompleteArtists) + ".";
		SimilarArtistsPlaylistSelection selection = new SimilarArtistsPlaylistSelection(
				search.sourceArtistName(),
				List.copyOf(selectedArtists),
				List.copyOf(deduplicatedTracks),
				trackLimit,
				warning);
		selectionCache.put(cacheKey, new CachedSelection(selection, Instant.now()));
		return selection;
	}

	public GenerationResult generatePlaylist(
			String accessToken,
			String sourceArtistName,
			List<String> selectedArtistNames,
			Integer tracksPerArtist,
			List<SelectedTrackRequest> selectedTracks,
			String playlistName,
			String playlistDescription) {
		SimilarArtistsPlaylistSelection fullSelection = findTopTracks(
				sourceArtistName, selectedArtistNames, tracksPerArtist);
		List<GenreTrackCandidate> filteredTracks = filterSelectedTracks(
				fullSelection.tracks(), selectedTracks);
		SimilarArtistsPlaylistSelection selection = new SimilarArtistsPlaylistSelection(
				fullSelection.sourceArtistName(),
				fullSelection.artists(),
				filteredTracks,
				fullSelection.tracksPerArtist(),
				fullSelection.warning());
		List<GenreTrackMatch> matches = spotifyTrackMatchingService.matchGenreTracks(
				accessToken, selection.tracks());
		List<String> matchedUris = matches.stream()
				.filter(match -> match.spotifyMatch().status() == MatchStatus.MATCHED)
				.map(match -> match.spotifyMatch().uri())
				.filter(uri -> uri != null && !uri.isBlank())
				.distinct()
				.toList();
		if (matchedUris.isEmpty()) {
			throw new ExternalApiException("No sufficiently reliable Spotify matches found");
		}
		String finalName = playlistName != null && !playlistName.isBlank()
				? playlistName.trim()
				: selection.sourceArtistName() + " - Artisti simili";
		String description = playlistDescription != null && !playlistDescription.isBlank()
				? playlistDescription.trim()
				: "Playlist generated by playlistcreator from artists similar to "
						+ selection.sourceArtistName();
		var playlist = spotifyApiClient.createPlaylist(accessToken, finalName, description, true);
		spotifyApiClient.updatePlaylistVisibility(accessToken, playlist.id(), true);
		for (List<String> batch : batches(matchedUris, 100)) {
			spotifyApiClient.addItemsToPlaylist(accessToken, playlist.id(), batch);
		}
		log.info("Similar artists playlist generated. sourceArtist={}, playlistId={}, artists={}, matchedTracks={}",
				selection.sourceArtistName(), playlist.id(), selection.artists().size(), matchedUris.size());
		return new GenerationResult(
				selection,
				matches,
				playlist.id(),
				playlist.name(),
				playlist.external_urls() != null ? playlist.external_urls().get("spotify") : null,
				matchedUris.size());
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

	private List<SimilarArtist> getSimilarArtists(String artistName, int limit) {
		String cacheKey = songNormalizer.normalizeArtist(artistName) + ":" + limit;
		CachedSimilarArtists cached = similarCache.get(cacheKey);
		if (cached != null && cached.isValid()) {
			return cached.artists();
		}
		var response = lastFmClient.getSimilarArtists(artistName, limit);
		List<SimilarArtist> artists = response != null && response.similarartists() != null
				&& response.similarartists().artist() != null
						? List.copyOf(response.similarartists().artist())
						: List.of();
		similarCache.put(cacheKey, new CachedSimilarArtists(artists, Instant.now()));
		return artists;
	}

	private String resolveSourceArtist(String requestedArtist) {
		var response = lastFmClient.getSimilarArtists(requestedArtist, 1);
		if (response != null && response.similarartists() != null
				&& response.similarartists().attributes() != null
				&& response.similarartists().attributes().artist() != null
				&& !response.similarartists().attributes().artist().isBlank()) {
			return response.similarartists().attributes().artist().trim();
		}
		return requestedArtist;
	}

	private double findReciprocalSimilarity(String candidateName, String normalizedSource) {
		return getSimilarArtists(candidateName, RECIPROCAL_SEARCH_LIMIT).stream()
				.filter(artist -> artist != null && artist.name() != null
						&& normalizedSource.equals(songNormalizer.normalizeArtist(artist.name())))
				.mapToDouble(artist -> clamp(parseDouble(artist.match())))
				.max()
				.orElse(0);
	}

	private List<Tag> getTopTags(String artistName, String musicBrainzId) {
		String cacheKey = musicBrainzId != null && !musicBrainzId.isBlank()
				? "mbid:" + musicBrainzId
				: "artist:" + songNormalizer.normalizeArtist(artistName);
		CachedTags cached = tagsCache.get(cacheKey);
		if (cached != null && cached.isValid()) {
			return cached.tags();
		}
		var response = lastFmClient.getArtistTopTags(artistName, musicBrainzId);
		List<Tag> tags = response != null && response.toptags() != null && response.toptags().tag() != null
				? response.toptags().tag().stream().limit(TAG_LIMIT).toList()
				: List.of();
		tagsCache.put(cacheKey, new CachedTags(tags, Instant.now()));
		return tags;
	}

	private double calculateTagSimilarity(List<Tag> sourceTags, List<Tag> candidateTags) {
		Map<String, Double> sourceWeights = tagWeights(sourceTags);
		Map<String, Double> candidateWeights = tagWeights(candidateTags);
		if (sourceWeights.isEmpty() || candidateWeights.isEmpty()) {
			return 0;
		}
		double sharedWeight = sourceWeights.entrySet().stream()
				.filter(entry -> candidateWeights.containsKey(entry.getKey()))
				.mapToDouble(entry -> Math.min(entry.getValue(), candidateWeights.get(entry.getKey())))
				.sum();
		double sourceWeight = sourceWeights.values().stream().mapToDouble(Double::doubleValue).sum();
		return sourceWeight == 0 ? 0 : clamp(sharedWeight / sourceWeight);
	}

	private Map<String, Double> tagWeights(List<Tag> tags) {
		Map<String, Double> weights = new LinkedHashMap<>();
		for (int index = 0; index < Math.min(TAG_LIMIT, tags.size()); index++) {
			Tag tag = tags.get(index);
			if (tag == null || tag.name() == null || tag.name().isBlank()) {
				continue;
			}
			weights.putIfAbsent(songNormalizer.normalizeArtist(tag.name()), 1.0 / (index + 1));
		}
		return weights;
	}

	private Set<String> normalizeSelectedArtists(List<String> selectedArtistNames) {
		if (selectedArtistNames == null || selectedArtistNames.isEmpty()) {
			throw new ExternalApiException("Select at least one similar artist");
		}
		Set<String> names = new LinkedHashSet<>();
		selectedArtistNames.stream()
				.filter(name -> name != null && !name.isBlank())
				.map(songNormalizer::normalizeArtist)
				.forEach(names::add);
		return names;
	}

	private List<SimilarArtistCandidate> rerank(List<SimilarArtistCandidate> artists) {
		List<SimilarArtistCandidate> ranked = new ArrayList<>();
		for (int index = 0; index < artists.size(); index++) {
			SimilarArtistCandidate artist = artists.get(index);
			ranked.add(new SimilarArtistCandidate(
					artist.name(),
					artist.musicBrainzId(),
					artist.lastFmUrl(),
					artist.directSimilarity(),
					artist.reciprocalSimilarity(),
					artist.tagSimilarity(),
					artist.similarityScore(),
					artist.topTags(),
					index + 1));
		}
		return ranked;
	}

	private List<GenreTrackCandidate> deduplicateTracks(List<GenreTrackCandidate> tracks) {
		Map<String, GenreTrackCandidate> deduplicated = new LinkedHashMap<>();
		for (GenreTrackCandidate track : tracks) {
			String key = songNormalizer.normalizeArtist(track.artistName()) + ":" + track.normalizedTitle();
			deduplicated.putIfAbsent(key, track);
		}
		return new ArrayList<>(deduplicated.values());
	}

	private String selectionCacheKey(
			String sourceArtist,
			List<SimilarArtistCandidate> artists,
			int tracksPerArtist) {
		return songNormalizer.normalizeArtist(sourceArtist) + ":"
				+ artists.stream()
						.map(SimilarArtistCandidate::name)
						.map(songNormalizer::normalizeArtist)
						.sorted()
						.reduce("", (left, right) -> left + "," + right)
				+ ":" + tracksPerArtist;
	}

	private String validateArtistName(String artistName) {
		if (artistName == null || artistName.isBlank()) {
			throw new ExternalApiException("artistName is required");
		}
		return artistName.trim();
	}

	private int validateTracksPerArtist(Integer value) {
		int limit = value != null ? value : 3;
		if (limit < 1 || limit > 10) {
			throw new ExternalApiException("tracksPerArtist must be between 1 and 10");
		}
		return limit;
	}

	private double parseDouble(String value) {
		try {
			return value == null ? 0 : Double.parseDouble(value);
		}
		catch (NumberFormatException ex) {
			return 0;
		}
	}

	private long parseLong(String value) {
		try {
			return value == null ? 0 : Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			return 0;
		}
	}

	private double clamp(double value) {
		return Math.max(0, Math.min(1, value));
	}

	private boolean isRateLimitError(ExternalApiException ex) {
		return ex.getMessage() != null
				&& (ex.getMessage().contains("Last.fm error 29")
						|| ex.getMessage().contains("429 TOO_MANY_REQUESTS"));
	}

	private List<List<String>> batches(List<String> values, int batchSize) {
		List<List<String>> batches = new ArrayList<>();
		for (int start = 0; start < values.size(); start += batchSize) {
			batches.add(values.subList(start, Math.min(start + batchSize, values.size())));
		}
		return batches;
	}

	private record CachedArtists(ArtistSearchResult result, Instant createdAt) {
		boolean isValid() {
			return createdAt.plus(CACHE_TTL).isAfter(Instant.now());
		}
	}

	private record CachedTags(List<Tag> tags, Instant createdAt) {
		boolean isValid() {
			return createdAt.plus(CACHE_TTL).isAfter(Instant.now());
		}
	}

	private record CachedSimilarArtists(List<SimilarArtist> artists, Instant createdAt) {
		boolean isValid() {
			return createdAt.plus(CACHE_TTL).isAfter(Instant.now());
		}
	}

	private record CachedSelection(SimilarArtistsPlaylistSelection selection, Instant createdAt) {
		boolean isValid() {
			return createdAt.plus(CACHE_TTL).isAfter(Instant.now());
		}
	}

	public record ArtistSearchResult(
			String sourceArtistName,
			List<SimilarArtistCandidate> artists,
			int checkedCandidateCount,
			String warning) {
	}

	public record SourceArtistSearchResult(
			String query,
			List<Artist> artists) {

		public record Artist(String id, String name, String spotifyUrl) {
		}
	}

	public record GenerationResult(
			SimilarArtistsPlaylistSelection selection,
			List<GenreTrackMatch> matches,
			String playlistId,
			String playlistName,
			String playlistUrl,
			int addedTracksCount) {
	}
}
