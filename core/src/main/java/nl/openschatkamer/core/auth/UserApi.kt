package nl.openschatkamer.core.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.SchatkamerHttpClient
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.data.SchatkamerText
import nl.openschatkamer.core.data.SiteFallbacks.firstString
import nl.openschatkamer.core.data.SiteFallbacks.imageUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Een lijst van je account op De Schatkamer. */
data class OnlineList(
    val uuid: String,
    val name: String,
    val programCount: Int = 0,
    val programIds: List<String> = emptyList(),
    val programs: List<SchatkamerItem> = emptyList(),
    val thumbnailUrl: String = "",
    val createdAt: String = ""
) {
    val shareUrl: String get() = "${SchatkamerHttpClient.BASE_URL}/lijst/$uuid"
}

class UserApiException(val code: Int, message: String) : IOException(message)

/**
 * De gebruikersdienst van de website (/api/user/bff), dezelfde aanroepen als
 * de site doet als je ingelogd bent: lijsten en "Mijn series".
 */
class UserApi(private val http: OkHttpClient, private val account: SchatkamerAccount) {

    // ---------------------------------------------------------------- lijsten

    suspend fun lists(): List<OnlineList> = call("GET", "/lists?expand=thumbnail").asArray().map(::parseList)
        .sortedByDescending { it.createdAt }

    /** Lijst met de uitzendingen erin (werkt ook voor gedeelde lijsten van anderen). */
    suspend fun list(uuid: String): OnlineList = parseList(call("GET", "/lists/$uuid", auth = false).asObject())

    suspend fun createList(name: String): String =
        call("POST", "/lists", JSONObject().put("name", name)).asObject().firstString("uuid", "id")

    suspend fun renameList(uuid: String, name: String) { call("PATCH", "/lists/$uuid", JSONObject().put("name", name)) }

    suspend fun deleteList(uuid: String) { call("DELETE", "/lists/$uuid") }

    suspend fun addToList(uuid: String, programId: String) { call("POST", "/lists/$uuid/items", JSONObject().put("programId", programId)) }

    suspend fun removeFromList(uuid: String, programId: String) { call("DELETE", "/lists/$uuid/items/$programId") }

    /** In welke van je lijsten deze uitzending staat. */
    suspend fun listsContaining(programId: String): Set<String> =
        call("GET", "/lists?programId=$programId").asArray()
            .filter { it.optBoolean("isAdded", false) }
            .map { it.firstString("uuid", "id") }
            .toSet()

    suspend fun setMemberships(programId: String, listUuids: Set<String>) {
        call("PUT", "/programs/$programId/list-memberships", JSONObject().put("listUuids", JSONArray(listUuids.toList())))
    }

    // ----------------------------------------------------------- Mijn series

    suspend fun savedSeries(): List<SchatkamerItem> = call("GET", "/series").asArray().mapNotNull { s ->
        val id = s.firstString("seriesId", "id")
        if (id.isBlank()) return@mapNotNull null
        val title = SchatkamerText.prettyTitle(s.firstString("title", "seriesTitle")).ifBlank { id }
        val image = s.optJSONObject("portraitImage")?.firstString("url")?.ifBlank { null }
            ?: s.optJSONObject("seriesPortraitImage")?.firstString("url")?.ifBlank { null }
            ?: s.imageUrl().ifBlank { s.optJSONObject("seriesImage")?.firstString("url").orEmpty() }
        SchatkamerItem(
            id = id, title = title, kind = ItemKind.SERIES, imageUrl = image, seriesId = id, seriesTitle = title,
            slug = s.firstString("slug", "seriesSlug").ifBlank { null }
        )
    }

    suspend fun saveSeries(seriesId: String) { call("POST", "/series", JSONObject().put("seriesId", seriesId)) }

    suspend fun unsaveSeries(seriesId: String) { call("DELETE", "/series/$seriesId") }

    // --------------------------------------------------------------- intern

    private fun parseList(o: JSONObject): OnlineList {
        val programs = o.optJSONArray("programs")?.let { a ->
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::parseProgram) }
        }.orEmpty()
        val ids = o.optJSONArray("programIds")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        val thumb = o.optJSONObject("thumbnail")?.firstString("url")?.ifBlank { null }
            ?: o.optJSONArray("thumbnails")?.optJSONObject(0)?.firstString("url").orEmpty()
        return OnlineList(
            uuid = o.firstString("uuid", "id"),
            name = o.firstString("name", "title").ifBlank { "Lijst" },
            programCount = o.optInt("programCount", maxOf(ids.size, programs.size)),
            programIds = ids.ifEmpty { programs.map { it.id } },
            programs = programs,
            thumbnailUrl = thumb.ifBlank { programs.firstOrNull()?.imageUrl.orEmpty() },
            createdAt = o.firstString("createdAt")
        )
    }

    private fun parseProgram(p: JSONObject): SchatkamerItem? {
        val id = p.firstString("id", "externalId", "programId")
        if (id.isBlank()) return null
        val series = p.optJSONObject("series")
        val seriesTitle = series?.firstString("title")?.let(SchatkamerText::prettyTitle)?.ifBlank { null }
        val date = p.firstString("publishedAt", "date").take(10)
        val description = p.firstString("description", "summary")
        return SchatkamerItem(
            id = id,
            title = SchatkamerText.episodeTitle(p.firstString("displayTitle", "name", "title"), seriesTitle, date, description),
            subTitle = seriesTitle.orEmpty(),
            description = SchatkamerText.cleanDescription(description),
            date = date,
            mediaType = p.firstString("mediaType").ifBlank { "video" },
            broadcaster = p.optJSONObject("broadcaster")?.firstString("name") ?: p.firstString("broadcaster"),
            imageUrl = p.imageUrl(),
            url = "${SchatkamerHttpClient.BASE_URL}/programma/$id",
            seriesId = series?.firstString("id")?.ifBlank { null },
            seriesTitle = seriesTitle,
            isPlayable = p.optBoolean("isPlayable", true)
        )
    }

    private sealed interface Body {
        fun asArray(): List<JSONObject>
        fun asObject(): JSONObject
    }

    private class Parsed(private val value: Any?) : Body {
        // De dienst verpakt antwoorden soms in {"data": ...}.
        private val unwrapped: Any? = (value as? JSONObject)?.let { o -> if (o.has("data") && o.length() <= 2) o.opt("data") else o } ?: value
        override fun asArray(): List<JSONObject> = when (val v = unwrapped) {
            is JSONArray -> (0 until v.length()).mapNotNull { v.optJSONObject(it) }
            is JSONObject -> listOf("items", "lists", "results", "series").firstNotNullOfOrNull { v.optJSONArray(it) }
                ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
            else -> emptyList()
        }
        override fun asObject(): JSONObject = unwrapped as? JSONObject ?: JSONObject()
    }

    private suspend fun call(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): Body =
        withContext(Dispatchers.IO) {
            suspend fun attempt(force: Boolean): Pair<Int, String> {
                val builder = Request.Builder().url(SchatkamerAccount.USER_API + path).header("Accept", "application/json")
                if (auth) {
                    val token = account.accessToken(forceRefresh = force) ?: throw UserApiException(401, "Je bent niet ingelogd.")
                    builder.header("Authorization", "Bearer $token")
                }
                val requestBody = body?.toString()?.toRequestBody("application/json".toMediaType())
                builder.method(method, requestBody ?: if (method == "POST" || method == "PUT" || method == "PATCH") "{}".toRequestBody("application/json".toMediaType()) else null)
                http.newCall(builder.build()).execute().use { return it.code to it.body?.string().orEmpty() }
            }
            var (code, text) = attempt(force = false)
            if (code == 401 && auth) {
                val retry = attempt(force = true)
                code = retry.first; text = retry.second
            }
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optJSONObject("data")?.optString("errorMessage") }.getOrNull()
                throw UserApiException(code, message?.ifBlank { null } ?: "De Schatkamer gaf foutcode $code.")
            }
            Parsed(runCatching { if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text) }.getOrNull())
        }
}
