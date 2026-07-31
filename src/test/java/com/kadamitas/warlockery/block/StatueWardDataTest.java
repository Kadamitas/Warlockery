package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class StatueWardDataTest {
    @Test
    void defensiveStatuesResolveToDistinctWardKinds() {
        assertEquals(
            StatueWardData.WardKind.HEXES,
            StatueWardData.WardKind.forProfile(StatueProfile.find("broken_hexes_statue").orElseThrow()).orElseThrow()
        );
        assertEquals(
            StatueWardData.WardKind.SUMMONING,
            StatueWardData.WardKind.forProfile(StatueProfile.find("occluded_summons_statue").orElseThrow()).orElseThrow()
        );
        assertTrue(StatueWardData.WardKind.forProfile(
            StatueProfile.find("statuegoddess").orElseThrow()
        ).isEmpty());
    }

    @Test
    void wardRadiusMatchesTheArchivedConfigurableRange() {
        assertTrue(StatueWardData.WARD_RADIUS >= 32);
        assertTrue(StatueWardData.WARD_RADIUS <= 128);
    }
}
