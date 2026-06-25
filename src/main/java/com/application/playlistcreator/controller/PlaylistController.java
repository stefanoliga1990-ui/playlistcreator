package com.application.playlistcreator.controller;

import com.application.playlistcreator.dto.GeneratePlaylistRequest;
import com.application.playlistcreator.dto.GeneratePlaylistResponse;
import com.application.playlistcreator.dto.PreviewSetlistResponse;
import com.application.playlistcreator.exception.ExternalApiException;
import com.application.playlistcreator.model.SpotifyUserToken;
import com.application.playlistcreator.service.PlaylistGenerationService;
import com.application.playlistcreator.service.SetlistService;
import com.application.playlistcreator.service.SpotifyOAuthService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

	private static final Logger log = LoggerFactory.getLogger(PlaylistController.class);

	private final SpotifyOAuthService spotifyOAuthService;
	private final PlaylistGenerationService playlistGenerationService;
	private final SetlistService setlistService;

	public PlaylistController(SpotifyOAuthService spotifyOAuthService,
			PlaylistGenerationService playlistGenerationService,
			SetlistService setlistService) {
		this.spotifyOAuthService = spotifyOAuthService;
		this.playlistGenerationService = playlistGenerationService;
		this.setlistService = setlistService;
	}

	@GetMapping("/preview")
	public PreviewSetlistResponse preview(@RequestParam String artistName) {
		if (artistName == null || artistName.isBlank()) {
			throw new ExternalApiException("artistName is required");
		}
		log.info("Preview requested. artistName={}", artistName);
		PreviewSetlistResponse response = PreviewSetlistResponse.from(setlistService.selectProbableSongs(artistName));
		log.info("Preview completed. artistName={}, resolvedArtist={}, setlists={}, recentSongs={}",
				artistName, response.artist().name(), response.sourceSetlists().size(), response.recentSongsCount());
		return response;
	}

	@PostMapping("/generate")
	public GeneratePlaylistResponse generate(@RequestBody GeneratePlaylistRequest request, HttpSession session) {
		if (request == null || request.artistName() == null || request.artistName().isBlank()) {
			throw new ExternalApiException("artistName is required");
		}
		log.info("Playlist generation requested. artistName={}, playlistName={}, sessionId={}",
				request.artistName(), request.playlistName(), session.getId());
		SpotifyUserToken token = spotifyOAuthService.getValidToken(session);
		GeneratePlaylistResponse response = playlistGenerationService.generatePlaylist(
				token.accessToken(),
				request.artistName(),
				request.playlistName(),
				request.playlistDescription(),
				request.selectedTracks());
		log.info("Playlist generation completed. artistName={}, playlistId={}, addedTracks={}, uncertainTracks={}, notFoundTracks={}",
				request.artistName(), response.playlist().id(), response.addedTracksCount(),
				response.uncertainTracksCount(), response.notFoundTracksCount());
		return response;
	}
}
