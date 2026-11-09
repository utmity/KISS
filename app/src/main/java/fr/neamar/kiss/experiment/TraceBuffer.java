package fr.neamar.kiss.experiment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded, ordered in-memory observations. No file IO on the input path. */
public final class TraceBuffer {
    private final int capacity;
    private final ArrayDeque<String> rows = new ArrayDeque<>();
    private long dropped;

    public TraceBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    public synchronized void add(String row) {
        if (rows.size() == capacity) {
            rows.removeFirst();
            dropped++;
        }
        rows.addLast(row);
    }

    public synchronized List<String> snapshot() { return new ArrayList<>(rows); }
    public synchronized long dropped() { return dropped; }
    public synchronized void clear() { rows.clear(); dropped = 0; }
}
