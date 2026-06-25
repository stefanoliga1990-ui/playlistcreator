package com.application.playlistcreator.exception;

public class SetlistFmArtistNotFoundException extends ExternalApiException {

	public SetlistFmArtistNotFoundException() {
		super("Artista non presente su setlist.fm");
	}
}
