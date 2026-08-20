package com.kadamitas.warlockery.crafting;

import java.util.Optional;

public record MachineProfile(
    String recipeType,
    int inputSlots,
    int outputStart,
    int outputSlots,
    int fuelSlot,
    boolean requiresExternalHeat,
    boolean rejectsUnexpectedInputs,
    boolean supportsFluids,
    String displayBlock,
    Optional<String> dedicatedInputIngredient,
    boolean hasPrimaryInputSlot
) {
    public MachineProfile {
        if (recipeType.isBlank() || inputSlots < 0 || outputStart < inputSlots || outputSlots <= 0 || fuelSlot < -1
            || fuelSlot >= outputStart || displayBlock.isBlank()) {
            throw new IllegalArgumentException("Invalid machine profile");
        }
        dedicatedInputIngredient = dedicatedInputIngredient == null ? Optional.empty() : dedicatedInputIngredient;
        if (dedicatedInputIngredient.filter(String::isBlank).isPresent()
            || dedicatedInputIngredient.isPresent() && inputSlots == 0
            || hasPrimaryInputSlot && inputSlots == 0) {
            throw new IllegalArgumentException("Invalid dedicated machine input");
        }
    }

    public boolean hasFuelSlot() {
        return fuelSlot >= 0;
    }

    public boolean hasDedicatedInputSlot() {
        return dedicatedInputIngredient.isPresent();
    }

    public int dedicatedInputSlot() {
        return hasDedicatedInputSlot() ? inputSlots - 1 : -1;
    }

    public boolean isDedicatedInputSlot(final int slot) {
        return slot == dedicatedInputSlot();
    }

    public int outputEnd() {
        return outputStart + outputSlots;
    }
}
