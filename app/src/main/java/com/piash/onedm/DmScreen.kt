package com.piash.onedm

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private fun inTab(d: DItem, t: String) = when (t) {
    "DOWNLOADING" -> d.status == Status.QUEUED || d.status == Status.DOWNLOADING || d.status == Status.PAUSED
    "FINISHED" -> d.status == Status.FINISHED
    "ERRORED" -> d.status == Status.ERRORED
    else -> true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmScreen() {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("ALL") }
    var searching by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    val s = AppState

    LaunchedEffect(s.openDrawer) { if (s.openDrawer) { drawer.open(); s.openDrawer = false } }

    val shown = s.downloads.filter {
        inTab(it, tab) && (s.selectedType == "Everything" || it.type == s.selectedType) && (q.isEmpty() || it.name.lowercase().contains(q))
    }

    ModalNavigationDrawer(drawerState = drawer, drawerContent = { AppDrawer { scope.launch { drawer.close() } } }) {
        Scaffold(
            topBar = {
                Column(Modifier.background(Purple)) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(APP_NAME, fontWeight = FontWeight.SemiBold)
                                if (s.selectedType != "Everything")
                                    Box(Modifier.padding(start = 8.dp).background(Color.White.copy(0.2f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text(s.selectedType, fontSize = 11.sp, color = Color.White)
                                    }
                            }
                        },
                        navigationIcon = { IconButton({ scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, null) } },
                        actions = {
                            IconButton({ searching = !searching }) { Icon(Icons.Default.Search, null) }
                            IconButton({ s.setSort() }) { Icon(Icons.Default.Sort, null) }
                            IconButton({ s.inBrowser = true }) { Icon(Icons.Default.Public, null) }
                            IconButton({ showMagnetDialog() }) { Text("U", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                            Box {
                                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, null) }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem({ Text("Start all") }, { menu = false; s.startAll() }, leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = Accent) })
                                    DropdownMenuItem({ Text("Pause all") }, { menu = false; s.pauseAll() }, leadingIcon = { Icon(Icons.Default.Pause, null, tint = Accent) })
                                    DropdownMenuItem({ Text("Remove finished") }, { menu = false; s.clearFinished() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) })
                                    DropdownMenuItem({ Text("Add from clipboard") }, { menu = false; showAddDialog() }, leadingIcon = { Icon(Icons.Default.Link, null, tint = Accent) })
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
                    )
                    if (searching) Row(Modifier.fillMaxWidth().background(Color(0xFF4527A0)).padding(horizontal = 12.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(q, { q = it.lowercase() }, singleLine = true, cursorBrush = SolidColor(Color.White),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp), modifier = Modifier.weight(1f),
                            decorationBox = { inner -> if (q.isEmpty()) Text("Filter downloads by name...", color = Color.White.copy(0.6f)); inner() })
                        IconButton({ searching = false; q = "" }) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("ALL", "DOWNLOADING", "FINISHED", "ERRORED").forEach { t ->
                            val on = tab == t
                            Box(Modifier.clickable { tab = t }.padding(horizontal = 16.dp).height(46.dp), contentAlignment = Alignment.Center) {
                                Text("$t (${s.downloads.count { inTab(it, t) }})", color = if (on) Color.White else Color.White.copy(0.65f), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp)
                                if (on) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.White))
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton({ showAddDialog() }, containerColor = Color(0xFF673AB7), contentColor = Color.White, shape = CircleShape) { Icon(Icons.Default.Add, null, modifier = Modifier.size(28.dp)) }
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                UpdateBanner()
                if (banner) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tip: open the Browser (globe icon) and tap \"Grab Media\" to download videos and files from any page.", color = Color(0xFFE53935), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton({ banner = false }) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
                }
                if (shown.isEmpty()) EmptyState()
                else LazyColumn(contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown, key = { it.id }) { DCard(it) }
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    Surface(Modifier.padding(bottom = 14.dp).rotate(-19f).clickable(onClick = onClick), color = Accent, shape = RoundedCornerShape(4.dp), shadowElevation = 3.dp) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxSize().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Pill("TIPS") { info("$APP_NAME Pro Tips", "1. Open the Browser (globe icon) to detect videos & audio on a page.\n2. Tap the + button and paste any direct link.\n3. Pause keeps the partial file; Resume continues from there.") }
        Pill("HELP") { info("Help & Troubleshooting", "• Downloads waiting? Check Wifi only in Settings > Options.\n• Too many at once? Lower \"Number of downloads\".\n• Need torrents? Tap the U icon to open a magnet link in a torrent app.") }
        Pill("WATCH TUTORIAL") { showTutorial() }
    }
}

@Composable
private fun DCard(d: DItem) {
    val fin = d.status == Status.FINISHED
    val run = d.status == Status.DOWNLOADING
    val col = when { fin -> Color(0xFF43A047); run -> Accent; d.status == Status.ERRORED -> Color.Red; else -> Color(0xFFF9A825) }
    val label = if (run) "${fmtBytes(d.speed)}/s" else if (d.status == Status.QUEUED) "QUEUED" else d.status.name
    Card(Modifier.fillMaxWidth().clickable(enabled = fin) { AppState.openFile(d) }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(Accent.copy(0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(typeIcon(d.type), null, tint = Accent) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(buildAnnotatedString {
                        append("${fmtBytes(d.done)} / ${if (d.total > 0) fmtBytes(d.total) else "?"}  •  ")
                        withStyle(SpanStyle(color = col, fontWeight = FontWeight.Bold)) { append(label) }
                    }, fontSize = 11.sp, color = Color.Gray)
                    if (d.status == Status.ERRORED && d.error != null) Text(d.error!!, fontSize = 10.sp, color = Color.Red, maxLines = 2)
                }
                if (!fin) IconButton({ if (run || d.status == Status.QUEUED) AppState.pause(d) else AppState.resume(d) }) {
                    Icon(if (run || d.status == Status.QUEUED) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Accent)
                } else IconButton({ AppState.openFile(d) }) { Icon(Icons.Default.OpenInNew, null, tint = Color(0xFF43A047)) }
                IconButton({ confirmDelete(d) }) { Icon(Icons.Default.Delete, null, tint = Color.Gray) }
            }
            Spacer(Modifier.height(6.dp))
            val r = if (fin) 1f else d.ratio
            val m = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            if (r != null) LinearProgressIndicator({ r }, m, color = if (fin) Color(0xFF43A047) else Accent, trackColor = Color.Gray.copy(0.3f))
            else if (run) LinearProgressIndicator(m, color = Accent, trackColor = Color.Gray.copy(0.3f))
            else LinearProgressIndicator({ 0f }, m, trackColor = Color.Gray.copy(0.3f))
        }
    }
}

private fun confirmDelete(d: DItem) {
    if (d.status != Status.FINISHED) { AppState.remove(d); return }
    AppState.show { close ->
        PurpleDialog("Remove download", close, actions = {
            TextButton({ close(); AppState.remove(d, false) }) { Text("List only") }
            TextButton({ close(); AppState.remove(d) }) { Text("Delete file too", color = Color.Red) }
        }) { Text(d.name) }
    }
}
