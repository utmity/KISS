package fr.neamar.kiss.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WidgetGridRulesTest {
    @Test void sideBySideDoesNotOverlap() { assertFalse(WidgetGridRules.overlaps(0,0,2,2,2,0,2,2)); }
    @Test void verticalEdgesDoNotOverlap() { assertFalse(WidgetGridRules.overlaps(0,0,4,2,0,2,4,2)); }
    @Test void partialOverlapRejected() { assertTrue(WidgetGridRules.overlaps(0,0,3,2,2,1,2,2)); }
    @Test void containmentRejected() { assertTrue(WidgetGridRules.overlaps(0,0,4,4,1,1,1,1)); }
    @Test void boundsValidated() {
        assertTrue(WidgetGridRules.valid(2,0,2,1));
        assertFalse(WidgetGridRules.valid(3,0,2,1));
        assertFalse(WidgetGridRules.valid(3,0,Integer.MAX_VALUE,1));
        assertFalse(WidgetGridRules.valid(0,-1,2,1));
        assertFalse(WidgetGridRules.valid(0,0,0,1));
        assertFalse(WidgetGridRules.valid(0,0,2,201));
    }
}
