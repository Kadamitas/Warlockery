package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile.Feature;
import org.junit.jupiter.api.Test;

final class TreefydRulesTest {
    @Test
    void creatorAllowlistAndOtherTreefydAreAlwaysSafe() {
        assertFalse(TreefydRules.canAttack(true, false, false));
        assertFalse(TreefydRules.canAttack(false, true, false));
        assertFalse(TreefydRules.canAttack(false, false, true));
        assertTrue(TreefydRules.canAttack(false, false, false));
    }

    @Test
    void profileExposesTaglockAndBolineControls() {
        final CreatureBehaviorProfile profile = CreatureBehaviorProfile.find(CreatureKind.BRAMBLE_COLOSSUS)
            .orElseThrow();
        assertTrue(profile.has(Feature.ALLOWLIST_GUARD));
        assertTrue(profile.has(Feature.WANDER_TOGGLE));
    }
}
