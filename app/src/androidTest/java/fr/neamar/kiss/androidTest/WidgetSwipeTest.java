package fr.neamar.kiss.androidTest;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.ui.WidgetView;

public class WidgetSwipeTest {
    @org.junit.BeforeClass public static void disableUnrelatedProviders() {
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().getTargetContext()).edit().putBoolean("enable-contacts",false).commit();
    }
    @Rule public ActivityScenarioRule<MainActivity> rule=new ActivityScenarioRule<>(MainActivity.class);
    private WidgetView widget;
    private final java.util.List<String> actions=new java.util.ArrayList<>();
    private int clicks,cancels;
    private long down;
    private void setup(MainActivity a,String mode,boolean scrolling) {
        PreferenceManager.getDefaultSharedPreferences(a).edit().putString("widget-swipe-mode",mode).putString("widget-swipe-distance","48").putString("gesture-right","display-apps").commit();
        widget=new WidgetView(a) { @Override protected void onSizeChanged(int w,int h,int ow,int oh) { } };
        widget.setSwipeListener(actions::add);
        Button button=new Button(a);
        button.setOnClickListener(v->clicks++);
        button.setOnTouchListener((v,e)->{ if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)cancels++; return false; });
        if(scrolling) {
            ScrollView scroll=new ScrollView(a); scroll.addView(button,new ScrollView.LayoutParams(-1,1800));
            widget.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        } else widget.addView(button,new FrameLayout.LayoutParams(-1,-1));
        a.addContentView(widget,new FrameLayout.LayoutParams(800,1000));
        widget.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));
        widget.layout(0,0,800,1000); down=SystemClock.uptimeMillis();
    }
    private void event(int action,float x,float y,long delay) {
        MotionEvent e=MotionEvent.obtain(down,down+delay,action,x,y,0);
        widget.dispatchTouchEvent(e);e.recycle();
    }
    @Test public void edgeSwipeCancelsChildAndRunsOnlyOnce() {
        rule.getScenario().onActivity(a->{setup(a,"edge",false);event(0,2,300,0);event(2,350,300,100);event(2,450,300,150);event(1,450,300,180);assertEquals(1,actions.size());assertEquals("right",actions.get(0));assertEquals(1,cancels);});
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertEquals(0,clicks);
    }
    @Test public void centerStaysWithWidgetInEdgeMode() {
        rule.getScenario().onActivity(a->{setup(a,"edge",false);event(0,300,300,0);event(2,650,300,100);event(1,650,300,180);assertTrue(actions.isEmpty());});
    }
    @Test public void autoProtectsScrollableDescendant() {
        rule.getScenario().onActivity(a->{setup(a,"auto",true);event(0,300,300,0);event(2,300,650,100);event(1,300,650,180);assertTrue(actions.isEmpty());});
    }
    @Test public void tapStillClicks() {
        rule.getScenario().onActivity(a->{setup(a,"edge",false);event(0,20,300,0);event(1,20,300,80);});
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertEquals(1,clicks);assertTrue(actions.isEmpty());
    }
    @Test public void cancellationAndSecondPointerDoNotExecute() {
        rule.getScenario().onActivity(a->{setup(a,"edge",false);event(0,2,300,0);event(2,350,300,100);event(3,350,300,150);event(1,350,300,180);assertTrue(actions.isEmpty());});
    }
    @Test public void disabledAndDoNothingDoNotSteal() {
        rule.getScenario().onActivity(a->{setup(a,"off",false);event(0,2,300,0);event(2,350,300,100);event(1,350,300,180);assertTrue(actions.isEmpty());assertEquals(0,cancels);
            setup(a,"edge",false);PreferenceManager.getDefaultSharedPreferences(a).edit().putString("gesture-right","do-nothing").commit();event(0,2,300,0);event(2,350,300,100);event(1,350,300,180);assertTrue(actions.isEmpty());assertEquals(0,cancels);
        });
    }
    @Test public void secondPointerCancelsClaim() {
        rule.getScenario().onActivity(a->{
            setup(a,"edge",false);event(0,2,300,0);event(2,350,300,100);
            MotionEvent.PointerProperties p0=new MotionEvent.PointerProperties(),p1=new MotionEvent.PointerProperties();
            p0.id=0;p1.id=1;p0.toolType=1;p1.toolType=1;
            MotionEvent.PointerCoords c0=new MotionEvent.PointerCoords(),c1=new MotionEvent.PointerCoords();
            c0.x=350;c0.y=300;c1.x=400;c1.y=400;c0.pressure=1;c1.pressure=1;
            MotionEvent e=MotionEvent.obtain(down,down+120,MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,new MotionEvent.PointerProperties[]{p0,p1},new MotionEvent.PointerCoords[]{c0,c1},0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
            widget.dispatchTouchEvent(e);e.recycle();event(1,450,300,180);assertTrue(actions.isEmpty());
        });
    }
    @Test public void longPressStillWins() {
        rule.getScenario().onActivity(a->{setup(a,"edge",false);widget.setOnLongClickListener(v->{clicks++;return true;});event(0,2,300,0);});
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout()+150);
        rule.getScenario().onActivity(a->{event(2,350,300,700);event(1,350,300,800);assertTrue(actions.isEmpty());assertEquals(1,clicks);});
    }
}
