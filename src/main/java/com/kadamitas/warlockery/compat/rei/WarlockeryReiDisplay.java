package com.kadamitas.warlockery.compat.rei;

import com.kadamitas.warlockery.compat.jei.CustomBrewJeiRecipe;
import com.kadamitas.warlockery.compat.viewer.RecipeViewerIngredients;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.ritual.RitualDefinition;
import com.kadamitas.warlockery.ritual.RitualManager;
import dev.architectury.fluid.FluidStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Client-local display data. Warlockery recipes remain owned by the shared viewer snapshot. */
public final class WarlockeryReiDisplay extends BasicDisplay {
    public enum Kind {
        MACHINE,
        RITUAL,
        CUSTOM_BREW,
        WORLD_INTERACTION,
        INFORMATION
    }

    private final Kind kind;
    private final Object value;
    private final String machine;
    private final List<Component> information;

    private WarlockeryReiDisplay(
        final Kind kind,
        final Object value,
        final String machine,
        final List<EntryIngredient> inputs,
        final List<EntryIngredient> outputs,
        final Identifier location,
        final List<Component> information
    ) {
        super(List.copyOf(inputs), List.copyOf(outputs), Optional.of(location));
        this.kind = kind;
        this.value = value;
        this.machine = machine;
        this.information = List.copyOf(information);
    }

    public static WarlockeryReiDisplay machine(final MachineRecipeManager.Match match) {
        final List<EntryIngredient> outputs = match.recipe().outputs().stream()
            .map(output -> RecipeViewerIngredients.directItem(output.item(), output.count()))
            .flatMap(Optional::stream)
            .map(WarlockeryReiDisplay::item)
            .toList();
        return new WarlockeryReiDisplay(
            Kind.MACHINE,
            match,
            match.recipe().machine(),
            machineInputs(match),
            outputs,
            match.id(),
            List.of()
        );
    }

    public static WarlockeryReiDisplay ritual(final RitualManager.Entry entry) {
        final RitualDefinition definition = entry.definition();
        final List<EntryIngredient> inputs = definition.requirements().ingredients().stream()
            .map(ingredient -> items(RecipeViewerIngredients.itemStacks(ingredient.ingredient(), ingredient.count())))
            .toList();
        final List<EntryIngredient> outputs = ritualOutput(definition).stream()
            .map(WarlockeryReiDisplay::item)
            .toList();
        return new WarlockeryReiDisplay(
            Kind.RITUAL, entry, "", inputs, outputs, entry.id(), List.of()
        );
    }

    public static WarlockeryReiDisplay customBrew(final CustomBrewJeiRecipe recipe) {
        return new WarlockeryReiDisplay(
            Kind.CUSTOM_BREW,
            recipe,
            "",
            List.of(items(RecipeViewerIngredients.itemStacks(recipe.definition().ingredient(), 1))),
            List.of(),
            recipe.id(),
            List.of()
        );
    }

    public static WarlockeryReiDisplay worldInteraction(
        final Identifier id,
        final String ingredient,
        final String placedBlock,
        final String result
    ) {
        return new WarlockeryReiDisplay(
            Kind.WORLD_INTERACTION,
            id,
            "",
            List.of(
                items(RecipeViewerIngredients.itemStacks(ingredient, 1)),
                items(RecipeViewerIngredients.itemStacks(placedBlock, 1))
            ),
            RecipeViewerIngredients.directItem(result, 1).stream().map(WarlockeryReiDisplay::item).toList(),
            id,
            List.of()
        );
    }

    public static WarlockeryReiDisplay information(
        final Identifier id,
        final ItemStack ingredient,
        final List<Component> text
    ) {
        final EntryIngredient entry = item(ingredient);
        return new WarlockeryReiDisplay(
            Kind.INFORMATION, ingredient.copy(), "", List.of(entry), List.of(entry), id, text
        );
    }

    public Kind kind() {
        return kind;
    }

    public String machine() {
        return machine;
    }

    public MachineRecipeManager.Match machineRecipe() {
        return (MachineRecipeManager.Match) value;
    }

    public RitualManager.Entry ritual() {
        return (RitualManager.Entry) value;
    }

    public CustomBrewJeiRecipe customBrew() {
        return (CustomBrewJeiRecipe) value;
    }

    public List<Component> information() {
        return information;
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        return switch (kind) {
            case MACHINE -> machineInputs(machineRecipe());
            case RITUAL -> ritual().definition().requirements().ingredients().stream()
                .map(ingredient -> items(RecipeViewerIngredients.itemStacks(
                    ingredient.ingredient(), ingredient.count()
                )))
                .toList();
            case CUSTOM_BREW -> List.of(items(RecipeViewerIngredients.itemStacks(
                customBrew().definition().ingredient(), 1
            )));
            case WORLD_INTERACTION, INFORMATION -> super.getInputEntries();
        };
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return switch (kind) {
            case MACHINE -> WarlockeryReiPlugin.machineCategory(machine);
            case RITUAL -> WarlockeryReiPlugin.RITUALS;
            case CUSTOM_BREW -> WarlockeryReiPlugin.CUSTOM_BREWS;
            case WORLD_INTERACTION -> WarlockeryReiPlugin.WORLD_INTERACTIONS;
            case INFORMATION -> WarlockeryReiPlugin.INFORMATION;
        };
    }

    @Override
    public DisplaySerializer<? extends WarlockeryReiDisplay> getSerializer() {
        return null;
    }

    static long nativeFluidAmount(final int milliBuckets) {
        return Math.multiplyExact((long) milliBuckets, FluidStack.bucketAmount()) / 1_000L;
    }

    private static EntryIngredient items(final List<ItemStack> stacks) {
        return EntryIngredient.of(stacks.stream().map(EntryStacks::of).toList());
    }

    private static EntryIngredient item(final ItemStack stack) {
        return EntryIngredient.of(EntryStacks.of(stack));
    }

    private static List<EntryIngredient> machineInputs(final MachineRecipeManager.Match match) {
        final List<EntryIngredient> inputs = new ArrayList<>();
        for (int index = 0; index < match.recipe().inputs().size(); index++) {
            inputs.add(items(RecipeViewerIngredients.machineInputs(match, index)));
        }
        match.recipe().fluid().ifPresent(fluid -> inputs.add(EntryIngredient.of(
            RecipeViewerIngredients.fluids(fluid.ingredient()).stream()
                .map(candidate -> EntryStacks.of(candidate, nativeFluidAmount(fluid.amount())))
                .toList()
        )));
        return List.copyOf(inputs);
    }

    private static Optional<ItemStack> ritualOutput(final RitualDefinition definition) {
        return switch (definition.action()) {
            case "summon_item", "bind_item", "bind_circle" ->
                RecipeViewerIngredients.directItem(definition.target(), definition.count());
            case "bind_fetish" -> RecipeViewerIngredients.directItem(definition.target(), 1);
            case "bind_waystone", "copy_waystone" ->
                RecipeViewerIngredients.directItem("warlockery:ingredient_waystone_bound", 1);
            default -> Optional.empty();
        };
    }
}
