package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.crafting.MachineProfile;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.crafting.MachineRecipeSlotPlan;
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
    static final int POWER_TEXT_HEIGHT = 36;
    static final int IGNITION_TEXT_HEIGHT = 36;
    static final int EFFECT_TEXT_HEIGHT = 24;
    private static final int DIAGRAM_Y_OFFSET = 24;
    private final MachineProfile profile;
    private final MachineUiLayout layout;

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
        super(
            recipeType,
            title(profile),
            icon(profile, guiHelper),
            240,
            MachineUiLayout.forKind(profile.recipeType()).statusY() - DIAGRAM_Y_OFFSET + (profile.recipeType().equals("brazier") ? 132 : 68)
        );
        this.profile = profile;
        layout = MachineUiLayout.forKind(profile.recipeType());
    }

    @Override
    public void setRecipe(
        final IRecipeLayoutBuilder builder,
        final MachineRecipeManager.Match match,
        final IFocusGroup focuses
    ) {
        final MachineRecipeDefinition recipe = match.recipe();
        final java.util.List<Integer> inputSlots = MachineRecipeSlotPlan.inputSlots(profile, recipe);
        for (int index = 0; index < recipe.inputs().size(); index++) {
            final MachineRecipeDefinition.Input input = recipe.inputs().get(index);
            final MachineUiLayout.SlotPosition position = layout.slots().get(inputSlots.get(index));
            builder.addInputSlot(position.x(), position.y() - DIAGRAM_Y_OFFSET).setStandardSlotBackground()
                .addItemStacks(JeiMachineInputs.itemStacks(match, index));
        }
        recipe.fluid().ifPresent(fluid -> {
            final int[] fluidPosition = fluidPosition(layout.kind());
            final var slot = builder.addInputSlot(fluidPosition[0], fluidPosition[1] - DIAGRAM_Y_OFFSET)
                .setStandardSlotBackground()
                .setFluidRenderer(JeiIngredients.fluidAmount(fluid.amount()), true, 16, 16);
            JeiIngredients.addFluid(slot, fluid.ingredient(), fluid.amount());
        });
        for (int index = 0; index < recipe.outputs().size(); index++) {
            final MachineRecipeDefinition.Output output = recipe.outputs().get(index);
            final MachineUiLayout.SlotPosition position = layout.slots().get(profile.outputStart() + index);
            JeiIngredients.directItem(output.item(), output.count()).ifPresent(stack -> builder
                .addOutputSlot(position.x(), position.y() - DIAGRAM_Y_OFFSET)
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
        final int[] arrow = arrowPosition(layout.kind());
        builder.addAnimatedRecipeArrow(recipe.processingTime()).setPosition(arrow[0], arrow[1] - DIAGRAM_Y_OFFSET);
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
        builder.addText(details, getWidth() - 4, 24)
            .setPosition(2, layout.statusY() - DIAGRAM_Y_OFFSET + 2)
            .setColor(0xFF404040);
        if (recipe.powerMode() == com.kadamitas.warlockery.crafting.PowerMode.CONTINUOUS) {
            builder.addText(Component.translatable("manual.warlockery.machine_recipe.continuous_power"), getWidth() - 4, POWER_TEXT_HEIGHT)
                .setPosition(2, layout.statusY() - DIAGRAM_Y_OFFSET + 28).setColor(0xFF404040);
        }
        if (profile.recipeType().equals("brazier")) {
            builder.addText(Component.translatable("manual.warlockery.machine_recipe.ignite"), getWidth() - 4, IGNITION_TEXT_HEIGHT)
                .setPosition(2, layout.statusY() - DIAGRAM_Y_OFFSET + 68).setColor(0xFF404040);
        }
        com.kadamitas.warlockery.crafting.BrazierEffectRuntime.Effect.fromRecipe(match.id()).ifPresent(effect -> {
            builder.addText(Component.translatable("jei.warlockery.effect." + effect.recipePath()), getWidth() - 4, EFFECT_TEXT_HEIGHT)
                .setPosition(2, layout.statusY() - DIAGRAM_Y_OFFSET + 108).setColor(0xFF404040);
        });
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

    private static int[] fluidPosition(final String machine) {
        return switch (machine) {
            case "distillery" -> new int[] {54, 84};
            case "kettle" -> new int[] {94, 54};
            case "cauldron" -> new int[] {70, 56};
            default -> new int[] {78, 50};
        };
    }

    private static int[] arrowPosition(final String machine) {
        return switch (machine) {
            case "alchemical_oven" -> new int[] {103, 81};
            case "distillery" -> new int[] {126, 84};
            case "kettle" -> new int[] {126, 92};
            case "cauldron" -> new int[] {154, 85};
            case "silvervat" -> new int[] {104, 89};
            case "spinningwheel" -> new int[] {146, 87};
            case "brazier" -> new int[] {137, 87};
            default -> new int[] {101, 82};
        };
    }
}
