package fr.neamar.kiss.ui;

import static android.content.res.Configuration.UI_MODE_NIGHT_MASK;
import static android.content.res.Configuration.UI_MODE_NIGHT_NO;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;

import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Source: <a href="https://github.com/willli666/Android-Trebuchet-Launcher-Standalone/blob/master/src/com/cyanogenmod/trebuchet/LauncherAppWidgetHostView.java">LauncherAppWidgetHostView.java</a>
 */
public class WidgetView extends AppWidgetHostView {
    protected boolean mHasPerformedLongPress;
    private CheckForLongPress mPendingCheckForLongPress;
    private float xPos;
    private float yPos;

    private final WidgetSwipe swipe = new WidgetSwipe();
    private java.util.function.Consumer<String> swipeListener;
    private boolean swipeOwned;

    public void setSwipeListener(java.util.function.Consumer<String> listener) {
        swipeListener = listener;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            swipeOwned = false;
            String mode = prefs.getString("widget-swipe-mode", "auto");
            float density = getResources().getDisplayMetrics().density;
            float edge = 24 * density;
            boolean atEdge = event.getX() < edge || event.getX() > getWidth() - edge
                    || event.getY() < edge || event.getY() > getHeight() - edge;
            boolean editing = getParent() instanceof WidgetGrid && ((WidgetGrid) getParent()).isEditing();
            boolean eligible = !editing && swipeListener != null && ("edge".equals(mode) && atEdge
                    || "auto".equals(mode) && !scrollableAt(this, event.getX(), event.getY()));
            int threshold = 48;
            try { threshold = Integer.parseInt(prefs.getString("widget-swipe-distance", "48")); }
            catch (NumberFormatException ignored) { }
            threshold = Math.max(32, Math.min(160, threshold));
            swipe.start(event.getX(), event.getY(), event.getEventTime(), threshold * density,
                    Math.min(400, ViewConfiguration.getLongPressTimeout()), eligible);
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_CANCEL || mHasPerformedLongPress) {
            swipe.cancel();
        }
        if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1 && !swipeOwned
                && swipe.move(event.getX(), event.getY(), event.getEventTime())) {
            String direction = WidgetSwipe.direction(event.getX() - xPos, event.getY() - yPos, 0);
            String fallback = "down".equals(direction) ? "display-notifications" : "up".equals(direction) ? "display-keyboard" : "display-apps";
            if ("do-nothing".equals(prefs.getString("gesture-" + direction, fallback))) {
                swipe.cancel();
            } else {
                swipeOwned = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                cancelPendingLongPress();
                mHasPerformedLongPress = false;
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
            }
        }
        if (swipeOwned) {
            // Parent remains free to intercept a second pointer for area scrolling.
            if (action == MotionEvent.ACTION_UP) {
                String direction = swipe.finish(event.getX(), event.getY(), event.getEventTime());
                swipeOwned = false;
                if (direction != null && swipeListener != null) swipeListener.accept(direction);
            } else if (action == MotionEvent.ACTION_CANCEL) {
                swipeOwned = false;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) swipe.cancel();
        return super.dispatchTouchEvent(event);
    }

    private static boolean scrollableAt(android.view.View view, float x, float y) {
        if (view instanceof android.widget.AbsListView || view instanceof android.widget.ScrollView
                || view instanceof android.widget.HorizontalScrollView || view instanceof android.webkit.WebView
                || view instanceof androidx.recyclerview.widget.RecyclerView
                || view.canScrollVertically(-1) || view.canScrollVertically(1)
                || view.canScrollHorizontally(-1) || view.canScrollHorizontally(1)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                android.view.View child = group.getChildAt(i);
                float cx = x + group.getScrollX() - child.getLeft(), cy = y + group.getScrollY() - child.getTop();
                if (child.getVisibility() == VISIBLE && cx >= 0 && cy >= 0 && cx < child.getWidth() && cy < child.getHeight()
                        && scrollableAt(child, cx, cy)) return true;
            }
        }
        return false;
    }

    private static final int POOL_SIZE = Math.max(Runtime.getRuntime().availableProcessors(), 2);
    private static final int KEEP_ALIVE = 1;

    /**
     * An {@link ThreadPoolExecutor} to be used with async task with no limit on the queue size.
     */
    public static final ThreadPoolExecutor THREAD_POOL_EXECUTOR;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(POOL_SIZE, POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        } else {
            THREAD_POOL_EXECUTOR = null;
        }
    }

    public WidgetView(Context context) {
        super(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setExecutor(THREAD_POOL_EXECUTOR);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setOnLightBackground(context.getResources().getConfiguration().uiMode);
        }
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Consume any touch events for ourselves after long press is triggered
        if (mHasPerformedLongPress) {
            mHasPerformedLongPress = false;
            return true;
        }

        // Watch for long press events at this level to make sure
        // users can always pick up this widget
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                postCheckForLongClick();
                xPos = ev.getX();
                yPos = ev.getY();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (Math.abs(ev.getX() - xPos) > ViewConfiguration.get(getContext()).getScaledTouchSlop() || Math.abs(ev.getY() - yPos) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                    mHasPerformedLongPress = false;
                    cancelPendingLongPress();
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mHasPerformedLongPress = false;
                cancelPendingLongPress();
                break;
        }

        // Otherwise continue letting touch events fall through to children
        return false;
    }

    /**
     * Cancel the pending long-press check here too: once a parent has
     * intercepted a gesture, the resulting ACTION_CANCEL bypasses
     * {@link #onInterceptTouchEvent} entirely.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                mHasPerformedLongPress = false;
                cancelPendingLongPress();
                break;
        }
        return super.onTouchEvent(ev);
    }

    protected class CheckForLongPress implements Runnable {
        private int mOriginalWindowAttachCount;

        public void run() {
            if ((getParent() != null) && hasWindowFocus()
                    && mOriginalWindowAttachCount == getWindowAttachCount()
                    && !WidgetView.this.mHasPerformedLongPress) {
                if (performLongClick()) {
                    WidgetView.this.mHasPerformedLongPress = true;
                }
            }
        }

        void rememberWindowAttachCount() {
            mOriginalWindowAttachCount = getWindowAttachCount();
        }
    }

    private void postCheckForLongClick() {
        mHasPerformedLongPress = false;

        if (mPendingCheckForLongPress == null) {
            mPendingCheckForLongPress = new CheckForLongPress();
        }
        mPendingCheckForLongPress.rememberWindowAttachCount();
        postDelayed(mPendingCheckForLongPress, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelPendingLongPress() {
        if (mPendingCheckForLongPress != null) {
            removeCallbacks(mPendingCheckForLongPress);
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallow) {
        // Internal scrolling/seek controls own their gesture; do not open the editor mid-scroll.
        if (disallow) { super.cancelLongPress(); mHasPerformedLongPress = false; cancelPendingLongPress(); }
        super.requestDisallowInterceptTouchEvent(disallow);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        swipe.cancel();
        swipeOwned = false;

        mHasPerformedLongPress = false;
        cancelPendingLongPress();
    }

    @Override
    public int getDescendantFocusability() {
        return ViewGroup.FOCUS_BLOCK_DESCENDANTS;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // calculate size in dips
        float density = getResources().getDisplayMetrics().density;
        int widthDips = (int) (w / density);
        int heightDips = (int) (h / density);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateAppWidgetSize(Bundle.EMPTY, Collections.singletonList(new SizeF(widthDips, heightDips)));
        } else {
            updateAppWidgetSize(null, widthDips, heightDips, widthDips, heightDips);
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    public void setOnLightBackground(int uiMode) {
        switch (uiMode & UI_MODE_NIGHT_MASK) {
            case UI_MODE_NIGHT_NO:
                setOnLightBackground(true);
            case UI_MODE_NIGHT_YES:
                setOnLightBackground(false);
        }
    }
}
