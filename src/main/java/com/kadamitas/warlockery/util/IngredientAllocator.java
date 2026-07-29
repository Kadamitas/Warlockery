package com.kadamitas.warlockery.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;
import net.minecraft.world.item.ItemStack;

public final class IngredientAllocator {
    private IngredientAllocator() {
    }

    public static Allocation allocate(
        final List<? extends CountedIngredient> requirements,
        final List<ItemStack> stacks
    ) {
        return allocate(
            requirements,
            stacks,
            ItemStack::getCount,
            (stack, ingredient) -> ItemIngredient.parse(ingredient)
                .filter(parsed -> parsed.matches(stack))
                .isPresent()
        );
    }

    public static <T> Allocation allocate(
        final List<? extends CountedIngredient> requirements,
        final List<T> stacks,
        final ToIntFunction<T> count,
        final BiPredicate<T, String> matcher
    ) {
        final int[] available = stacks.stream().mapToInt(stack -> Math.max(0, count.applyAsInt(stack))).toArray();
        final FlowLayout layout = new FlowLayout(requirements.size(), stacks.size());
        final int[][] capacity = new int[layout.nodeCount()][layout.nodeCount()];

        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            final CountedIngredient requirement = requirements.get(requirementIndex);
            final int requirementNode = layout.requirementNode(requirementIndex);
            capacity[layout.source()][requirementNode] = Math.max(0, requirement.count());
            for (int slot = 0; slot < stacks.size(); slot++) {
                if (available[slot] > 0 && matcher.test(stacks.get(slot), requirement.ingredient())) {
                    capacity[requirementNode][layout.stackNode(slot)] = Math.min(requirement.count(), available[slot]);
                }
            }
        }
        for (int slot = 0; slot < stacks.size(); slot++) {
            capacity[layout.stackNode(slot)][layout.sink()] = available[slot];
        }

        final int[][] residual = Arrays.stream(capacity).map(int[]::clone).toArray(int[][]::new);
        routeMaximumFlow(residual, layout.source(), layout.sink());
        final ArrayList<RequirementMatch> matches = new ArrayList<>(requirements.size());
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            final int node = layout.requirementNode(requirementIndex);
            final int matched = capacity[layout.source()][node] - residual[layout.source()][node];
            matches.add(new RequirementMatch(requirements.get(requirementIndex), matched));
        }

        final int[] reserved = new int[stacks.size()];
        final int[] unreserved = new int[stacks.size()];
        for (int slot = 0; slot < stacks.size(); slot++) {
            final int node = layout.stackNode(slot);
            reserved[slot] = capacity[node][layout.sink()] - residual[node][layout.sink()];
            unreserved[slot] = available[slot] - reserved[slot];
        }

        return new Allocation(
            matches,
            Arrays.stream(reserved).boxed().toList(),
            Arrays.stream(unreserved).boxed().toList()
        );
    }

    private static void routeMaximumFlow(final int[][] residual, final int source, final int sink) {
        final int[] parent = new int[residual.length];
        while (findAugmentingPath(residual, source, sink, parent)) {
            int amount = Integer.MAX_VALUE;
            for (int node = sink; node != source; node = parent[node]) {
                amount = Math.min(amount, residual[parent[node]][node]);
            }
            for (int node = sink; node != source; node = parent[node]) {
                residual[parent[node]][node] -= amount;
                residual[node][parent[node]] += amount;
            }
        }
    }

    private static boolean findAugmentingPath(
        final int[][] residual,
        final int source,
        final int sink,
        final int[] parent
    ) {
        Arrays.fill(parent, -1);
        parent[source] = source;
        final ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(source);
        while (!pending.isEmpty() && parent[sink] < 0) {
            final int node = pending.removeFirst();
            for (int next = 0; next < residual.length; next++) {
                if (parent[next] < 0 && residual[node][next] > 0) {
                    parent[next] = node;
                    pending.addLast(next);
                }
            }
        }
        return parent[sink] >= 0;
    }

    private record FlowLayout(int requirementCount, int stackCount) {
        private int source() {
            return 0;
        }

        private int requirementNode(final int index) {
            return 1 + index;
        }

        private int stackNode(final int index) {
            return 1 + requirementCount + index;
        }

        private int sink() {
            return requirementCount + stackCount + 1;
        }

        private int nodeCount() {
            return sink() + 1;
        }
    }

    public record RequirementMatch(CountedIngredient requirement, int matched) {
        public boolean complete() {
            return matched >= requirement.count();
        }

        public int missing() {
            return Math.max(0, requirement.count() - matched);
        }
    }

    public record Allocation(
        List<RequirementMatch> requirements,
        List<Integer> reservedBySlot,
        List<Integer> unreservedBySlot
    ) {
        public Allocation {
            requirements = List.copyOf(requirements);
            reservedBySlot = List.copyOf(reservedBySlot);
            unreservedBySlot = List.copyOf(unreservedBySlot);
        }

        public boolean complete() {
            return requirements.stream().allMatch(RequirementMatch::complete);
        }

        public int matchedCount() {
            return requirements.stream().mapToInt(RequirementMatch::matched).sum();
        }

        public void consumeFrom(final List<ItemStack> stacks) {
            applyTo(stacks, ItemStack::shrink);
        }

        public <T> void applyTo(final List<T> stacks, final BiConsumer<T, Integer> consumer) {
            if (!complete()) {
                throw new IllegalStateException("Cannot consume an incomplete ingredient allocation");
            }
            if (stacks.size() != reservedBySlot.size()) {
                throw new IllegalArgumentException("Allocation and stack counts must match");
            }
            for (int slot = 0; slot < stacks.size(); slot++) {
                consumer.accept(stacks.get(slot), reservedBySlot.get(slot));
            }
        }
    }
}
