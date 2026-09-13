package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.ritual.HuntsmanSummoningStructure;

final class CreatureIntegrityClosureTest {
    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);

    @Test void deathDisguiseRequiresEveryQualification() {
        assertFalse(DeathImpersonationRules.qualifies(false, true, true, true));
        assertFalse(DeathImpersonationRules.qualifies(true, false, true, true));
        assertFalse(DeathImpersonationRules.qualifies(true, true, false, true));
        assertFalse(DeathImpersonationRules.qualifies(true, true, true, false));
        assertTrue(DeathImpersonationRules.qualifies(true, true, true, true));
    }

    @Test void spectralMountControlRequiresItsOwnerAndRetainsDistinctSpeeds() {
        assertFalse(SpectralMountRules.canControl(CreatureKind.PALE_STEED, Optional.of(OTHER), OWNER));
        assertFalse(SpectralMountRules.canControl(CreatureKind.NIGHTMARE, Optional.empty(), OWNER));
        assertTrue(SpectralMountRules.canControl(CreatureKind.PALE_STEED, Optional.of(OWNER), OWNER));
        assertTrue(SpectralMountRules.canControl(CreatureKind.NIGHTMARE, Optional.of(OWNER), OWNER));
        assertTrue(SpectralMountRules.speed(CreatureKind.PALE_STEED, .23) > .23F);
        assertTrue(SpectralMountRules.speed(CreatureKind.NIGHTMARE, .23) > SpectralMountRules.speed(CreatureKind.PALE_STEED, .23));
    }

    @Test void recallRequiresLivingLoadedOwnedFamiliar() {
        assertFalse(FamiliarRecallRules.eligible(true, true, false));
        assertFalse(FamiliarRecallRules.eligible(false, true, true));
        assertTrue(FamiliarRecallRules.eligible(true, true, true));
    }

    @Test void hobgoblinWorkPrioritizesDepositAndRequiresPermission() {
        assertFalse(HobgoblinWorkRules.canWork(false, false, false, true));
        assertTrue(HobgoblinWorkRules.canWork(true, false, false, true));
        assertEquals(HobgoblinWorkRules.WorkAction.IDLE, HobgoblinWorkRules.nextAction(false, true, false));
        assertEquals(HobgoblinWorkRules.WorkAction.COLLECT, HobgoblinWorkRules.nextAction(false, false, true));
        assertEquals(HobgoblinWorkRules.WorkAction.DEPOSIT, HobgoblinWorkRules.nextAction(true, true, true));
    }

    @Test void huntsmanNeedsTheCompleteFourBundleStructure() {
        assertFalse(HuntsmanSummoningStructure.ready(3));
        assertTrue(HuntsmanSummoningStructure.ready(4));
        assertEquals(4, HuntsmanSummoningStructure.positions(net.minecraft.core.BlockPos.ZERO).size());
    }
}
