# OpenSchatkamer — Android & Android TV

Twee apps: **OpenSchatkamer** (telefoon/tablet) en **OpenSchatkamer TV** (Android TV / Google TV), elk met een eigen icoon. Een **onofficiële** app om te kijken en luisteren in [De Schatkamer van Beeld & Geluid](https://schatkamer.beeldengeluid.nl/), voor telefoon/tablet en Android TV.

> OpenSchatkamer is niet verbonden aan Beeld & Geluid. Alle uitzendingen komen uit De Schatkamer en zijn eigendom van Beeld & Geluid en de omroepen. De app gebruikt alleen wat de website zelf openbaar aan elke browser geeft.

## Modules

```text
brand/   Eigen huisstijl: filmspoel-beeldmerk, amber/inkt-kleuren, DM Serif Display (OFL), splash
core/    Catalogus, zoeken, afspeelgegevens, gedeelde ViewModels, gedeelde speler en cast-payload
mobile/  Telefoon/tablet (Compose, Chromecast, PiP, mediasessie, mini-speler)
app/     Android TV (Compose, D-pad, zijbalk, tv-speler)
```

## Hoe de app de catalogus leest

Zoals bij de Family7-app: de app leest de website zoals een browser dat doet, slim en zuinig.

| Wat | Bron |
|---|---|
| Startpagina (redactionele rijen) | HTML van `/`, Next.js RSC-stroom, getypeerd per rij: verhalen, uitzendingen, series, personen, omroepen |
| Zoeken, bladeren, afleveringen | JSON-dienst van de site: `/api/media/bff/search` (`query`, `broadcaster[]`, `genre[]`, `person[]`, `subject[]`, `seriesId`, `material[]`, `sort`, `yearmin`/`yearmax`, `playable`, `offset`, `limit`) |
| Serie | `/api/media/bff/series/{id}` |
| Uitzending | `/api/media/bff/programs/{id}` |
| Stream | HTML van `/programma/{id}` → `programStream` (het adres staat als RSC-tekstblok `$NN`) |
| Spelerinstellingen | `window.__CONFIG__` van de site (THEOplayer-licentie, CDN-sleutel, Chromecast-app-id), met terugval in `core/build.gradle.kts` |

- **Gedeelde verzoeken en kort geheugen** (`SchatkamerHttpClient`), net als de `PageFetcher` van Family7.
- **Stale-while-revalidate**: de startpagina staat op schijf en verschijnt bij een koude start meteen.
- **Leesbare teksten**: `JISKEFET` wordt "Jiskefet", een ontbrekende titel wordt "Uitzending van 16 september 1990", interne archiefvoorvoegsels verdwijnen.
- **Zoeken** vangt typefouten op ("jiskfet" vindt Jiskefet), toont series bovenaan, suggesties tijdens het typen en geschiedenis. Filters op video/audio, periode, genre (met live aantallen) en sortering.

## Kids

Een eigen sectie (tabblad op de telefoon, item in de zijbalk op tv) met kinderprogramma's. De site heeft geen kindergenre, dus `KidsViewModel` combineert:
- **Klassiekers**: een vaste lijst kinderseries met archief-id en nette naam (Sesamstraat, Het Klokhuis, Swiebertje, Dappere Dodo, Paulus de boskabouter, …). De live contracttest controleert wekelijks dat ze nog bestaan.
- **Schooltv**: alles van de omroepen NOT en TELEAC, gegroepeerd per serie (meeste uitzendingen eerst), zodat nieuwe series vanzelf meekomen.
- Rijen uit de zoekdienst: poppenspel, drama voor de jeugd, het Jeugdjournaal en Het Klokhuis.
- **Weekkeuze** (`WeeklyPick`): de eigen rijen van de app (Kids en de onderwerpen in Ontdekken) tonen elke week een andere selectie: een ander deel van de resultaten, in een andere volgorde. De hele week blijft het hetzelfde, op elk apparaat. Rijen die van de site zelf komen, wisselen daar al en blijven zoals ze zijn.
- **Verras me** opent een willekeurig kinderprogramma uit een van deze bronnen (`KidsViewModel.surprise`).
- Elke rij heeft **Kijk meer**: het bladerscherm met precies die zoekopdracht (`BrowseSpec` kan ook meerdere archiefomroepen en een serie aan).

## Verras me

Op de tv opent **Verras me** een willekeurige uitzending (`core/data/RandomPick.kt`):
- op Start uit het hele archief: eerst een willekeurig jaar vanaf 1920, dan een willekeurige uitzending uit dat jaar (de zoekdienst laat maar 10.000 resultaten zien, anders kwamen alleen de nieuwste aan bod);
- in Kids alleen kinderprogramma's;
- bij een serie met meer dan 50 afleveringen (zoals Het Klokhuis en het Jeugdjournaal) een willekeurige aflevering van die serie.

## Afspelen

- HLS (CMAF) met **Widevine via EZDRM**, net als de website.
- De CloudFront-parameters in het stream-adres zijn eigenlijk cookies. Lokaal sturen we ze als `Cookie`-header mee op elk verzoek.
- Alleen het programma zelf wordt getoond: de archiefband wordt geknipt op `start`/`end` van de site.
- Een handtekening verloopt na ongeveer 22 uur. De app haalt streams pas bij het afspelen op en ververst ze automatisch, ook na een 403.
- Voortgang wordt bewaard ("Verder kijken", hervatten), lokaal op het toestel.

## Bewaren en je Schatkamer-account

- **Zonder account** staat alles op het toestel (Room-database): Mijn lijst, eigen lijsten, gevolgde series met "nieuwe afleveringen", kijkvoortgang, "Gezien" en kijkgeschiedenis. Er is een back-up via export/import (JSON).
- **Met je Schatkamer-account** (zelfde inlog als de website: Amazon Cognito met SRP, dus het wachtwoord gaat nooit leesbaar over de lijn en wordt niet bewaard) gebruikt de app de gebruikersdienst van de site (`/api/user/bff`):
  - je lijsten (aanmaken, hernoemen, verwijderen, items toevoegen en weghalen);
  - favorieten (een lijst "Favorieten");
  - "Mijn series" (volgen).

  Alles is ook zichtbaar op schatkamer.beeldengeluid.nl. Het verversingstoken staat versleuteld in de Android Keystore. Wordt er een code gevraagd (sms, e-mail of authenticator), dan kun je die invullen.
- Kijkvoortgang en geschiedenis blijven altijd lokaal; de website heeft daar geen opslag voor.

## Robuust als de site verandert

Elke bron heeft een terugvalroute; `SourceHealth` houdt bij welke route werkt (verborgen diagnosescherm: tik 7× op het versienummer in Mijn lijst).

| Gegeven | Primair | Terugval |
|---|---|---|
| Startpagina | RSC-modules | HTML-kaarten (links + koppen) → eigen startpagina uit de zoekdienst → snapshot op schijf |
| Stream | `programStream` | ondertekend `master.m3u8`-adres los uit de pagina |
| Zoeken | JSON-dienst, tolerant voor hernoemde velden | server-gerenderde zoekpagina |
| Spelerinstellingen | `window.__CONFIG__` | opgeslagen → ingebouwd |

- `./gradlew :core:testDebugUnitTest -Plive` draait contracttests tegen de echte site.
- De GitHub Action `site-contract.yml` doet dat wekelijks en maakt een issue aan als de site is veranderd.

**Nieuwe content** komt binnen door te verversen bij het openen en bij terugkeer naar de app. Nieuwe kaarten krijgen het label "Nieuw" en gevolgde series tonen nieuwe afleveringen. `robots.txt` van de site staat geen crawlers toe; de app doet daarom niets op de achtergrond en haalt alleen op wat je bekijkt, zoals een browser.

## Native

- **Systeem**: het systeem-opstartscherm, predictive back, themed icons (Android 13+), deelmenu, mediasessie en -melding, PiP, haptics bij lang indrukken.
- **Telefoon**: launcher-snelkoppelingen (Zoeken, Mijn lijst), deep links (`openschatkamer://…` en links naar de site via "Openen met") en spraakzoeken.
- **Speler**: storyboard-miniaturen bij het spoelen, afspeelsnelheid en automatisch de volgende aflevering. Op de tv staan in de middelste rij ook knoppen voor de vorige en volgende aflevering en terug naar het begin; met ▲ kom je bij die knoppen, met ▼ bij de knoppen rechtsonder (beeldformaat, ondertiteling, instellingen). Het beeldformaat (passend, 110/125/133% inzoomen tegen ingebakken zwarte randen, scherm vullen of uitrekken); de keuze blijft bewaard. Bij audio staat er in plaats van een zwart scherm een eigen achtergrond: het beeld van de uitzending (of het omroeplogo), titel, gegevens, omschrijving en bewegende balkjes. De eerste keer Terug toont de bediening; nog een keer Terug sluit de speler.
- **TV**:
  - Op het startscherm van Google TV: "Verder kijken" (Watch Next) en een eigen kanaal met uitgelichte uitzendingen.
  - Zoeken vanuit Google TV en de Assistent (`SearchProvider`, alleen leesbaar voor het systeem), de zoekknop op de afstandsbediening en spraakzoeken.
  - Layout zoals Google TV: een inklapbare zijbalk, een vaste kop die rustig meeloopt met de focus, rijen die verticaal onder een vast focuspunt doorschuiven (`TvPivotScroll`), en horizontaal een focus die vrij over de kaarten loopt en pas bij de rand meeschuift (`TvEdgeScroll`). Omlaag of omhoog land je op de kaart recht eronder of erboven. Start in de zijbalk opent altijd een verse startpagina; het tabblad waar je al in zit brengt je terug naar het begin ervan.
  - De hele interface staat op 80% schaal (`TV_UI_SCALE`), zodat er meer tegelijk in beeld past.
  - Focusherstel per rij, een actiemenu via lang OK en laadvlakken in plaats van een laadrondje.
- **Tablet**: lijsten in 2 of 3 kolommen, en op de uitzendingspagina staan beeld en titel naast elkaar.
- **Snelheid**: Baseline Profiles voor beide apps (`baselineprofile-mobile`, `baselineprofile-tv`). Opnieuw maken met `./gradlew :mobile:generateReleaseBaselineProfile` en `:app:generateReleaseBaselineProfile`, met een emulator aan.

## Chromecast

De app stuurt naar de Chromecast-receiver van De Schatkamer (`377B8439`, THEOplayer).

- Die receiver leest zijn instellingen uit `customData` van het **LOAD-verzoek**. Media3's `CastPlayer` laadt via `queueLoad` zonder die data, dus de app laadt zelf met `RemoteMediaClient.load(MediaLoadRequestData)`. Zie `core/playback/CastPayload.kt`.
- `src` is het ondertekende adres **ongewijzigd**. CloudFront antwoordt met een redirect plus cookies en staat de receiver-origin toe via CORS, met `useCredentials` en `network.cors.credentials = include`.
- Overschakelen tussen telefoon en tv behoudt de plek. Stopt het casten, dan gaat het programma gepauzeerd verder op de telefoon.

AirPlay en "naar de TV-app sturen" zijn verwijderd. Een Apple TV kan deze Widevine-streams niet afspelen, en de TV-app had geen ontvanger. Een Google TV of een tv met ingebouwde Chromecast werkt via de Cast-knop.

## Bouwen

```bash
./gradlew assembleDebug            # beide apps
./gradlew testDebugUnitTest        # o.a. parsertests op echte site-fixtures (core/src/test/resources/fixtures)
./gradlew assembleRelease          # R8, ondertekend met keystore.properties (anders debug-sleutel)
```

Op externe volumes komen de builds in `~/.builds/Schatkamer/` (zie `build.gradle.kts`).
App-id's: `nl.openschatkamer.mobile` en `nl.openschatkamer.tv`.

## Updates

De apps staan niet in de Play Store; ze werken zichzelf bij vanuit de [GitHub-releases](https://github.com/jwhengeveld/OpenSchatkamer/releases).
- Bij het openen kijkt de app hoogstens twee keer per dag naar de nieuwste release (`core/update/AppUpdater.kt`). "Later" slaat die versie over tot er een nieuwere is.
- Handmatig controleren kan via **Mijn lijst → Controleren op updates**.
- De app downloadt de APK voor het eigen platform (`openschatkamer-mobile-…` of `openschatkamer-tv-…`) en geeft hem aan de Android-installer. De eerste keer vraagt Android om toestemming om apps van deze bron te installeren.
- Een update past alleen als hij met dezelfde sleutel is ondertekend. Bouw releases daarom altijd op dezelfde machine, of met dezelfde `keystore.properties`.
- Een release heeft altijd beide APK's met precies deze namen nodig, anders vindt de app de update niet.

## Licenties

DM Serif Display staat onder de SIL Open Font License (`brand/licenses/`). Omroeplogo's en alle uitzendingen zijn eigendom van de rechthebbenden.
