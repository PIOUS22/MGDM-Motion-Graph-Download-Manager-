package com.piash.onedm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import java.io.File

@Composable
private fun Section(title: String, pad: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(true) }
    Column {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp, 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
        if (open) Column(Modifier.padding(horizontal = if (pad) 16.dp else 0.dp).padding(bottom = 12.dp), content = content)
        HorizontalDivider()
    }
}

@Composable
private fun Sw(label: String, key: String, def: Boolean, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Color.Unspecified else Color.Gray)
        Switch(AppState.bool(key, def), { AppState.set(key, it) }, enabled = enabled)
    }
}

@Composable
private fun Sl(label: String, v: Float, range: ClosedFloatingPointRange<Float>, steps: Int, enabled: Boolean = true, on: (Float) -> Unit) {
    Column {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Color.Unspecified else Color.Gray)
        Slider(v, on, valueRange = range, steps = steps, enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF799CF0), activeTrackColor = Color(0xFF799CF0), inactiveTrackColor = Color(0xFF616161)))
    }
}

@Composable
private fun Lnk(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(14.dp)); Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Pick(label: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Box {
            TextButton({ open = true }) { Text(value); Icon(Icons.Default.ArrowDropDown, null) }
            DropdownMenu(open, { open = false }) { options.forEach { o -> DropdownMenuItem({ Text(o) }, { onPick(o); open = false }) } }
        }
    }
}

@Composable
fun AppDrawer(close: () -> Unit) {
    val s = AppState
    ModalDrawerSheet(Modifier.width(300.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp, 8.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, null); Spacer(Modifier.width(12.dp))
            Text("Settings", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            IconButton(close) { Icon(Icons.Default.MenuOpen, null) }
        }
        HorizontalDivider()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Section("TYPES", pad = false) {
                fileTypes.forEach { t ->
                    val on = s.selectedType == t
                    Row(Modifier.fillMaxWidth().clickable { s.selectedType = t; close() }
                        .then(if (on) Modifier.padding(0.dp) else Modifier).padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(typeIcon(t), null, tint = if (on) Accent else Color.Gray, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(16.dp))
                        Text(t, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Accent else Color.Unspecified)
                    }
                }
            }
            Section("DOWNLOAD") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Download Location", fontWeight = FontWeight.SemiBold); Text(s.dir.path, fontSize = 11.sp, color = Color.Gray) }
                    IconButton({
                        try { val f = File(s.dir, ".write_test"); f.writeText("ok"); f.delete(); s.toast("Storage path verified: Writable") } catch (_: Exception) { s.toast("Storage path is NOT writable") }
                    }) { Icon(Icons.Default.Visibility, null) }
                }
                Sl("Number of downloads: ${s.int("numDownloads", 3)}", s.int("numDownloads", 3).toFloat(), 1f..10f, 8) { s.set("numDownloads", it.toInt()) }
                Sl("Number of parts: ${s.int("numParts", 8)}", s.int("numParts", 8).toFloat(), 1f..16f, 14) { s.set("numParts", it.toInt()) }
                val lim = s.bool("speedLimit", false)
                Sw("Enable speed limit", "speedLimit", false)
                Sw("Apply speed limit to all", "speedAll", false, lim)
                val dl = s.int("dlLimit", 100); val ul = s.int("ulLimit", 100)
                Sl("Download speed limit: ${if (dl == 100) "MAX" else "${dl * 50} kB/s"}", dl.toFloat(), 1f..100f, 98, lim) { s.set("dlLimit", it.toInt()) }
                Sl("Upload speed limit: ${if (ul == 100) "MAX" else "${ul * 25} kB/s"}", ul.toFloat(), 1f..100f, 98, lim) { s.set("ulLimit", it.toInt()) }
                Sw("Smart download", "smart", false)
                Sw("Switch off Wifi after all downloads are finished or errored", "wifiOffAll", false)
                Sw("Switch off Wifi only after all downloads are finished", "wifiOffFin", false)
                Sw("Remove download links from list after downloads are finished (Files will not be deleted)", "removeAfter", false)
            }
            Section("OPTIONS") {
                Sw("Wifi only", "wifiOnly", false)
                val bat = s.int("batteryStop", 0)
                Sl("Stop downloads if battery below: ${if (bat == 0) "Off" else "$bat%"}", bat.toFloat(), 0f..50f, 9) { s.set("batteryStop", it.toInt()) }
                Sw("Use advance download method", "advance", true)
                Sw("Disable cataloging", "noCatalog", false)
                Sw("Use proxy", "proxy", false)
                Sw("Disable torrent support", "noTorrent", false)
                Sw("Seed finished torrents", "seed", false)
                Sw("Sequential download", "sequential", true)
                Sw("Make folder view the default view for torrent file selection", "folderView", false)
                Sw("Retry", "retry", true)
                Sw("Create empty file", "emptyFile", true)
                Sw("Acquire wake lock", "wakeLock", true)
                Sw("Acquire wifi lock", "wifiLock", true)
                Sw("Auto restart non resumable links", "autoRestart", false)
            }
            Section("NOTIFICATIONS") {
                Sw("Notifications", "notif", true); Sw("Play sound", "sound", false); Sw("Vibrate", "vibrate", false); Sw("Show notification window", "notifWin", false)
            }
            Section("APP") {
                Sw("Follow System Theme if supported by device", "followSystem", false)
                Pick("App Theme", if (s.dark) "Dark" else "Light", listOf("Light", "Dark")) { s.set("dark", it == "Dark"); s.set("followSystem", false) }
                Pick("Language", s.str("lang", "English"), listOf("English", "Spanish", "Hindi", "Bengali", "Arabic")) { s.set("lang", it); s.toast("Language saved (screens are English only for now)") }
                Lnk(Icons.Default.Share, "Share the app") { s.share("Try $APP_FULL_NAME - download manager & browser") }
                Lnk(Icons.Default.Public, "Manage adblock hosts & filters") { close(); showAdblock() }
                Lnk(Icons.Default.BackHand, "Grabber") { close(); s.inBrowser = true; Browser.pendingGrab = true }
                Lnk(Icons.Default.ArrowCircleDown, "Batch download") { close(); showBatchDialog() }
                Lnk(Icons.Default.SmartDisplay, "Tutorial video") { close(); showTutorial() }
                Lnk(Icons.Default.HelpOutline, "FAQs") {
                    close()
                    info("Frequently Asked Questions", "Q: Can I pause and resume?\nA: Yes. Pause keeps the partial file and resumes from where it stopped if the server supports it.\n\nQ: How do I grab a video?\nA: Open the page in the Browser and tap the Grab Media button.\n\nQ: Where are files saved?\nA: ${s.dir.path}")
                }
                Lnk(Icons.Default.Inventory2, "Saved pages") { close(); s.inBrowser = true; Browser.openSaved() }
                Lnk(Icons.Default.Explore, "Browser") { close(); s.inBrowser = true }
                Lnk(Icons.Default.Storage, "Storage info") { close(); info("Storage Info", "${s.dir.path}\n\nUsed by $APP_NAME downloads: ${fmtBytes(s.usedBytes())}") }
                Lnk(Icons.Default.PowerSettingsNew, "Exit") { android.os.Process.killProcess(android.os.Process.myPid()) }
            }
        }
        Surface(color = Olive, modifier = Modifier.fillMaxWidth().clickable { close(); info("About $APP_NAME", "$APP_FULL_NAME ($APP_NAME)\nDownload manager & browser.\nFiles are saved to:\n${s.dir.path}") }) {
            Row(Modifier.navigationBarsPadding().padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = Color.White); Spacer(Modifier.width(12.dp))
                Text("About", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
