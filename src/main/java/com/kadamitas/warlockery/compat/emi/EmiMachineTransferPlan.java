package com.kadamitas.warlockery.compat.emi;

import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeDefinition;
import com.kadamitas.warlockery.crafting.MachineRecipeSlotPlan;
import java.util.List;

final class EmiMachineTransferPlan {
    static final List<String> MACHINES = MachineProfiles.blockIds().stream()
        .map(id -> MachineProfiles.forBlock(id).recipeType()).distinct().sorted().toList();
    private EmiMachineTransferPlan() {}

    static boolean supports(String machine, MachineRecipeDefinition recipe) {
        return recipe.machine().equals(machine) && recipe.fluid().isEmpty() && MachineProfiles.supportsRecipeType(machine);
    }

    static List<Integer> slots(MachineRecipeDefinition recipe) {
        return supports(recipe.machine(), recipe)
            ? MachineRecipeSlotPlan.inputSlots(MachineProfiles.forRecipeType(recipe.machine()).orElseThrow(), recipe) : List.of();
    }

    static List<Integer> clearSlots(String machine) {
        return MachineProfiles.forRecipeType(machine)
            .map(profile -> java.util.stream.IntStream.range(0, profile.inputSlots()).boxed().toList()).orElse(List.of());
    }
}
