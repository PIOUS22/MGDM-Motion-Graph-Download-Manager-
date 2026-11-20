package com.piash.onedm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

val Purple = Color(0xFF512DA8)
val Accent = Color(0xFF7E57C2)
val Olive = Color(0xFF7CB342)

fun typeIcon(t: String): ImageVector = when (t) {
    "Everything" -> Icons.Default.GridView
    "Torrents" -> Icons.Default.FileDownload
    "Compressed" -> Icons.Default.FolderZip
    "Documents" -> Icons.Default.Description
    "Music" -> Icons.Default.MusicNote
    "Videos" -> Icons.Default.Movie
    "Subtitles" -> Icons.Default.ClosedCaption
    "Photos" -> Icons.Default.Image
    "Programs" -> Icons.Default.Android
    else -> Icons.Default.InsertDriveFile
}

@Composable
fun PurpleDialog(
    title: String,
    onClose: () -> Unit,
    icon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp).widthIn(max = 350.dp).fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().background(Purple).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClose) { Icon(Icons.Default.Close, null, tint = Color.White) }
                }
                Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).padding(16.dp), content = content)
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End, content = actions)
            }
        }
    }
}

fun info(title: String, text: String) = AppState.show { close ->
    PurpleDialog(title, close, actions = { TextButton(close) { Text("OK") } }) { Text(text, fontSize = 13.sp, lineHeight = 20.sp) }
}

@Composable
fun AccentButton(text: String, onClick: () -> Unit, color: Color = Accent) =
    Button(onClick, colors = ButtonDefaults.buttonColors(containerColor = color)) { Text(text) }

@Composable
fun TypeDropdown(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Category: $value", modifier = Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null) }
        DropdownMenu(open, { open = false }) {
            fileTypes.drop(1).forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { onChange(t); open = false }) }
        }
    }
}

fun showAddDialog(initialUrl: String? = null) {
    val clip = AppState.clipText()
    AppState.show { close ->
        var url by remember { mutableStateOf(initialUrl ?: if (clip.startsWith("http")) clip else "") }
        var name by remember { mutableStateOf(if (url.isEmpty()) "" else fileNameFromUrl(url)) }
        var type by remember { mutableStateOf(if (name.isEmpty()) "Videos" else typeFromName(name)) }
        PurpleDialog("Add New Download", close, actions = {
            TextButton(close) { Text("Cancel") }
            AccentButton("Start", {
                if (!url.trim().startsWith("http")) AppState.toast("Enter a valid http/https link")
                else { AppState.add(url.trim(), name, type); close() }
            })
        }) {
            OutlinedTextField(url, { url = it; name = fileNameFromUrl(it); type = typeFromName(name) }, label = { Text("Download Link (URL)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, { name = it }, label = { Text("File Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TypeDropdown(type) { type = it }
        }
    }
}

fun askDownload(url: String, name: String, cookie: String? = null, ua: String? = null) = AppState.show { close ->
    PurpleDialog("Download file?", close, actions = {
        TextButton(close) { Text("Cancel") }
        AccentButton("Download", { AppState.add(url, name, null, cookie, ua); AppState.toast("Added to $APP_NAME: $name"); close() })
    }) { Text(name) }
}

fun showMagnetDialog() = AppState.show { close ->
    var v by remember { mutableStateOf("") }
    PurpleDialog("Add Torrent / Magnet Link", close, actions = {
        TextButton(close) { Text("Cancel") }
        AccentButton("Add", {
            val s = v.trim(); close()
            when {
                s.startsWith("magnet:") -> if (!AppState.openExternal(s)) AppState.toast("No torrent app installed to handle magnet links")
                s.startsWith("http") -> AppState.add(s)
                else -> AppState.toast("Not a magnet or http link")
            }
        })
    }) {
        Text("Paste a magnet link or a .torrent URL.\n\nMagnet links open in your installed torrent app. A .torrent URL downloads the .torrent file.", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(v, { v = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("magnet:?xt=urn:btih:…") })
    }
}

fun showBatchDialog() = AppState.show { close ->
    var pat by remember { mutableStateOf("https://example.com/file_(*).zip") }
    var from by remember { mutableStateOf("1") }
    var to by remember { mutableStateOf("5") }
    PurpleDialog("Batch Download Generator", close, actions = {
        TextButton(close) { Text("Close") }
        AccentButton("Queue Batch", {
            val a = from.toIntOrNull() ?: 1; val b = to.toIntOrNull() ?: a
            if (!pat.contains("(*)") || b < a || b - a > 200) AppState.toast("Check the pattern and range (max 200 files)")
            else { for (n in a..b) AppState.add(pat.replace("(*)", n.toString().padStart(to.trim().length, '0'))); close(); AppState.toast("Queued ${b - a + 1} files") }
        })
    }) {
        Text("Use (*) as the number placeholder, e.g. part_(*).zip", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(pat, { pat = it }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(from, { from = it }, label = { Text("From") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(to, { to = it }, label = { Text("To") }, modifier = Modifier.weight(1f), singleLine = true)
        }
    }
}

fun showTutorial() = AppState.show { close ->
    PurpleDialog("$APP_NAME Quick Guide", close, actions = {
        TextButton(close) { Text("Close") }
        AccentButton("Open Browser", { close(); AppState.inBrowser = true })
    }) {
        Text("1. Tap the globe icon to open the Browser.\n2. Visit a page with a video, song or file.\n3. Tap Grab Media (or tap a file link) to add it to your downloads.\n4. Come back with the download icon in the browser bar.\n\nYou can also tap + and paste any direct link.", lineHeight = 22.sp)
    }
}

fun showAdblock() = AppState.show { close ->
    PurpleDialog("Adblock Hosts & Filters", close) {
        adFilters.forEach { (name, hosts) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text("${hosts.size} hosts", fontSize = 11.sp, color = Color.Gray) }
                Switch(AppState.bool("adf_$name", true), { AppState.set("adf_$name", it) })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Blocked so far: ${AppState.blocked}", fontSize = 12.sp, color = Color.Gray)
    }
}

fun showLinks(title: String, items: List<SavedLink>, onOpen: (String) -> Unit, onDelete: ((SavedLink) -> Unit)? = null) = AppState.show { close ->
    PurpleDialog("$title (${items.size})", close) {
        if (items.isEmpty()) Text("Nothing here yet")
        items.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp).then(Modifier)) {
                    TextButton({ close(); onOpen(l.url) }, contentPadding = PaddingValues(0.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(l.title, maxLines = 1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (l.date.isEmpty()) l.url else "${l.url} • ${l.date}", maxLines = 1, fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
