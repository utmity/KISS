package fr.neamar.kiss.keyboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class LongPressStateTest {
    private final LongPressState state = new LongPressState();

    private boolean down() {
        return state.down(1, 34, 0, 100, 100, true, "app", 700);
    }

    @Test void shortChordDoesNotLaunch() {
        assertTrue(down());
        assertTrue(state.up(1, 34));
        assertNull(state.fire(1000));
    }

    @Test void deadlineIsInclusiveAndLaunchesOnce() {
        assertTrue(down());
        assertNull(state.fire(799));
        assertEquals("app", state.fire(800));
        assertNull(state.fire(900));
        assertTrue(state.up(1, 34));
    }

    @Test void repeatDoesNotResetDeadline() {
        down();
        assertTrue(state.down(1, 34, 1, 100, 600, true, "app", 700));
        assertEquals(200, state.remaining(600));
        assertEquals("app", state.fire(800));
    }

    @Test void repeatAfterLaunchDoesNotRelaunch() {
        down();
        state.fire(800);
        assertTrue(state.down(1, 34, 5, 100, 1000, true, "app", 700));
        assertNull(state.fire(2000));
    }

    @Test void ordinaryLettersPassThrough() {
        assertFalse(state.down(1, 34, 0, 100, 100, false, "app", 700));
        assertFalse(state.up(1, 34));
        assertNull(state.fire(800));
    }

    @Test void unassignedChordsPassThrough() {
        assertFalse(state.down(1, 34, 0, 100, 100, true, "", 700));
        assertFalse(state.up(1, 34));
    }

    @Test void orphanRepeatCannotStartShortcut() {
        assertFalse(state.down(1, 34, 2, 100, 400, true, "app", 700));
        assertNull(state.fire(1500));
    }

    @Test void cancellationConsumesTailWithoutLaunching() {
        down();
        state.cancel();
        assertTrue(state.down(1, 34, 2, 100, 600, false, "", 700));
        assertTrue(state.up(1, 34));
        assertNull(state.fire(2000));
    }

    @Test void anotherKeyCancelsWithoutSwallowingThatKey() {
        down();
        assertFalse(state.down(1, 35, 0, 200, 200, true, "other", 700));
        assertFalse(state.up(1, 35));
        assertNull(state.fire(1000));
        assertTrue(state.up(1, 34));
    }

    @Test void otherDeviceReleaseDoesNotReleaseCandidate() {
        down();
        assertFalse(state.up(2, 34));
        assertEquals("app", state.fire(800));
    }

    @Test void otherDevicePressCancelsCandidate() {
        down();
        assertFalse(state.down(2, 34, 0, 200, 200, true, "other", 700));
        assertNull(state.fire(1000));
        assertTrue(state.up(1, 34));
    }

    @Test void successiveChordsCanLaunchAgain() {
        down();
        assertEquals("app", state.fire(800));
        state.up(1, 34);
        assertTrue(state.down(1, 34, 0, 1000, 1000, true, "second", 700));
        assertEquals("second", state.fire(1700));
    }

    @Test void freshDownRecoversMissingRelease() {
        down();
        state.cancel();
        assertTrue(state.down(1, 34, 0, 1000, 1000, true, "second", 700));
        assertNull(state.fire(1699));
        assertEquals("second", state.fire(1700));
    }

    @Test void staleDownDoesNotImmediatelyLaunch() {
        assertTrue(state.down(1, 34, 0, 10, 10000, true, "app", 700));
        assertNull(state.fire(10001));
        assertEquals("app", state.fire(10700));
    }

    @Test void losingEligibilityDuringRepeatCancels() {
        down();
        assertTrue(state.down(1, 34, 1, 100, 500, false, "app", 700));
        assertNull(state.fire(800));
        assertTrue(state.up(1, 34));
    }

    @Test void releaseInAnotherAppDoesNotBlockNextShortcut() {
        down();
        state.fire(800);
        state.leaveSession();
        assertTrue(state.down(1, 35, 0, 2000, 2000, true, "other", 700));
        assertEquals("other", state.fire(2700));
    }

    @Test void returningWhileHeldStillConsumesOldRepeats() {
        down();
        state.leaveSession();
        assertTrue(state.down(1, 34, 3, 100, 2000, false, "", 700));
        assertNull(state.fire(3000));
        assertTrue(state.up(1, 34));
    }

    @ParameterizedTest
    @CsvSource({"700,700", "200,200", "2000,2000", "-1,200", "9999,2000", "broken,700", "99999999999,700"})
    void timeoutIsBounded(String value, int expected) {
        assertEquals(expected, LongPressState.timeout(value));
    }
}
