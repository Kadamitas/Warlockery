package com.kadamitas.warlockery.util;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.world.item.ItemStack;

public record IngredientAllocationPlan<T>(
    List<CountedIngredient> requirements,
    List<Predicate<T>> matchers
) {
    public IngredientAllocationPlan {
        requirements = List.copyOf(requirements);
        matchers = List.copyOf(matchers);
        if (requirements.size() != matchers.size()) {
            throw new IllegalArgumentException("Every ingredient requirement needs one matcher");
        }
    }

    public static IngredientAllocationPlan<ItemStack> forItems(
        final List<? extends CountedIngredient> requirements
    ) {
        return create(requirements, requirement -> ItemIngredient.parse(requirement.ingredient())
            .<Predicate<ItemStack>>map(ingredient -> ingredient::matches)
            .orElse(_ -> false));
    }

    public static <T, R extends CountedIngredient> IngredientAllocationPlan<T> create(
        final List<R> requirements,
        final Function<? super R, Predicate<T>> matcherFactory
    ) {
        final List<CountedIngredient> immutableRequirements = List.copyOf(requirements);
        final List<Predicate<T>> matchers = requirements.stream()
            .map(matcherFactory)
            .toList();
        return new IngredientAllocationPlan<>(immutableRequirements, matchers);
    }

    public IngredientAllocator.Allocation allocate(
        final List<T> stacks,
        final ToIntFunction<T> count
    ) {
        return IngredientAllocator.allocate(requirements, stacks, count, matchers);
    }
}
