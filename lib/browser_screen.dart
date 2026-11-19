import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'dm_screen.dart';
import 'models.dart';
import 'state.dart';
import 'widgets.dart';

final browserKey = GlobalKey<BrowserScreenState>();

const _desktopUA =
    'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36';

const _scanJs = r'''
(function(){var r=[];
function add(u,t){if(!u||u.indexOf('blob:')==0||u.indexOf('data:')==0)return;try{u=new URL(u,location.href).href}catch(e){return}r.push({url:u,type:t})}
document.querySelectorAll('video,audio').forEach(function(e){var t=e.tagName=='VIDEO'?'Videos':'Music';add(e.currentSrc||e.src,t);e.querySelectorAll('source').forEach(function(s){add(s.src,t)})});
document.querySelectorAll('a[href]').forEach(function(a){if(/\.(mp4|mkv|webm|mp3|m4a|flac|wav|zip|rar|7z|apk|pdf|iso|torrent|srt|vtt)(\?|$)/i.test(a.href))add(a.href,'')});
var seen={};return JSON.stringify(r.filter(function(x){if(seen[x.url])return false;seen[x.url]=1;return true}))})()
''';

const _readerJs = r'''
(function(){var id='__reader';var o=document.getElementById(id);if(o){o.remove();return}
var s=document.createElement('style');s.id=id;
s.textContent='header,nav,aside,footer,iframe,[class*=sidebar],[class*=advert],[id*=sidebar]{display:none!important}body{max-width:700px!important;margin:0 auto!important;padding:16px!important;font-size:19px!important;line-height:1.7!important;background:#fafafa!important;color:#222!important}';
document.head.appendChild(s)})()
''';

class BTab {
  static int _n = 0;
  final int id = ++_n;
  final bool incognito;
  late final WebViewController c;
  String url = '', title = 'New Tab';
  int progress = 100;
  bool home = true;
  List<Map<String, dynamic>> media = [];
  BTab(this.incognito);
}

class _MI {
  final String label;
  final IconData icon;
  final VoidCallback onTap;
  final bool? checked;
  _MI(this.label, this.icon, this.onTap, {this.checked});
}

class BrowserScreen extends StatefulWidget {
  final AppState s;
  const BrowserScreen(this.s, {super.key});
  @override
  State<BrowserScreen> createState() => BrowserScreenState();
}

class BrowserScreenState extends State<BrowserScreen> {
  AppState get s => widget.s;
  final tabs = <BTab>[];
  int cur = 0;
  final urlCtl = TextEditingController();
  final urlFocus = FocusNode();
  final findCtl = TextEditingController();
  bool findOpen = false, desktop = false, js = true, reader = false;
  String findCount = '0/0';
  BTab get t => tabs[cur];

  @override
  void initState() {
    super.initState();
    tabs.add(_make(false));
  }

  BTab _make(bool inc) {
    final tb = BTab(inc);
    tb.c = WebViewController()
      ..setJavaScriptMode(js ? JavaScriptMode.unrestricted : JavaScriptMode.disabled)
      ..setNavigationDelegate(NavigationDelegate(
        onNavigationRequest: (r) => _onNav(r),
        onPageStarted: (u) { tb.url = u; tb.progress = 5; _sync(tb); },
        onProgress: (p) { tb.progress = p; _sync(tb); },
        onPageFinished: (u) async {
          tb.url = u;
          tb.progress = 100;
          tb.title = (await tb.c.getTitle()) ?? u;
          if (!tb.incognito) s.addHistory(u);
          _sync(tb);
          _scan(tb);
          Future.delayed(const Duration(seconds: 3), () => _scan(tb));
        },
        onUrlChange: (ch) { if (ch.url != null) { tb.url = ch.url!; _sync(tb); } },
      ));
    if (desktop) tb.c.setUserAgent(_desktopUA);
    return tb;
  }

  NavigationDecision _onNav(NavigationRequest r) {
    final u = Uri.tryParse(r.url);
    if (u == null) return NavigationDecision.navigate;
    if (u.scheme == 'magnet') {
      launchUrl(u, mode: LaunchMode.externalApplication);
      return NavigationDecision.prevent;
    }
    if (!u.scheme.startsWith('http')) return NavigationDecision.prevent;
    if (s.blockAds && s.hostBlocked(u.host)) { s.blocked++; return NavigationDecision.prevent; }
    final ext = u.path.toLowerCase().contains('.') ? u.path.toLowerCase().split('.').last : '';
    if (r.isMainFrame && downloadableExts.contains(ext)) {
      _askDownload(r.url);
      return NavigationDecision.prevent;
    }
    return NavigationDecision.navigate;
  }

  void _askDownload(String url) {
    purpleDialog(context,
        title: 'Download file?',
        body: Text(fileNameFromUrl(url)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          filledBtn('Download', () { Navigator.pop(context); s.add(url); s.toast('Added to $kAppName: ${fileNameFromUrl(url)}'); }),
        ]);
  }

  void _sync(BTab tb) {
    if (!mounted) return;
    setState(() {
      if (tb == t && !urlFocus.hasFocus) urlCtl.text = tb.home ? '' : tb.url;
    });
  }

  Future<void> _scan(BTab tb) async {
    try {
      var r = (await tb.c.runJavaScriptReturningResult(_scanJs)).toString();
      if (r.startsWith('"')) r = jsonDecode(r) as String;
      final l = (jsonDecode(r) as List).map((e) => Map<String, dynamic>.from(e as Map)).toList();
      if (mounted) setState(() => tb.media = l);
    } catch (_) {}
  }

  // ---------- navigation ----------
  void go(String q) {
    q = q.trim();
    if (q.isEmpty) return;
    final isUrl = q.startsWith('http://') || q.startsWith('https://') || (q.contains('.') && !q.contains(' '));
    final url = isUrl ? (q.startsWith('http') ? q : 'https://$q') : 'https://www.google.com/search?q=${Uri.encodeQueryComponent(q)}';
    FocusScope.of(context).unfocus();
    setState(() {
      t.home = false;
      t.url = url;
      t.media = [];
      urlCtl.text = url;
    });
    t.c.loadRequest(Uri.parse(url));
  }

  void _addTab(bool inc) {
    setState(() {
      tabs.add(_make(inc));
      cur = tabs.length - 1;
      urlCtl.text = '';
    });
    s.toast(inc ? 'Incognito tab (history not recorded)' : 'New tab opened');
  }

  void _goHome() {
    setState(() { t.home = true; t.url = ''; t.media = []; urlCtl.text = ''; });
  }

  Future<void> handleBack() async {
    if (findOpen) { setState(() => findOpen = false); return; }
    if (!t.home && await t.c.canGoBack()) { t.c.goBack(); return; }
    if (!t.home) { _goHome(); return; }
    s.setBrowser(false);
  }

  String get _curUrl => t.home ? 'https://www.google.com' : t.url;

  // ---------- UI ----------
  @override
  Widget build(BuildContext context) {
    final grab = s.captureMedia && !t.home && t.media.isNotEmpty;
    return Scaffold(
      body: SafeArea(
        child: Column(children: [
          _bar(),
          if (t.progress < 100 && !t.home) LinearProgressIndicator(value: t.progress / 100, minHeight: 2, color: kAccent),
          if (findOpen) _findBar(),
          Expanded(
            child: Stack(children: [
              Positioned.fill(child: t.home ? _home() : WebViewWidget(key: ValueKey(t.id), controller: t.c)),
              if (grab)
                Positioned(
                  right: 16, bottom: 24,
                  child: FloatingActionButton.extended(
                    backgroundColor: kPrimary,
                    foregroundColor: Colors.white,
                    onPressed: openGrabber,
                    icon: const Icon(Icons.cloud_download),
                    label: Text('Grab Media (${t.media.length})', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                  ),
                ),
            ]),
          ),
        ]),
      ),
    );
  }

  Widget _bar() {
    final dlCount = s.downloads.length;
    return Container(
      padding: const EdgeInsets.fromLTRB(10, 6, 4, 6),
      child: Row(children: [
        InkWell(
          onTap: _tabsDialog,
          child: Container(
            width: 28, height: 28,
            alignment: Alignment.center,
            decoration: BoxDecoration(border: Border.all(width: 1.8, color: Colors.grey.shade600), borderRadius: BorderRadius.circular(4)),
            child: Text('${tabs.length}', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            decoration: BoxDecoration(color: Colors.grey.withValues(alpha: 0.18), borderRadius: BorderRadius.circular(24)),
            child: Row(children: [
              if (t.incognito) const Padding(padding: EdgeInsets.only(right: 6), child: Icon(Icons.visibility_off, size: 16, color: kAccent)),
              Expanded(
                child: TextField(
                  controller: urlCtl,
                  focusNode: urlFocus,
                  keyboardType: TextInputType.url,
                  textInputAction: TextInputAction.go,
                  style: const TextStyle(fontSize: 15),
                  decoration: const InputDecoration(border: InputBorder.none, hintText: 'Search', isDense: true, contentPadding: EdgeInsets.symmetric(vertical: 10)),
                  onTap: () => urlCtl.selection = TextSelection(baseOffset: 0, extentOffset: urlCtl.text.length),
                  onSubmitted: go,
                ),
              ),
              InkWell(onTap: _refresh, child: Icon(t.progress < 100 && !t.home ? Icons.close : Icons.refresh, size: 18)),
            ]),
          ),
        ),
        Stack(clipBehavior: Clip.none, children: [
          IconButton(icon: const Icon(Icons.download), onPressed: () => s.setBrowser(false)),
          if (dlCount > 0)
            Positioned(
              right: 4, top: 4,
              child: Container(
                width: 16, height: 16, alignment: Alignment.center,
                decoration: const BoxDecoration(color: kAccent, shape: BoxShape.circle),
                child: Text('$dlCount', style: const TextStyle(color: Colors.white, fontSize: 9, fontWeight: FontWeight.bold)),
              ),
            ),
        ]),
        IconButton(icon: const Icon(Icons.more_vert), onPressed: _menu),
      ]),
    );
  }

  void _refresh() {
    if (t.home) { s.toast('Home refreshed'); return; }
    if (t.progress < 100) { t.c.runJavaScript('window.stop()'); return; }
    t.c.reload();
    s.toast('Page refreshed');
  }

  Widget _findBar() => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        child: Row(children: [
          const Icon(Icons.search, size: 18, color: Colors.grey),
          const SizedBox(width: 8),
          Expanded(
            child: TextField(
              controller: findCtl,
              autofocus: true,
              decoration: const InputDecoration(border: InputBorder.none, hintText: 'Find in page...'),
              onChanged: _find,
              onSubmitted: (v) => t.c.runJavaScript('window.find(${jsonEncode(v)})'),
            ),
          ),
          Text(findCount, style: const TextStyle(fontSize: 12, color: Colors.grey)),
          IconButton(icon: const Icon(Icons.keyboard_arrow_down), onPressed: () => t.c.runJavaScript('window.find(${jsonEncode(findCtl.text)})')),
          IconButton(icon: const Icon(Icons.close, size: 18), onPressed: () => setState(() => findOpen = false)),
        ]),
      );

  Future<void> _find(String v) async {
    if (v.isEmpty) { setState(() => findCount = '0/0'); return; }
    try {
      final r = await t.c.runJavaScriptReturningResult(
          '(document.body.innerText.toLowerCase().split(${jsonEncode(v.toLowerCase())}).length-1)');
      t.c.runJavaScript('window.find(${jsonEncode(v)})');
      if (mounted) setState(() => findCount = '${r.toString()} found');
    } catch (_) {}
  }

  Widget _home() {
    final form = TextEditingController();
    Widget quick(String label, IconData i, Color col, String url) => InkWell(
          onTap: () => go(url),
          child: Column(children: [
            Container(width: 46, height: 46, decoration: BoxDecoration(color: col.withValues(alpha: 0.15), borderRadius: BorderRadius.circular(16)), child: Icon(i, color: col)),
            const SizedBox(height: 6),
            Text(label, style: const TextStyle(fontSize: 11)),
          ]),
        );
    const colors = [Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF4285F4), Color(0xFF34A853), Color(0xFFEA4335)];
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 80),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Text.rich(TextSpan(children: [
            for (var i = 0; i < 6; i++) TextSpan(text: 'Google'[i], style: TextStyle(color: colors[i])),
          ]), style: const TextStyle(fontSize: 52, fontWeight: FontWeight.w600)),
          const SizedBox(height: 24),
          Material(
            elevation: 2,
            borderRadius: BorderRadius.circular(30),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: TextField(
                controller: form,
                textInputAction: TextInputAction.search,
                onSubmitted: go,
                decoration: InputDecoration(border: InputBorder.none, hintText: 'Search...', suffixIcon: IconButton(icon: const Icon(Icons.search), onPressed: () => go(form.text))),
              ),
            ),
          ),
          const SizedBox(height: 32),
          Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
            quick('Videos', Icons.movie, kAccent, 'https://archive.org/details/movies'),
            quick('Music', Icons.music_note, Colors.blue, 'https://archive.org/details/audio'),
            quick('ISO Files', Icons.album, Colors.amber.shade800, 'https://releases.ubuntu.com'),
            quick('E-Books', Icons.menu_book, Colors.green, 'https://www.gutenberg.org'),
          ]),
        ]),
      ),
    );
  }

  // ---------- dialogs ----------
  void _tabsDialog() {
    showDialog(
      context: context,
      builder: (dc) => StatefulBuilder(
        builder: (dc, set) => Dialog(
          clipBehavior: Clip.antiAlias,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Container(color: kPrimary, width: double.infinity, padding: const EdgeInsets.all(14), child: Text('Open Tabs (${tabs.length})', style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w600))),
            Flexible(
              child: ListView(shrinkWrap: true, padding: const EdgeInsets.all(8), children: [
                for (var i = 0; i < tabs.length; i++)
                  ListTile(
                    selected: i == cur,
                    leading: Icon(tabs[i].incognito ? Icons.visibility_off : Icons.tab),
                    title: Text(tabs[i].home ? 'New Tab' : tabs[i].title, maxLines: 1, overflow: TextOverflow.ellipsis),
                    onTap: () { Navigator.pop(dc); setState(() { cur = i; urlCtl.text = t.home ? '' : t.url; }); },
                    trailing: IconButton(
                      icon: const Icon(Icons.close, size: 18),
                      onPressed: () {
                        if (tabs.length == 1) { s.toast('Last tab can\'t be closed'); return; }
                        setState(() { tabs.removeAt(i); if (cur >= tabs.length) cur = tabs.length - 1; urlCtl.text = t.home ? '' : t.url; });
                        set(() {});
                      },
                    ),
                  ),
              ]),
            ),
            Padding(
              padding: const EdgeInsets.all(12),
              child: Row(children: [
                Expanded(child: filledBtn('New Tab', () { Navigator.pop(dc); _addTab(false); })),
                const SizedBox(width: 8),
                Expanded(child: filledBtn('Incognito', () { Navigator.pop(dc); _addTab(true); }, color: Colors.grey.shade700)),
              ]),
            ),
          ]),
        ),
      ),
    );
  }

  void openGrabber() {
    _scan(t).then((_) {
      if (!mounted) return;
      purpleDialog(context,
          title: 'Media & Resource Grabber',
          icon: Icons.back_hand_outlined,
          body: t.media.isEmpty
              ? const Text('Nothing detected on this page yet. Open a page with a video, audio or file links, play the media, then try again.', style: TextStyle(fontSize: 13))
              : Column(children: [
                  for (final m in t.media)
                    Container(
                      margin: const EdgeInsets.only(bottom: 8),
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(border: Border.all(color: Colors.grey.withValues(alpha: 0.4)), borderRadius: BorderRadius.circular(12)),
                      child: Row(children: [
                        Expanded(
                          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text(fileNameFromUrl(m['url'] as String), maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12)),
                            Text(m['url'] as String, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 10, color: Colors.grey)),
                          ]),
                        ),
                        const SizedBox(width: 8),
                        filledBtn('Grab', () {
                          final name = fileNameFromUrl(m['url'] as String);
                          final ty = (m['type'] as String).isEmpty ? typeFromName(name) : m['type'] as String;
                          s.add(m['url'] as String, name: name, type: ty);
                          s.toast('Added to $kAppName: $name');
                        }),
                      ]),
                    ),
                ]));
    });
  }

  void openSaved() => _linkList('Saved Pages', s.saved, canDelete: false);

  void _linkList(String title, List<SavedLink> items, {bool canDelete = false}) {
    purpleDialog(context,
        title: '$title (${items.length})',
        body: items.isEmpty
            ? const Text('Nothing here yet')
            : Column(children: [
                for (final l in items)
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    title: Text(l.title, maxLines: 1, overflow: TextOverflow.ellipsis),
                    subtitle: Text(l.date.isEmpty ? l.url : '${l.url}  •  ${l.date}', maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 10)),
                    onTap: () { Navigator.pop(context); go(l.url); },
                  ),
              ]));
  }

  Future<void> _source() async {
    try {
      var r = (await t.c.runJavaScriptReturningResult('document.documentElement.outerHTML')).toString();
      if (r.startsWith('"')) r = jsonDecode(r) as String;
      if (!mounted) return;
      purpleDialog(context,
          title: 'Page Source',
          body: Container(
            padding: const EdgeInsets.all(8),
            color: Colors.black,
            child: SelectableText(r.length > 20000 ? '${r.substring(0, 20000)}\n...(truncated)' : r, style: const TextStyle(color: Colors.greenAccent, fontSize: 10, fontFamily: 'monospace')),
          ),
          actions: [TextButton(onPressed: () { Clipboard.setData(ClipboardData(text: r)); s.toast('HTML source copied'); }, child: const Text('Copy'))]);
    } catch (_) {
      s.toast('Could not read the page source');
    }
  }

  Future<void> _clearData() async {
    await t.c.clearCache();
    await t.c.clearLocalStorage();
    await WebViewCookieManager().clearCookies();
    s.clearHistory();
    s.toast('Cache, cookies & history cleared');
  }

  Future<void> _clipTabs() async {
    final txt = (await Clipboard.getData('text/plain'))?.text ?? '';
    final urls = txt.split(RegExp(r'\s+')).where((e) => e.startsWith('http')).toList();
    if (urls.isEmpty) { s.toast('No links in clipboard'); return; }
    for (final u in urls.take(10)) {
      _addTab(false);
      go(u);
    }
  }

  void _menu() {
    showDialog(
      context: context,
      barrierColor: Colors.black26,
      builder: (dc) => StatefulBuilder(builder: (dc, set) {
        final items = <_MI>[
          _MI('New Tab', Icons.add, () => _addTab(false)),
          _MI('Switch to Incognito', Icons.visibility_off, () => _addTab(true)),
          _MI('Return to $kAppName', Icons.cloud_download, () => s.setBrowser(false)),
          _MI('Exit Browser', Icons.exit_to_app, () => s.setBrowser(false)),
          _MI('Block Ads', Icons.shield, () { s.setB('blockAds', !s.blockAds); set(() {}); }, checked: s.blockAds),
          _MI('Capture audio/video', Icons.travel_explore, () { s.setB('captureMedia', !s.captureMedia); set(() {}); }, checked: s.captureMedia),
          _MI('View Page Resources', Icons.layers, openGrabber),
          _MI('Clone Tab', Icons.copy, () { final u = t.url; _addTab(t.incognito); if (u.isNotEmpty) go(u); }),
          _MI('Grabber', Icons.back_hand_outlined, openGrabber),
          _MI('History', Icons.history, () => _linkList('History', [for (final h in s.history) SavedLink(h, h)])),
          _MI('Open tab for all links in clipboard', Icons.content_paste_go, _clipTabs),
          _MI('Clear browsing data', Icons.cleaning_services, _clearData),
          _MI('View page source', Icons.code, _source),
          _MI('Save page', Icons.save, () { s.savePage(t.title, _curUrl); s.toast('Page link saved'); }),
          _MI('Saved pages', Icons.inventory_2_outlined, openSaved),
          _MI('Find in Page', Icons.find_in_page, () => setState(() => findOpen = true)),
          _MI('Copy link', Icons.link, () { Clipboard.setData(ClipboardData(text: _curUrl)); s.toast('Link copied to clipboard'); }),
          _MI('Copy all tab links', Icons.content_copy, () { Clipboard.setData(ClipboardData(text: tabs.map((e) => e.home ? 'https://www.google.com' : e.url).join('\n'))); s.toast('Copied ${tabs.length} link(s)'); }),
          _MI('Bookmarks', Icons.star_border, () => _linkList('Bookmarks', s.bookmarks)),
          _MI('Add Bookmark', Icons.star, () { s.addBookmark(t.title, _curUrl); s.toast('Bookmarked'); }),
          _MI('Bookmark all tabs', Icons.bookmark_add, () { for (final e in tabs) { if (!e.home) s.addBookmark(e.title, e.url); } s.toast('Bookmarked all open tabs'); }),
          _MI('Share link', Icons.share, () => Share.share(_curUrl)),
          _MI('Dark mode', Icons.dark_mode, () { s.setB('dark', !s.dark); s.setB('followSystem', false); set(() {}); }, checked: s.dark),
          _MI('Request desktop site', Icons.desktop_windows, () {
            desktop = !desktop;
            for (final e in tabs) { e.c.setUserAgent(desktop ? _desktopUA : null); }
            if (!t.home) t.c.reload();
            set(() {});
          }, checked: desktop),
          _MI('Enable JavaScript', Icons.javascript, () {
            js = !js;
            for (final e in tabs) { e.c.setJavaScriptMode(js ? JavaScriptMode.unrestricted : JavaScriptMode.disabled); }
            if (!t.home) t.c.reload();
            set(() {});
          }, checked: js),
          _MI('Reader Mode', Icons.chrome_reader_mode, () { reader = !reader; t.c.runJavaScript(_readerJs); s.toast(reader ? 'Reader Mode ON' : 'Reader Mode OFF'); set(() {}); }, checked: reader),
          _MI('Manage adblock hosts & filters', Icons.public, () => showAdblockDialog(context, s)),
          _MI('Settings', Icons.settings, s.openSettings),
          _MI('About', Icons.info_outline, () => infoDialog(context, 'About $kAppName Browser', '$kAppName: browser & download manager.\nFiles are saved to:\n${s.dir.path}')),
        ];
        void quick(VoidCallback f) { Navigator.pop(dc); f(); }
        return Align(
          alignment: Alignment.topRight,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(8, 40, 8, 12),
            child: Material(
              borderRadius: BorderRadius.circular(16),
              elevation: 8,
              clipBehavior: Clip.antiAlias,
              child: ConstrainedBox(
                constraints: BoxConstraints(maxWidth: 290, maxHeight: MediaQuery.of(context).size.height - 90),
                child: SizedBox(
                  width: 290,
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
                      child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                        IconButton(icon: const Icon(Icons.arrow_back, size: 18), onPressed: () => quick(handleBack)),
                        IconButton(icon: const Icon(Icons.arrow_forward, size: 18), onPressed: () => quick(() async { if (await t.c.canGoForward()) t.c.goForward(); })),
                        IconButton(icon: const Icon(Icons.star_border, size: 18), onPressed: () => quick(() { s.addBookmark(t.title, _curUrl); s.toast('Bookmarked'); })),
                        IconButton(icon: const Icon(Icons.refresh, size: 18), onPressed: () => quick(_refresh)),
                        IconButton(icon: const Icon(Icons.settings, size: 18), onPressed: () => quick(s.openSettings)),
                        IconButton(icon: const Icon(Icons.dns_outlined, size: 18), onPressed: () => quick(openGrabber)),
                      ]),
                    ),
                    const Divider(height: 1),
                    Flexible(
                      child: ListView(shrinkWrap: true, padding: EdgeInsets.zero, children: [
                        for (final it in items)
                          InkWell(
                            onTap: () {
                              if (it.checked == null) Navigator.pop(dc);
                              it.onTap();
                            },
                            child: Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
                              child: Row(children: [
                                Icon(it.icon, size: 18, color: Colors.grey),
                                const SizedBox(width: 14),
                                Expanded(child: Text(it.label, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w500))),
                                if (it.checked != null)
                                  Container(
                                    width: 18, height: 18,
                                    decoration: BoxDecoration(color: it.checked! ? kAccent : null, border: Border.all(color: kAccent, width: 2), borderRadius: BorderRadius.circular(3)),
                                    child: it.checked! ? const Icon(Icons.check, size: 13, color: Colors.white) : null,
                                  ),
                              ]),
                            ),
                          ),
                      ]),
                    ),
                  ]),
                ),
              ),
            ),
          ),
        );
      }),
    );
  }
}
