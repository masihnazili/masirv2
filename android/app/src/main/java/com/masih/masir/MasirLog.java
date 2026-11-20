package com.masih.masir;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Tiny file logger + report builder, so problems can be copied out of the app. */
final class MasirLog {
    private static final int MAX = 60000;
    private static boolean installed = false;

    private MasirLog() {}

    private static File file(Context c) {
        return new File(c.getApplicationContext().getFilesDir(), "masir_debug.log");
    }

    static synchronized void d(Context c, String tag, String msg) {
        try {
            String line = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())
                    + " [" + tag + "] " + msg + "\n";
            File f = file(c);
            if (f.length() > MAX) {
                String all = read(c);
                String keep = all.substring(all.length() / 2);
                try (FileOutputStream o = new FileOutputStream(f, false)) {
                    o.write(keep.getBytes(StandardCharsets.UTF_8));
                }
            }
            try (FileOutputStream o = new FileOutputStream(f, true)) {
                o.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    static void e(Context c, String tag, String msg, Throwable t) {
        StringWriter sw = new StringWriter();
        if (t != null) t.printStackTrace(new PrintWriter(sw));
        d(c, tag, "ERROR " + msg + "\n" + sw);
    }

    static synchronized String read(Context c) {
        try {
            File f = file(c);
            if (!f.exists()) return "";
            byte[] b = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int off = 0;
                while (off < b.length) {
                    int n = in.read(b, off, b.length - off);
                    if (n < 0) break;
                    off += n;
                }
            }
            return new String(b, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    static synchronized void clear(Context c) {
        try { file(c).delete(); } catch (Throwable ignored) {}
    }

    /** Also record uncaught crashes. */
    static synchronized void install(final Context c) {
        if (installed) return;
        installed = true;
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                e(app, "CRASH", "thread=" + t.getName(), e);
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    static String report(Context c) {
        StringBuilder b = new StringBuilder();
        b.append("=== MASIR DEBUG REPORT ===\n");
        b.append("time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            b.append("app: ").append(c.getPackageName()).append(" v").append(pi.versionName).append('\n');
        } catch (Throwable t) { b.append("app: ?\n"); }
        b.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        b.append("android: ").append(Build.VERSION.RELEASE).append(" (sdk ").append(Build.VERSION.SDK_INT).append(")\n");
        try {
            PackageInfo wv = WebView.getCurrentWebViewPackage();
            b.append("webview: ").append(wv == null ? "null" : wv.packageName + " " + wv.versionName).append('\n');
        } catch (Throwable t) { b.append("webview: ?\n"); }
        b.append("todayKey(native): ").append(MasirWidgetProvider.todayKey()).append('\n');

        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, MasirWidgetProvider.class));
            b.append("widget instances: ").append(ids == null ? 0 : ids.length).append('\n');
        } catch (Throwable t) { b.append("widget instances: error ").append(t).append('\n'); }

        SharedPreferences p = c.getSharedPreferences(MasirWidgetProvider.PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(MasirWidgetProvider.KEY_SNAP, null);
        if (raw == null) {
            b.append("snapshot: NONE (web app has not pushed data yet)\n");
        } else {
            b.append("snapshot length: ").append(raw.length()).append('\n');
            try {
                JSONObject s = new JSONObject(raw);
                b.append("snapshot date: ").append(s.optString("date"))
                        .append("  fresh=").append(MasirWidgetProvider.todayKey().equals(s.optString("date"))).append('\n');
                b.append("snapshot total/done/tasks: ").append(s.optInt("total")).append('/')
                        .append(s.optInt("done")).append('/')
                        .append(s.optJSONArray("tasks") == null ? -1 : s.optJSONArray("tasks").length()).append('\n');
            } catch (Throwable t) { b.append("snapshot JSON parse FAILED: ").append(t).append('\n'); }
            b.append("snapshot raw: ").append(raw.length() > 1500 ? raw.substring(0, 1500) + "..." : raw).append('\n');
        }
        b.append("pending: ").append(p.getString(MasirWidgetProvider.KEY_PENDING, "[]")).append('\n');

        try {
            MasirWidgetProvider.buildInner(c);
            b.append("build() test: OK\n");
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            b.append("build() test: FAILED\n").append(sw).append('\n');
        }

        b.append("\n--- LOG ---\n");
        String log = read(c);
        b.append(log.isEmpty() ? "(empty)\n" : log);
        String out = b.toString();
        return out.length() > 45000 ? out.substring(out.length() - 45000) : out;
    }
}
