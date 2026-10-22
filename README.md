# De Schatkamer van Beeld en Geluid - Android App

Native Android applicatie voor mobiel (smartphone/tablet) en Android TV voor **De Schatkamer van Beeld en Geluid** ([schatkamer.beeldengeluid.nl](https://schatkamer.beeldengeluid.nl/)).

Gebaseerd op de robuuste multi-module architectuur van de Family7 Android app, geoptimaliseerd voor het Nederlandse publieke omroeparchief (Polygoonjournaals, historische tv-uitzendingen, radio-opnames, iconische jeugdseries en omroepklassiekers van NOS, AVROTROS, BNNVARA, VPRO, EO, KRO-NCRV, NTR, Omroep MAX en HUMAN).

---

## 📱 Modules & Architectuur

Het project is opgezet als een modulaire Gradle codebase:

```text
Schatkamer/
├── brand/         # Visuele merkidentiteit, vectordrawables, kleurenpallet, splash screen
├── core/          # RSC JSON parser, OkHttp client, catalogus & video repositories, DRM, Room/Prefs
├── mobile/        # Mobiele app (Jetpack Compose, Google Cast, PiP, MiniPlayer, MediaSession)
└── app/           # Android TV app (10-foot D-Pad navigatie, Leanback launcher, TV speler)
```

### 1. `:brand`
- **Branding & Kleuren**:
  - `SchatkamerMagenta` (`#FF00BC`)
  - `SchatkamerCyan` (`#05C8F0`)
  - `SchatkamerYellow` (`#FFE500`)
  - `SchatkamerDarkBackground` (`#111218`) & `SchatkamerDarkElevated` (`#1A1C24`)
- **Vector Assets & Transparante Omroeplogo's**:
  - `schatkamer_logo.xml`: Officieel "De Schatkamer" woordmerk + logo
  - `schatkamer_mark.xml`: Embleem (cyaan cirkel met magenta sleutelgat)
  - `beeldengeluid_logo.xml`: Beeld & Geluid embleem
  - `schatkamer_tv_banner.xml`: 320x180 (16:9) Android TV Leanback launcher banner
  - `ic_broadcaster_*`: 100% officiële, authentieke transparante omroeplogo's voor alle publieke omroepen (EO, NOS, AVROTROS, BNNVARA, VPRO, KRO-NCRV, NTR, Omroep MAX, HUMAN, WNL, PowNed, NPO) met perfecte donker-thema optimalisatie.
  - `ic_launcher_*`: Adaptieve launcher iconen voor Android 8+ en monochrome iconen voor Android 13+ theming
- **SchatkamerSplash**: Geanimeerd opstartscherm met pulserend sleutelgatlogo en merkovergang.

### 2. `:core`
- **Beeld & Geluid BFF Search API & RSC Parser**:
  - Directe integratie met de `https://schatkamer.beeldengeluid.nl/api/media/bff/search` backend API voor volledige archieftoegang.
  - Ontgrendelt het volledige publieke omroeparchief (10.000+ programma's per omroep en honderdduizenden in totaal) met dynamische paginering (`offset` + `limit`).
  - Ondersteunt multi-omroep queries voor fusieomroepen (bijv. AVROTROS vraagt automatisch ook het archief van AVRO en TROS op; BNNVARA vraagt BNN en VARA op; KRO-NCRV vraagt KRO en NCRV op).
  - Rijke facetfilters voor decennia, jaartallen, mediumtype (video/audio) en genres.
  - Parseert tevens Next.js Server Components streaming payload (`self.__next_f.push`) en server-side gerenderde HTML-kaarten als fallback en voor redactionele swimlanes.
- **Slimme Stale-While-Revalidate Disk Cache**:
  - `CatalogSnapshotStore`: Cacheert home-swimlanes en omroepcatalogi lokaal op de schijf.
  - 0 ms opstarttijd: toont direct de meest recente catalogus bij het openen en valideert op de achtergrond geruisloos met de Schatkamer-servers.
- **Media & DRM Ondersteuning**:
  - HLS streams (`master.m3u8`) met CloudFront Signed Cookies en session tokens.
  - Widevine DRM configuratie via EZDRM license proxy:
    `https://widevine-dash.ezdrm.com/widevine-php/widevine-foreignkey.php?pX=E24145`
- **Repositories**:
  - `SchatkamerCatalogRepository`: Home swimlanes, zoekmachine met suggesties, omroepselectie (NOS, AVROTROS, EO, etc.) en thematische genres (Amusement, Fictie, Humor, Jeugd, Muziek, Informatief).
  - `SchatkamerVideoRepository`: Ophalen en valideren van afspeelbare video- en audiostream metadata.
  - `FavoritesRepository`: Lokale opslag van favorieten ("Mijn Lijst").

### 3. `:mobile` (Telefoon & Tablet)
- **Volledig Archief & Oneindig Scrollen (Infinite Scroll)**:
  - Naadloze paginering bij het omlaag scrollen: laadt automatisch de volgende batches van 24 programma's in real-time in zonder haperingen.
- **Precieze Zoekmachine (BFF Search API Parameter Fix)**:
  - Volledige afstemming op de Beeld & Geluid backend parameter `query` (en fallback `q`), waardoor specifieke zoektermen (zoals *tante gazeuse*, *jiskefet*, *polygoon*) direct de exacte archieftitels en afleveringen als eerste tonen in plaats van een ongefilterde archieflijst.
  - Schone coroutine-aansturing in `SearchViewModel` zonder race conditions of zelfannulering.
- **Uitgebreide Filters, Live Facet Tellers & Filter Bottom Sheet**:
  - **Filter ModalBottomSheet**: Volledig geavanceerd filter-paneel via de `[ Filters (N) ]` knop met realtime facet-aantallen, gesorteerd op mediumtype, tijdsperiode (decennia) en thematische genres.
  - **Live Facet Aantallen**: Realtime indicatoren rechtstreeks uit de Beeld & Geluid Elasticsearch/BFF backend (`religieus programma (10k+)`, `video (1.403)`, etc.) vóórdat een filter wordt geactiveerd.
  - **Snelle Toggle Chips**: Direct op de horizontale filterbalk wisselen van sortering (`Relevantie`, `Nieuwste`, `Oudste`), decennium en één-taps `🎬 Video` en `📻 Audio` filters.
  - **Pull-To-Refresh**: Native `PullToRefreshBox` op zowel Ontdekken als Zoeken voor directe verversing met automatische cache-invalidatie.
  - **Sub-milliseconde L1 In-Memory Cache**: `ConcurrentHashMap` met 10-minuten TTL op alle zoek- en filtercombinaties voor bliksemsnelle navigatie zonder herhaalde netwerklatentie.
  - **Scroll-to-Top Reset & Idempotente Paginering**: Automatische reset naar de top (`scrollToItem(0)`) bij het wisselen van filters/sortering/omroep en mutex-beveiligde paginering tegen dubbele aanvragen.
  - **Robuuste Empty & Error States**: Duidelijke foutafhandeling met retry-knoppen en filter-reset wanneer er geen resultaten zijn.
  - **Contextueel Zoeken**: Zoek direct binnen een specifieke omroep via het geïntegreerde zoekveld ("Zoek binnen EO...").
  - **Aantal Badge**: Live teller van het aantal beschikbare programma's in het archief.
- **Terugknop Navigatiestack (Back Handling)**:
  - Volledige hiërarchische terug-navigatie: Speler sluiten -> Programmadetail sluiten -> Categorie/Zoekopdracht resetten -> Terugkeren naar Start-tab -> Pas bij startscherm app afsluiten. Voorkomt dat de app onverwacht afsluit bij het indrukken van de terugknop of bij het uitvoeren van het terug-veeggebaar.
- **Slimme Titels & Geen "Geen afleveringstitel" Meer**:
  - Automatische reconstructie van betekenisvolle titels voor archiefuitzendingen aan de hand van serienaam, uitzenddatum (`KERKDIENST (1956-12-09)` ipv `Geen afleveringstitel`) en beschrijving.
  - Disk snapshot cache geüpgraded naar `v2` met automatische opschoning van verouderde gegevens.
- **Systeemrotatie & Tablet Support**:
  - Volgt naadloos de systeemrotatie van het toestel; geoptimaliseerd voor zowel smartphones als tablets.
- **Native Zender Knoppen (Google Cast & Apple AirPlay)**:
  - **Officiële Schatkamer Chromecast Receiver (`377B8439`)**: Gekoppeld aan de officiële Beeld & Geluid THEOplayer Chromecast Receiver (`377B8439`) inclusief volledige `configuration`- en `sourceDescription`-payloads met EZDRM Widevine & PlayReady integratie. Voorkomt dat streams direct na het starten crashen of stoppen op Chromecast en Google TV's.
  - **Standaard Google Cast Knop**: Direct geïntegreerd in de topbalk en videospeler. Opent direct de native Android `MediaRouteChooserDialog` voor betrouwbare verbinding met Chromecast en Google Nest schermen.
  - **Dedicated Apple AirPlay Zender Knop**: Opent een stabiel `AirPlaySenderDialog` met live mDNS discovery (`_airplay._tcp`), apparaatselectie, verbindingsstatus en afstandsbediening (play/pause/seek).
- **Volledig Scherm & Native Redesign**:
  - Statusbalk insets strak uitgelijnd zonder zwarte gaten of dubbele padding boven het logo.
  - Volledige schermbenutting en scherpe typografie.
  - Dynamische badges zonder "null"-waardes.
- **Moderne UI (Jetpack Compose Material 3)**:
  - **Start**: Hero carousel met trending programma's en dynamische swimlanes.
  - **Zoeken**: Live zoekfunctie met zoekgeschiedenis, categorie-chips en resultatenweergave.
  - **Ontdekken**: Bladeren op omroep (met transparante logo's) en genre/thema.
  - **Mijn Lijst**: Snel overzicht van opgeslagen afleveringen en favorieten.
  - **Programmadetail**: Beschrijving, uitzenddatum, omroep, categorieën en directe afspeelknop.
- **Afspeelfuncties**:
  - `ExoPlayer` met hardwarematige decodering en adaptive bitrate streaming.
  - MiniPlayer onderaan het scherm tijdens browsen (met swipe-to-dismiss & close knop).
  - Volledige liggende modus (Landscape) met schermvergrendeling en meedraaien.
  - Picture-in-Picture (PiP) ondersteuning voor Android 8.0+.
  - `MediaSessionService` integratie voor bediening via vergrendelscherm, Android 13+ audionotificaties en bluetooth apparaten.

### 4. `:app` (Android TV)
- **10-Foot Experience**:
  - Volledige D-Pad afstandsbediening-navigatie met vloeiende focus-animaties (1.06x vergroting + cyan/magenta glow effect).
  - `TvNavigationRail` voor snelle navigatie tussen Start, Zoeken, Ontdekken en Mijn Lijst.
  - Leanback launcher compatibiliteit (`android.intent.category.LEANBACK_LAUNCHER`).
- **TV Video Player**:
  - Aangepaste `schatkamer_player_view` en `schatkamer_player_controls` voor 10-foot schermen.
  - D-Pad transportbediening: OK (Play/Pause), Links/Rechts (10s achteruit/vooruit spoelen), Terugknop handling.
  - OSD met programma-titel, omroep, actuele tijdcode en progress bar.

---

## 🛠️ Bouwen en Installeren

### Vereisten
- **JDK**: Java 21 (`openjdk@21`)
- **Android SDK**: `compileSdk = 35`, `minSdk = 26`
- **Gradle**: 8.10.2 (meegeleverd via Gradle Wrapper)

### Debug & Release APK bouwen
```bash
# Debug APK's bouwen
./gradlew assembleDebug

# Release APK's bouwen (ondertekend met R8 optimalisatie)
./gradlew assembleRelease

# Release App Bundles (.aab) voor Google Play
./gradlew bundleRelease
```

> **Opmerking macOS:** Bij bouwen op externe volumes (zoals `/Volumes/...`) worden de build-outputs automatisch omgeleid naar `~/.builds/Schatkamer/` om AppleDouble metadata corruptie te voorkomen.

Locatie van de gegenereerde APK's:
- **Android TV (Release)**: `~/.builds/Schatkamer/app/outputs/apk/release/app-release.apk`
- **Android Mobile (Release)**: `~/.builds/Schatkamer/mobile/outputs/apk/release/mobile-release.apk`
- **Android TV (Debug)**: `~/.builds/Schatkamer/app/outputs/apk/debug/app-debug.apk`
- **Android Mobile (Debug)**: `~/.builds/Schatkamer/mobile/outputs/apk/debug/mobile-debug.apk`

### Installeren via ADB

**Mobile**:
```bash
adb -s <telefoon_apparaat_id> install -r ~/.builds/Schatkamer/mobile/outputs/apk/debug/mobile-debug.apk
```

**Android TV**:
```bash
adb -s <tv_apparaat_id> install -r ~/.builds/Schatkamer/app/outputs/apk/debug/app-debug.apk
```

### Unit Tests draaien
```bash
./gradlew test
```

---

## 🔒 Licentie & Rechten
Alle content, logo's en beeldmerken zijn auteursrechtelijk beschermd eigendom van het Nederlands Instituut voor Beeld & Geluid en de respectievelijke omroepen.
