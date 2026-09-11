package com.kadamitas.warlockery.compat.emi;

import com.kadamitas.warlockery.client.ManualRitualInstructions;
import com.kadamitas.warlockery.compat.jei.CustomBrewJeiRecipe;
import com.kadamitas.warlockery.compat.viewer.RecipeViewerIngredients;
import com.kadamitas.warlockery.crafting.BrazierEffectRuntime;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.crafting.PowerMode;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import com.kadamitas.warlockery.ritual.RitualDefinition;
import com.kadamitas.warlockery.ritual.RitualManager;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class WarlockeryEmiRecipe implements EmiRecipe {
    private static final int WIDTH = 240;
    private final EmiRecipeCategory category;
    private final Identifier id;
    private final List<EmiIngredient> inputs;
    private final List<EmiIngredient> catalysts;
    private final List<EmiStack> outputs;
    private final List<Component> paragraphs;
    private final MachineRecipeManager.Match machine;
    private final List<ChalkCircleLayout.Ring> rings;
    private final boolean recipeTree;

    private WarlockeryEmiRecipe(EmiRecipeCategory category, Identifier id, List<EmiIngredient> inputs,
            List<EmiIngredient> catalysts, List<EmiStack> outputs, List<Component> paragraphs,
            MachineRecipeManager.Match machine, List<ChalkCircleLayout.Ring> rings, boolean recipeTree) {
        this.category = category;
        this.id = id;
        this.inputs = List.copyOf(inputs);
        this.catalysts = List.copyOf(catalysts);
        this.outputs = List.copyOf(outputs);
        this.paragraphs = List.copyOf(paragraphs);
        this.machine = machine;
        this.rings = List.copyOf(rings);
        this.recipeTree = recipeTree;
    }

    static WarlockeryEmiRecipe machine(EmiRecipeCategory category, MachineRecipeManager.Match match) {
        var recipe = match.recipe();
        var inputs = new ArrayList<EmiIngredient>();
        for (int index = 0; index < recipe.inputs().size(); index++) {
            inputs.add(ingredient(RecipeViewerIngredients.machineInputs(match, index), recipe.inputs().get(index).count()));
        }
        recipe.fluid().ifPresent(fluid -> inputs.add(EmiIngredient.of(RecipeViewerIngredients.fluids(fluid.ingredient())
            .stream().map(value -> EmiStack.of(value, fluidAmount(fluid.amount()))).toList(), fluidAmount(fluid.amount()))));
        var outputs = recipe.outputs().stream().flatMap(output -> RecipeViewerIngredients.directItem(output.item(), output.count()).stream())
            .map(EmiStack::of).toList();
        var profile = MachineProfiles.forRecipeType(recipe.machine()).orElseThrow();
        var catalysts = new ArrayList<EmiIngredient>();
        catalysts.add(item("warlockery:" + profile.displayBlock(), 1));
        if (recipe.altarPower() > 0) catalysts.add(item("warlockery:altar", 1));
        var text = new ArrayList<Component>();
        text.add(category.getName());
        text.add(Component.translatable("jei.warlockery.machine.processing_time", Math.max(1, recipe.processingTime() / 20)));
        if (recipe.altarPower() > 0) text.add(Component.translatable("jei.warlockery.machine.altar_power", recipe.altarPower()));
        if (recipe.requiresFuel()) text.add(Component.translatable("jei.warlockery.machine.fuel"));
        if (profile.requiresExternalHeat()) text.add(Component.translatable("jei.warlockery.machine.heat"));
        if (recipe.powerMode() == PowerMode.CONTINUOUS) text.add(Component.translatable("manual.warlockery.machine_recipe.continuous_power"));
        if (recipe.machine().equals("brazier")) text.add(Component.translatable("manual.warlockery.machine_recipe.ignite"));
        BrazierEffectRuntime.Effect.fromRecipe(match.id()).ifPresent(effect ->
            text.add(Component.translatable("jei.warlockery.effect." + effect.recipePath())));
        return new WarlockeryEmiRecipe(category, synthetic("machine/" + recipe.machine(), match.id()), inputs, catalysts, outputs,
            text, match, List.of(), !outputs.isEmpty());
    }

    static WarlockeryEmiRecipe ritual(EmiRecipeCategory category, RitualManager.Entry entry) {
        var definition = entry.definition();
        var inputs = new ArrayList<EmiIngredient>();
        var catalysts = new ArrayList<EmiIngredient>();
        var text = new ArrayList<Component>();
        text.add(Component.translatable(definition.title()));
        text.add(Component.translatable(definition.description()));
        for (var ingredient : definition.requirements().ingredients()) {
            var value = item(ingredient.ingredient(), ingredient.count());
            if (ingredient.consume()) inputs.add(value); else catalysts.add(value);
            if (!ingredient.consume()) text.add(Component.translatable("jei.warlockery.ritual.not_consumed").copy().append(": ")
                .append(RecipeViewerIngredients.itemStacks(ingredient.ingredient(), ingredient.count()).stream().findFirst()
                    .map(ItemStack::getHoverName).orElse(Component.literal(ingredient.ingredient()))));
        }
        catalysts.add(item("warlockery:chalkheart", 1));
        catalysts.add(item("warlockery:arcane_focus", 1));
        if (definition.power() > 0) catalysts.add(item("warlockery:altar", 1));
        var rings = ChalkCircleLayout.rings(definition.glyphs());
        for (var ring : rings) {
            String chalk = chalk(ring.glyph());
            catalysts.add(item("warlockery:" + chalk, 1));
            text.add(Component.translatable("item.warlockery." + chalk).copy().append(": ")
                .append(Component.translatable("jei.warlockery.ritual.chalk_marks", ring.requiredCount())));
        }
        text.add(Component.translatable("jei.warlockery.ritual.power", definition.power()));
        text.add(Component.translatable("jei.warlockery.ritual.casting_time", Math.max(1, definition.castingTime() / 20)));
        text.addAll(conditions(definition));
        if (definition.action().equals("glyph_transform")) text.add(Component.translatable("jei.warlockery.ritual.glyph_transform_sizes"));
        ManualRitualInstructions.keys(entry.id().getPath(), definition.action(), definition.target()).forEach(key -> text.add(
            Component.translatable("manual.warlockery.ritual.setup." + key, definition.radius(), Math.clamp(definition.radius() * 2, 8, 24))));
        var outputs = ritualOutput(definition).stream().map(EmiStack::of).toList();
        return new WarlockeryEmiRecipe(category, synthetic("ritual", entry.id()), inputs, catalysts, outputs, text, null, rings, false);
    }

    static WarlockeryEmiRecipe component(EmiRecipeCategory category, CustomBrewJeiRecipe component) {
        return new WarlockeryEmiRecipe(category, synthetic("custom_brew", component.id()),
            List.of(item(component.definition().ingredient(), 1)), List.of(item("warlockery:cauldron", 1)), List.of(),
            List.of(component.role(), component.details(), Component.translatable("jei.warlockery.custom.setup"),
                Component.translatable("jei.warlockery.custom.order")), null, List.of(), false);
    }

    static WarlockeryEmiRecipe anoint(EmiRecipeCategory category) {
        return new WarlockeryEmiRecipe(category, Identifier.fromNamespaceAndPath("warlockery", "/world/anoint_cauldron"),
            List.of(item("warlockery:ingredient_annointing_paste", 1), item("minecraft:cauldron", 1)), List.of(),
            RecipeViewerIngredients.directItem("warlockery:cauldron", 1).stream().map(EmiStack::of).toList(),
            List.of(Component.translatable("jei.warlockery.world.anoint_cauldron")), null, List.of(), false);
    }

    static WarlockeryEmiRecipe information(EmiRecipeCategory category, String item, List<Component> text) {
        var ingredient = item(item, 1);
        return new WarlockeryEmiRecipe(category, Identifier.fromNamespaceAndPath("warlockery", "/information/" + item.replace(':', '/')),
            List.of(ingredient), List.of(), ingredient.getEmiStacks(), text, null, List.of(), false);
    }

    static EmiIngredient item(String id, int amount) { return ingredient(RecipeViewerIngredients.itemStacks(id, amount), amount); }
    static EmiIngredient ingredient(List<ItemStack> alternatives, int amount) {
        return EmiIngredient.of(alternatives.stream().map(EmiStack::of).toList(), amount);
    }
    static long fluidAmount(int milliBuckets) {
        return EmiFluidUnits.amount(milliBuckets);
    }
    static Identifier synthetic(String kind, Identifier id) {
        return Identifier.fromNamespaceAndPath("warlockery", "/" + kind + "/" + id.getNamespace() + "/" + id.getPath());
    }

    MachineRecipeManager.Match machine() { return machine; }
    @Override public EmiRecipeCategory getCategory() { return category; }
    @Override public Identifier getId() { return id; }
    @Override public List<EmiIngredient> getInputs() { return inputs; }
    @Override public List<EmiIngredient> getCatalysts() { return catalysts; }
    @Override public List<EmiStack> getOutputs() { return outputs; }
    @Override public boolean supportsRecipeTree() { return recipeTree; }
    @Override public int getDisplayWidth() { return WIDTH; }
    @Override public int getDisplayHeight() { return Math.max(176, bodyTop() + 132); }

    private int columns() { return rings.isEmpty() ? 11 : 7; }
    private int inputHeight() { return inputs.isEmpty() ? 0 : ((inputs.size() + columns() - 1) / columns()) * 20; }
    private int outputHeight() { return outputs.isEmpty() ? 0 : 28; }
    private int bodyTop() {
        int slots = inputHeight() + outputHeight() + ((catalysts.size() + columns() - 1) / columns()) * 20 + 8;
        return rings.isEmpty() ? slots : Math.max(96, slots);
    }

    @Override public void addWidgets(WidgetHolder widgets) {
        for (int index = 0; index < inputs.size(); index++) {
            widgets.addSlot(inputs.get(index), 4 + index % columns() * 20, 2 + index / columns() * 20);
        }
        for (int index = 0; index < outputs.size(); index++) {
            widgets.addSlot(outputs.get(index), 4 + index * 28, inputHeight() + 2).large(true).recipeContext(this);
        }
        for (int index = 0; index < catalysts.size(); index++) {
            widgets.addSlot(catalysts.get(index), 4 + index % columns() * 20,
                inputHeight() + outputHeight() + 2 + index / columns() * 20).catalyst(true);
        }
        if (!rings.isEmpty()) addDiagram(widgets);
        widgets.add(new EmiPagedTextWidget(paragraphs, 2, bodyTop(), WIDTH - 4, Math.max(29, widgets.getHeight() - bodyTop())));
    }

    private void addDiagram(WidgetHolder widgets) {
        widgets.addDrawable(154, 2, 84, 84, (graphics, mouseX, mouseY, delta) -> {
            int radius = rings.stream().mapToInt(ring -> ring.size().radius()).max().orElse(0);
            int cell = Math.max(1, 82 / (radius * 2 + 1));
            int center = 42;
            graphics.fill(0, 0, 84, 84, 0xFF302737);
            for (var ring : rings) {
                int color = switch (ring.glyph()) {
                    case "circleglyphinfernal" -> 0xFFFF6B6B;
                    case "circleglyph_veil" -> 0xFF94BDFF;
                    case "circleglyphgolden" -> 0xFFFFD966;
                    default -> 0xFFF8F3EF;
                };
                for (var offset : ring.size().offsets()) {
                    int x = center + offset.getX() * cell - cell / 2;
                    int y = center + offset.getZ() * cell - cell / 2;
                    graphics.fill(x, y, x + Math.max(1, cell - 1), y + Math.max(1, cell - 1), color);
                }
            }
            graphics.fill(center - cell / 2, center - cell / 2, center + (cell + 1) / 2, center + (cell + 1) / 2, 0xFFFFD966);
        });
    }

    private static String chalk(String glyph) {
        return switch (glyph) {
            case "circleglyphgolden" -> "chalkheart";
            case "circleglyphinfernal" -> "chalkinfernal";
            case "circleglyph_veil" -> "chalk_veil";
            default -> "chalkritual";
        };
    }

    private static Optional<ItemStack> ritualOutput(RitualDefinition definition) {
        return switch (definition.action()) {
            case "summon_item", "bind_item", "bind_circle" -> RecipeViewerIngredients.directItem(definition.target(), definition.count());
            case "bind_fetish" -> RecipeViewerIngredients.directItem(definition.target(), 1);
            case "bind_waystone", "copy_waystone" -> RecipeViewerIngredients.directItem("warlockery:ingredient_waystone_bound", 1);
            default -> Optional.empty();
        };
    }

    private static List<Component> conditions(RitualDefinition definition) {
        var requirements = definition.requirements();
        var result = new ArrayList<Component>();
        if (definition.nightOnly()) result.add(condition("night"));
        if (requirements.dayOnly()) result.add(condition("day"));
        if (requirements.fullMoon()) result.add(condition("full_moon"));
        if (requirements.raining()) result.add(condition("rain"));
        if (requirements.thundering()) result.add(condition("thunder"));
        if (!requirements.dimension().isBlank()) result.add(condition(requirements.dimension().replace(':', '.')));
        if (requirements.minimumPlayers() > 1) result.add(Component.translatable("screen.warlockery.ritual.requirement_count",
            condition("coven"), requirements.minimumPlayers(), requirements.minimumPlayers()));
        requirements.entities().forEach(entity -> {
            var id = Identifier.tryParse(entity.entity());
            var name = id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                ? Component.translatable(BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescriptionId()) : Component.literal(entity.entity());
            result.add(Component.translatable("screen.warlockery.ritual.requirement_count", name, entity.count(), entity.count()));
        });
        return List.copyOf(result);
    }

    private static Component condition(String condition) { return Component.translatable("screen.warlockery.ritual.requirement." + condition); }
}
