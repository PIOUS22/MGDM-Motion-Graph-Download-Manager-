package com.piash.onedm

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppState.init(application)
        Updater.check(false)
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        intent?.data?.let { u -> if (u.scheme?.startsWith("http") == true) { AppState.inBrowser = false; showAddDialog(u.toString()) } }
        setContent { Root() }
    }
}

@Composable
fun Root() {
    val dark = if (AppState.followSystem) isSystemInDarkTheme() else AppState.dark
    val scheme = if (dark) darkColorScheme(primary = Accent) else lightColorScheme(primary = Accent)
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                if (AppState.inBrowser) BrowserScreen() else DmScreen()

                val dlg = AppState.dialog
                if (dlg != null) dlg { AppState.dialog = null }

                val t = AppState.toastState
                LaunchedEffect(t?.id) {
                    if (t != null) { delay(2400); if (AppState.toastState?.id == t.id) AppState.toastState = null }
                }
                if (t != null) {
                    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
                        Surface(color = Color(0xFF2D2D2D), shape = RoundedCornerShape(22.dp)) {
                            Text(t.msg, color = Color.White, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                        }
                    }
                }
            }
        }
    }
}
