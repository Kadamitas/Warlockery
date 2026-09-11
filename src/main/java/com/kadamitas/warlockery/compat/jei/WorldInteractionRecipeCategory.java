package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class WorldInteractionRecipeCategory extends AbstractRecipeCategory<WorldInteractionJeiRecipe> {
    WorldInteractionRecipeCategory(IGuiHelper gui) {
        super(WarlockeryJeiRecipeTypes.WORLD_INTERACTIONS, Component.translatable("jei.warlockery.world.title"),
            gui.createDrawableItemLike(ModBlocks.ALL.get("cauldron").get()), 220, 106);
    }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, WorldInteractionJeiRecipe recipe, IFocusGroup focuses) {
        JeiIngredients.addItem(builder.addInputSlot(12, 8).setStandardSlotBackground(), recipe.ingredient(), 1);
        JeiIngredients.addItem(builder.addInputSlot(46, 8).setStandardSlotBackground(), recipe.placedBlock(), 1);
        JeiIngredients.addItem(builder.addOutputSlot(178, 8).setOutputSlotBackground(), recipe.result(), 1);
    }
    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, WorldInteractionJeiRecipe recipe, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(112, 8);
        builder.addText(Component.translatable("jei.warlockery.world.anoint_cauldron"), 212, 72)
            .setPosition(4, 34).setColor(0xFF404040);
    }
    @Override public Identifier getIdentifier(WorldInteractionJeiRecipe recipe) { return recipe.id(); }
}
