package com.deskwork.app;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends BridgeActivity {
  private final Object lock = new Object();
  private Thread loader;
  private byte[] data;
  private String name = "file";
  private String mime = "";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    WebView wv = getBridge().getWebView();
    wv.addJavascriptInterface(new NativeFiles(), "DeskworkNative");
    handle(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    handle(intent);
    final WebView wv = getBridge().getWebView();
    wv.post(new Runnable() {
      public void run() {
        wv.evaluateJavascript("window.__dwIntent&&window.__dwIntent()", null);
      }
    });
  }

  private void handle(Intent intent) {
    if (intent == null) return;
    Uri uri = null;
    String action = intent.getAction();
    if (Intent.ACTION_VIEW.equals(action)) {
      uri = intent.getData();
    } else if (Intent.ACTION_SEND.equals(action)) {
      uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }
    if (uri == null) return;
    final Uri u = uri;
    synchronized (lock) { data = null; }
    loader = new Thread(new Runnable() {
      public void run() { load(u); }
    });
    loader.start();
  }

  private void load(Uri u) {
    String n = "file";
    String m = "";
    try {
      String t = getContentResolver().getType(u);
      if (t != null) m = t;
      Cursor c = null;
      try {
        c = getContentResolver().query(u, null, null, null, null);
        if (c != null && c.moveToFirst()) {
          int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
          if (i >= 0) {
            String s = c.getString(i);
            if (s != null) n = s;
          }
        }
      } catch (Exception ignore) {
      } finally {
        if (c != null) c.close();
      }
      if (n.equals("file")) {
        String p = u.getLastPathSegment();
        if (p != null) n = p;
      }
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      InputStream in = getContentResolver().openInputStream(u);
      try {
        byte[] buf = new byte[65536];
        int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
      } finally {
        in.close();
      }
      synchronized (lock) { data = bo.toByteArray(); name = n; mime = m; }
    } catch (Exception e) {
      synchronized (lock) { data = null; }
    }
  }

  private class NativeFiles {
    @JavascriptInterface
    public String info() {
      Thread t = loader;
      if (t == null) return "";
      try { t.join(); } catch (InterruptedException e) { return ""; }
      synchronized (lock) {
        if (data == null) return "";
        return "{\"name\":" + org.json.JSONObject.quote(name)
          + ",\"mime\":" + org.json.JSONObject.quote(mime)
          + ",\"size\":" + data.length + "}";
      }
    }

    @JavascriptInterface
    public String chunk(int off, int len) {
      synchronized (lock) {
        if (data == null) return "";
        return Base64.encodeToString(data, off, len, Base64.NO_WRAP);
      }
    }

    @JavascriptInterface
    public void done() {
      synchronized (lock) { data = null; }
      loader = null;
      setIntent(new Intent(Intent.ACTION_MAIN));
    }
  }
}
