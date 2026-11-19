# 1DM – Flutter Android app

Download manager + built-in browser, built from your HTML design.

## Build the APK (no Android Studio needed)
1. Create a GitHub repo and upload everything in this folder.
2. Actions tab -> "Build Android APK" -> Run workflow.
3. Download artifact `1DM-apk`, unzip, install `app-release.apk` on your phone.

## Build locally (Ubuntu, Flutter installed)
    flutter create --platforms=android --org com.piash --project-name onedm /tmp/scaffold
    cp -r /tmp/scaffold/android ./android
    python3 tool/patch_android.py
    flutter pub get
    flutter run            # phone connected with USB debugging
    flutter build apk --release

## Files
lib/main.dart          app + theme + view switching
lib/state.dart         settings, bookmarks/history, download engine
lib/models.dart        DownloadItem, file types (change kAppName here)
lib/dm_screen.dart     download list, tabs, search, add/magnet/batch dialogs
lib/drawer.dart        Settings drawer (Types, Download, Options, Notifications, App)
lib/browser_screen.dart WebView browser, tabs, 3-dot menu, grabber
lib/widgets.dart       shared dialogs and helpers
tool/patch_android.py  adds permissions to AndroidManifest
