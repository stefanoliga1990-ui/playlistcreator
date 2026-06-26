package com.application.playlistcreator.exception;

public class SetlistFmArtistNotFoundException extends ExternalApiException {

	public SetlistFmArtistNotFoundException() {
		super("Artist not found on setlist.fm");
	}
}
