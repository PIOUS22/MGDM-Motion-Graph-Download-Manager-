import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'browser_screen.dart';
import 'dm_screen.dart';
import 'models.dart';
import 'state.dart';
import 'widgets.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final s = await AppState.load();
  runApp(OneDmApp(s));
}

class OneDmApp extends StatelessWidget {
  final AppState s;
  const OneDmApp(this.s, {super.key});

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: s,
        builder: (_, __) => MaterialApp(
          title: kAppName,
          debugShowCheckedModeBanner: false,
          scaffoldMessengerKey: messengerKey,
          themeMode: s.followSystem ? ThemeMode.system : (s.dark ? ThemeMode.dark : ThemeMode.light),
          theme: ThemeData(useMaterial3: true, colorSchemeSeed: kAccent, brightness: Brightness.light),
          darkTheme: ThemeData(useMaterial3: true, colorSchemeSeed: kAccent, brightness: Brightness.dark),
          home: Root(s),
        ),
      );
}

class Root extends StatefulWidget {
  final AppState s;
  const Root(this.s, {super.key});
  @override
  State<Root> createState() => _RootState();
}

class _RootState extends State<Root> {
  bool opened = false;

  @override
  Widget build(BuildContext context) {
    final s = widget.s;
    if (s.inBrowser) opened = true;
    SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(statusBarColor: Colors.transparent));
    return PopScope(
      canPop: !s.inBrowser,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) browserKey.currentState?.handleBack();
      },
      child: IndexedStack(
        index: s.inBrowser ? 1 : 0,
        children: [
          DmScreen(s),
          opened ? BrowserScreen(s, key: browserKey) : const SizedBox.shrink(),
        ],
      ),
    );
  }
}
