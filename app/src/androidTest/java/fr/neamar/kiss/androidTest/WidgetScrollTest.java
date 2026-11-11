package fr.neamar.kiss.androidTest;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import org.junit.Rule;
import org.junit.Test;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.ui.WidgetScrollView;
import static org.junit.Assert.*;

public class WidgetScrollTest {
    @Rule public ActivityScenarioRule<MainActivity> rule = new ActivityScenarioRule<>(MainActivity.class);

    private void event(WidgetScrollView view, long down, int action, int count, float y) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new MotionEvent.PointerCoords();
            coordinates[i].x = 100 + i * 50;
            coordinates[i].y = y;
            coordinates[i].pressure = 1;
        }
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                count, properties, coordinates, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    @Test public void oneFingerBelongsToChildTwoFingersScrollWidgetArea() {
        rule.getScenario().onActivity(a -> {
            WidgetScrollView scroll = new WidgetScrollView(a);
            View child = new View(a);
            // ScrollView measures its content with an unspecified height. A bare
            // View has no intrinsic height, unlike the real widget grid.
            child.setMinimumHeight(2000);
            child.setOnTouchListener((v, e) -> true);
            scroll.addView(child, new FrameLayout.LayoutParams(600, 2000));
            a.addContentView(scroll, new FrameLayout.LayoutParams(600, 600));
            scroll.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
            scroll.layout(0, 0, 600, 600);
            assertTrue("Fixture must overflow the viewport", child.getHeight() > scroll.getHeight());
            long down = SystemClock.uptimeMillis();
            event(scroll, down, MotionEvent.ACTION_DOWN, 1, 400);
            event(scroll, down, MotionEvent.ACTION_MOVE, 1, 300);
            assertEquals(0, scroll.getScrollY());
            event(scroll, down, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 300);
            event(scroll, down, MotionEvent.ACTION_MOVE, 2, 100);
            assertTrue("Two fingers should scroll overflowing content", scroll.getScrollY() > 0);
            event(scroll, down, MotionEvent.ACTION_CANCEL, 2, 100);
        });
    }
}
