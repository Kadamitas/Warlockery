package com.kadamitas.warlockery.compat.rei;

import com.kadamitas.warlockery.client.ManualRitualInstructions;
import com.kadamitas.warlockery.compat.viewer.RecipeViewerIngredients;
import com.kadamitas.warlockery.crafting.BrazierEffectRuntime;
import com.kadamitas.warlockery.crafting.MachineProfile;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import com.kadamitas.warlockery.crafting.MachineRecipeSlotPlan;
import com.kadamitas.warlockery.crafting.PowerMode;
import com.kadamitas.warlockery.menu.MachineUiLayout;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import com.kadamitas.warlockery.ritual.RitualDefinition;
import java.util.ArrayList;
import java.util.List;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class WarlockeryReiCategory implements DisplayCategory<WarlockeryReiDisplay> {
    private static final int WIDTH = 240;
    private static final int DIAGRAM_Y_OFFSET = 24;
    private final CategoryIdentifier<WarlockeryReiDisplay> identifier;
    private final WarlockeryReiDisplay.Kind kind;
    private final String machine;
    private final Component title;
    private final Renderer icon;
    private final int height;

    private WarlockeryReiCategory(
        final CategoryIdentifier<WarlockeryReiDisplay> identifier,
        final WarlockeryReiDisplay.Kind kind,
        final String machine,
        final Component title,
        final ItemLike icon,
        final int height
    ) {
        this.identifier = identifier;
        this.kind = kind;
        this.machine = machine;
        this.title = title;
        this.icon = EntryStacks.of(icon);
        this.height = height;
    }

    static WarlockeryReiCategory machine(final String machine) {
        final MachineProfile profile = MachineProfiles.forRecipeType(machine).orElseThrow();
        final MachineUiLayout layout = MachineUiLayout.forKind(machine);
        final int extra = machine.equals("brazier") ? 132 : 68;
        final ItemLike block = ModBlocks.ALL.get(profile.displayBlock()).get();
        return new WarlockeryReiCategory(
            WarlockeryReiPlugin.machineCategory(machine),
            WarlockeryReiDisplay.Kind.MACHINE,
            machine,
            Component.translatable(block.asItem().getDescriptionId()),
            block,
            layout.statusY() - DIAGRAM_Y_OFFSET + extra
        );
    }

    static WarlockeryReiCategory rituals() {
        return new WarlockeryReiCategory(
            WarlockeryReiPlugin.RITUALS,
            WarlockeryReiDisplay.Kind.RITUAL,
            "",
            Component.translatable("screen.warlockery.ritual.title"),
            ModItems.ALL.get("arcane_focus").get(),
            238
        );
    }

    static WarlockeryReiCategory customBrews() {
        return new WarlockeryReiCategory(
            WarlockeryReiPlugin.CUSTOM_BREWS,
            WarlockeryReiDisplay.Kind.CUSTOM_BREW,
            "",
            Component.translatable("jei.warlockery.custom.title"),
            ModBlocks.ALL.get("cauldron").get(),
            176
        );
    }

    static WarlockeryReiCategory worldInteractions() {
        return new WarlockeryReiCategory(
            WarlockeryReiPlugin.WORLD_INTERACTIONS,
            WarlockeryReiDisplay.Kind.WORLD_INTERACTION,
            "",
            Component.translatable("jei.warlockery.world.title"),
            ModBlocks.ALL.get("cauldron").get(),
            106
        );
    }

    static WarlockeryReiCategory information() {
        return new WarlockeryReiCategory(
            WarlockeryReiPlugin.INFORMATION,
            WarlockeryReiDisplay.Kind.INFORMATION,
            "",
            Component.translatable("viewer.warlockery.information"),
            ModItems.ALL.get("arcane_focus").get(),
            220
        );
    }

    @Override
    public CategoryIdentifier<? extends WarlockeryReiDisplay> getCategoryIdentifier() {
        return identifier;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public Renderer getIcon() {
        return icon;
    }

    @Override
    public int getDisplayWidth(final WarlockeryReiDisplay display) {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return height;
    }

    @Override
    public List<Widget> setupDisplay(final WarlockeryReiDisplay display, final Rectangle bounds) {
        if (display.kind() != kind || kind == WarlockeryReiDisplay.Kind.MACHINE && !machine.equals(display.machine())) {
            return List.of(Widgets.createRecipeBase(bounds));
        }
        final List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        switch (kind) {
            case MACHINE -> setupMachine(widgets, display, bounds);
            case RITUAL -> setupRitual(widgets, display, bounds);
            case CUSTOM_BREW -> setupCustomBrew(widgets, display, bounds);
            case WORLD_INTERACTION -> setupWorldInteraction(widgets, display, bounds);
            case INFORMATION -> setupInformation(widgets, display, bounds);
        }
        return widgets;
    }

    private void setupMachine(
        final List<Widget> widgets,
        final WarlockeryReiDisplay display,
        final Rectangle bounds
    ) {
        final var match = display.machineRecipe();
        final MachineRecipeDefinition recipe = match.recipe();
        final MachineProfile profile = MachineProfiles.forRecipeType(machine).orElseThrow();
        final MachineUiLayout layout = MachineUiLayout.forKind(machine);
        final List<Integer> inputSlots = MachineRecipeSlotPlan.inputSlots(profile, recipe);
        for (int index = 0; index < recipe.inputs().size(); index++) {
            final MachineUiLayout.SlotPosition position = layout.slots().get(inputSlots.get(index));
            widgets.add(Widgets.createSlot(point(bounds, position.x(), position.y() - DIAGRAM_Y_OFFSET))
                .entries(display.getInputEntries().get(index)).markInput());
        }
        recipe.fluid().ifPresent(fluid -> {
            final int[] position = fluidPosition(machine);
            widgets.add(Widgets.createSlot(point(bounds, position[0], position[1] - DIAGRAM_Y_OFFSET))
                .entries(display.getInputEntries().get(recipe.inputs().size())).markInput());
        });
        for (int index = 0; index < display.getOutputEntries().size(); index++) {
            final MachineUiLayout.SlotPosition position = layout.slots().get(profile.outputStart() + index);
            final Point point = point(bounds, position.x(), position.y() - DIAGRAM_Y_OFFSET);
            widgets.add(Widgets.createResultSlotBackground(point));
            widgets.add(Widgets.createSlot(point).entries(display.getOutputEntries().get(index))
                .disableBackground().markOutput());
        }
        final int[] arrow = arrowPosition(machine);
        widgets.add(Widgets.createArrow(point(bounds, arrow[0], arrow[1] - DIAGRAM_Y_OFFSET))
            .animationDurationTicks(recipe.processingTime()));
        if (recipe.requiresFuel()) {
            widgets.add(Widgets.createBurningFire(point(bounds, arrow[0] - 2, arrow[1] + 18 - DIAGRAM_Y_OFFSET)));
        }
        Component details = Component.translatable(
            "jei.warlockery.machine.processing_time", Math.max(1, recipe.processingTime() / 20)
        );
        if (recipe.altarPower() > 0) {
            details = details.copy().append("  ").append(Component.translatable(
                "jei.warlockery.machine.altar_power", recipe.altarPower()
            ));
        }
        if (recipe.requiresFuel()) {
            details = details.copy().append("  ").append(Component.translatable("jei.warlockery.machine.fuel"));
        }
        if (profile.requiresExternalHeat()) {
            details = details.copy().append("  ").append(Component.translatable("jei.warlockery.machine.heat"));
        }
        if (recipe.fluid().isPresent()) {
            details = details.copy().append("  ").append(Component.literal(
                "Add the shown fluid manually; REI item transfer does not fill machine tanks."
            ));
        }
        text(widgets, bounds, details, 2, layout.statusY() - DIAGRAM_Y_OFFSET + 2, WIDTH - 4, 0xFF404040);
        if (recipe.powerMode() == PowerMode.CONTINUOUS) {
            text(widgets, bounds, Component.translatable("manual.warlockery.machine_recipe.continuous_power"),
                2, layout.statusY() - DIAGRAM_Y_OFFSET + 28, WIDTH - 4, 0xFF404040);
        }
        if (machine.equals("brazier")) {
            text(widgets, bounds, Component.translatable("manual.warlockery.machine_recipe.ignite"),
                2, layout.statusY() - DIAGRAM_Y_OFFSET + 68, WIDTH - 4, 0xFF404040);
            BrazierEffectRuntime.Effect.fromRecipe(match.id()).ifPresent(effect -> text(
                widgets,
                bounds,
                Component.translatable("jei.warlockery.effect." + effect.recipePath()),
                2,
                layout.statusY() - DIAGRAM_Y_OFFSET + 108,
                WIDTH - 4,
                0xFF404040
            ));
        }
    }

    private static void setupRitual(
        final List<Widget> widgets,
        final WarlockeryReiDisplay display,
        final Rectangle bounds
    ) {
        final var entry = display.ritual();
        final RitualDefinition definition = entry.definition();
        text(widgets, bounds, Component.translatable(definition.title()), 2, 0, WIDTH - 4, 0xFF342040);
        text(widgets, bounds, Component.translatable(definition.description()), 2, 26, WIDTH - 4, 0xFF505050);
        for (int index = 0; index < definition.requirements().ingredients().size(); index++) {
            final RitualDefinition.Ingredient ingredient = definition.requirements().ingredients().get(index);
            EntryIngredient entries = display.getInputEntries().get(index);
            if (!ingredient.consume()) {
                entries = entries.map(stack -> stack.tooltip(Component.translatable("jei.warlockery.ritual.not_consumed")));
            }
            if (definition.action().equals("glyph_transform")) {
                entries = entries.map(stack -> stack.tooltip(Component.translatable(
                    "jei.warlockery.ritual.glyph_transform_sizes"
                )));
            }
            widgets.add(Widgets.createSlot(point(bounds, 4 + index * 20, 80)).entries(entries).markInput());
        }
        widgets.add(Widgets.createSlot(point(bounds, 4, 104))
            .entry(EntryStacks.of(ModItems.ALL.get("chalkheart").get())));
        int glyphIndex = 1;
        for (final ChalkCircleLayout.Ring ring : ChalkCircleLayout.rings(definition.glyphs())) {
            final String chalk = switch (ring.glyph()) {
                case "circleglyphgolden" -> "chalkheart";
                case "circleglyphritual" -> "chalkritual";
                case "circleglyphinfernal" -> "chalkinfernal";
                case "circleglyph_veil" -> "chalk_veil";
                default -> "";
            };
            final var tool = ModItems.ALL.get(chalk);
            final var block = ModBlocks.ALL.get(ring.glyph());
            if (tool != null || block != null) {
                final ItemStack stack = tool != null
                    ? new ItemStack(tool.get())
                    : new ItemStack(block.get(), ring.requiredCount());
                widgets.add(Widgets.createSlot(point(bounds, 4 + glyphIndex * 20, 104)).entry(
                    EntryStacks.of(stack).tooltip(Component.translatable(
                        "jei.warlockery.ritual.chalk_marks", ring.requiredCount()
                    ))
                ));
                glyphIndex++;
            }
        }
        if (!display.getOutputEntries().isEmpty()) {
            final Point output = point(bounds, 218, 86);
            widgets.add(Widgets.createResultSlotBackground(output));
            widgets.add(Widgets.createSlot(output).entries(display.getOutputEntries().getFirst())
                .disableBackground().markOutput());
        }
        final Component timing = Component.translatable("jei.warlockery.ritual.power", definition.power())
            .append("  ")
            .append(Component.translatable(
                "jei.warlockery.ritual.casting_time", Math.max(1, definition.castingTime() / 20)
            ));
        text(widgets, bounds, timing, 2, 128, WIDTH - 4, 0xFF404040);
        final Component conditions = conditions(definition);
        if (!conditions.getString().isBlank()) {
            text(widgets, bounds, Component.translatable("jei.warlockery.ritual.conditions", conditions),
                2, 154, WIDTH - 4, 0xFF505050);
        }
        final Component[] instructions = ManualRitualInstructions.keys(
            entry.id().toString(), definition.action(), definition.target()
        ).stream().map(key -> Component.translatable(
                "manual.warlockery.ritual.setup." + key,
                definition.radius(),
                Math.clamp(definition.radius() * 2, 8, 24)
            )).toArray(Component[]::new);
        widgets.add(Widgets.createLabel(point(bounds, 2, 214), Component.translatable("viewer.warlockery.information"))
            .leftAligned().noShadow().color(0xFF505050).tooltip(instructions));
    }

    private static void setupCustomBrew(
        final List<Widget> widgets,
        final WarlockeryReiDisplay display,
        final Rectangle bounds
    ) {
        final var recipe = display.customBrew();
        widgets.add(Widgets.createSlot(point(bounds, 4, 6)).entries(display.getInputEntries().getFirst()).markInput());
        widgets.add(Widgets.createSlot(point(bounds, 218, 6)).entry(EntryStacks.of(ModBlocks.ALL.get("cauldron").get())));
        final Component name = RecipeViewerIngredients.itemStacks(recipe.definition().ingredient(), 1).stream()
            .findFirst()
            .map(stack -> stack.getHoverName().copy().append(": ").append(recipe.role()))
            .map(Component.class::cast)
            .orElseGet(recipe::role);
        text(widgets, bounds, name, 28, 7, 180, 0xFF342040);
        text(widgets, bounds, recipe.details(), 4, 34, 232, 0xFF404040);
        text(widgets, bounds, Component.translatable("jei.warlockery.custom.setup"), 4, 80, 232, 0xFF404040);
        text(widgets, bounds, Component.translatable("jei.warlockery.custom.order"), 4, 126, 232, 0xFF404040);
    }

    private static void setupWorldInteraction(
        final List<Widget> widgets,
        final WarlockeryReiDisplay display,
        final Rectangle bounds
    ) {
        widgets.add(Widgets.createSlot(point(bounds, 12, 8)).entries(display.getInputEntries().get(0)).markInput());
        widgets.add(Widgets.createSlot(point(bounds, 46, 8)).entries(display.getInputEntries().get(1)).markInput());
        final Point output = point(bounds, 178, 8);
        widgets.add(Widgets.createResultSlotBackground(output));
        widgets.add(Widgets.createSlot(output).entries(display.getOutputEntries().getFirst())
            .disableBackground().markOutput());
        widgets.add(Widgets.createArrow(point(bounds, 112, 8)));
        text(widgets, bounds, Component.translatable("jei.warlockery.world.anoint_cauldron"),
            4, 34, 212, 0xFF404040);
    }

    private static void setupInformation(
        final List<Widget> widgets,
        final WarlockeryReiDisplay display,
        final Rectangle bounds
    ) {
        widgets.add(Widgets.createSlot(point(bounds, 4, 6)).entries(display.getInputEntries().getFirst()));
        int y = 6;
        for (final Component line : display.information()) {
            text(widgets, bounds, line, 28, y, 208, 0xFF404040);
            y += 36;
        }
    }

    private static Component conditions(final RitualDefinition definition) {
        final RitualDefinition.Requirements requirements = definition.requirements();
        final List<Component> conditions = new ArrayList<>();
        if (definition.nightOnly()) conditions.add(Component.translatable("screen.warlockery.ritual.requirement.night"));
        if (requirements.dayOnly()) conditions.add(Component.translatable("screen.warlockery.ritual.requirement.day"));
        if (requirements.fullMoon()) conditions.add(Component.translatable("screen.warlockery.ritual.requirement.full_moon"));
        if (requirements.raining()) conditions.add(Component.translatable("screen.warlockery.ritual.requirement.rain"));
        if (requirements.thundering()) conditions.add(Component.translatable("screen.warlockery.ritual.requirement.thunder"));
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
            if (index > 0) joined = joined.copy().append(", ");
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
            "screen.warlockery.ritual.requirement_count", label, requirement.count(), requirement.count()
        );
    }

    private static void text(
        final List<Widget> widgets,
        final Rectangle bounds,
        final Component text,
        final int x,
        final int y,
        final int width,
        final int color
    ) {
        widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> graphics.drawWordWrap(
            Minecraft.getInstance().font, text, bounds.x + x, bounds.y + y, width, color
        )));
    }

    private static Point point(final Rectangle bounds, final int x, final int y) {
        return new Point(bounds.x + x, bounds.y + y);
    }

    static int[] fluidPosition(final String machine) {
        return switch (machine) {
            case "distillery" -> new int[] {54, 84};
            case "kettle" -> new int[] {94, 54};
            case "cauldron" -> new int[] {70, 56};
            default -> new int[] {78, 50};
        };
    }

    static int[] arrowPosition(final String machine) {
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
