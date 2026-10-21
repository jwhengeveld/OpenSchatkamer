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
- **Vector Assets**:
  - `schatkamer_logo.xml`: Officieel "De Schatkamer" woordmerk + logo
  - `schatkamer_mark.xml`: Embleem (cyaan cirkel met magenta sleutelgat)
  - `beeldengeluid_logo.xml`: Beeld & Geluid embleem
  - `schatkamer_tv_banner.xml`: 320x180 (16:9) Android TV Leanback launcher banner
  - `ic_launcher_*`: Adaptieve launcher iconen voor Android 8+ en monochrome iconen voor Android 13+ theming
- **SchatkamerSplash**: Geanimeerd opstartscherm met pulserend sleutelgatlogo en merkovergang.

### 2. `:core`
- **Next.js RSC Parser**:
  - Haalt de rijke Next.js Server Components streaming payload (`self.__next_f.push`) op van de website.
  - Parseert swimlanes (Trending, Recent toegevoegd, Uitgelicht), categorieën en zoekresultaten zonder afhankelijk te zijn van private JSON REST endpoints.
- **Media & DRM Ondersteuning**:
  - HLS streams (`master.m3u8`) met CloudFront Signed Cookies en session tokens.
  - Widevine DRM configuratie via EZDRM license proxy:
    `https://widevine-dash.ezdrm.com/widevine-php/widevine-foreignkey.php?pX=E24145`
- **Repositories**:
  - `SchatkamerCatalogRepository`: Home swimlanes, zoekmachine met suggesties, omroepselectie (NOS, AVROTROS, etc.) en thematische genres (Amusement, Fictie, Humor, Jeugd, Muziek, Informatief).
  - `SchatkamerVideoRepository`: Ophalen en valideren van afspeelbare video- en audiostream metadata.
  - `FavoritesRepository`: Lokale opslag van favorieten ("Mijn Lijst").

### 3. `:mobile` (Telefoon & Tablet)
- **Google Cast (Chromecast)**:
  - Volledige integratie via Google Play Services Cast Framework.
  - Cast Receiver Application ID `377B8439` (officieel geconfigureerd voor Beeld en Geluid).
  - Naadloze overdracht van afspelen tussen lokaal ExoPlayer en Chromecast via `PlaybackManager`.
- **Moderne UI (Jetpack Compose Material 3)**:
  - **Start**: Hero carousel met trending programma's en dynamische swimlanes.
  - **Zoeken**: Live zoekfunctie met zoekgeschiedenis, categorie-chips en resultatenweergave.
  - **Ontdekken**: Bladeren op omroep (NOS, VPRO, etc.) en genre/thema.
  - **Mijn Lijst**: Snel overzicht van opgeslagen afleveringen en favorieten.
  - **Programmadetail**: Beschrijving, uitzenddatum, omroep, categorieën en directe afspeelknop.
- **Afspeelfuncties**:
  - `ExoPlayer` met hardwarematige decodering en adaptive bitrate streaming.
  - MiniPlayer onderaan het scherm tijdens browsen.
  - Picture-in-Picture (PiP) ondersteuning voor Android 8.0+.
  - `MediaSession` integratie voor bediening via vergrendelscherm, notificaties en bluetooth headsets.

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
