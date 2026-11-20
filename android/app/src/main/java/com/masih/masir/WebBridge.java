package com.masih.masir;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;

/** Exposed to the web app as window.MasirNative. */
public class WebBridge {
    private final Context c;

    WebBridge(Context ctx) {
        this.c = ctx.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return c.getSharedPreferences(MasirWidgetProvider.PREFS, Context.MODE_PRIVATE);
    }

    /** Web app pushes today's snapshot for the widget. */
    @JavascriptInterface
    public void update(String json) {
        if (json == null) return;
        prefs().edit().putString(MasirWidgetProvider.KEY_SNAP, json).apply();
        MasirWidgetProvider.refreshAll(c);
    }

    /** Returns (and clears) check-offs made from the widget, as a JSON array string. */
    @JavascriptInterface
    public String takePending() {
        SharedPreferences p = prefs();
        String s = p.getString(MasirWidgetProvider.KEY_PENDING, "[]");
        p.edit().remove(MasirWidgetProvider.KEY_PENDING).apply();
        return s;
    }
}
