package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import org.junit.jupiter.api.Test;

final class AbyssalRegentParityTest {
    @Test
    void legacyCombatProfileRetainsFiveHundredHealth() {
        assertEquals(500.0D, AbyssalRegentRules.MAX_HEALTH);
        final CreatureBehaviorProfile profile = CreatureBehaviorProfile.find(CreatureKind.ABYSSAL_REGENT).orElseThrow();
        assertTrue(profile.has(Feature.TORMENT_BANISHMENT));
        assertTrue(profile.has(Feature.FEAR_AURA));
    }

    @Test
    void tormentPhaseBeginsOnceAtHalfHealth() {
        assertFalse(AbyssalRegentRules.beginsTormentPhase(251.0D, false));
        assertTrue(AbyssalRegentRules.beginsTormentPhase(250.0D, false));
        assertFalse(AbyssalRegentRules.beginsTormentPhase(200.0D, true));
    }

    @Test
    void hierarchyPhaseTransitionKeepsTheLegacyLatchContract() {
        assertEquals("WarlockeryAbyssalTormentPhase", InfernalHierarchyEntity.LEGACY_PHASE_KEY,
            "the persisted half-health latch key is immutable");
        assertEquals(30, InfernalHierarchyRules.PHASE_TELEGRAPH_TICKS);
        assertEquals(240, InfernalHierarchyRules.PHASE_EFFECT_TICKS);
        assertEquals(60, InfernalHierarchyRules.PHASE_RECOVERY_TICKS);
        assertEquals(2, InfernalHierarchyRules.PHASE_SUMMON_CAP);
        final InfernalHierarchyState migrated = InfernalHierarchyState.read(
            new net.minecraft.nbt.CompoundTag(),
            InfernalHierarchyRules.Rank.ABYSSAL_REGENT,
            new java.util.UUID(1L, 1L),
            100L,
            true
        );
        assertTrue(migrated.phaseCompleted(), "a true legacy latch migrates to a completed phase");
        assertFalse(AbyssalRegentRules.beginsTormentPhase(100.0D, migrated.phaseCompleted()),
            "migration never replays the phase");
    }
}
