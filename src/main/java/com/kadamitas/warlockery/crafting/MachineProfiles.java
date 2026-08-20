package com.kadamitas.warlockery.crafting;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MachineProfiles {
    private static final MachineProfile ALCHEMICAL_OVEN = fuelled("alchemical_oven", "alchemical_oven", false);
    private static final MachineProfile DISTILLERY = distillery();
    private static final MachineProfile KETTLE = heated("kettle", false, true);
    private static final MachineProfile CAULDRON = heated("cauldron", true, true);
    private static final MachineProfile SILVER_VAT = heated("silvervat", false, false);
    private static final MachineProfile SPINNING_WHEEL = spinningWheel();
    private static final MachineProfile BRAZIER = brazier();
    private static final MachineProfile UNKNOWN = unpowered("unknown");

    private static final Map<String, MachineProfile> BY_BLOCK = Map.ofEntries(
        Map.entry("alchemical_oven", ALCHEMICAL_OVEN),
        Map.entry("alchemical_oven_lit", ALCHEMICAL_OVEN),
        Map.entry("distilleryidle", DISTILLERY),
        Map.entry("distilleryburning", DISTILLERY),
        Map.entry("kettle", KETTLE),
        Map.entry("cauldron", CAULDRON),
        Map.entry("silvervat", SILVER_VAT),
        Map.entry("spinningwheel", SPINNING_WHEEL),
        Map.entry("brazier", BRAZIER)
    );
    private static final Map<String, MachineProfile> BY_RECIPE_TYPE = BY_BLOCK.values().stream()
        .collect(Collectors.toUnmodifiableMap(MachineProfile::recipeType, Function.identity(), (first, _) -> first));

    private MachineProfiles() {
    }

    public static MachineProfile forBlock(final String blockId) {
        return BY_BLOCK.getOrDefault(blockId, UNKNOWN);
    }

    public static Optional<MachineProfile> forRecipeType(final String recipeType) {
        return Optional.ofNullable(BY_RECIPE_TYPE.get(recipeType));
    }

    public static boolean supportsRecipeType(final String recipeType) {
        return BY_RECIPE_TYPE.containsKey(recipeType);
    }

    public static Set<String> blockIds() {
        return BY_BLOCK.keySet();
    }

    public static boolean isMachineBlock(final String blockId) {
        return BY_BLOCK.containsKey(blockId);
    }

    private static MachineProfile fuelled(final String recipeType, final String displayBlock, final boolean supportsFluids) {
        return new MachineProfile(
            recipeType,
            4,
            5,
            4,
            4,
            false,
            false,
            supportsFluids,
            displayBlock,
            Optional.of("warlockery:ingredient_clay_jar"),
            false
        );
    }

    private static MachineProfile distillery() {
        return new MachineProfile(
            "distillery",
            3,
            3,
            4,
            -1,
            false,
            true,
            false,
            "distilleryidle",
            Optional.of("warlockery:ingredient_clay_jar"),
            false
        );
    }

    private static MachineProfile heated(
        final String recipeType,
        final boolean rejectsUnexpectedInputs,
        final boolean supportsFluids
    ) {
        return new MachineProfile(
            recipeType, 6, 6, 3, -1, true, rejectsUnexpectedInputs, supportsFluids, recipeType, Optional.empty(), false
        );
    }

    private static MachineProfile unpowered(final String recipeType) {
        return new MachineProfile(
            recipeType, 6, 6, 3, -1, false, false, false, recipeType, Optional.empty(), false
        );
    }

    private static MachineProfile spinningWheel() {
        return new MachineProfile(
            "spinningwheel", 4, 4, 1, -1, false, true, false, "spinningwheel", Optional.empty(), true
        );
    }

    private static MachineProfile brazier() {
        return new MachineProfile(
            "brazier", 3, 3, 1, -1, false, true, false, "brazier", Optional.empty(), false
        );
    }
}
