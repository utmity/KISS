package fr.neamar.kiss.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RelativeLayout;

import fr.neamar.kiss.keyboard.KeyboardShortcuts;

/** Root interception precedes both SearchEditText and the IME. */
public class KeyboardShortcutLayout extends RelativeLayout {
    private KeyboardShortcuts shortcuts;

    public KeyboardShortcutLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setShortcuts(KeyboardShortcuts shortcuts) {
        this.shortcuts = shortcuts;
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        return (shortcuts != null && shortcuts.onKeyPreIme(event)) || super.dispatchKeyEventPreIme(event);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (shortcuts != null) shortcuts.leaveSession();
        super.requestChildFocus(child, focused);
    }

    @Override
    public void clearChildFocus(View child) {
        if (shortcuts != null) shortcuts.leaveSession();
        super.clearChildFocus(child);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (shortcuts != null) shortcuts.leaveSession();
        super.onDetachedFromWindow();
    }
}
