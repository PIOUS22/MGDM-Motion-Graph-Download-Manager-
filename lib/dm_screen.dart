import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:open_filex/open_filex.dart';
import 'package:url_launcher/url_launcher.dart';
import 'drawer.dart';
import 'models.dart';
import 'state.dart';
import 'widgets.dart';

class DmScreen extends StatefulWidget {
  final AppState s;
  const DmScreen(this.s, {super.key});
  @override
  State<DmScreen> createState() => _DmScreenState();
}

class _DmScreenState extends State<DmScreen> {
  String tab = 'ALL', q = '';
  bool searching = false, banner = true;
  final sc = TextEditingController();
  AppState get s => widget.s;

  bool _inTab(DownloadItem d, String t) {
    switch (t) {
      case 'DOWNLOADING':
        return d.status == DStatus.queued || d.status == DStatus.downloading || d.status == DStatus.paused;
      case 'FINISHED':
        return d.status == DStatus.finished;
      case 'ERRORED':
        return d.status == DStatus.errored;
    }
    return true;
  }

  List<DownloadItem> get list => s.downloads.where((d) {
        if (!_inTab(d, tab)) return false;
        if (s.selectedType != 'Everything' && d.type != s.selectedType) return false;
        if (q.isNotEmpty && !d.name.toLowerCase().contains(q)) return false;
        return true;
      }).toList();

  int count(String t) => s.downloads.where((d) => _inTab(d, t)).length;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: s,
      builder: (context, _) {
        final items = list;
        return Scaffold(
          key: s.dmScaffoldKey,
          drawer: AppDrawer(s),
          appBar: AppBar(
            backgroundColor: kPrimary,
            foregroundColor: Colors.white,
            titleSpacing: 0,
            title: Row(children: [
              const Text(kAppName, style: TextStyle(fontWeight: FontWeight.w600)),
              if (s.selectedType != 'Everything')
                Container(
                  margin: const EdgeInsets.only(left: 8),
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(20)),
                  child: Text(s.selectedType, style: const TextStyle(fontSize: 11)),
                ),
            ]),
            actions: [
              IconButton(icon: const Icon(Icons.search), onPressed: () => setState(() => searching = !searching)),
              IconButton(icon: const Icon(Icons.sort), onPressed: s.toggleSort),
              IconButton(icon: const Icon(Icons.public), onPressed: () => s.setBrowser(true)),
              IconButton(icon: const Text('U', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800)), onPressed: () => showMagnetDialog(context, s)),
              PopupMenuButton<String>(
                onSelected: (v) {
                  if (v == 'start') s.startAll();
                  if (v == 'pause') s.pauseAll();
                  if (v == 'clear') s.clearFinished();
                  if (v == 'clip') showAddDialog(context, s);
                },
                itemBuilder: (_) => const [
                  PopupMenuItem(value: 'start', child: ListTile(leading: Icon(Icons.play_arrow, color: kAccent), title: Text('Start all'))),
                  PopupMenuItem(value: 'pause', child: ListTile(leading: Icon(Icons.pause, color: kAccent), title: Text('Pause all'))),
                  PopupMenuItem(value: 'clear', child: ListTile(leading: Icon(Icons.delete_outline, color: Colors.red), title: Text('Remove finished'))),
                  PopupMenuItem(value: 'clip', child: ListTile(leading: Icon(Icons.link, color: kAccent), title: Text('Add from clipboard'))),
                ],
              ),
            ],
            bottom: PreferredSize(
              preferredSize: Size.fromHeight(46.0 + (searching ? 44 : 0)),
              child: Column(children: [
                if (searching)
                  Container(
                    color: const Color(0xFF4527A0),
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    height: 44,
                    child: Row(children: [
                      const Icon(Icons.search, size: 18, color: Colors.white70),
                      const SizedBox(width: 8),
                      Expanded(
                        child: TextField(
                          controller: sc,
                          autofocus: true,
                          style: const TextStyle(color: Colors.white),
                          decoration: const InputDecoration(border: InputBorder.none, hintText: 'Filter downloads by name...', hintStyle: TextStyle(color: Colors.white60)),
                          onChanged: (v) => setState(() => q = v.toLowerCase()),
                        ),
                      ),
                      IconButton(icon: const Icon(Icons.close, size: 18, color: Colors.white), onPressed: () => setState(() { searching = false; q = ''; sc.clear(); })),
                    ]),
                  ),
                SizedBox(
                  height: 46,
                  child: ListView(scrollDirection: Axis.horizontal, children: [
                    for (final t in ['ALL', 'DOWNLOADING', 'FINISHED', 'ERRORED']) _tabBtn(t),
                  ]),
                ),
              ]),
            ),
          ),
          floatingActionButton: FloatingActionButton(
            backgroundColor: const Color(0xFF673AB7),
            foregroundColor: Colors.white,
            shape: const CircleBorder(),
            onPressed: () => showAddDialog(context, s),
            child: const Icon(Icons.add, size: 28),
          ),
          body: Column(children: [
            if (banner)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(border: Border(bottom: BorderSide(color: Colors.grey.withValues(alpha: 0.3)))),
                child: Row(children: [
                  const Expanded(
                    child: Text('Tip: open the Browser (globe icon) and tap "Grab Media" to download videos and files from any page.',
                        textAlign: TextAlign.center, style: TextStyle(color: Color(0xFFE53935), fontSize: 13, fontWeight: FontWeight.w600)),
                  ),
                  IconButton(icon: const Icon(Icons.close, size: 18), onPressed: () => setState(() => banner = false)),
                ]),
              ),
            Expanded(child: items.isEmpty ? _empty() : ListView.separated(
              padding: const EdgeInsets.fromLTRB(12, 12, 12, 90),
              itemCount: items.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (_, i) => _card(items[i]),
            )),
          ]),
        );
      },
    );
  }

  Widget _tabBtn(String t) {
    final on = tab == t;
    return InkWell(
      onTap: () => setState(() => tab = t),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        alignment: Alignment.center,
        decoration: BoxDecoration(border: Border(bottom: BorderSide(color: on ? Colors.white : Colors.transparent, width: 3))),
        child: Text('$t (${count(t)})', style: TextStyle(color: on ? Colors.white : Colors.white60, fontWeight: FontWeight.bold, fontSize: 12, letterSpacing: 0.8)),
      ),
    );
  }

  Widget _pill(String label, VoidCallback f) => Padding(
        padding: const EdgeInsets.only(bottom: 14),
        child: Transform.rotate(
          angle: -19 * math.pi / 180,
          child: Material(
            color: kAccent,
            elevation: 2,
            borderRadius: BorderRadius.circular(4),
            child: InkWell(
              onTap: f,
              child: Padding(padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 10), child: Text(label, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600, letterSpacing: 1))),
            ),
          ),
        ),
      );

  Widget _empty() => Center(
        child: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            _pill('TIPS', () => infoDialog(context, '$kAppName Pro Tips',
                '1. Open the Browser (globe icon) to detect videos & audio on a page.\n2. Tap the + button and paste any direct link.\n3. Pause keeps the partial file; Resume continues from there.')),
            _pill('HELP', () => infoDialog(context, 'Help & Troubleshooting',
                '• Downloads waiting? Check Wifi only in Settings > Options.\n• Too many at once? Lower "Number of downloads".\n• Need torrents? Tap the U icon to open a magnet link in a torrent app.')),
            _pill('WATCH TUTORIAL', () => showTutorial(context, s)),
          ]),
        ),
      );

  Widget _card(DownloadItem d) {
    final fin = d.status == DStatus.finished, run = d.status == DStatus.downloading;
    final col = fin ? Colors.green.shade600 : run ? kAccent : d.status == DStatus.errored ? Colors.red : Colors.amber.shade700;
    final label = run ? '${fmtBytes(d.speed)}/s' : d.status == DStatus.queued ? 'QUEUED' : d.status.name.toUpperCase();
    return Card(
      margin: EdgeInsets.zero,
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: fin ? () => OpenFilex.open(d.path) : null,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(children: [
            Row(children: [
              Container(
                width: 40, height: 40,
                decoration: BoxDecoration(color: kAccent.withValues(alpha: 0.15), borderRadius: BorderRadius.circular(8)),
                child: Icon(typeIcons[d.type] ?? Icons.insert_drive_file, color: kAccent),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(d.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                  const SizedBox(height: 2),
                  Text.rich(TextSpan(style: const TextStyle(fontSize: 11, color: Colors.grey), children: [
                    TextSpan(text: '${fmtBytes(d.done)} / ${d.total > 0 ? fmtBytes(d.total) : '?'}  •  '),
                    TextSpan(text: label, style: TextStyle(color: col, fontWeight: FontWeight.bold)),
                  ])),
                  if (d.status == DStatus.errored && d.error != null)
                    Text(d.error!, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 10, color: Colors.red)),
                ]),
              ),
              if (!fin)
                IconButton(
                  icon: Icon(run || d.status == DStatus.queued ? Icons.pause : Icons.play_arrow, color: kAccent),
                  onPressed: () => run || d.status == DStatus.queued ? s.pause(d) : s.resume(d),
                )
              else
                IconButton(icon: const Icon(Icons.open_in_new, color: Colors.green), onPressed: () => OpenFilex.open(d.path)),
              IconButton(icon: const Icon(Icons.delete_outline, color: Colors.grey), onPressed: () => _delete(d)),
            ]),
            const SizedBox(height: 6),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                minHeight: 8,
                value: fin ? 1 : (d.ratio ?? (run ? null : 0)),
                color: fin ? Colors.green : kAccent,
                backgroundColor: Colors.grey.withValues(alpha: 0.3),
              ),
            ),
          ]),
        ),
      ),
    );
  }

  Future<void> _delete(DownloadItem d) async {
    if (d.status != DStatus.finished) { s.remove(d); return; }
    await purpleDialog(context,
        title: 'Remove download',
        body: Text(d.name),
        actions: [
          TextButton(onPressed: () { Navigator.pop(context); s.remove(d, deleteFile: false); }, child: const Text('List only')),
          TextButton(onPressed: () { Navigator.pop(context); s.remove(d); }, child: const Text('Delete file too', style: TextStyle(color: Colors.red))),
        ]);
  }
}

// ---------------- dialogs ----------------

Future<void> showAddDialog(BuildContext c, AppState s, {String? url}) async {
  var clip = '';
  try { clip = (await Clipboard.getData('text/plain'))?.text?.trim() ?? ''; } catch (_) {}
  if (!c.mounted) return;
  final urlCtl = TextEditingController(text: url ?? (clip.startsWith('http') ? clip : ''));
  final nameCtl = TextEditingController(text: urlCtl.text.isEmpty ? '' : fileNameFromUrl(urlCtl.text));
  var type = nameCtl.text.isEmpty ? 'Videos' : typeFromName(nameCtl.text);
  await purpleDialog(c,
      title: 'Add New Download',
      body: StatefulBuilder(
        builder: (ctx, set) => Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(
            controller: urlCtl,
            decoration: const InputDecoration(labelText: 'Download Link (URL)', border: OutlineInputBorder()),
            onChanged: (v) { nameCtl.text = fileNameFromUrl(v); set(() => type = typeFromName(nameCtl.text)); },
          ),
          const SizedBox(height: 10),
          TextField(controller: nameCtl, decoration: const InputDecoration(labelText: 'File Name', border: OutlineInputBorder())),
          const SizedBox(height: 10),
          DropdownButtonFormField<String>(
            value: type,
            decoration: const InputDecoration(labelText: 'Category', border: OutlineInputBorder()),
            items: [for (final t in fileTypes.skip(1)) DropdownMenuItem(value: t, child: Text(t))],
            onChanged: (v) => set(() => type = v!),
          ),
        ]),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(c), child: const Text('Cancel')),
        filledBtn('Start', () {
          final u = urlCtl.text.trim();
          if (!u.startsWith('http')) { s.toast('Enter a valid http/https link'); return; }
          s.add(u, name: nameCtl.text, type: type);
          Navigator.pop(c);
        }),
      ]);
}

Future<void> showMagnetDialog(BuildContext c, AppState s) {
  final ctl = TextEditingController();
  return purpleDialog(c,
      title: 'Add Torrent / Magnet Link',
      body: Column(mainAxisSize: MainAxisSize.min, children: [
        const Text('Paste a magnet link or a .torrent URL.\n\nMagnet links open in your installed torrent app. A .torrent URL downloads the .torrent file.',
            style: TextStyle(fontSize: 12, color: Colors.grey)),
        const SizedBox(height: 10),
        TextField(controller: ctl, maxLines: 3, style: const TextStyle(fontSize: 12, fontFamily: 'monospace'), decoration: const InputDecoration(border: OutlineInputBorder(), hintText: 'magnet:?xt=urn:btih:...')),
      ]),
      actions: [
        TextButton(onPressed: () => Navigator.pop(c), child: const Text('Cancel')),
        filledBtn('Add', () async {
          final v = ctl.text.trim();
          Navigator.pop(c);
          if (v.startsWith('magnet:')) {
            final ok = await launchUrl(Uri.parse(v), mode: LaunchMode.externalApplication).catchError((_) => false);
            if (!ok) s.toast('No torrent app installed to handle magnet links');
          } else if (v.startsWith('http')) {
            s.add(v);
          } else {
            s.toast('Not a magnet or http link');
          }
        }),
      ]);
}

Future<void> showBatchDialog(BuildContext c, AppState s) {
  final pat = TextEditingController(text: 'https://example.com/file_(*).zip');
  final from = TextEditingController(text: '1'), to = TextEditingController(text: '5');
  return purpleDialog(c,
      title: 'Batch Download Generator',
      body: Column(mainAxisSize: MainAxisSize.min, children: [
        const Text('Use (*) as the number placeholder, e.g. part_(*).zip', style: TextStyle(fontSize: 12, color: Colors.grey)),
        const SizedBox(height: 10),
        TextField(controller: pat, decoration: const InputDecoration(border: OutlineInputBorder())),
        const SizedBox(height: 10),
        Row(children: [
          Expanded(child: TextField(controller: from, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'From', border: OutlineInputBorder()))),
          const SizedBox(width: 10),
          Expanded(child: TextField(controller: to, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'To', border: OutlineInputBorder()))),
        ]),
      ]),
      actions: [
        TextButton(onPressed: () => Navigator.pop(c), child: const Text('Close')),
        filledBtn('Queue Batch', () {
          final a = int.tryParse(from.text) ?? 1, b = int.tryParse(to.text) ?? a;
          if (!pat.text.contains('(*)') || b < a || b - a > 200) { s.toast('Check the pattern and range (max 200 files)'); return; }
          final w = to.text.trim().length;
          for (var n = a; n <= b; n++) {
            s.add(pat.text.replaceAll('(*)', n.toString().padLeft(w, '0')));
          }
          Navigator.pop(c);
          s.toast('Queued ${b - a + 1} files');
        }),
      ]);
}

Future<void> showTutorial(BuildContext c, AppState s) => purpleDialog(c,
    title: '$kAppName Quick Guide',
    body: const Text('1. Tap the globe icon to open the Browser.\n2. Visit a page with a video, song or file.\n3. Tap Grab Media (or tap a file link) to add it to your downloads.\n4. Come back with the download icon in the browser bar.\n\nYou can also tap + and paste any direct link.', style: TextStyle(height: 1.6)),
    actions: [
      TextButton(onPressed: () => Navigator.pop(c), child: const Text('Close')),
      filledBtn('Open Browser', () { Navigator.pop(c); s.setBrowser(true); }),
    ]);
