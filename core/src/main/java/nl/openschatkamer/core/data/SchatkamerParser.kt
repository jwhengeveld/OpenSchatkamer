package nl.openschatkamer.core.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Leest de Schatkamer-website zoals een browser dat doet.
 *
 * - Pagina's (start, uitzending, verhaal) zijn Next.js-pagina's: de gegevens
 *   staan als "React Server Components"-stroom in `self.__next_f.push(...)`.
 *   Lange teksten, zoals het ondertekende streamadres, staan daarin als
 *   losse tekstblokken ("26:T458,...") waarnaar verwezen wordt met "$26".
 * - Zoeken, series en programma's komen uit de JSON-dienst die de website zelf
 *   gebruikt (/api/media/bff/...). Die is stabieler dan de HTML, dus de apps
 *   gebruiken de HTML alleen voor wat nergens anders staat.
 */
object SchatkamerParser {

    private const val PUSH_MARKER = "self.__next_f.push([1,\""
    private val MODULE_START = Regex("""\{"data":\{"id":"(module-[^"]+)"""")
    private val CONFIG_VALUE = { key: String -> Regex("""\\?"$key\\?"\s*:\s*\\?"([^"\\]+)\\?"""") }

    // ---------------------------------------------------------------- RSC-stroom

    /** Alle stukken van de RSC-stroom achter elkaar, als gewone tekst. */
    fun extractRscPayload(html: String): String {
        val sb = StringBuilder()
        var from = 0
        while (true) {
            val start = html.indexOf(PUSH_MARKER, from)
            if (start == -1) break
            val quote = start + PUSH_MARKER.length - 1
            val (text, end) = JsonScan.readString(html, quote) ?: break
            sb.append(text)
            from = end
        }
        return sb.toString()
    }

    /**
     * Zoekt het tekstblok waar "$id" naar verwijst. De lengte in de kop is in
     * bytes (UTF-8), niet in tekens.
     */
    fun resolveTextRef(payload: String, ref: String): String? {
        if (!ref.startsWith("$")) return ref
        return textChunks(payload)[ref.removePrefix("$")]
    }

    /**
     * Leest de stroom rij voor rij, zoals React dat doet. Gewone rijen
     * ("1a:[...]") eindigen op een regeleinde; tekstrijen ("27:T458,...") hebben
     * een lengte in bytes en géén afsluiter, dus de volgende rij kan er direct
     * achter staan ("...naar huis gaan.27:T458,https://...").
     */
    fun textChunks(payload: String): Map<String, String> {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val chunks = HashMap<String, String>()
        var pos = 0
        fun isHex(b: Byte) = b in '0'.code.toByte()..'9'.code.toByte() || b in 'a'.code.toByte()..'f'.code.toByte()
        while (pos < bytes.size) {
            var colon = pos
            while (colon < bytes.size && isHex(bytes[colon])) colon++
            if (colon == pos || colon >= bytes.size || bytes[colon] != ':'.code.toByte()) {
                // Geen rijkop: door naar de volgende regel.
                while (pos < bytes.size && bytes[pos] != '\n'.code.toByte()) pos++
                pos++
                continue
            }
            val id = String(bytes, pos, colon - pos, Charsets.US_ASCII)
            if (colon + 1 < bytes.size && bytes[colon + 1] == 'T'.code.toByte()) {
                var comma = colon + 2
                while (comma < bytes.size && bytes[comma] != ','.code.toByte()) comma++
                val length = String(bytes, colon + 2, comma - colon - 2, Charsets.US_ASCII).toIntOrNull(16)
                if (length != null) {
                    val start = comma + 1
                    val end = minOf(start + length, bytes.size)
                    chunks[id] = String(bytes, start, end - start, Charsets.UTF_8)
                    pos = end
                    continue
                }
            }
            while (pos < bytes.size && bytes[pos] != '\n'.code.toByte()) pos++
            pos++
        }
        return chunks
    }

    // ------------------------------------------------------------- startpagina

    fun parseHome(html: String): SchatkamerHome {
        val payload = extractRscPayload(html)
        val rows = mutableListOf<SchatkamerRow>()
        for (match in MODULE_START.findAll(payload)) {
            val json = JsonScan.objectAt(payload, match.range.first) ?: continue
            val data = runCatching { JSONObject(json).getJSONObject("data") }.getOrNull() ?: continue
            val items = data.optJSONArray("items").objects().mapNotNull(::parseModuleItem)
            if (items.isEmpty()) continue
            rows += SchatkamerRow(
                id = data.optString("id"),
                title = SchatkamerText.clean(data.optString("title")).ifBlank { "Uitgelicht" },
                items = items.distinctBy { it.kind to it.id },
                layout = layoutOf(data.optString("itemLayout"), items.first().kind)
            )
        }
        return SchatkamerHome(rows.distinctBy { it.id })
    }

    private fun layoutOf(itemLayout: String, kind: ItemKind): RowLayout = when (itemLayout) {
        "landscape-large" -> RowLayout.LANDSCAPE_LARGE
        "portrait" -> RowLayout.PORTRAIT
        "numbered-list" -> RowLayout.RANKED
        "circle" -> RowLayout.CIRCLE
        "logo" -> RowLayout.LOGO
        else -> when (kind) {
            ItemKind.SERIES -> RowLayout.PORTRAIT
            ItemKind.PERSON -> RowLayout.CIRCLE
            ItemKind.BROADCASTER -> RowLayout.LOGO
            else -> RowLayout.LANDSCAPE
        }
    }

    /** Een kaart uit een rij op de website; het soort volgt uit de route waar hij heen linkt. */
    fun parseModuleItem(obj: JSONObject): SchatkamerItem? {
        val urlObject = obj.optJSONObject("urlObject")
        val pathname = urlObject?.optString("pathname").orEmpty()
        val params = urlObject?.optJSONObject("params")
        val id = SchatkamerText.clean(obj.optString("id"))
        if (id.isBlank()) return null
        val title = SchatkamerText.clean(obj.optString("title"))
        val subTitle = SchatkamerText.clean(obj.optString("subTitle"))
        val image = obj.optJSONObject("image")?.optString("url")?.let(SchatkamerText::clean).orEmpty()
        val broadcaster = SchatkamerText.clean(obj.optString("broadcaster"))
        val url = SchatkamerText.clean(obj.optString("url"))

        return when {
            pathname.startsWith("/story") -> SchatkamerItem(
                id = params?.optString("slug")?.ifBlank { null } ?: id,
                title = title, kind = ItemKind.STORY, subTitle = "Verhaal", imageUrl = image, url = url
            )
            pathname.startsWith("/person") -> SchatkamerItem(
                id = id, title = title, kind = ItemKind.PERSON, subTitle = subTitle, imageUrl = image, url = url
            )
            pathname.startsWith("/broadcaster") -> SchatkamerItem(
                id = id, title = title, kind = ItemKind.BROADCASTER, imageUrl = image, url = url,
                slug = slugParam(params)
            )
            pathname.startsWith("/series/") && pathname.contains("/program/") -> SchatkamerItem(
                id = params?.optString("programId")?.ifBlank { null } ?: id,
                title = SchatkamerText.episodeTitle(title, null, obj.optString("date"), null),
                kind = ItemKind.PROGRAM,
                subTitle = subTitle,
                date = SchatkamerText.toIsoDate(obj.optString("date")),
                mediaType = SchatkamerText.clean(obj.optString("mediaType")).ifBlank { "video" },
                broadcaster = broadcaster,
                imageUrl = image,
                url = url,
                seriesId = params?.optString("id")?.ifBlank { null }
            )
            pathname.startsWith("/series") -> SchatkamerItem(
                id = id,
                title = SchatkamerText.prettyTitle(title),
                kind = ItemKind.SERIES,
                subTitle = subTitle,
                mediaType = SchatkamerText.clean(obj.optString("mediaType")).ifBlank { "video" },
                broadcaster = broadcaster.ifBlank { subTitle },
                imageUrl = image,
                url = url,
                seriesId = id,
                seriesTitle = SchatkamerText.prettyTitle(title),
                slug = slugParam(params)
            )
            else -> SchatkamerItem(
                id = id,
                title = SchatkamerText.episodeTitle(title, null, obj.optString("date"), null),
                kind = ItemKind.PROGRAM,
                subTitle = subTitle,
                date = SchatkamerText.toIsoDate(obj.optString("date")),
                mediaType = SchatkamerText.clean(obj.optString("mediaType")).ifBlank { "video" },
                broadcaster = broadcaster,
                imageUrl = image,
                url = url
            )
        }
    }

    private fun slugParam(params: JSONObject?): String? {
        val slug = params?.opt("slug") ?: return null
        return when (slug) {
            is JSONArray -> slug.optString(0)
            else -> slug.toString()
        }.takeIf { it.isNotBlank() }
    }

    // ------------------------------------------------------------ afspelen

    /** De afspeelgegevens van een uitzendingspagina, of null als er niets af te spelen valt. */
    fun parseStreamInfo(html: String): SchatkamerStreamInfo? {
        val payload = extractRscPayload(html).ifEmpty { html }
        val idx = payload.indexOf("\"programStream\":{")
        if (idx == -1) return null
        val json = JsonScan.objectAt(payload, idx + "\"programStream\":".length) ?: return null
        val programStream = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val stream = programStream.optJSONArray("streams").objects()
            .minByOrNull { it.optInt("playoutOrder", 0) } ?: return null

        val rawUrl = SchatkamerText.clean(stream.optString("url"))
        val playUrl = resolveTextRef(payload, rawUrl)?.trim().orEmpty()
        if (!playUrl.startsWith("http")) return null

        val posterUrl = SchatkamerText.clean(stream.optString("poster"))
            .ifBlank { SchatkamerText.clean(programStream.optString("poster")) }
        val expiresAt = stream.optLong("expiresAt", 0L).takeIf { it > 0 }
            ?: Regex("""[?&]Expires=(\d+)""").find(playUrl)?.groupValues?.get(1)?.toLongOrNull()
            ?: 0L

        return SchatkamerStreamInfo(
            playUrl = playUrl,
            manifestUrl = playUrl.substringBefore('?'),
            cookieHeader = cloudFrontCookies(playUrl),
            posterUrl = posterUrl,
            subtitlesUrl = SchatkamerText.clean(stream.optString("subtitles")).ifBlank { null },
            storyboardUrl = SchatkamerText.clean(stream.optString("storyboard")).ifBlank { null },
            clipStartSec = stream.optDouble("start", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            clipEndSec = stream.optDouble("end", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            drmProtected = stream.optString("drmState", "LIVE").equals("LIVE", ignoreCase = true),
            expiresAtSec = expiresAt
        )
    }

    /**
     * De CloudFront-parameters in het adres zijn eigenlijk cookies: CloudFront
     * weigert ze als queryparameter. Als Cookie-header meegestuurd openen ze het
     * manifest én alle onderliggende playlists en fragmenten.
     */
    fun cloudFrontCookies(url: String): String {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return ""
        return query.split('&')
            .filter { it.startsWith("CloudFront-") && it.contains('=') }
            .joinToString("; ")
    }

    // ------------------------------------------------------------ site-config

    /** De publieke spelerinstellingen van de website (window.__CONFIG__). */
    fun parseSiteConfig(html: String): SiteConfig? {
        fun value(key: String) = CONFIG_VALUE(key).find(html)?.groupValues?.get(1)
        val license = value("THEOPLAYER_KEY") ?: return null
        val cdnKey = value("THEOPLAYER_CDN_KEY") ?: return null
        val appId = value("CHROMECAST_APP_ID").orEmpty()
        return SiteConfig(license, cdnKey, appId)
    }

    // ---------------------------------------------------------------- verhalen

    fun parseStory(html: String, slug: String): SchatkamerStory {
        fun meta(property: String) = Regex(
            """<meta[^>]+property="$property"[^>]+content="([^"]*)""""
        ).find(html)?.groupValues?.get(1)?.let(::unescapeHtml).orEmpty()

        val payload = extractRscPayload(html)
        val programs = mutableListOf<SchatkamerItem>()
        var from = 0
        while (true) {
            val idx = payload.indexOf("\"program\":{", from)
            if (idx == -1) break
            from = idx + 10
            val json = JsonScan.objectAt(payload, idx + 10) ?: continue
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: continue
            val id = SchatkamerText.clean(obj.optString("id"))
            if (id.isBlank()) continue
            val series = obj.optJSONObject("series")
            val seriesTitle = series?.optString("title")?.let(SchatkamerText::prettyTitle)?.ifBlank { null }
            val date = SchatkamerText.clean(obj.optString("publishedAtISO")).ifBlank { obj.optString("publishedAt") }
            programs += SchatkamerItem(
                id = id,
                title = SchatkamerText.episodeTitle(
                    obj.optString("title").ifBlank { obj.optString("name") }, seriesTitle, date, obj.optString("description")
                ),
                kind = ItemKind.PROGRAM,
                subTitle = seriesTitle.orEmpty(),
                description = SchatkamerText.cleanDescription(obj.optString("description")),
                date = SchatkamerText.toIsoDate(date),
                mediaType = SchatkamerText.clean(obj.optString("mediaType")).ifBlank { "video" },
                broadcaster = obj.optJSONObject("broadcaster")?.optString("name").orEmpty(),
                imageUrl = obj.optJSONObject("image")?.optString("url").orEmpty(),
                url = SchatkamerText.clean(obj.optString("url")),
                seriesId = series?.optString("id")?.ifBlank { null },
                seriesTitle = seriesTitle
            )
        }

        // De kop van het verhaal: titel met achtergrondbeeld, gemarkeerd als "Verhaal".
        val header = Regex(
            """"title":"((?:[^"\\]|\\.)+)","description":(?:null|"(?:[^"\\]|\\.)*"),"variant":"[a-z]+","subTitle":"Verhaal","background":\{[^}]*?"url":"([^"]+)""""
        ).find(payload)
        val ogTitle = meta("og:title").removePrefix("De Schatkamer | ").substringBefore(" | De Schatkamer")

        return SchatkamerStory(
            slug = slug,
            title = header?.groupValues?.get(1)?.replace("\\\"", "\"") ?: ogTitle.ifBlank { slug.replace('-', ' ') },
            description = meta("og:description"),
            imageUrl = header?.groupValues?.get(2) ?: programs.firstOrNull()?.imageUrl.orEmpty(),
            programs = programs.distinctBy { it.id }
        )
    }

    private fun unescapeHtml(text: String): String = text
        .replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    // --------------------------------------------------------- JSON-dienst (BFF)

    fun parseBffSearch(json: String, query: String, offset: Int): SearchResult {
        val data = JSONObject(json).let { it.optJSONObject("data") ?: it }
        val total = data.optInt("total", 0)
        val items = data.optJSONArray("results").objects().mapNotNull(::parseBffProgram)
        val facets = data.optJSONObject("facets")
        fun facet(name: String) = facets?.optJSONArray(name).objects().mapNotNull { f ->
            val label = SchatkamerText.clean(f.optString("label"))
            if (label.isBlank()) null else FacetItem(label, f.optInt("total", 0))
        }
        return SearchResult(
            query = query,
            total = total,
            items = items,
            offset = offset,
            hasMore = offset + items.size < total && items.isNotEmpty(),
            facets = SearchFacets(
                genres = facet("genre"),
                subjects = facet("subject"),
                people = facet("person"),
                broadcasters = facet("broadcaster"),
                materials = facet("material"),
                programs = facet("program")
            )
        )
    }

    private fun parseBffProgram(obj: JSONObject): SchatkamerItem? {
        val id = SchatkamerText.clean(obj.optString("externalId")).ifBlank { SchatkamerText.clean(obj.optString("id")) }
        if (id.isBlank()) return null
        val series = obj.optJSONObject("series")
        val seriesTitle = series?.optString("title")?.let(SchatkamerText::prettyTitle)?.ifBlank { null }
            ?: SchatkamerText.prettyTitle(obj.optString("seriesTitle")).ifBlank { null }
        val date = SchatkamerText.clean(obj.optString("date"))
        val description = obj.optString("summary").ifBlank { obj.optString("description") }
        return SchatkamerItem(
            id = id,
            title = SchatkamerText.episodeTitle(obj.optString("title"), seriesTitle, date, description),
            kind = ItemKind.PROGRAM,
            subTitle = seriesTitle.orEmpty(),
            description = SchatkamerText.cleanDescription(description),
            date = date,
            mediaType = SchatkamerText.clean(obj.optString("mediaType")).ifBlank { "video" },
            broadcaster = SchatkamerText.clean(obj.optString("broadcaster")),
            imageUrl = obj.optJSONObject("image")?.optString("url")?.let(SchatkamerText::clean).orEmpty(),
            url = "${SchatkamerHttpClient.BASE_URL}/programma/$id",
            seriesId = series?.optString("id")?.ifBlank { null },
            seriesTitle = seriesTitle,
            slug = SchatkamerText.clean(obj.optString("slug")).ifBlank { null },
            isPlayable = obj.optBoolean("isPlayable", true)
        )
    }

    fun parseBffSeries(json: String): SchatkamerSeries {
        val d = JSONObject(json).let { it.optJSONObject("data") ?: it }
        return SchatkamerSeries(
            id = d.optString("id"),
            title = SchatkamerText.prettyTitle(d.optString("title")),
            slug = SchatkamerText.clean(d.optString("slug")),
            summary = SchatkamerText.cleanDescription(d.optString("summary")),
            imageUrl = d.optJSONObject("image")?.optString("url").orEmpty(),
            portraitUrl = d.optJSONObject("portraitImage")?.optString("url").orEmpty(),
            broadcaster = d.optJSONObject("broadcaster")?.optString("name")
                ?: SchatkamerText.clean(d.optString("broadcaster")),
            genres = d.optJSONArray("genres").strings(),
            firstYear = SchatkamerText.year(d.optString("firstProgramDate")),
            lastYear = SchatkamerText.year(d.optString("lastProgramDate"))
        )
    }

    fun parseBffProgramDetail(json: String): SchatkamerProgramDetail {
        val d = JSONObject(json).let { it.optJSONObject("data") ?: it }
        val series = d.optJSONObject("series")
        val seriesTitle = series?.optString("title")?.let(SchatkamerText::prettyTitle)?.ifBlank { null }
        val date = SchatkamerText.clean(d.optString("publishedAt")).take(10)
        val description = d.optString("description").ifBlank { d.optString("alternativeSummary") }
        val broadcaster = d.optJSONObject("broadcaster")

        val credits = buildList {
            fun people(key: String, role: String) = d.optJSONArray(key).objects().forEach { p ->
                val name = SchatkamerText.clean(p.optString("name"))
                if (name.isNotBlank()) add(Credit(name, SchatkamerText.clean(p.optString("role")).ifBlank { role }))
            }
            people("presenters", "Presentatie")
            people("performers", "Optreden")
            people("actors", "Te zien")
            people("guests", "Gast")
            people("directors", "Regie")
            people("others", "Medewerking")
        }.distinctBy { it.name }

        return SchatkamerProgramDetail(
            id = d.optString("id"),
            title = SchatkamerText.episodeTitle(
                SchatkamerText.clean(d.optString("displayTitle")).ifBlank { d.optString("name") }, seriesTitle, date, description
            ),
            seriesId = series?.optString("id")?.ifBlank { null },
            seriesTitle = seriesTitle,
            seriesSlug = series?.optString("slug")?.ifBlank { null },
            description = SchatkamerText.cleanDescription(description),
            date = date,
            mediaType = SchatkamerText.clean(d.optString("mediaType")).ifBlank { "video" },
            broadcaster = broadcaster?.optString("name").orEmpty(),
            broadcasterLogo = broadcaster?.optJSONObject("logo")?.optString("url").orEmpty(),
            imageUrl = d.optJSONObject("image")?.optString("url").orEmpty(),
            durationMinutes = d.optInt("duration", 0),
            genres = d.optJSONArray("genres").strings(),
            subjects = d.optJSONArray("subjects").strings(),
            credits = credits,
            episodeNumber = d.optInt("episodeNumber", 0).takeIf { it > 0 },
            seasonTitle = d.optJSONObject("season")?.optString("title")?.ifBlank { null },
            disclaimer = SchatkamerText.clean(d.optString("disclaimer")).ifBlank { null },
            isPlayable = d.optBoolean("isPlayable", true)
        )
    }

    // ---------------------------------------------------------------- hulp

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList()
        else (0 until length()).map { SchatkamerText.clean(optString(it)) }.filter { it.isNotBlank() }
}

/** Snelle, string-bewuste ontleding van JSON-fragmenten in een grotere tekst. */
internal object JsonScan {

    /** Leest een JSON-string vanaf het openingsaanhalingsteken; geeft de tekst en de index ná het slotteken. */
    fun readString(text: String, quoteIdx: Int): Pair<String, Int>? {
        if (quoteIdx >= text.length || text[quoteIdx] != '"') return null
        val sb = StringBuilder()
        var i = quoteIdx + 1
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> return sb.toString() to i + 1
                c == '\\' && i + 1 < text.length -> {
                    when (val n = text[i + 1]) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            val hex = text.substring(i + 2, minOf(i + 6, text.length))
                            val code = hex.toIntOrNull(16)
                            if (code != null && hex.length == 4) {
                                sb.append(code.toChar()); i += 4
                            } else sb.append("\\u")
                        }
                        else -> sb.append(n)
                    }
                    i += 2
                    continue
                }
                else -> sb.append(c)
            }
            i++
        }
        return null
    }

    /** Het volledige JSON-object dat begint bij de eerste '{' vanaf [from]. */
    fun objectAt(text: String, from: Int): String? = balanced(text, from, '{', '}')

    fun arrayAt(text: String, from: Int): String? = balanced(text, from, '[', ']')

    private fun balanced(text: String, from: Int, open: Char, close: Char): String? {
        val start = text.indexOf(open, from)
        if (start == -1) return null
        var depth = 0
        var inString = false
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (c == '\\') i++ else if (c == '"') inString = false
            } else when (c) {
                '"' -> inString = true
                open -> depth++
                close -> if (--depth == 0) return text.substring(start, i + 1)
            }
            i++
        }
        return null
    }
}
