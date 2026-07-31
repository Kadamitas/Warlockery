package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SilverVatFurnaceObserverTest {
    @Test
    void adjacentFurnacesEachCreateSilverWhenTheirGoldSmeltCompletes() {
        final SilverVatFurnaceObserver observer = new SilverVatFurnaceObserver();

        assertEquals(0, observer.observe(List.of(active(1L, 3), active(2L, 2))));
        assertEquals(1, observer.observe(List.of(active(1L, 3), active(2L, 2))));
        assertEquals(1, observer.observe(List.of(active(1L, 3), active(2L, 2))));
        assertEquals(1, observer.observe(List.of(active(1L, 3), active(2L, 2))));
    }

    @Test
    void interruptedOrDisconnectedFurnacesCannotFinishAStaleCycle() {
        final SilverVatFurnaceObserver observer = new SilverVatFurnaceObserver();

        observer.observe(List.of(active(4L, 3)));
        observer.observe(List.of(new SilverVatFurnaceObserver.FurnaceCycle(4L, false, false, 3)));
        assertEquals(0, observer.observe(List.of(active(4L, 3))));
        observer.observe(List.of());
        assertEquals(0, observer.observe(List.of(active(4L, 3))));
    }

    @Test
    void savedProgressResumesAfterChunkReload() {
        final SilverVatFurnaceObserver beforeReload = new SilverVatFurnaceObserver();
        beforeReload.observe(List.of(active(9L, 2)));

        final SilverVatFurnaceObserver afterReload = new SilverVatFurnaceObserver();
        afterReload.restore(beforeReload.snapshot());

        assertEquals(1, afterReload.observe(List.of(active(9L, 2))));
    }

    @Test
    void theLastGoldOreStillProducesItsDepositWhenTheInputSlotEmpties() {
        final SilverVatFurnaceObserver observer = new SilverVatFurnaceObserver();

        observer.observe(List.of(active(12L, 3)));
        observer.observe(List.of(active(12L, 3)));

        assertEquals(1, observer.observe(List.of(
            new SilverVatFurnaceObserver.FurnaceCycle(12L, false, true, 3)
        )));
    }

    private static SilverVatFurnaceObserver.FurnaceCycle active(final long position, final int cookingTime) {
        return new SilverVatFurnaceObserver.FurnaceCycle(position, true, false, cookingTime);
    }
}
