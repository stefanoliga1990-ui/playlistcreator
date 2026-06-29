package com.application.playlistcreator.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.application.playlistcreator.client.lastfm.LastFmClient;
import com.application.playlistcreator.client.lastfm.LastFmClient.SimilarArtist;
import com.application.playlistcreator.client.lastfm.LastFmClient.Track;
import com.application.playlistcreator.client.spotify.SpotifyApiClient;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.SpotifyArtist;
import com.application.playlistcreator.config.BoundedCacheFactory;
import com.application.playlistcreator.dto.SelectedArtistRequest;
import com.application.playlistcreator.dto.SelectedTrackRequest;
import com.application.playlistcreator.exception.ExternalApiException;
import com.application.playlistcreator.model.GenreTrackCandidate;
import com.application.playlistcreator.model.GenreTrackMatch;
import com.application.playlistcreator.model.SpotifyTrackMatch.MatchStatus;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DiscoverNewMusicService {

	private static final Logger log = LoggerFactory.getLogger(DiscoverNewMusicService.class);
	private static final Duration CACHE_TTL = Duration.ofMinutes(30);
	private static final long USER_CACHE_MAX_SIZE = 100;
	private static final long RESULT_CACHE_MAX_SIZE = 500;
	private static final int TOP_ARTIST_LIMIT = 10;
	private static final int PROFILE_LIMIT = 50;
	private static final int SIMILAR_ARTISTS_PER_SOURCE = 4;
	private static final int LASTFM_TRACK_CANDIDATE_LIMIT = 10;
	private static final int TRACKS_PER_ARTIST = 3;
	private static final int SPOTIFY_PAGE_SIZE = 50;
	private static final List<String> PROFILE_TIME_RANGES = List.of(
			"short_term",
			"medium_term",
			"long_term");

	private final SpotifyApiClient spotifyApiClient;
	private final LastFmClient lastFmClient;
	private final SpotifyTrackMatchingService spotifyTrackMatchingService;
	private final SongNormalizer songNormalizer;
	private final Cache<String, TopArtistsResult> topArtistsCache = BoundedCacheFactory.create(
			CACHE_TTL, USER_CACHE_MAX_SIZE);
	private final Cache<String, KnownMusicProfile> knownMusicProfileCache = BoundedCacheFactory.create(
			CACHE_TTL, USER_CACHE_MAX_SIZE);
	private final Cache<String, SimilarArtistsResult> similarArtistsCache = BoundedCacheFactory.create(
			CACHE_TTL, RESULT_CACHE_MAX_SIZE);
	private final Cache<String, TracksResult> tracksCache = BoundedCacheFactory.create(
			CACHE_TTL, RESULT_CACHE_MAX_SIZE);

	public DiscoverNewMusicService(
			SpotifyApiClient spotifyApiClient,
			LastFmClient lastFmClient,
			SpotifyTrackMatchingService spotifyTrackMatchingService,
			SongNormalizer songNormalizer) {
		this.spotifyApiClient = spotifyApiClient;
		this.lastFmClient = lastFmClient;
		this.spotifyTrackMatchingService = spotifyTrackMatchingService;
		this.songNormalizer = songNormalizer;
	}

	public TopArtistsResult findTopArtists(String accessToken) {
		String userId = currentUserId(accessToken);
		TopArtistsResult cached = topArtistsCache.getIfPresent(userId);
		if (cached != null) {
			return cached;
		}
		var page = spotifyApiClient.getCurrentUserTopArtists(
				accessToken, "medium_term", TOP_ARTIST_LIMIT, 0);
		List<SpotifyArtist> items = page != null && page.items() != null ? page.items() : List.of();
		List<TopArtist> artists = new ArrayList<>();
		for (SpotifyArtist artist : items) {
			if (artist == null || artist.id() == null || artist.name() == null) {
				continue;
			}
			artists.add(new TopArtist(
					artists.size() + 1,
					artist.id(),
					artist.name(),
					artist.external_urls() != null ? artist.external_urls().get("spotify") : null));
		}
		if (artists.isEmpty()) {
			throw new ExternalApiException(
					"Spotify did not return enough artists to create recommendations");
		}
		String warning = artists.size() < TOP_ARTIST_LIMIT
				? "Spotify returned only " + artists.size()
						+ " of the " + TOP_ARTIST_LIMIT + " requested artists."
				: null;
		TopArtistsResult result = new TopArtistsResult(List.copyOf(artists), warning);
		topArtistsCache.put(userId, result);
		log.info("Discovery top artists ready. userId={}, artists={}, warning={}",
				userId, artists.size(), warning != null);
		return result;
	}

	public SimilarArtistsResult findSimilarArtists(
			String accessToken,
			List<SelectedArtistRequest> selectedArtists) {
		List<TopArtist> sources = validateSelectedTopArtists(accessToken, selectedArtists);
		String cacheKey = artistSelectionKey(sources);
		SimilarArtistsResult cached = similarArtistsCache.getIfPresent(cacheKey);
		if (cached != null) {
			return cached;
		}

		Set<String> sourceIds = sources.stream().map(TopArtist::id)
				.collect(java.util.stream.Collectors.toSet());
		Map<String, SimilarArtistResultBuilder> deduplicated = new LinkedHashMap<>();
		List<String> unresolvedArtists = new ArrayList<>();
		for (TopArtist source : sources) {
			var response = lastFmClient.getSimilarArtists(
					source.name(), SIMILAR_ARTISTS_PER_SOURCE);
			List<SimilarArtist> lastFmArtists = response != null
					&& response.similarartists() != null
					&& response.similarartists().artist() != null
							? response.similarartists().artist()
							: List.of();
			for (SimilarArtist candidate : lastFmArtists) {
				if (candidate == null || candidate.name() == null || candidate.name().isBlank()) {
					continue;
				}
				SpotifyArtist spotifyArtist = resolveExactSpotifyArtist(accessToken, candidate.name());
				if (spotifyArtist == null) {
					unresolvedArtists.add(candidate.name());
					continue;
				}
				if (sourceIds.contains(spotifyArtist.id())) {
					continue;
				}
				SimilarArtistResultBuilder builder = deduplicated.computeIfAbsent(
						spotifyArtist.id(),
						ignored -> new SimilarArtistResultBuilder(
								spotifyArtist.id(),
								spotifyArtist.name(),
								spotifyArtist.external_urls() != null
										? spotifyArtist.external_urls().get("spotify")
										: null));
				builder.basedOnArtists().add(source.name());
			}
		}
		List<DiscoveryArtist> artists = deduplicated.values().stream()
				.map(SimilarArtistResultBuilder::build)
				.toList();
		if (artists.isEmpty()) {
			throw new ExternalApiException(
					"No usable similar artists were found on Spotify");
		}
		String warning = unresolvedArtists.isEmpty()
				? null
				: "Some artists suggested by Last.fm could not be matched accurately on Spotify.";
		SimilarArtistsResult result = new SimilarArtistsResult(
				List.copyOf(sources),
				artists,
				warning);
		similarArtistsCache.put(cacheKey, result);
		log.info("Discovery similar artists ready. sources={}, artists={}, unresolvedArtists={}, warning={}",
				sources.size(), artists.size(), unresolvedArtists.size(), warning != null);
		return result;
	}

	public TracksResult findNewTracks(
			String accessToken,
			List<SelectedArtistRequest> sourceArtists,
			List<SelectedArtistRequest> selectedSimilarArtists) {
		SimilarArtistsResult similarResult = findSimilarArtists(accessToken, sourceArtists);
		List<DiscoveryArtist> selectedArtists = filterSelectedSimilarArtists(
				similarResult.artists(), selectedSimilarArtists);
		String userId = currentUserId(accessToken);
		String cacheKey = userId + ":"
				+ artistSelectionKey(similarResult.sourceArtists()) + ":"
				+ artistSelectionKey(selectedArtists);
		TracksResult cached = tracksCache.getIfPresent(cacheKey);
		if (cached != null) {
			return cached;
		}

		KnownMusicProfile knownMusic = loadKnownMusicProfile(accessToken, userId);
		Set<String> selectedTrackIds = new LinkedHashSet<>();
		List<DiscoveryTrack> tracks = new ArrayList<>();
		List<String> incompleteArtists = new ArrayList<>();
		for (DiscoveryArtist artist : selectedArtists) {
			var response = lastFmClient.getArtistTopTracks(
					artist.name(), LASTFM_TRACK_CANDIDATE_LIMIT);
			List<Track> lastFmTracks = response != null
					&& response.toptracks() != null
					&& response.toptracks().track() != null
							? response.toptracks().track()
							: List.of();
			List<GenreTrackCandidate> candidates = new ArrayList<>();
			for (int index = 0; index < lastFmTracks.size(); index++) {
				Track track = lastFmTracks.get(index);
				if (track == null || track.name() == null || track.name().isBlank()) {
					continue;
				}
				candidates.add(new GenreTrackCandidate(
						artist.name(),
						track.name(),
						songNormalizer.normalizeTitle(track.name()),
						track.url(),
						parseLong(track.listeners()),
						parseLong(track.playcount()),
						0,
						index + 1));
			}
			int accepted = 0;
			for (GenreTrackCandidate candidate : candidates) {
				GenreTrackMatch match = spotifyTrackMatchingService.matchGenreTrack(
						accessToken, candidate);
				if (match.spotifyMatch().status() != MatchStatus.MATCHED
						|| match.spotifyMatch().uri() == null) {
					continue;
				}
				String trackId = spotifyIdFromUri(match.spotifyMatch().uri());
				if (trackId == null
						|| knownMusic.trackIds().contains(trackId)
						|| !selectedTrackIds.add(trackId)) {
					continue;
				}
				accepted++;
				tracks.add(new DiscoveryTrack(
						trackId,
						match.spotifyMatch().spotifyTrackName(),
						artist.name(),
						match.spotifyMatch().uri(),
						match.spotifyMatch().score(),
						knownMusic.artistIds().contains(artist.id()),
						artist.basedOnArtists()));
				if (accepted == TRACKS_PER_ARTIST) {
					break;
				}
			}
			if (accepted < TRACKS_PER_ARTIST) {
				incompleteArtists.add(artist.name() + " (" + accepted + "/" + TRACKS_PER_ARTIST + ")");
			}
			log.info("Discovery tracks selected for artist. artist={}, candidates={}, accepted={}, knownArtist={}",
					artist.name(), candidates.size(), accepted,
					knownMusic.artistIds().contains(artist.id()));
		}
		if (tracks.isEmpty()) {
			throw new ExternalApiException(
					"No new tracks with a reliable Spotify match were found");
		}
		String warning = "New music is estimated using your top tracks, recent plays, and Spotify library; "
				+ "Spotify does not expose your complete listening history.";
		if (!incompleteArtists.isEmpty()) {
			warning += " Fewer than " + TRACKS_PER_ARTIST
					+ " reliable new tracks were found for: " + String.join(", ", incompleteArtists) + ".";
		}
		TracksResult result = new TracksResult(
				similarResult.sourceArtists(),
				List.copyOf(selectedArtists),
				List.copyOf(tracks),
				warning);
		tracksCache.put(cacheKey, result);
		log.info("Discovery tracks ready. userId={}, artists={}, tracks={}, warning={}",
				userId, selectedArtists.size(), tracks.size(), warning != null);
		return result;
	}

	public GenerationResult generatePlaylist(
			String accessToken,
			List<SelectedArtistRequest> sourceArtists,
			List<SelectedArtistRequest> selectedSimilarArtists,
			List<SelectedTrackRequest> selectedTracks,
			String playlistName,
			String playlistDescription) {
		TracksResult fullSelection = findNewTracks(
				accessToken, sourceArtists, selectedSimilarArtists);
		List<DiscoveryTrack> tracks = filterSelectedTracks(
				fullSelection.tracks(), selectedTracks);
		String finalName = playlistName != null && !playlistName.isBlank()
				? playlistName.trim()
				: "Discover New Music";
		String description = playlistDescription != null && !playlistDescription.isBlank()
				? playlistDescription.trim()
				: "Playlist generated by playlistcreator";
		var playlist = spotifyApiClient.createPlaylist(
				accessToken, finalName, description, true);
		spotifyApiClient.updatePlaylistVisibility(accessToken, playlist.id(), true);
		List<String> uris = tracks.stream().map(DiscoveryTrack::uri).distinct().toList();
		for (int start = 0; start < uris.size(); start += 100) {
			spotifyApiClient.addItemsToPlaylist(
					accessToken,
					playlist.id(),
					uris.subList(start, Math.min(start + 100, uris.size())));
		}
		TracksResult selection = new TracksResult(
				fullSelection.sourceArtists(),
				fullSelection.similarArtists(),
				tracks,
				fullSelection.warning());
		log.info("Discovery playlist generated. playlistId={}, artists={}, tracks={}",
				playlist.id(), selection.similarArtists().size(), uris.size());
		return new GenerationResult(
				selection,
				playlist.id(),
				playlist.name(),
				playlist.external_urls() != null
						? playlist.external_urls().get("spotify")
						: null,
				uris.size());
	}

	private List<TopArtist> validateSelectedTopArtists(
			String accessToken,
			List<SelectedArtistRequest> selectedArtists) {
		if (selectedArtists == null || selectedArtists.isEmpty()) {
			throw new ExternalApiException("Select at least one artist");
		}
		Map<String, TopArtist> available = findTopArtists(accessToken).artists().stream()
				.collect(java.util.stream.Collectors.toMap(
						TopArtist::id,
						artist -> artist));
		List<TopArtist> selected = selectedArtists.stream()
				.filter(artist -> artist != null && artist.id() != null)
				.map(artist -> available.get(artist.id()))
				.filter(java.util.Objects::nonNull)
				.distinct()
				.toList();
		if (selected.isEmpty()) {
			throw new ExternalApiException("None of the selected artists is available");
		}
		return selected;
	}

	private List<DiscoveryArtist> filterSelectedSimilarArtists(
			List<DiscoveryArtist> artists,
			List<SelectedArtistRequest> selectedArtists) {
		if (selectedArtists == null || selectedArtists.isEmpty()) {
			throw new ExternalApiException("Select at least one similar artist");
		}
		Set<String> selectedIds = selectedArtists.stream()
				.filter(artist -> artist != null && artist.id() != null)
				.map(SelectedArtistRequest::id)
				.collect(java.util.stream.Collectors.toSet());
		List<DiscoveryArtist> selected = artists.stream()
				.filter(artist -> selectedIds.contains(artist.id()))
				.toList();
		if (selected.isEmpty()) {
			throw new ExternalApiException("None of the selected similar artists is available");
		}
		return selected;
	}

	private List<DiscoveryTrack> filterSelectedTracks(
			List<DiscoveryTrack> tracks,
			List<SelectedTrackRequest> selectedTracks) {
		if (selectedTracks == null || selectedTracks.isEmpty()) {
			throw new ExternalApiException("Select at least one track");
		}
		Set<String> selectedIds = selectedTracks.stream()
				.filter(track -> track != null && track.id() != null)
				.map(SelectedTrackRequest::id)
				.collect(java.util.stream.Collectors.toSet());
		List<DiscoveryTrack> selected = tracks.stream()
				.filter(track -> selectedIds.contains(track.id()))
				.toList();
		if (selected.isEmpty()) {
			throw new ExternalApiException("None of the selected tracks is available");
		}
		return selected;
	}

	private KnownMusicProfile loadKnownMusicProfile(String accessToken, String userId) {
		KnownMusicProfile cached = knownMusicProfileCache.getIfPresent(userId);
		if (cached != null) {
			return cached;
		}
		Set<String> artistIds = new LinkedHashSet<>();
		Set<String> trackIds = new LinkedHashSet<>();
		for (String timeRange : PROFILE_TIME_RANGES) {
			var artistsPage = spotifyApiClient.getCurrentUserTopArtists(
					accessToken, timeRange, PROFILE_LIMIT, 0);
			if (artistsPage != null && artistsPage.items() != null) {
				artistsPage.items().stream()
						.filter(artist -> artist != null && artist.id() != null)
						.map(SpotifyArtist::id)
						.forEach(artistIds::add);
			}
			var tracksPage = spotifyApiClient.getCurrentUserTopTracks(
					accessToken, timeRange, PROFILE_LIMIT, 0);
			if (tracksPage != null && tracksPage.items() != null) {
				tracksPage.items().forEach(track -> addKnownTrack(track, trackIds, artistIds));
			}
		}
		var recentlyPlayed = spotifyApiClient.getRecentlyPlayedTracks(
				accessToken, PROFILE_LIMIT);
		if (recentlyPlayed != null && recentlyPlayed.items() != null) {
			recentlyPlayed.items().stream()
					.filter(java.util.Objects::nonNull)
					.map(SpotifyApiClient.PlayHistory::track)
					.forEach(track -> addKnownTrack(track, trackIds, artistIds));
		}
		int offset = 0;
		while (true) {
			var page = spotifyApiClient.getSavedTracks(
					accessToken, SPOTIFY_PAGE_SIZE, offset);
			List<SpotifyApiClient.SavedTrack> items = page != null && page.items() != null
					? page.items()
					: List.of();
			items.stream()
					.filter(java.util.Objects::nonNull)
					.map(SpotifyApiClient.SavedTrack::track)
					.forEach(track -> addKnownTrack(track, trackIds, artistIds));
			offset += items.size();
			if (items.isEmpty()
					|| page.next() == null
					|| page.next().isBlank()) {
				break;
			}
		}
		KnownMusicProfile profile = new KnownMusicProfile(
				Set.copyOf(artistIds), Set.copyOf(trackIds));
		knownMusicProfileCache.put(userId, profile);
		log.info("Known Spotify music profile ready. userId={}, artists={}, tracks={}",
				userId, artistIds.size(), trackIds.size());
		return profile;
	}

	private void addKnownTrack(
			SpotifyApiClient.Track track,
			Set<String> trackIds,
			Set<String> artistIds) {
		if (track == null) {
			return;
		}
		if (track.id() != null) {
			trackIds.add(track.id());
		}
		if (track.artists() != null) {
			track.artists().stream()
					.filter(artist -> artist != null && artist.id() != null)
					.map(SpotifyApiClient.Artist::id)
					.forEach(artistIds::add);
		}
	}

	private SpotifyArtist resolveExactSpotifyArtist(String accessToken, String artistName) {
		var response = spotifyApiClient.searchArtists(accessToken, artistName);
		List<SpotifyArtist> artists = response != null
				&& response.artists() != null
				&& response.artists().items() != null
						? response.artists().items()
						: List.of();
		String normalizedName = songNormalizer.normalizeArtist(artistName);
		return artists.stream()
				.filter(artist -> artist != null
						&& artist.id() != null
						&& artist.name() != null
						&& normalizedName.equals(
								songNormalizer.normalizeArtist(artist.name())))
				.max(Comparator.comparingInt(
						artist -> artist.popularity() != null ? artist.popularity() : 0))
				.orElse(null);
	}

	private String currentUserId(String accessToken) {
		var user = spotifyApiClient.getCurrentUser(accessToken);
		if (user == null || user.id() == null || user.id().isBlank()) {
			throw new ExternalApiException("Unable to identify the Spotify user");
		}
		return user.id();
	}

	private String artistSelectionKey(List<? extends ArtistReference> artists) {
		return artists.stream()
				.map(ArtistReference::id)
				.sorted()
				.reduce("", (left, right) -> left + "," + right);
	}

	private String spotifyIdFromUri(String uri) {
		int separator = uri != null ? uri.lastIndexOf(':') : -1;
		return separator >= 0 && separator < uri.length() - 1
				? uri.substring(separator + 1)
				: null;
	}

	private long parseLong(String value) {
		try {
			return value == null ? 0 : Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			return 0;
		}
	}

	private interface ArtistReference {
		String id();
	}

	public record TopArtist(
			int rank,
			String id,
			String name,
			String spotifyUrl) implements ArtistReference {
	}

	public record DiscoveryArtist(
			String id,
			String name,
			String spotifyUrl,
			List<String> basedOnArtists) implements ArtistReference {
	}

	public record DiscoveryTrack(
			String id,
			String title,
			String artistName,
			String uri,
			int matchScore,
			boolean knownArtist,
			List<String> basedOnArtists) {
	}

	public record TopArtistsResult(List<TopArtist> artists, String warning) {
	}

	public record SimilarArtistsResult(
			List<TopArtist> sourceArtists,
			List<DiscoveryArtist> artists,
			String warning) {
	}

	public record TracksResult(
			List<TopArtist> sourceArtists,
			List<DiscoveryArtist> similarArtists,
			List<DiscoveryTrack> tracks,
			String warning) {
	}

	public record GenerationResult(
			TracksResult selection,
			String playlistId,
			String playlistName,
			String playlistUrl,
			int addedTracksCount) {
	}

	private record KnownMusicProfile(Set<String> artistIds, Set<String> trackIds) {
	}

	private record SimilarArtistResultBuilder(
			String id,
			String name,
			String spotifyUrl,
			Set<String> basedOnArtists) {

		SimilarArtistResultBuilder(String id, String name, String spotifyUrl) {
			this(id, name, spotifyUrl, new LinkedHashSet<>());
		}

		DiscoveryArtist build() {
			return new DiscoveryArtist(
					id, name, spotifyUrl, List.copyOf(basedOnArtists));
		}
	}

}
