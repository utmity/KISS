package fr.neamar.kiss.keyboard;

import java.util.HashSet;
import java.util.Set;

/** Pure state machine. Consumed chords stay consumed until release, even after cancellation. */
public final class LongPressState {
    private final Set<Long> swallowed = new HashSet<>();
    private long candidate;
    private long started;
    private long deadline;
    private String action;
    private boolean newSession;

    private static long identity(int device, int key) {
        return ((long) device << 32) | (key & 0xffffffffL);
    }

    public boolean down(int device, int key, int repeat, long downTime, long now,
                        boolean eligible, String target, int timeout) {
        long id = identity(device, key);
        if (newSession && repeat == 0) {
            // Release may have gone to the app we launched. A new press in a new
            // focus session must not be blocked by that missing release.
            swallowed.clear();
            newSession = false;
        }
        if (swallowed.contains(id)) {
            if (repeat > 0 || downTime == started) {
                if (!eligible) cancel();
                return true;
            }
            // A fresh press after a lost key-up must not inherit an old timer.
            swallowed.remove(id);
            cancel();
        }
        if (action != null || !swallowed.isEmpty()) {
            cancel();
            return false;
        }
        if (!eligible || repeat != 0 || target == null || target.isEmpty()) return false;
        candidate = id;
        started = downTime;
        // Start at receipt, so a stale/delayed DOWN cannot launch immediately.
        deadline = now + timeout;
        action = target;
        swallowed.add(id);
        return true;
    }

    public boolean up(int device, int key) {
        long id = identity(device, key);
        if (id == candidate) cancel();
        return swallowed.remove(id);
    }

    public void cancel() {
        action = null;
    }

    public void leaveSession() {
        cancel();
        newSession = true;
    }

    public String fire(long now) {
        if (action == null || now < deadline) return null;
        String result = action;
        action = null;
        return result;
    }

    public boolean pending() {
        return action != null;
    }

    public long remaining(long now) {
        return Math.max(0, deadline - now);
    }

    public static int timeout(String value) {
        try {
            return Math.max(200, Math.min(2000, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return 700;
        }
    }
}
