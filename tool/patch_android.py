"""Adds permissions / cleartext / queries to the generated AndroidManifest.xml"""
import re, pathlib
p = pathlib.Path('android/app/src/main/AndroidManifest.xml')
x = p.read_text()
perms = '''    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <uses-permission android:name="android.permission.WAKE_LOCK"/>
'''
if 'android.permission.INTERNET' not in x:
    x = x.replace('<application', perms + '    <application', 1)
if 'usesCleartextTraffic' not in x:
    x = x.replace('<application', '<application android:usesCleartextTraffic="true"', 1)
queries = '''    <queries>
        <intent><action android:name="android.intent.action.VIEW"/><data android:scheme="https"/></intent>
        <intent><action android:name="android.intent.action.VIEW"/><data android:scheme="magnet"/></intent>
    </queries>
'''
if '<queries>' not in x:
    x = x.replace('</manifest>', queries + '</manifest>')
x = re.sub(r'android:label="[^"]*"', 'android:label="1DM"', x, count=1)
p.write_text(x)
print('patched')
