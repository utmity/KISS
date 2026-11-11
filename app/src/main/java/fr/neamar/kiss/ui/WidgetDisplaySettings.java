package fr.neamar.kiss.ui;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

/** Optional per-instance settings; the existing grid serialization stays unchanged. */
public final class WidgetDisplaySettings {
    private WidgetDisplaySettings() { }
    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
    public static boolean ignoresLimits(Context context, int id) {
        return prefs(context).getBoolean("widget-ignore-limits-" + id, false);
    }
    public static void setIgnoresLimits(Context context, int id, boolean enabled) {
        prefs(context).edit().putBoolean("widget-ignore-limits-" + id, enabled).apply();
    }
    public static int scalePercent(Context context, int id) {
        return Math.max(50, Math.min(200, prefs(context).getInt("widget-scale-percent-" + id, 100)));
    }
    public static void setScalePercent(Context context, int id, int percent) {
        prefs(context).edit().putInt("widget-scale-percent-" + id, Math.max(50, Math.min(200, percent))).apply();
    }
    public static void remove(Context context, int id) {
        prefs(context).edit().remove("widget-ignore-limits-" + id).remove("widget-scale-percent-" + id).apply();
    }
}
