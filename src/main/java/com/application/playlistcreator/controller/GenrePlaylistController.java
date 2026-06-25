package com.application.playlistcreator.controller;

import com.application.playlistcreator.dto.GenerateGenrePlaylistRequest;
import com.application.playlistcreator.dto.GenerateGenrePlaylistResponse;
import com.application.playlistcreator.dto.PreviewGenreArtistsResponse;
import com.application.playlistcreator.dto.PreviewGenreTracksRequest;
import com.application.playlistcreator.dto.PreviewGenreTracksResponse;
import com.application.playlistcreator.model.SpotifyUserToken;
import com.application.playlistcreator.service.GenrePlaylistService;
import com.application.playlistcreator.service.SpotifyOAuthService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/genre-playlists")
public class GenrePlaylistController {

	private static final Logger log = LoggerFactory.getLogger(GenrePlaylistController.class);

	private final GenrePlaylistService genrePlaylistService;
	private final SpotifyOAuthService spotifyOAuthService;

	public GenrePlaylistController(GenrePlaylistService genrePlaylistService, SpotifyOAuthService spotifyOAuthService) {
		this.genrePlaylistService = genrePlaylistService;
		this.spotifyOAuthService = spotifyOAuthService;
	}

	@GetMapping("/artists")
	public PreviewGenreArtistsResponse previewArtists(@RequestParam String genre,
			@RequestParam(required = false) Integer artistLimit) {
		log.info("Genre artists preview requested. genre={}, artistLimit={}", genre, artistLimit);
		var result = genrePlaylistService.findTopArtists(genre, artistLimit);
		PreviewGenreArtistsResponse response = PreviewGenreArtistsResponse.from(result);
		log.info("Genre artists preview completed. genre={}, requestedArtists={}, checkedCandidates={}, artists={}, warning={}",
				response.genre(), response.requestedArtistCount(), response.checkedCandidateCount(),
				response.artistCount(), response.warning() != null);
		return response;
	}

	@PostMapping("/tracks")
	public PreviewGenreTracksResponse previewTracks(@RequestBody PreviewGenreTracksRequest request) {
		log.info("Genre tracks preview requested. genre={}, artistLimit={}, tracksPerArtist={}",
				request != null ? request.genre() : null,
				request != null ? request.artistLimit() : null,
				request != null ? request.tracksPerArtist() : null);
		PreviewGenreTracksResponse response = PreviewGenreTracksResponse.from(
				genrePlaylistService.findTopTracks(
						request != null ? request.genre() : null,
						request != null ? request.artistLimit() : null,
						request != null ? request.selectedArtistNames() : null,
						request != null ? request.tracksPerArtist() : null));
		log.info("Genre tracks preview completed. genre={}, artists={}, tracks={}",
				response.genre(), response.artistCount(), response.trackCount());
		return response;
	}

	@PostMapping("/generate")
	public GenerateGenrePlaylistResponse generate(@RequestBody GenerateGenrePlaylistRequest request, HttpSession session) {
		log.info("Genre playlist generation requested. genre={}, playlistName={}, sessionId={}",
				request != null ? request.genre() : null,
				request != null ? request.playlistName() : null,
				session.getId());
		SpotifyUserToken token = spotifyOAuthService.getValidToken(session);
		GenerateGenrePlaylistResponse response = GenerateGenrePlaylistResponse.from(
				genrePlaylistService.generatePlaylist(
						token.accessToken(),
						request != null ? request.genre() : null,
						request != null ? request.artistLimit() : null,
						request != null ? request.selectedArtistNames() : null,
						request != null ? request.tracksPerArtist() : null,
						request != null ? request.selectedTracks() : null,
						request != null ? request.playlistName() : null,
						request != null ? request.playlistDescription() : null));
		log.info("Genre playlist generation completed. genre={}, playlistId={}, addedTracks={}, uncertainTracks={}, notFoundTracks={}",
				response.genre(), response.playlist().id(), response.addedTracksCount(),
				response.uncertainTracksCount(), response.notFoundTracksCount());
		return response;
	}
}
