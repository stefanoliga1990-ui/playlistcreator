package com.application.playlistcreator.controller;

import com.application.playlistcreator.dto.GenerateDiscoverPlaylistRequest;
import com.application.playlistcreator.dto.GenerateDiscoverPlaylistResponse;
import com.application.playlistcreator.dto.PreviewDiscoverSimilarArtistsRequest;
import com.application.playlistcreator.dto.PreviewDiscoverSimilarArtistsResponse;
import com.application.playlistcreator.dto.PreviewDiscoverTopArtistsResponse;
import com.application.playlistcreator.dto.PreviewDiscoverTracksRequest;
import com.application.playlistcreator.dto.PreviewDiscoverTracksResponse;
import com.application.playlistcreator.service.DiscoverNewMusicService;
import com.application.playlistcreator.service.SpotifyOAuthService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discover-new-music")
public class DiscoverNewMusicController {

	private static final Logger log = LoggerFactory.getLogger(DiscoverNewMusicController.class);

	private final DiscoverNewMusicService discoverNewMusicService;
	private final SpotifyOAuthService spotifyOAuthService;

	public DiscoverNewMusicController(
			DiscoverNewMusicService discoverNewMusicService,
			SpotifyOAuthService spotifyOAuthService) {
		this.discoverNewMusicService = discoverNewMusicService;
		this.spotifyOAuthService = spotifyOAuthService;
	}

	@GetMapping("/top-artists")
	public PreviewDiscoverTopArtistsResponse topArtists(HttpSession session) {
		log.info("Discovery top artists requested. sessionId={}", session.getId());
		var token = spotifyOAuthService.getValidToken(session);
		var response = PreviewDiscoverTopArtistsResponse.from(
				discoverNewMusicService.findTopArtists(token.accessToken()));
		log.info("Discovery top artists completed. artists={}, warning={}",
				response.artistCount(), response.warning() != null);
		return response;
	}

	@PostMapping("/similar-artists")
	public PreviewDiscoverSimilarArtistsResponse similarArtists(
			@RequestBody PreviewDiscoverSimilarArtistsRequest request,
			HttpSession session) {
		log.info("Discovery similar artists requested. selectedArtists={}, sessionId={}",
				request != null && request.selectedArtists() != null
						? request.selectedArtists().size()
						: 0,
				session.getId());
		var token = spotifyOAuthService.getValidToken(session);
		var response = PreviewDiscoverSimilarArtistsResponse.from(
				discoverNewMusicService.findSimilarArtists(
						token.accessToken(),
						request != null ? request.selectedArtists() : null));
		log.info("Discovery similar artists completed. artists={}, warning={}",
				response.artistCount(), response.warning() != null);
		return response;
	}

	@PostMapping("/tracks")
	public PreviewDiscoverTracksResponse tracks(
			@RequestBody PreviewDiscoverTracksRequest request,
			HttpSession session) {
		log.info("Discovery tracks requested. sources={}, similarArtists={}, sessionId={}",
				request != null && request.sourceArtists() != null
						? request.sourceArtists().size()
						: 0,
				request != null && request.selectedSimilarArtists() != null
						? request.selectedSimilarArtists().size()
						: 0,
				session.getId());
		var token = spotifyOAuthService.getValidToken(session);
		var response = PreviewDiscoverTracksResponse.from(
				discoverNewMusicService.findNewTracks(
						token.accessToken(),
						request != null ? request.sourceArtists() : null,
						request != null ? request.selectedSimilarArtists() : null));
		log.info("Discovery tracks completed. artists={}, tracks={}, warning={}",
				response.artistCount(), response.trackCount(), response.warning() != null);
		return response;
	}

	@PostMapping("/generate")
	public GenerateDiscoverPlaylistResponse generate(
			@RequestBody GenerateDiscoverPlaylistRequest request,
			HttpSession session) {
		log.info("Discovery playlist generation requested. tracks={}, sessionId={}",
				request != null && request.selectedTracks() != null
						? request.selectedTracks().size()
						: 0,
				session.getId());
		var token = spotifyOAuthService.getValidToken(session);
		var response = GenerateDiscoverPlaylistResponse.from(
				discoverNewMusicService.generatePlaylist(
						token.accessToken(),
						request != null ? request.sourceArtists() : null,
						request != null ? request.selectedSimilarArtists() : null,
						request != null ? request.selectedTracks() : null,
						request != null ? request.playlistName() : null,
						request != null ? request.playlistDescription() : null));
		log.info("Discovery playlist generation completed. playlistId={}, tracks={}",
				response.playlist().id(), response.addedTracksCount());
		return response;
	}
}
