import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'browser_screen.dart';
import 'dm_screen.dart';
import 'models.dart';
import 'state.dart';
import 'widgets.dart';

class AppDrawer extends StatelessWidget {
  final AppState s;
  const AppDrawer(this.s, {super.key});

  @override
  Widget build(BuildContext context) {
    final w = (MediaQuery.of(context).size.width * 0.78).clamp(260.0, 320.0);
    return Drawer(
      width: w,
      shape: const RoundedRectangleBorder(),
      child: ListenableBuilder(
        listenable: s,
        builder: (ctx, _) => Column(children: [
          SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
              child: Row(children: [
                const Icon(Icons.settings, size: 22),
                const SizedBox(width: 12),
                const Expanded(child: Text('Settings', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600))),
                IconButton(onPressed: () => Navigator.pop(ctx), icon: const Icon(Icons.menu_open)),
              ]),
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: ListView(padding: EdgeInsets.zero, children: [
              _section('TYPES', [for (final t in fileTypes) _typeTile(ctx, t)], pad: false),
              _section('DOWNLOAD', _download(ctx)),
              _section('OPTIONS', _options()),
              _section('NOTIFICATIONS', [
                _sw('Notifications', 'notif', true),
                _sw('Play sound', 'sound', false),
                _sw('Vibrate', 'vibrate', false),
                _sw('Show notification window', 'notifWin', false),
              ]),
              _section('APP', _app(ctx)),
            ]),
          ),
          Material(
            color: kOlive,
            child: InkWell(
              onTap: () { Navigator.pop(ctx); infoDialog(context, 'About $kAppName', '$kAppName download manager & browser.\nFiles are saved to:\n${s.dir.path}'); },
              child: const SafeArea(
                top: false,
                child: Padding(
                  padding: EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                  child: Row(children: [Icon(Icons.info_outline, color: Colors.white), SizedBox(width: 12), Text('About', style: TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.bold))]),
                ),
              ),
            ),
          ),
        ]),
      ),
    );
  }

  Widget _section(String title, List<Widget> kids, {bool pad = true}) => Theme(
        data: ThemeData(dividerColor: Colors.transparent),
        child: ExpansionTile(
          initiallyExpanded: true,
          tilePadding: const EdgeInsets.symmetric(horizontal: 16),
          title: Text(title, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, letterSpacing: 1, color: Colors.grey)),
          childrenPadding: pad ? const EdgeInsets.fromLTRB(16, 0, 16, 12) : EdgeInsets.zero,
          children: kids,
        ),
      );

  Widget _typeTile(BuildContext ctx, String t) {
    final on = s.selectedType == t;
    return ListTile(
      dense: true,
      selected: on,
      selectedTileColor: Colors.grey.withValues(alpha: 0.25),
      leading: Icon(typeIcons[t], size: 20),
      title: Text(t, style: TextStyle(fontWeight: on ? FontWeight.w600 : FontWeight.w500)),
      onTap: () { s.selectType(t); Navigator.pop(ctx); },
    );
  }

  Widget _sw(String label, String key, bool def, {bool enabled = true}) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 2),
        child: Row(children: [
          Expanded(child: Text(label, style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: enabled ? null : Colors.grey))),
          Switch(value: s.b(key, def), onChanged: enabled ? (v) => s.setB(key, v) : null),
        ]),
      );

  Widget _slider(BuildContext c, String label, double v, double min, double max, int? div, ValueChanged<double> on, {bool enabled = true}) =>
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(label, style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: enabled ? null : Colors.grey)),
        SliderTheme(
          data: SliderTheme.of(c).copyWith(
            trackHeight: 16,
            activeTrackColor: const Color(0xFF799CF0),
            inactiveTrackColor: const Color(0xFF616161),
            thumbColor: const Color(0xFF799CF0),
            overlayShape: SliderComponentShape.noOverlay,
          ),
          child: Slider(value: v, min: min, max: max, divisions: div, onChanged: enabled ? on : null),
        ),
      ]);

  List<Widget> _download(BuildContext c) {
    final lim = s.b('speedLimit', false);
    final dl = s.i('dlLimit', 100), ul = s.i('ulLimit', 100);
    return [
      Row(children: [
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Download Location', style: TextStyle(fontWeight: FontWeight.w600)),
            Text(s.dir.path, style: const TextStyle(fontSize: 11, color: Colors.grey)),
          ]),
        ),
        IconButton(
          icon: const Icon(Icons.visibility_outlined),
          onPressed: () async {
            try {
              final f = File('${s.dir.path}/.write_test');
              await f.writeAsString('ok');
              await f.delete();
              s.toast('Storage path verified: Writable');
            } catch (_) {
              s.toast('Storage path is NOT writable');
            }
          },
        ),
      ]),
      _slider(c, 'Number of downloads: ${s.numDownloads}', s.numDownloads.toDouble(), 1, 10, 9, (v) => s.setI('numDownloads', v.round())),
      _slider(c, 'Number of parts: ${s.i('numParts', 8)}', s.i('numParts', 8).toDouble(), 1, 16, 15, (v) => s.setI('numParts', v.round())),
      _sw('Enable speed limit', 'speedLimit', false),
      _sw('Apply speed limit to all', 'speedAll', false, enabled: lim),
      _slider(c, 'Download speed limit: ${dl == 100 ? 'MAX' : '${dl * 50} kB/s'}', dl.toDouble(), 1, 100, 99, (v) => s.setI('dlLimit', v.round()), enabled: lim),
      _slider(c, 'Upload speed limit: ${ul == 100 ? 'MAX' : '${ul * 25} kB/s'}', ul.toDouble(), 1, 100, 99, (v) => s.setI('ulLimit', v.round()), enabled: lim),
      _sw('Smart download', 'smart', false),
      _sw('Switch off Wifi after all downloads are finished or errored', 'wifiOffAll', false),
      _sw('Switch off Wifi only after all downloads are finished', 'wifiOffFin', false),
      _sw('Remove download links from list after downloads are finished (Files will not be deleted)', 'removeAfter', false),
    ];
  }

  List<Widget> _options() {
    final bat = s.i('batteryStop', 0);
    return [
      _sw('Wifi only', 'wifiOnly', false),
      Builder(builder: (c) => _slider(c, 'Stop downloads if battery below: ${bat == 0 ? 'Off' : '$bat%'}', bat.toDouble(), 0, 50, 10, (v) => s.setI('batteryStop', v.round()))),
      _sw('Use advance download method', 'advance', true),
      _sw('Disable cataloging', 'noCatalog', false),
      _sw('Use proxy', 'proxy', false),
      _sw('Disable torrent support', 'noTorrent', false),
      _sw('Seed finished torrents', 'seed', false),
      _sw('Sequential download', 'sequential', true),
      _sw('Make folder view the default view for torrent file selection', 'folderView', false),
      _sw('Retry', 'retry', true),
      _sw('Create empty file', 'emptyFile', true),
      _sw('Acquire wake lock', 'wakeLock', true),
      _sw('Acquire wifi lock', 'wifiLock', true),
      _sw('Auto restart non resumable links', 'autoRestart', false),
    ];
  }

  Widget _link(IconData i, String t, VoidCallback f) => InkWell(
        onTap: f,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Row(children: [Icon(i, size: 20, color: Colors.grey), const SizedBox(width: 14), Expanded(child: Text(t, style: const TextStyle(fontWeight: FontWeight.w600)))]),
        ),
      );

  List<Widget> _app(BuildContext ctx) {
    void close() => Navigator.pop(ctx);
    final root = s.dmScaffoldKey.currentContext ?? ctx;
    return [
      _sw('Follow System Theme if supported by device', 'followSystem', false),
      Row(children: [
        const Expanded(child: Text('App Theme', style: TextStyle(fontWeight: FontWeight.w600))),
        DropdownButton<String>(
          value: s.dark ? 'Dark' : 'Light',
          items: const [DropdownMenuItem(value: 'Light', child: Text('Light')), DropdownMenuItem(value: 'Dark', child: Text('Dark'))],
          onChanged: (v) { s.setB('dark', v == 'Dark'); s.setB('followSystem', false); },
        ),
      ]),
      Row(children: [
        const Expanded(child: Text('Language', style: TextStyle(fontWeight: FontWeight.w600))),
        DropdownButton<String>(
          value: s.str('lang', 'English'),
          items: [for (final l in ['English', 'Spanish', 'Hindi', 'Bengali', 'Arabic']) DropdownMenuItem(value: l, child: Text(l))],
          onChanged: (v) { s.setS('lang', v!); s.toast('Language saved (screens are English only for now)'); },
        ),
      ]),
      _link(Icons.share, 'Share the app', () => Share.share('Try $kAppName - download manager & browser')),
      _link(Icons.public, 'Manage adblock hosts & filters', () { close(); showAdblockDialog(root, s); }),
      _link(Icons.back_hand_outlined, 'Grabber', () { close(); s.setBrowser(true); WidgetsBinding.instance.addPostFrameCallback((_) => browserKey.currentState?.openGrabber()); }),
      _link(Icons.arrow_circle_down, 'Batch download', () { close(); showBatchDialog(root, s); }),
      _link(Icons.smart_display_outlined, 'Tutorial video', () { close(); showTutorial(root, s); }),
      _link(Icons.help_outline, 'FAQs', () {
        close();
        infoDialog(root, 'Frequently Asked Questions',
            'Q: Can I pause and resume?\nA: Yes. Pause keeps the partial file and resumes from where it stopped if the server supports it.\n\nQ: How do I grab a video?\nA: Open the page in the Browser and tap the Grab Media button.\n\nQ: Where are files saved?\nA: ${s.dir.path}');
      }),
      _link(Icons.inventory_2_outlined, 'Saved pages', () { close(); s.setBrowser(true); WidgetsBinding.instance.addPostFrameCallback((_) => browserKey.currentState?.openSaved()); }),
      _link(Icons.explore_outlined, 'Browser', () { close(); s.setBrowser(true); }),
      _link(Icons.storage, 'Storage info', () async {
        close();
        final used = await s.usedBytes();
        if (root.mounted) infoDialog(root, 'Storage Info', '${s.dir.path}\n\nUsed by $kAppName downloads: ${fmtBytes(used)}');
      }),
      _link(Icons.power_settings_new, 'Exit', () { SystemNavigator.pop(); }),
    ];
  }
}
