package fr.neamar.kiss.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.appcompat.widget.AppCompatEditText;
import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.experiment.InputTimeline;
import fr.neamar.kiss.experiment.TracingInputConnection;

public class SearchEditText extends AppCompatEditText {
    private OnEditorActionListener mEditorListener;
    private int observedKey = -1;
    private int observedDevice;
    private long observedDownTime;
    private long revision;
    private long drawnRevision = -1;
    private final Runnable holdMarker = () -> InputTimeline.record("hold.700ms.observed", this,
            "key=" + observedKey + "; observation only; no launch or rollback");

    public SearchEditText(Context context) {
        super(context);
        installObserver();
    }

    public SearchEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        installObserver();
    }

    public SearchEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        installObserver();
    }

    private void installObserver() {
        if (!BuildConfig.DEBUG) return;
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                InputTimeline.record("text.before", SearchEditText.this, "start=" + start + " removed=" + count + " inserted=" + after);
            }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                revision++;
                InputTimeline.record("text.changed", SearchEditText.this, "start=" + start + " removed=" + before + " inserted=" + count);
            }
            @Override public void afterTextChanged(Editable text) {
                InputTimeline.record("text.after", SearchEditText.this, "");
            }
        });
    }

    @Override public InputConnection onCreateInputConnection(EditorInfo info) {
        InputConnection connection = super.onCreateInputConnection(info);
        InputTimeline.record("ic.created", this, "inputType=" + info.inputType);
        return connection == null || !BuildConfig.DEBUG ? connection : new TracingInputConnection(connection, this);
    }

    @Override public boolean dispatchKeyEventPreIme(KeyEvent event) {
        InputTimeline.key("preIme.enter", this, event);
        if (InputTimeline.enabled()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                cancelObservation("new key");
                if (event.getKeyCode() == KeyEvent.KEYCODE_F && event.hasNoModifiers() && length() == 0) {
                    observedKey = event.getKeyCode();
                    observedDevice = event.getDeviceId();
                    observedDownTime = event.getDownTime();
                    InputTimeline.record("hold.candidate", this, "empty editor; F; no event consumed");
                    postDelayed(holdMarker, 700);
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == observedKey
                    && event.getDeviceId() == observedDevice) {
                InputTimeline.record("hold.release", this, "held_ms=" + (event.getEventTime() - observedDownTime));
                cancelObservation("release");
            }
            if (event.isCanceled()) cancelObservation("cancelled event");
        }
        boolean result = super.dispatchKeyEventPreIme(event);
        InputTimeline.record("preIme.return", this, "consumed=" + result);
        return result;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        InputTimeline.key("view.postIme.enter", this, event);
        boolean result = super.dispatchKeyEvent(event);
        InputTimeline.record("view.postIme.return", this, "consumed=" + result);
        return result;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (InputTimeline.enabled() && revision != drawnRevision) {
            drawnRevision = revision;
            InputTimeline.record("draw.return", this, "revision=" + revision + "; not a compositor presentation timestamp");
        }
    }

    public void cancelObservation(String reason) {
        if (observedKey != -1) InputTimeline.record("hold.end", this, reason);
        removeCallbacks(holdMarker);
        observedKey = -1;
    }

    @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        if (!focused) cancelObservation("editor focus lost");
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        InputTimeline.record("window.focus", this, "focused=" + focused);
        if (!focused) cancelObservation("window focus lost");
    }

    @Override protected void onDetachedFromWindow() {
        cancelObservation("detach");
        super.onDetachedFromWindow();
    }

    @Override
    public void setOnEditorActionListener(OnEditorActionListener listener) {
        mEditorListener = listener;
        super.setOnEditorActionListener(listener);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP)
            if (mEditorListener != null && mEditorListener.onEditorAction(this, android.R.id.closeButton, event))
                return true;
        return super.onKeyPreIme(keyCode, event);
    }
}
