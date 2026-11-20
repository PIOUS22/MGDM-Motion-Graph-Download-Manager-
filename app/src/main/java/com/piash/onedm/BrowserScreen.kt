package com.piash.onedm

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONTokener
import java.io.ByteArrayInputStream

private const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
private val mediaRe = Regex("\\.(mp4|m3u8|mp3|m4a|webm|mkv|flac|wav|mpd)$")

private const val SCAN_JS = """(function(){var r=[];
function add(u,t){if(!u||u.indexOf('blob:')==0||u.indexOf('data:')==0)return;try{u=new URL(u,location.href).href}catch(e){return}r.push([u,t])}
document.querySelectorAll('video,audio').forEach(function(e){var t=e.tagName=='VIDEO'?'Videos':'Music';add(e.currentSrc||e.src,t);e.querySelectorAll('source').forEach(function(s){add(s.src,t)})});
document.querySelectorAll('a[href]').forEach(function(a){if(/\.(mp4|mkv|webm|mp3|m4a|flac|wav|zip|rar|7z|apk|pdf|iso|torrent|srt|vtt)(\?|${'$'})/i.test(a.href))add(a.href,'')});
var seen={};return JSON.stringify(r.filter(function(x){if(seen[x[0]])return false;seen[x[0]]=1;return true}))})()"""

private const val READER_JS = """(function(){var id='__reader';var o=document.getElementById(id);if(o){o.remove();return}
var s=document.createElement('style');s.id=id;
s.textContent='header,nav,aside,footer,iframe,[class*=sidebar],[class*=advert]{display:none!important}body{max-width:700px!important;margin:0 auto!important;padding:16px!important;font-size:19px!important;line-height:1.7!important;background:#fafafa!important;color:#222!important}';
document.head.appendChild(s)})()"""

private var tabCounter = 0

@SuppressLint("SetJavaScriptEnabled")
class BTab(val incognito: Boolean, js: Boolean, desktop: Boolean) {
    val id = ++tabCounter
    var url by mutableStateOf("")
    var title by mutableStateOf("New Tab")
    var progress by mutableIntStateOf(100)
    var home by mutableStateOf(true)
    val media = mutableStateListOf<Pair<String, String>>()
    val web = WebView(AppState.app)

    init {
        web.settings.apply {
            javaScriptEnabled = js
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = if (desktop) DESKTOP_UA else null
        }
        web.setDownloadListener { u, ua, cd, mime, _ ->
            askDownload(u, URLUtil.guessFileName(u, cd, mime), CookieManager.getInstance().getCookie(u), ua)
        }
        web.setFindListener { active, total, _ -> Browser.findCount = if (total == 0) "0/0" else "${active + 1}/$total" }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) { progress = p }
            override fun onReceivedTitle(view: WebView, t: String?) { if (!t.isNullOrBlank()) title = t }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url
                if (u.scheme == "magnet") { AppState.openExternal(u.toString()); return true }
                if (u.scheme != "http" && u.scheme != "https") return true
                val ext = (u.path ?: "").lowercase().substringAfterLast('.', "")
                if (r.isForMainFrame && ext in downloadableExts) {
                    val s = u.toString()
                    askDownload(s, fileNameFromUrl(s), CookieManager.getInstance().getCookie(s), v.settings.userAgentString)
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                val u = r.url
                if (AppState.blockAds && AppState.hostBlocked(u.host ?: "")) {
                    v.post { AppState.blocked++ }
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                val path = (u.path ?: "").lowercase()
                if (AppState.captureMedia && mediaRe.containsMatchIn(path)) {
                    val s = u.toString(); val ty = typeFromName(path)
                    v.post { if (media.none { it.first == s }) media.add(s to ty) }
                }
                return null
            }

            override fun onPageStarted(v: WebView, u: String, f: Bitmap?) { url = u; progress = 5; media.clear() }
            override fun onPageFinished(v: WebView, u: String) {
                url = u; progress = 100; title = v.title ?: u
                if (!incognito) AppState.addHistory(u)
                scan()
            }
        }
    }

    fun scan() {
        web.evaluateJavascript(SCAN_JS) { res ->
            try {
                val s = JSONTokener(res).nextValue() as? String ?: return@evaluateJavascript
                val a = JSONArray(s)
                for (i in 0 until a.length()) {
                    val p = a.getJSONArray(i); val u = p.getString(0)
                    val t = p.getString(1).ifEmpty { typeFromName(fileNameFromUrl(u)) }
                    if (media.none { it.first == u }) media.add(u to t)
                }
            } catch (_: Exception) {}
        }
    }
}

object Browser {
    val tabs = mutableStateListOf<BTab>()
    var cur by mutableIntStateOf(0)
    var desktop by mutableStateOf(false)
    var js by mutableStateOf(true)
    var reader by mutableStateOf(false)
    var findOpen by mutableStateOf(false)
    var findCount by mutableStateOf("")
    var pendingGrab by mutableStateOf(false)
    val t get() = tabs[cur]

    init { tabs.add(BTab(false, true, false)) }

    fun newTab(inc: Boolean) {
        tabs.add(BTab(inc, js, desktop)); cur = tabs.size - 1
        AppState.toast(if (inc) "Incognito tab (history not recorded)" else "New tab opened")
    }

    fun go(q0: String) {
        val q = q0.trim()
        if (q.isEmpty()) return
        val isUrl = q.startsWith("http://") || q.startsWith("https://") || (q.contains('.') && !q.contains(' '))
        val url = if (isUrl) (if (q.startsWith("http")) q else "https://$q") else "https://www.google.com/search?q=" + java.net.URLEncoder.encode(q, "UTF-8")
        t.home = false; t.url = url; t.media.clear(); t.web.loadUrl(url)
    }

    fun goHome() { t.home = true; t.url = ""; t.media.clear() }

    fun back() {
        when {
            findOpen -> { findOpen = false; t.web.clearMatches() }
            !t.home && t.web.canGoBack() -> t.web.goBack()
            !t.home -> goHome()
            else -> AppState.inBrowser = false
        }
    }

    fun refresh() {
        if (t.home) { AppState.toast("Home refreshed"); return }
        if (t.progress < 100) t.web.stopLoading() else { t.web.reload(); AppState.toast("Page refreshed") }
    }

    val curUrl get() = if (t.home) "https://www.google.com" else t.url

    fun openGrabber() {
        val tab = t
        tab.scan()
        AppState.show { close ->
            PurpleDialog("Media & Resource Grabber", close, icon = Icons.Default.BackHand) {
                if (tab.media.isEmpty()) Text("Nothing detected on this page yet. Open a page with a video, audio or file links, play the media, then try again.", fontSize = 13.sp)
                tab.media.toList().forEach { (u, ty) ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).border(1.dp, Color.Gray.copy(0.4f), RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fileNameFromUrl(u), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(u, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp, color = Color.Gray)
                        }
                        Spacer(Modifier.width(8.dp))
                        AccentButton("Grab", {
                            val n = fileNameFromUrl(u)
                            AppState.add(u, n, ty, CookieManager.getInstance().getCookie(u), tab.web.settings.userAgentString)
                            AppState.toast("Added to $APP_NAME: $n")
                        })
                    }
                }
            }
        }
    }

    fun openSaved() = showLinks("Saved Pages", AppState.saved, { AppState.inBrowser = true; go(it) })

    fun showTabs() = AppState.show { close ->
        PurpleDialog("Open Tabs (${tabs.size})", close, actions = {
            AccentButton("New Tab", { close(); newTab(false) })
            Spacer(Modifier.width(8.dp))
            AccentButton("Incognito", { close(); newTab(true) }, Color.DarkGray)
        }) {
            tabs.toList().forEachIndexed { i, tab ->
                Row(Modifier.fillMaxWidth().clickable { cur = i; close() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (tab.incognito) Icons.Default.VisibilityOff else Icons.Default.Tab, null, tint = if (i == cur) Accent else Color.Gray)
                    Spacer(Modifier.width(12.dp))
                    Text(if (tab.home) "New Tab" else tab.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (i == cur) FontWeight.Bold else FontWeight.Normal)
                    IconButton({
                        if (tabs.size == 1) AppState.toast("Last tab can't be closed")
                        else { val gone = tabs[i]; tabs.removeAt(i); gone.web.destroy(); if (cur >= tabs.size) cur = tabs.size - 1 }
                    }) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }

    private fun decode(r: String?): String = try { JSONTokener(r).nextValue() as? String ?: "" } catch (_: Exception) { "" }

    fun showSource() = t.web.evaluateJavascript("document.documentElement.outerHTML") { r ->
        val src = decode(r)
        AppState.show { close ->
            PurpleDialog("Page Source", close, actions = { TextButton({ AppState.copy(src); AppState.toast("HTML source copied") }) { Text("Copy") } }) {
                SelectionContainer {
                    Text(if (src.length > 20000) src.take(20000) + "\n…(truncated)" else src, color = Color(0xFF69F0AE), fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.background(Color.Black).padding(8.dp))
                }
            }
        }
    }

    fun clearData() {
        t.web.clearCache(true)
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        AppState.history.clear(); AppState.save()
        AppState.toast("Cache, cookies & history cleared")
    }

    fun clipTabs() {
        val urls = AppState.clipText().split(Regex("\\s+")).filter { it.startsWith("http") }.take(10)
        if (urls.isEmpty()) { AppState.toast("No links in clipboard"); return }
        urls.forEach { newTab(false); go(it) }
    }

    fun setDesktop(v: Boolean) {
        desktop = v
        tabs.forEach { it.web.settings.userAgentString = if (v) DESKTOP_UA else null }
        if (!t.home) t.web.reload()
    }

    fun setJs(v: Boolean) {
        js = v
        tabs.forEach { it.web.settings.javaScriptEnabled = v }
        if (!t.home) t.web.reload()
    }

    fun print() {
        try {
            val pm = AppState.app.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print(t.title, t.web.createPrintDocumentAdapter(t.title), PrintAttributes.Builder().build())
        } catch (_: Exception) { AppState.toast("Printing is not available") }
    }
}

private class MI(val label: String, val icon: ImageVector, val checked: Boolean? = null, val act: () -> Unit)

@Composable
fun BrowserScreen() {
    val B = Browser
    val t = B.t
    BackHandler(true) { B.back() }
    LaunchedEffect(B.pendingGrab) { if (B.pendingGrab) { B.pendingGrab = false; B.openGrabber() } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BrowserBar()
        if (t.progress < 100 && !t.home) LinearProgressIndicator({ t.progress / 100f }, Modifier.fillMaxWidth().height(2.dp), color = Accent)
        if (B.findOpen) FindBar()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (t.home) HomePage()
            else key(t.id) {
                AndroidView(factory = { (t.web.parent as? ViewGroup)?.removeView(t.web); t.web }, modifier = Modifier.fillMaxSize())
            }
            if (AppState.captureMedia && !t.home && t.media.isNotEmpty())
                ExtendedFloatingActionButton({ B.openGrabber() }, Modifier.align(Alignment.BottomEnd).padding(16.dp), containerColor = Purple, contentColor = Color.White,
                    icon = { Icon(Icons.Default.CloudDownload, null) }, text = { Text("Grab Media (${t.media.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
        }
    }
}

@Composable
private fun BrowserBar() {
    val B = Browser
    val t = B.t
    val focus = LocalFocusManager.current
    var menu by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var text by remember(t.id, t.url, t.home) { mutableStateOf(if (t.home) "" else t.url) }

    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).border(2.dp, Color.Gray, RoundedCornerShape(4.dp)).clickable { B.showTabs() }, contentAlignment = Alignment.Center) {
            Text("${B.tabs.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f).background(Color.Gray.copy(0.18f), RoundedCornerShape(24.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (t.incognito) Icon(Icons.Default.VisibilityOff, null, tint = Accent, modifier = Modifier.size(16.dp).padding(end = 4.dp))
            BasicTextField(
                text, { text = it }, singleLine = true,
                modifier = Modifier.weight(1f).padding(vertical = 11.dp).onFocusChanged { focused = it.isFocused },
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                keyboardActions = KeyboardActions(onGo = { B.go(text); focus.clearFocus() }),
                decorationBox = { inner -> if (text.isEmpty()) Text("Search", color = Color.Gray); inner() },
            )
            Icon(if (t.progress < 100 && !t.home) Icons.Default.Close else Icons.Default.Refresh, null, modifier = Modifier.size(18.dp).clickable { B.refresh() })
        }
        IconButton({ AppState.inBrowser = false }) {
            BadgedBox(badge = { if (AppState.downloads.isNotEmpty()) Badge(containerColor = Accent) { Text("${AppState.downloads.size}") } }) { Icon(Icons.Default.Download, null) }
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, null) }
            DropdownMenu(menu, { menu = false }, Modifier.width(280.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton({ menu = false; B.back() }) { Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(18.dp)) }
                    IconButton({ menu = false; if (t.web.canGoForward()) t.web.goForward() }) { Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp)) }
                    IconButton({ menu = false; AppState.addBookmark(t.title, B.curUrl); AppState.toast("Bookmarked") }) { Icon(Icons.Default.StarBorder, null, modifier = Modifier.size(18.dp)) }
                    IconButton({ menu = false; B.refresh() }) { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp)) }
                    IconButton({ menu = false; AppState.openSettings() }) { Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp)) }
                    IconButton({ menu = false; B.openGrabber() }) { Icon(Icons.Default.Dns, null, modifier = Modifier.size(18.dp)) }
                }
                HorizontalDivider()
                val items = listOf(
                    MI("New Tab", Icons.Default.Add) { B.newTab(false) },
                    MI("Switch to Incognito", Icons.Default.VisibilityOff) { B.newTab(true) },
                    MI("Return to $APP_NAME", Icons.Default.CloudDownload) { AppState.inBrowser = false },
                    MI("Exit Browser", Icons.Default.ExitToApp) { AppState.inBrowser = false },
                    MI("Block Ads", Icons.Default.Shield, AppState.blockAds) { AppState.set("blockAds", !AppState.blockAds) },
                    MI("Capture audio/video", Icons.Default.TravelExplore, AppState.captureMedia) { AppState.set("captureMedia", !AppState.captureMedia) },
                    MI("View Page Resources", Icons.Default.Layers) { B.openGrabber() },
                    MI("Clone Tab", Icons.Default.ContentCopy) { val u = t.url; B.newTab(t.incognito); if (u.isNotEmpty()) B.go(u) },
                    MI("Grabber", Icons.Default.BackHand) { B.openGrabber() },
                    MI("History", Icons.Default.History) { showLinks("History", AppState.history.map { SavedLink(it, it) }, { B.go(it) }) },
                    MI("Open tab for all links in clipboard", Icons.Default.ContentPasteGo) { B.clipTabs() },
                    MI("Clear browsing data", Icons.Default.CleaningServices) { B.clearData() },
                    MI("View page source", Icons.Default.Code) { B.showSource() },
                    MI("Save page", Icons.Default.Save) { AppState.savePage(t.title, B.curUrl); AppState.toast("Page link saved") },
                    MI("Saved pages", Icons.Default.Inventory2) { B.openSaved() },
                    MI("Find in Page", Icons.Default.FindInPage) { B.findOpen = true },
                    MI("Print page", Icons.Default.Print) { B.print() },
                    MI("Copy link", Icons.Default.Link) { AppState.copy(B.curUrl); AppState.toast("Link copied to clipboard") },
                    MI("Copy all tab links", Icons.Default.ContentCopy) { AppState.copy(B.tabs.joinToString("\n") { if (it.home) "https://www.google.com" else it.url }); AppState.toast("Copied ${B.tabs.size} link(s)") },
                    MI("Bookmarks", Icons.Default.StarBorder) { showLinks("Bookmarks", AppState.bookmarks, { B.go(it) }) },
                    MI("Add Bookmark", Icons.Default.Star) { AppState.addBookmark(t.title, B.curUrl); AppState.toast("Bookmarked") },
                    MI("Bookmark all tabs", Icons.Default.BookmarkAdd) { B.tabs.forEach { if (!it.home) AppState.addBookmark(it.title, it.url) }; AppState.toast("Bookmarked all open tabs") },
                    MI("Share link", Icons.Default.Share) { AppState.share(B.curUrl) },
                    MI("Dark mode", Icons.Default.DarkMode, AppState.dark) { AppState.set("dark", !AppState.dark); AppState.set("followSystem", false) },
                    MI("Request desktop site", Icons.Default.DesktopWindows, B.desktop) { B.setDesktop(!B.desktop) },
                    MI("Enable JavaScript", Icons.Default.Javascript, B.js) { B.setJs(!B.js) },
                    MI("Reader Mode", Icons.Default.ChromeReaderMode, B.reader) { B.reader = !B.reader; t.web.evaluateJavascript(READER_JS, null); AppState.toast(if (B.reader) "Reader Mode ON" else "Reader Mode OFF") },
                    MI("Manage adblock hosts & filters", Icons.Default.Public) { showAdblock() },
                    MI("Settings", Icons.Default.Settings) { AppState.openSettings() },
                    MI("About", Icons.Default.Info) { info("About $APP_NAME Browser", "$APP_FULL_NAME ($APP_NAME)\nBrowser & download manager.\nFiles are saved to:\n${AppState.dir.path}") },
                )
                items.forEach { it0 ->
                    DropdownMenuItem(
                        text = { Text(it0.label, fontSize = 14.sp) },
                        leadingIcon = { Icon(it0.icon, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                        trailingIcon = { if (it0.checked != null) Checkbox(it0.checked, null) },
                        onClick = { if (it0.checked == null) menu = false; it0.act() },
                    )
                }
            }
        }
    }
}

@Composable
private fun FindBar() {
    val B = Browser
    var q by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
        BasicTextField(q, { q = it; if (it.isEmpty()) B.t.web.clearMatches() else B.t.web.findAllAsync(it) }, singleLine = true, modifier = Modifier.weight(1f),
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp), cursorBrush = SolidColor(Accent),
            decorationBox = { inner -> if (q.isEmpty()) Text("Find in page...", color = Color.Gray); inner() })
        Text(B.findCount, fontSize = 12.sp, color = Color.Gray)
        IconButton({ B.t.web.findNext(false) }) { Icon(Icons.Default.KeyboardArrowUp, null) }
        IconButton({ B.t.web.findNext(true) }) { Icon(Icons.Default.KeyboardArrowDown, null) }
        IconButton({ B.findOpen = false; B.t.web.clearMatches() }) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun HomePage() {
    var q by remember { mutableStateOf("") }
    val gc = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF4285F4), Color(0xFF34A853), Color(0xFFEA4335))
    @Composable fun Quick(label: String, icon: ImageVector, col: Color, url: String) {
        Column(Modifier.clickable { Browser.go(url) }, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(46.dp).background(col.copy(0.15f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = col) }
            Spacer(Modifier.height(6.dp)); Text(label, fontSize = 11.sp)
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(buildAnnotatedString { "Google".forEachIndexed { i, c -> withStyle(SpanStyle(color = gc[i])) { append(c) } } }, fontSize = 52.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        Surface(shape = RoundedCornerShape(30.dp), shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(q, { q = it }, singleLine = true, modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp), cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { Browser.go(q) }),
                    decorationBox = { inner -> if (q.isEmpty()) Text("Search...", color = Color.Gray); inner() })
                Icon(Icons.Default.Search, null, modifier = Modifier.clickable { Browser.go(q) })
            }
        }
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Quick("Videos", Icons.Default.Movie, Accent, "https://archive.org/details/movies")
            Quick("Music", Icons.Default.MusicNote, Color(0xFF2196F3), "https://archive.org/details/audio")
            Quick("ISO Files", Icons.Default.Album, Color(0xFFF9A825), "https://releases.ubuntu.com")
            Quick("E-Books", Icons.Default.MenuBook, Color(0xFF43A047), "https://www.gutenberg.org")
        }
    }
}
