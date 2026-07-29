package com.kadamitas.warlockery.brew.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.kadamitas.warlockery.brew.BrewBehavior;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CustomBrewBottlingTest {
    @Test
    void bottlingExpandsAThrowableBatchWithoutCreatingImpactBottles() {
        final CustomBrewFormula formula = formula(CustomBrewDelivery.THROWABLE, 0, true);

        assertEquals(3, formula.outputCount());
        assertFalse(formula.behaviorKind().behaviors().contains(BrewBehavior.BOTTLE_YIELD));
    }

    @Test
    void strongerPowerIncreasesTheBoundedBatchForEveryDelivery() {
        assertEquals(6, formula(CustomBrewDelivery.DRINKABLE, 3, true).outputCount());
        assertEquals(8, formula(CustomBrewDelivery.THROWABLE, 8, true).outputCount());
    }

    @Test
    void ordinaryFormulasStillProduceOneContainer() {
        assertEquals(1, formula(CustomBrewDelivery.DRINKABLE, 3, false).outputCount());
    }

    private static CustomBrewFormula formula(
        final CustomBrewDelivery delivery,
        final int powerLevel,
        final boolean bottling
    ) {
        return new CustomBrewFormula(
            List.of("capacity", "bottling", "container"),
            bottling ? List.of("bottling") : List.of(),
            delivery,
            List.of(),
            bottling ? List.of(BrewBehavior.BOTTLE_YIELD) : List.of(),
            8,
            3,
            powerLevel,
            1,
            1,
            1,
            0,
            0x7FC8B8,
            3.0F,
            1.0F,
            false,
            false,
            false,
            false,
            0
        );
    }
}
