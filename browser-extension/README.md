# Schatkamer Downloader (Chrome/Chromium-extensie)

Download onversleutelde streams uit De Schatkamer van Beeld en Geluid als
MP4 (video) of M4A (audio), zonder hercodering. Bedoeld als persoonlijke
back-up voor offline gebruik, bijvoorbeeld in het vliegtuig.

## Wat het doet

1. Je opent een programma of aflevering op schatkamer.beeldengeluid.nl.
2. Rechtsonder verschijnt een paneel met de gevonden stream.
3. Klik op **Download als MP4** of **Download als M4A**.
4. De extensie haalt de playlist en alle segmenten op, zet ze om naar een
   geldig MP4/M4A-bestand en bewaart dat in `Downloads/Schatkamer/`.

Streams worden gevonden via de netwerkverzoeken van de speler, het
`<video>`/`<audio>`-element of de HTML van de pagina. Starten is meestal
niet nodig; lukt een download toch niet, start de uitzending dan één keer
en probeer opnieuw.

## Wat wel en niet werkt

- **Audio (radio-uitzendingen):** werkt. Deze streams zijn onversleutelde
  HLS en worden als M4A opgeslagen.
- **Video zonder DRM:** werkt, als de site zo'n stream aanbiedt. Het
  resultaat is MP4.
- **Video met DRM (Widevine/PlayReady/FairPlay):** wordt bewust geweigerd.
  De extensie herkent de DRM-sleutels in de playlist en stopt met een
  duidelijke melding. Er wordt niets omzeild of ontsleuteld.

De meeste video's in de Schatkamer zijn DRM-beveiligd. Daarvoor geldt:
downloaden vereist het omzeilen van de technische beveiliging, en dat is
in Nederland en de EU niet toegestaan (Auteurswet art. 29a, EU
InfoSoc-richtlijn art. 6). "Voor eigen gebruik" is geen uitzondering op
dat verbod. De extensie doet dat dus niet.

## Installeren (unpacked)

1. Open `chrome://extensions`.
2. Zet **Ontwikkelaarsmodus** aan.
3. Klik **Extensie laden** (of **Load unpacked**).
4. Kies de map `browser-extension/`.

Werkt in Chrome, Chromium, Edge en andere Chromium-browsers.

## Installeren als CRX

De build maakt een ondertekend CRX3-bestand:

    cd browser-extension
    ./build-extension.sh

Resultaat:

- `../dist/schatkamer-downloader-extension-v1.1.1.zip`
- `../dist/schatkamer-downloader-extension-v1.1.1.crx`

De bijbehorende sleutel staat in `browser-extension/keys/crx-key.pem`
(buiten git) en bepaalt het extensie-ID. Bewaar die sleutel; met dezelfde
sleutel houden updates hetzelfde ID.

Installatie van een CRX buiten de Web Store:

- **Chromium en de meeste Chromium-browsers:** sleep het `.crx`-bestand
  naar `chrome://extensions` of open het bestand.
- **Google Chrome (stabiel, macOS/Windows):** Chrome blokkeert CRX-bestanden
  die niet via de Web Store zijn geïnstalleerd. Gebruik daar **Extensie
  laden** met de uitgepakte map.

Het CRX3-formaat wordt door `pack-crx.mjs` gemaakt en direct geverifieerd.
Het script genereert desgewenst een nieuwe RSA-sleutel en kan ook los
draaien:

    node pack-crx.mjs pack <zip> <crx> [key.pem]
    node pack-crx.mjs verify <crx>

## Gebruik

1. Open een uitzending in de Schatkamer.
2. Wacht tot het paneel rechtsonder de stream toont.
3. Klik op de downloadknop.
4. Het bestand verschijnt in `Downloads/Schatkamer/`.

Bij een 403-melding: ververs de pagina en probeer opnieuw. Helpt dat
niet, start de uitzending één keer (klik op play) en download daarna.

## Uitvoerformaat

- Audio: `.m4a` (AAC in een MP4-container).
- Video: `.mp4` (fMP4 of TS die zonder hercodering naar fMP4 is omgezet).
- Terugval: `.ts`, als de gebruikte muxer een stream niet kan omzetten.
  Dat speelt prima af in VLC en de meeste mediaspelers; met ffmpeg kun je
  het verliesvrij omzetten:

      ffmpeg -i bestand.ts -c copy bestand.mp4

Er wordt nooit opnieuw gecodeerd: beeld en geluid blijven bit-voor-bit
gelijk.

## Hoe de CloudFront-authenticatie werkt

De CDN werkt met kortstondige, ondertekende URL's en cookies. De URL in de
HTML van de pagina is een Next.js RSC-payload; daar plakt soms de id van
het volgende chunkje direct achter de laatste `Key-Pair-Id`, waardoor de
signed-URL-key twee tekens te lang wordt en CloudFront 403 `InvalidKey`
teruggeeft. De extensie normaliseert zo'n URL (de native key wordt
teruggebracht tot de `CloudFront-Key-Pair-Id`) en volgt daarna de
redirect die de CDN-sessie instelt. De segmenten worden met dezelfde
sessie opgehaald.

## Rechten en privacy

- `downloads`: het voltooide bestand opslaan.
- `offscreen`: een blob-URL maken; een MV3-serviceworker kan dat zelf niet.
- Host-toegang tot `schatkamer.beeldengeluid.nl` en `*.beeldengeluid.nl`
  om playlists en segmenten op te halen.

Er worden geen gegevens naar externe servers gestuurd. Alle verwerking
gebeurt lokaal in de browser.

## Licentie en attributie

De extensie gebruikt [mux.js](https://github.com/videojs/mux.js) 6.3.0
(Apache-2.0) voor het omzetten van MPEG-TS naar gefragmenteerde MP4. De
licentietekst staat in `vendor/LICENSE-muxjs.txt`.

Alle content, logo's en beeldmerken zijn eigendom van het Nederlands
Instituut voor Beeld en Geluid en de respectievelijke omroepen. Deze tool
is bedoeld voor persoonlijk gebruik. Verspreid geen Schatkamer-materiaal
zonder toestemming.

## Bestanden

| Bestand | Rol |
|---|---|
| `manifest.json` | MV3-manifest met rechten en host-permissies |
| `background.js` | Worker: URL normaliseren, playlist lezen, segmenten ophalen, muxen |
| `content.js` | Vindt de stream en toont het paneel op de pagina |
| `overlay.css` | Stijl van het paneel (Schatkamer-magenta en -cyan) |
| `popup.html`, `popup.js` | Uitleg bij het extensie-icoon |
| `offscreen.html`, `offscreen.js` | Maakt een blob-URL voor de download |
| `vendor/mux.min.js` | mux.js 6.3.0, TS naar fMP4 |
| `pack-crx.mjs` | CRX3-packer en -verifier |
| `build-extension.sh` | Bouwt zip en ondertekende CRX |
| `icons/` | Extensie-iconen 16/32/48/128 px |
| `../remux-to-mp4.sh` | Optioneel hulpscript voor `.ts`-bestanden |
