package fr.neamar.kiss.ui;

/** Android-independent, release-then-press shortcut state machine. */
public final class KeySequence {
    public enum Decision { PASS, CONSUME, LAUNCH }
    private int swallowedKey = -1;
    private int device;
    private long downTime;
    private boolean prefixHeld;
    private long deadline;

    /** Cancel the sequence but still swallow the remainder of an intercepted press. */
    public void cancel() {
        prefixHeld = false;
        deadline = 0;
    }

    public Decision event(int key, int deviceId, long pressTime, long now, boolean down,
                          boolean repeat, boolean canceled, boolean eligible,
                          int prefix, int timeout, boolean mapped) {
        if (key == swallowedKey && deviceId == device && pressTime == downTime) {
            if (!down) {
                swallowedKey = -1;
                if (prefixHeld && !canceled && eligible) deadline = now + timeout;
                prefixHeld = false;
            }
            return Decision.CONSUME;
        }
        if (!eligible || canceled) {
            cancel();
            return Decision.PASS;
        }
        if (!down) return Decision.PASS;
        if (prefixHeld) {
            cancel(); // A chord is not a sequence.
            return Decision.PASS;
        }
        if (deadline != 0) {
            boolean ready = now < deadline && deviceId == device;
            cancel();
            if (ready) {
                if (!repeat && mapped) {
                    swallow(key, deviceId, pressTime);
                    return Decision.LAUNCH;
                }
                return Decision.PASS;
            }
        }
        if (key == prefix && !repeat) {
            swallow(key, deviceId, pressTime);
            prefixHeld = true;
            return Decision.CONSUME;
        }
        return Decision.PASS;
    }

    private void swallow(int key, int deviceId, long pressTime) {
        swallowedKey = key;
        device = deviceId;
        downTime = pressTime;
    }
}
