package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

final class AltarPowerNetworkRangeTest {
    @AfterEach
    void clearIndexedFixtures() {
        AltarRangeIndex.within(null, BlockPos.ZERO, 1_000, 1_000, 1_000)
            .toList().forEach(position -> AltarRangeIndex.remove(null, position));
    }
    @Test
    void brazierCanReachAnAltarAtTheArchivedFifteenBlockDistance() {
        assertTrue(AltarPowerNetwork.BASE_HORIZONTAL_RANGE >= 15);
    }

    @Test
    void ordinaryAltarsReachThirtyTwoBlocksInclusively() {
        assertEquals(32, AltarPowerNetwork.BASE_HORIZONTAL_RANGE);
        final BlockPos origin = BlockPos.ZERO;
        final BlockPos inside = new BlockPos(31, 0, 0);
        final BlockPos boundary = new BlockPos(32, 0, 0);
        final BlockPos outside = new BlockPos(33, 0, 0);
        Set.of(inside, boundary, outside).forEach(position -> AltarRangeIndex.update(null, position, false));
        assertEquals(Set.of(inside, boundary), AltarRangeIndex.within(null, origin,
            AltarPowerNetwork.BASE_HORIZONTAL_RANGE, 4, 6).collect(Collectors.toSet()));
    }

    @Test
    void focusDoublesServiceReachWithoutChangingItsInclusiveBoundary() {
        final BlockPos origin = new BlockPos(0, 100, 0);
        final BlockPos inside = origin.offset(63, 0, 0);
        final BlockPos boundary = origin.offset(64, 0, 0);
        final BlockPos outside = origin.offset(65, 0, 0);
        Set.of(inside, boundary, outside).forEach(position -> AltarRangeIndex.update(null, position, true));
        assertEquals(Set.of(inside, boundary), AltarRangeIndex.within(null, origin,
            AltarPowerNetwork.BASE_HORIZONTAL_RANGE, 4, 6).collect(Collectors.toSet()));
    }

    @Test
    void ordinaryVerticalReachRemainsFourDownAndSixUp() {
        final BlockPos origin = new BlockPos(0, 200, 0);
        final BlockPos lower = origin.below(4);
        final BlockPos upper = origin.above(6);
        Set.of(lower, upper, origin.below(5), origin.above(7))
            .forEach(position -> AltarRangeIndex.update(null, position, false));
        assertEquals(Set.of(lower, upper), AltarRangeIndex.within(null, origin,
            AltarPowerNetwork.BASE_HORIZONTAL_RANGE, 4, 6).collect(Collectors.toSet()));
    }

    @Test
    void focusDoublesVerticalReachToEightDownAndTwelveUp() {
        final BlockPos origin = new BlockPos(0, 300, 0);
        final BlockPos lower = origin.below(8);
        final BlockPos upper = origin.above(12);
        Set.of(lower, upper, origin.below(9), origin.above(13))
            .forEach(position -> AltarRangeIndex.update(null, position, true));
        assertEquals(Set.of(lower, upper), AltarRangeIndex.within(null, origin,
            AltarPowerNetwork.BASE_HORIZONTAL_RANGE, 4, 6).collect(Collectors.toSet()));
    }

    @Test
    void removingTheFocusImmediatelyRestoresNormalRange() {
        final BlockPos position = new BlockPos(40, 0, 0);
        AltarRangeIndex.update(null, position, true);
        assertEquals(Set.of(position), AltarRangeIndex.within(null, BlockPos.ZERO,
            AltarPowerNetwork.BASE_HORIZONTAL_RANGE, 4, 6).collect(Collectors.toSet()));
        AltarRangeIndex.update(null, position, false);
        assertEquals(Set.of(), AltarRangeIndex.within(null, BlockPos.ZERO,
            AltarPowerNetwork.BASE_HORIZONTAL_RANGE, 4, 6).collect(Collectors.toSet()));
    }

    @Test
    void removedOrUnloadedAltarsLeaveTheIndex() {
        final BlockPos position = new BlockPos(32, 0, 0);
        AltarRangeIndex.update(null, position, false);
        AltarRangeIndex.remove(null, position);
        assertEquals(Set.of(), AltarRangeIndex.within(null, BlockPos.ZERO,
            AltarPowerNetwork.BASE_HORIZONTAL_RANGE, 4, 6).collect(Collectors.toSet()));
    }

    @Test
    void removingAnOldInstanceCannotEraseItsSamePositionReplacement() {
        final BlockPos position = new BlockPos(64, 0, 0);
        final Object previous = new Object();
        final Object replacement = new Object();
        AltarRangeIndex.update(null, position, false, previous);
        AltarRangeIndex.update(null, position, true, replacement);
        AltarRangeIndex.remove(null, position, previous);
        assertEquals(Set.of(position), AltarRangeIndex.within(null, BlockPos.ZERO,
            AltarPowerNetwork.BASE_HORIZONTAL_RANGE, 4, 6).collect(Collectors.toSet()));
        AltarRangeIndex.remove(null, position, replacement);
        assertEquals(Set.of(), AltarRangeIndex.within(null, BlockPos.ZERO,
            AltarPowerNetwork.BASE_HORIZONTAL_RANGE, 4, 6).collect(Collectors.toSet()));
    }
}
