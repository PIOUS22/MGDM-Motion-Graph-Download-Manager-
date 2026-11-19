import 'package:dio/dio.dart';

/// Change this one line to rename the app inside the UI.
const kAppName = '1DM';

enum DStatus { queued, downloading, paused, finished, errored }

const fileTypes = [
  'Everything', 'Torrents', 'Compressed', 'Documents', 'Music',
  'Videos', 'Subtitles', 'Photos', 'Programs', 'Others'
];

const _extMap = <String, List<String>>{
  'Torrents': ['torrent'],
  'Compressed': ['zip', 'rar', '7z', 'tar', 'gz', 'xz', 'bz2'],
  'Documents': ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'epub', 'mobi', 'csv'],
  'Music': ['mp3', 'm4a', 'aac', 'flac', 'wav', 'ogg', 'opus'],
  'Videos': ['mp4', 'mkv', 'webm', 'avi', 'mov', '3gp', 'flv'],
  'Subtitles': ['srt', 'vtt', 'ass'],
  'Photos': ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'],
  'Programs': ['apk', 'xapk', 'exe', 'deb', 'msi', 'dmg'],
};

final Set<String> downloadableExts = {
  for (final l in _extMap.values) ...l,
  'iso',
};

String typeFromName(String name) {
  final n = name.toLowerCase();
  final e = n.contains('.') ? n.split('.').last : '';
  for (final en in _extMap.entries) {
    if (en.value.contains(e)) return en.key;
  }
  return 'Others';
}

String fileNameFromUrl(String url) {
  final u = Uri.tryParse(url);
  var n = (u != null && u.pathSegments.isNotEmpty) ? u.pathSegments.last : '';
  try {
    n = Uri.decodeComponent(n);
  } catch (_) {}
  if (n.isEmpty) n = 'download_${DateTime.now().millisecondsSinceEpoch}';
  return n.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_');
}

class DownloadItem {
  int id;
  String name, url, type, path;
  int total, done;
  DStatus status;
  String? error;
  int speed = 0, mark = 0, retries = 0;
  CancelToken? cancel;

  DownloadItem({
    required this.id,
    required this.name,
    required this.url,
    required this.type,
    required this.path,
    this.total = -1,
    this.done = 0,
    this.status = DStatus.queued,
  });

  double? get ratio => total > 0 ? (done / total).clamp(0.0, 1.0) : null;

  Map<String, dynamic> toJson() => {
        'id': id, 'name': name, 'url': url, 'type': type, 'path': path,
        'total': total, 'done': done, 'status': status.index,
      };

  factory DownloadItem.fromJson(Map j) {
    final st = DStatus.values[j['status'] as int];
    return DownloadItem(
      id: j['id'] as int,
      name: j['name'] as String,
      url: j['url'] as String,
      type: j['type'] as String,
      path: j['path'] as String,
      total: j['total'] as int,
      done: j['done'] as int,
      status: (st == DStatus.downloading || st == DStatus.queued) ? DStatus.paused : st,
    );
  }
}
