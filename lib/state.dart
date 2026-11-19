import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'models.dart';

final messengerKey = GlobalKey<ScaffoldMessengerState>();

const adFilters = <String, List<String>>{
  'EasyList Standard': ['doubleclick.net', 'googlesyndication.com', 'googleadservices.com', 'adservice.google.com', 'adnxs.com', 'taboola.com', 'outbrain.com'],
  'EasyPrivacy Tracker Blocker': ['google-analytics.com', 'scorecardresearch.com', 'hotjar.com', 'mixpanel.com', 'segment.io'],
  'AdGuard Mobile Ads Filter': ['ads.yahoo.com', 'moatads.com', 'pubmatic.com', 'rubiconproject.com', 'criteo.com', 'amazon-adsystem.com'],
  'Malware & Crypto-Miner Hosts': ['coinhive.com', 'coin-hive.com', 'minero.cc', 'popads.net', 'propellerads.com', 'exoclick.com'],
};

class SavedLink {
  String title, url, date;
  SavedLink(this.title, this.url, [this.date = '']);
  Map<String, String> toJson() => {'t': title, 'u': url, 'd': date};
  factory SavedLink.fromJson(Map j) => SavedLink(j['t'] as String, j['u'] as String, (j['d'] ?? '') as String);
}

class AppState extends ChangeNotifier {
  final SharedPreferences p;
  late Directory dir;
  AppState(this.p);

  final dmScaffoldKey = GlobalKey<ScaffoldState>();
  final dio = Dio(BaseOptions(connectTimeout: const Duration(seconds: 20), followRedirects: true));
  List<DownloadItem> downloads = [];
  List<SavedLink> bookmarks = [], saved = [];
  List<String> history = [];
  String selectedType = 'Everything';
  bool inBrowser = false, sortAsc = false;
  int blocked = 0;

  static Future<AppState> load() async {
    final s = AppState(await SharedPreferences.getInstance());
    Directory base;
    try {
      base = (await getExternalStorageDirectory()) ?? await getApplicationDocumentsDirectory();
    } catch (_) {
      base = await getApplicationDocumentsDirectory();
    }
    s.dir = Directory('${base.path}/$kAppName');
    await s.dir.create(recursive: true);
    s._restore();
    var tick = 0;
    Timer.periodic(const Duration(seconds: 1), (_) {
      var any = false;
      for (final d in s.downloads) {
        if (d.status == DStatus.downloading) {
          d.speed = d.done - d.mark;
          d.mark = d.done;
          any = true;
        }
      }
      if (any) s.notifyListeners();
      if (++tick % 5 == 0) s._save();
    });
    return s;
  }

  // ---------- settings ----------
  bool b(String k, bool d) => p.getBool(k) ?? d;
  void setB(String k, bool v) { p.setBool(k, v); notifyListeners(); }
  int i(String k, int d) => p.getInt(k) ?? d;
  void setI(String k, int v) { p.setInt(k, v); notifyListeners(); }
  String str(String k, String d) => p.getString(k) ?? d;
  void setS(String k, String v) { p.setString(k, v); notifyListeners(); }

  bool get dark => b('dark', false);
  bool get followSystem => b('followSystem', false);
  bool get blockAds => b('blockAds', true);
  bool get captureMedia => b('captureMedia', true);
  int get numDownloads => i('numDownloads', 3);
  int get speedLimitBps => b('speedLimit', false) && i('dlLimit', 100) < 100 ? i('dlLimit', 100) * 50 * 1024 : 0;

  bool hostBlocked(String host) {
    for (final e in adFilters.entries) {
      if (!b('adf_${e.key}', true)) continue;
      for (final h in e.value) {
        if (host == h || host.endsWith('.$h')) return true;
      }
    }
    return false;
  }

  // ---------- persistence ----------
  void _restore() {
    try {
      downloads = (jsonDecode(p.getString('downloads') ?? '[]') as List).map((e) => DownloadItem.fromJson(e as Map)).toList();
      bookmarks = (jsonDecode(p.getString('bookmarks') ?? '[]') as List).map((e) => SavedLink.fromJson(e as Map)).toList();
      saved = (jsonDecode(p.getString('saved') ?? '[]') as List).map((e) => SavedLink.fromJson(e as Map)).toList();
      history = p.getStringList('history') ?? [];
    } catch (_) {}
    if (bookmarks.isEmpty) bookmarks = [SavedLink('Google Search', 'https://www.google.com')];
  }

  void _save() {
    p.setString('downloads', jsonEncode(downloads.map((d) => d.toJson()).toList()));
    p.setString('bookmarks', jsonEncode(bookmarks.map((e) => e.toJson()).toList()));
    p.setString('saved', jsonEncode(saved.map((e) => e.toJson()).toList()));
    p.setStringList('history', history.take(200).toList());
  }

  // ---------- UI helpers ----------
  void toast(String m) {
    messengerKey.currentState
      ?..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(
        content: Text(m, style: const TextStyle(color: Colors.white, fontSize: 14)),
        backgroundColor: const Color(0xFF2D2D2D),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
        duration: const Duration(milliseconds: 2400),
        margin: const EdgeInsets.fromLTRB(40, 0, 40, 24),
      ));
  }

  void setBrowser(bool v) { inBrowser = v; notifyListeners(); }
  void openSettings() {
    setBrowser(false);
    WidgetsBinding.instance.addPostFrameCallback((_) => dmScaffoldKey.currentState?.openDrawer());
  }
  void selectType(String t) { selectedType = t; notifyListeners(); }
  void toggleSort() { sortAsc = !sortAsc; downloads = downloads.reversed.toList(); notifyListeners(); toast(sortAsc ? 'Sorted oldest first' : 'Sorted newest first'); }

  // ---------- bookmarks / history ----------
  void addHistory(String u) { history.remove(u); history.insert(0, u); _save(); }
  void clearHistory() { history = []; _save(); notifyListeners(); }
  void addBookmark(String title, String url) {
    if (bookmarks.any((e) => e.url == url)) return;
    bookmarks.insert(0, SavedLink(title.isEmpty ? url : title, url)); _save(); notifyListeners();
  }
  void removeBookmark(SavedLink l) { bookmarks.remove(l); _save(); notifyListeners(); }
  void savePage(String title, String url) {
    final n = DateTime.now();
    saved.insert(0, SavedLink(title.isEmpty ? url : title, url, '${n.day}/${n.month}/${n.year} ${n.hour}:${n.minute.toString().padLeft(2, '0')}'));
    _save(); notifyListeners();
  }

  // ---------- download engine ----------
  DownloadItem add(String url, {String? name, String? type}) {
    var n = (name == null || name.trim().isEmpty) ? fileNameFromUrl(url) : name.trim().replaceAll(RegExp(r'[\\/:*?"<>|]'), '_');
    final dot = n.lastIndexOf('.');
    final base = dot > 0 ? n.substring(0, dot) : n;
    final ext = dot > 0 ? n.substring(dot) : '';
    var path = '${dir.path}/$n';
    var k = 1;
    while (File(path).existsSync() || downloads.any((d) => d.path == path)) {
      n = '$base ($k)$ext';
      path = '${dir.path}/$n';
      k++;
    }
    final d = DownloadItem(id: DateTime.now().microsecondsSinceEpoch, name: n, url: url, type: type ?? typeFromName(n), path: path);
    downloads.insert(0, d);
    _save();
    notifyListeners();
    _pump();
    return d;
  }

  void _pump() {
    var slots = numDownloads - downloads.where((d) => d.status == DStatus.downloading).length;
    for (final d in downloads.reversed.toList()) {
      if (slots <= 0) break;
      if (d.status == DStatus.queued) { slots--; _run(d); }
    }
  }

  Future<void> _run(DownloadItem d) async {
    d.status = DStatus.downloading;
    d.error = null;
    final token = CancelToken();
    d.cancel = token;
    notifyListeners();
    IOSink? sink;
    try {
      if (b('wifiOnly', false)) {
        final c = await Connectivity().checkConnectivity();
        if (!c.contains(ConnectivityResult.wifi) && !c.contains(ConnectivityResult.ethernet)) {
          d.retries = 99;
          throw 'Waiting for Wifi (Wifi only is on)';
        }
      }
      final f = File(d.path);
      var have = await f.exists() ? await f.length() : 0;
      final r = await dio.get<ResponseBody>(d.url,
          cancelToken: token,
          options: Options(
            responseType: ResponseType.stream,
            headers: have > 0 ? {'Range': 'bytes=$have-'} : null,
            validateStatus: (s) => s != null && s < 400,
          ));
      if (have > 0 && r.statusCode != 206) have = 0;
      final len = int.tryParse(r.headers.value('content-length') ?? '') ?? -1;
      d.total = len > 0 ? len + have : -1;
      d.done = have;
      d.mark = have;
      sink = f.openWrite(mode: have > 0 ? FileMode.append : FileMode.write);
      final sw = Stopwatch()..start();
      var run = 0;
      final limit = speedLimitBps;
      await for (final chunk in r.data!.stream) {
        sink.add(chunk);
        d.done += chunk.length;
        run += chunk.length;
        if (limit > 0) {
          final need = (run * 1000 / limit).round();
          if (need > sw.elapsedMilliseconds) await Future.delayed(Duration(milliseconds: need - sw.elapsedMilliseconds));
        }
      }
      await sink.close();
      sink = null;
      d.status = DStatus.finished;
      if (d.total < 0) d.total = d.done;
      toast('Finished: ${d.name}');
      if (b('removeAfter', false)) downloads.remove(d);
    } catch (e) {
      try { await sink?.close(); } catch (_) {}
      if (token.isCancelled) {
        if (d.status == DStatus.downloading) d.status = DStatus.paused;
      } else if (b('retry', true) && d.retries < 3) {
        d.retries++;
        d.status = DStatus.queued;
        await Future.delayed(const Duration(seconds: 2));
      } else {
        d.status = DStatus.errored;
        d.error = e is DioException ? (e.message ?? 'Network error') : e.toString();
      }
    }
    d.speed = 0;
    d.cancel = null;
    _save();
    notifyListeners();
    _pump();
  }

  void pause(DownloadItem d) {
    if (d.status == DStatus.downloading) { d.status = DStatus.paused; d.cancel?.cancel(); }
    else if (d.status == DStatus.queued) { d.status = DStatus.paused; }
    notifyListeners();
  }
  void resume(DownloadItem d) {
    if (d.status == DStatus.paused || d.status == DStatus.errored) { d.retries = 0; d.status = DStatus.queued; notifyListeners(); _pump(); }
  }
  void startAll() { for (final d in downloads) { if (d.status == DStatus.paused || d.status == DStatus.errored) { d.retries = 0; d.status = DStatus.queued; } } notifyListeners(); _pump(); }
  void pauseAll() { for (final d in downloads) { pause(d); } }
  Future<void> remove(DownloadItem d, {bool deleteFile = true}) async {
    d.status = DStatus.paused;
    d.cancel?.cancel();
    downloads.remove(d);
    if (deleteFile) { try { final f = File(d.path); if (await f.exists()) await f.delete(); } catch (_) {} }
    _save();
    notifyListeners();
  }
  void clearFinished() { downloads.removeWhere((d) => d.status == DStatus.finished); _save(); notifyListeners(); }

  Future<int> usedBytes() async {
    var t = 0;
    try { await for (final e in dir.list(recursive: true)) { if (e is File) t += await e.length(); } } catch (_) {}
    return t;
  }
}
