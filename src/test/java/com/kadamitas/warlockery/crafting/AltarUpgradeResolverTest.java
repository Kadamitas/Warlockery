package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.crafting.AltarUpgradeResolver.UpgradeClass;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class AltarUpgradeResolverTest {
    @Test
    void strongestUpgradeInEachLegacyFamilyWins() {
        final var modifiers = AltarUpgradeResolver.resolve(Stream.of(
            UpgradeClass.TORCH,
            UpgradeClass.CANDELABRA,
            UpgradeClass.SKULL,
            UpgradeClass.WITHER_SKULL,
            UpgradeClass.CHALICE,
            UpgradeClass.FILLED_CHALICE,
            UpgradeClass.PENTACLE
        ));

        assertEquals(5.0, modifiers.capacityMultiplier());
        assertEquals(18, modifiers.rechargeMultiplier());
        assertTrue(modifiers.activeClasses().contains(UpgradeClass.CANDELABRA));
        assertTrue(modifiers.activeClasses().contains(UpgradeClass.WITHER_SKULL));
        assertTrue(modifiers.activeClasses().contains(UpgradeClass.FILLED_CHALICE));
        assertTrue(modifiers.activeClasses().contains(UpgradeClass.PENTACLE));
        assertFalse(modifiers.activeClasses().contains(UpgradeClass.TORCH));
        assertFalse(modifiers.activeClasses().contains(UpgradeClass.SKULL));
    }

    @Test
    void playerHeadAndParadoxEggRestoreRareLegacyPowerClasses() {
        final var head = AltarUpgradeResolver.resolve(Stream.of(UpgradeClass.PLAYER_HEAD));
        assertEquals(2.5, head.capacityMultiplier());
        assertEquals(4, head.rechargeMultiplier());

        final var egg = AltarUpgradeResolver.resolve(Stream.of(UpgradeClass.PARADOX_EGG));
        assertEquals(10.0, egg.capacityMultiplier());
        assertEquals(10, egg.rechargeMultiplier());
        assertEquals(10_000, egg.applyCapacity(1_000));
    }
}
