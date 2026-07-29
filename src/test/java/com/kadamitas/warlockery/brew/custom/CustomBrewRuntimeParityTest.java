package com.kadamitas.warlockery.brew.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kadamitas.warlockery.brew.BrewBehavior;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CustomBrewRuntimeParityTest {
    @Test
    void standardInstantDamageIsCappedAtOneVanillaHealthBar() {
        assertEquals(6.0F, CustomBrewDamageRules.instantDamage(0, 1.0, false));
        assertEquals(12.0F, CustomBrewDamageRules.instantDamage(1, 1.0, false));
        assertEquals(20.0F, CustomBrewDamageRules.instantDamage(8, 1.0, false));
    }

    @Test
    void uncappedInstantDamageRetainsPowerWithAnEngineSafetyBound() {
        assertEquals(1_536.0F, CustomBrewDamageRules.instantDamage(8, 1.0, true));
        assertEquals(2_048.0F, CustomBrewDamageRules.instantDamage(30, 1.0, true));
        assertEquals(768.0F, CustomBrewDamageRules.instantDamage(8, 0.5, true));
    }

    @Test
    void distanceScaleCannotCreateNegativeOrAmplifiedDamage() {
        assertEquals(0.0F, CustomBrewDamageRules.instantDamage(4, -1.0, true));
        assertEquals(96.0F, CustomBrewDamageRules.instantDamage(4, 2.0, true));
    }

    @Test
    void skipBlockAndEntityModifiersFilterTheirRuntimeBehaviors() {
        final List<BrewBehavior> behaviors = List.of(
            BrewBehavior.GROW,
            BrewBehavior.PUSH,
            BrewBehavior.FREEZE
        );
        assertEquals(behaviors, formula(behaviors, false, false).behaviorKind().behaviors());
        assertEquals(
            List.of(BrewBehavior.PUSH),
            formula(behaviors, true, false).behaviorKind().behaviors()
        );
        assertEquals(
            List.of(BrewBehavior.GROW),
            formula(behaviors, false, true).behaviorKind().behaviors()
        );
        assertEquals(List.of(), formula(behaviors, true, true).behaviorKind().behaviors());
    }

    private static CustomBrewFormula formula(
        final List<BrewBehavior> behaviors,
        final boolean skipBlocks,
        final boolean skipEntities
    ) {
        return new CustomBrewFormula(
            List.of(),
            List.of(),
            CustomBrewDelivery.THROWABLE,
            List.of(),
            behaviors,
            8,
            1,
            0,
            1,
            1,
            1,
            0,
            0x123456,
            4.0F,
            1.0F,
            false,
            skipBlocks,
            skipEntities,
            false,
            0
        );
    }
}
