package com.kadamitas.warlockery.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class MachineMenuPowerSyncTest {
    @ParameterizedTest
    @MethodSource("powerValues")
    void signedContainerShortsRoundTripTheFullNonNegativeIntRange(final int power) {
        final short transmittedLow = (short) MachineMenu.lowWord(power);
        final short transmittedHigh = (short) MachineMenu.highWord(power);

        assertEquals(power, MachineMenu.combineWords(transmittedLow, transmittedHigh));
    }

    private static Stream<Integer> powerValues() {
        return Stream.of(0, 32_767, 32_768, 65_535, 65_536, 4_800_000, Integer.MAX_VALUE);
    }
}

