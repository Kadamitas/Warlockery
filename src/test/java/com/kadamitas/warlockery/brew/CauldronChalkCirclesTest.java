package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.brew.custom.CustomBrewDelivery;
import com.kadamitas.warlockery.brew.custom.CustomBrewFormula;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CauldronChalkCirclesTest {
    @Test
    void exactRingsUseTwelveAndTwentyFourNonOverlappingMarks() {
        final Set<CauldronChalkCircles.Offset> small = Set.copyOf(
            CauldronChalkCircles.offsets(CauldronChalkCircles.Size.SMALL)
        );
        final Set<CauldronChalkCircles.Offset> medium = Set.copyOf(
            CauldronChalkCircles.offsets(CauldronChalkCircles.Size.MEDIUM)
        );

        assertEquals(12, small.size());
        assertEquals(24, medium.size());
        assertTrue(java.util.Collections.disjoint(small, medium));
    }

    @Test
    void ritualInnerRingAndInfernalOuterRingCombineTheirInfluences() {
        final CauldronChalkCircles.State state = CauldronChalkCircles.evaluate(
            Set.copyOf(CauldronChalkCircles.offsets(CauldronChalkCircles.Size.SMALL)),
            Set.copyOf(CauldronChalkCircles.offsets(CauldronChalkCircles.Size.MEDIUM)),
            Set.of()
        );

        assertEquals(CauldronChalkCircles.RingKind.RITUAL, state.small().kind());
        assertEquals(CauldronChalkCircles.RingKind.INFERNAL, state.medium().kind());
        assertEquals(1.5F, state.potencyMultiplier());
        assertEquals(0.1F, state.stability());
        assertEquals(0.2F, state.mishapRisk());
        assertEquals(0.1F, state.riskDelta());
    }

    @Test
    void incompleteAndMixedRingsNeverGrantPowerOrStability() {
        final List<CauldronChalkCircles.Offset> small = CauldronChalkCircles.offsets(
            CauldronChalkCircles.Size.SMALL
        );
        final Set<CauldronChalkCircles.Offset> ritual = Set.copyOf(small.subList(0, 6));
        final Set<CauldronChalkCircles.Offset> infernal = Set.copyOf(small.subList(6, small.size()));
        final CauldronChalkCircles.State mixed = CauldronChalkCircles.evaluate(ritual, infernal, Set.of());
        final CauldronChalkCircles.State incomplete = CauldronChalkCircles.evaluate(ritual, Set.of(), Set.of());

        assertEquals(CauldronChalkCircles.RingKind.MIXED, mixed.small().kind());
        assertEquals(CauldronChalkCircles.RingKind.INCOMPLETE, incomplete.small().kind());
        assertEquals(1.0F, mixed.potencyMultiplier());
        assertEquals(0.0F, mixed.stability());
        assertEquals(0.0F, incomplete.mishapRisk());
    }

    @Test
    void infernalCircleRaisesBehaviorPotencyAndEffectStrength() {
        final CauldronChalkCircles.State circles = CauldronChalkCircles.evaluate(
            Set.of(),
            Set.copyOf(CauldronChalkCircles.offsets(CauldronChalkCircles.Size.MEDIUM)),
            Set.of()
        );
        final CustomBrewFormula formula = formula();

        final CustomBrewFormula influenced = CauldronChalkCircles.influence(formula, circles);

        assertEquals(3.0F, influenced.potency());
        assertEquals(1, influenced.effects().getFirst().amplifier());
        assertEquals(formula.components(), influenced.components());
        assertEquals(formula.altarPower(), influenced.altarPower());
    }

    private static CustomBrewFormula formula() {
        return new CustomBrewFormula(
            List.of("warlockery:test_effect", "warlockery:test_container"),
            List.of("warlockery:test_effect"),
            CustomBrewDelivery.THROWABLE,
            List.of(new BrewEffectSpec("minecraft:poison", 200, 0)),
            List.of(BrewBehavior.IGNITE),
            8,
            4,
            0,
            1,
            1,
            1,
            500,
            0x884422,
            4.0F,
            2.0F,
            false,
            false,
            false,
            false,
            0
        );
    }
}
