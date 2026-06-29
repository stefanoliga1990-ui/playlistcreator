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

## Profili di configurazione

L'applicazione utilizza due profili Spring:

- `local`, attivo di default per lo sviluppo sul proprio computer;
- `production`, da attivare esplicitamente nell'ambiente online.

Le impostazioni comuni e prive di credenziali sono definite in
`src/main/resources/application.properties`. I valori specifici dei due ambienti
sono separati in `application-local.properties` e
`application-production.properties`.

## Configurazione locale

Le credenziali non sono versionate. Con il profilo `local`, attivo di default,
l'applicazione carica automaticamente, quando presente, il file esterno:

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

## Configurazione di produzione

In produzione non viene caricato `application-local.properties` e non sono
previsti valori di fallback per le credenziali obbligatorie. Configurare nella
piattaforma di hosting le variabili presenti in `.env.example` e attivare il
profilo con:

```text
SPRING_PROFILES_ACTIVE=production
```

Le variabili obbligatorie sono:

```text
SETLISTFM_API_KEY
SPOTIFY_CLIENT_ID
SPOTIFY_CLIENT_SECRET
SPOTIFY_REDIRECT_URI
LASTFM_API_KEY
```

`LASTFM_SHARED_SECRET` rimane opzionale perche le integrazioni Last.fm attuali
eseguono soltanto chiamate pubbliche in lettura.

`SPOTIFY_REDIRECT_URI` dovra utilizzare HTTPS e il dominio pubblico definitivo,
ad esempio:

```text
https://playlist.example.com/login/oauth2/code/spotify
```

Se una variabile obbligatoria non e configurata, il profilo `production`
interrompe l'avvio invece di eseguire l'applicazione con credenziali vuote.

## Avvio

Con il `settings.xml` locale presente nella directory principale:

```powershell
mvn -s .\settings.xml -gs .\settings.xml spring-boot:run
```

Per verificare localmente il profilo di produzione, impostare prima le variabili
d'ambiente richieste e avviare con:

```powershell
mvn -s .\settings.xml -gs .\settings.xml -Dspring-boot.run.profiles=production spring-boot:run
```

L'applicazione sarà disponibile all'indirizzo:

```text
http://127.0.0.1:8080
```

## Packaging JAR

Per compilare, eseguire i test e produrre il JAR Spring Boot eseguibile:

```powershell
mvn -s .\settings.xml -gs .\settings.xml clean package
```

L'artefatto viene creato in:

```text
target\playlistcreator-0.0.1-SNAPSHOT.jar
```

Per avviarlo con il profilo locale:

```powershell
java -jar .\target\playlistcreator-0.0.1-SNAPSHOT.jar
```

Per avviarlo su un server con le variabili d'ambiente di produzione configurate:

```powershell
java -jar .\target\playlistcreator-0.0.1-SNAPSHOT.jar --spring.profiles.active=production
```

## Test

```powershell
mvn -s .\settings.xml -gs .\settings.xml test
```

## Sicurezza

L'applicazione non gestisce account o password propri. L'accesso alle API
applicative richiede una sessione Spotify valida; le richieste che modificano
lo stato sono protette tramite token CSRF. In produzione i cookie di sessione
sono `Secure`, `HttpOnly` e `SameSite=Lax`, quindi il dominio pubblico deve
essere raggiungibile esclusivamente tramite HTTPS.

Non aggiungere mai al repository:

- `application-local.properties`;
- `.env`;
- `settings.xml` personali;
- API key, client secret o token OAuth.

## Resilienza delle API esterne

I timeout configurati si applicano a ogni singola chiamata HTTP verso Spotify,
Last.fm o setlist.fm, non all'intero flusso applicativo. Un'elaborazione composta
da molte chiamate puo quindi continuare anche per diversi minuti.

La configurazione predefinita prevede:

- timeout di connessione di 10 secondi;
- timeout di risposta di 90 secondi per ciascuna chiamata;
- un solo retry per letture interrotte, errori `5xx` e risposte `429` con un
  `Retry-After` non superiore a 10 secondi;
- nessun retry automatico dopo un timeout;
- nessun retry per le scritture Spotify, per evitare playlist o brani duplicati;
- circuit breaker distinti per Spotify API, Spotify Login, Last.fm e setlist.fm.

In caso di rate limit la risposta applicativa usa lo stato HTTP `429` e propaga
l'eventuale header `Retry-After`. Dopo ripetuti errori temporanei il circuito del
solo provider coinvolto rimane aperto per 30 secondi, evitando di sovraccaricarlo
con ulteriori richieste destinate a fallire.

## Cache in memoria

Le cache applicative usano Caffeine e rimangono locali alla singola istanza.
Ogni cache ha un TTL e una dimensione massima: gli elementi scaduti vengono
rimossi e, raggiunto il limite, vengono eliminate automaticamente le voci meno
utili. I TTL funzionali restano invariati rispetto alla versione locale:

- 10 minuti per le selezioni da setlist;
- 30 minuti per discografie, generi e dati Spotify dell'utente;
- 24 ore per tag e metadati degli artisti simili.

I limiti variano da 100 elementi per i dati Spotify associati agli utenti fino
a 5000 elementi per i metadati Last.fm, impedendo una crescita indefinita della
memoria senza richiedere database o cache esterne.
