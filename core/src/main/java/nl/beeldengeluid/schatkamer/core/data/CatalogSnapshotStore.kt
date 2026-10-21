package nl.beeldengeluid.schatkamer.core.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Bewaart snapshots van de startpagina en geopende programma's op schijf in de no-backup map.
 * Daardoor start de app altijd meteen op zonder een leeg wit/zwart scherm.
 */
class CatalogSnapshotStore(context: Context) {

    private val dir = File(context.applicationContext.noBackupFilesDir, "schatkamer_snapshots").apply {
        if (!exists()) mkdirs()
    }

    private val homeFile = File(dir, "home_snapshot.json")

    fun writeHome(home: SchatkamerHome) {
        runCatching {
            val root = JSONObject()
            val heroArray = JSONArray()
            for (h in home.heroItems) {
                heroArray.put(itemToJson(h))
            }
            root.put("hero", heroArray)

            val rowsArray = JSONArray()
            for (r in home.rows) {
                val rObj = JSONObject()
                rObj.put("id", r.id)
                rObj.put("title", r.title)
                val itms = JSONArray()
                for (it in r.items) {
                    itms.put(itemToJson(it))
                }
                rObj.put("items", itms)
                rowsArray.put(rObj)
            }
            root.put("rows", rowsArray)

            homeFile.writeText(root.toString())
        }
    }

    fun readHome(): SchatkamerHome? {
        if (!homeFile.exists()) return null
        return runCatching {
            val text = homeFile.readText()
            val root = JSONObject(text)
            val heroItems = mutableListOf<SchatkamerItem>()
            val heroArr = root.optJSONArray("hero")
            if (heroArr != null) {
                for (i in 0 until heroArr.length()) {
                    heroArr.optJSONObject(i)?.let { jsonToItem(it)?.let { itm -> heroItems.add(itm) } }
                }
            }
            val rows = mutableListOf<SchatkamerRow>()
            val rowsArr = root.optJSONArray("rows")
            if (rowsArr != null) {
                for (i in 0 until rowsArr.length()) {
                    val rObj = rowsArr.optJSONObject(i) ?: continue
                    val id = rObj.optString("id")
                    val title = rObj.optString("title")
                    val itmsArr = rObj.optJSONArray("items") ?: continue
                    val itms = mutableListOf<SchatkamerItem>()
                    for (j in 0 until itmsArr.length()) {
                        itmsArr.optJSONObject(j)?.let { jsonToItem(it)?.let { itm -> itms.add(itm) } }
                    }
                    rows.add(SchatkamerRow(id = id, title = title, items = itms))
                }
            }
            if (rows.isNotEmpty()) SchatkamerHome(heroItems = heroItems, rows = rows) else null
        }.getOrNull()
    }

    private fun itemToJson(item: SchatkamerItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("subTitle", item.subTitle)
        put("description", item.description)
        put("date", item.date)
        put("mediaType", item.mediaType)
        put("broadcaster", item.broadcaster)
        put("imageUrl", item.imageUrl)
        put("url", item.url)
        put("seriesId", item.seriesId.orEmpty())
        put("seriesSlug", item.seriesSlug.orEmpty())
        put("isPlayable", item.isPlayable)
    }

    private fun jsonToItem(obj: JSONObject): SchatkamerItem? {
        val id = obj.optString("id")
        val title = obj.optString("title")
        if (id.isBlank() && title.isBlank()) return null
        return SchatkamerItem(
            id = id,
            title = title,
            subTitle = obj.optString("subTitle", ""),
            description = obj.optString("description", ""),
            date = obj.optString("date", ""),
            mediaType = obj.optString("mediaType", "video"),
            broadcaster = obj.optString("broadcaster", ""),
            imageUrl = obj.optString("imageUrl", ""),
            url = obj.optString("url", ""),
            seriesId = obj.optString("seriesId").takeIf { it.isNotBlank() },
            seriesSlug = obj.optString("seriesSlug").takeIf { it.isNotBlank() },
            isPlayable = obj.optBoolean("isPlayable", true)
        )
    }
}
