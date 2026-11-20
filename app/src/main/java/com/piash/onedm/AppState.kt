package com.piash.onedm

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.webkit.MimeTypeMap
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

const val APP_NAME = "MGDM"
const val APP_FULL_NAME = "Motion Graph Download Manager"
const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

enum class Status { QUEUED, DOWNLOADING, PAUSED, FINISHED, ERRORED }

val fileTypes = listOf("Everything", "Torrents", "Compressed", "Documents", "Music", "Videos", "Subtitles", "Photos", "Programs", "Others")

private val extMap = mapOf(
    "Torrents" to listOf("torrent"),
    "Compressed" to listOf("zip", "rar", "7z", "tar", "gz", "xz", "bz2"),
    "Documents" to listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "mobi", "csv"),
    "Music" to listOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus"),
    "Videos" to listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv"),
    "Subtitles" to listOf("srt", "vtt", "ass"),
    "Photos" to listOf("jpg", "jpeg", "png", "gif", "webp", "bmp"),
    "Programs" to listOf("apk", "xapk", "exe", "deb", "msi", "dmg"),
)
val downloadableExts: Set<String> = extMap.values.flatten().toSet() + "iso"

fun typeFromName(name: String): String {
    val e = name.lowercase().substringAfterLast('.', "")
    return extMap.entries.firstOrNull { e in it.value }?.key ?: "Others"
}

fun fileNameFromUrl(url: String): String {
    var n = try { Uri.parse(url).lastPathSegment ?: "" } catch (_: Exception) { "" }
    if (n.isEmpty()) n = "download_${System.currentTimeMillis()}"
    return n.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}

fun fmtBytes(b: Long): String {
    if (b <= 0) return "0 B"
    val u = listOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble(); var i = 0
    while (v >= 1024 && i < 4) { v /= 1024; i++ }
    return if (i < 2) "${v.toInt()} ${u[i]}" else "%.1f %s".format(v, u[i])
}

class DItem(
    val id: Long, val name: String, val url: String, val type: String, val path: String,
    total: Long = -1, done: Long = 0, status: Status = Status.QUEUED,
) {
    var total by mutableLongStateOf(total)
    var done by mutableLongStateOf(done)
    var status by mutableStateOf(status)
    var speed by mutableLongStateOf(0L)
    var error by mutableStateOf<String?>(null)
    var mark = 0L
    var retries = 0
    var job: Job? = null
    @Volatile var conn: HttpURLConnection? = null
    var cookie: String? = null
    var ua: String? = null
    val ratio: Float? get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
}

class SavedLink(val title: String, val url: String, val date: String = "")

val adFilters = linkedMapOf(
    "EasyList Standard" to listOf("doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com", "adnxs.com", "taboola.com", "outbrain.com"),
    "EasyPrivacy Tracker Blocker" to listOf("google-analytics.com", "scorecardresearch.com", "hotjar.com", "mixpanel.com", "segment.io"),
    "AdGuard Mobile Ads Filter" to listOf("ads.yahoo.com", "moatads.com", "pubmatic.com", "rubiconproject.com", "criteo.com", "amazon-adsystem.com"),
    "Malware & Crypto-Miner Hosts" to listOf("coinhive.com", "coin-hive.com", "minero.cc", "popads.net", "propellerads.com", "exoclick.com"),
)

class ToastMsg(val msg: String, val id: Long = System.nanoTime())

object AppState {
    lateinit var app: Application
    private lateinit var prefs: SharedPreferences
    lateinit var dir: File
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val downloads = mutableStateListOf<DItem>()
    val bookmarks = mutableStateListOf<SavedLink>()
    val saved = mutableStateListOf<SavedLink>()
    val history = mutableStateListOf<String>()
    val settings = mutableStateMapOf<String, String>()

    var selectedType by mutableStateOf("Everything")
    var inBrowser by mutableStateOf(false)
    var openDrawer by mutableStateOf(false)
    var sortAsc by mutableStateOf(false)
    var blocked by mutableIntStateOf(0)
    var toastState by mutableStateOf<ToastMsg?>(null)
    var dialog by mutableStateOf<(@Composable (() -> Unit) -> Unit)?>(null)

    private var serviceOn = false

    fun init(a: Application) {
        if (::app.isInitialized) return
        app = a
        prefs = a.getSharedPreferences("1dm", Context.MODE_PRIVATE)
        dir = File(a.getExternalFilesDir(null) ?: a.filesDir, APP_NAME).apply { mkdirs() }
        restore()
        scope.launch {
            var t = 0
            while (true) {
                delay(1000)
                downloads.filter { it.status == Status.DOWNLOADING }.forEach { it.speed = it.done - it.mark; it.mark = it.done }
                updateService()
                if (++t % 5 == 0) save()
            }
        }
    }

    // ---------- settings ----------
    fun bool(k: String, d: Boolean) = settings[k]?.toBooleanStrictOrNull() ?: d
    fun int(k: String, d: Int) = settings[k]?.toIntOrNull() ?: d
    fun str(k: String, d: String) = settings[k] ?: d
    fun set(k: String, v: Any) { settings[k] = v.toString(); prefs.edit().putString("s_$k", v.toString()).apply() }

    val dark get() = bool("dark", false)
    val followSystem get() = bool("followSystem", false)
    val blockAds get() = bool("blockAds", true)
    val captureMedia get() = bool("captureMedia", true)
    private fun limitBps(): Long = if (bool("speedLimit", false) && int("dlLimit", 100) < 100) int("dlLimit", 100) * 50L * 1024 else 0L

    fun hostBlocked(host: String): Boolean {
        for ((name, hosts) in adFilters) {
            if (!bool("adf_$name", true)) continue
            if (hosts.any { host == it || host.endsWith(".$it") }) return true
        }
        return false
    }

    // ---------- persistence ----------
    private fun restore() {
        prefs.all.forEach { (k, v) -> if (k.startsWith("s_") && v is String) settings[k.removePrefix("s_")] = v }
        try {
            val a = JSONArray(prefs.getString("downloads", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                var st = Status.values()[o.getInt("status")]
                if (st == Status.DOWNLOADING || st == Status.QUEUED) st = Status.PAUSED
                downloads.add(DItem(o.getLong("id"), o.getString("name"), o.getString("url"), o.getString("type"), o.getString("path"), o.getLong("total"), o.getLong("done"), st))
            }
            readLinks("bookmarks", bookmarks); readLinks("saved", saved)
            val h = JSONArray(prefs.getString("history", "[]"))
            for (i in 0 until h.length()) history.add(h.getString(i))
        } catch (_: Exception) {}
        if (bookmarks.isEmpty()) bookmarks.add(SavedLink("Google Search", "https://www.google.com"))
    }

    private fun readLinks(key: String, into: MutableList<SavedLink>) {
        val a = JSONArray(prefs.getString(key, "[]"))
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); into.add(SavedLink(o.getString("t"), o.getString("u"), o.optString("d"))) }
    }

    private fun links(l: List<SavedLink>) = JSONArray().apply { l.forEach { put(JSONObject().put("t", it.title).put("u", it.url).put("d", it.date)) } }.toString()

    fun save() {
        val a = JSONArray()
        downloads.toList().forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("type", it.type).put("path", it.path)
                .put("total", it.total).put("done", it.done).put("status", it.status.ordinal))
        }
        prefs.edit().putString("downloads", a.toString()).putString("bookmarks", links(bookmarks)).putString("saved", links(saved))
            .putString("history", JSONArray(history.take(200)).toString()).apply()
    }

    // ---------- UI helpers ----------
    fun toast(m: String) { toastState = ToastMsg(m) }
    fun show(content: @Composable (close: () -> Unit) -> Unit) { dialog = content }
    fun clipText(): String = try {
        val cm = app.getSystemService(ClipboardManager::class.java)
        cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
    } catch (_: Exception) { "" }
    fun copy(text: String) { app.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("1dm", text)) }
    fun share(text: String) {
        val i = Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); app.startActivity(i)
    }
    fun openExternal(url: String): Boolean = try {
        app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Exception) { false }
    fun openFile(d: DItem) {
        try {
            val f = File(d.path)
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", f)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            app.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) { toast("No app can open this file") } catch (_: Exception) { toast("Cannot open file") }
    }
    fun setSort() {
        sortAsc = !sortAsc
        val l = downloads.toList().reversed(); downloads.clear(); downloads.addAll(l)
        toast(if (sortAsc) "Sorted oldest first" else "Sorted newest first")
    }
    fun openSettings() { inBrowser = false; openDrawer = true }

    // ---------- bookmarks / history ----------
    fun addHistory(u: String) { history.remove(u); history.add(0, u); save() }
    fun addBookmark(title: String, url: String) { if (bookmarks.none { it.url == url }) { bookmarks.add(0, SavedLink(title.ifEmpty { url }, url)); save() } }
    fun savePage(title: String, url: String) {
        val c = java.util.Calendar.getInstance()
        saved.add(0, SavedLink(title.ifEmpty { url }, url, "%d/%d/%d %d:%02d".format(c[5], c[2] + 1, c[1], c[11], c[12]))); save()
    }

    // ---------- download engine ----------
    fun add(url: String, name: String? = null, type: String? = null, cookie: String? = null, ua: String? = null): DItem {
        var n = if (name.isNullOrBlank()) fileNameFromUrl(url) else name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val dot = n.lastIndexOf('.')
        val base = if (dot > 0) n.substring(0, dot) else n
        val ext = if (dot > 0) n.substring(dot) else ""
        var path = File(dir, n)
        var k = 1
        while (path.exists() || downloads.any { it.path == path.path }) { n = "$base ($k)$ext"; path = File(dir, n); k++ }
        val d = DItem(System.currentTimeMillis(), n, url, type ?: typeFromName(n), path.path)
        d.cookie = cookie; d.ua = ua
        downloads.add(0, d); save(); pump()
        return d
    }

    private fun onWifi(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val c = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun pump() {
        var slots = int("numDownloads", 3) - downloads.count { it.status == Status.DOWNLOADING }
        for (d in downloads.toList().reversed()) {
            if (slots <= 0) break
            if (d.status == Status.QUEUED) { slots--; start(d) }
        }
        updateService()
    }

    private fun open(d: DItem, have: Long): HttpURLConnection {
        var u = d.url
        var hops = 0
        while (true) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 20000; c.readTimeout = 30000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", d.ua ?: UA)
            c.setRequestProperty("Accept-Encoding", "identity")
            d.cookie?.let { c.setRequestProperty("Cookie", it) }
            if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
            d.conn = c
            val code = c.responseCode
            if (code in listOf(301, 302, 303, 307, 308) && hops < 6) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Bad redirect")
                u = URL(URL(u), loc).toString(); c.disconnect(); hops++; continue
            }
            return c
        }
    }

    private fun start(d: DItem) {
        d.status = Status.DOWNLOADING; d.error = null
        d.job = scope.launch(Dispatchers.IO) {
            try {
                if (bool("wifiOnly", false) && !onWifi()) { d.retries = 99; throw IOException("Waiting for Wifi (Wifi only is on)") }
                val f = File(d.path)
                var have = if (f.exists()) f.length() else 0L
                val c = open(d, have)
                if (c.responseCode >= 400) throw IOException("HTTP ${c.responseCode}")
                if (have > 0 && c.responseCode != 206) have = 0
                val len = c.contentLengthLong
                d.total = if (len > 0) len + have else -1
                d.done = have; d.mark = have
                val limit = limitBps()
                val t0 = System.nanoTime()
                var sent = 0L
                c.inputStream.use { input ->
                    FileOutputStream(f, have > 0).use { out ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            d.done += n; sent += n
                            if (limit > 0) {
                                val need = sent * 1000 / limit
                                val el = (System.nanoTime() - t0) / 1_000_000
                                if (need > el) delay(need - el)
                            }
                        }
                    }
                }
                d.status = Status.FINISHED
                if (d.total < 0) d.total = d.done
                toast("Finished: ${d.name}")
                if (bool("removeAfter", false)) downloads.remove(d)
            } catch (e: Exception) {
                if (d.status != Status.DOWNLOADING) {
                    // paused or removed by the user
                } else if (bool("retry", true) && d.retries < 3) {
                    d.retries++; d.status = Status.QUEUED; delay(2000)
                } else {
                    d.status = Status.ERRORED; d.error = e.message ?: "Network error"
                }
            } finally {
                d.speed = 0
                try { d.conn?.disconnect() } catch (_: Exception) {}
            }
            withContext(NonCancellable + Dispatchers.Main) { save(); pump() }
        }
    }

    fun pause(d: DItem) {
        if (d.status == Status.DOWNLOADING) { d.status = Status.PAUSED; d.job?.cancel(); try { d.conn?.disconnect() } catch (_: Exception) {} }
        else if (d.status == Status.QUEUED) d.status = Status.PAUSED
    }
    fun resume(d: DItem) { if (d.status == Status.PAUSED || d.status == Status.ERRORED) { d.retries = 0; d.status = Status.QUEUED; pump() } }
    fun startAll() { downloads.forEach { if (it.status == Status.PAUSED || it.status == Status.ERRORED) { it.retries = 0; it.status = Status.QUEUED } }; pump() }
    fun pauseAll() = downloads.toList().forEach { pause(it) }
    fun remove(d: DItem, deleteFile: Boolean = true) {
        d.status = Status.PAUSED; d.job?.cancel(); try { d.conn?.disconnect() } catch (_: Exception) {}
        downloads.remove(d)
        if (deleteFile) scope.launch(Dispatchers.IO) { try { File(d.path).delete() } catch (_: Exception) {} }
        save()
    }
    fun clearFinished() { downloads.removeAll { it.status == Status.FINISHED }; save() }

    fun usedBytes(): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    // ---------- background service ----------
    fun notifText(): String {
        val act = downloads.filter { it.status == Status.DOWNLOADING }
        val speed = act.sumOf { it.speed }
        return if (act.isEmpty()) "Waiting to start…" else "${act.size} downloading • ${fmtBytes(speed)}/s"
    }

    private fun updateService() {
        val active = downloads.count { it.status == Status.DOWNLOADING || it.status == Status.QUEUED }
        try {
            if (active > 0) {
                if (!serviceOn) { ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java)); serviceOn = true }
                else app.getSystemService(NotificationManager::class.java).notify(1, DownloadService.build(app, notifText()))
            } else if (serviceOn) { app.stopService(Intent(app, DownloadService::class.java)); serviceOn = false }
        } catch (_: Exception) {}
    }
}

class DownloadService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, "Downloads", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val n = build(this, "Starting…")
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(1, n)
        return START_NOT_STICKY
    }

    companion object {
        const val CH = "downloads"
        fun build(ctx: Context, text: String): Notification {
            val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            return Notification.Builder(ctx, CH)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(APP_NAME).setContentText(text)
                .setOngoing(true).setContentIntent(pi).build()
        }
    }
}
