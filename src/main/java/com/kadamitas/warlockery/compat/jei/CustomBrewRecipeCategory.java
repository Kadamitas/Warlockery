package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class CustomBrewRecipeCategory extends AbstractRecipeCategory<CustomBrewJeiRecipe> {
    CustomBrewRecipeCategory(IGuiHelper gui) {
        super(WarlockeryJeiRecipeTypes.CUSTOM_BREWS, Component.translatable("jei.warlockery.custom.title"),
            gui.createDrawableItemLike(ModBlocks.ALL.get("cauldron").get()), 240, 176);
    }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, CustomBrewJeiRecipe recipe, IFocusGroup focuses) {
        JeiIngredients.addItem(builder.addInputSlot(4, 6).setStandardSlotBackground(), recipe.definition().ingredient(), 1);
        builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 218, 6).setStandardSlotBackground()
            .add(new ItemStack(ModBlocks.ALL.get("cauldron").get()));
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, CustomBrewJeiRecipe recipe, IFocusGroup focuses) {
        Component name = JeiIngredients.itemStacks(recipe.definition().ingredient(), 1).stream().findFirst()
            .map(stack -> stack.getHoverName().copy().append(": ").append(recipe.role()))
            .map(Component.class::cast).orElseGet(recipe::role);
        builder.addText(name, 180, 22).setPosition(28, 7).setColor(0xFF342040);
        builder.addText(recipe.details(), 232, 42).setPosition(4, 34).setColor(0xFF404040);
        builder.addText(Component.translatable("jei.warlockery.custom.setup"), 232, 42).setPosition(4, 80).setColor(0xFF404040);
        builder.addText(Component.translatable("jei.warlockery.custom.order"), 232, 44).setPosition(4, 126).setColor(0xFF404040);
    }

    @Override public Identifier getIdentifier(CustomBrewJeiRecipe recipe) { return recipe.id(); }
}
