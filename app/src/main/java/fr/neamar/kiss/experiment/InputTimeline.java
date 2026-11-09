package fr.neamar.kiss.experiment;

import android.content.Context;
import android.os.SystemClock;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.widget.EditText;

import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import fr.neamar.kiss.BuildConfig;

/** Opt-in diagnostic recording; not a long-press implementation or IME rollback. */
public final class InputTimeline {
    public static final String ENABLED = "input-timeline-enabled";
    private static final TraceBuffer BUFFER = new TraceBuffer(4096);
    private static volatile boolean enabled;
    private static long sequence;

    private InputTimeline() { }

    public static synchronized void setEnabled(boolean value) {
        enabled = BuildConfig.DEBUG && value;
    }

    public static boolean enabled() { return enabled; }

    public static synchronized void clear() {
        BUFFER.clear();
        sequence = 0;
    }

    private static String quote(CharSequence value) {
        if (value == null) return "null";
        String text = value.toString();
        return JSONObject.quote(text.length() > 256 ? text.substring(0, 256) : text);
    }

    /** Times use uptime, the same clock domain as KeyEvent.eventTime. */
    public static synchronized void record(String stage, EditText view, String detail) {
        if (!enabled) return;
        long begin = System.nanoTime();
        Editable text = view == null ? null : view.getText();
        String row = "{\"seq\":" + (++sequence) + ",\"t_ns\":" + begin
                + ",\"uptime_ms\":" + SystemClock.uptimeMillis()
                + ",\"thread\":" + Thread.currentThread().getId()
                + ",\"stage\":" + quote(stage) + ",\"detail\":" + quote(detail)
                + ",\"text\":" + quote(text)
                + ",\"length\":" + (text == null ? -1 : text.length())
                + ",\"selectionStart\":" + (view == null ? -1 : view.getSelectionStart())
                + ",\"selectionEnd\":" + (view == null ? -1 : view.getSelectionEnd())
                + ",\"composingStart\":" + (text == null ? -1 : BaseInputConnection.getComposingSpanStart(text))
                + ",\"composingEnd\":" + (text == null ? -1 : BaseInputConnection.getComposingSpanEnd(text));
        long cost = System.nanoTime() - begin;
        BUFFER.add(row + ",\"encode_ns\":" + cost + "}");
    }

    public static void key(String stage, EditText view, KeyEvent event) {
        if (!enabled) return;
        record(stage, view, "action=" + event.getAction() + " key=" + event.getKeyCode()
                + " repeat=" + event.getRepeatCount() + " device=" + event.getDeviceId()
                + " meta=" + event.getMetaState() + " flags=" + event.getFlags()
                + " down_ms=" + event.getDownTime() + " event_ms=" + event.getEventTime());
    }

    public static List<String> snapshot() { return BUFFER.snapshot(); }

    /** Caller must run export off the UI/input thread. */
    public static File export(Context context) throws java.io.IOException {
        List<String> rows = BUFFER.snapshot();
        File file = new File(context.getFilesDir(), "input-timeline.jsonl");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(("{\"format\":1,\"dropped\":" + BUFFER.dropped()
                    + ",\"clock\":\"nanoTime plus uptime_ms for KeyEvent correlation\",\"source\":\"KISS editor observations; not IME internal state\"}\n")
                    .getBytes(StandardCharsets.UTF_8));
            for (String row : rows) output.write((row + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
