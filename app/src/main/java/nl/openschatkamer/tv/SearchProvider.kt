package nl.openschatkamer.tv

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import nl.openschatkamer.core.data.SearchFilter

/**
 * Zoekresultaten voor het systeem: zo vindt "Jiskefet" in de zoekbalk van
 * Google TV (of via de Assistent) ook uitzendingen en series in OpenSchatkamer.
 * Een klik opent ze via openschatkamer://program/… of …/series/….
 */
class SearchProvider : ContentProvider() {

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        val query = (selectionArgs?.firstOrNull() ?: uri.lastPathSegment).orEmpty().trim()
        if (query.length < 2 || query == SearchManager.SUGGEST_URI_PATH_QUERY) return cursor
        val app = context?.applicationContext as? SchatkamerTvApp ?: return cursor
        val limit = uri.getQueryParameter(SearchManager.SUGGEST_PARAMETER_LIMIT)?.toIntOrNull()?.coerceIn(1, 30) ?: 20
        val result = runBlocking {
            withTimeoutOrNull(5_000) { app.catalog.search(SearchFilter(query = query), limit = limit).getOrNull() }
        } ?: return cursor
        var row = 0L
        result.series.take(3).forEach { s ->
            cursor.addRow(arrayOf(row++, s.title, "Serie", s.imageUrl, "openschatkamer://series/${s.seriesId ?: s.id}", "video/*", null, null))
        }
        result.items.take(limit).forEach { item ->
            cursor.addRow(arrayOf(
                row++, item.title,
                listOfNotNull(item.seriesTitle?.takeIf { it != item.title }, item.broadcaster.ifBlank { null }, item.year.ifBlank { null }).joinToString(" · "),
                item.imageUrl, "openschatkamer://program/${item.id}",
                if (item.isAudio) "audio/*" else "video/*",
                item.year.toIntOrNull(), null
            ))
        }
        return cursor
    }

    override fun getType(uri: Uri): String = SearchManager.SUGGEST_MIME_TYPE
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private companion object {
        val COLUMNS = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_CONTENT_TYPE,
            SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR,
            SearchManager.SUGGEST_COLUMN_DURATION
        )
    }
}
