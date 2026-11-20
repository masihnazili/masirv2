package com.masih.masir;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class DebugActivity extends Activity {
    private TextView out;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        MasirLog.install(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 24);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LinearLayout row1 = row();
        LinearLayout row2 = row();
        row1.addView(btn("Copy all", new View.OnClickListener() {
            @Override public void onClick(View v) { copy(); }
        }));
        row1.addView(btn("Share", new View.OnClickListener() {
            @Override public void onClick(View v) { share(); }
        }));
        row1.addView(btn("Refresh", new View.OnClickListener() {
            @Override public void onClick(View v) { reload(); }
        }));
        row2.addView(btn("Test data", new View.OnClickListener() {
            @Override public void onClick(View v) { testData(); }
        }));
        row2.addView(btn("Update widget", new View.OnClickListener() {
            @Override public void onClick(View v) { updateWidget(); }
        }));
        row2.addView(btn("Clear log", new View.OnClickListener() {
            @Override public void onClick(View v) { MasirLog.clear(DebugActivity.this); reload(); }
        }));
        root.addView(row1);
        root.addView(row2);

        ScrollView sv = new ScrollView(this);
        out = new TextView(this);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextSize(10.5f);
        out.setTextIsSelectable(true);
        out.setPadding(0, 16, 0, 0);
        sv.addView(out, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        reload();
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private Button btn(String t, View.OnClickListener l) {
        Button x = new Button(this);
        x.setText(t);
        x.setAllCaps(false);
        x.setTextSize(12f);
        x.setOnClickListener(l);
        x.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return x;
    }

    private void reload() {
        out.setText(MasirLog.report(this));
    }

    private void copy() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("masir-debug", MasirLog.report(this)));
        Toast.makeText(this, "Copied. Paste it to Claude.", Toast.LENGTH_SHORT).show();
    }

    private void share() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, MasirLog.report(this));
        startActivity(Intent.createChooser(i, "Share debug report"));
    }

    private void updateWidget() {
        try {
            MasirLog.d(this, "DEBUG", "manual widget update");
            MasirWidgetProvider.refreshAll(this);
        } catch (Throwable t) {
            MasirLog.e(this, "DEBUG", "manual update failed", t);
        }
        reload();
    }

    /** Writes a fake snapshot so the widget can be tested without the web app. */
    private void testData() {
        try {
            JSONObject s = new JSONObject();
            s.put("date", MasirWidgetProvider.todayKey());
            s.put("streak", 3);
            s.put("total", 3);
            s.put("done", 1);
            JSONArray a = new JSONArray();
            a.put(task("t1", "تست تیک", "check", false, 0, 1, "#007AFF", "عمومی"));
            a.put(task("t2", "نوشیدن آب", "count", false, 2, 8, "#34C759", "سلامت"));
            a.put(task("t3", "مطالعه", "timer", true, 20, 20, "#FF9500", "یادگیری"));
            s.put("tasks", a);
            getSharedPreferences(MasirWidgetProvider.PREFS, Context.MODE_PRIVATE)
                    .edit().putString(MasirWidgetProvider.KEY_SNAP, s.toString()).apply();
            MasirLog.d(this, "DEBUG", "test snapshot written");
            MasirWidgetProvider.refreshAll(this);
        } catch (Throwable t) {
            MasirLog.e(this, "DEBUG", "test data failed", t);
        }
        reload();
    }

    private JSONObject task(String id, String title, String type, boolean fin, int val, int target, String color, String area) throws Exception {
        JSONObject t = new JSONObject();
        t.put("id", id); t.put("title", title); t.put("type", type); t.put("fin", fin);
        t.put("val", val); t.put("target", target); t.put("color", color); t.put("area", area);
        return t;
    }
}
