package com.kadamitas.warlockery.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class IngredientAllocatorTest {
    @Test
    void reservesAcrossStacksWithoutDoubleCounting() {
        final var allocation = IngredientAllocator.allocate(
            List.of(new Requirement("minecraft:stone", 3), new Requirement("minecraft:stone", 2)),
            List.of(new Stack("minecraft:stone", 4), new Stack("minecraft:stone", 1)),
            Stack::count,
            (stack, ingredient) -> stack.item().equals(ingredient)
        );
        assertTrue(allocation.complete());
        assertEquals(List.of(4, 1), allocation.reservedBySlot());
        assertEquals(List.of(0, 0), allocation.unreservedBySlot());
    }

    @Test
    void reportsMissingAndUnreservedCountsFromOnePlan() {
        final var allocation = IngredientAllocator.allocate(
            List.of(new Requirement("minecraft:stone", 3)),
            List.of(new Stack("minecraft:stone", 2), new Stack("minecraft:dirt", 4)),
            Stack::count,
            (stack, ingredient) -> stack.item().equals(ingredient)
        );
        assertFalse(allocation.complete());
        assertEquals(1, allocation.requirements().getFirst().missing());
        assertEquals(List.of(0, 4), allocation.unreservedBySlot());
    }

    @Test
    void consumptionUsesTheDiagnosticAllocation() {
        final List<Stack> stacks = List.of(new Stack("minecraft:stone", 4), new Stack("minecraft:stone", 2));
        final var allocation = IngredientAllocator.allocate(
            List.of(new Requirement("minecraft:stone", 5)),
            stacks,
            Stack::count,
            (stack, ingredient) -> stack.item().equals(ingredient)
        );
        final int[] remaining = {4, 2};
        allocation.applyTo(List.of(0, 1), (slot, consumed) -> remaining[slot] -= consumed);
        assertEquals(0, remaining[0]);
        assertEquals(1, remaining[1]);
    }

    @Test
    void overlappingTagsDoNotStealItemsFromExactRequirements() {
        final var allocation = IngredientAllocator.allocate(
            List.of(new Requirement("#minecraft:logs", 1), new Requirement("minecraft:oak_log", 1)),
            List.of(new Stack("minecraft:oak_log", 1), new Stack("minecraft:birch_log", 1)),
            Stack::count,
            (stack, ingredient) -> ingredient.equals(stack.item())
                || ingredient.equals("#minecraft:logs") && stack.item().endsWith("_log")
        );
        assertTrue(allocation.complete());
        assertEquals(List.of(1, 1), allocation.requirements().stream()
            .map(IngredientAllocator.RequirementMatch::matched)
            .toList());
        assertEquals(List.of(1, 1), allocation.reservedBySlot());
    }

    @Test
    void incompletePlansCannotConsumeInputs() {
        final var allocation = IngredientAllocator.allocate(
            List.of(new Requirement("minecraft:stone", 3)),
            List.of(new Stack("minecraft:stone", 2)),
            Stack::count,
            (stack, ingredient) -> stack.item().equals(ingredient)
        );
        assertThrows(IllegalStateException.class, () -> allocation.applyTo(List.of(0), (_, _) -> { }));
    }

    @Test
    void reusablePlansBuildMatchersOnceAndKeepTheirInputsImmutable() {
        final AtomicInteger matcherFactories = new AtomicInteger();
        final IngredientAllocationPlan<Stack> plan = IngredientAllocationPlan.create(
            List.of(new Requirement("minecraft:stone", 2)),
            requirement -> {
                matcherFactories.incrementAndGet();
                return stack -> stack.item().equals(requirement.ingredient());
            }
        );

        assertTrue(plan.allocate(List.of(new Stack("minecraft:stone", 2)), Stack::count).complete());
        assertFalse(plan.allocate(List.of(new Stack("minecraft:stone", 1)), Stack::count).complete());
        assertEquals(1, matcherFactories.get());
        assertThrows(UnsupportedOperationException.class, () -> plan.requirements().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.matchers().clear());
    }

    private record Requirement(String ingredient, int count) implements CountedIngredient {
    }

    private record Stack(String item, int count) {
    }
}
