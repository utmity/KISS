package fr.neamar.kiss.androidTest;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.SizeF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import java.util.List;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.ui.WidgetDisplaySettings;
import fr.neamar.kiss.ui.WidgetGrid;
import fr.neamar.kiss.ui.WidgetView;
import static org.junit.Assert.*;

/** Fixed-size RemoteViews-like root, independent of any third-party provider. */
public class WidgetSizingTest {
    private static final int ID = 91001;
    @BeforeClass public static void permissions() {
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().getTargetContext())
                .edit().putBoolean("enable-contacts", false).putBoolean("enable-phone", false).commit();
    }
    @Rule public ActivityScenarioRule<MainActivity> rule = new ActivityScenarioRule<>(MainActivity.class);
    private WidgetGrid grid;
    private Host host;
    private int clicks;

    private static class Host extends WidgetView {
        final AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        int notices, widthDp, heightDp;
        Host(Context context) { super(context); }
        @Override public int getAppWidgetId() { return ID; }
        @Override public AppWidgetProviderInfo getAppWidgetInfo() { return info; }
        @Override public void updateAppWidgetSize(Bundle options, List<SizeF> sizes) {
            notices++; widthDp = (int) sizes.get(0).getWidth(); heightDp = (int) sizes.get(0).getHeight();
        }
        @Override public void updateAppWidgetSize(Bundle options, int w, int h, int maxW, int maxH) {
            notices++; widthDp = w; heightDp = h;
        }
    }
    private void setup(MainActivity a, boolean clear) {
        if (clear) {
            WidgetDisplaySettings.remove(a, ID);
            PreferenceManager.getDefaultSharedPreferences(a).edit().remove("widget-grid-v1-" + ID)
                    .putString("widget-swipe-mode", "off").commit();
        }
        grid = new WidgetGrid(a, null);
        a.addContentView(grid, new FrameLayout.LayoutParams(600, 1600));
        host = new Host(a);
        grid.place(host, 500, 0);
        layout();
    }
    private void layout() {
        grid.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, 600, 1600);
    }
    private void event(View target, int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        target.dispatchTouchEvent(event); event.recycle();
    }
    private void resize(int columns, int rows) {
        grid.beginEdit(host); event(grid, MotionEvent.ACTION_UP, 0, 0);
        float x = host.getRight() - 5, y = host.getBottom() - 5;
        int row = Math.round(48 * host.getResources().getDisplayMetrics().density);
        event(grid, MotionEvent.ACTION_DOWN, x, y);
        event(grid, MotionEvent.ACTION_MOVE, x + columns * 150, y + rows * row);
        event(grid, MotionEvent.ACTION_UP, x + columns * 150, y + rows * row);
        grid.finishEdit(); layout();
    }
    private WidgetGrid.Cell cell() { return (WidgetGrid.Cell) host.getLayoutParams(); }

    @Test public void fixedProviderOnlyResizesWhenOverrideEnabled() {
        rule.getScenario().onActivity(a -> {
            setup(a, true); int rows = cell().rows;
            resize(-3, -100); assertEquals(4, cell().span); assertEquals(rows, cell().rows);
            host.info.minWidth = 600; host.info.minHeight = 500;
            WidgetDisplaySettings.setIgnoresLimits(a, ID, true);
            resize(-3, -100); assertEquals(1, cell().span); assertEquals(1, cell().rows);
            resize(100, 300); assertEquals(4, cell().span); assertEquals(200, cell().rows);
        });
    }
    @Test public void normalResizeHonorsMinimumMaximumAndDirection() {
        rule.getScenario().onActivity(a -> {
            setup(a, true); host.info.resizeMode = AppWidgetProviderInfo.RESIZE_HORIZONTAL;
            host.info.minResizeWidth = 300;
            if (android.os.Build.VERSION.SDK_INT >= 31) host.info.maxResizeWidth = 450;
            int rows = cell().rows;
            resize(-3, -100); assertEquals(2, cell().span); assertEquals(rows, cell().rows);
            resize(10, 10);
            assertEquals(android.os.Build.VERSION.SDK_INT >= 31 ? 3 : 4, cell().span);
            assertEquals(rows, cell().rows);
            WidgetDisplaySettings.setIgnoresLimits(a, ID, true);
            resize(10, 1); assertEquals(4, cell().span); assertEquals(rows + 1, cell().rows);
        });
    }
    @Test public void forcedResizeStillRejectsCollisions() {
        rule.getScenario().onActivity(a -> {
            setup(a, true); WidgetDisplaySettings.setIgnoresLimits(a, ID, true);
            android.appwidget.AppWidgetHostView other = new android.appwidget.AppWidgetHostView(a) {
                @Override public int getAppWidgetId() { return ID + 1; }
            };
            PreferenceManager.getDefaultSharedPreferences(a).edit().remove("widget-grid-v1-" + (ID + 1)).commit();
            grid.place(other, 200, 0); int rows = cell().rows;
            layout(); resize(0, 1); assertEquals(rows, cell().rows);
        });
    }
    private View fixedContent(MainActivity a) {
        host.setBackgroundColor(Color.WHITE);
        View root = new View(a);
        root.setBackgroundColor(Color.RED);
        root.setOnClickListener(v -> clicks++);
        host.addView(root, new FrameLayout.LayoutParams(200, 100, Gravity.CENTER));
        return root;
    }
    @Test public void zoomActuallyChangesPixelsAndTapCoordinates() {
        rule.getScenario().onActivity(a -> {
            setup(a, true); View root = fixedContent(a);
            for (int percent : new int[]{50, 200}) {
                WidgetDisplaySettings.setScalePercent(a, ID, percent); host.requestLayout(); layout();
                int cx = host.getWidth() / 2, cy = host.getHeight() / 2;
                Bitmap bitmap = Bitmap.createBitmap(host.getWidth(), host.getHeight(), Bitmap.Config.ARGB_8888);
                host.draw(new Canvas(bitmap));
                int radius = percent; // fixed root width 200 * scale / 2
                assertEquals(Color.RED, bitmap.getPixel(cx + radius - 2, cy));
                assertEquals(Color.WHITE, bitmap.getPixel(cx + radius + 2, cy));
                bitmap.recycle();
                // At 200% this point lies outside the untransformed root but must be clickable.
                float x = cx + radius - 10;
                event(host, MotionEvent.ACTION_DOWN, x, cy); event(host, MotionEvent.ACTION_UP, x, cy);
                assertEquals(percent / 100f, root.getScaleX(), .001f);
                assertEquals(4, cell().span);
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync(); assertEquals(2, clicks);
    }
    @Test public void defaultAndResetPreserveProvidersOwnTransform() {
        rule.getScenario().onActivity(a -> {
            setup(a, true); View root = fixedContent(a); root.setScaleX(.8f); root.setScaleY(.9f);
            root.setPivotX(12); root.setPivotY(16); layout();
            assertEquals(.8f, root.getScaleX(), .001f); assertEquals(12, root.getPivotX(), .001f);
            WidgetDisplaySettings.setScalePercent(a, ID, 150); host.requestLayout(); layout();
            assertEquals(1.2f, root.getScaleX(), .001f);
            WidgetDisplaySettings.setScalePercent(a, ID, 100); host.requestLayout(); layout();
            assertEquals(.8f, root.getScaleX(), .001f); assertEquals(.9f, root.getScaleY(), .001f);
            assertEquals(12, root.getPivotX(), .001f); assertEquals(16, root.getPivotY(), .001f);
        });
    }
    @Test public void enlargedContentIsClippedToItsOwnCell() {
        rule.getScenario().onActivity(a -> {
            setup(a, true); fixedContent(a); cell().span = 1;
            WidgetDisplaySettings.setScalePercent(a, ID, 200); host.requestLayout(); layout();
            Bitmap bitmap = Bitmap.createBitmap(600, 1600, Bitmap.Config.ARGB_8888);
            grid.draw(new Canvas(bitmap));
            assertEquals(Color.RED, bitmap.getPixel(149, host.getHeight() / 2));
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(151, host.getHeight() / 2));
            bitmap.recycle();
        });
    }
    @Test public void autoSwipeRespectsTransformedScrollArea() {
        rule.getScenario().onActivity(a -> {
            setup(a, true);
            PreferenceManager.getDefaultSharedPreferences(a).edit().putString("widget-swipe-mode", "auto").commit();
            WidgetDisplaySettings.setScalePercent(a, ID, 200);
            ScrollView scroll = new ScrollView(a); View content = new View(a); content.setMinimumHeight(2000);
            scroll.addView(content, new ScrollView.LayoutParams(-1, 2000));
            host.addView(scroll, new FrameLayout.LayoutParams(200, 100, Gravity.CENTER));
            final int[] gestures = {0}; host.setSwipeListener(direction -> gestures[0]++); layout();
            long now = SystemClock.uptimeMillis();
            for (int i = 0; i < 3; i++) {
                MotionEvent e = MotionEvent.obtain(now, now + i * 80, new int[]{0, 2, 1}[i],
                        scroll.getLeft() - 20, host.getHeight() / 2f + (i == 0 ? 0 : -250), 0);
                host.dispatchTouchEvent(e); e.recycle();
            }
            assertEquals(0, gestures[0]);
        });
    }
    @Test public void sizeNotificationsFollowViewportNotZoomAndRestoreOnRecreation() {
        rule.getScenario().onActivity(a -> {
            setup(a, true); assertEquals(1, host.notices);
            float density = a.getResources().getDisplayMetrics().density;
            assertEquals((int) (600 / density), host.widthDp);
            cell().span = 2; host.requestLayout(); grid.requestLayout(); layout();
            assertEquals(2, host.notices); assertEquals((int) (300 / density), host.widthDp);
            WidgetDisplaySettings.setScalePercent(a, ID, 175);
            WidgetDisplaySettings.setIgnoresLimits(a, ID, true);
            host.requestLayout(); layout(); assertEquals(2, host.notices);
            grid.save();
        });
        rule.getScenario().recreate();
        rule.getScenario().onActivity(a -> {
            setup(a, false); assertEquals(2, cell().span); assertEquals(1, host.notices);
            assertTrue(WidgetDisplaySettings.ignoresLimits(a, ID));
            assertEquals(175, WidgetDisplaySettings.scalePercent(a, ID));
            View root = fixedContent(a); layout(); assertEquals(1.75f, root.getScaleX(), .001f);
        });
    }
}
