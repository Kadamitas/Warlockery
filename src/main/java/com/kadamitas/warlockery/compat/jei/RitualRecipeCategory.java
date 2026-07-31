package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import com.kadamitas.warlockery.ritual.RitualDefinition;
import com.kadamitas.warlockery.ritual.RitualManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class RitualRecipeCategory extends AbstractRecipeCategory<RitualManager.Entry> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 132;

    RitualRecipeCategory(final IGuiHelper guiHelper) {
        super(
            WarlockeryJeiRecipeTypes.RITUALS,
            Component.translatable("screen.warlockery.ritual.title"),
            icon(guiHelper),
            WIDTH,
            HEIGHT
        );
    }

    @Override
    public void setRecipe(
        final IRecipeLayoutBuilder builder,
        final RitualManager.Entry entry,
        final IFocusGroup focuses
    ) {
        final RitualDefinition definition = entry.definition();
        final List<RitualDefinition.Ingredient> ingredients = definition.requirements().ingredients();
        for (int index = 0; index < ingredients.size(); index++) {
            final RitualDefinition.Ingredient ingredient = ingredients.get(index);
            final var slot = builder.addInputSlot(4 + index * 20, 48).setStandardSlotBackground();
            JeiIngredients.addItem(slot, ingredient.ingredient(), ingredient.count());
            if ("glyph_transform".equals(definition.action())) {
                slot.addRichTooltipCallback((_, tooltip) ->
                    tooltip.add(Component.translatable("jei.warlockery.ritual.glyph_transform_sizes")));
            }
            if (!ingredient.consume()) {
                slot.addRichTooltipCallback((_, tooltip) ->
                    tooltip.add(Component.translatable("jei.warlockery.ritual.not_consumed")));
            }
        }
        int glyphIndex = 0;
        for (final ChalkCircleLayout.Ring ring : ChalkCircleLayout.rings(definition.glyphs())) {
            final var block = ModBlocks.ALL.get(ring.glyph());
            if (block != null) {
                builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 4 + glyphIndex * 20, 70)
                    .setStandardSlotBackground()
                    .add(new ItemStack(block.get(), ring.requiredCount()))
                    .addRichTooltipCallback((_, tooltip) ->
                        tooltip.add(Component.translatable("jei.warlockery.ritual.not_consumed")));
                glyphIndex++;
            }
        }
        output(definition).ifPresent(stack -> builder.addOutputSlot(154, 58).setOutputSlotBackground().add(stack));
    }

    @Override
    public void createRecipeExtras(
        final IRecipeExtrasBuilder builder,
        final RitualManager.Entry entry,
        final IFocusGroup focuses
    ) {
        final RitualDefinition definition = entry.definition();
        builder.addText(Component.translatable(definition.title()), WIDTH, 12)
            .setPosition(2, 0)
            .setColor(0xFF342040);
        builder.addText(Component.translatable(definition.description()), WIDTH, 30)
            .setPosition(2, 14)
            .setColor(0xFF505050);
        final Component timing = Component.translatable("jei.warlockery.ritual.power", definition.power())
            .append("  ")
            .append(Component.translatable(
                "jei.warlockery.ritual.casting_time",
                Math.max(1, definition.castingTime() / 20)
            ));
        builder.addText(timing, WIDTH, 12).setPosition(2, 94).setColor(0xFF404040);
        final Component conditions = conditions(definition);
        if (!conditions.getString().isBlank()) {
            builder.addText(
                Component.translatable("jei.warlockery.ritual.conditions", conditions),
                WIDTH,
                24
            ).setPosition(2, 108).setColor(0xFF505050);
        }
    }

    @Override
    public Identifier getIdentifier(final RitualManager.Entry entry) {
        return entry.id();
    }

    private static Optional<ItemStack> output(final RitualDefinition definition) {
        if (!List.of("summon_item", "bind_item", "bind_circle").contains(definition.action())) {
            return Optional.empty();
        }
        return JeiIngredients.directItem(definition.target(), definition.count());
    }

    private static Component conditions(final RitualDefinition definition) {
        final RitualDefinition.Requirements requirements = definition.requirements();
        final List<Component> conditions = new ArrayList<>();
        if (definition.nightOnly()) {
            conditions.add(Component.translatable("screen.warlockery.ritual.requirement.night"));
        }
        if (requirements.dayOnly()) {
            conditions.add(Component.translatable("screen.warlockery.ritual.requirement.day"));
        }
        if (requirements.fullMoon()) {
            conditions.add(Component.translatable("screen.warlockery.ritual.requirement.full_moon"));
        }
        if (requirements.raining()) {
            conditions.add(Component.translatable("screen.warlockery.ritual.requirement.rain"));
        }
        if (requirements.thundering()) {
            conditions.add(Component.translatable("screen.warlockery.ritual.requirement.thunder"));
        }
        if (!requirements.dimension().isBlank()) {
            conditions.add(Component.translatable(
                "screen.warlockery.ritual.requirement." + requirements.dimension().replace(':', '.')
            ));
        }
        if (requirements.minimumPlayers() > 1) {
            conditions.add(Component.translatable(
                "screen.warlockery.ritual.requirement_count",
                Component.translatable("screen.warlockery.ritual.requirement.coven"),
                requirements.minimumPlayers(),
                requirements.minimumPlayers()
            ));
        }
        requirements.entities().forEach(requirement -> conditions.add(entityRequirement(requirement)));
        Component joined = Component.empty();
        for (int index = 0; index < conditions.size(); index++) {
            if (index > 0) {
                joined = joined.copy().append(", ");
            }
            joined = joined.copy().append(conditions.get(index));
        }
        return joined;
    }

    private static Component entityRequirement(final RitualDefinition.EntityRequirement requirement) {
        final String value = requirement.entity();
        final Identifier id = Identifier.tryParse(value.startsWith("#") ? value.substring(1) : value);
        final Component label = id == null || value.startsWith("#") || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)
            ? Component.literal(value)
            : Component.translatable(BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescriptionId());
        return Component.translatable(
            "screen.warlockery.ritual.requirement_count",
            label,
            requirement.count(),
            requirement.count()
        );
    }

    private static IDrawable icon(final IGuiHelper guiHelper) {
        return guiHelper.createDrawableItemLike(ModItems.ALL.get("arcane_focus").get());
    }
}
