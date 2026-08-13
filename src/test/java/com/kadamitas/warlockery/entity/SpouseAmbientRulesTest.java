package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SpouseAmbientRulesTest {
    @Test
    void hardSafetyAndRelationshipGatesPreventAmbientWork() {
        assertEquals(SpouseAmbientRules.Routine.NONE, choose(context(false, true, true, true, true, true, true), 0, 0));
        assertEquals(SpouseAmbientRules.Routine.NONE, choose(context(true, false, true, true, true, true, true), 0, 0));
        assertEquals(SpouseAmbientRules.Routine.NONE, choose(context(true, true, false, true, true, true, true), 0, 0));
        assertEquals(SpouseAmbientRules.Routine.NONE, choose(context(true, true, true, false, true, true, true), 0, 0));
        assertEquals(SpouseAmbientRules.Routine.NONE, choose(context(true, true, true, true, false, true, true), 0, 0));
        assertEquals(SpouseAmbientRules.Routine.NONE, choose(context(true, true, true, true, true, false, true), 0, 0));
    }

    @Test
    void cookingWinsWhenBothRareOpportunitiesAreReady() {
        assertEquals(
            SpouseAmbientRules.Routine.COOK,
            choose(context(true, true, true, true, true, true, true), 0, 0)
        );
    }

    @Test
    void affectionCanRunWithoutCookingWork() {
        assertEquals(
            SpouseAmbientRules.Routine.KISS,
            choose(context(true, true, true, true, true, true, false), 0, 1)
        );
    }

    @Test
    void cooldownsKeepRareActionsBounded() {
        final long now = 30_000L;
        final SpouseAmbientRules.Context context = new SpouseAmbientRules.Context(
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            now,
            now + 1,
            now + 1
        );
        assertEquals(SpouseAmbientRules.Routine.NONE, choose(context, 0, 0));
        assertEquals(now + SpouseAmbientRules.KISS_COOLDOWN_TICKS,
            SpouseAmbientRules.nextReadyAt(SpouseAmbientRules.Routine.KISS, now));
        assertEquals(now + SpouseAmbientRules.COOK_COOLDOWN_TICKS,
            SpouseAmbientRules.nextReadyAt(SpouseAmbientRules.Routine.COOK, now));
    }

    @Test
    void invalidRandomInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> choose(context(true, true, true, true, true, true, true), -1, 0));
    }

    private static SpouseAmbientRules.Routine choose(
        final SpouseAmbientRules.Context context,
        final int kissRoll,
        final int cookRoll
    ) {
        return SpouseAmbientRules.choose(context, kissRoll, cookRoll);
    }

    private static SpouseAmbientRules.Context context(
        final boolean married,
        final boolean sameDimension,
        final boolean peaceful,
        final boolean safe,
        final boolean adult,
        final boolean emptyHand,
        final boolean hasCookWork
    ) {
        return new SpouseAmbientRules.Context(
            married,
            sameDimension,
            peaceful,
            safe,
            adult,
            emptyHand,
            hasCookWork,
            30_000L,
            0L,
            0L
        );
    }
}
