# Schatkamer Downloader (Chrome/Chromium-extensie)

Download een video uit De Schatkamer van Beeld en Geluid als back-up,
zodat je hem later offline kunt kijken.

## Wat het doet

1. Je opent een programma of aflevering op schatkamer.beeldengeluid.nl
2. Je start de video op de pagina
3. Rechtsonder verschijnt een paneel met Download als MP4
4. De extensie haalt de master.m3u8 op, volgt de beste kwaliteitsvariant,
   downloadt alle segmenten (met de juiste CloudFront-cookies) en zet ze aan
   elkaar tot een bestand

## Installeren (unpacked, het makkelijkst)

1. Open chrome://extensions
2. Zet Ontwikkelaarsmodus aan (rechtsboven)
3. Klik Extensie laden, dan Gemapte extensie laden
4. Kies deze map: browser-extension/
5. Pin de extensie met het puzzelstukje

## Installeren als crx

Een crx is hetzelfde pakket als hierboven, maar ondertekend. Chrome maakt
dat zelf, in twee klikken:

1. chrome://extensions, Ontwikkelaarsmodus aan
2. Klik Extensie inpakken
3. Kies deze map en vul een willekeurige sleutel in
4. Chrome maakt het crx-bestand plus een pem-sleutel in de map
5. Sleep het crx-bestand naar een Chrome-venster om het te installeren

Handmatig een crx ondertekenen raak ik niet aan: de indeling is
browser-specifiek en een half werkend crx is erger dan een geladen map.

## Gebruik

    1  Open een programma in de Schatkamer
    2  Druk op afspelen
    3  Wacht tot het paneel rechtsonder een stream toont
    4  Klik Download als MP4
    5  Kies de opslagmap in het Chrome-dialoogvenster

Het bestand komt in Downloads/Schatkamer/.

## Output: ts of mp4?

De HLS-stukken zijn MPEG-transportstreams. De extensie plakt ze tot een
ts-bestand. Dat is het standaardformaat van HLS en speelt prima af in
VLC, QuickTime en de meeste mediaspelers.

Wil je een echte mp4 (handig voor telefoon en mpv), remux dan met ffmpeg.
Dat is geen hercodering, dus snel en verliesvrij. Voor een enkel bestand:

    ./remux-to-mp4.sh ~/Downloads/Schatkamer/Programma.ts

Voor de hele map: zie remux-to-mp4.sh, of loop zelf over de bestanden met
ffmpeg -c copy en een extensie-vervanging van .ts naar .mp4.

## Wat niet werkt

DRM (Widevine). De Android-app in deze repo is geconfigureerd voor Widevine
via EZDRM. Browsers spelen DRM af via EME, met een Content Decryption Module
in de browser zelf. Die versleuteling is niet te omzeilen, en daar schrijf
ik geen tooling voor. De extensie herkent zo'n stream en stopt met een
duidelijke melding, in plaats van een onbruikbaar bestand te maken.

Wel werkt het bij de niet-DRM streams die de website ook aanbiedt.

## Rechten

De extensie vraagt alleen rechten voor schatkamer.beeldengeluid.nl en
.beeldengeluid.nl: cookies lezen (voor de CloudFront signed cookies),
downloads en opslag. Er gaat niets naar buiten.

Alle content, logo's en beeldmerken zijn auteursrechtelijk beschermd
eigendom van het Nederlands Instituut voor Beeld en Geluid en de
respectievelijke omroepen. Deze tool is bedoeld voor persoonlijk gebruik.
Verspreid geen Schatkamer-materiaal zonder toestemming.

## Bestanden

| Bestand | Rol |
|---|---|
| manifest.json | MV3-manifest met rechten en host-permissies |
| background.js | Achtergrondworker: playlist lezen, segmenten ophalen, samenvoegen, opslaan |
| content.js | Vindt de stream en toont het paneel op de pagina |
| overlay.css | Stijl van het paneel (Schatkamer-magenta en -cyan) |
| popup.html, popup.js | Uitleg bij het extensie-icoon |
| remux-to-mp4.sh | Hulp: ts naar mp4 zonder hercodering |
