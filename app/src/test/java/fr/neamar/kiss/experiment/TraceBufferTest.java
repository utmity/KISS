package fr.neamar.kiss.experiment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;

class TraceBufferTest {
    @Test void preservesInputOrder() {
        TraceBuffer buffer = new TraceBuffer(4);
        buffer.add("down"); buffer.add("compose"); buffer.add("search"); buffer.add("up");
        assertEquals(Arrays.asList("down", "compose", "search", "up"), buffer.snapshot());
    }
    @Test void boundedOverflowIsVisible() {
        TraceBuffer buffer = new TraceBuffer(2);
        buffer.add("a"); buffer.add("b"); buffer.add("c");
        assertEquals(Arrays.asList("b", "c"), buffer.snapshot());
        assertEquals(1, buffer.dropped());
    }
    @Test void snapshotDoesNotChangeOnLaterInput() {
        TraceBuffer buffer = new TraceBuffer(2);
        buffer.add("a");
        java.util.List<String> snapshot = buffer.snapshot();
        buffer.add("b");
        assertEquals(Arrays.asList("a"), snapshot);
    }
    @Test void clearingResetsOverflow() {
        TraceBuffer buffer = new TraceBuffer(1);
        buffer.add("a"); buffer.add("b"); buffer.clear();
        assertTrue(buffer.snapshot().isEmpty()); assertEquals(0, buffer.dropped());
    }
}
