package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.compat.viewer.PackagedRecipeViewerCatalog;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.ritual.RitualManager;
import java.util.List;

public final class PackagedJeiCatalog {
    private PackagedJeiCatalog() { }
    public static List<MachineRecipeManager.Match> machines() { return PackagedRecipeViewerCatalog.machines(); }
    public static List<RitualManager.Entry> rituals() { return PackagedRecipeViewerCatalog.rituals(); }
    public static List<CustomBrewJeiRecipe> customBrews() { return PackagedRecipeViewerCatalog.customBrews(); }
}
