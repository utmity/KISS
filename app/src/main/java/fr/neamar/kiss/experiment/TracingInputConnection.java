package fr.neamar.kiss.experiment;

import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.TextAttribute;
import android.widget.EditText;
import androidx.annotation.RequiresApi;

/** Every intercepted call is forwarded exactly once, synchronously and unchanged. */
public final class TracingInputConnection extends InputConnectionWrapper {
    private final EditText view;
    public TracingInputConnection(InputConnection target, EditText view) {
        super(target, false);
        this.view = view;
    }

    @Override public boolean commitText(CharSequence text, int position) {
        InputTimeline.record("ic.commitText.before", view, "position=" + position);
        boolean result = super.commitText(text, position);
        InputTimeline.record("ic.commitText.after", view, "returned=" + result);
        return result;
    }

    @Override public boolean setComposingText(CharSequence text, int position) {
        InputTimeline.record("ic.setComposingText.before", view, "position=" + position);
        boolean result = super.setComposingText(text, position);
        InputTimeline.record("ic.setComposingText.after", view, "returned=" + result);
        return result;
    }

    @RequiresApi(33)
    @Override public boolean commitText(CharSequence text, int position, TextAttribute attributes) {
        InputTimeline.record("ic.commitText.before", view, "api33 position=" + position);
        boolean result = super.commitText(text, position, attributes);
        InputTimeline.record("ic.commitText.after", view, "returned=" + result);
        return result;
    }

    @RequiresApi(33)
    @Override public boolean setComposingText(CharSequence text, int position, TextAttribute attributes) {
        InputTimeline.record("ic.setComposingText.before", view, "api33 position=" + position);
        boolean result = super.setComposingText(text, position, attributes);
        InputTimeline.record("ic.setComposingText.after", view, "returned=" + result);
        return result;
    }

    @Override public boolean setComposingRegion(int start, int end) {
        InputTimeline.record("ic.setComposingRegion.before", view, "start=" + start + " end=" + end);
        boolean result = super.setComposingRegion(start, end);
        InputTimeline.record("ic.setComposingRegion.after", view, "returned=" + result);
        return result;
    }

    @RequiresApi(33)
    @Override public boolean setComposingRegion(int start, int end, TextAttribute attributes) {
        InputTimeline.record("ic.setComposingRegion.before", view, "api33 start=" + start + " end=" + end);
        boolean result = super.setComposingRegion(start, end, attributes);
        InputTimeline.record("ic.setComposingRegion.after", view, "returned=" + result);
        return result;
    }

    @Override public boolean finishComposingText() {
        InputTimeline.record("ic.finishComposingText.before", view, "");
        boolean result = super.finishComposingText();
        InputTimeline.record("ic.finishComposingText.after", view, "returned=" + result);
        return result;
    }

    @Override public boolean beginBatchEdit() {
        InputTimeline.record("ic.beginBatchEdit.before", view, "");
        boolean result = super.beginBatchEdit();
        InputTimeline.record("ic.beginBatchEdit.after", view, "returned=" + result);
        return result;
    }

    @Override public boolean endBatchEdit() {
        InputTimeline.record("ic.endBatchEdit.before", view, "");
        boolean result = super.endBatchEdit();
        InputTimeline.record("ic.endBatchEdit.after", view, "returned=" + result);
        return result;
    }

    @Override public boolean deleteSurroundingText(int before, int after) {
        InputTimeline.record("ic.deleteSurroundingText.before", view, "before=" + before + " after=" + after);
        boolean result = super.deleteSurroundingText(before, after);
        InputTimeline.record("ic.deleteSurroundingText.after", view, "returned=" + result);
        return result;
    }

    @RequiresApi(24)
    @Override public boolean deleteSurroundingTextInCodePoints(int before, int after) {
        InputTimeline.record("ic.deleteSurroundingTextInCodePoints.before", view, "before=" + before + " after=" + after);
        boolean result = super.deleteSurroundingTextInCodePoints(before, after);
        InputTimeline.record("ic.deleteSurroundingTextInCodePoints.after", view, "returned=" + result);
        return result;
    }

    @Override public boolean setSelection(int start, int end) {
        InputTimeline.record("ic.setSelection.before", view, "start=" + start + " end=" + end);
        boolean result = super.setSelection(start, end);
        InputTimeline.record("ic.setSelection.after", view, "returned=" + result);
        return result;
    }

    @Override public boolean sendKeyEvent(KeyEvent event) {
        InputTimeline.record("ic.sendKeyEvent.before", view, "key=" + event.getKeyCode() + " action=" + event.getAction());
        boolean result = super.sendKeyEvent(event);
        InputTimeline.record("ic.sendKeyEvent.after", view, "returned=" + result);
        return result;
    }
}
