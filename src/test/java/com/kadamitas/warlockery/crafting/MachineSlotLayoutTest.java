package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class MachineSlotLayoutTest {
    @Test
    void fuelledMachineSeparatesInputsFuelAndOutputsByFace() {
        final MachineSlotLayout layout = MachineSlotLayout.create(
            MachineProfiles.forBlock("alchemical_oven"),
            9
        );

        assertArrayEquals(new int[]{0, 1, 2, 3}, layout.slotsFor(Direction.UP));
        assertArrayEquals(new int[]{4}, layout.slotsFor(Direction.NORTH));
        assertArrayEquals(new int[]{5, 6, 7, 8, 4}, layout.slotsFor(Direction.DOWN));
        assertTrue(layout.accepts(Direction.UP, 2));
        assertTrue(layout.accepts(Direction.EAST, 4));
        assertFalse(layout.accepts(Direction.DOWN, 5));
        assertTrue(layout.extractsOutput(Direction.DOWN, 5));
        assertTrue(layout.extractsFuelRemainder(Direction.DOWN, 4));
        assertFalse(layout.extractsOutput(Direction.UP, 5));
    }

    @Test
    void unpoweredMachineAcceptsInputsFromTopAndSides() {
        final MachineSlotLayout layout = MachineSlotLayout.create(
            MachineProfiles.forBlock("spinningwheel"),
            9
        );

        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5}, layout.slotsFor(Direction.UP));
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5}, layout.slotsFor(Direction.WEST));
        assertArrayEquals(new int[]{6, 7, 8}, layout.slotsFor(Direction.DOWN));
        assertTrue(layout.accepts(Direction.SOUTH, 0));
        assertFalse(layout.accepts(Direction.SOUTH, 6));
    }
}
