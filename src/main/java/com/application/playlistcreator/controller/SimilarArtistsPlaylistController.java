package com.application.playlistcreator.controller;

import com.application.playlistcreator.dto.GenerateSimilarArtistsPlaylistRequest;
import com.application.playlistcreator.dto.GenerateSimilarArtistsPlaylistResponse;
import com.application.playlistcreator.dto.PreviewSimilarArtistTracksRequest;
import com.application.playlistcreator.dto.PreviewSimilarArtistTracksResponse;
import com.application.playlistcreator.dto.PreviewSimilarArtistsResponse;
import com.application.playlistcreator.service.SimilarArtistsPlaylistService;
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
@RequestMapping("/api/similar-artists-playlists")
public class SimilarArtistsPlaylistController {

	private static final Logger log = LoggerFactory.getLogger(SimilarArtistsPlaylistController.class);

	private final SimilarArtistsPlaylistService similarArtistsPlaylistService;
	private final SpotifyOAuthService spotifyOAuthService;

	public SimilarArtistsPlaylistController(
			SimilarArtistsPlaylistService similarArtistsPlaylistService,
			SpotifyOAuthService spotifyOAuthService) {
		this.similarArtistsPlaylistService = similarArtistsPlaylistService;
		this.spotifyOAuthService = spotifyOAuthService;
	}

	@GetMapping("/artists")
	public PreviewSimilarArtistsResponse previewArtists(@RequestParam String artistName) {
		log.info("Similar artists preview requested. artistName={}", artistName);
		var response = PreviewSimilarArtistsResponse.from(
				similarArtistsPlaylistService.findSimilarArtists(artistName));
		log.info("Similar artists preview completed. sourceArtist={}, checkedCandidates={}, artists={}, warning={}",
				response.sourceArtistName(), response.checkedCandidateCount(),
				response.artistCount(), response.warning() != null);
		return response;
	}

	@PostMapping("/tracks")
	public PreviewSimilarArtistTracksResponse previewTracks(
			@RequestBody PreviewSimilarArtistTracksRequest request) {
		log.info("Similar artists tracks preview requested. sourceArtist={}, selectedArtists={}, tracksPerArtist={}",
				request != null ? request.sourceArtistName() : null,
				request != null && request.selectedArtistNames() != null
						? request.selectedArtistNames().size() : 0,
				request != null ? request.tracksPerArtist() : null);
		var response = PreviewSimilarArtistTracksResponse.from(
				similarArtistsPlaylistService.findTopTracks(
						request != null ? request.sourceArtistName() : null,
						request != null ? request.selectedArtistNames() : null,
						request != null ? request.tracksPerArtist() : null));
		log.info("Similar artists tracks preview completed. sourceArtist={}, artists={}, tracks={}, warning={}",
				response.sourceArtistName(), response.artistCount(),
				response.trackCount(), response.warning() != null);
		return response;
	}

	@PostMapping("/generate")
	public GenerateSimilarArtistsPlaylistResponse generate(
			@RequestBody GenerateSimilarArtistsPlaylistRequest request,
			HttpSession session) {
		log.info("Similar artists playlist generation requested. sourceArtist={}, selectedArtists={}, tracksPerArtist={}, sessionId={}",
				request != null ? request.sourceArtistName() : null,
				request != null && request.selectedArtistNames() != null
						? request.selectedArtistNames().size() : 0,
				request != null ? request.tracksPerArtist() : null,
				session.getId());
		var token = spotifyOAuthService.getValidToken(session);
		var response = GenerateSimilarArtistsPlaylistResponse.from(
				similarArtistsPlaylistService.generatePlaylist(
						token.accessToken(),
						request != null ? request.sourceArtistName() : null,
						request != null ? request.selectedArtistNames() : null,
						request != null ? request.tracksPerArtist() : null,
						request != null ? request.selectedTracks() : null,
						request != null ? request.playlistName() : null,
						request != null ? request.playlistDescription() : null));
		log.info("Similar artists playlist generation completed. playlistId={}, addedTracks={}, uncertainTracks={}, notFoundTracks={}",
				response.playlist().id(), response.addedTracksCount(),
				response.uncertainTracksCount(), response.notFoundTracksCount());
		return response;
	}
}
