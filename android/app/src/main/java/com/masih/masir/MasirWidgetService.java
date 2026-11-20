package com.masih.masir; // TODO: change to your appId / package name

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

public class MasirWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    static class Factory implements RemoteViewsFactory {
        private final Context c;
        private JSONArray tasks = new JSONArray();

        Factory(Context c) {
            this.c = c;
        }

        @Override public void onCreate() { load(); }
        @Override public void onDataSetChanged() { load(); }
        @Override public void onDestroy() { tasks = new JSONArray(); }
        @Override public int getCount() { return tasks.length(); }
        @Override public int getViewTypeCount() { return 1; }
        @Override public boolean hasStableIds() { return false; }
        @Override public long getItemId(int i) { return i; }
        @Override public RemoteViews getLoadingView() {
            return new RemoteViews(c.getPackageName(), R.layout.widget_item);
        }

        private void load() {
            JSONObject s = MasirWidgetProvider.snap(c);
            tasks = new JSONArray();
            if (s != null && MasirWidgetProvider.todayKey().equals(s.optString("date"))) {
                JSONArray a = s.optJSONArray("tasks");
                if (a != null) tasks = a;
            }
        }

        @Override
        public RemoteViews getViewAt(int i) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_item);
            try {
                JSONObject t = tasks.getJSONObject(i);
                boolean fin = t.optBoolean("fin");
                String type = t.optString("type");
                int color;
                try { color = Color.parseColor(t.optString("color", "#007AFF")); } catch (Exception e) { color = Color.parseColor("#007AFF"); }

                String title = t.optString("title");
                if ("timer".equals(type)) title += "  ·  " + MasirWidgetProvider.fa(t.optInt("val")) + "/" + MasirWidgetProvider.fa(t.optInt("target")) + " دقیقه";
                else if ("count".equals(type)) title += "  ·  " + MasirWidgetProvider.fa(t.optInt("val")) + "/" + MasirWidgetProvider.fa(t.optInt("target"));
                SpannableString sp = new SpannableString(title);
                if (fin) sp.setSpan(new StrikethroughSpan(), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                rv.setTextViewText(R.id.item_title, sp);
                rv.setTextColor(R.id.item_title, c.getResources().getColor(fin ? R.color.masir_muted : R.color.masir_text));
                rv.setTextViewText(R.id.item_sub, t.optString("area"));
                rv.setInt(R.id.item_color, "setBackgroundColor", color);

                boolean isCheck = "check".equals(type);
                rv.setImageViewResource(R.id.item_check, fin ? R.drawable.ic_circle_on : R.drawable.ic_circle_off);
                rv.setInt(R.id.item_check, "setColorFilter", color);
                if (isCheck) {
                    Intent fill = new Intent();
                    fill.putExtra(MasirWidgetProvider.EXTRA_ID, t.optString("id"));
                    rv.setOnClickFillInIntent(R.id.item_check, fill);
                    rv.setOnClickFillInIntent(R.id.item_title, fill);
                }
            } catch (Exception ignored) {
            }
            return rv;
        }
    }
}
