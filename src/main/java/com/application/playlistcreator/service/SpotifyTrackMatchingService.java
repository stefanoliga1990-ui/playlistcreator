package com.application.playlistcreator.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.application.playlistcreator.client.spotify.SpotifyApiClient;
import com.application.playlistcreator.client.spotify.SpotifyApiClient.Track;
import com.application.playlistcreator.model.CandidateSong;
import com.application.playlistcreator.model.GenreTrackCandidate;
import com.application.playlistcreator.model.GenreTrackMatch;
import com.application.playlistcreator.model.SpotifyTrackMatch;
import com.application.playlistcreator.model.SpotifyTrackMatch.MatchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SpotifyTrackMatchingService {

	private static final Logger log = LoggerFactory.getLogger(SpotifyTrackMatchingService.class);
	private static final int MIN_SAFE_EXTENSION_TITLE_LENGTH = 5;
	private static final int MAX_SAFE_EXTENSION_EXTRA_TOKENS = 3;

	private final SpotifyApiClient spotifyApiClient;
	private final SongNormalizer songNormalizer;

	public SpotifyTrackMatchingService(SpotifyApiClient spotifyApiClient, SongNormalizer songNormalizer) {
		this.spotifyApiClient = spotifyApiClient;
		this.songNormalizer = songNormalizer;
	}

	public List<SpotifyTrackMatch> matchTracks(String accessToken, String artistName, List<CandidateSong> songs) {
		log.info("Starting Spotify track matching. artistName={}, songs={}", artistName, songs.size());
		List<SpotifyTrackMatch> matches = new ArrayList<>();
		for (CandidateSong song : songs) {
			matches.add(matchTrack(accessToken, artistName, song, true));
		}
		long matched = matches.stream().filter(match -> match.status() == MatchStatus.MATCHED).count();
		long uncertain = matches.stream().filter(match -> match.status() == MatchStatus.UNCERTAIN).count();
		long notFound = matches.stream().filter(match -> match.status() == MatchStatus.NOT_FOUND).count();
		log.info("Spotify track matching completed. artistName={}, matched={}, uncertain={}, notFound={}",
				artistName, matched, uncertain, notFound);
		return matches;
	}

	public List<GenreTrackMatch> matchGenreTracks(String accessToken, List<GenreTrackCandidate> tracks) {
		log.info("Starting Spotify genre track matching. tracks={}", tracks.size());
		List<GenreTrackMatch> matches = new ArrayList<>();
		for (GenreTrackCandidate track : tracks) {
			CandidateSong song = new CandidateSong(
					track.title(),
					track.normalizedTitle(),
					track.trackRank(),
					track.trackRank(),
					false,
					null);
			matches.add(new GenreTrackMatch(track, matchTrack(accessToken, track.artistName(), song, false)));
		}
		long matched = matches.stream().filter(match -> match.spotifyMatch().status() == MatchStatus.MATCHED).count();
		long uncertain = matches.stream().filter(match -> match.spotifyMatch().status() == MatchStatus.UNCERTAIN).count();
		long notFound = matches.stream().filter(match -> match.spotifyMatch().status() == MatchStatus.NOT_FOUND).count();
		log.info("Spotify genre track matching completed. matched={}, uncertain={}, notFound={}",
				matched, uncertain, notFound);
		return matches;
	}

	public GenreTrackMatch matchGenreTrack(String accessToken, GenreTrackCandidate track) {
		CandidateSong song = new CandidateSong(
				track.title(),
				track.normalizedTitle(),
				track.trackRank(),
				track.trackRank(),
				false,
				null);
		return new GenreTrackMatch(
				track,
				matchTrack(accessToken, track.artistName(), song, false));
	}

	private SpotifyTrackMatch matchTrack(
			String accessToken,
			String artistName,
			CandidateSong song,
			boolean allowSetlistTitleFallback) {
		List<String> queries = buildQueries(artistName, song);
		SpotifyTrackMatch bestMatch = null;
		Map<String, Track> candidates = new LinkedHashMap<>();
		for (String query : queries) {
			log.info("Searching Spotify track. song={}, query={}", song.title(), query);
			var response = spotifyApiClient.searchTracks(accessToken, query);
			List<Track> tracks = response != null && response.tracks() != null && response.tracks().items() != null
					? response.tracks().items()
					: List.of();
			log.info("Spotify search returned candidates. song={}, query={}, candidates={}",
					song.title(), query, tracks.size());
			tracks.forEach(track -> candidates.putIfAbsent(candidateKey(track), track));
			SpotifyTrackMatch queryBestMatch = tracks.stream()
					.map(track -> scoreTrack(song, artistName, track, allowSetlistTitleFallback, false))
					.max(Comparator.comparingInt(SpotifyTrackMatch::score))
					.orElse(null);
			if (queryBestMatch != null && (bestMatch == null || queryBestMatch.score() > bestMatch.score())) {
				bestMatch = queryBestMatch;
			}
			if (bestMatch != null && bestMatch.score() >= 85) {
				break;
			}
		}
		if (allowSetlistTitleFallback && (bestMatch == null || bestMatch.status() != MatchStatus.MATCHED)) {
			SpotifyTrackMatch fallbackMatch = findSafeSetlistFallback(song, artistName, candidates.values().stream().toList());
			if (fallbackMatch != null && (bestMatch == null || fallbackMatch.score() > bestMatch.score())) {
				bestMatch = fallbackMatch;
				log.info("Safe setlist title fallback accepted. song={}, spotifyTrack={}, artists={}, score={}",
						song.title(), bestMatch.spotifyTrackName(), bestMatch.spotifyArtists(), bestMatch.score());
			}
		}
		if (bestMatch == null) {
			log.warn("No Spotify track candidates found. song={}", song.title());
			return new SpotifyTrackMatch(song, null, List.of(), null, 0, MatchStatus.NOT_FOUND);
		}
		log.info("Best Spotify match selected. song={}, spotifyTrack={}, artists={}, score={}, status={}",
				song.title(), bestMatch.spotifyTrackName(), bestMatch.spotifyArtists(),
				bestMatch.score(), bestMatch.status());
		return bestMatch;
	}

	private SpotifyTrackMatch findSafeSetlistFallback(
			CandidateSong song,
			String artistName,
			List<Track> tracks) {
		boolean reliableExactCandidateExists = tracks.stream()
				.anyMatch(track -> isReliableExactSetlistCandidate(song, artistName, track));
		if (reliableExactCandidateExists) {
			return null;
		}
		return tracks.stream()
				.filter(track -> isSafeSetlistTitleExpansion(song, artistName, track))
				.map(track -> scoreTrack(song, artistName, track, true, true))
				.max(Comparator.comparingInt(SpotifyTrackMatch::score))
				.filter(match -> match.status() == MatchStatus.MATCHED)
				.orElse(null);
	}

	private List<String> buildQueries(String artistName, CandidateSong song) {
		List<String> queries = new ArrayList<>();
		queries.add("track:\"" + escapeQuery(song.title()) + "\" artist:\"" + escapeQuery(artistName) + "\"");
		queries.add("\"" + escapeQuery(song.title()) + "\" \"" + escapeQuery(artistName) + "\"");
		if (song.cover() && song.coverArtist() != null && !song.coverArtist().isBlank()) {
			queries.add("track:\"" + escapeQuery(song.title()) + "\" artist:\"" + escapeQuery(song.coverArtist()) + "\"");
		}
		return queries;
	}

	private String escapeQuery(String value) {
		return value == null ? "" : value.replace("\"", "");
	}

	private SpotifyTrackMatch scoreTrack(
			CandidateSong song,
			String artistName,
			Track track,
			boolean setlistMatching,
			boolean safeTitleExpansion) {
		String requestedTitle = song.normalizedTitle();
		String spotifyTitle = setlistMatching
				? songNormalizer.normalizeTitleWithoutFeaturedArtist(track.name())
				: songNormalizer.normalizeTitle(track.name());
		String requestedArtist = songNormalizer.normalizeArtist(artistName);
		List<String> artistNames = track.artists() != null
				? track.artists().stream().map(SpotifyApiClient.Artist::name).toList()
				: List.of();
		List<String> normalizedArtists = artistNames.stream()
				.map(songNormalizer::normalizeArtist)
				.toList();

		int score = 0;
		if (requestedTitle.equals(spotifyTitle)) {
			score += 50;
		}
		else if (safeTitleExpansion) {
			score += 45;
		}
		else if (spotifyTitle.contains(requestedTitle) || requestedTitle.contains(spotifyTitle)) {
			score += 35;
		}
		else {
			score += tokenOverlapScore(requestedTitle, spotifyTitle, 30);
		}

		if (normalizedArtists.stream().anyMatch(requestedArtist::equals)) {
			score += 30;
			if (safeTitleExpansion && !normalizedArtists.isEmpty()
					&& requestedArtist.equals(normalizedArtists.get(0))) {
				score += 5;
			}
		}
		else if (normalizedArtists.stream().anyMatch(artist -> artist.contains(requestedArtist) || requestedArtist.contains(artist))) {
			score += 18;
		}
		else if (song.cover() && song.coverArtist() != null) {
			String coverArtist = songNormalizer.normalizeArtist(song.coverArtist());
			if (normalizedArtists.stream().anyMatch(coverArtist::equals)) {
				score += 12;
			}
		}

		if (Boolean.FALSE.equals(track.is_playable())) {
			score -= 100;
		}
		if (songNormalizer.hasPenaltyTerm(track.name())
				|| (track.album() != null && songNormalizer.hasPenaltyTerm(track.album().name()))) {
			score -= 12;
		}
		if (track.album() != null && ("album".equals(track.album().album_type()) || "single".equals(track.album().album_type()))) {
			score += 5;
		}
		if (track.popularity() != null) {
			score += Math.min(10, track.popularity() / 10);
		}
		score = Math.max(0, Math.min(100, score));
		MatchStatus status = score >= 85 ? MatchStatus.MATCHED : score >= 65 ? MatchStatus.UNCERTAIN : MatchStatus.NOT_FOUND;
		return new SpotifyTrackMatch(song, track.name(), artistNames, track.uri(), score, status);
	}

	private boolean isReliableExactSetlistCandidate(CandidateSong song, String artistName, Track track) {
		if (Boolean.FALSE.equals(track.is_playable())) {
			return false;
		}
		String requestedArtist = songNormalizer.normalizeArtist(artistName);
		boolean artistMatches = track.artists() != null && track.artists().stream()
				.map(SpotifyApiClient.Artist::name)
				.map(songNormalizer::normalizeArtist)
				.anyMatch(requestedArtist::equals);
		return artistMatches && song.normalizedTitle().equals(
				songNormalizer.normalizeTitleWithoutFeaturedArtist(track.name()));
	}

	private boolean isSafeSetlistTitleExpansion(CandidateSong song, String artistName, Track track) {
		String requestedTitle = song.normalizedTitle();
		String spotifyTitle = songNormalizer.normalizeTitleWithoutFeaturedArtist(track.name());
		if (requestedTitle.length() < MIN_SAFE_EXTENSION_TITLE_LENGTH
				|| !spotifyTitle.startsWith(requestedTitle + " ")
				|| splitTokens(spotifyTitle).size() - splitTokens(requestedTitle).size()
						> MAX_SAFE_EXTENSION_EXTRA_TOKENS
				|| !hasExplicitTitleExtension(song.title(), track.name())
				|| Boolean.FALSE.equals(track.is_playable())
				|| songNormalizer.hasPenaltyTerm(track.name())
				|| (track.album() != null && songNormalizer.hasPenaltyTerm(track.album().name()))) {
			return false;
		}
		String requestedArtist = songNormalizer.normalizeArtist(artistName);
		boolean primaryArtistMatches = track.artists() != null
				&& !track.artists().isEmpty()
				&& requestedArtist.equals(songNormalizer.normalizeArtist(track.artists().get(0).name()));
		boolean regularRelease = track.album() != null
				&& ("album".equals(track.album().album_type()) || "single".equals(track.album().album_type()));
		return primaryArtistMatches && regularRelease;
	}

	private boolean hasExplicitTitleExtension(String requestedTitle, String spotifyTitle) {
		if (requestedTitle == null || requestedTitle.isBlank() || spotifyTitle == null) {
			return false;
		}
		Pattern pattern = Pattern.compile(
				"(?iu)^\\s*" + Pattern.quote(requestedTitle.trim()) + "\\s*[-\\u2013\\u2014:]\\s*\\S+");
		return pattern.matcher(spotifyTitle).find();
	}

	private String candidateKey(Track track) {
		if (track.uri() != null && !track.uri().isBlank()) {
			return track.uri();
		}
		String artists = track.artists() != null
				? track.artists().stream().map(SpotifyApiClient.Artist::name).collect(Collectors.joining(","))
				: "";
		return track.name() + ":" + artists;
	}

	private int tokenOverlapScore(String left, String right, int maxScore) {
		Set<String> leftTokens = splitTokens(left);
		Set<String> rightTokens = splitTokens(right);
		if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
			return 0;
		}
		long intersection = leftTokens.stream().filter(rightTokens::contains).count();
		long union = leftTokens.stream().collect(Collectors.toSet()).size();
		union += rightTokens.stream().filter(token -> !leftTokens.contains(token)).count();
		return union == 0 ? 0 : (int) ((intersection * maxScore) / union);
	}

	private Set<String> splitTokens(String value) {
		if (value == null || value.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(value.split("\\s+"))
				.filter(token -> !token.isBlank())
				.collect(Collectors.toSet());
	}
}
