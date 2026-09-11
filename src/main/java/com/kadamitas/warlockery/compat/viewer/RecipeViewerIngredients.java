package com.kadamitas.warlockery.compat.viewer;

import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.crafting.MachineRecipeSlotPlan;
import com.kadamitas.warlockery.util.FluidIngredient;
import com.kadamitas.warlockery.util.ItemIngredient;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

public final class RecipeViewerIngredients {
    private static List<MachineRecipeManager.Match> ovenSource;
    private static Function<NonNullList<ItemStack>, Optional<MachineRecipeManager.Match>> ovenMatcher;

    private RecipeViewerIngredients() { }

    public static List<ItemStack> itemStacks(final String value, final int count) {
        if (count <= 0) return List.of();
        return ItemIngredient.parse(value).map(ingredient -> ingredient.tag()
            ? StreamSupport.stream(BuiltInRegistries.ITEM
                .getTagOrEmpty(TagKey.create(Registries.ITEM, ingredient.id())).spliterator(), false)
                .map(holder -> new ItemStack(holder.value(), count)).filter(stack -> !stack.isEmpty()).toList()
            : directItem(value, count).stream().toList()
        ).orElseGet(List::of);
    }

    public static Optional<ItemStack> directItem(final String value, final int count) {
        if (count <= 0) return Optional.empty();
        return ItemIngredient.parse(value).filter(ingredient -> !ingredient.tag())
            .flatMap(ingredient -> BuiltInRegistries.ITEM.get(ingredient.id()))
            .map(holder -> new ItemStack(holder.value(), count)).filter(stack -> !stack.isEmpty());
    }

    public static List<Fluid> fluids(final String value) {
        return FluidIngredient.parse(value).map(ingredient -> ingredient.tag()
            ? StreamSupport.stream(BuiltInRegistries.FLUID
                .getTagOrEmpty(TagKey.create(Registries.FLUID, ingredient.id())).spliterator(), false)
                .map(holder -> holder.value()).distinct().toList()
            : BuiltInRegistries.FLUID.get(ingredient.id()).stream().map(holder -> holder.value()).toList()
        ).orElseGet(List::of);
    }

    public static List<ItemStack> machineInputs(final MachineRecipeManager.Match match, final int ingredientIndex) {
        final var input = match.recipe().inputs().get(ingredientIndex);
        final var choices = itemStacks(input.ingredient(), input.count());
        if (ingredientIndex != 0 || !match.recipe().machine().equals("alchemical_oven")) return choices;
        final var profile = MachineProfiles.forRecipeType(match.recipe().machine()).orElseThrow();
        final var slots = MachineRecipeSlotPlan.inputSlots(profile, match.recipe());
        final var matcher = ovenMatcher();
        return choices.stream().filter(candidate -> {
            final var inventory = NonNullList.withSize(profile.outputEnd(), ItemStack.EMPTY);
            for (int index = 0; index < match.recipe().inputs().size(); index++) {
                final var requirement = match.recipe().inputs().get(index);
                final var alternatives = itemStacks(requirement.ingredient(), requirement.count());
                if (alternatives.isEmpty()) return false;
                inventory.set(slots.get(index), index == ingredientIndex ? candidate.copy() : alternatives.getFirst().copy());
            }
            return matcher.apply(inventory).filter(selected -> selected.id().equals(match.id())).isPresent();
        }).toList();
    }

    private static synchronized Function<NonNullList<ItemStack>, Optional<MachineRecipeManager.Match>> ovenMatcher() {
        final var recipes = RecipeViewerCatalog.machines();
        if (ovenSource != recipes) {
            ovenSource = recipes;
            ovenMatcher = MachineRecipeManager.inputMatcher(recipes,
                MachineProfiles.forRecipeType("alchemical_oven").orElseThrow());
        }
        return ovenMatcher;
    }
}
