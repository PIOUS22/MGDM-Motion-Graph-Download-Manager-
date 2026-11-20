package com.piash.onedm

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateInfo(val code: Long, val name: String, val apkUrl: String, val notes: String)

object Updater {
    /** Put your own link here (or use Settings > Set update URL inside the app). */
    const val DEFAULT_URL = "https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/version.json"

    var info by mutableStateOf<UpdateInfo?>(null)
    var item by mutableStateOf<DItem?>(null)
    var hidden by mutableStateOf(false)

    private fun pkg() = AppState.app.packageManager.getPackageInfo(AppState.app.packageName, 0)
    fun currentCode(): Long = pkg().longVersionCode
    fun currentName(): String = pkg().versionName ?: ""

    fun check(manual: Boolean) {
        AppState.scope.launch(Dispatchers.IO) {
            val url = AppState.str("updateUrl", DEFAULT_URL)
            if (url.contains("YOUR_USER")) {
                if (manual) AppState.toast("Set your update URL first (Settings > Set update URL)")
                return@launch
            }
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 15000; c.useCaches = false
                val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val code = o.getLong("versionCode")
                if (code > currentCode()) {
                    info = UpdateInfo(code, o.optString("versionName", code.toString()), o.getString("apkUrl"), o.optString("notes"))
                    hidden = false
                    if (manual) AppState.toast("New version available")
                } else {
                    info = null
                    if (manual) AppState.toast("You're up to date (v${currentName()})")
                }
            } catch (e: Exception) {
                if (manual) AppState.toast("Update check failed: ${e.message}")
            }
        }
    }

    fun startDownload() {
        val i = info ?: return
        val wanted = "$APP_NAME-${i.name}"
        var d = AppState.downloads.firstOrNull { it.name.startsWith(wanted) && it.name.endsWith(".apk") }
        if (d != null && d.status == Status.FINISHED && !File(d.path).exists()) {
            AppState.remove(d, deleteFile = false); d = null
        }
        if (d == null) d = AppState.add(i.apkUrl, "$wanted.apk", "Programs")
        else if (d.status == Status.PAUSED || d.status == Status.ERRORED) AppState.resume(d)
        item = d
        AppState.toast("Downloading update in background")
    }

    fun install() {
        val d = item ?: return
        val app = AppState.app
        if (!app.packageManager.canRequestPackageInstalls()) {
            AppState.toast("Allow installs from $APP_NAME, then tap Install now again")
            app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        AppState.openFile(d)
    }

    fun showUrlDialog() = AppState.show { close ->
        var v by remember { mutableStateOf(AppState.str("updateUrl", DEFAULT_URL)) }
        PurpleDialog("Update URL", close, actions = {
            TextButton(close) { Text("Cancel") }
            AccentButton("Save & Check", { AppState.set("updateUrl", v.trim()); close(); check(true) })
        }) {
            Text("Link to a version.json file with versionCode, versionName, apkUrl and notes.", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(v, { v = it }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun UpdateBanner() {
    val i = Updater.info ?: return
    if (Updater.hidden) return
    val d = Updater.item
    val ready = d != null && d.status == Status.FINISHED && File(d.path).exists()
    val failed = d != null && d.status == Status.ERRORED
    val busy = d != null && !ready && !failed

    Surface(
        Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SystemUpdate, null, tint = Accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when { ready -> "Update downloaded"; busy -> "Downloading $APP_NAME v${i.name}…"; failed -> "Update download failed"; else -> "New version available" },
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    )
                    Text(
                        when { ready -> "v${i.name} is ready to install"; else -> "v${i.name}" + if (i.notes.isNotBlank()) " • ${i.notes}" else "" },
                        fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!busy) IconButton({ Updater.hidden = true }) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            when {
                ready -> Button({ Updater.install() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Olive)) {
                    Icon(Icons.Default.InstallMobile, null); Spacer(Modifier.width(8.dp)); Text("Install now")
                }
                failed -> Button({ Updater.startDownload() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Retry download") }
                busy -> {
                    val r = d!!.ratio
                    val m = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    if (r != null) LinearProgressIndicator({ r }, m, color = Accent) else LinearProgressIndicator(m, color = Accent)
                    Text("${if (r != null) "${(r * 100).toInt()}%  •  " else ""}${fmtBytes(d.done)}${if (d.speed > 0) "  •  ${fmtBytes(d.speed)}/s" else ""}", fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                else -> Button({ Updater.startDownload() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download now")
                }
            }
        }
    }
}
