package com.kadamitas.warlockery.entity.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class TicksTest {

    @Test
    void aDeadlineIsDueOnceNowReachesIt() {
        assertFalse(Ticks.due(100L, 99L));
        assertTrue(Ticks.due(100L, 100L));
        assertTrue(Ticks.due(100L, 5_000L));
    }

    @Test
    void additionSaturatesRatherThanWrapping() {
        assertEquals(Long.MAX_VALUE, Ticks.saturatingAdd(Long.MAX_VALUE, 1L));
        assertEquals(Long.MAX_VALUE, Ticks.saturatingAdd(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(Long.MIN_VALUE, Ticks.saturatingAdd(Long.MIN_VALUE, -1L));
        assertEquals(30L, Ticks.saturatingAdd(10L, 20L));
    }

    @Test
    void aDeadlineBeyondTheHorizonIsPulledBackSoAnUnloadCannotStrandIt() {
        assertEquals(1_000L + Ticks.MAX_FUTURE_HORIZON_TICKS,
            Ticks.clampDeadline(Long.MAX_VALUE, 1_000L));
        assertEquals(1_500L, Ticks.clampDeadline(1_500L, 1_000L));
        assertEquals(1_000L, Ticks.clampDeadline(40L, 1_000L),
            "a deadline already behind us is now");
    }

    @Test
    void loadedCountdownsNeverGoNegativeAndClampWhenRestored() {
        assertEquals(0, Ticks.decrementLoaded(0));
        assertEquals(0, Ticks.decrementLoaded(1));
        assertEquals(4, Ticks.decrementLoaded(5));
        assertEquals(0, Ticks.clampRemaining(-40, 100));
        assertEquals(100, Ticks.clampRemaining(9_999, 100));
        assertEquals(37, Ticks.clampRemaining(37, 100));
    }

    @Test
    void aStableOffsetIsDeterministicInRangeAndSpreadsACrowd() {
        final UUID identity = UUID.fromString("4b1c1b2e-0000-4000-8000-000000000001");
        assertEquals(Ticks.stableOffset(identity, 64), Ticks.stableOffset(identity, 64));
        assertEquals(0, Ticks.stableOffset(identity, 0), "a zero span cannot be indexed into");
        assertEquals(0, Ticks.stableOffset(identity, -5));

        final Set<Integer> offsets = new HashSet<>();
        for (int entity = 0; entity < 500; entity++) {
            final int offset = Ticks.stableOffset(UUID.randomUUID(), 20);
            assertTrue(offset >= 0 && offset < 20);
            offsets.add(offset);
        }
        assertTrue(offsets.size() > 10, "a crowd must not all fire on the same tick");
    }
}
