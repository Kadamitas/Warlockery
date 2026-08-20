package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LycanVillagerRulesTest {
    @Test void freezesBalanceAndCadenceConstants() {
        assertAll(
            () -> assertEquals(20.0, LycanVillagerRules.MAX_HEALTH),
            () -> assertEquals(0.5, LycanVillagerRules.MOVEMENT_SPEED),
            () -> assertEquals(16.0, LycanVillagerRules.FOLLOW_RANGE),
            () -> assertEquals(6.0, LycanVillagerRules.ATTACK_DAMAGE),
            () -> assertEquals(4, LycanVillagerRules.FAMILIARITY_CAP),
            () -> assertEquals(8, LycanVillagerRules.MAX_FAMILIARITY),
            () -> assertEquals(200, LycanVillagerRules.FAMILIARITY_GAIN_TICKS),
            () -> assertEquals(72_000, LycanVillagerRules.FAMILIARITY_DECAY_TICKS),
            () -> assertEquals(20, LycanVillagerRules.WARNING_TICKS),
            () -> assertEquals(200, LycanVillagerRules.PURSUIT_TICKS),
            () -> assertEquals(100, LycanVillagerRules.WITHDRAW_TICKS),
            () -> assertEquals(3, LycanVillagerRules.MAX_ROUTE_FAILURES),
            () -> assertEquals(100, LycanVillagerRules.ROUTE_RETRY_TICKS),
            () -> assertEquals(40, LycanVillagerRules.EVIDENCE_FRESHNESS_TICKS),
            () -> assertEquals(16, LycanVillagerRules.TRADE_COOLDOWN_CAP));
    }

    @Test void higherAuthoritiesAlwaysCancelSentinelClaims() {
        assertFalse(LycanVillagerRules.mustCancelSentinel(true, false, false, false, false, false));
        assertTrue(LycanVillagerRules.mustCancelSentinel(false, false, false, false, false, false));
        assertTrue(LycanVillagerRules.mustCancelSentinel(true, true, false, false, false, false));
        assertTrue(LycanVillagerRules.mustCancelSentinel(true, false, true, false, false, false));
        assertTrue(LycanVillagerRules.mustCancelSentinel(true, false, false, true, false, false));
        assertTrue(LycanVillagerRules.mustCancelSentinel(true, false, false, false, true, false));
        assertTrue(LycanVillagerRules.mustCancelSentinel(true, false, false, false, false, true));
    }

    @Test void panicYieldsWatchAndSocialButNeverQualifiedCombat() {
        assertTrue(LycanVillagerRules.panicOverridesIntent(LycanVillagerRules.Intent.BOUNDARY_WATCH));
        assertTrue(LycanVillagerRules.panicOverridesIntent(LycanVillagerRules.Intent.MOON_WATCH));
        assertTrue(LycanVillagerRules.panicOverridesIntent(LycanVillagerRules.Intent.GREETING));
        assertTrue(LycanVillagerRules.panicOverridesIntent(LycanVillagerRules.Intent.RESERVE));
        assertFalse(LycanVillagerRules.panicOverridesIntent(LycanVillagerRules.Intent.ROUTINE));
        assertFalse(LycanVillagerRules.panicOverridesIntent(LycanVillagerRules.Intent.WARNING));
        assertFalse(LycanVillagerRules.panicOverridesIntent(LycanVillagerRules.Intent.INTERCEPT));
        assertFalse(LycanVillagerRules.panicOverridesIntent(LycanVillagerRules.Intent.DEFEND));
        assertFalse(LycanVillagerRules.panicOverridesIntent(LycanVillagerRules.Intent.WITHDRAW));
        assertFalse(LycanVillagerRules.panicOverridesIntent(LycanVillagerRules.Intent.RETURN));
    }

    @Test void staleEvidenceReleasesOnlyDuringWarning() {
        assertTrue(LycanVillagerRules.releasesAggressor(false, false, true, true, false, false));
        assertFalse(LycanVillagerRules.releasesAggressor(false, false, false, true, false, false));
        assertFalse(LycanVillagerRules.releasesAggressor(false, false, true, false, false, false));
        assertTrue(LycanVillagerRules.releasesAggressor(true, false, false, false, false, false));
        assertTrue(LycanVillagerRules.releasesAggressor(false, true, false, false, false, false));
        assertTrue(LycanVillagerRules.releasesAggressor(false, false, false, false, true, false));
        assertTrue(LycanVillagerRules.releasesAggressor(false, false, false, false, false, true));
        assertFalse(LycanVillagerRules.releasesAggressor(false, false, false, false, false, false));
    }

    @Test void legalTransitionsPreserveWarningAndReturnSequence() {
        assertTrue(LycanVillagerRules.canTransition(LycanVillagerRules.Intent.ROUTINE, LycanVillagerRules.Intent.WARNING));
        assertFalse(LycanVillagerRules.canTransition(LycanVillagerRules.Intent.WARNING, LycanVillagerRules.Intent.DEFEND));
        assertTrue(LycanVillagerRules.canTransition(LycanVillagerRules.Intent.WARNING, LycanVillagerRules.Intent.INTERCEPT));
        assertTrue(LycanVillagerRules.canTransition(LycanVillagerRules.Intent.INTERCEPT, LycanVillagerRules.Intent.DEFEND));
        assertTrue(LycanVillagerRules.canTransition(LycanVillagerRules.Intent.DEFEND, LycanVillagerRules.Intent.WITHDRAW));
        assertTrue(LycanVillagerRules.canTransition(LycanVillagerRules.Intent.WITHDRAW, LycanVillagerRules.Intent.RETURN));
        assertTrue(LycanVillagerRules.canTransition(LycanVillagerRules.Intent.RETURN, LycanVillagerRules.Intent.ROUTINE));
    }

    @Test void watchRequiresSafeUnoccupiedRoutineAndAnchor() {
        var eligible = new LycanVillagerRules.WatchInputs(true, true, true, true, false, false, false, false, false, true);
        assertEquals(LycanVillagerRules.Intent.MOON_WATCH, LycanVillagerRules.watchIntent(eligible));
        assertEquals(LycanVillagerRules.Intent.BOUNDARY_WATCH,
            LycanVillagerRules.watchIntent(new LycanVillagerRules.WatchInputs(true, false, false, true, false, false, false, false, false, true)));
        assertEquals(LycanVillagerRules.Intent.ROUTINE,
            LycanVillagerRules.watchIntent(new LycanVillagerRules.WatchInputs(true, true, true, true, true, false, false, false, false, true)));
    }

    @Test void stableStaggerIsBounded() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-00000000002a");
        assertTrue(LycanVillagerRules.stagger(id, 100) >= 0);
        assertTrue(LycanVillagerRules.stagger(id, 100) < 100);
        assertEquals(LycanVillagerRules.stagger(id, 100), LycanVillagerRules.stagger(id, 100));
        assertThrows(IllegalArgumentException.class, () -> LycanVillagerRules.stagger(id, 0));
    }
}
