package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class GoblinSettlementLifeRulesTest {
    @Test
    void housingFollowsTheVanillaOneFreeBedForOneChildPrinciple() {
        assertTrue(GoblinSettlementLifeRules.needsHousing(2, 2));
        assertFalse(GoblinSettlementLifeRules.canReproduce(2, 2));
        assertFalse(GoblinSettlementLifeRules.needsHousing(2, 3));
        assertTrue(GoblinSettlementLifeRules.canReproduce(2, 3));
        assertFalse(GoblinSettlementLifeRules.canReproduce(GoblinSettlementLifeRules.POPULATION_CAP, 16));
    }

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
    void hutCostsAreExplicitAndCannotBePaidWithPartialMaterials() {
        assertEquals(18, GoblinSettlementLifeRules.HUT_DIRT_COST);
        assertEquals(3, GoblinSettlementLifeRules.HUT_LOG_COST);
        assertEquals(32, GoblinSettlementLifeRules.HUT_EDIT_COST);
        assertTrue(GoblinSettlementLifeRules.HUT_EDIT_COST < GoblinSettlementLifeRules.WORLD_EDIT_CAP);
    }

    @Test
    void settlementKeysSeparateSpeciesAndDistantRegions() {
        final long goblin = GoblinSettlementLifeRules.settlementKey(BlockPos.ZERO, CreatureKind.GOBLIN);
        final long hobgoblin = GoblinSettlementLifeRules.settlementKey(BlockPos.ZERO, CreatureKind.HOBGOBLIN);
        final long distant = GoblinSettlementLifeRules.settlementKey(new BlockPos(256, 0, 0), CreatureKind.GOBLIN);
        assertNotEquals(goblin, hobgoblin);
        assertNotEquals(goblin, distant);
    }

    @Test
    void ambientLifeExcludesRaidersBossesAndOtherCreatureFamilies() {
        assertTrue(GoblinSettlementLifeRules.participates(CreatureKind.GOBLIN, false, false));
        assertTrue(GoblinSettlementLifeRules.participates(CreatureKind.HOBGOBLIN, false, false));
        assertFalse(GoblinSettlementLifeRules.participates(CreatureKind.GOBLIN, true, false));
        assertFalse(GoblinSettlementLifeRules.participates(CreatureKind.STONEBROKER, false, false));
    }

    @Test
    void giftsAndMiningAreRareAndCooldownBounded() {
        assertTrue(GoblinSettlementLifeRules.giftReady(100, 100, true));
        assertFalse(GoblinSettlementLifeRules.giftReady(99, 100, true));
        assertFalse(GoblinSettlementLifeRules.giftReady(100, 100, false));
        assertTrue(GoblinSettlementLifeRules.shouldAttemptTunnel(0));
        assertFalse(GoblinSettlementLifeRules.shouldAttemptTunnel(1));
    }
}

