package fr.neamar.kiss.keyboard;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.View;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.R;
import fr.neamar.kiss.pojo.AppPojo;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.result.Result;

/** Observes the root's pre-IME path. Never buffers or replays ordinary text keys. */
public final class KeyboardShortcuts {
    public static final String ENABLED = "keyboard-long-press-enabled";
    public static final String TIMEOUT = "keyboard-long-press-timeout";
    public static final String PREFIX = "keyboard-long-press-";
    private final MainActivity activity;
    private final View search;
    private final SharedPreferences preferences;
    private final LongPressState state = new LongPressState();
    private final Runnable fire = this::fire;

    public KeyboardShortcuts(MainActivity activity, View search) {
        this.activity = activity;
        this.search = search;
        preferences = PreferenceManager.getDefaultSharedPreferences(activity);
    }

    public boolean onKeyPreIme(KeyEvent event) {
        int key = event.getKeyCode();
        boolean ctrlKey = key == KeyEvent.KEYCODE_CTRL_LEFT || key == KeyEvent.KEYCODE_CTRL_RIGHT;
        if (event.isCanceled() || (ctrlKey && event.getAction() == KeyEvent.ACTION_UP)) cancel();
        if (event.getAction() == KeyEvent.ACTION_UP) {
            boolean consumed = state.up(event.getDeviceId(), key);
            if (!state.pending()) search.removeCallbacks(fire);
            return consumed;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            cancel();
            return false;
        }
        boolean letter = key >= KeyEvent.KEYCODE_A && key <= KeyEvent.KEYCODE_Z;
        boolean eligible = letter && event.isCtrlPressed() && !event.isShiftPressed()
                && !event.isAltPressed() && !event.isMetaPressed() && !event.isFunctionPressed()
                && !event.isCanceled() && event.isFromSource(InputDevice.SOURCE_KEYBOARD)
                && event.getDeviceId() != KeyCharacterMap.VIRTUAL_KEYBOARD
                && active();
        String target = eligible ? preferences.getString(PREFIX + (char) ('a' + key - KeyEvent.KEYCODE_A), "") : "";
        int delay = LongPressState.timeout(preferences.getString(TIMEOUT, "700"));
        boolean consumed = state.down(event.getDeviceId(), key, event.getRepeatCount(),
                event.getDownTime(), SystemClock.uptimeMillis(), eligible, target, delay);
        search.removeCallbacks(fire);
        if (state.pending()) search.postDelayed(fire, state.remaining(SystemClock.uptimeMillis()));
        return consumed;
    }

    private boolean active() {
        return search.hasFocus() && search.hasWindowFocus() && search.isShown()
                && !activity.isFinishing() && preferences.getBoolean(ENABLED, false);
    }

    public void cancel() {
        state.cancel();
        search.removeCallbacks(fire);
    }

    public void leaveSession() {
        cancel();
        state.leaveSession();
    }

    private void fire() {
        if (!active()) {
            cancel();
            return;
        }
        String id = state.fire(SystemClock.uptimeMillis());
        if (id == null) return;
        Pojo item = KissApplication.getApplication(activity).getDataHandler().getItemById(id);
        if (item instanceof AppPojo) {
            Result.fromPojo(activity, item).fastLaunch(activity, search);
        } else {
            Toast.makeText(activity, R.string.keyboard_shortcuts_missing, Toast.LENGTH_SHORT).show();
        }
    }
}
