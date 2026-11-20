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

## In-app updates
The app checks a `version.json` file on launch (and from Settings > Check for updates).
If `versionCode` is higher than the installed app, a banner appears: Download now ->
background download -> Install now.

One-time setup:
1. Put `version.json` (sample in this folder) in your GitHub repo root and edit it.
2. Set `DEFAULT_URL` in Update.kt to
   https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/version.json
   (or paste the link inside the app: Settings > Set update URL).

Releasing a new version:
1. In app/build.gradle.kts raise `versionCode` (2, 3, ...) and `versionName`.
2. Push a tag:  git tag v1.1 && git push origin v1.1
   GitHub Actions builds and attaches app-release.apk to a Release.
3. In version.json set the same versionCode/versionName and
   "apkUrl": "https://github.com/YOUR_USER/YOUR_REPO/releases/latest/download/app-release.apk"
   Commit it. Phones with the older version will see the update banner.

Signing: builds use app/mgdm.keystore (password mgdm1234, alias mgdm) so every version
has the same signature and can install over the old one. Keep this file safe and make
your repo private. If you lose it, users must uninstall and reinstall.
