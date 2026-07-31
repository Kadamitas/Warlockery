package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.crafting.MachineProfile;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.menu.MachineUiLayout;
import java.util.Objects;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

final class MachineRecipeCategory extends AbstractRecipeCategory<MachineRecipeManager.Match> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 82;
    private final MachineProfile profile;

    MachineRecipeCategory(
        final String machine,
        final IRecipeType<MachineRecipeManager.Match> recipeType,
        final IGuiHelper guiHelper
    ) {
        this(recipeType, profile(machine), guiHelper);
    }

    private MachineRecipeCategory(
        final IRecipeType<MachineRecipeManager.Match> recipeType,
        final MachineProfile profile,
        final IGuiHelper guiHelper
    ) {
        super(recipeType, title(profile), icon(profile, guiHelper), WIDTH, HEIGHT);
        this.profile = profile;
    }

    @Override
    public void setRecipe(
        final IRecipeLayoutBuilder builder,
        final MachineRecipeManager.Match match,
        final IFocusGroup focuses
    ) {
        final MachineRecipeDefinition recipe = match.recipe();
        final MachineUiLayout layout = MachineUiLayout.forKind(profile.recipeType());
        for (int index = 0; index < recipe.inputs().size(); index++) {
            final MachineRecipeDefinition.Input input = recipe.inputs().get(index);
            final MachineUiLayout.SlotPosition position = layout.slots().get(index);
            JeiIngredients.addItem(
                builder.addInputSlot(position.x(), position.y()).setStandardSlotBackground(),
                input.ingredient(),
                input.count()
            );
        }
        recipe.fluid().ifPresent(fluid -> {
            final var slot = builder.addInputSlot(70, 16)
                .setStandardSlotBackground()
                .setFluidRenderer(fluid.amount(), true, 16, 16);
            JeiIngredients.addFluid(slot, fluid.ingredient(), fluid.amount());
        });
        for (int index = 0; index < recipe.outputs().size(); index++) {
            final MachineRecipeDefinition.Output output = recipe.outputs().get(index);
            final MachineUiLayout.SlotPosition position = layout.slots().get(profile.outputStart() + index);
            JeiIngredients.directItem(output.item(), output.count()).ifPresent(stack -> builder
                .addOutputSlot(position.x(), position.y())
                .setOutputSlotBackground()
                .add(stack));
        }
    }

    @Override
    public void createRecipeExtras(
        final IRecipeExtrasBuilder builder,
        final MachineRecipeManager.Match match,
        final IFocusGroup focuses
    ) {
        final MachineRecipeDefinition recipe = match.recipe();
        builder.addAnimatedRecipeArrow(recipe.processingTime()).setPosition(101, 18);
        Component details = Component.translatable(
            "jei.warlockery.machine.processing_time",
            Math.max(1, recipe.processingTime() / 20)
        );
        if (recipe.altarPower() > 0) {
            details = details.copy().append("  ").append(Component.translatable(
                "jei.warlockery.machine.altar_power",
                recipe.altarPower()
            ));
        }
        if (recipe.requiresFuel()) {
            details = details.copy().append("  ").append(Component.translatable("jei.warlockery.machine.fuel"));
        }
        if (profile.requiresExternalHeat()) {
            details = details.copy().append("  ").append(Component.translatable("jei.warlockery.machine.heat"));
        }
        builder.addText(details, WIDTH, 24).setPosition(2, 56).setColor(0xFF404040);
    }

    @Override
    public boolean isHandled(final MachineRecipeManager.Match match) {
        return profile.recipeType().equals(match.recipe().machine());
    }

    @Override
    public Identifier getIdentifier(final MachineRecipeManager.Match match) {
        return match.id();
    }

    private static MachineProfile profile(final String machine) {
        return MachineProfiles.forRecipeType(machine)
            .orElseThrow(() -> new IllegalArgumentException("Unknown machine recipe type: " + machine));
    }

    private static Component title(final MachineProfile profile) {
        return Component.translatable(block(profile).getDescriptionId());
    }

    private static IDrawable icon(final MachineProfile profile, final IGuiHelper guiHelper) {
        return guiHelper.createDrawableItemLike(block(profile));
    }

    private static Block block(final MachineProfile profile) {
        return Objects.requireNonNull(ModBlocks.ALL.get(profile.displayBlock()), profile.displayBlock()).get();
    }
}
