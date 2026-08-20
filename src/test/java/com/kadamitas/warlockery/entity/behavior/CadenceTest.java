package com.kadamitas.warlockery.entity.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CadenceTest {

    @Test
    void aFreshCadenceIsDueAtOnceAndAnArmedOneIsAFullPeriodAway() {
        assertTrue(Cadence.every(20).due());
        assertFalse(Cadence.armed(20).due());
        assertEquals(20, Cadence.armed(20).untilDue());
    }

    @Test
    void itBecomesDueExactlyOnThePeriod() {
        Cadence cadence = Cadence.armed(7);
        for (int tick = 0; tick < 6; tick++) {
            cadence = cadence.step();
            assertFalse(cadence.due(), "not yet due after " + (tick + 1) + " ticks");
        }
        assertTrue(cadence.step().due());
    }

    @Test
    void elapsedTicksSaturateSoALongIdleCadenceCannotOverflow() {
        Cadence cadence = Cadence.armed(3);
        for (int tick = 0; tick < 1_000_000; tick++) {
            cadence = cadence.step();
        }
        assertEquals(3, cadence.sinceLast());
        assertTrue(cadence.due());
    }

    @Test
    void armingRecordsThatTheWorkRanWhateverItFound() {
        final Cadence due = Cadence.every(15);
        assertTrue(due.due());
        assertFalse(due.arm().due(), "arming is about having run, not about having succeeded");
    }

    @Test
    void triggeringMakesWorkDueBeforeItsPeriodElapses() {
        assertTrue(Cadence.armed(100).trigger().due());
    }

    @Test
    void aNonPositivePeriodIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Cadence(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cadence(-4, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cadence(5, -1));
    }

    @Test
    void changingThePeriodKeepsTheElapsedCount() {
        assertEquals(new Cadence(40, 3), new Cadence(10, 3).withPeriod(40));
    }
}
