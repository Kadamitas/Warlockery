package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.compat.viewer.RecipeViewerIngredients;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import net.minecraft.world.item.ItemStack;

final class JeiIngredients {
    private JeiIngredients() { }
    static void addItem(final IRecipeSlotBuilder slot, final String value, final int count) {
        slot.addItemStacks(itemStacks(value, count));
    }
    static List<ItemStack> itemStacks(final String value, final int count) {
        return RecipeViewerIngredients.itemStacks(value, count);
    }
    static Optional<ItemStack> directItem(final String value, final int count) {
        return RecipeViewerIngredients.directItem(value, count);
    }
    static long fluidAmount(final int milliBuckets) { return com.kadamitas.warlockery.util.FluidContents.dropletsFromMilliBuckets(milliBuckets); }
    static void addFluid(final IRecipeSlotBuilder slot, final String value, final int amount) {
        RecipeViewerIngredients.fluids(value).forEach(fluid -> slot.add(fluid, fluidAmount(amount)));
    }
}
