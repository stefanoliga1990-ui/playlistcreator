package com.application.playlistcreator.controller;

import com.application.playlistcreator.dto.GenerateTopTracksPlaylistRequest;
import com.application.playlistcreator.dto.GenerateTopTracksPlaylistResponse;
import com.application.playlistcreator.dto.PreviewTopTracksResponse;
import com.application.playlistcreator.service.SpotifyOAuthService;
import com.application.playlistcreator.service.TopTracksPlaylistService;
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
@RequestMapping("/api/top-tracks-playlists")
public class TopTracksPlaylistController {

	private static final Logger log = LoggerFactory.getLogger(TopTracksPlaylistController.class);

	private final TopTracksPlaylistService topTracksPlaylistService;
	private final SpotifyOAuthService spotifyOAuthService;

	public TopTracksPlaylistController(
			TopTracksPlaylistService topTracksPlaylistService,
			SpotifyOAuthService spotifyOAuthService) {
		this.topTracksPlaylistService = topTracksPlaylistService;
		this.spotifyOAuthService = spotifyOAuthService;
	}

	@GetMapping("/preview")
	public PreviewTopTracksResponse preview(
			@RequestParam Integer trackLimit,
			@RequestParam Integer months,
			HttpSession session) {
		log.info("Spotify top tracks preview requested. trackLimit={}, months={}, sessionId={}",
				trackLimit, months, session.getId());
		var token = spotifyOAuthService.getValidToken(session);
		var response = PreviewTopTracksResponse.from(
				topTracksPlaylistService.findTopTracks(token.accessToken(), trackLimit, months));
		log.info("Spotify top tracks preview completed. requestedTracks={}, availableTracks={}, months={}, warning={}",
				response.requestedTrackCount(),
				response.availableTrackCount(),
				response.months(),
				response.warning() != null);
		return response;
	}

	@PostMapping("/generate")
	public GenerateTopTracksPlaylistResponse generate(
			@RequestBody GenerateTopTracksPlaylistRequest request,
			HttpSession session) {
		log.info("Spotify top tracks playlist generation requested. trackLimit={}, months={}, sessionId={}",
				request != null ? request.trackLimit() : null,
				request != null ? request.months() : null,
				session.getId());
		var token = spotifyOAuthService.getValidToken(session);
		var response = GenerateTopTracksPlaylistResponse.from(
				topTracksPlaylistService.generatePlaylist(
						token.accessToken(),
						request != null ? request.trackLimit() : null,
						request != null ? request.months() : null,
						request != null ? request.selectedTracks() : null,
						request != null ? request.playlistName() : null,
						request != null ? request.playlistDescription() : null));
		log.info("Spotify top tracks playlist generation completed. playlistId={}, addedTracks={}, months={}",
				response.playlist().id(), response.addedTracksCount(), response.months());
		return response;
	}
}
