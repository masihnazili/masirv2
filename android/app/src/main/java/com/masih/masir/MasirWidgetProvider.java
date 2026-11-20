package com.masih.masir; // TODO: change to your appId / package name

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MasirWidgetProvider extends AppWidgetProvider {
    static final String PREFS = "masir_widget";
    static final String KEY_SNAP = "snap";
    static final String KEY_PENDING = "pending";
    static final String ACTION_TOGGLE = "com.masih.masir.WIDGET_TOGGLE"; // keep in sync with your appId if you like
    static final String EXTRA_ID = "task_id";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c, id));
        m.notifyAppWidgetViewDataChanged(ids, R.id.list);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            String id = intent.getStringExtra(EXTRA_ID);
            if (id != null) toggle(c, id);
            refreshAll(c);
        }
    }

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, MasirWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        for (int id : ids) m.updateAppWidget(id, build(c, id));
        m.notifyAppWidgetViewDataChanged(ids, R.id.list);
    }

    static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static JSONObject snap(Context c) {
        try {
            String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SNAP, null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    static String fa(int n) {
        String s = String.valueOf(n);
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(ch >= '0' && ch <= '9' ? (char) ('\u06F0' + (ch - '0')) : ch);
        return b.toString();
    }

    static RemoteViews build(Context c, int widgetId) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_masir);
        JSONObject s = snap(c);
        boolean fresh = s != null && todayKey().equals(s.optString("date"));

        // header -> opens the app
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch != null) {
            PendingIntent open = PendingIntent.getActivity(c, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            rv.setOnClickPendingIntent(R.id.header, open);
            rv.setOnClickPendingIntent(R.id.empty, open);
        }

        if (s == null) {
            rv.setTextViewText(R.id.sub, "");
            rv.setProgressBar(R.id.prog, 1, 0, false);
            rv.setTextViewText(R.id.empty, "برنامه را یک بار باز کن");
        } else if (!fresh) {
            rv.setTextViewText(R.id.sub, "");
            rv.setProgressBar(R.id.prog, 1, 0, false);
            rv.setTextViewText(R.id.empty, "برای شروع روز جدید، برنامه را باز کن");
        } else {
            int total = s.optInt("total"), done = s.optInt("done"), streak = s.optInt("streak");
            rv.setTextViewText(R.id.sub, fa(done) + " از " + fa(total) + " · زنجیره " + fa(streak));
            rv.setProgressBar(R.id.prog, Math.max(1, total), done, false);
            rv.setTextViewText(R.id.empty, "امروز کاری نداری");
        }

        Intent svc = new Intent(c, MasirWidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        rv.setRemoteAdapter(R.id.list, svc);
        rv.setEmptyView(R.id.list, R.id.empty);

        Intent tpl = new Intent(c, MasirWidgetProvider.class).setAction(ACTION_TOGGLE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, tpl,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
        rv.setPendingIntentTemplate(R.id.list, pi);
        return rv;
    }

    /** Flip a check-type task locally and queue the change for the web app. */
    static void toggle(Context c, String taskId) {
        try {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONObject s = snap(c);
            if (s == null) return;
            String date = s.optString("date");
            if (!todayKey().equals(date)) return;
            JSONArray tasks = s.getJSONArray("tasks");
            int want = -1;
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject t = tasks.getJSONObject(i);
                if (taskId.equals(t.optString("id")) && "check".equals(t.optString("type"))) {
                    boolean nf = !t.optBoolean("fin");
                    t.put("fin", nf);
                    t.put("val", nf ? 1 : 0);
                    s.put("done", Math.max(0, s.optInt("done") + (nf ? 1 : -1)));
                    want = nf ? 1 : 0;
                    break;
                }
            }
            if (want < 0) return;
            p.edit().putString(KEY_SNAP, s.toString()).apply();

            String prefix = date + "|" + taskId + "|";
            JSONArray old = new JSONArray(p.getString(KEY_PENDING, "[]"));
            JSONArray neu = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                String x = old.getString(i);
                if (!x.startsWith(prefix)) neu.put(x);
            }
            neu.put(prefix + want);
            p.edit().putString(KEY_PENDING, neu.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
