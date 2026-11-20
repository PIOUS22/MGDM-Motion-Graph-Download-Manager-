# MGDM (Motion Graph Download Manager) – Kotlin / Jetpack Compose Android app

Download manager + built-in browser, native Kotlin, built from your HTML design.

## Build the APK without Android Studio (easiest)
1. Create a GitHub repo and upload everything in this folder.
2. Actions tab -> "Build Android APK" -> Run workflow.
3. Download artifact `MGDM-apk`, unzip, install the .apk on your phone
   (allow "install unknown apps").

## Build with Android Studio
Open this folder as a project, let Gradle sync (AGP 8.5.2, Kotlin 2.0.20),
then Run on your phone with USB debugging, or Build > Build APK.
(If Studio asks about the Gradle wrapper, choose Gradle 8.9.)

## Files (app/src/main/java/com/piash/onedm)
AppState.kt      settings, bookmarks/history, download engine, foreground service
MainActivity.kt  entry point, theme, dialog + toast host
DmScreen.kt      download list, tabs, search, cards
Drawer.kt        Settings drawer (Types, Download, Options, Notifications, App)
BrowserScreen.kt WebView browser, tabs, 3-dot menu, grabber, find, reader
Ui.kt            shared dialogs (add, magnet, batch, tutorial, adblock)
Rename the app inside the UI: APP_NAME in AppState.kt (and res/values/strings.xml).
