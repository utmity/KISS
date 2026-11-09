package fr.neamar.kiss.androidTest;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.*;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.util.List;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.experiment.InputTimeline;
import fr.neamar.kiss.experiment.TracingInputConnection;
import fr.neamar.kiss.ui.SearchEditText;

/** Controlled calls test instrumentation transparency, not any real IME's conversion engine. */
@RunWith(AndroidJUnit4.class)
public class InputTimelineTest {
    private ActivityScenario<MainActivity> scenario;
    private static Object contacts;
    private static Object tracing;
    private static SharedPreferences prefs;

    @BeforeClass public static void beforeClass() {
        prefs = PreferenceManager.getDefaultSharedPreferences(getInstrumentation().getTargetContext());
        contacts = prefs.getAll().get("enable-contacts");
        tracing = prefs.getAll().get(InputTimeline.ENABLED);
        assertTrue(prefs.edit().putBoolean("enable-contacts", false).putBoolean(InputTimeline.ENABLED, true).commit());
    }

    @AfterClass public static void afterClass() throws Exception {
        InputTimeline.record("suite.complete", null, "synthetic direct InputConnection/View tests; not physical IME input");
        InputTimeline.export(getInstrumentation().getTargetContext());
        InputTimeline.setEnabled(false);
        SharedPreferences.Editor editor = prefs.edit();
        if (contacts instanceof Boolean) editor.putBoolean("enable-contacts", (Boolean) contacts);
        else editor.remove("enable-contacts");
        if (tracing instanceof Boolean) editor.putBoolean(InputTimeline.ENABLED, (Boolean) tracing);
        else editor.remove(InputTimeline.ENABLED);
        assertTrue(editor.commit());
    }

    @Before public void before() {
        scenario = ActivityScenario.launch(MainActivity.class);
        getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> {
            activity.searchEditText.requestFocus();
            activity.searchEditText.setText("");
            InputTimeline.setEnabled(true);
            InputTimeline.record("test.begin", activity.searchEditText, "synthetic");
        });
    }

    @After public void after() { if (scenario != null) scenario.close(); }

    private static int index(List<String> rows, String stage, int from) {
        for (int i = from; i < rows.size(); i++) {
            if (rows.get(i).contains("\"stage\":\"" + stage + "\"")) return i;
        }
        throw new AssertionError("Missing " + stage);
    }

    @Test public void commitUpdatesSearchSynchronouslyBeforeRelease() {
        scenario.onActivity(activity -> {
            int start = InputTimeline.snapshot().size();
            SearchEditText view = activity.searchEditText;
            InputConnection connection = view.onCreateInputConnection(new EditorInfo());
            long now = SystemClock.uptimeMillis();
            assertFalse(view.dispatchKeyEventPreIme(new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_F, 0, 0, 1, 0, 0, InputDevice.SOURCE_KEYBOARD)));
            assertTrue(connection.commitText("f", 1));
            assertEquals("f", view.getText().toString());
            assertFalse(view.dispatchKeyEventPreIme(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_F, 0, 0, 1, 0, 0, InputDevice.SOURCE_KEYBOARD)));
            List<String> rows = InputTimeline.snapshot();
            int before = index(rows, "ic.commitText.before", start);
            int changed = index(rows, "text.changed", before);
            int search = index(rows, "search.update", changed);
            int after = index(rows, "ic.commitText.after", search);
            assertTrue(index(rows, "hold.release", after) > after);
        });
    }

    @Test public void composingReplacementAndCommitKeepSpans() {
        scenario.onActivity(activity -> {
            InputConnection connection = activity.searchEditText.onCreateInputConnection(new EditorInfo());
            assertTrue(connection.setComposingText("f", 1));
            assertEquals(0, BaseInputConnection.getComposingSpanStart(activity.searchEditText.getText()));
            assertTrue(connection.setComposingText("ふ", 1));
            assertEquals("ふ", activity.searchEditText.getText().toString());
            assertTrue(connection.setComposingText("ふじ", 1));
            assertEquals("ふじ", activity.searchEditText.getText().toString());
            assertTrue(connection.commitText("富士", 1));
            assertEquals("富士", activity.searchEditText.getText().toString());
            assertEquals(-1, BaseInputConnection.getComposingSpanStart(activity.searchEditText.getText()));
        });
    }

    @Test public void secondCharacterAndRepetitionAreImmediateAndOrdered() {
        scenario.onActivity(activity -> {
            InputConnection connection = activity.searchEditText.onCreateInputConnection(new EditorInfo());
            String expected = "";
            for (char c : "fujifffffABC".toCharArray()) {
                expected += c;
                assertTrue(connection.commitText(String.valueOf(c), 1));
                assertEquals(expected, activity.searchEditText.getText().toString());
            }
        });
    }

    @Test public void recordingOffDoesNotChangeEditing() {
        scenario.onActivity(activity -> {
            InputTimeline.setEnabled(false);
            int count = InputTimeline.snapshot().size();
            InputConnection connection = activity.searchEditText.onCreateInputConnection(new EditorInfo());
            connection.setComposingText("ふ", 1);
            connection.commitText("府", 1);
            assertEquals("府", activity.searchEditText.getText().toString());
            assertEquals(count, InputTimeline.snapshot().size());
            InputTimeline.setEnabled(true);
        });
    }

    @Test public void wrapperForwardsOriginalArgumentsExactlyOnce() {
        scenario.onActivity(activity -> {
            final int[] calls = {0};
            CharSequence payload = new android.text.SpannableString("ふ");
            InputConnection delegate = new BaseInputConnection(activity.searchEditText, true) {
                @Override public boolean setComposingText(CharSequence text, int position) {
                    calls[0]++;
                    assertSame(payload, text);
                    assertEquals(-2, position);
                    return false;
                }
            };
            InputConnection wrapper = new TracingInputConnection(delegate, activity.searchEditText);
            assertFalse(wrapper.setComposingText(payload, -2));
            assertEquals(1, calls[0]);
        });
    }

    @Test public void batchEditAndDeletionPreserveFinalText() {
        scenario.onActivity(activity -> {
            InputConnection connection = activity.searchEditText.onCreateInputConnection(new EditorInfo());
            connection.beginBatchEdit();
            connection.commitText("abc", 1);
            connection.deleteSurroundingText(1, 0);
            connection.endBatchEdit();
            assertEquals("ab", activity.searchEditText.getText().toString());
        });
    }

    @Test public void recordsAreValidOrderedJson() throws Exception {
        long prior = -1;
        for (String row : InputTimeline.snapshot()) {
            JSONObject object = new JSONObject(row);
            assertTrue(object.getLong("seq") > prior);
            prior = object.getLong("seq");
            assertTrue(object.has("composingStart"));
            assertTrue(object.has("encode_ns"));
        }
    }
}
