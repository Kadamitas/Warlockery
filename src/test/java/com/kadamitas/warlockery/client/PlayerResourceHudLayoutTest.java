package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class PlayerResourceHudLayoutTest {
    @Test
    void twoResourceRowsReserveTheTopOfTheSharedLeftLane() {
        final PlayerResourceHudLayout layout = PlayerResourceHudLayout.leftLane(2, 8, 8, 20, 3);

        assertEquals(8, layout.x());
        assertEquals(8, layout.rowY(0));
        assertEquals(31, layout.rowY(1));
        assertEquals(51, layout.stackBottom());
        assertEquals(55, layout.nextStackTop(24, 4));
    }

    @Test
    void emptyResourceStackKeepsTheOriginalDollOffset() {
        final PlayerResourceHudLayout layout = PlayerResourceHudLayout.leftLane(0, 8, 8, 20, 3);

        assertEquals(8, layout.stackBottom());
        assertEquals(24, layout.nextStackTop(24, 4));
    }

    @Test
    void dollRowsAreCappedByAvailableScaledHeight() {
        assertEquals(6, PlayerResourceHudLayout.visibleRowCount(240, 55, 23, 8, 28));
        assertEquals(0, PlayerResourceHudLayout.visibleRowCount(100, 55, 23, 8, 28));
        assertEquals(0, PlayerResourceHudLayout.visibleRowCount(50, 55, 23, 8, 28));
        assertEquals(0, PlayerResourceHudLayout.visibleRowCount(240, 55, 0, 8, 28));
    }
}
