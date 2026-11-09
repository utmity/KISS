package fr.neamar.kiss.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WidgetSwipeTest {
    private WidgetSwipe start(boolean eligible) { WidgetSwipe s=new WidgetSwipe(); s.start(0,0,0,48,400,eligible); return s; }
    @Test void shortDragStaysWithWidget() { assertFalse(start(true).move(47,0,100)); }
    @Test void diagonalIsNotClaimed() { assertFalse(start(true).move(70,60,100)); }
    @Test void disabledNeverClaims() { assertFalse(start(false).move(100,0,100)); }
    @Test void slowAndLongPressNotClaimed() { assertFalse(start(true).move(100,0,400)); }
    @Test void directionAndSingleExecution() { WidgetSwipe s=start(true); assertTrue(s.move(0,60,100)); assertEquals("down",s.finish(0,80,200)); assertNull(s.finish(0,80,201)); }
    @Test void reversalCancels() { WidgetSwipe s=start(true); s.move(80,0,100); assertNull(s.finish(-80,0,200)); }
    @Test void multitouchCancelPreventsAction() { WidgetSwipe s=start(true); s.move(80,0,100); s.cancel(); assertNull(s.finish(80,0,200)); }
    @Test void timeoutAfterClaimCancels() { WidgetSwipe s=start(true); s.move(80,0,100); assertNull(s.finish(80,0,1000)); }
    @Test void allDirections() { assertEquals("left",WidgetSwipe.direction(-100,0,48)); assertEquals("right",WidgetSwipe.direction(100,0,48)); assertEquals("up",WidgetSwipe.direction(0,-100,48)); assertEquals("down",WidgetSwipe.direction(0,100,48)); }
}
