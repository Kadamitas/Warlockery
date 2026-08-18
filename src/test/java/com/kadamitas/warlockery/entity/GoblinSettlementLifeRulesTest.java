package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Covers the persistence remainder only. The behavioural assertions that lived here (housing,
 * reproduction, participation, gifting, tunnel rolls) went with the retired settlement runtime and
 * the shared hobgoblin body; what is pinned below is the clamp and key contract that
 * {@code GoblinSettlementLifeData} applies when reading a 1.4-era record off disk.
 */
final class GoblinSettlementLifeRulesTest {
    @Test
    void autonomousConstructionHasPersistentHardCaps() {
        assertTrue(GoblinSettlementLifeRules.canReserveHut(0, 0));
        assertFalse(GoblinSettlementLifeRules.canReserveHut(GoblinSettlementLifeRules.HUT_CAP, 0));
        assertFalse(GoblinSettlementLifeRules.canReserveHut(
            0,
            GoblinSettlementLifeRules.WORLD_EDIT_CAP - GoblinSettlementLifeRules.HUT_EDIT_COST + 1
        ));
        assertTrue(GoblinSettlementLifeRules.canReserveTunnel(0, 0, 10));
        assertFalse(GoblinSettlementLifeRules.canReserveTunnel(GoblinSettlementLifeRules.TUNNEL_CAP, 0, 1));
        assertFalse(GoblinSettlementLifeRules.canReserveTunnel(0, 0,
            GoblinSettlementLifeRules.TUNNEL_EDIT_CAP + 1));
    }

    @Test
    void persistedRecordCapsKeepTheirExactStoredValues() {
        assertEquals(3, GoblinSettlementLifeRules.HUT_CAP);
        assertEquals(1, GoblinSettlementLifeRules.TUNNEL_CAP);
        assertEquals(128, GoblinSettlementLifeRules.WORLD_EDIT_CAP);
        assertEquals(32, GoblinSettlementLifeRules.HUT_EDIT_COST);
        assertEquals(10, GoblinSettlementLifeRules.TUNNEL_EDIT_CAP);
        assertTrue(GoblinSettlementLifeRules.HUT_EDIT_COST < GoblinSettlementLifeRules.WORLD_EDIT_CAP);
    }

    @Test
    void naturalBlockGatheringStopsAtTheEditBudget() {
        assertTrue(GoblinSettlementLifeRules.canGatherNaturalBlock(0));
        assertTrue(GoblinSettlementLifeRules.canGatherNaturalBlock(
            GoblinSettlementLifeRules.WORLD_EDIT_CAP - 1));
        assertFalse(GoblinSettlementLifeRules.canGatherNaturalBlock(
            GoblinSettlementLifeRules.WORLD_EDIT_CAP));
    }

    @Test
    void settlementKeysSeparateSpeciesAndDistantRegions() {
        final long goblin = GoblinSettlementLifeRules.settlementKey(BlockPos.ZERO, CreatureKind.GOBLIN);
        final long hobgoblin = GoblinSettlementLifeRules.settlementKey(BlockPos.ZERO, CreatureKind.HOBGOBLIN);
        final long distant = GoblinSettlementLifeRules.settlementKey(new BlockPos(256, 0, 0), CreatureKind.GOBLIN);
        assertNotEquals(goblin, hobgoblin);
        assertNotEquals(goblin, distant);
    }
}
