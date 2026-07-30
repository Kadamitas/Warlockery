package com.kadamitas.warlockery.crafting;

import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

public final class MachineInsertionRules {
    private MachineInsertionRules() {
    }

    public static boolean accepts(
        final MachineProfile profile,
        final int slot,
        final ItemStack stack,
        final boolean authoritative,
        final Predicate<ItemStack> inputPredicate,
        final Predicate<ItemStack> fuelPredicate
    ) {
        if (slot < 0 || stack.isEmpty()) {
            return false;
        }
        if (profile.hasFuelSlot() && slot == profile.fuelSlot()) {
            return !authoritative || fuelPredicate.test(stack);
        }
        return slot < profile.inputSlots() && (!authoritative || inputPredicate.test(stack));
    }
}
