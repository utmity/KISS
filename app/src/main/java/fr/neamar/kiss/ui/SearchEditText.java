package fr.neamar.kiss.ui;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.TextAttribute;

import androidx.appcompat.widget.AppCompatEditText;

public class SearchEditText extends AppCompatEditText {
    private OnEditorActionListener mEditorListener;
    private SequentialShortcuts shortcuts;

    public void setSequentialShortcuts(SequentialShortcuts shortcuts) {
        this.shortcuts = shortcuts;
    }

    public void cancelKeySequence() {
        if (shortcuts != null) shortcuts.cancel();
    }

    public void onSearchCleared() {
        if (shortcuts != null) shortcuts.searchCleared();
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (shortcuts != null && shortcuts.onKey(event)) return true;
        return super.dispatchKeyEventPreIme(event);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo info) {
        cancelKeySequence();
        InputConnection connection = super.onCreateInputConnection(info);
        if (shortcuts != null) shortcuts.newInputSession();
        if (connection == null) return null;
        return new InputConnectionWrapper(connection, false) {
            private void changed() {
                if (shortcuts != null) shortcuts.inputChanged();
            }

            @Override
            public boolean setComposingText(CharSequence text, int cursor) {
                changed();
                return super.setComposingText(text, cursor);
            }

            @Override
            public boolean setComposingRegion(int start, int end) {
                changed();
                return super.setComposingRegion(start, end);
            }

            @Override
            public boolean commitText(CharSequence text, int cursor) {
                changed();
                return super.commitText(text, cursor);
            }

            @Override
            @androidx.annotation.RequiresApi(33)
            public boolean setComposingText(CharSequence text, int cursor, TextAttribute attribute) {
                changed();
                return super.setComposingText(text, cursor, attribute);
            }

            @Override
            @androidx.annotation.RequiresApi(33)
            public boolean setComposingRegion(int start, int end, TextAttribute attribute) {
                changed();
                return super.setComposingRegion(start, end, attribute);
            }

            @Override
            @androidx.annotation.RequiresApi(33)
            public boolean commitText(CharSequence text, int cursor, TextAttribute attribute) {
                changed();
                return super.commitText(text, cursor, attribute);
            }
        };
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect rect) {
        cancelKeySequence();
        super.onFocusChanged(focused, direction, rect);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int before, int count) {
        if (shortcuts != null) shortcuts.inputChanged();
        super.onTextChanged(text, start, before, count);
    }

    public SearchEditText(Context context) {
        super(context);
    }

    public SearchEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SearchEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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
