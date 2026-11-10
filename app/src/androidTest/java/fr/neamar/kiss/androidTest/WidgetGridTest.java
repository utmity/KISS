package fr.neamar.kiss.androidTest;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.ui.WidgetGrid;

public class WidgetGridTest {
    @Rule public ActivityScenarioRule<MainActivity> rule = new ActivityScenarioRule<>(MainActivity.class);
    private WidgetGrid grid;
    private MainActivity activity;
    private AppWidgetHostView add(int id) {
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(activity, MainActivity.class);
        info.resizeMode = AppWidgetProviderInfo.RESIZE_BOTH;
        AppWidgetHostView view = new AppWidgetHostView(activity) {
            @Override public int getAppWidgetId() { return id; }
            @Override public AppWidgetProviderInfo getAppWidgetInfo() { return info; }
        };
        grid.place(view, 144, 0);
        return view;
    }
    private void setup(MainActivity a) {
        activity = a;
        PreferenceManager.getDefaultSharedPreferences(a).edit().remove("widget-grid-v1-90001").remove("widget-grid-v1-90002").commit();
        grid = new WidgetGrid(a, null);
        a.addContentView(grid, new FrameLayout.LayoutParams(600, 1200));
    }
    private void layout() {
        grid.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY));
        grid.layout(0,0,600,1200);
    }
    private void event(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(now,now,action,x,y,0);
        grid.dispatchTouchEvent(e); e.recycle();
    }
    @Test public void migratesVerticalAndRestoresSideBySide() {
        rule.getScenario().onActivity(a -> {
            setup(a); AppWidgetHostView first = add(90001), second = add(90002);
            WidgetGrid.Cell f = (WidgetGrid.Cell) first.getLayoutParams(), s = (WidgetGrid.Cell) second.getLayoutParams();
            assertEquals(4,f.span); assertEquals(f.rows,s.y);
            f.span=2; s.span=2; s.x=2; s.y=0; grid.save();
            grid.removeAllViews(); first=add(90001); second=add(90002); layout();
            assertEquals(first.getTop(),second.getTop()); assertEquals(first.getRight(),second.getLeft());
        });
    }
    @Test public void dragMovesAndCancelRestores() {
        rule.getScenario().onActivity(a -> {
            setup(a); AppWidgetHostView first=add(90001); WidgetGrid.Cell c=(WidgetGrid.Cell)first.getLayoutParams(); c.span=2; layout();
            event(MotionEvent.ACTION_DOWN,40,40); grid.beginEdit(first);
            event(MotionEvent.ACTION_MOVE,190,340); event(MotionEvent.ACTION_UP,190,340);
            assertEquals(1,c.x); assertTrue(c.y>0); int y=c.y; layout();
            event(MotionEvent.ACTION_DOWN,first.getLeft()+20,first.getTop()+20);
            event(MotionEvent.ACTION_MOVE,first.getLeft()+20,first.getTop()+220);
            event(MotionEvent.ACTION_CANCEL,0,0); assertEquals(y,c.y);
            assertTrue(grid.finishEdit()); assertFalse(grid.isEditing());
        });
    }
    @Test public void overlappingDropRollsBack() {
        rule.getScenario().onActivity(a -> {
            setup(a); AppWidgetHostView first=add(90001),second=add(90002); layout();
            WidgetGrid.Cell c=(WidgetGrid.Cell)first.getLayoutParams();
            event(MotionEvent.ACTION_DOWN,20,20); grid.beginEdit(first);
            event(MotionEvent.ACTION_MOVE,20,20+second.getTop()); event(MotionEvent.ACTION_UP,20,20+second.getTop());
            assertEquals(0,c.y);
        });
    }
    @Test public void resizeHandleChangesBothDimensions() {
        rule.getScenario().onActivity(a -> {
            setup(a); AppWidgetHostView first=add(90001); WidgetGrid.Cell c=(WidgetGrid.Cell)first.getLayoutParams(); c.span=2; c.rows=3; layout();
            grid.beginEdit(first); event(MotionEvent.ACTION_UP,0,0);
            float x=first.getRight()-10,y=first.getBottom()-10;
            event(MotionEvent.ACTION_DOWN,x,y); event(MotionEvent.ACTION_MOVE,x+150,y+150); event(MotionEvent.ACTION_UP,x+150,y+150);
            assertEquals(3,c.span); assertTrue(c.rows>3);
        });
    }
    @Test public void normalTapReachesWidgetChild() {
        final int[] clicks={0};
        rule.getScenario().onActivity(a -> {
            setup(a); AppWidgetHostView first=add(90001); first.removeAllViews();
            android.widget.Button button=new android.widget.Button(a); button.setOnClickListener(v->clicks[0]++);
            first.addView(button,new FrameLayout.LayoutParams(-1,-1)); layout();
            event(MotionEvent.ACTION_DOWN,40,40); event(MotionEvent.ACTION_UP,40,40);
            assertFalse(grid.isEditing());
        });
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(1,clicks[0]);
    }
}
