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
}
