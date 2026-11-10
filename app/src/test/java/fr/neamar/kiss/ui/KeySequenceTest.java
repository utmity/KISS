package fr.neamar.kiss.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static fr.neamar.kiss.ui.KeySequence.Decision.*;

class KeySequenceTest {
    private final KeySequence sequence = new KeySequence();
    private KeySequence.Decision event(int key, long press, long time, boolean down,
                                       boolean repeat, boolean canceled, boolean eligible, boolean mapped) {
        return sequence.event(key, 1, press, time, down, repeat, canceled, eligible, 62, 800, mapped);
    }
    private void prefix() {
        assertEquals(CONSUME, event(62, 10, 10, true, false, false, true, false));
        assertEquals(CONSUME, event(62, 10, 30, false, false, false, true, false));
    }
    @Test void releaseThenPressLaunchesOnce() {
        prefix();
        assertEquals(LAUNCH, event(34, 100, 100, true, false, false, true, true));
        assertEquals(CONSUME, event(34, 100, 200, true, true, false, true, true));
        assertEquals(CONSUME, event(34, 100, 300, false, false, false, true, true));
        assertEquals(PASS, event(34, 400, 400, true, false, false, true, true));
    }
    @Test void deadlineStartsAtReleaseAndExpiresInclusively() {
        event(62, 10, 10, true, false, false, true, false);
        event(62, 10, 900, false, false, false, true, false);
        assertEquals(LAUNCH, event(34, 1699, 1699, true, false, false, true, true));
        prefix();
        assertEquals(PASS, event(34, 830, 830, true, false, false, true, true));
    }
    @Test void chordDoesNotLaunchOrArmOnRelease() {
        event(62, 10, 10, true, false, false, true, false);
        assertEquals(PASS, event(34, 20, 20, true, false, false, true, true));
        event(62, 10, 30, false, false, false, true, false);
        assertEquals(PASS, event(34, 50, 50, true, false, false, true, true));
    }
    @Test void unknownKeyPassesImmediatelyAndCancels() {
        prefix();
        assertEquals(PASS, event(35, 100, 100, true, false, false, true, false));
        assertEquals(PASS, event(34, 200, 200, true, false, false, true, true));
    }
    @Test void cancellationWhileHeldStillConsumesRelease() {
        event(62, 10, 10, true, false, false, true, false);
        sequence.cancel();
        assertEquals(CONSUME, event(62, 10, 30, false, false, false, true, false));
        assertEquals(PASS, event(34, 100, 100, true, false, false, true, true));
    }
    @Test void ineligibleInputPassesAndCancels() {
        assertEquals(PASS, event(62, 10, 10, true, false, false, false, false));
        prefix();
        assertEquals(PASS, event(34, 100, 100, true, false, false, false, true));
        assertEquals(PASS, event(34, 200, 200, true, false, false, true, true));
    }
    @Test void canceledPrefixDoesNotArm() {
        event(62, 10, 10, true, false, false, true, false);
        event(62, 10, 30, false, false, true, true, false);
        assertEquals(PASS, event(34, 100, 100, true, false, false, true, true));
    }
    @Test void repeatCannotStartSequenceOrLaunch() {
        assertEquals(PASS, event(62, 10, 20, true, true, false, true, false));
        prefix();
        assertEquals(PASS, event(34, 100, 200, true, true, false, true, true));
    }
    @Test void secondKeyMayEqualPrefix() {
        prefix();
        assertEquals(LAUNCH, event(62, 100, 100, true, false, false, true, true));
    }
    @Test void anotherDeviceCannotCompleteSequence() {
        prefix();
        assertEquals(PASS, sequence.event(34, 2, 100, 100, true, false, false, true, 62, 800, true));
    }
    @Test void missingReleaseDoesNotSwallowNextPressForever() {
        prefix();
        event(34, 100, 100, true, false, false, true, true);
        sequence.cancel();
        assertEquals(PASS, event(34, 1000, 1000, true, false, false, true, true));
    }
    @Test void cancellationAfterLaunchStillConsumesKeyUp() {
        prefix();
        event(34, 100, 100, true, false, false, true, true);
        sequence.cancel();
        assertEquals(CONSUME, event(34, 100, 150, false, false, false, false, true));
    }
}
