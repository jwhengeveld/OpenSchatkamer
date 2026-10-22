package nl.beeldengeluid.schatkamer.core.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Parser voor Next.js Server Components (RSC) en React Query dehydrated state
 * van Schatkamer Beeld & Geluid.
 */
object SchatkamerParser {

    const val WIDEVINE_LICENSE_URL =
        "https://widevine-dash.ezdrm.com/widevine-php/widevine-foreignkey.php?pX=E24145"

    private val RSC_PUSH = Pattern.compile("self\\.__next_f\\.push\\(\\[1,\"(.*?)\"\\]\\)", Pattern.DOTALL)
    private val MASTER_M3U8_URL = Pattern.compile("https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*")

    /**
     * Pakt de Next.js RSC payload uit de HTML-broncode.
     */
    fun extractRscPayload(html: String): String {
        val matcher = RSC_PUSH.matcher(html)
        val sb = StringBuilder()
        while (matcher.find()) {
            val raw = matcher.group(1).orEmpty()
            sb.append(unescapeJavaString(raw))
        }
        return sb.toString()
    }

    /**
     * Leest de startpagina uit: hero items en alle carousels/rijen.
     */
    fun parseHome(html: String): SchatkamerHome {
        val payload = extractRscPayload(html)
        val lines = payload.split("\n")

        val rows = mutableListOf<SchatkamerRow>()
        val heroItems = mutableListOf<SchatkamerItem>()

        for (line in lines) {
            if (!line.contains("module-") && !line.contains("carousel")) continue
            if (!line.contains("\"items\":[")) continue

            val row = parseCarouselRow(line)
            if (row != null && row.items.isNotEmpty()) {
                rows.add(row)
                if (heroItems.isEmpty() && row.items.isNotEmpty()) {
                    heroItems.addAll(row.items.take(5))
                }
            }
        }

        // Fallback als RSC push anders ingedeeld is: zoek direct op items arrays
        if (rows.isEmpty()) {
            val fallbackRows = parseFallbackRows(payload.ifEmpty { html })
            return SchatkamerHome(
                heroItems = fallbackRows.firstOrNull()?.items?.take(5) ?: emptyList(),
                rows = fallbackRows
            )
        }

        return SchatkamerHome(heroItems = heroItems, rows = rows)
    }

    /**
     * Parset een carousellijn uit de RSC stream.
     */
    private fun parseCarouselRow(line: String): SchatkamerRow? {
        val dataIdx = line.indexOf("{\"data\":")
        if (dataIdx == -1) return null

        val jsonStr = extractBalancedJsonObject(line, dataIdx + 8) ?: return null
        return runCatching {
            val obj = JSONObject(jsonStr)
            val id = obj.optString("id", "")
            val title = obj.optString("title", "Uitgelicht")
            val itemsArray = obj.optJSONArray("items") ?: return null

            val items = mutableListOf<SchatkamerItem>()
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.optJSONObject(i) ?: continue
                parseItem(itemObj)?.let { items.add(it) }
            }
            if (items.isNotEmpty()) SchatkamerRow(id = id, title = title, items = items) else null
        }.getOrNull()
    }

    /**
     * Controleert of een titel generiek, ontbrekend of onbruikbaar is (zoals 'Geen afleveringstitel').
     */
    fun isGenericOrEmptyTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return true
        val t = title.trim().lowercase()
        return t == "geen afleveringstitel" ||
                t == "zonder titel" ||
                t == "geen titel" ||
                t == "onbekend" ||
                t == "niet bekend" ||
                t.startsWith("niet bekend -") ||
                t.startsWith("niet bekend-") ||
                t == "null" ||
                t == "undefined"
    }

    /**
     * Construeert een betekenisvolle, nette titel aan de hand van serienaam, uitzenddatum of beschrijving.
     */
    fun buildSmartTitle(
        rawTitle: String?,
        subTitle: String = "",
        seriesTitle: String = "",
        date: String = "",
        description: String = "",
        broadcaster: String = ""
    ): String {
        val series = cleanStr(subTitle.ifBlank { seriesTitle })
        var cleanedRaw = cleanStr(rawTitle)
        if (cleanedRaw.startsWith("null:", ignoreCase = true)) {
            cleanedRaw = cleanedRaw.substring(5).trim()
        }
        if (cleanedRaw.startsWith("undefined:", ignoreCase = true)) {
            cleanedRaw = cleanedRaw.substring(10).trim()
        }
        val isGeneric = isGenericOrEmptyTitle(cleanedRaw)

        return when {
            !isGeneric && series.isNotBlank() && !cleanedRaw.contains(series, ignoreCase = true) -> {
                "$series: $cleanedRaw"
            }
            !isGeneric -> cleanedRaw
            series.isNotBlank() && date.isNotBlank() -> {
                "$series ($date)"
            }
            series.isNotBlank() -> series
            description.isNotBlank() -> {
                val snippet = description.take(65).substringBefore(".").trim()
                if (snippet.isNotBlank() && !isGenericOrEmptyTitle(snippet)) snippet else description.take(50).trim()
            }
            date.isNotBlank() -> "Uitzending $date"
            broadcaster.isNotBlank() -> "Archiefopname $broadcaster"
            else -> "Archiefuitzending"
        }
    }

    /**
     * Parset een SchatkamerItem uit een JSON-object.
     */
    fun parseItem(obj: JSONObject): SchatkamerItem? {
        val id = cleanStr(obj.optString("id").ifBlank { obj.optString("programId") })
        val rawTitle = cleanStr(obj.optString("title").ifBlank { obj.optString("name") })
        if (id.isBlank() && rawTitle.isBlank()) return null

        val seriesTitle = cleanStr(obj.optString("seriesTitle", ""))
        val subTitle = cleanStr(obj.optString("subTitle", "")).ifBlank { seriesTitle }
        val description = cleanStr(obj.optString("description", "")).ifBlank { cleanStr(obj.optString("summary", "")) }
        val date = cleanStr(obj.optString("date", "")).ifBlank { cleanStr(obj.optString("publishedAt", "")) }
        val mediaType = cleanStr(obj.optString("mediaType", "video")).ifBlank { "video" }
        val broadcaster = cleanStr(obj.optString("broadcaster", ""))
        val url = cleanStr(obj.optString("url", ""))
        val isPlayable = obj.optBoolean("isPlayable", true)

        val title = buildSmartTitle(
            rawTitle = rawTitle,
            subTitle = subTitle,
            seriesTitle = seriesTitle,
            date = date,
            description = description,
            broadcaster = broadcaster
        )

        var imageUrl = ""
        val imgObj = obj.optJSONObject("image")
        if (imgObj != null) {
            imageUrl = cleanStr(imgObj.optString("url", ""))
        } else {
            imageUrl = cleanStr(obj.optString("imageUrl", ""))
        }

        var seriesId: String? = null
        var seriesSlug: String? = null
        val urlObj = obj.optJSONObject("urlObject")
        if (urlObj != null) {
            val params = urlObj.optJSONObject("params")
            if (params != null) {
                seriesId = params.optString("id").takeIf { it.isNotBlank() }
                seriesSlug = params.optString("slug").takeIf { it.isNotBlank() }
            }
        }

        return SchatkamerItem(
            id = id,
            title = title,
            subTitle = subTitle,
            description = description,
            date = date,
            mediaType = mediaType,
            broadcaster = broadcaster,
            imageUrl = imageUrl,
            url = url,
            seriesId = seriesId,
            seriesSlug = seriesSlug,
            isPlayable = isPlayable
        )
    }

    /**
     * Parset zoekresultaten uit /zoeken?q=... of direct uit HTML kaarten.
     */
    fun parseSearch(html: String, query: String): SearchResult {
        val payload = extractRscPayload(html)
        val text = payload.ifEmpty { html }

        // Zoek naar `"results":[`
        val resultsIdx = text.indexOf("\"results\":[")
        if (resultsIdx != -1) {
            val arrayStr = extractBalancedJsonArray(text, resultsIdx + 10)
            if (arrayStr != null) {
                val jsonResult = runCatching {
                    val array = JSONArray(arrayStr)
                    val items = mutableListOf<SchatkamerItem>()
                    for (i in 0 until array.length()) {
                        val itemObj = array.optJSONObject(i) ?: continue
                        val id = cleanStr(itemObj.optString("id"))
                        val rawTitle = cleanStr(itemObj.optString("title"))
                        val seriesTitle = cleanStr(itemObj.optString("seriesTitle"))
                        val desc = cleanStr(itemObj.optString("description"))
                        val date = cleanStr(itemObj.optString("date"))
                        val mediaType = cleanStr(itemObj.optString("mediaType", "video")).ifBlank { "video" }
                        val broadcaster = cleanStr(itemObj.optString("broadcaster"))
                        val url = cleanStr(itemObj.optString("url"))
                        val isPlayable = itemObj.optBoolean("isPlayable", true)

                        val title = buildSmartTitle(
                            rawTitle = rawTitle,
                            subTitle = seriesTitle,
                            seriesTitle = seriesTitle,
                            date = date,
                            description = desc,
                            broadcaster = broadcaster
                        )

                        var imageUrl = ""
                        val imgObj = itemObj.optJSONObject("image")
                        if (imgObj != null) {
                            imageUrl = imgObj.optString("url")
                        }

                        items.add(
                            SchatkamerItem(
                                id = id,
                                title = title,
                                subTitle = seriesTitle.ifBlank { date },
                                description = desc,
                                date = date,
                                mediaType = mediaType,
                                broadcaster = broadcaster,
                                imageUrl = imageUrl,
                                url = url,
                                isPlayable = isPlayable
                            )
                        )
                    }
                    if (items.isNotEmpty()) SearchResult(query = query, total = items.size, items = items) else null
                }.getOrNull()

                if (jsonResult != null && jsonResult.items.isNotEmpty()) {
                    return jsonResult
                }
            }
        }

        // Fallback: parseer HTML kaarten direct uit de DOM/SSR
        return parseHtmlCards(html, query)
    }

    /**
     * Parset kaart-elementen direct uit de HTML (zoals op omroep- en genre-pagina's).
     */
    fun parseHtmlCards(html: String, query: String): SearchResult {
        val items = mutableListOf<SchatkamerItem>()
        val liPattern = java.util.regex.Pattern.compile("<li[^>]*data-gtm-component=\"([^\"]+)\"[^>]*>(.*?)</li>", java.util.regex.Pattern.DOTALL)
        val matcher = liPattern.matcher(html)

        while (matcher.find()) {
            val rawJson = matcher.group(1).orEmpty()
            val innerHtml = matcher.group(2).orEmpty()

            val unescapedJson = unescapeHtmlEntities(rawJson)
            val json = runCatching { JSONObject(unescapedJson) }.getOrNull() ?: continue

            val rawTitle = unescapeHtmlEntities(json.optString("title", "")).trim()
            val rawSubTitle = unescapeHtmlEntities(json.optString("subTitle", "")).trim()
            val broadcaster = unescapeHtmlEntities(json.optString("broadcaster", "")).trim()

            // Vind datum in innerHtml
            val dateMatcher = java.util.regex.Pattern.compile("\\b(\\d{1,2}\\s+[a-zA-Z]+\\s+\\d{4}|\\d{4}-\\d{2}-\\d{2})\\b").matcher(innerHtml)
            val cardDate = if (dateMatcher.find()) dateMatcher.group(1).orEmpty() else ""

            // Vind beschrijving in innerHtml (tekst zonder HTML tags)
            val textOnly = stripHtml(innerHtml)
            var cardDesc = textOnly
            for (removeToken in listOf(rawTitle, rawSubTitle, broadcaster, cardDate, "Geen afleveringstitel")) {
                if (removeToken.isNotBlank()) {
                    cardDesc = cardDesc.replace(removeToken, "")
                }
            }
            cardDesc = cardDesc.replace(Regex("^[-–—\\s,.:;]+"), "").replace(Regex("\\s+"), " ").trim()

            // Vind href in innerHtml
            val hrefMatcher = java.util.regex.Pattern.compile("href=\"([^\"]+)\"").matcher(innerHtml)
            val href = if (hrefMatcher.find()) hrefMatcher.group(1).orEmpty() else ""

            // Vind image url in innerHtml (via url=... in srcSet of src)
            var imageUrl = ""
            val imgMatcher = java.util.regex.Pattern.compile("url=([a-zA-Z0-9_-]+)").matcher(innerHtml)
            if (imgMatcher.find()) {
                val b64 = imgMatcher.group(1).orEmpty()
                runCatching {
                    val padded = b64 + "=".repeat((4 - b64.length % 4) % 4)
                    val decoded = String(android.util.Base64.decode(padded.replace('-', '+').replace('_', '/'), android.util.Base64.DEFAULT))
                    if (decoded.startsWith("http")) imageUrl = decoded
                }
            }
            if (imageUrl.isBlank()) {
                val directSrcMatcher = java.util.regex.Pattern.compile("src=\"([^\"]+)\"").matcher(innerHtml)
                if (directSrcMatcher.find()) imageUrl = directSrcMatcher.group(1).orEmpty()
            }

            // Extract ID
            val idMatcher = java.util.regex.Pattern.compile("/(?:aflevering|programma|serie)/(\\d+)").matcher(href)
            val id = if (idMatcher.find()) idMatcher.group(1).orEmpty() else href.hashCode().toString()

            val displayTitle = buildSmartTitle(
                rawTitle = rawTitle,
                subTitle = rawSubTitle,
                date = cardDate,
                description = cardDesc,
                broadcaster = broadcaster
            )

            if (displayTitle.isNotBlank()) {
                items.add(
                    SchatkamerItem(
                        id = id,
                        title = displayTitle,
                        subTitle = rawSubTitle.ifBlank { broadcaster },
                        description = cardDesc,
                        date = cardDate,
                        mediaType = "video",
                        broadcaster = broadcaster,
                        imageUrl = imageUrl,
                        url = href,
                        isPlayable = true
                    )
                )
            }
        }

        return SearchResult(query = query, total = items.size, items = items)
    }

    private fun stripHtml(text: String): String {
        return text.replace(Regex("<[^>]+>"), " ")
            .let { unescapeHtmlEntities(it) }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun unescapeHtmlEntities(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&eacute;", "é")
            .replace("&euml;", "ë")
            .replace("&iuml;", "ï")
            .replace("&oacute;", "ó")
    }

    /**
     * Parset de programmadetails en spelerinformatie (HLS stream, DRM, ondertitels, storyboard).
     */
    fun parseProgramDetail(html: String, programId: String): SchatkamerProgramDetail {
        val payload = extractRscPayload(html)
        val text = payload.ifEmpty { html }

        // 1. Zoek program stream metadata
        val streamInfo = parseStreamInfo(text)

        // 2. Zoek program metadata
        var title = ""
        var seriesTitle: String? = null
        var seriesId: String? = null
        var seriesSlug: String? = null
        var description = ""
        var date = ""
        var mediaType = "video"
        var broadcaster = ""
        var imageUrl = ""
        var collection = ""
        var legalBasisText = ""
        val presenters = mutableListOf<String>()
        val directors = mutableListOf<String>()
        val genres = mutableListOf<String>()
        val subjects = mutableListOf<String>()
        val episodes = mutableListOf<SchatkamerItem>()

        val programIdx = text.indexOf("\"program\":{")
        if (programIdx != -1) {
            val progJsonStr = extractBalancedJsonObject(text, programIdx + 10)
            if (progJsonStr != null) {
                runCatching {
                    val p = JSONObject(progJsonStr)
                    val rawTitle = cleanStr(p.optString("title").ifBlank { p.optString("name") })
                    description = cleanStr(p.optString("description"))
                    date = cleanStr(p.optString("publishedAt"))
                    mediaType = cleanStr(p.optString("mediaType", "video")).ifBlank { "video" }
                    collection = cleanStr(p.optString("collection"))
                    legalBasisText = cleanStr(p.optString("legalBasisText"))

                    val seriesObj = p.optJSONObject("series")
                    if (seriesObj != null) {
                        seriesId = cleanStr(seriesObj.optString("id")).takeIf { it.isNotBlank() }
                        seriesTitle = cleanStr(seriesObj.optString("title")).takeIf { it.isNotBlank() }
                        seriesSlug = cleanStr(seriesObj.optString("slug")).takeIf { it.isNotBlank() }
                    }

                    val broadcasterObj = p.optJSONObject("broadcaster")
                    if (broadcasterObj != null) {
                        broadcaster = cleanStr(broadcasterObj.optString("name"))
                    }

                    title = if (!isGenericOrEmptyTitle(rawTitle)) {
                        rawTitle
                    } else {
                        buildSmartTitle(
                            rawTitle = rawTitle,
                            seriesTitle = seriesTitle.orEmpty(),
                            date = date,
                            description = description,
                            broadcaster = broadcaster
                        )
                    }

                    val imgObj = p.optJSONObject("image")
                    if (imgObj != null) {
                        imageUrl = imgObj.optString("url")
                    }

                    p.optJSONArray("genres")?.let { arr ->
                        for (i in 0 until arr.length()) arr.optString(i)?.let { genres.add(it) }
                    }
                    p.optJSONArray("subjects")?.let { arr ->
                        for (i in 0 until arr.length()) arr.optString(i)?.let { subjects.add(it) }
                    }
                    p.optJSONArray("presenters")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            arr.optJSONObject(i)?.optString("name")?.let { presenters.add(it) }
                        }
                    }
                    p.optJSONArray("directors")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            arr.optJSONObject(i)?.optString("name")?.let { directors.add(it) }
                        }
                    }
                }
            }
        }

        // 3. Afleveringen in dezelfde serie
        val resultsIdx = text.indexOf("\"results\":[")
        if (resultsIdx != -1) {
            val arrayStr = extractBalancedJsonArray(text, resultsIdx + 10)
            if (arrayStr != null) {
                runCatching {
                    val array = JSONArray(arrayStr)
                    for (i in 0 until array.length()) {
                        val itm = array.optJSONObject(i) ?: continue
                        parseItem(itm)?.let { episodes.add(it) }
                    }
                }
            }
        }

        // Fallback voor titel en poster
        if (title.isBlank() || isGenericOrEmptyTitle(title)) {
            val titleMatch = Pattern.compile("<title>(.*?)</title>").matcher(html)
            if (titleMatch.find()) {
                val extracted = titleMatch.group(1)?.substringBefore(" | ")?.trim().orEmpty()
                if (!isGenericOrEmptyTitle(extracted)) {
                    title = extracted
                }
            }
        }
        if (imageUrl.isBlank() && streamInfo != null) {
            imageUrl = streamInfo.posterUrl
        }

        return SchatkamerProgramDetail(
            id = programId,
            title = title,
            seriesTitle = seriesTitle,
            seriesId = seriesId,
            seriesSlug = seriesSlug,
            description = description,
            date = date,
            mediaType = mediaType,
            broadcaster = broadcaster,
            imageUrl = imageUrl,
            presenters = presenters,
            directors = directors,
            genres = genres,
            subjects = subjects,
            collection = collection,
            legalBasisText = legalBasisText,
            isPlayable = streamInfo != null,
            streamInfo = streamInfo,
            episodes = episodes
        )
    }

    /**
     * Parset de stream metadata inclusief CloudFront signed URL / cookies en Widevine DRM.
     */
    fun parseStreamInfo(text: String): SchatkamerStreamInfo? {
        // 1. Zoek programStream JSON metadata
        var posterUrl = ""
        var subtitlesUrl: String? = null
        var storyboardUrl: String? = null
        var durationNumber = 0L
        var startSeconds = 0.0
        var endSeconds = 0.0
        var drmState = "LIVE"
        var refUrl = ""

        val streamIdx = text.indexOf("\"programStream\":{")
        if (streamIdx != -1) {
            val streamJson = extractBalancedJsonObject(text, streamIdx + 16)
            if (streamJson != null) {
                runCatching {
                    val obj = JSONObject(streamJson)
                    posterUrl = obj.optString("poster", "")
                    val streamsArr = obj.optJSONArray("streams")
                    if (streamsArr != null && streamsArr.length() > 0) {
                        val s = streamsArr.getJSONObject(0)
                        refUrl = s.optString("url", "")
                        posterUrl = s.optString("poster", posterUrl)
                        subtitlesUrl = s.optString("subtitles").takeIf { it.isNotBlank() && it != "null" }
                        storyboardUrl = s.optString("storyboard").takeIf { it.isNotBlank() && it != "null" }
                        durationNumber = s.optLong("durationNumber", 0L)
                        startSeconds = s.optDouble("start", 0.0)
                        endSeconds = s.optDouble("end", 0.0)
                        drmState = s.optString("drmState", "LIVE")
                    }
                }
            }
        }

        // 2. Extraheer de volledige master.m3u8 stream tokens en CloudFront cookies
        val tokens = extractStreamTokens(text)
            ?: (if (refUrl.startsWith("http")) extractStreamTokens(refUrl) else null)
            ?: return null

        return SchatkamerStreamInfo(
            streamUrl = tokens.streamUrl,
            posterUrl = posterUrl,
            subtitlesUrl = subtitlesUrl,
            storyboardUrl = storyboardUrl,
            durationNumber = durationNumber,
            startSeconds = startSeconds,
            endSeconds = endSeconds,
            drmState = drmState,
            widevineLicenseUrl = WIDEVINE_LICENSE_URL,
            cookieHeader = tokens.cookieHeader
        )
    }

    data class StreamTokens(
        val streamUrl: String,
        val cookieHeader: String
    )

    fun extractStreamTokens(text: String): StreamTokens? {
        val masterIdx = text.indexOf("master.m3u8")
        if (masterIdx == -1) return null

        val httpIdx = text.lastIndexOf("https://sk-video.cdn.beeldengeluid.nl/", masterIdx)
        if (httpIdx == -1) return null

        val sub = text.substring(httpIdx)
        val endIdx = sub.indexOfAny(charArrayOf('"', '\'', '\n', '\r', '}', ']', '<', '>', ' '))
        val urlSection = if (endIdx != -1) sub.substring(0, endIdx) else sub

        val baseEnd = urlSection.indexOf("master.m3u8") + "master.m3u8".length
        val baseUrl = urlSection.substring(0, baseEnd)

        val scope = if (urlSection.length > 300) urlSection else text

        val polMatch = Pattern.compile("(?:CloudFront-Policy|Policy)=([^\\\\&\"'\\s;,]+)").matcher(scope)
        val sigMatch = Pattern.compile("(?:CloudFront-Signature|Signature)=([^\\\\&\"'\\s;,]+)").matcher(scope)
        val keyMatch = Pattern.compile("(?:CloudFront-Key-Pair-Id|Key-Pair-Id)=([^\\\\&\"'\\s;,]+)").matcher(scope)

        val policy = if (polMatch.find()) polMatch.group(1).orEmpty() else ""
        val signature = if (sigMatch.find()) sigMatch.group(1).orEmpty() else ""
        val keyPairId = if (keyMatch.find()) keyMatch.group(1).orEmpty() else ""

        return if (policy.isNotBlank() && signature.isNotBlank() && keyPairId.isNotBlank()) {
            val cleanUrl = "$baseUrl?Policy=$policy&Signature=$signature&Key-Pair-Id=$keyPairId"
            val cookieHeader = "CloudFront-Policy=$policy; CloudFront-Signature=$signature; CloudFront-Key-Pair-Id=$keyPairId"
            StreamTokens(cleanUrl, cookieHeader)
        } else {
            StreamTokens(baseUrl, "")
        }
    }

    private fun parseFallbackRows(text: String): List<SchatkamerRow> {
        val rows = mutableListOf<SchatkamerRow>()
        val matches = Pattern.compile("\"title\":\"([^\"]+)\"[^}]*\"items\":\\[").matcher(text)
        var rowCount = 0
        while (matches.find()) {
            val title = matches.group(1).orEmpty()
            val itemsIdx = text.indexOf("\"items\":[", matches.start())
            if (itemsIdx != -1) {
                val arrayStr = extractBalancedJsonArray(text, itemsIdx + 8)
                if (arrayStr != null) {
                    runCatching {
                        val arr = JSONArray(arrayStr)
                        val items = mutableListOf<SchatkamerItem>()
                        for (i in 0 until arr.length()) {
                            arr.optJSONObject(i)?.let { parseItem(it)?.let { item -> items.add(item) } }
                        }
                        if (items.isNotEmpty()) {
                            rows.add(SchatkamerRow(id = "row_$rowCount", title = title, items = items))
                            rowCount++
                        }
                    }
                }
            }
        }
        return rows
    }

    private fun extractBalancedJsonObject(str: String, startIdx: Int): String? {
        val firstBrace = str.indexOf('{', startIdx)
        if (firstBrace == -1) return null
        var depth = 0
        for (i in firstBrace until str.length) {
            if (str[i] == '{') depth++
            else if (str[i] == '}') {
                depth--
                if (depth == 0) return str.substring(firstBrace, i + 1)
            }
        }
        return null
    }

    private fun extractBalancedJsonArray(str: String, startIdx: Int): String? {
        val firstBracket = str.indexOf('[', startIdx)
        if (firstBracket == -1) return null
        var depth = 0
        for (i in firstBracket until str.length) {
            if (str[i] == '[') depth++
            else if (str[i] == ']') {
                depth--
                if (depth == 0) return str.substring(firstBracket, i + 1)
            }
        }
        return null
    }

    private fun unescapeJavaString(st: String): String {
        val sb = StringBuilder(st.length)
        var i = 0
        while (i < st.length) {
            val ch = st[i]
            if (ch == '\\' && i + 1 < st.length) {
                val next = st[i + 1]
                when (next) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    '\"' -> { sb.append('\"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    'u' -> {
                        if (i + 5 < st.length) {
                            val code = st.substring(i + 2, i + 6)
                            runCatching {
                                sb.append(code.toInt(16).toChar())
                            }.onSuccess { i += 6 }.onFailure { sb.append("\\u"); i += 2 }
                        } else {
                            sb.append(ch)
                            i++
                        }
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    fun cleanStr(s: String?): String {
        val t = s?.trim().orEmpty()
        return if (t.equals("null", ignoreCase = true) || t.equals("undefined", ignoreCase = true)) "" else t
    }

    /**
     * Parset zoek- en omroepresultaten direct uit de officiële BFF API (/api/media/bff/search).
     */
    fun parseBffSearch(
        jsonString: String,
        fallbackQuery: String,
        currentOffset: Int = 0
    ): SearchResult {
        return runCatching {
            val root = JSONObject(jsonString)
            val dataObj = root.optJSONObject("data") ?: root
            val total = dataObj.optInt("total", 0)
            val resultsArr = dataObj.optJSONArray("results") ?: JSONArray()
            val items = mutableListOf<SchatkamerItem>()

            for (i in 0 until resultsArr.length()) {
                val obj = resultsArr.optJSONObject(i) ?: continue
                val extId = cleanStr(obj.optString("externalId").ifBlank { obj.optString("id") })
                val rawTitle = cleanStr(obj.optString("title"))
                val summary = cleanStr(obj.optString("summary"))
                val date = cleanStr(obj.optString("date"))
                val mediaType = cleanStr(obj.optString("mediaType", "video")).ifBlank { "video" }
                val broadcaster = cleanStr(obj.optString("broadcaster"))
                val isPlayable = obj.optBoolean("isPlayable", true)
                val slug = cleanStr(obj.optString("slug"))

                var seriesTitle = ""
                var seriesId: String? = null
                var seriesSlug: String? = null
                val seriesObj = obj.optJSONObject("series")
                if (seriesObj != null) {
                    seriesTitle = cleanStr(seriesObj.optString("title"))
                    seriesId = cleanStr(seriesObj.optString("id")).takeIf { it.isNotBlank() }
                    seriesSlug = cleanStr(seriesObj.optString("slug")).takeIf { it.isNotBlank() }
                }

                var imageUrl = ""
                val imgObj = obj.optJSONObject("image")
                if (imgObj != null) {
                    imageUrl = cleanStr(imgObj.optString("url"))
                }

                val url = if (slug.isNotBlank()) {
                    "https://schatkamer.beeldengeluid.nl/programma/$extId/$slug"
                } else {
                    "https://schatkamer.beeldengeluid.nl/programma/$extId"
                }

                val displayTitle = buildSmartTitle(
                    rawTitle = rawTitle,
                    subTitle = seriesTitle,
                    seriesTitle = seriesTitle,
                    date = date,
                    description = summary,
                    broadcaster = broadcaster
                )

                items.add(
                    SchatkamerItem(
                        id = extId,
                        title = displayTitle,
                        subTitle = seriesTitle.ifBlank { broadcaster.ifBlank { date } },
                        description = summary,
                        date = date,
                        mediaType = mediaType,
                        broadcaster = broadcaster,
                        imageUrl = imageUrl,
                        url = url,
                        seriesId = seriesId,
                        seriesSlug = seriesSlug,
                        isPlayable = isPlayable
                    )
                )
            }

            val availableGenres = mutableListOf<FacetItem>()
            val availableMaterials = mutableListOf<FacetItem>()
            val availableBroadcasters = mutableListOf<FacetItem>()
            val facetsObj = dataObj.optJSONObject("facets")
            if (facetsObj != null) {
                val genreArr = facetsObj.optJSONArray("genre")
                if (genreArr != null) {
                    for (g in 0 until genreArr.length()) {
                        val gObj = genreArr.optJSONObject(g)
                        val lbl = gObj?.optString("label")?.trim().orEmpty()
                        val count = gObj?.optInt("total", 0) ?: 0
                        if (lbl.isNotBlank()) availableGenres.add(FacetItem(label = lbl, count = count))
                    }
                }
                val materialArr = facetsObj.optJSONArray("material")
                if (materialArr != null) {
                    for (m in 0 until materialArr.length()) {
                        val mObj = materialArr.optJSONObject(m)
                        val lbl = mObj?.optString("label")?.trim().orEmpty()
                        val count = mObj?.optInt("total", 0) ?: 0
                        if (lbl.isNotBlank()) availableMaterials.add(FacetItem(label = lbl, count = count))
                    }
                }
                val broadcasterArr = facetsObj.optJSONArray("broadcaster")
                if (broadcasterArr != null) {
                    for (b in 0 until broadcasterArr.length()) {
                        val bObj = broadcasterArr.optJSONObject(b)
                        val lbl = bObj?.optString("label")?.trim().orEmpty()
                        val count = bObj?.optInt("total", 0) ?: 0
                        if (lbl.isNotBlank()) availableBroadcasters.add(FacetItem(label = lbl, count = count))
                    }
                }
            }

            val hasMore = currentOffset + items.size < total
            SearchResult(
                query = fallbackQuery,
                total = total,
                items = items,
                offset = currentOffset,
                hasMore = hasMore,
                availableGenres = availableGenres,
                availableMaterials = availableMaterials,
                availableBroadcasters = availableBroadcasters
            )
        }.getOrElse {
            SearchResult(
                query = fallbackQuery,
                total = 0,
                items = emptyList(),
                offset = currentOffset,
                hasMore = false
            )
        }
    }
}
