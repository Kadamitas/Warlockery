package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class VeilWaystoneRulesTest {
    @Test
    void bindingRingIsAnExactHollowThreeByThree() {
        assertEquals(8, VeilWaystoneRules.bindingRing().size());
        assertEquals(8, Set.copyOf(VeilWaystoneRules.bindingRing()).size());
        assertFalse(VeilWaystoneRules.bindingRing().contains(BlockPos.ZERO));
        assertTrue(VeilWaystoneRules.bindingRing().stream().allMatch(offset ->
            Math.max(Math.abs(offset.getX()), Math.abs(offset.getZ())) == 1));
    }

    @Test
    void transpositionRingIsFiveByFiveWithoutCornerMarks() {
        assertEquals(12, VeilWaystoneRules.transpositionRing().size());
        assertEquals(12, Set.copyOf(VeilWaystoneRules.transpositionRing()).size());
        assertTrue(VeilWaystoneRules.transpositionRing().stream().allMatch(offset ->
            Math.max(Math.abs(offset.getX()), Math.abs(offset.getZ())) == 2));
        assertTrue(VeilWaystoneRules.transpositionRing().stream().noneMatch(offset ->
            Math.abs(offset.getX()) == 2 && Math.abs(offset.getZ()) == 2));
    }

    @Test
    void onlyOneWaystoneWithALivingTargetUsesCreatureBinding() {
        assertEquals(VeilWaystoneRules.BindingMode.CREATURE, VeilWaystoneRules.bindingMode(1, true));
        assertEquals(VeilWaystoneRules.BindingMode.POSITION, VeilWaystoneRules.bindingMode(1, false));
        assertEquals(VeilWaystoneRules.BindingMode.POSITION, VeilWaystoneRules.bindingMode(2, true));
        assertEquals(VeilWaystoneRules.CREATURE_BINDING_POWER,
            VeilWaystoneRules.requiredPower(VeilWaystoneRules.BindingMode.CREATURE));
        assertEquals(VeilWaystoneRules.POSITION_BINDING_POWER,
            VeilWaystoneRules.requiredPower(VeilWaystoneRules.BindingMode.POSITION));
    }
}
