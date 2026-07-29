package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.ritual.RitualManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.recipe.types.IRecipeType;

public final class WarlockeryJeiRecipeTypes {
    public static final List<String> MACHINE_IDS = List.of(
        "alchemical_oven",
        "distillery",
        "kettle",
        "cauldron",
        "silvervat",
        "spinningwheel",
        "brazier"
    );
    public static final Map<String, IRecipeType<MachineRecipeManager.Match>> MACHINES = createMachineTypes();
    public static final IRecipeType<RitualManager.Entry> RITUALS = IRecipeType.create(
        Warlockery.MOD_ID,
        "circle_rites",
        RitualManager.Entry.class
    );

    private WarlockeryJeiRecipeTypes() {
    }

    private static Map<String, IRecipeType<MachineRecipeManager.Match>> createMachineTypes() {
        final Map<String, IRecipeType<MachineRecipeManager.Match>> types = new LinkedHashMap<>();
        MACHINE_IDS.forEach(machine -> types.put(
            machine,
            IRecipeType.create(Warlockery.MOD_ID, "machine/" + machine, MachineRecipeManager.Match.class)
        ));
        return Map.copyOf(types);
    }
}
