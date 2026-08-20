package com.kadamitas.warlockery.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;

/** Assigns recipe ingredients to the same semantic slots used by the live machine. */
public final class MachineRecipeSlotPlan {
    private MachineRecipeSlotPlan() {
    }

    public static List<Integer> inputSlots(
        final MachineProfile profile,
        final MachineRecipeDefinition recipe
    ) {
        final PrimitiveIterator.OfInt ordinarySlots = IntStream.range(0, profile.inputSlots())
            .filter(slot -> !profile.isDedicatedInputSlot(slot))
            .iterator();
        final List<Integer> planned = new ArrayList<>(recipe.inputs().size());
        for (MachineRecipeDefinition.Input input : recipe.inputs()) {
            if (MachineRecipeManager.ingredientUsesDedicatedSlot(profile, input.ingredient())) {
                planned.add(profile.dedicatedInputSlot());
            } else if (ordinarySlots.hasNext()) {
                planned.add(ordinarySlots.nextInt());
            } else {
                throw new IllegalArgumentException("Recipe inputs exceed the machine's semantic slots");
            }
        }
        return List.copyOf(planned);
    }
}

