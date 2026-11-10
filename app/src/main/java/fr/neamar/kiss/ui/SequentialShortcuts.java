package fr.neamar.kiss.ui;

import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.InputDevice;
import android.view.inputmethod.BaseInputConnection;
import androidx.preference.PreferenceManager;
import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.pojo.AppPojo;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.result.Result;

/** Only invoked before the IME. Never replays or delays normal input. */
public final class SequentialShortcuts {
    public static final String ENABLED = "sequential-shortcuts-enabled";
    public static final String PREFIX = "sequential-shortcuts-prefix";
    public static final String TIMEOUT = "sequential-shortcuts-timeout";
    public static final String BINDING = "sequential-shortcuts-key-";
    private final MainActivity activity;
    private final SearchEditText editor;
    private final SharedPreferences prefs;
    private final KeySequence sequence = new KeySequence();
    // An IME can retain roman letters without exposing text or composing spans.
    private boolean inputUncertain;

    public SequentialShortcuts(MainActivity activity, SearchEditText editor) {
        this.activity = activity;
        this.editor = editor;
        prefs = PreferenceManager.getDefaultSharedPreferences(activity);
    }

    public void cancel() { sequence.cancel(); }

    public void inputChanged() {
        cancel();
        inputUncertain = true;
    }

    public void newInputSession() {
        cancel();
        inputUncertain = false;
    }

    /** Explicit search clearing also asks the IME to discard its private composition. */
    public void searchCleared() {
        cancel();
        if (prefs.getBoolean(ENABLED, false) && editor.hasFocus()) {
            android.view.inputmethod.InputMethodManager manager =
                    (android.view.inputmethod.InputMethodManager) activity.getSystemService(
                            android.content.Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.restartInput(editor);
            // Stay conservative until onCreateInputConnection confirms a new session.
        }
    }

    private int number(String key, int fallback) {
        try { return Integer.parseInt(prefs.getString(key, Integer.toString(fallback))); }
        catch (NumberFormatException exception) { return fallback; }
    }

    public boolean onKey(KeyEvent event) {
        boolean hardware = event.getDeviceId() >= 0
                && event.isFromSource(InputDevice.SOURCE_KEYBOARD)
                && (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) == 0;
        boolean eligible = hardware && prefs.getBoolean(ENABLED, false)
                && editor.hasFocus() && editor.hasWindowFocus() && !inputUncertain
                && editor.getText() != null && editor.getText().length() == 0
                && BaseInputConnection.getComposingSpanStart(editor.getText()) < 0
                && BaseInputConnection.getComposingSpanEnd(editor.getText()) < 0
                && !event.isCtrlPressed() && !event.isAltPressed()
                && !event.isMetaPressed() && !event.isShiftPressed()
                && event.getKeyCode() != KeyEvent.KEYCODE_ESCAPE;
        String id = prefs.getString(BINDING + event.getKeyCode(), "");
        Pojo target = !eligible || id.isEmpty() ? null
                : KissApplication.getApplication(activity).getDataHandler().getPojo(id);
        boolean mapped = target instanceof AppPojo && !target.isDisabled();
        KeySequence.Decision decision = sequence.event(event.getKeyCode(), event.getDeviceId(),
                event.getDownTime(), event.getEventTime(), event.getAction() == KeyEvent.ACTION_DOWN,
                event.getRepeatCount() != 0, event.isCanceled() || event.getAction() == KeyEvent.ACTION_MULTIPLE,
                eligible, number(PREFIX, KeyEvent.KEYCODE_SPACE),
                Math.max(300, Math.min(2000, number(TIMEOUT, 800))), mapped);
        if (decision == KeySequence.Decision.LAUNCH) {
            Result.fromPojo(activity, target).launch(activity, editor, activity);
        }
        if (decision != KeySequence.Decision.PASS) return true;
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && (event.isPrintingKey() || event.getKeyCode() == KeyEvent.KEYCODE_SPACE
                || event.getKeyCode() == KeyEvent.KEYCODE_DEL)) inputUncertain = true;
        return false;
    }
}
