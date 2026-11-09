package fr.neamar.kiss.ui;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.preference.PreferenceManager;

/** Four-column, vertically scrolling widget workspace. Only editing captures touches. */
public class WidgetGrid extends LinearLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences prefs;
    private final int row;
    private View selected;
    private float downX, downY;
    private int startX, startY, startW, startH;
    private boolean resizing, dragging, valid = true;
    private Runnable changed;
    private OnClickListener menu;

    public static class Cell extends LinearLayout.LayoutParams {
        public int x, y, span = 4, rows;
        public Cell(int height, int row) {
            super(MATCH_PARENT, height);
            rows = Math.max(1, (height + row - 1) / row);
        }
    }

    public WidgetGrid(Context context, AttributeSet attrs) {
        super(context, attrs);
        row = Math.round(48 * getResources().getDisplayMetrics().density);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setWillNotDraw(false);
    }

    public void setEditCallbacks(Runnable changed, OnClickListener menu) {
        this.changed = changed;
        this.menu = menu;
    }

    public void place(AppWidgetHostView view, int height, int spacing) {
        Cell c = new Cell(height, row);
        c.bottomMargin = spacing;
        for (int i = 0; i < getChildCount(); i++) {
            Cell other = (Cell) getChildAt(i).getLayoutParams();
            c.y = Math.max(c.y, other.y + other.rows);
        }
        String saved = prefs.getString("widget-grid-v1-" + view.getAppWidgetId(), "");
        try {
            String[] values = saved.split(",");
            if (values.length == 4) {
                c.x = Integer.parseInt(values[0]); c.y = Integer.parseInt(values[1]);
                c.span = Integer.parseInt(values[2]); c.rows = Integer.parseInt(values[3]);
                if (!WidgetGridRules.valid(c.x, c.y, c.span, c.rows)) throw new NumberFormatException();
            }
        } catch (NumberFormatException ignored) {
            c.x = 0; c.span = 4; c.rows = Math.max(1, (height + row - 1) / row);
            c.y = 0;
            for (int i = 0; i < getChildCount(); i++) {
                Cell other = (Cell) getChildAt(i).getLayoutParams();
                c.y = Math.max(c.y, other.y + other.rows);
            }
        }
        view.setLayoutParams(c);
        // Recover overlapping/corrupt saved positions without losing a widget.
        while (overlaps(view, c)) c.y++;
        addView(view);
    }

    public void save() {
        SharedPreferences.Editor editor = prefs.edit();
        for (int i = 0; i < getChildCount(); i++) {
            AppWidgetHostView child = (AppWidgetHostView) getChildAt(i);
            Cell c = (Cell) child.getLayoutParams();
            editor.putString("widget-grid-v1-" + child.getAppWidgetId(), c.x + "," + c.y + "," + c.span + "," + c.rows);
        }
        editor.apply();
    }

    public boolean isEditing() { return selected != null; }

    public void beginEdit(View child) {
        selected = child;
        child.cancelLongPress();
        getParent().requestDisallowInterceptTouchEvent(true);
        snapshot();
        resizing = false;
        dragging = true;
        child.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        announceForAccessibility(getContext().getString(fr.neamar.kiss.R.string.widget_edit_hint));
        android.widget.Toast.makeText(getContext(), fr.neamar.kiss.R.string.widget_edit_hint, android.widget.Toast.LENGTH_SHORT).show();
        invalidate();
    }

    public boolean finishEdit() {
        if (selected == null) return false;
        finishDrag(false);
        selected = null;
        getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
        return true;
    }

    private void snapshot() {
        Cell c = (Cell) selected.getLayoutParams();
        startX = c.x; startY = c.y; startW = c.span; startH = c.rows;
        valid = true;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY();
        }
        return super.dispatchTouchEvent(event);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        return selected != null;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (selected == null) return false;
        Cell c = (Cell) selected.getLayoutParams();
        int unit = Math.max(1, getWidth() / 4);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                float x = event.getX(), y = event.getY();
                if (x < selected.getLeft() || x > selected.getRight() || y < selected.getTop() || y > selected.getBottom()) {
                    finishEdit(); return true;
                }
                if (x >= selected.getRight() - row && y < selected.getTop() + row) {
                    View target = selected;
                    finishEdit();
                    if (menu != null) menu.onClick(target);
                    return true;
                }
                snapshot();
                resizing = x >= selected.getRight() - row && y >= selected.getBottom() - row;
                dragging = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging || event.getPointerCount() != 1) break;
                int dx = Math.round((event.getX() - downX) / unit);
                int dy = Math.round((event.getY() - downY) / row);
                if (resizing) {
                    AppWidgetProviderInfo info = ((AppWidgetHostView) selected).getAppWidgetInfo();
                    boolean horizontal = info == null || (info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0;
                    boolean vertical = info == null || (info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0;
                    int minW = info == null ? 1 : Math.max(1, (int) Math.ceil((double) (info.minResizeWidth > 0 ? info.minResizeWidth : info.minWidth) / unit));
                    int minH = info == null ? 1 : Math.max(1, (int) Math.ceil((double) (info.minResizeHeight > 0 ? info.minResizeHeight : info.minHeight) / row));
                    if (horizontal) c.span = Math.max(Math.min(minW, 4 - c.x), Math.min(4 - c.x, startW + dx));
                    if (vertical) c.rows = Math.max(minH, Math.min(200, startH + dy));
                } else {
                    c.x = Math.max(0, Math.min(4 - c.span, startX + dx));
                    c.y = Math.max(0, Math.min(10000, startY + dy));
                }
                valid = !overlaps(selected, c);
                requestLayout(); invalidate();
                if (getParent() instanceof ScrollView) {
                    ScrollView scroll = (ScrollView) getParent();
                    float local = event.getY() - scroll.getScrollY();
                    if (local < row) scroll.scrollBy(0, -row / 4);
                    else if (local > scroll.getHeight() - row) scroll.scrollBy(0, row / 4);
                }
                break;
            case MotionEvent.ACTION_UP: finishDrag(false); break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL: finishDrag(true); break;
        }
        return true;
    }

    private void finishDrag(boolean cancel) {
        if (!dragging || selected == null) return;
        Cell c = (Cell) selected.getLayoutParams();
        if (cancel || !valid) { c.x = startX; c.y = startY; c.span = startW; c.rows = startH; }
        dragging = false; valid = true;
        requestLayout(); invalidate();
        save();
        if (changed != null) changed.run();
    }

    private boolean overlaps(View target, Cell c) {
        for (int i = 0; i < getChildCount(); i++) {
            View other = getChildAt(i);
            if (other == target) continue;
            Cell o = (Cell) other.getLayoutParams();
            if (WidgetGridRules.overlaps(c.x, c.y, c.span, c.rows, o.x, o.y, o.span, o.rows)) return true;
        }
        return false;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), bottom = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            Cell c = (Cell) child.getLayoutParams();
            c.height = Math.max(row, c.rows * row - c.bottomMargin);
            child.measure(MeasureSpec.makeMeasureSpec(width * c.span / 4, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(c.height, MeasureSpec.EXACTLY));
            bottom = Math.max(bottom, (c.y + c.rows) * row);
        }
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(bottom, heightSpec));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            Cell c = (Cell) child.getLayoutParams();
            int left = getWidth() * c.x / 4, top = c.y * row;
            child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (selected == null || selected.getParent() != this) return;
        paint.setColor(valid ? Color.rgb(0, 150, 220) : Color.RED);
        paint.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(selected.getLeft() + 2, selected.getTop() + 2, selected.getRight() - 2, selected.getBottom() - 2, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(selected.getRight() - row, selected.getBottom() - row, selected.getRight(), selected.getBottom(), paint);
        canvas.drawRect(selected.getRight() - row, selected.getTop(), selected.getRight(), selected.getTop() + row, paint);
        paint.setColor(Color.WHITE); paint.setTextSize(row / 2f);
        canvas.drawText("↔", selected.getRight() - row * .85f, selected.getBottom() - row * .3f, paint);
        canvas.drawText("⋮", selected.getRight() - row * .65f, selected.getTop() + row * .65f, paint);
    }

    @Override public void onViewRemoved(View child) {
        if (child == selected) finishEdit();
        super.onViewRemoved(child);
    }

    @Override protected void onDetachedFromWindow() {
        finishEdit();
        super.onDetachedFromWindow();
    }
}
