import 'package:flutter/material.dart';
import 'models.dart';
import 'state.dart';

const kPrimary = Color(0xFF512DA8);
const kAccent = Color(0xFF7E57C2);
const kOlive = Color(0xFF7CB342);

const typeIcons = <String, IconData>{
  'Everything': Icons.grid_view,
  'Torrents': Icons.file_download,
  'Compressed': Icons.folder_zip,
  'Documents': Icons.description,
  'Music': Icons.music_note,
  'Videos': Icons.movie,
  'Subtitles': Icons.closed_caption,
  'Photos': Icons.image,
  'Programs': Icons.android,
  'Others': Icons.insert_drive_file,
};

String fmtBytes(num b) {
  if (b <= 0) return '0 B';
  const u = ['B', 'KB', 'MB', 'GB', 'TB'];
  var i = 0;
  var v = b.toDouble();
  while (v >= 1024 && i < 4) { v /= 1024; i++; }
  return '${v.toStringAsFixed(i < 2 ? 0 : 1)} ${u[i]}';
}

Future<void> purpleDialog(BuildContext c,
    {required String title, required Widget body, List<Widget> actions = const [], IconData? icon, Color head = kPrimary}) {
  return showDialog(
    context: c,
    builder: (dc) => Dialog(
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 350),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Container(
            color: head,
            padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
            child: Row(children: [
              if (icon != null) ...[Icon(icon, color: Colors.white, size: 20), const SizedBox(width: 8)],
              Expanded(child: Text(title, style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w600))),
              IconButton(onPressed: () => Navigator.pop(dc), icon: const Icon(Icons.close, color: Colors.white, size: 20)),
            ]),
          ),
          Flexible(child: SingleChildScrollView(padding: const EdgeInsets.all(16), child: body)),
          if (actions.isNotEmpty)
            Padding(padding: const EdgeInsets.fromLTRB(8, 0, 12, 10), child: Row(mainAxisAlignment: MainAxisAlignment.end, children: actions)),
        ]),
      ),
    ),
  );
}

Future<void> infoDialog(BuildContext c, String title, String text) => purpleDialog(c,
    title: title,
    body: Text(text, style: const TextStyle(fontSize: 13, height: 1.5)),
    actions: [TextButton(onPressed: () => Navigator.pop(c), child: const Text('OK'))]);

Widget filledBtn(String t, VoidCallback f, {Color color = kAccent}) => FilledButton(
    onPressed: f, style: FilledButton.styleFrom(backgroundColor: color), child: Text(t));

void showAdblockDialog(BuildContext c, AppState s) {
  purpleDialog(c,
      title: 'Adblock Hosts & Filters',
      body: StatefulBuilder(
        builder: (ctx, set) => Column(children: [
          for (final e in adFilters.entries)
            Row(children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(e.key, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                  Text('${e.value.length} hosts', style: const TextStyle(fontSize: 11, color: Colors.grey)),
                ]),
              ),
              Switch(value: s.b('adf_${e.key}', true), onChanged: (v) { s.setB('adf_${e.key}', v); set(() {}); }),
            ]),
          const SizedBox(height: 4),
          Text('Blocked so far: ${s.blocked}', style: const TextStyle(fontSize: 12, color: Colors.grey)),
        ]),
      ));
}
