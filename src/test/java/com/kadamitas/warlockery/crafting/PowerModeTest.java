package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class PowerModeTest {
    @Test
    void legacyRecipesInferCompletionPowerWithoutChangingOldData() {
        assertEquals(PowerMode.NONE, recipe(0).powerMode());
        assertEquals(PowerMode.ON_COMPLETE, recipe(50).powerMode());
    }

    @Test
    void continuousPowerDistributesExactFractionalRatesWithoutRoundingLoss() {
        final int spinningTotal = IntStream.range(0, 300)
            .map(progress -> PowerMode.CONTINUOUS.powerForAdvance(180, 300, progress, progress + 1))
            .sum();
        final int distillingTotal = IntStream.range(0, 800)
            .map(progress -> PowerMode.CONTINUOUS.powerForAdvance(480, 800, progress, progress + 1))
            .sum();

        assertEquals(180, spinningTotal);
        assertEquals(480, distillingTotal);
        assertEquals(600, PowerMode.CONTINUOUS.millipowerPerTick(180, 300));
        assertEquals(600, PowerMode.CONTINUOUS.millipowerPerTick(480, 800));
        assertEquals(1, PowerMode.CONTINUOUS.requiredAvailablePower(180));
        assertEquals(0, PowerMode.CONTINUOUS.completionCost(180));
    }

    @Test
    void powerModeAndTotalMustAgree() {
        assertThrows(IllegalArgumentException.class, () -> explicitRecipe(0, PowerMode.CONTINUOUS));
        assertThrows(IllegalArgumentException.class, () -> explicitRecipe(20, PowerMode.NONE));
    }

    private static MachineRecipeDefinition recipe(final int altarPower) {
        return new MachineRecipeDefinition(
            "spinningwheel",
            List.of(new MachineRecipeDefinition.Input("minecraft:string", 1)),
            List.of(new MachineRecipeDefinition.Output("minecraft:cobweb", 1)),
            300,
            false,
            Optional.empty(),
            altarPower
        );
    }

    private static MachineRecipeDefinition explicitRecipe(final int altarPower, final PowerMode powerMode) {
        return new MachineRecipeDefinition(
            "spinningwheel",
            List.of(new MachineRecipeDefinition.Input("minecraft:string", 1)),
            List.of(new MachineRecipeDefinition.Output("minecraft:cobweb", 1)),
            300,
            false,
            Optional.empty(),
            altarPower,
            powerMode
        );
    }
}

