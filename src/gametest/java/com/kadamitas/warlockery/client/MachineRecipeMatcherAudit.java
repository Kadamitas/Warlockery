package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.crafting.*;
import com.kadamitas.warlockery.util.FluidContents;
import com.kadamitas.warlockery.util.FluidIngredient;
import com.kadamitas.warlockery.util.ItemIngredient;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

final class MachineRecipeMatcherAudit {
    static List<Map<String, Object>> run() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var recipe : MachineRecipeManager.INSTANCE.all()) {
            var profile = MachineProfiles.forRecipeType(recipe.recipe().machine()).orElseThrow();
            List<List<ItemStack>> variants = recipe.recipe().inputs().stream().map(input -> {
                var ingredient = ItemIngredient.parse(input.ingredient()).orElseThrow();
                return BuiltInRegistries.ITEM.stream().map(item -> new ItemStack(item, input.count()))
                    .filter(ingredient::matches).toList();
            }).toList();
            List<FluidContents> fluids = recipe.recipe().fluid().map(required -> {
                var ingredient = FluidIngredient.parse(required.ingredient()).orElseThrow();
                return BuiltInRegistries.FLUID.stream().filter(fluid -> fluid != Fluids.EMPTY)
                    .map(fluid -> new FluidContents(FluidVariant.of(fluid), required.amount()))
                    .filter(ingredient::matches).toList();
            }).orElseGet(() -> List.of(FluidContents.EMPTY));
            Map<String, Long> selections = new LinkedHashMap<>();
            Map<String, Object> witnesses = new LinkedHashMap<>();
            List<Set<String>> winningInputs = variants.stream().map(ignored -> (Set<String>) new TreeSet<String>()).toList();
            long combinations = fluids.size();
            for (var variant : variants) combinations = Math.multiplyExact(combinations, variant.size());
            System.out.println("WARLOCKERY_MATCHER_CARDINALITY " + recipe.id() + " " + combinations);
            if (combinations > 100_000) throw new AssertionError("Declared-input product requires set-intersection audit: " + recipe.id() + " " + combinations);
            var inventory = NonNullList.withSize(profile.outputEnd(), ItemStack.EMPTY);
            for (var fluid : fluids) enumerate(recipe, profile, variants, fluid, inventory, 0, selections, witnesses, winningInputs);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", recipe.id().toString());
            row.put("machine", recipe.recipe().machine());
            row.put("resolvedCartesianFixtures", combinations);
            row.put("selectedRecipeCounts", selections);
            row.put("mismatchWitnesses", witnesses);
            row.put("winningInputAlternatives", winningInputs);
            row.put("reachableOnDeclaredInputs", selections.containsKey(recipe.id().toString()));
            row.put("semantics", "Every combination of live item/tag alternatives and fluid/tag alternatives, exact declared counts, semantic slot plan, unlimited altar power; actual MachineRecipeManager.find. Extra-input supersets and overfilled-stack counts are outside this declared-recipe census.");
            result.add(row);
        }
        return result;
    }
    private static void enumerate(MachineRecipeManager.Match recipe, MachineProfile profile,
        List<List<ItemStack>> variants, FluidContents fluid, NonNullList<ItemStack> inventory, int index,
        Map<String, Long> selections, Map<String, Object> witnesses, List<Set<String>> winningInputs) {
        if (index < variants.size()) {
            int slot = MachineRecipeSlotPlan.inputSlots(profile, recipe.recipe()).get(index);
            for (var stack : variants.get(index)) {
                inventory.set(slot, stack.copy());
                enumerate(recipe, profile, variants, fluid, inventory, index + 1, selections, witnesses, winningInputs);
            }
            inventory.set(slot, ItemStack.EMPTY);
            return;
        }
        String selected = MachineRecipeManager.INSTANCE.find(profile, inventory, fluid, Integer.MAX_VALUE)
            .map(match -> match.id().toString()).orElse("NONE");
        selections.merge(selected, 1L, Long::sum);
        if (selected.equals(recipe.id().toString())) {
            var slots = MachineRecipeSlotPlan.inputSlots(profile, recipe.recipe());
            for (int input = 0; input < slots.size(); input++) {
                var stack = inventory.get(slots.get(input));
                winningInputs.get(input).add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount());
            }
        }
        if (!selected.equals(recipe.id().toString())) witnesses.computeIfAbsent(selected, ignored -> Map.of(
            "items", inventory.stream().filter(stack -> !stack.isEmpty()).map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount()).toList(),
            "fluid", fluid.toString()));
    }
}
