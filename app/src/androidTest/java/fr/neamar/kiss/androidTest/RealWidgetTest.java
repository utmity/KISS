package fr.neamar.kiss.androidTest;
import android.appwidget.*;
import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.R;
import fr.neamar.kiss.ui.WidgetGrid;

/** Uses the installed system analog clock, with bind permission granted to the debug package. */
public class RealWidgetTest {
    @org.junit.BeforeClass public static void disableUnrelatedProviders() {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        PreferenceManager.getDefaultSharedPreferences(c).edit().putBoolean("enable-contacts",false).putBoolean("enable-phone",false).putBoolean("force-portrait",false).commit();
    }
    @Rule public ActivityScenarioRule<MainActivity> rule = new ActivityScenarioRule<>(MainActivity.class);
    @Test public void realClockLongPressDragAndActivityRestore() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        AppWidgetProviderInfo info=null;
        for (AppWidgetProviderInfo candidate:manager.getInstalledProviders()) {
            if (candidate.provider.getClassName().endsWith("AnalogAppWidgetProvider")) info=candidate;
        }
        assumeNotNull(info);
        AppWidgetHost host=new AppWidgetHost(context,442);
        int id=host.allocateAppWidgetId();
        try {
            assertTrue("Grant debug widget bind permission before this test",manager.bindAppWidgetIdIfAllowed(id,info.provider));
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("history-hide",true).putString("widgets-conf",id+"-2").commit();
            rule.getScenario().recreate();
            final long down=SystemClock.uptimeMillis();
            rule.getScenario().onActivity(a->{
                WidgetGrid grid=a.findViewById(R.id.widgetLayout);
                assertEquals(1,grid.getChildCount());
                MotionEvent e=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,30,30,0);
                grid.dispatchTouchEvent(e);e.recycle();
            });
            SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout()+150);
            final int[] savedY={0};
            rule.getScenario().onActivity(a->{
                WidgetGrid grid=a.findViewById(R.id.widgetLayout);
                assertTrue("Real WidgetView enters edit after holding",grid.isEditing());
                for (int action:new int[]{MotionEvent.ACTION_MOVE,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}) {
                    MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,30,330,0);
                    grid.dispatchTouchEvent(e);e.recycle();
                }
                savedY[0]=((WidgetGrid.Cell)grid.getChildAt(0).getLayoutParams()).y;
                assertTrue(savedY[0]>0);
                grid.finishEdit();
            });
            rule.getScenario().recreate();
            rule.getScenario().onActivity(a->{
                WidgetGrid grid=a.findViewById(R.id.widgetLayout);
                assertEquals(savedY[0],((WidgetGrid.Cell)grid.getChildAt(0).getLayoutParams()).y);
                assertEquals(id,((AppWidgetHostView)grid.getChildAt(0)).getAppWidgetId());
                assertTrue(manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) > 0);
                fr.neamar.kiss.ui.WidgetDisplaySettings.setScalePercent(a,id,150);
                fr.neamar.kiss.ui.WidgetDisplaySettings.setIgnoresLimits(a,id,true);
            });
            // Rotate the emulator display; the activity restores the user's orientation
            // preference on recreation, so a one-off setRequestedOrientation is insufficient.
            InstrumentationRegistry.getInstrumentation().getUiAutomation().setRotation(android.app.UiAutomation.ROTATION_FREEZE_90);
            final boolean[] landscape={false};
            long deadline=SystemClock.uptimeMillis()+8000;
            while (!landscape[0] && SystemClock.uptimeMillis()<deadline) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                rule.getScenario().onActivity(a->landscape[0]=a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE);
                if (!landscape[0]) SystemClock.sleep(100);
            }
            assertTrue("Activity rotated to landscape",landscape[0]);
            rule.getScenario().onActivity(a->{
                WidgetGrid grid=a.findViewById(R.id.widgetLayout);
                AppWidgetHostView view=(AppWidgetHostView)grid.getChildAt(0);
                assertEquals(id,view.getAppWidgetId());
                assertEquals(savedY[0],((WidgetGrid.Cell)view.getLayoutParams()).y);
                assertTrue(fr.neamar.kiss.ui.WidgetDisplaySettings.ignoresLimits(a,id));
                assertEquals(150,fr.neamar.kiss.ui.WidgetDisplaySettings.scalePercent(a,id));
                android.graphics.Rect padding=AppWidgetHostView.getDefaultPaddingForWidget(a,view.getAppWidgetInfo().provider,null);
                float density=a.getResources().getDisplayMetrics().density;
                int expected=(int)(view.getWidth()/density)-(int)((padding.left+padding.right)/density);
                assertEquals(expected,manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),1);
                if (android.os.Build.VERSION.SDK_INT>=31) assertNotNull(manager.getAppWidgetOptions(id).getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES));
            });
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().setRotation(android.app.UiAutomation.ROTATION_FREEZE_0);
            InstrumentationRegistry.getInstrumentation().getUiAutomation().setRotation(android.app.UiAutomation.ROTATION_UNFREEZE);
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString("widgets-conf","").remove("widget-grid-v1-"+id).commit();
            host.deleteAppWidgetId(id);
            fr.neamar.kiss.ui.WidgetDisplaySettings.remove(context,id);
        }
    }
}
