package fr.neamar.kiss.androidTest;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.R;
import fr.neamar.kiss.ui.SearchEditText;
import fr.neamar.kiss.ui.SequentialShortcuts;
import static org.junit.Assert.*;

/** View/IME integration on a disposable emulator; not a hardware keyboard test. */
public class SequentialInputTest {
    @BeforeClass public static void disableUnrelatedPermissionRequests() {
        // A fresh emulator has no contacts/phone grants. Those dialogs take window
        // focus away from the editor and are unrelated to keyboard shortcuts.
        PreferenceManager.getDefaultSharedPreferences(
                InstrumentationRegistry.getInstrumentation().getTargetContext()).edit()
                .putBoolean("enable-contacts", false).putBoolean("enable-phone", false).commit();
    }

    @Rule public ActivityScenarioRule<MainActivity> rule = new ActivityScenarioRule<>(MainActivity.class);

    @Before public void waitForWindowFocus() {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] focused = {false};
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            rule.getScenario().onActivity(a -> focused[0] = a.hasWindowFocus());
            if (focused[0]) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Launcher must have window focus before keyboard input is tested");
    }

    private KeyEvent key(int code, int action, long down, int repeat) {
        return new KeyEvent(down, SystemClock.uptimeMillis(), action, code, repeat,
                0, 1, 0, 0, InputDevice.SOURCE_KEYBOARD) {
            // Device 1 can be a non-printing virtual control on older emulators.
            // Model a QWERTY hardware keyboard consistently for these synthetic events.
            @Override public boolean isPrintingKey() {
                return android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD)
                        .isPrintingKey(getKeyCode());
            }
        };
    }

    private SearchEditText setup(MainActivity activity, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
                .putBoolean(SequentialShortcuts.ENABLED, enabled)
                .putString(SequentialShortcuts.PREFIX, "62")
                .remove(SequentialShortcuts.BINDING + KeyEvent.KEYCODE_F).commit();
        SearchEditText editor = activity.searchEditText;
        editor.requestFocus();
        editor.setText("");
        editor.onCreateInputConnection(new EditorInfo());
        return editor;
    }

    @Test public void disabledAndNonemptySearchPassPrefixImmediately() {
        rule.getScenario().onActivity(a -> {
            SearchEditText editor = setup(a, false);
            long now = SystemClock.uptimeMillis();
            assertFalse(editor.dispatchKeyEventPreIme(key(62, KeyEvent.ACTION_DOWN, now, 0)));
            setup(a, true);
            editor.setText("検索");
            assertFalse(editor.dispatchKeyEventPreIme(key(62, KeyEvent.ACTION_DOWN, now + 1, 0)));
            assertEquals("検索", editor.getText().toString());
        });
    }

    @Test public void prefixRepeatAndReleaseConsumedUnknownSecondKeyPasses() {
        rule.getScenario().onActivity(a -> {
            SearchEditText editor = setup(a, true);
            long now = SystemClock.uptimeMillis();
            assertTrue(editor.hasWindowFocus());
            assertTrue(editor.dispatchKeyEventPreIme(key(62, KeyEvent.ACTION_DOWN, now, 0)));
            assertTrue(editor.dispatchKeyEventPreIme(key(62, KeyEvent.ACTION_DOWN, now, 1)));
            assertTrue(editor.dispatchKeyEventPreIme(key(62, KeyEvent.ACTION_UP, now, 0)));
            assertFalse(editor.dispatchKeyEventPreIme(key(KeyEvent.KEYCODE_F, KeyEvent.ACTION_DOWN, now + 1, 0)));
            assertEquals("", editor.getText().toString());
        });
    }

    @Test public void hiddenImeInputAndEmptyCompositionBlockPrefix() {
        rule.getScenario().onActivity(a -> {
            SearchEditText editor = setup(a, true);
            long now = SystemClock.uptimeMillis();
            assertFalse(editor.dispatchKeyEventPreIme(key(KeyEvent.KEYCODE_K, KeyEvent.ACTION_DOWN, now, 0)));
            assertEquals(0, editor.length()); // Simulates an IME retaining the roman letter.
            assertFalse(editor.dispatchKeyEventPreIme(key(62, KeyEvent.ACTION_DOWN, now + 1, 0)));
            InputConnection connection = editor.onCreateInputConnection(new EditorInfo());
            assertNotNull(connection);
            connection.setComposingText("", 1);
            assertFalse(editor.dispatchKeyEventPreIme(key(62, KeyEvent.ACTION_DOWN, now + 2, 0)));
            editor.onCreateInputConnection(new EditorInfo());
            assertTrue(editor.dispatchKeyEventPreIme(key(62, KeyEvent.ACTION_DOWN, now + 3, 0)));
        });
    }

    @Test public void newDefaultDoesNotOverwriteExistingPreferences() {
        rule.getScenario().onActivity(a -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(a);
            prefs.edit().putString("widget-swipe-mode", "off")
                    .putString("widget-grid-v1-90001", "1,2,2,3")
                    .putString(SequentialShortcuts.PREFIX, "29")
                    .putString(SequentialShortcuts.BINDING + 34, "saved-app-id").commit();
            PreferenceManager.setDefaultValues(a, R.xml.preferences, true);
            assertEquals("off", prefs.getString("widget-swipe-mode", ""));
            assertEquals("1,2,2,3", prefs.getString("widget-grid-v1-90001", ""));
            assertEquals("29", prefs.getString(SequentialShortcuts.PREFIX, ""));
            assertEquals("saved-app-id", prefs.getString(SequentialShortcuts.BINDING + 34, ""));
            prefs.edit().remove("widget-swipe-mode").commit();
            PreferenceManager.setDefaultValues(a, R.xml.preferences, true);
            assertEquals("auto", prefs.getString("widget-swipe-mode", ""));
        });
    }
}
