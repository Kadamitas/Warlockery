package com.kadamitas.warlockery.crafting;

import com.kadamitas.warlockery.util.FluidIngredient;
import com.kadamitas.warlockery.util.IngredientAllocationPlan;
import com.kadamitas.warlockery.util.ItemIngredient;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

record MachineRecipeCatalog(
    Map<Identifier, MachineRecipeDefinition> definitions,
    Map<String, List<PreparedRecipe>> recipesByMachine,
    Map<String, List<ItemIngredient>> inputsByMachine,
    Map<MachineRecipeDefinition, IngredientAllocationPlan<ItemStack>> allocationPlans
) {
    static final MachineRecipeCatalog EMPTY = new MachineRecipeCatalog(Map.of(), Map.of(), Map.of(), Map.of());
    private static final Comparator<PreparedRecipe> MATCH_ORDER = Comparator
        .comparingInt(PreparedRecipe::requiredItemCount)
        .reversed()
        .thenComparing(Comparator.comparingInt(PreparedRecipe::specificity).reversed())
        .thenComparing(PreparedRecipe::id);

    MachineRecipeCatalog {
        definitions = immutableMap(definitions);
        recipesByMachine = immutableListMap(recipesByMachine);
        inputsByMachine = immutableListMap(inputsByMachine);
        allocationPlans = immutableMap(allocationPlans);
    }

    static MachineRecipeCatalog create(final Map<Identifier, MachineRecipeDefinition> definitions) {
        if (definitions.isEmpty()) {
            return EMPTY;
        }
        final Map<Identifier, MachineRecipeDefinition> orderedDefinitions = definitions.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (_, replacement) -> replacement,
                LinkedHashMap::new
            ));
        final List<PreparedRecipe> prepared = orderedDefinitions.entrySet().stream()
            .map(PreparedRecipe::create)
            .toList();
        final Map<String, List<PreparedRecipe>> byMachine = prepared.stream()
            .collect(Collectors.groupingBy(
                recipe -> recipe.definition().machine(),
                LinkedHashMap::new,
                Collectors.collectingAndThen(Collectors.toList(), recipes -> recipes.stream().sorted(MATCH_ORDER).toList())
            ));
        final Map<String, List<ItemIngredient>> inputs = prepared.stream()
            .collect(Collectors.groupingBy(
                recipe -> recipe.definition().machine(),
                LinkedHashMap::new,
                Collectors.flatMapping(
                    recipe -> recipe.ingredients().stream(),
                    Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), List::copyOf)
                )
            ));
        final Map<MachineRecipeDefinition, IngredientAllocationPlan<ItemStack>> plans = prepared.stream()
            .collect(Collectors.toMap(
                PreparedRecipe::definition,
                PreparedRecipe::allocationPlan,
                (first, _) -> first,
                LinkedHashMap::new
            ));
        return new MachineRecipeCatalog(orderedDefinitions, byMachine, inputs, plans);
    }

    List<PreparedRecipe> forMachine(final String machine) {
        return recipesByMachine.getOrDefault(machine, List.of());
    }

    List<ItemIngredient> inputsFor(final String machine) {
        return inputsByMachine.getOrDefault(machine, List.of());
    }

    Optional<MachineRecipeDefinition> definition(final Identifier id) {
        return Optional.ofNullable(definitions.get(id));
    }

    IngredientAllocationPlan<ItemStack> allocationPlan(final MachineRecipeDefinition definition) {
        final IngredientAllocationPlan<ItemStack> existing = allocationPlans.get(definition);
        return existing == null ? IngredientAllocationPlan.forItems(definition.inputs()) : existing;
    }

    private static <K, V> Map<K, V> immutableMap(final Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static <K, V> Map<K, List<V>> immutableListMap(final Map<K, List<V>> source) {
        return source.entrySet().stream().collect(Collectors.collectingAndThen(
            Collectors.toMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue()),
                (_, replacement) -> replacement,
                LinkedHashMap::new
            ),
            Collections::unmodifiableMap
        ));
    }

    record PreparedRecipe(
        Identifier id,
        MachineRecipeDefinition definition,
        List<ItemIngredient> ingredients,
        IngredientAllocationPlan<ItemStack> allocationPlan,
        Optional<FluidIngredient> fluid,
        int requiredItemCount,
        int specificity,
        String primaryOutput
    ) {
        PreparedRecipe {
            ingredients = List.copyOf(ingredients);
            fluid = fluid == null ? Optional.empty() : fluid;
        }

        private static PreparedRecipe create(
            final Map.Entry<Identifier, MachineRecipeDefinition> entry
        ) {
            final MachineRecipeDefinition definition = entry.getValue();
            final Map<String, ItemIngredient> ingredientsByValue = definition.inputs().stream()
                .map(MachineRecipeDefinition.Input::ingredient)
                .distinct()
                .collect(Collectors.toMap(
                    value -> value,
                    value -> ItemIngredient.parse(value).orElseThrow(),
                    (first, _) -> first,
                    LinkedHashMap::new
                ));
            return new PreparedRecipe(
                entry.getKey(),
                definition,
                List.copyOf(ingredientsByValue.values()),
                IngredientAllocationPlan.create(
                    definition.inputs(),
                    input -> ingredientsByValue.get(input.ingredient())::matches
                ),
                definition.fluid()
                    .map(MachineRecipeDefinition.FluidInput::ingredient)
                    .flatMap(FluidIngredient::parse),
                definition.inputs().stream().mapToInt(MachineRecipeDefinition.Input::count).sum(),
                MachineRecipeManager.specificity(definition),
                definition.outputs().getFirst().item()
            );
        }
    }
}
