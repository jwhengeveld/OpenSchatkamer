package nl.openschatkamer.mobile.ui

import android.net.Uri
import androidx.navigation.NavController
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.ui.BrowseSpec
import nl.openschatkamer.core.ui.ItemMemo

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val DISCOVER = "discover"
    const val MY_LIST = "mylist"
    const val SERIES = "series/{id}"
    const val PROGRAM = "program/{id}"
    const val STORY = "story/{slug}"
    const val BROWSE = "browse?spec={spec}"
    const val LIST = "list/{key}"
    const val ACCOUNT = "account"
    const val HISTORY = "history"
    const val DIAGNOSTICS = "diagnostics"

    fun list(key: String) = "list/${Uri.encode(key)}"

    fun series(id: String) = "series/$id"
    fun program(id: String) = "program/$id"
    fun story(slug: String) = "story/${Uri.encode(slug)}"
    fun browse(spec: BrowseSpec) = "browse?spec=${Uri.encode(spec.encode())}"
}

/** Opent een kaart op de plek die bij het soort hoort. */
fun NavController.open(item: SchatkamerItem) {
    ItemMemo.remember(item)
    when (item.kind) {
        ItemKind.PROGRAM -> navigate(Routes.program(item.id))
        ItemKind.SERIES -> navigate(Routes.series(item.seriesId ?: item.id))
        ItemKind.STORY -> navigate(Routes.story(item.id))
        ItemKind.PERSON -> navigate(Routes.browse(BrowseSpec(title = item.title, person = item.title)))
        ItemKind.BROADCASTER -> navigate(Routes.browse(BrowseSpec(title = item.title, broadcaster = item.title)))
    }
}
