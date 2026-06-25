package com.application.playlistcreator.controller;

import com.application.playlistcreator.dto.GenerateDiscographyPlaylistRequest;
import com.application.playlistcreator.dto.GenerateDiscographyPlaylistResponse;
import com.application.playlistcreator.dto.PreviewDiscographyAlbumsResponse;
import com.application.playlistcreator.dto.PreviewDiscographyTracksRequest;
import com.application.playlistcreator.dto.PreviewDiscographyTracksResponse;
import com.application.playlistcreator.service.DiscographyPlaylistService;
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
@RequestMapping("/api/discography-playlists")
public class DiscographyPlaylistController {

	private static final Logger log = LoggerFactory.getLogger(DiscographyPlaylistController.class);

	private final DiscographyPlaylistService discographyPlaylistService;
	private final SpotifyOAuthService spotifyOAuthService;

	public DiscographyPlaylistController(
			DiscographyPlaylistService discographyPlaylistService,
			SpotifyOAuthService spotifyOAuthService) {
		this.discographyPlaylistService = discographyPlaylistService;
		this.spotifyOAuthService = spotifyOAuthService;
	}

	@GetMapping("/albums")
	public PreviewDiscographyAlbumsResponse previewAlbums(
			@RequestParam String artistName,
			HttpSession session) {
		log.info("Discography albums preview requested. artistName={}, sessionId={}", artistName, session.getId());
		var token = spotifyOAuthService.getValidToken(session);
		var response = PreviewDiscographyAlbumsResponse.from(
				discographyPlaylistService.findAlbums(token.accessToken(), artistName));
		log.info("Discography albums preview completed. artist={}, albums={}, filteredAlbums={}",
				response.artist().name(), response.albumCount(), response.filteredAlbumCount());
		return response;
	}

	@PostMapping("/tracks")
	public PreviewDiscographyTracksResponse previewTracks(
			@RequestBody PreviewDiscographyTracksRequest request,
			HttpSession session) {
		log.info("Discography tracks preview requested. artistName={}, albums={}, tracksPerAlbum={}, sessionId={}",
				request != null ? request.artistName() : null,
				request != null && request.includedAlbumIds() != null ? request.includedAlbumIds().size() : 0,
				request != null ? request.tracksPerAlbum() : null,
				session.getId());
		var token = spotifyOAuthService.getValidToken(session);
		var response = PreviewDiscographyTracksResponse.from(
				discographyPlaylistService.selectTracks(
						token.accessToken(),
						request != null ? request.artistId() : null,
						request != null ? request.artistName() : null,
						request != null ? request.includedAlbumIds() : null,
						request != null ? request.tracksPerAlbum() : null));
		log.info("Discography tracks preview completed. artist={}, albums={}, tracks={}",
				response.artistName(), response.albumCount(), response.trackCount());
		return response;
	}

	@PostMapping("/generate")
	public GenerateDiscographyPlaylistResponse generate(
			@RequestBody GenerateDiscographyPlaylistRequest request,
			HttpSession session) {
		log.info("Discography playlist generation requested. artistName={}, albums={}, tracksPerAlbum={}, sessionId={}",
				request != null ? request.artistName() : null,
				request != null && request.includedAlbumIds() != null ? request.includedAlbumIds().size() : 0,
				request != null ? request.tracksPerAlbum() : null,
				session.getId());
		var token = spotifyOAuthService.getValidToken(session);
		var response = GenerateDiscographyPlaylistResponse.from(
				discographyPlaylistService.generatePlaylist(
						token.accessToken(),
						request != null ? request.artistId() : null,
						request != null ? request.artistName() : null,
						request != null ? request.includedAlbumIds() : null,
						request != null ? request.tracksPerAlbum() : null,
						request != null ? request.selectedTracks() : null,
						request != null ? request.playlistName() : null,
						request != null ? request.playlistDescription() : null));
		log.info("Discography playlist generation completed. artist={}, playlistId={}, addedTracks={}",
				response.artistName(), response.playlist().id(), response.addedTracksCount());
		return response;
	}
}
