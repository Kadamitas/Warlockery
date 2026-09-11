package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.compat.viewer.RecipeViewerIngredients;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import java.util.List;
import net.minecraft.world.item.ItemStack;

final class JeiMachineInputs {
    private JeiMachineInputs() { }
    static List<ItemStack> itemStacks(final MachineRecipeManager.Match match, final int ingredientIndex) {
        return RecipeViewerIngredients.machineInputs(match, ingredientIndex);
    }
}
