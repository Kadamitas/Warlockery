package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class MachineRecipeSlotPlanTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void ovenJarIsDrawnInItsDedicatedFourthInputSlot() {
        assertEquals(
            List.of(0, 3),
            MachineRecipeSlotPlan.inputSlots(
                MachineProfiles.forRecipeType("alchemical_oven").orElseThrow(),
                recipe("alchemical_oven", "#minecraft:saplings", "warlockery:ingredient_clay_jar")
            )
        );
    }

    @Test
    void distilleryJarIsDrawnInItsDedicatedThirdInputSlot() {
        assertEquals(
            List.of(0, 1, 2),
            MachineRecipeSlotPlan.inputSlots(
                MachineProfiles.forRecipeType("distillery").orElseThrow(),
                recipe(
                    "distillery",
                    "minecraft:ender_pearl",
                    "warlockery:ingredient_oil_of_vitriol",
                    "warlockery:ingredient_clay_jar"
                )
            )
        );
    }

    @Test
    void taggedDedicatedIngredientsKeepTheirSemanticSlot() {
        final MachineProfile taggedDedicatedInput = new MachineProfile(
            "tagged", 3, 3, 1, -1, false, true, false, "minecraft:stone",
            Optional.of("#minecraft:planks"), false
        );

        assertEquals(
            List.of(2),
            MachineRecipeSlotPlan.inputSlots(
                taggedDedicatedInput,
                recipe("tagged", "#minecraft:planks")
            )
        );
    }

    private static MachineRecipeDefinition recipe(final String machine, final String... ingredients) {
        return new MachineRecipeDefinition(
            machine,
            java.util.Arrays.stream(ingredients).map(value -> new MachineRecipeDefinition.Input(value, 1)).toList(),
            List.of(new MachineRecipeDefinition.Output("minecraft:stone", 1)),
            200,
            "alchemical_oven".equals(machine),
            Optional.empty(),
            0
        );
    }
}

