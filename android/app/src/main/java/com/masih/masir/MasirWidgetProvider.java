package com.masih.masir;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
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
    static final String ACTION_TOGGLE = "com.masih.masir.WIDGET_TOGGLE";
    static final String EXTRA_ID = "task_id";

    static final int[] ROW = {R.id.r0, R.id.r1, R.id.r2, R.id.r3, R.id.r4, R.id.r5, R.id.r6, R.id.r7};
    static final int[] COLOR = {R.id.r0_color, R.id.r1_color, R.id.r2_color, R.id.r3_color, R.id.r4_color, R.id.r5_color, R.id.r6_color, R.id.r7_color};
    static final int[] TITLE = {R.id.r0_title, R.id.r1_title, R.id.r2_title, R.id.r3_title, R.id.r4_title, R.id.r5_title, R.id.r6_title, R.id.r7_title};
    static final int[] SUB = {R.id.r0_sub, R.id.r1_sub, R.id.r2_sub, R.id.r3_sub, R.id.r4_sub, R.id.r5_sub, R.id.r6_sub, R.id.r7_sub};
    static final int[] CHECK = {R.id.r0_check, R.id.r1_check, R.id.r2_check, R.id.r3_check, R.id.r4_check, R.id.r5_check, R.id.r6_check, R.id.r7_check};

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        MasirLog.install(c);
        MasirLog.d(c, "WIDGET", "onUpdate ids=" + java.util.Arrays.toString(ids));
        for (int id : ids) {
            try { m.updateAppWidget(id, build(c)); }
            catch (Throwable t) { MasirLog.e(c, "WIDGET", "updateAppWidget failed", t); }
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        MasirLog.install(c);
        MasirLog.d(c, "WIDGET", "onReceive action=" + (intent == null ? null : intent.getAction()));
        if (intent != null && ACTION_TOGGLE.equals(intent.getAction())) {
            String id = intent.getStringExtra(EXTRA_ID);
            if (id != null) toggle(c, id);
            refreshAll(c);
        }
    }

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, MasirWidgetProvider.class));
        if (ids == null || ids.length == 0) {
            MasirLog.d(c, "WIDGET", "refreshAll: no widget instances on screen");
            return;
        }
        MasirLog.d(c, "WIDGET", "refreshAll ids=" + java.util.Arrays.toString(ids));
        try {
            RemoteViews rv = build(c);
            for (int id : ids) m.updateAppWidget(id, rv);
        } catch (Throwable t) {
            MasirLog.e(c, "WIDGET", "refreshAll failed", t);
        }
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

    static PendingIntent openApp(Context c) {
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch == null) return null;
        return PendingIntent.getActivity(c, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent toggleIntent(Context c, String taskId) {
        Intent i = new Intent(c, MasirWidgetProvider.class)
                .setAction(ACTION_TOGGLE)
                .setData(Uri.parse("masir://toggle/" + Uri.encode(taskId)))
                .putExtra(EXTRA_ID, taskId);
        return PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static RemoteViews build(Context c) {
        try {
            return buildInner(c);
        } catch (Throwable t) {
            MasirLog.e(c, "WIDGET", "build() crashed", t);
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_masir);
            rv.setTextViewText(R.id.empty, "خطا: " + t.getClass().getSimpleName() + " (Masir Debug را باز کن)");
            return rv;
        }
    }

    static RemoteViews buildInner(Context c) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_masir);
        JSONObject s = snap(c);
        boolean fresh = s != null && todayKey().equals(s.optString("date"));

        PendingIntent open = openApp(c);
        if (open != null) {
            rv.setOnClickPendingIntent(R.id.header, open);
            rv.setOnClickPendingIntent(R.id.empty, open);
        }

        JSONArray tasks = null;
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
            tasks = s.optJSONArray("tasks");
        }

        int count = tasks == null ? 0 : tasks.length();
        rv.setViewVisibility(R.id.empty, count == 0 ? android.view.View.VISIBLE : android.view.View.GONE);

        for (int i = 0; i < ROW.length; i++) {
            if (i >= count) {
                rv.setViewVisibility(ROW[i], android.view.View.GONE);
                continue;
            }
            try {
                JSONObject t = tasks.getJSONObject(i);
                boolean fin = t.optBoolean("fin");
                String type = t.optString("type");
                int color;
                try { color = Color.parseColor(t.optString("color", "#007AFF")); }
                catch (Exception e) { color = Color.parseColor("#007AFF"); }

                String title = t.optString("title");
                if ("timer".equals(type)) title += "  ·  " + fa(t.optInt("val")) + "/" + fa(t.optInt("target")) + " دقیقه";
                else if ("count".equals(type)) title += "  ·  " + fa(t.optInt("val")) + "/" + fa(t.optInt("target"));
                SpannableString sp = new SpannableString(title);
                if (fin) sp.setSpan(new StrikethroughSpan(), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                rv.setViewVisibility(ROW[i], android.view.View.VISIBLE);
                rv.setTextViewText(TITLE[i], sp);
                rv.setTextColor(TITLE[i], c.getResources().getColor(fin ? R.color.masir_muted : R.color.masir_text));
                rv.setTextViewText(SUB[i], t.optString("area"));
                rv.setInt(COLOR[i], "setColorFilter", color);
                rv.setImageViewResource(CHECK[i], fin ? R.drawable.ic_circle_on : R.drawable.ic_circle_off);
                rv.setInt(CHECK[i], "setColorFilter", color);

                if ("check".equals(type)) {
                    PendingIntent pi = toggleIntent(c, t.optString("id"));
                    rv.setOnClickPendingIntent(ROW[i], pi);
                    rv.setOnClickPendingIntent(CHECK[i], pi);
                } else if (open != null) {
                    rv.setOnClickPendingIntent(ROW[i], open);
                    rv.setOnClickPendingIntent(CHECK[i], open);
                }
            } catch (Exception e) {
                rv.setViewVisibility(ROW[i], android.view.View.GONE);
            }
        }
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
            if (want < 0) {
                MasirLog.d(c, "WIDGET", "toggle ignored (task not found / not check): " + taskId);
                return;
            }
            MasirLog.d(c, "WIDGET", "toggle " + taskId + " -> " + want);
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
        } catch (Exception e) {
            MasirLog.e(c, "WIDGET", "toggle failed", e);
        }
    }
}
