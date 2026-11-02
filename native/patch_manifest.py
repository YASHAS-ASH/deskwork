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
open(p, 'w').write(s)
print('manifest patched')
