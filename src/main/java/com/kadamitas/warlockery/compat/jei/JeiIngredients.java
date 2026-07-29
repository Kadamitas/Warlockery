package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.util.FluidIngredient;
import com.kadamitas.warlockery.util.ItemIngredient;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

final class JeiIngredients {
    private JeiIngredients() {
    }

    static void addItem(final IRecipeSlotBuilder slot, final String value, final int count) {
        slot.addItemStacks(itemStacks(value, count));
    }

    static List<ItemStack> itemStacks(final String value, final int count) {
        return ItemIngredient.parse(value).map(ingredient -> ingredient.tag()
            ? taggedItems(ingredient, count)
            : directItem(ingredient, count).stream().toList()
        ).orElseGet(List::of);
    }

    static Optional<ItemStack> directItem(final String value, final int count) {
        return ItemIngredient.parse(value)
            .filter(ingredient -> !ingredient.tag())
            .flatMap(ingredient -> directItem(ingredient, count));
    }

    static void addFluid(final IRecipeSlotBuilder slot, final String value, final int amount) {
        FluidIngredient.parse(value).ifPresent(ingredient -> {
            if (ingredient.tag()) {
                StreamSupport.stream(BuiltInRegistries.FLUID
                        .getTagOrEmpty(TagKey.create(Registries.FLUID, ingredient.id()))
                        .spliterator(), false)
                    .map(holder -> holder.value())
                    .distinct()
                    .forEach(fluid -> slot.add(fluid, amount));
            } else {
                final Fluid fluid = BuiltInRegistries.FLUID.getValue(ingredient.id());
                if (fluid != null) {
                    slot.add(fluid, amount);
                }
            }
        });
    }

    private static List<ItemStack> taggedItems(final ItemIngredient ingredient, final int count) {
        return StreamSupport.stream(BuiltInRegistries.ITEM
                .getTagOrEmpty(TagKey.create(Registries.ITEM, ingredient.id()))
                .spliterator(), false)
            .map(holder -> new ItemStack(holder.value(), count))
            .toList();
    }

    private static Optional<ItemStack> directItem(final ItemIngredient ingredient, final int count) {
        final Item item = BuiltInRegistries.ITEM.getValue(ingredient.id());
        return Optional.ofNullable(item).map(value -> new ItemStack(value, count));
    }
}
