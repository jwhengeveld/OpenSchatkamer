package nl.openschatkamer.mobile.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.brand.BrandMark
import nl.openschatkamer.brand.UNOFFICIAL_NOTICE
import nl.openschatkamer.core.auth.Account
import nl.openschatkamer.core.data.AnyList
import nl.openschatkamer.core.data.ItemKind
import nl.openschatkamer.core.data.SchatkamerItem
import nl.openschatkamer.core.data.SourceHealth
import nl.openschatkamer.core.data.UserLibrary
import nl.openschatkamer.core.ui.AccountViewModel
import nl.openschatkamer.core.ui.ListViewModel
import nl.openschatkamer.core.ui.Load
import nl.openschatkamer.mobile.ui.components.Artwork
import nl.openschatkamer.mobile.ui.components.CardShape
import nl.openschatkamer.mobile.ui.components.EmptyState
import nl.openschatkamer.mobile.ui.components.ErrorState
import nl.openschatkamer.mobile.ui.components.MessageState
import nl.openschatkamer.mobile.ui.components.Pill
import nl.openschatkamer.mobile.ui.components.ProgramCard
import nl.openschatkamer.mobile.ui.components.ProgramListItem
import nl.openschatkamer.mobile.ui.components.ScreenTitle
import nl.openschatkamer.mobile.ui.components.SectionHeader
import nl.openschatkamer.mobile.ui.components.SeriesPoster
import nl.openschatkamer.mobile.ui.components.SkeletonList

// ------------------------------------------------------------- Mijn lijst

@Composable
fun MyListScreen(
    library: UserLibrary,
    onOpen: (SchatkamerItem) -> Unit,
    onPlay: (SchatkamerItem) -> Unit,
    onOpenList: (AnyList) -> Unit,
    onAccount: () -> Unit,
    onHistory: () -> Unit,
    onDiagnostics: () -> Unit,
    contentPadding: PaddingValues
) {
    val account by library.account.collectAsState()
    val favorites by library.favorites.collectAsState()
    val continueWatching by library.local.continueWatching.collectAsState()
    val series by library.series.collectAsState()
    val lists by library.lists.collectAsState()
    val syncing by library.online.syncing.collectAsState()
    val onlineLists by library.online.lists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var versionTaps by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Back-up maken en terugzetten (lokaal, via het systeemkiesscherm).
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(library.local.export().toByteArray()) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@launch
            runCatching { library.local.import(text) }
        }
    }

    val newEpisodes = series.filter { it.newEpisodes > 0 }
    val savedPrograms = favorites.filter { it.kind != ItemKind.SERIES }
    val onlineFavorites = library.online.favoritesList

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item { ScreenTitle("Mijn lijst", Modifier.windowInsetsPadding(WindowInsets.statusBars)) }
        item { AccountCard(account, syncing, onAccount) }

        if (newEpisodes.isNotEmpty()) {
            item {
                SectionHeader("Nieuwe afleveringen")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(newEpisodes, key = { it.item.id }) { f ->
                        Box { SeriesPoster(f.item, { onOpen(f.item) }, width = 120.dp)
                            Text(
                                "+${f.newEpisodes}", style = MaterialTheme.typography.labelLarge, color = BrandColors.Ink,
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(BrandColors.Amber).padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        if (continueWatching.isNotEmpty()) {
            item {
                SectionHeader("Verder kijken", actionLabel = "Geschiedenis", onAction = onHistory)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(continueWatching, key = { it.item.id }) { p -> ProgramCard(p.item, { onPlay(p.item) }, progress = p.fraction, width = 220.dp) }
                }
            }
        }
        if (series.isNotEmpty()) {
            item {
                SectionHeader(if (account != null) "Mijn series" else "Gevolgde series")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(series, key = { it.item.id }) { SeriesPoster(it.item, { onOpen(it.item) }, width = 116.dp) }
                }
            }
        }

        // Lijsten: online (bij je account) en op dit toestel.
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Lijsten", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Rounded.Add, null, tint = BrandColors.Amber)
                    Spacer(Modifier.width(4.dp))
                    Text("Nieuwe lijst", color = BrandColors.Amber)
                }
            }
        }
        if (account == null && savedPrograms.isNotEmpty()) {
            item {
                ListRow(AnyList("local:favorites", "Bewaarde uitzendingen", false, savedPrograms.size, savedPrograms.first().imageUrl, savedPrograms), onOpenList)
            }
        }
        if (lists.isEmpty() && savedPrograms.isEmpty() && onlineFavorites == null) {
            item {
                Text(
                    "Bewaar uitzendingen met het bladwijzer-icoon, of maak een lijst om programma's te verzamelen. Houd een kaart ingedrukt voor meer opties.",
                    style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        items(lists, key = { it.key }) { list -> ListRow(list, onOpenList) }
        if (account != null && savedPrograms.isNotEmpty()) {
            item {
                ListRow(AnyList("local:favorites", "Op dit toestel bewaard", false, savedPrograms.size, savedPrograms.first().imageUrl, savedPrograms), onOpenList)
            }
        }

        item {
            SectionHeader("Meer")
            MoreRow(Icons.Rounded.History, "Kijkgeschiedenis", onHistory)
            MoreRow(Icons.Rounded.Bookmark, "Back-up maken van je lijsten op dit toestel") { exporter.launch("openschatkamer-backup.json") }
            MoreRow(Icons.Rounded.CloudSync, "Back-up terugzetten") { importer.launch(arrayOf("application/json")) }
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Text(UNOFFICIAL_NOTICE, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
                val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                Text(
                    "Versie $version", style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp).clickable { if (++versionTaps >= 7) { versionTaps = 0; onDiagnostics() } }
                )
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "Nieuwe lijst",
            hint = if (account != null || onlineLists.isNotEmpty()) "Wordt opgeslagen in je Schatkamer-account." else "Wordt opgeslagen op dit toestel.",
            initial = "",
            onConfirm = { library.createList(it, onlineList = account != null); creating = false },
            onDismiss = { creating = false }
        )
    }
}

@Composable
private fun AccountCard(account: Account?, syncing: Boolean, onAccount: () -> Unit) {
    Row(
        Modifier.padding(16.dp).fillMaxWidth().clip(CardShape).background(BrandColors.Surface).clickable(onClick = onAccount).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (account != null) BrandColors.Amber else BrandColors.SurfaceHigh), contentAlignment = Alignment.Center) {
            if (account != null) Text(account.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = BrandColors.Ink)
            else Icon(Icons.Rounded.Person, null, tint = BrandColors.TextMuted)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (account != null) {
                Text(account.name, style = MaterialTheme.typography.titleMedium)
                Text(if (syncing) "Bezig met bijwerken…" else "Lijsten en series staan in je Schatkamer-account", style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
            } else {
                Text("Inloggen met je Schatkamer-account", style = MaterialTheme.typography.titleMedium)
                Text("Optioneel: je lijsten en series ook op de website en andere apparaten", style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
            }
        }
        if (syncing) CircularProgressIndicator(color = BrandColors.Amber, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else Icon(if (account != null) Icons.Rounded.Cloud else Icons.Rounded.Lock, null, tint = BrandColors.Amber)
    }
}

@Composable
private fun ListRow(list: AnyList, onOpen: (AnyList) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(list) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(CardShape).background(BrandColors.SurfaceHigh), contentAlignment = Alignment.Center) {
            if (list.thumbnailUrl.isNotBlank()) Artwork(list.thumbnailUrl, Modifier.fillMaxSize())
            else Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = BrandColors.TextMuted)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(list.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${list.count} ${if (list.count == 1) "uitzending" else "uitzendingen"}", style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
        }
        Icon(if (list.online) Icons.Rounded.Cloud else Icons.Rounded.PhoneAndroid, if (list.online) "Online" else "Op dit toestel", tint = BrandColors.TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MoreRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = BrandColors.TextMuted)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun NameDialog(title: String, hint: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BrandColors.Surface,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Naam") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandColors.Amber, cursorColor = BrandColors.Amber)
                )
                if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Opslaan", color = BrandColors.Amber) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren", color = BrandColors.TextMuted) } }
    )
}

// ------------------------------------------------------------------ lijst

@Composable
fun ListScreen(
    viewModel: ListViewModel?,
    localFavorites: List<SchatkamerItem>?,
    library: UserLibrary,
    onOpen: (SchatkamerItem) -> Unit,
    onPlay: (SchatkamerItem) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues
) {
    val list = viewModel?.list?.collectAsState()?.value
    val state = viewModel?.items?.collectAsState()?.value ?: Load.Ready(localFavorites.orEmpty())
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val title = list?.name ?: "Bewaarde uitzendingen"

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Terug") }
            Column(Modifier.weight(1f)) {
                Text(if (list?.online == true) "ONLINE LIJST" else "LIJST OP DIT TOESTEL", style = MaterialTheme.typography.labelSmall, color = BrandColors.Amber)
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (list != null) {
                if (list.online) IconButton(onClick = {
                    val url = "https://schatkamer.beeldengeluid.nl/lijst/${list.uuid}"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${list.name}\n$url"), "Lijst delen"))
                }) { Icon(Icons.Rounded.Share, "Delen") }
                IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Hernoemen") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Verwijderen") }
            }
        }
        when (state) {
            Load.Loading -> SkeletonList()
            is Load.Failed -> ErrorState(state.message, { viewModel?.reload() })
            is Load.Ready -> if (state.data.isEmpty()) {
                EmptyState("Deze lijst is leeg", "Houd een uitzending ingedrukt en kies \"Toevoegen aan lijst\".")
            } else {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Alles afspelen", { onPlay(state.data.first()) }, selected = true, leading = Icons.AutoMirrored.Rounded.PlaylistPlay)
                }
                LazyColumn(contentPadding = contentPadding) {
                    items(state.data, key = { it.id }) { item ->
                        ProgramListItem(item, { onOpen(item) }, trailing = {
                            IconButton(onClick = {
                                if (list != null) { library.toggleInList(list, item); viewModel.reload() } else library.local.removeFavorite(item.id)
                            }) { Icon(Icons.Rounded.Close, "Uit de lijst halen", tint = BrandColors.TextMuted) }
                        })
                    }
                }
            }
        }
    }
    if (renaming && list != null) {
        NameDialog("Lijst hernoemen", "", list.name, onConfirm = { library.renameList(list, it); renaming = false }, onDismiss = { renaming = false })
    }
    if (confirmDelete && list != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = BrandColors.Surface,
            title = { Text("Lijst verwijderen?") },
            text = { Text(if (list.online) "\"${list.name}\" wordt ook van je Schatkamer-account verwijderd." else "\"${list.name}\" wordt van dit toestel verwijderd.") },
            confirmButton = { TextButton(onClick = { library.deleteList(list); confirmDelete = false; onBack() }) { Text("Verwijderen", color = BrandColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuleren", color = BrandColors.TextMuted) } }
        )
    }
}

// ------------------------------------------------------------ geschiedenis

@Composable
fun HistoryScreen(library: UserLibrary, onOpen: (SchatkamerItem) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues) {
    val history by library.local.history.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Terug") }
            Text("Kijkgeschiedenis", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) TextButton(onClick = { confirm = true }) { Text("Wissen", color = BrandColors.TextMuted) }
        }
        if (history.isEmpty()) {
            MessageState(Icons.Rounded.History, "Nog niets bekeken", "Wat je bekijkt of beluistert, verschijnt hier. De geschiedenis blijft op dit toestel.")
        } else LazyColumn(contentPadding = contentPadding) {
            items(history, key = { it.item.id }) { w ->
                ProgramListItem(w.item, { onOpen(w.item) }, progress = if (w.isFinished) null else w.fraction, trailing = {
                    IconButton(onClick = { library.local.removeFromHistory(w.item.id) }) { Icon(Icons.Rounded.Close, "Verwijderen", tint = BrandColors.TextMuted) }
                })
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false }, containerColor = BrandColors.Surface,
            title = { Text("Geschiedenis wissen?") },
            text = { Text("Ook je kijkvoortgang verdwijnt.") },
            confirmButton = { TextButton(onClick = { library.local.clearHistory(); confirm = false }) { Text("Wissen", color = BrandColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuleren", color = BrandColors.TextMuted) } }
        )
    }
}

// ----------------------------------------------------------------- account

@Composable
fun AccountScreen(viewModel: AccountViewModel, library: UserLibrary, onBack: () -> Unit) {
    val user by viewModel.user.collectAsState()
    val state by viewModel.state.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(user) { if (user != null) password = "" }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Terug") }
            Text("Account", style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(contentPadding = PaddingValues(20.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Je Schatkamer-account", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(10.dp))
            }
            val signedIn = user
            if (signedIn != null) {
                item {
                    Text("Ingelogd als", style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
                    Text(signedIn.name, style = MaterialTheme.typography.titleMedium)
                    Text(signedIn.email, style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Je lijsten, favorieten en \"Mijn series\" staan nu in je account en zijn ook te zien op schatkamer.beeldengeluid.nl. Kijkvoortgang en geschiedenis blijven op dit toestel.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(onClick = { library.online.refresh() }) { Icon(Icons.Rounded.CloudSync, null, tint = BrandColors.Amber); Spacer(Modifier.width(8.dp)); Text("Nu bijwerken", color = BrandColors.Text) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = viewModel::signOut) { Icon(Icons.Rounded.Logout, null, tint = BrandColors.Danger); Spacer(Modifier.width(8.dp)); Text("Uitloggen", color = BrandColors.Text) }
                }
            } else if (state.pendingCode != null) {
                item {
                    Text(state.pendingCode!!.hint, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.filter(Char::isDigit).take(8) }, singleLine = true, label = { Text("Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.confirmCode(code) }),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandColors.Amber, cursorColor = BrandColors.Amber),
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.error?.let { Text(it, color = BrandColors.Danger, modifier = Modifier.padding(top = 8.dp)) }
                    Row(Modifier.padding(top = 16.dp)) {
                        TextButton(onClick = viewModel::cancelCode) { Text("Annuleren", color = BrandColors.TextMuted) }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { viewModel.confirmCode(code) }, enabled = !state.busy && code.length >= 4,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Amber, contentColor = BrandColors.Ink)) { Text("Bevestigen") }
                    }
                }
            } else {
                item {
                    Text(
                        "Log in met het account dat je op schatkamer.beeldengeluid.nl hebt gemaakt. Dan worden je lijsten, favorieten en series daar bewaard. Zonder account werkt alles ook, alleen op dit toestel.",
                        style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted
                    )
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = email, onValueChange = { email = it.trim() }, singleLine = true, label = { Text("E-mailadres") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandColors.Amber, cursorColor = BrandColors.Amber),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, singleLine = true, label = { Text("Wachtwoord") },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showPassword) "Verberg wachtwoord" else "Toon wachtwoord")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.signIn(email, password) }),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandColors.Amber, cursorColor = BrandColors.Amber),
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.error?.let { Text(it, color = BrandColors.Danger, modifier = Modifier.padding(top = 10.dp)) }
                    Button(
                        onClick = { viewModel.signIn(email, password) },
                        enabled = !state.busy && email.contains('@') && password.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Amber, contentColor = BrandColors.Ink),
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
                    ) {
                        if (state.busy) CircularProgressIndicator(color = BrandColors.Ink, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        else Text("Inloggen")
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://schatkamer.beeldengeluid.nl/registreren")))
                    }) { Text("Nog geen account? Maak er een aan op de website", color = BrandColors.Amber) }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://schatkamer.beeldengeluid.nl/wachtwoord-vergeten")))
                    }) { Text("Wachtwoord vergeten?", color = BrandColors.TextMuted) }
                    Text(
                        "Je wachtwoord gaat versleuteld rechtstreeks naar De Schatkamer en wordt niet in de app bewaard.",
                        style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------- diagnose

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val status by SourceHealth.status.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Terug") }
            Text("Diagnose", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "Hoe de app De Schatkamer nu leest. \"Terugval\" betekent dat de website is veranderd en de app een andere route gebruikt.",
            style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted, modifier = Modifier.padding(16.dp)
        )
        SourceHealth.Source.entries.forEach { source ->
            val s = status[source]
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).clip(CircleShape).background(
                        when (s?.outcome) {
                            SourceHealth.Outcome.OK -> androidx.compose.ui.graphics.Color(0xFF5BD18B)
                            SourceHealth.Outcome.FALLBACK -> BrandColors.Amber
                            SourceHealth.Outcome.FAILED -> BrandColors.Danger
                            null -> BrandColors.Outline
                        }
                    )
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(source.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (s?.outcome) {
                            null -> "Nog niet gebruikt"
                            SourceHealth.Outcome.OK -> "In orde"
                            SourceHealth.Outcome.FALLBACK -> "Terugval: ${s.detail}"
                            SourceHealth.Outcome.FAILED -> "Mislukt: ${s.detail}"
                        },
                        style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted
                    )
                }
            }
        }
    }
}
