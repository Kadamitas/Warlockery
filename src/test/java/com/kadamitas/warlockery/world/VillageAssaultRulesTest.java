package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.world.VillageAssaultRules.AssaultKind;
import com.kadamitas.warlockery.world.VillageAssaultRules.RewardTheme;
import com.kadamitas.warlockery.world.VillageAssaultRules.SettlementKind;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

final class VillageAssaultRulesTest {
    @Test
    void everyAssaultEscalatesAcrossThreeValidatedWaves() {
        for (final AssaultKind kind : AssaultKind.values()) {
            final int first = VillageAssaultRules.waveSize(kind, 1);
            final int second = VillageAssaultRules.waveSize(kind, 2);
            final int third = VillageAssaultRules.waveSize(kind, 3);
            assertTrue(first > 0);
            assertTrue(second > first);
            assertTrue(third > second);
            assertThrows(IllegalArgumentException.class, () -> VillageAssaultRules.waveSize(kind, 0));
            assertThrows(IllegalArgumentException.class, () -> VillageAssaultRules.waveSize(kind, 4));
        }
    }

    @Test
    void raidTimingRejectsPeacefulCooldownAndDuplicateStarts() {
        assertTrue(VillageAssaultRules.canStart(
            Difficulty.NORMAL, SettlementKind.HUMAN, false, 24_000L, 24_000L, false, false
        ));
        assertFalse(VillageAssaultRules.canStart(
            Difficulty.PEACEFUL, SettlementKind.HUMAN, false, 24_000L, 24_000L, true, true
        ));
        assertFalse(VillageAssaultRules.canStart(
            Difficulty.NORMAL, SettlementKind.HUMAN, true, 24_000L, 24_000L, true, true
        ));
        assertFalse(VillageAssaultRules.canStart(
            Difficulty.NORMAL, SettlementKind.HUMAN, false, 23_999L, 24_000L, true, true
        ));
        assertFalse(VillageAssaultRules.canStart(
            Difficulty.NORMAL, null, false, 24_000L, 24_000L, true, true
        ));
    }

    @Test
    void supernaturalRaidWindowsRespectNightAndMoon() {
        assertTrue(VillageAssaultRules.allowedAt(AssaultKind.GOBLIN, SettlementKind.HUMAN, false, false));
        assertFalse(VillageAssaultRules.allowedAt(AssaultKind.GOBLIN, SettlementKind.HOBGOBLIN, false, false));
        for (final SettlementKind settlement : SettlementKind.values()) {
            assertFalse(VillageAssaultRules.allowedAt(AssaultKind.VAMPIRE, settlement, false, true));
            assertTrue(VillageAssaultRules.allowedAt(AssaultKind.VAMPIRE, settlement, true, false));
            assertFalse(VillageAssaultRules.allowedAt(AssaultKind.WEREWOLF, settlement, true, false));
            assertTrue(VillageAssaultRules.allowedAt(AssaultKind.WEREWOLF, settlement, true, true));
        }
        assertEquals(
            EnumSet.allOf(AssaultKind.class),
            EnumSet.copyOf(VillageAssaultRules.eligibleKinds(SettlementKind.HUMAN, true, true))
        );
        assertEquals(
            EnumSet.of(AssaultKind.VAMPIRE, AssaultKind.WEREWOLF),
            EnumSet.copyOf(VillageAssaultRules.eligibleKinds(SettlementKind.HOBGOBLIN, true, true))
        );
    }

    @Test
    void supernaturalRaidersReceiveEscalatingPlayerProgressionPowers() {
        for (final AssaultKind kind : EnumSet.of(AssaultKind.VAMPIRE, AssaultKind.WEREWOLF)) {
            final var first = VillageAssaultRules.npcPowers(kind, 1);
            final var third = VillageAssaultRules.npcPowers(kind, 3);
            assertTrue(first.progressionLevel() > 0);
            assertTrue(third.progressionLevel() > first.progressionLevel());
            assertTrue(kind == AssaultKind.VAMPIRE
                ? !third.vampireAbilities().isEmpty() && third.werewolfAbilities().isEmpty()
                : third.vampireAbilities().isEmpty() && !third.werewolfAbilities().isEmpty());
        }
        final var goblin = VillageAssaultRules.npcPowers(AssaultKind.GOBLIN, 3);
        assertEquals(0, goblin.progressionLevel());
        assertTrue(goblin.vampireAbilities().isEmpty());
        assertTrue(goblin.werewolfAbilities().isEmpty());
    }

    @Test
    void onlyLivingLowHealthSupernaturalRaidersEscapeOnce() {
        for (final AssaultKind kind : EnumSet.of(AssaultKind.VAMPIRE, AssaultKind.WEREWOLF)) {
            assertTrue(VillageAssaultRules.shouldEscape(kind, 5.0F, 20.0F, false));
            assertFalse(VillageAssaultRules.shouldEscape(kind, 5.1F, 20.0F, false));
            assertFalse(VillageAssaultRules.shouldEscape(kind, 5.0F, 20.0F, true));
            assertFalse(VillageAssaultRules.shouldEscape(kind, 0.0F, 20.0F, false));
        }
        assertFalse(VillageAssaultRules.shouldEscape(AssaultKind.GOBLIN, 1.0F, 20.0F, false));
    }

    @Test
    void werewolfRaidersCanInfectVillagersButNeverPlayers() {
        assertTrue(VillageAssaultRules.canInfectVillager(AssaultKind.WEREWOLF, true, false));
        assertFalse(VillageAssaultRules.canInfectVillager(AssaultKind.WEREWOLF, true, true));
        assertFalse(VillageAssaultRules.canInfectVillager(AssaultKind.WEREWOLF, false, false));
        assertFalse(VillageAssaultRules.canInfectVillager(AssaultKind.VAMPIRE, true, false));
        assertFalse(VillageAssaultRules.canInfectVillager(AssaultKind.GOBLIN, true, false));
    }

    @Test
    void objectiveQuotasAndFreshTargetSelectionAreKindSpecific() {
        assertEquals(0, VillageAssaultRules.objectiveQuota(AssaultKind.GOBLIN));
        assertEquals(4, VillageAssaultRules.objectiveQuota(AssaultKind.VAMPIRE));
        assertEquals(3, VillageAssaultRules.objectiveQuota(AssaultKind.WEREWOLF));
        assertFalse(VillageAssaultRules.objectiveSatisfied(AssaultKind.GOBLIN, 99, 0));
        assertTrue(VillageAssaultRules.objectiveSatisfied(AssaultKind.VAMPIRE, 4, 4));
        assertFalse(VillageAssaultRules.objectiveSatisfied(AssaultKind.VAMPIRE, 3, 4));
        assertTrue(VillageAssaultRules.isFreshObjectiveTarget(
            AssaultKind.VAMPIRE, "fresh", Set.of("other"), false, false
        ));
        assertFalse(VillageAssaultRules.isFreshObjectiveTarget(
            AssaultKind.VAMPIRE, "drained", Set.of(), true, false
        ));
        assertFalse(VillageAssaultRules.isFreshObjectiveTarget(
            AssaultKind.WEREWOLF, "infected", Set.of(), false, true
        ));
        assertFalse(VillageAssaultRules.isFreshObjectiveTarget(
            AssaultKind.WEREWOLF, "duplicate", Set.of("duplicate"), false, false
        ));
    }

    @Test
    void vampireFeedingDamageCanNeverKillItsVillagerVictim() {
        assertEquals(19.0F, VillageAssaultRules.nonlethalFeedingDamage(20.0F, 100.0F));
        assertEquals(3.0F, VillageAssaultRules.nonlethalFeedingDamage(20.0F, 3.0F));
        assertEquals(0.0F, VillageAssaultRules.nonlethalFeedingDamage(1.0F, 20.0F));
        assertEquals(0.0F, VillageAssaultRules.nonlethalFeedingDamage(0.0F, 20.0F));
    }

    @Test
    void vampireTradeLockEndsAtTheExactSeventyTwoThousandTickBoundary() {
        final long fedAt = 15_000L;
        final long expiresAt = fedAt + VillageAssaultRuntime.BLOOD_DRAINED_TICKS;
        assertEquals(72_000L, VillageAssaultRuntime.BLOOD_DRAINED_TICKS);
        assertTrue(VillageAssaultRules.tradeLocked(fedAt, expiresAt));
        assertTrue(VillageAssaultRules.tradeLocked(expiresAt - 1L, expiresAt));
        assertFalse(VillageAssaultRules.tradeLocked(expiresAt, expiresAt));
        assertFalse(VillageAssaultRules.tradeLocked(expiresAt + 1L, expiresAt));
    }

    @Test
    void infectedVillagersTransformOnlyOnFullMoonNightsAndRestoreByDay() {
        assertTrue(VillageAssaultRules.shouldTransformInfected(true, true));
        assertFalse(VillageAssaultRules.shouldTransformInfected(false, true));
        assertFalse(VillageAssaultRules.shouldTransformInfected(true, false));
        assertTrue(VillageAssaultRules.shouldRestoreVillager(true));
        assertFalse(VillageAssaultRules.shouldRestoreVillager(false));
    }

    @Test
    void fullDefenseRewardsAreDistinctUsefulAndSettlementAware() {
        final var goblin = VillageAssaultRules.reward(AssaultKind.GOBLIN, SettlementKind.HUMAN, 3);
        final var vampire = VillageAssaultRules.reward(AssaultKind.VAMPIRE, SettlementKind.HUMAN, 3);
        final var werewolf = VillageAssaultRules.reward(AssaultKind.WEREWOLF, SettlementKind.HOBGOBLIN, 3);
        assertTrue(goblin.complete() && vampire.complete() && werewolf.complete());
        assertEquals(RewardTheme.INDUSTRY, goblin.theme());
        assertEquals(RewardTheme.DAWNWARD, vampire.theme());
        assertEquals(RewardTheme.MOONWARD, werewolf.theme());
        assertNotEquals(goblin.theme(), vampire.theme());
        assertEquals(SettlementKind.HOBGOBLIN, werewolf.settlement());
        assertTrue(goblin.villageFavorTicks() > 0);
        assertTrue(goblin.absorptionTicks() > 0);
        assertTrue(goblin.signatureBoonTicks() > 0);
        assertTrue(goblin.signatureAmplifier() > 0);
    }

    @Test
    void incompleteDefenseDoesNotGrantTheSignatureAmplifier() {
        final var none = VillageAssaultRules.reward(AssaultKind.VAMPIRE, SettlementKind.HUMAN, -20);
        final var partial = VillageAssaultRules.reward(AssaultKind.VAMPIRE, SettlementKind.HUMAN, 2);
        final var complete = VillageAssaultRules.reward(AssaultKind.VAMPIRE, SettlementKind.HUMAN, 99);
        assertFalse(none.complete());
        assertFalse(partial.complete());
        assertEquals(0, partial.signatureAmplifier());
        assertTrue(complete.complete());
        assertEquals(1, complete.signatureAmplifier());
    }

    @Test
    void randomizedDelayAlwaysRemainsBounded() {
        for (final long roll : new long[]{Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE}) {
            final long delay = VillageAssaultRules.nextDelay(roll);
            assertTrue(delay >= VillageAssaultRules.MINIMUM_DELAY_TICKS);
            assertTrue(delay <= VillageAssaultRules.MAXIMUM_DELAY_TICKS);
        }
    }
}
