package com.kadamitas.warlockery.entity.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ReadBudgetTest {

    @Test
    void aRejectedReadCostsExactlyWhatAnAcceptedOneCosts() {
        final ReadBudget budget = ReadBudget.of(10);
        assertFalse(budget.accepts(() -> 1, value -> value > 5));
        assertTrue(budget.accepts(() -> 9, value -> value > 5));
        assertEquals(2, budget.spent(), "both the rejected and the accepted read were charged");
    }

    @Test
    void theCapBindsAndStopsHandingOutValues() {
        final ReadBudget budget = ReadBudget.of(3);
        final List<Integer> read = new ArrayList<>();
        for (int attempt = 0; attempt < 20; attempt++) {
            final int value = attempt;
            budget.read(() -> value).ifPresent(read::add);
        }
        assertEquals(3, read.size());
        assertEquals(3, budget.spent());
        assertEquals(0, budget.remaining());
        assertTrue(budget.exhausted());
    }

    @Test
    void chargeCoversTheLineOfSightShapeWhereTheReadIsTheEngineCall() {
        // Nine families run a line of sight loop as decrement, count, then clip. charge() is that
        // loop: it returns false exactly when the caller must not perform the trace.
        final ReadBudget budget = ReadBudget.of(4);
        int traces = 0;
        for (int candidate = 0; candidate < 30; candidate++) {
            if (!budget.charge()) {
                break;
            }
            traces++;
        }
        assertEquals(4, traces);
        assertEquals(4, budget.spent());
    }

    @Test
    void aZeroCapPerformsNoReadAtAll() {
        final ReadBudget budget = ReadBudget.of(0);
        assertEquals(Optional.empty(), budget.read(() -> {
            throw new AssertionError("the reader must not run when nothing can be spent");
        }));
        assertFalse(budget.charge());
        assertEquals(0, budget.spent());
    }

    @Test
    void aFailingReaderStillCostsWhatItConsumed() {
        final ReadBudget budget = ReadBudget.of(5);
        assertThrows(IllegalStateException.class, () -> budget.read(() -> {
            throw new IllegalStateException("the world call blew up");
        }));
        assertEquals(1, budget.spent(), "the charge lands before the reader runs");
    }

    @Test
    void aNegativeCapIsRejectedRatherThanTreatedAsUnlimited() {
        assertThrows(IllegalArgumentException.class, () -> ReadBudget.of(-1));
    }

    /**
     * The historical defect. BansheeRuntime.findSafeDestination tests the world border and the
     * footprint before charging anything, so a candidate rejected by those tests costs two real
     * world reads and is charged nothing. LycanPackRuntime.searchRefuge has the same shape. The cap
     * then bounds only the accepted minority and the scan can walk the whole envelope while
     * reporting that it stayed inside budget.
     */
    @Test
    void redChargingAfterTheFilterLetsTheScanExceedItsDeclaredCap() {
        final List<Integer> envelope = java.util.stream.IntStream.range(0, 500).boxed().toList();
        final int declaredCap = 16;

        // The defective shape, reproduced: only accepted candidates are counted.
        int defectiveCharged = 0;
        int defectiveActualReads = 0;
        for (final int candidate : envelope) {
            defectiveActualReads++;
            if (candidate % 50 != 0) {
                continue;
            }
            defectiveCharged++;
            if (defectiveCharged >= declaredCap) {
                break;
            }
        }
        assertEquals(500, defectiveActualReads,
            "every candidate in the envelope was really read");
        assertTrue(defectiveCharged < declaredCap,
            "yet the counter never even reached the cap, so the cap could never bind");

        // The primitive, same filter, same envelope.
        final ReadBudget budget = ReadBudget.of(declaredCap);
        int accepted = 0;
        for (final int candidate : envelope) {
            if (budget.exhausted()) {
                break;
            }
            if (budget.accepts(() -> candidate, value -> value % 50 == 0)) {
                accepted++;
            }
        }
        assertEquals(declaredCap, budget.spent(), "the declared cap bound the real cost");
        assertTrue(accepted <= 1);
    }
}
