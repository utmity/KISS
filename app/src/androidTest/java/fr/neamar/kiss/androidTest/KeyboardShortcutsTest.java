package fr.neamar.kiss.androidTest;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.R;
import fr.neamar.kiss.SettingsActivity;
import fr.neamar.kiss.keyboard.KeyboardShortcuts;
import fr.neamar.kiss.pojo.AppPojo;
import fr.neamar.kiss.ui.KeyboardShortcutLayout;

/** Real Android view/lifecycle integration. Synthetic pre-IME events are NOT physical-key/IME tests. */
@RunWith(AndroidJUnit4.class)
public class KeyboardShortcutsTest {
    private ActivityScenario<MainActivity> scenario;
    private SharedPreferences prefs;
    private final Map<String, Object> saved = new HashMap<>();
    private static final String F = KeyboardShortcuts.PREFIX + "f";
    private static final String G = KeyboardShortcuts.PREFIX + "g";
    private long downTime;
    private static Object savedContacts;

    @BeforeClass public static void disableContactsDuringSuite() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getInstrumentation().getTargetContext());
        savedContacts = preferences.getAll().get("enable-contacts");
        assertTrue(preferences.edit().putBoolean("enable-contacts", false).commit());
    }

    @AfterClass public static void restoreContactsAfterSuite() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getInstrumentation().getTargetContext()).edit();
        if (savedContacts instanceof Boolean) editor.putBoolean("enable-contacts", (Boolean) savedContacts);
        else editor.remove("enable-contacts");
        assertTrue(editor.commit());
    }

    @Before public void setUp() {
        Context context = getInstrumentation().getTargetContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        for (String key : new String[]{KeyboardShortcuts.ENABLED, KeyboardShortcuts.TIMEOUT, F, G}) {
            saved.put(key, prefs.getAll().get(key));
        }
        assertTrue(prefs.edit().putBoolean(KeyboardShortcuts.ENABLED, true).putBoolean("enable-contacts", false)
                .putString(KeyboardShortcuts.TIMEOUT, "200").putString(G, "").commit());
        scenario = ActivityScenario.launch(MainActivity.class);
        // Let the app provider initialize, with a bounded wait.
        AtomicBoolean ready = new AtomicBoolean();
        for (int i = 0; i < 100 && !ready.get(); i++) {
            scenario.onActivity(activity -> {
                java.util.List<AppPojo> apps = KissApplication.getApplication(activity).getDataHandler().getApplications();
                if (apps == null) return;
                for (AppPojo app : apps) {
                    if (app.packageName.equals("com.android.settings")) {
                        prefs.edit().putString(F, app.id).apply();
                        ready.set(true);
                        break;
                    }
                }
                activity.searchEditText.requestFocus();
                activity.searchEditText.setText("");
            });
            SystemClock.sleep(50);
        }
        assertTrue("Settings app must be available", ready.get());
        getInstrumentation().waitForIdleSync();
        AtomicBoolean focused = new AtomicBoolean();
        for (int i = 0; i < 100 && !focused.get(); i++) {
            scenario.onActivity(activity -> focused.set(activity.searchEditText.hasWindowFocus()));
            SystemClock.sleep(50);
        }
        assertTrue("Device must be unlocked and KISS focused", focused.get());
        downTime = SystemClock.uptimeMillis();
    }

    @After public void tearDown() {
        if (scenario != null) scenario.close();
        if (prefs != null) {
            SharedPreferences.Editor editor = prefs.edit();
            for (Map.Entry<String, Object> entry : saved.entrySet()) {
                if (entry.getValue() instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) entry.getValue());
                else if (entry.getValue() instanceof String) editor.putString(entry.getKey(), (String) entry.getValue());
                else editor.remove(entry.getKey());
            }
            assertTrue(editor.commit());
        }
    }

    private boolean event(int action, int key, int meta, int repeat, int flags, int device) {
        AtomicBoolean consumed = new AtomicBoolean();
        scenario.onActivity(activity -> {
            KeyboardShortcutLayout root = activity.findViewById(R.id.keyboardShortcutRoot);
            consumed.set(root.dispatchKeyEventPreIme(new KeyEvent(downTime, SystemClock.uptimeMillis(),
                    action, key, repeat, meta, device, 0, flags, InputDevice.SOURCE_KEYBOARD)));
        });
        return consumed.get();
    }

    private void begin() {
        assertTrue(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON, 0, 0, 1));
    }

    private void assertStillHere() {
        SystemClock.sleep(350);
        scenario.onActivity(activity -> {
            assertTrue(activity.hasWindowFocus());
            assertEquals("", activity.searchEditText.getText().toString());
        });
    }

    @Test public void ordinaryLettersAndShiftPassToIme() {
        assertFalse(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F, 0, 0, 0, 1));
        assertFalse(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F, 0, 0, 0, 1));
        assertFalse(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F, KeyEvent.META_SHIFT_ON, 0, 0, 1));
        assertFalse(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F, KeyEvent.META_SHIFT_ON, 0, 0, 1));
        assertStillHere();
    }

    @Test public void unassignedChordPassesToIme() {
        assertFalse(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_G, KeyEvent.META_CTRL_ON, 0, 0, 1));
        assertFalse(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_G, KeyEvent.META_CTRL_ON, 0, 0, 1));
    }

    @Test public void shortChordIsConsumedWithoutTyping() {
        begin();
        assertTrue(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON, 0, 0, 1));
        assertStillHere();
    }

    @Test public void releasingCtrlCancelsAndConsumesLetterTail() {
        begin();
        assertFalse(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0, 0, 1));
        assertTrue(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F, 0, 1, 0, 1));
        assertTrue(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F, 0, 0, 0, 1));
        assertStillHere();
    }

    @Test public void anotherKeyCancels() {
        begin();
        assertFalse(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_G, 0, 0, 0, 1));
        assertStillHere();
    }

    @Test public void canceledKeyUpCannotLaunch() {
        begin();
        assertTrue(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON, 0, KeyEvent.FLAG_CANCELED, 1));
        assertStillHere();
    }

    @Test public void virtualKeyboardCannotStartShortcut() {
        assertFalse(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON, 0, 0, -1));
        assertStillHere();
    }

    @Test public void modifiedCtrlChordPassesThrough() {
        for (int modifier : new int[]{KeyEvent.META_SHIFT_ON, KeyEvent.META_ALT_ON, KeyEvent.META_META_ON, KeyEvent.META_FUNCTION_ON}) {
            assertFalse(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON | modifier, 0, 0, 1));
            assertFalse(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON | modifier, 0, 0, 1));
        }
    }

    @Test public void disabledFeaturePassesThrough() {
        assertTrue(prefs.edit().putBoolean(KeyboardShortcuts.ENABLED, false).commit());
        assertFalse(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON, 0, 0, 1));
        assertStillHere();
    }

    @Test public void touchCancelsTimer() {
        begin();
        scenario.onActivity(activity -> {
            MotionEvent touch = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 0, 0, 0);
            activity.dispatchTouchEvent(touch);
            touch.recycle();
        });
        assertStillHere();
    }

    @Test public void pauseCancelsTimer() {
        begin();
        scenario.moveToState(Lifecycle.State.CREATED);
        SystemClock.sleep(350);
        scenario.moveToState(Lifecycle.State.RESUMED);
        assertStillHere();
    }

    @Test public void focusLossCancelsTimer() {
        begin();
        scenario.onActivity(activity -> activity.onWindowFocusChanged(false));
        assertStillHere();
    }

    @Test public void longChordLaunchesAssignedAppWithoutLetter() {
        begin();
        SystemClock.sleep(150);
        scenario.onActivity(activity -> assertEquals("", activity.searchEditText.getText().toString()));
        SystemClock.sleep(1000);
        android.view.accessibility.AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
        assertNotNull(root);
        assertEquals("com.android.settings", root.getPackageName().toString());
    }

    @Test public void settingsContainAllLettersAndUnassign() {
        try (ActivityScenario<SettingsActivity> settings = ActivityScenario.launch(SettingsActivity.class)) {
            settings.onActivity(activity -> {
                fr.neamar.kiss.SettingsFragment fragment = (fr.neamar.kiss.SettingsFragment)
                        activity.getSupportFragmentManager().findFragmentByTag(SettingsActivity.ARG_SHOW_FRAGMENT);
                assertNotNull(fragment);
                for (char key = 'a'; key <= 'z'; key++) {
                    androidx.preference.ListPreference preference = fragment.findPreference(KeyboardShortcuts.PREFIX + key);
                    assertNotNull(preference);
                    assertEquals("", preference.getEntryValues()[0]);
                }
            });
        }
    }
}
