# Playlist Creator

Applicazione Spring Boot per creare playlist Spotify usando diverse sorgenti e strategie:

- scalette recenti recuperate da setlist.fm;
- artisti e brani associati a un genere tramite Last.fm;
- discografia essenziale di un artista;
- brani preferiti dell'utente Spotify;
- artisti simili e relative canzoni principali.

## Requisiti

- Java 17
- Maven
- account Spotify
- applicazione Spotify in Development Mode
- API key setlist.fm
- API key Last.fm

## Configurazione locale

Le credenziali non sono versionate. L'applicazione importa automaticamente, quando presente, il file:

```text
application-local.properties
```

Creare il file nella directory principale del progetto con questa struttura:

```properties
playlistcreator.setlistfm.api-key=

playlistcreator.spotify.client-id=
playlistcreator.spotify.client-secret=
playlistcreator.spotify.redirect-uri=http://127.0.0.1:8080/login/oauth2/code/spotify

playlistcreator.lastfm.api-key=
playlistcreator.lastfm.shared-secret=
```

In alternativa è possibile configurare le seguenti variabili d'ambiente:

```text
SETLISTFM_API_KEY
SPOTIFY_CLIENT_ID
SPOTIFY_CLIENT_SECRET
SPOTIFY_REDIRECT_URI
LASTFM_API_KEY
LASTFM_SHARED_SECRET
```

Il redirect URI configurato nella Spotify Developer Dashboard deve coincidere esattamente con quello utilizzato dall'applicazione.

## Avvio

Con il `settings.xml` locale presente nella directory principale:

```powershell
mvn -s .\settings.xml -gs .\settings.xml spring-boot:run
```

L'applicazione sarà disponibile all'indirizzo:

```text
http://127.0.0.1:8080
```

## Test

```powershell
mvn -s .\settings.xml -gs .\settings.xml test
```

## Sicurezza

Non aggiungere mai al repository:

- `application-local.properties`;
- `.env`;
- `settings.xml` personali;
- API key, client secret o token OAuth.
