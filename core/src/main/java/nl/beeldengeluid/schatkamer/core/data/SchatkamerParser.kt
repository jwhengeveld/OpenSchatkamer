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
     * Parset een SchatkamerItem uit een JSON-object.
     */
    fun parseItem(obj: JSONObject): SchatkamerItem? {
        val id = obj.optString("id").ifBlank { obj.optString("programId") }
        val title = obj.optString("title").ifBlank { obj.optString("name") }
        if (id.isBlank() && title.isBlank()) return null

        val subTitle = obj.optString("subTitle", "")
        val description = obj.optString("description", "")
        val date = obj.optString("date", "")
        val mediaType = obj.optString("mediaType", "video")
        val broadcaster = obj.optString("broadcaster", "")
        val url = obj.optString("url", "")
        val isPlayable = obj.optBoolean("isPlayable", true)

        var imageUrl = ""
        val imgObj = obj.optJSONObject("image")
        if (imgObj != null) {
            imageUrl = imgObj.optString("url", "")
        } else {
            imageUrl = obj.optString("imageUrl", "")
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
     * Parset zoekresultaten uit /zoeken?q=...
     */
    fun parseSearch(html: String, query: String): SearchResult {
        val payload = extractRscPayload(html)
        val text = payload.ifEmpty { html }

        // Zoek naar `"results":[`
        val resultsIdx = text.indexOf("\"results\":[")
        if (resultsIdx == -1) {
            return SearchResult(query = query, total = 0, items = emptyList())
        }

        val arrayStr = extractBalancedJsonArray(text, resultsIdx + 10)
        if (arrayStr == null) {
            return SearchResult(query = query, total = 0, items = emptyList())
        }

        return runCatching {
            val array = JSONArray(arrayStr)
            val items = mutableListOf<SchatkamerItem>()
            for (i in 0 until array.length()) {
                val itemObj = array.optJSONObject(i) ?: continue
                val id = itemObj.optString("id")
                val title = itemObj.optString("title")
                val seriesTitle = itemObj.optString("seriesTitle").takeIf { it.isNotBlank() }
                val desc = itemObj.optString("description")
                val date = itemObj.optString("date")
                val mediaType = itemObj.optString("mediaType", "video")
                val broadcaster = itemObj.optString("broadcaster")
                val url = itemObj.optString("url")
                val isPlayable = itemObj.optBoolean("isPlayable", true)

                var imageUrl = ""
                val imgObj = itemObj.optJSONObject("image")
                if (imgObj != null) {
                    imageUrl = imgObj.optString("url")
                }

                items.add(
                    SchatkamerItem(
                        id = id,
                        title = title,
                        subTitle = seriesTitle ?: date,
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
            SearchResult(query = query, total = items.size, items = items)
        }.getOrDefault(SearchResult(query = query, total = 0, items = emptyList()))
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
                    title = p.optString("title").ifBlank { p.optString("name") }
                    description = p.optString("description")
                    date = p.optString("publishedAt")
                    mediaType = p.optString("mediaType", "video")
                    collection = p.optString("collection")
                    legalBasisText = p.optString("legalBasisText")

                    val seriesObj = p.optJSONObject("series")
                    if (seriesObj != null) {
                        seriesId = seriesObj.optString("id").takeIf { it.isNotBlank() }
                        seriesTitle = seriesObj.optString("title").takeIf { it.isNotBlank() }
                        seriesSlug = seriesObj.optString("slug").takeIf { it.isNotBlank() }
                    }

                    val broadcasterObj = p.optJSONObject("broadcaster")
                    if (broadcasterObj != null) {
                        broadcaster = broadcasterObj.optString("name")
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
        if (title.isBlank()) {
            val titleMatch = Pattern.compile("<title>(.*?)</title>").matcher(html)
            if (titleMatch.find()) {
                title = titleMatch.group(1)?.substringBefore(" | ")?.trim().orEmpty()
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
        // Zoek naar streams array in programStream
        var streamUrl = ""
        var posterUrl = ""
        var subtitlesUrl: String? = null
        var storyboardUrl: String? = null
        var durationNumber = 0L
        var startSeconds = 0.0
        var endSeconds = 0.0
        var drmState = "LIVE"

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
                        val refUrl = s.optString("url", "")
                        posterUrl = s.optString("poster", posterUrl)
                        subtitlesUrl = s.optString("subtitles").takeIf { it.isNotBlank() && it != "null" }
                        storyboardUrl = s.optString("storyboard").takeIf { it.isNotBlank() && it != "null" }
                        durationNumber = s.optLong("durationNumber", 0L)
                        startSeconds = s.optDouble("start", 0.0)
                        endSeconds = s.optDouble("end", 0.0)
                        drmState = s.optString("drmState", "LIVE")

                        if (refUrl.startsWith("$")) {
                            // Referentie naar eerdere regel in RSC stream (bijv. "$26")
                            val lineKey = refUrl.substring(1) + ":"
                            streamUrl = resolveRscReference(text, lineKey)
                        } else if (refUrl.startsWith("http")) {
                            streamUrl = refUrl
                        }
                    }
                }
            }
        }

        // Fallback: zoek direct naar master.m3u8 met query parameters in text
        if (streamUrl.isBlank()) {
            val matcher = MASTER_M3U8_URL.matcher(text)
            if (matcher.find()) {
                streamUrl = matcher.group(0)?.trim()?.substringBefore("\"")?.substringBefore("\n").orEmpty()
            }
        }

        if (streamUrl.isBlank()) return null

        return SchatkamerStreamInfo(
            streamUrl = streamUrl,
            posterUrl = posterUrl,
            subtitlesUrl = subtitlesUrl,
            storyboardUrl = storyboardUrl,
            durationNumber = durationNumber,
            startSeconds = startSeconds,
            endSeconds = endSeconds,
            drmState = drmState,
            widevineLicenseUrl = WIDEVINE_LICENSE_URL
        )
    }

    private fun resolveRscReference(text: String, lineKey: String): String {
        val lines = text.split("\n")
        for (l in lines) {
            if (l.startsWith(lineKey) || l.contains(lineKey)) {
                val httpIdx = l.indexOf("https://sk-video.cdn.beeldengeluid.nl/")
                if (httpIdx != -1) {
                    val clean = l.substring(httpIdx)
                    // Kap af bij JSON syntax of newline
                    val endIdx = clean.indexOfAny(charArrayOf('"', '\n', '\r', '}', ']'))
                    val candidate = if (endIdx != -1) clean.substring(0, endIdx) else clean
                    return cleanStreamUrl(candidate)
                }
            }
        }
        return ""
    }

    fun cleanStreamUrl(raw: String): String {
        var url = raw.trim()
        if (url.contains("Key-Pair-Id=")) {
            val keyIdx = url.indexOf("Key-Pair-Id=")
            val matcher = Pattern.compile("Key-Pair-Id=[A-Za-z0-9]+").matcher(url.substring(keyIdx))
            if (matcher.find()) {
                url = url.substring(0, keyIdx + matcher.end())
            }
        }
        return url
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
}
