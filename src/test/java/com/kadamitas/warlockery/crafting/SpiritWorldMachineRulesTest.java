package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class SpiritWorldMachineRulesTest {
    @Test
    void flowingSpiritIsExclusiveToTheSpiritWorld() {
        assertFalse(SpiritWorldMachineRules.allows(
            SpiritWorldMachineRules.FLOWING_SPIRIT,
            Identifier.withDefaultNamespace("overworld")
        ));
        assertTrue(SpiritWorldMachineRules.allows(
            SpiritWorldMachineRules.FLOWING_SPIRIT,
            Identifier.fromNamespaceAndPath("warlockery", "spirit_world")
        ));
        assertTrue(SpiritWorldMachineRules.allows(
            Identifier.fromNamespaceAndPath("warlockery", "cauldron_drop_of_luck"),
            Identifier.withDefaultNamespace("overworld")
        ));
    }
}
