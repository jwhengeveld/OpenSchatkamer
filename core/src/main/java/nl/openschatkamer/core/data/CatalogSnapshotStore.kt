package nl.openschatkamer.core.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Bewaart de laatst geladen startpagina op schijf. Bij een koude start toont
 * de app die meteen en ververst op de achtergrond (stale-while-revalidate),
 * zoals de Family7-app dat ook doet. Zonder netwerk blijft de app zo bruikbaar.
 */
class CatalogSnapshotStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "catalog_snapshots_v4").apply { mkdirs() }

    init {
        // Opruimen van snapshots uit eerdere versies met een ander formaat.
        listOf("catalog_snapshots", "catalog_snapshots_v2", "catalog_snapshots_v3").forEach {
            File(context.applicationContext.filesDir, it).deleteRecursively()
        }
    }

    fun readHome(): SchatkamerHome? = read("home")?.let { runCatching { decodeHome(it) }.getOrNull() }
        ?.takeIf { it.rows.isNotEmpty() }

    fun writeHome(home: SchatkamerHome) = write("home", encodeHome(home).toString())

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun read(name: String): String? = runCatching {
        File(dir, "$name.json").takeIf { it.exists() }?.readText()
    }.getOrNull()

    private fun write(name: String, json: String) {
        runCatching {
            val tmp = File(dir, "$name.json.tmp")
            tmp.writeText(json)
            tmp.renameTo(File(dir, "$name.json"))
        }
    }

    companion object {
        fun encodeHome(home: SchatkamerHome): JSONObject = JSONObject()
            .put("newIds", JSONArray(home.newIds.toList()))
            .put(
            "rows",
            JSONArray().apply {
                home.rows.forEach { row ->
                    put(
                        JSONObject()
                            .put("id", row.id)
                            .put("title", row.title)
                            .put("layout", row.layout.name)
                            .put("items", JSONArray().apply { row.items.forEach { put(encodeItem(it)) } })
                    )
                }
            }
        )

        fun decodeHome(json: String): SchatkamerHome {
            val root = JSONObject(json)
            val rows = root.getJSONArray("rows")
            val newIds = root.optJSONArray("newIds")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
            return SchatkamerHome(newIds = newIds, rows = (0 until rows.length()).map { i ->
                val r = rows.getJSONObject(i)
                val items = r.getJSONArray("items")
                SchatkamerRow(
                    id = r.getString("id"),
                    title = r.getString("title"),
                    layout = runCatching { RowLayout.valueOf(r.getString("layout")) }.getOrDefault(RowLayout.LANDSCAPE),
                    items = (0 until items.length()).mapNotNull { decodeItem(items.getJSONObject(it)) }
                )
            })
        }

        fun encodeItem(item: SchatkamerItem): JSONObject = JSONObject()
            .put("id", item.id)
            .put("title", item.title)
            .put("kind", item.kind.name)
            .put("subTitle", item.subTitle)
            .put("description", item.description)
            .put("date", item.date)
            .put("mediaType", item.mediaType)
            .put("broadcaster", item.broadcaster)
            .put("imageUrl", item.imageUrl)
            .put("url", item.url)
            .put("seriesId", item.seriesId.orEmpty())
            .put("seriesTitle", item.seriesTitle.orEmpty())
            .put("slug", item.slug.orEmpty())
            .put("isPlayable", item.isPlayable)

        fun decodeItem(o: JSONObject): SchatkamerItem? {
            val id = o.optString("id")
            if (id.isBlank()) return null
            return SchatkamerItem(
                id = id,
                title = o.optString("title"),
                kind = runCatching { ItemKind.valueOf(o.optString("kind")) }.getOrDefault(ItemKind.PROGRAM),
                subTitle = o.optString("subTitle"),
                description = o.optString("description"),
                date = o.optString("date"),
                mediaType = o.optString("mediaType", "video"),
                broadcaster = o.optString("broadcaster"),
                imageUrl = o.optString("imageUrl"),
                url = o.optString("url"),
                seriesId = o.optString("seriesId").ifBlank { null },
                seriesTitle = o.optString("seriesTitle").ifBlank { null },
                slug = o.optString("slug").ifBlank { null },
                isPlayable = o.optBoolean("isPlayable", true)
            )
        }
    }
}
