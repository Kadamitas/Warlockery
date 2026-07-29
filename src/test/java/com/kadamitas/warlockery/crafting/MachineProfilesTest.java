package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class MachineProfilesTest {
    private static final Set<String> MACHINE_BLOCKS = Set.of(
        "alchemical_oven",
        "alchemical_oven_lit",
        "distilleryidle",
        "distilleryburning",
        "kettle",
        "cauldron",
        "silvervat",
        "spinningwheel",
        "brazier"
    );

    @Test
    void everyMachineBlockHasOneProfile() {
        assertEquals(MACHINE_BLOCKS, MachineProfiles.blockIds());
        assertTrue(MACHINE_BLOCKS.stream()
            .map(MachineProfiles::forBlock)
            .noneMatch(profile -> "unknown".equals(profile.recipeType())));
    }

    @Test
    void ovenVariantsShareTheFuelledRecipeProfile() {
        final MachineProfile idle = MachineProfiles.forBlock("alchemical_oven");
        final MachineProfile lit = MachineProfiles.forBlock("alchemical_oven_lit");
        assertSame(idle, lit);
        assertEquals("alchemical_oven", idle.recipeType());
        assertTrue(idle.hasFuelSlot());
        assertFalse(idle.requiresExternalHeat());
    }

    @Test
    void recipeTypesResolveWithoutBlockNameHeuristics() {
        assertEquals(
            Set.of("alchemical_oven", "distillery", "kettle", "cauldron", "silvervat", "spinningwheel", "brazier"),
            MACHINE_BLOCKS.stream()
                .map(MachineProfiles::forBlock)
                .map(MachineProfile::recipeType)
                .collect(java.util.stream.Collectors.toUnmodifiableSet())
        );
    }

    @Test
    void exactIngredientMachinesRejectUnexpectedInputs() {
        final Set<String> exactIngredientMachines = Set.of("cauldron", "brazier");
        assertTrue(exactIngredientMachines.stream()
            .map(MachineProfiles::forBlock)
            .allMatch(MachineProfile::rejectsUnexpectedInputs));
        assertTrue(MACHINE_BLOCKS.stream()
            .filter(id -> !exactIngredientMachines.contains(id))
            .map(MachineProfiles::forBlock)
            .noneMatch(MachineProfile::rejectsUnexpectedInputs));
    }

    @Test
    void liquidMachinesAdvertiseFluidStorage() {
        assertTrue(MachineProfiles.forBlock("distilleryidle").supportsFluids());
        assertTrue(MachineProfiles.forBlock("kettle").supportsFluids());
        assertTrue(MachineProfiles.forBlock("cauldron").supportsFluids());
        assertTrue(MachineProfiles.forBlock("silvervat").supportsFluids());
        assertFalse(MachineProfiles.forBlock("alchemical_oven").supportsFluids());
        assertFalse(MachineProfiles.forBlock("spinningwheel").supportsFluids());
    }
}
