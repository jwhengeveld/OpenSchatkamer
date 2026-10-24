package nl.openschatkamer.core.data

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Terugvalroutes voor als de website van vorm verandert. Ze lezen wat elke
 * browser ziet (links, koppen, afbeeldingen) in plaats van de interne
 * Next.js-gegevens, en zijn dus minder precies maar veel stabieler.
 */
object SiteFallbacks {

    private val SERIES_EPISODE = Regex("""^/serie/(\d+)/[^/]+/aflevering/(\d+)""")
    private val SERIES = Regex("""^/serie/(\d+)(?:/([^/?#]+))?/?$""")
    private val PROGRAM = Regex("""^/programma/(\d+)""")
    private val STORY = Regex("""^/verhaal/([^/?#]+)""")
    private val PERSON = Regex("""^/persoon/(\d+)""")
    private val BROADCASTER = Regex("""^/omroep/(\d+)(?:/([^/?#]+))?""")

    /**
     * De startpagina uit de HTML zelf: elke lijst met kaarten onder een kop
     * wordt een rij; het soort volgt uit het adres waar de kaart heen linkt.
     */
    fun homeFromHtml(html: String): SchatkamerHome {
        val doc = Jsoup.parse(html, SchatkamerHttpClient.BASE_URL)
        val rows = mutableListOf<SchatkamerRow>()
        for ((index, list) in doc.select("ul:has(li[data-card])").withIndex()) {
            val items = list.select("li[data-card]").mapNotNull { card -> card.selectFirst("a[href]")?.let(::itemFromLink) }
                .distinctBy { it.kind to it.id }
            if (items.isEmpty()) continue
            val title = headingBefore(list) ?: "Uitgelicht"
            rows += SchatkamerRow(
                id = "html-$index",
                title = title,
                items = items,
                layout = when (items.first().kind) {
                    ItemKind.SERIES -> RowLayout.PORTRAIT
                    ItemKind.STORY -> RowLayout.LANDSCAPE_LARGE
                    ItemKind.PERSON -> RowLayout.CIRCLE
                    ItemKind.BROADCASTER -> RowLayout.LOGO
                    ItemKind.PROGRAM -> RowLayout.LANDSCAPE
                }
            )
        }
        return SchatkamerHome(rows)
    }

    private fun headingBefore(element: Element): String? {
        var node: Element? = element
        while (node != null) {
            var sibling = node.previousElementSibling()
            while (sibling != null) {
                val heading = if (sibling.tagName() == "h2") sibling else sibling.select("h2").lastOrNull()
                if (heading != null) return heading.text().trim().ifBlank { null }
                sibling = sibling.previousElementSibling()
            }
            node = node.parent()
        }
        return null
    }

    /** Een kaart uit een link, of null als de link nergens bekends heen gaat. */
    fun itemFromLink(link: Element): SchatkamerItem? {
        val href = link.attr("href").substringBefore('?').removePrefix(SchatkamerHttpClient.BASE_URL)
        val title = link.attr("data-gtm-interaction-text").ifBlank { link.selectFirst("img[alt]")?.attr("alt").orEmpty() }
            .ifBlank { link.text() }.trim()
        val image = imageOf(link)
        val url = SchatkamerHttpClient.BASE_URL + href
        SERIES_EPISODE.find(href)?.let { m ->
            return SchatkamerItem(
                id = m.groupValues[2], title = SchatkamerText.episodeTitle(title, null, null, null), kind = ItemKind.PROGRAM,
                imageUrl = image, url = url, seriesId = m.groupValues[1]
            )
        }
        PROGRAM.find(href)?.let { m ->
            return SchatkamerItem(id = m.groupValues[1], title = SchatkamerText.episodeTitle(title, null, null, null), imageUrl = image, url = url)
        }
        SERIES.find(href)?.let { m ->
            val name = SchatkamerText.prettyTitle(title)
            return SchatkamerItem(
                id = m.groupValues[1], title = name, kind = ItemKind.SERIES, imageUrl = image, url = url,
                seriesId = m.groupValues[1], seriesTitle = name, slug = m.groupValues[2].ifBlank { null }
            )
        }
        STORY.find(href)?.let { m ->
            return SchatkamerItem(id = m.groupValues[1], title = title, kind = ItemKind.STORY, subTitle = "Verhaal", imageUrl = image, url = url)
        }
        PERSON.find(href)?.let { m ->
            return SchatkamerItem(id = m.groupValues[1], title = title, kind = ItemKind.PERSON, imageUrl = image, url = url)
        }
        BROADCASTER.find(href)?.let { m ->
            return SchatkamerItem(id = m.groupValues[1], title = title, kind = ItemKind.BROADCASTER, imageUrl = image, url = url, slug = m.groupValues[2].ifBlank { null })
        }
        return null
    }

    /** De echte afbeelding achter de beeldoptimalisatie van de site (?url=<base64>). */
    @OptIn(ExperimentalEncodingApi::class)
    private fun imageOf(link: Element): String {
        val img = link.selectFirst("img") ?: return ""
        val source = img.attr("srcset").ifBlank { img.attr("srcSet") }.substringBefore(' ').ifBlank { img.attr("src") }
        val encoded = Regex("""[?&]url=([A-Za-z0-9_\-+/=]+)""").find(source)?.groupValues?.get(1)
        if (encoded != null) {
            runCatching {
                val padded = (encoded + "=".repeat((4 - encoded.length % 4) % 4)).replace('+', '-').replace('/', '_')
                val decoded = String(Base64.UrlSafe.decode(padded))
                if (decoded.startsWith("http")) return decoded
            }
        }
        return if (source.startsWith("http")) source.replace("&amp;", "&") else ""
    }

    /**
     * Een ondertekend streamadres ergens in de pagina, als het gestructureerde
     * programStream-blok onvindbaar is. De Key-Pair-Id wordt gelijkgezet aan
     * die van de cookies, zodat er nooit een aangeplakt stuk tekst in staat.
     */
    fun signedManifest(html: String): SchatkamerStreamInfo? {
        val text = html.replace("\\u0026", "&").replace("&amp;", "&")
        val match = Regex("""https://sk-video\.cdn\.beeldengeluid\.nl/[^"'\s<>\\]+?master\.m3u8\?[A-Za-z0-9_~\-=.&%]+""").find(text)
            ?: return null
        var url = match.value
        val keyPairId = Regex("""CloudFront-Key-Pair-Id=([A-Z0-9]{8,20})""").find(url)?.groupValues?.get(1)
        if (keyPairId != null) {
            url = url.replace(Regex("""([?&]Key-Pair-Id=)[A-Za-z0-9]+"""), "$1$keyPairId")
        }
        val expires = Regex("""[?&]Expires=(\d{10})""").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return SchatkamerStreamInfo(
            playUrl = url,
            manifestUrl = url.substringBefore('?'),
            cookieHeader = SchatkamerParser.cloudFrontCookies(url),
            expiresAtSec = expires
        )
    }

    /** Zoekresultaten uit de server-gerenderde zoekpagina (/zoeken?q=...). */
    fun searchFromHtml(html: String, query: String): SearchResult {
        val payload = SchatkamerParser.extractRscPayload(html).ifEmpty { html }
        val idx = payload.indexOf("\"results\":[")
        val items = if (idx >= 0) {
            JsonScan.arrayAt(payload, idx + 10)?.let { json ->
                runCatching { JSONArray(json) }.getOrNull()?.let { array ->
                    (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(SchatkamerParser::parseBffProgram) }
                }
            }.orEmpty()
        } else emptyList()
        val total = Regex(""""total":(\d+)""").find(payload)?.groupValues?.get(1)?.toIntOrNull() ?: items.size
        return SearchResult(query = query, total = total, items = items, hasMore = false)
    }

    /** Eerste niet-lege tekstwaarde onder een van de sleutels (de site hernoemt soms velden). */
    fun JSONObject.firstString(vararg keys: String): String {
        for (key in keys) {
            val value = SchatkamerText.clean(optString(key))
            if (value.isNotBlank()) return value
        }
        return ""
    }

    fun JSONObject.imageUrl(): String =
        optJSONObject("image")?.firstString("url", "src")?.ifBlank { null }
            ?: firstString("imageUrl", "image", "thumbnail", "poster")
}
