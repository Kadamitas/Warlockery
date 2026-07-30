package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class MachineInputAcceptanceTest {
    private static final MachineProfile OVEN = MachineProfiles.forBlock("alchemical_oven");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clearRecipeCatalog() {
        MachineRecipeManager.INSTANCE.apply(Map.of(), null, null);
    }

    @Test
    void semanticRulesSeparateRecipeInputsFuelAndOutputs() {
        final Predicate<ItemStack> recipeInput = stack -> stack.is(Items.BIRCH_SAPLING);
        final Predicate<ItemStack> fuel = stack -> stack.is(Items.COAL);

        assertTrue(accepts(0, Items.BIRCH_SAPLING, true, recipeInput, fuel));
        assertFalse(accepts(0, Items.IRON_SWORD, true, recipeInput, fuel));
        assertFalse(accepts(0, Items.COAL, true, recipeInput, fuel));
        assertTrue(accepts(OVEN.fuelSlot(), Items.COAL, true, recipeInput, fuel));
        assertFalse(accepts(OVEN.fuelSlot(), Items.BIRCH_SAPLING, true, recipeInput, fuel));
        assertFalse(accepts(OVEN.outputStart(), Items.BIRCH_SAPLING, true, recipeInput, fuel));
    }

    @Test
    void clientWithoutReloadedRecipesDoesNotDeadlockInputSlots() {
        final Predicate<ItemStack> emptyCatalog = _ -> false;
        final Predicate<ItemStack> fuel = stack -> stack.is(Items.COAL);

        assertTrue(accepts(0, Items.BIRCH_SAPLING, false, emptyCatalog, fuel));
        assertTrue(accepts(0, Items.IRON_SWORD, false, emptyCatalog, fuel));
        assertFalse(accepts(0, Items.IRON_SWORD, true, emptyCatalog, fuel));
        assertTrue(accepts(OVEN.fuelSlot(), Items.IRON_SWORD, false, emptyCatalog, fuel));
        assertFalse(accepts(OVEN.fuelSlot(), Items.IRON_SWORD, true, emptyCatalog, fuel));
        assertFalse(accepts(OVEN.outputStart(), Items.BIRCH_SAPLING, false, emptyCatalog, fuel));
    }

    @Test
    void recipeManagerRebuildsItsInputAllowlistOnReload() {
        final MachineRecipeDefinition recipe = new MachineRecipeDefinition(
            "alchemical_oven",
            List.of(new MachineRecipeDefinition.Input("minecraft:birch_sapling", 1)),
            List.of(new MachineRecipeDefinition.Output("minecraft:white_wool", 1)),
            80,
            true,
            Optional.empty(),
            0
        );
        MachineRecipeManager.INSTANCE.apply(
            Map.of(Identifier.parse("warlockery:test_oven_input"), recipe),
            null,
            null
        );

        assertTrue(MachineRecipeManager.INSTANCE.acceptsInput(OVEN, stack(Items.BIRCH_SAPLING)));
        assertFalse(MachineRecipeManager.INSTANCE.acceptsInput(OVEN, stack(Items.IRON_SWORD)));

        MachineRecipeManager.INSTANCE.apply(Map.of(), null, null);
        assertFalse(MachineRecipeManager.INSTANCE.acceptsInput(OVEN, stack(Items.BIRCH_SAPLING)));
    }

    private static boolean accepts(
        final int slot,
        final net.minecraft.world.level.ItemLike item,
        final boolean authoritative,
        final Predicate<ItemStack> recipeInput,
        final Predicate<ItemStack> fuel
    ) {
        return MachineInsertionRules.accepts(
            OVEN,
            slot,
            stack(item),
            authoritative,
            recipeInput,
            fuel
        );
    }

    private static ItemStack stack(final net.minecraft.world.level.ItemLike item) {
        return new ItemStack(Holder.direct(item.asItem()));
    }
}
