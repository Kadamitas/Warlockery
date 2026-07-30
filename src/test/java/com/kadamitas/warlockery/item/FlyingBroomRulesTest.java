package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.brew.BrewFactory;
import org.junit.jupiter.api.Test;

final class FlyingBroomRulesTest {
    @Test
    void missingBroomEndsNonPrivilegedFlight() {
        final FlyingBroomRules.FlightDecision decision = FlyingBroomRules.decide(true, false, false, false);
        assertFalse(decision.active());
        assertFalse(decision.mayFly());
        assertFalse(decision.keepFlying());
    }

    @Test
    void soaringEffectPowersTheBroomUpgrade() {
        final FlyingBroomRules.FlightDecision normal = FlyingBroomRules.decide(true, true, false, false);
        final FlyingBroomRules.FlightDecision soaring = FlyingBroomRules.decide(true, true, false, true);
        assertTrue(normal.active());
        assertTrue(normal.mayFly());
        assertEquals(FlyingBroomRules.NORMAL_SPEED, normal.speed());
        assertEquals(FlyingBroomRules.SOARING_SPEED, soaring.speed());
    }

    @Test
    void infusedSoaringBrewDoesNotGrantSlowFalling() {
        final var kind = BrewFactory.legacyKind("ingredient_brew_soaring").orElseThrow();
        assertTrue(kind.effects().stream().anyMatch(effect -> "warlockery:soaring".equals(effect.effect())));
        assertFalse(kind.effects().stream().anyMatch(effect -> "minecraft:slow_falling".equals(effect.effect())));
        assertEquals(20 * 60 * 120, kind.effects().getFirst().duration());
    }

    @Test
    void invalidSpeedIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new FlyingBroomRules.FlightDecision(true, true, true, 0.0F)
        );
    }
}
