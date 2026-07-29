package com.kadamitas.warlockery.crafting;

public record MachineProfile(
    String recipeType,
    int inputSlots,
    int outputStart,
    int fuelSlot,
    boolean requiresExternalHeat,
    boolean rejectsUnexpectedInputs,
    boolean supportsFluids,
    String displayBlock
) {
    public MachineProfile {
        if (recipeType.isBlank() || inputSlots < 0 || outputStart < inputSlots || fuelSlot < -1
            || fuelSlot >= outputStart || displayBlock.isBlank()) {
            throw new IllegalArgumentException("Invalid machine profile");
        }
    }

    public boolean hasFuelSlot() {
        return fuelSlot >= 0;
    }
}
