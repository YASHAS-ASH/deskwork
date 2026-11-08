package com.deskwork.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;
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

  private PdfRenderer pdf;
  private ParcelFileDescriptor pfd;
  private int pdfId = -1;
  private final Object pdfLock = new Object();
  private final java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newSingleThreadExecutor();

  private void closePdf() {
    try { if (pdf != null) pdf.close(); } catch (Exception e) {}
    try { if (pfd != null) pfd.close(); } catch (Exception e) {}
    pdf = null; pfd = null; pdfId = -1;
  }

  private int openPdf(int id) {
    synchronized (pdfLock) {
      try {
        closePdf();
        File f = new File(getCacheDir(), "pdf_" + id + ".pdf");
        pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        pdf = new PdfRenderer(pfd);
        pdfId = id;
        return pdf.getPageCount();
      } catch (Exception e) {
        closePdf();
        return -1;
      }
    }
  }

  private void writeFile(File f, byte[] b) throws Exception {
    FileOutputStream o = new FileOutputStream(f);
    try { o.write(b); } finally { o.close(); }
  }

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

    @JavascriptInterface
    public String save(String fname, String mimeType, String b64) {
      try {
        if (android.os.Build.VERSION.SDK_INT < 29) return "";
        byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fname);
        v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType);
        v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/Deskwork");
        Uri u = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (u == null) return "";
        java.io.OutputStream o = getContentResolver().openOutputStream(u);
        try { o.write(bytes); } finally { o.close(); }
        return "Downloads/Deskwork/" + fname;
      } catch (Exception e) {
        return "";
      }
    }

    @JavascriptInterface
    public int pdfFromPending(int id) {
      Thread t = loader;
      if (t == null) return -1;
      try { t.join(); } catch (InterruptedException e) { return -1; }
      byte[] b;
      synchronized (lock) { b = data; }
      if (b == null) return -1;
      try { writeFile(new File(getCacheDir(), "pdf_" + id + ".pdf"), b); } catch (Exception e) { return -1; }
      return openPdf(id);
    }

    @JavascriptInterface
    public int pdfOpenB64(int id, String b64) {
      try { writeFile(new File(getCacheDir(), "pdf_" + id + ".pdf"), Base64.decode(b64, Base64.DEFAULT)); } catch (Exception e) { return -1; }
      return openPdf(id);
    }

    @JavascriptInterface
    public int pdfUse(int id) {
      synchronized (pdfLock) {
        if (pdfId == id && pdf != null) return pdf.getPageCount();
      }
      return openPdf(id);
    }

    @JavascriptInterface
    public String pdfSize(int i) {
      synchronized (pdfLock) {
        try {
          PdfRenderer.Page p = pdf.openPage(i);
          String r = p.getWidth() + "," + p.getHeight();
          p.close();
          return r;
        } catch (Exception e) { return ""; }
      }
    }

    @JavascriptInterface
    public void pdfRender(final int id, final int i, final int width, final int req) {
      ex.execute(new Runnable() {
        public void run() {
          String out = "";
          synchronized (pdfLock) {
            try {
              if (pdf != null && pdfId == id) {
                PdfRenderer.Page p = pdf.openPage(i);
                int h = Math.max(1, Math.round(width * (float) p.getHeight() / p.getWidth()));
                Bitmap bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.WHITE);
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                p.close();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, bo);
                bmp.recycle();
                out = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
              }
            } catch (Exception e) { out = ""; }
          }
          final String js = "window.__dwPage&&window.__dwPage(" + req + ",\"" + out + "\")";
          final WebView wv = getBridge().getWebView();
          wv.post(new Runnable() { public void run() { wv.evaluateJavascript(js, null); } });
        }
      });
    }

    private String safeName(String n) {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < n.length(); i++) {
        char c = n.charAt(i);
        b.append("/\\:*?\"<>|".indexOf(c) >= 0 ? '_' : c);
      }
      return b.length() == 0 ? "file" : b.toString();
    }

    private boolean startShare(final File f, String mimeType) {
      try {
        Uri uri = androidx.core.content.FileProvider.getUriForFile(MainActivity.this, "com.deskwork.app.share", f);
        final Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mimeType);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, f.getName());
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        runOnUiThread(new Runnable() {
          public void run() {
            startActivity(Intent.createChooser(send, "Share " + f.getName()));
          }
        });
        return true;
      } catch (Exception e) {
        return false;
      }
    }

    @JavascriptInterface
    public boolean share(String fname, String mimeType, String b64) {
      try {
        File dir = new File(getCacheDir(), "share");
        dir.mkdirs();
        File f = new File(dir, safeName(fname));
        writeFile(f, Base64.decode(b64, Base64.DEFAULT));
        return startShare(f, mimeType);
      } catch (Exception e) {
        return false;
      }
    }

    @JavascriptInterface
    public boolean sharePdf(int id, String fname) {
      try {
        File src = new File(getCacheDir(), "pdf_" + id + ".pdf");
        File dir = new File(getCacheDir(), "share");
        dir.mkdirs();
        String n = safeName(fname);
        if (!n.toLowerCase().endsWith(".pdf")) n = n + ".pdf";
        File f = new File(dir, n);
        java.io.FileInputStream in = new java.io.FileInputStream(src);
        FileOutputStream out = new FileOutputStream(f);
        try {
          byte[] buf = new byte[65536];
          int r;
          while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        } finally {
          in.close();
          out.close();
        }
        return startShare(f, "application/pdf");
      } catch (Exception e) {
        return false;
      }
    }
  }
}
