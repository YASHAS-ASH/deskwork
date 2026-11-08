p = 'android/app/src/main/AndroidManifest.xml'
s = open(p).read()
mimes = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'application/msword', 'application/vnd.ms-excel', 'application/vnd.ms-powerpoint',
  'text/csv', 'text/comma-separated-values', 'application/csv', 'text/plain',
]
data = ''.join('<data android:mimeType="%s" />' % m for m in mimes)
view = ('<intent-filter><action android:name="android.intent.action.VIEW" />'
        '<category android:name="android.intent.category.DEFAULT" />'
        '<category android:name="android.intent.category.BROWSABLE" />'
        '<data android:scheme="content" /><data android:scheme="file" />' + data + '</intent-filter>')
send = ('<intent-filter><action android:name="android.intent.action.SEND" />'
        '<category android:name="android.intent.category.DEFAULT" />' + data + '</intent-filter>')
assert '</activity>' in s
s = s.replace('</activity>', view + send + '</activity>', 1)
prov = ('<provider android:name=".ShareProvider" android:authorities="com.deskwork.app.share" '
        'android:exported="false" android:grantUriPermissions="true">'
        '<meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/dw_paths" /></provider>')
assert '</application>' in s
s = s.replace('</application>', prov + '</application>', 1)
open(p, 'w').write(s)
import os
os.makedirs('android/app/src/main/res/xml', exist_ok=True)
open('android/app/src/main/res/xml/dw_paths.xml', 'w').write('<?xml version="1.0" encoding="utf-8"?><paths><cache-path name="share" path="share/" /></paths>')
print('manifest patched')
