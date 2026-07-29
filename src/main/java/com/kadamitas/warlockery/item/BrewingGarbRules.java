package com.kadamitas.warlockery.item;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public final class BrewingGarbRules {
    private BrewingGarbRules() {
    }

    public static UtilityDecision diagnose(final int pieces, final boolean eligibleOutput) {
        if (pieces <= 0) {
            return UtilityDecision.failure("missing_garb");
        }
        return eligibleOutput
            ? UtilityDecision.success("yield_chance")
            : UtilityDecision.failure("ineligible_output");
    }

    public static boolean duplicates(final int pieces, final int roll) {
        return pieces > 0 && roll >= 0 && roll < Math.min(80, pieces * 20);
    }

    public static List<ItemStack> duplicate(final List<ItemStack> outputs, final boolean duplicate) {
        if (!duplicate) {
            return outputs.stream().map(ItemStack::copy).toList();
        }
        return outputs.stream().map(stack -> {
            final ItemStack copy = stack.copy();
            copy.grow(Math.min(stack.getCount(), stack.getMaxStackSize() - stack.getCount()));
            return copy;
        }).toList();
    }
}
