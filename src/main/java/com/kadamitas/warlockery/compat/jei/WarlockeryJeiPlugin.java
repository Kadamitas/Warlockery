package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.RitualManager;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@JeiPlugin
public final class WarlockeryJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "jei_plugin");
    private Map<Identifier, MachineRecipeManager.Match> machineRecipes = Map.of();
    private Map<Identifier, RitualManager.Entry> rituals = Map.of();
    private IJeiRuntime runtime;

    public WarlockeryJeiPlugin() {
        JeiRecipeRefreshSignal.subscribe(this::scheduleRefresh);
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(final IRecipeCategoryRegistration registration) {
        final var guiHelper = registration.getJeiHelpers().getGuiHelper();
        WarlockeryJeiRecipeTypes.MACHINES.forEach((machine, type) ->
            registration.addRecipeCategories(new MachineRecipeCategory(machine, type, guiHelper)));
        registration.addRecipeCategories(new RitualRecipeCategory(guiHelper));
    }

    @Override
    public synchronized void registerRecipes(final IRecipeRegistration registration) {
        final List<MachineRecipeManager.Match> availableMachines = availableMachines();
        final Map<String, List<MachineRecipeManager.Match>> recipesByMachine = availableMachines.stream()
            .collect(Collectors.groupingBy(match -> match.recipe().machine()));
        WarlockeryJeiRecipeTypes.MACHINES.forEach((machine, type) -> {
            final List<MachineRecipeManager.Match> recipes = recipesByMachine.getOrDefault(machine, List.of());
            registration.addRecipes(type, recipes);
        });
        final List<RitualManager.Entry> availableRituals = visibleRituals();
        registration.addRecipes(WarlockeryJeiRecipeTypes.RITUALS, availableRituals);
        machineRecipes = byMachineId(availableMachines);
        rituals = byRitualId(availableRituals);
        registerInformation(registration);
    }

    @Override
    public void registerRecipeCatalysts(final IRecipeCatalystRegistration registration) {
        WarlockeryJeiRecipeTypes.MACHINES.forEach((machine, type) -> MachineProfiles.forRecipeType(machine)
            .map(profile -> ModBlocks.ALL.get(profile.displayBlock()))
            .ifPresent(block -> registration.addCraftingStation(type, block.get())));
        registration.addCraftingStation(
            WarlockeryJeiRecipeTypes.RITUALS,
            ModBlocks.ALL.get("altar").get(),
            ModItems.ALL.get("arcane_focus").get(),
            ModItems.ALL.get("chalkritual").get(),
            ModItems.ALL.get("chalkinfernal").get(),
            ModItems.ALL.get("chalk_veil").get()
        );
    }

    @Override
    public synchronized void onRuntimeAvailable(final IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        refreshRecipes();
    }

    @Override
    public synchronized void onRuntimeUnavailable() {
        runtime = null;
        machineRecipes = Map.of();
        rituals = Map.of();
    }

    private void scheduleRefresh() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(this::refreshRecipes);
        }
    }

    private synchronized void refreshRecipes() {
        if (runtime == null) {
            return;
        }
        final var recipeManager = runtime.getRecipeManager();
        final Map<Identifier, MachineRecipeManager.Match> nextMachines = byMachineId(availableMachines());
        final List<MachineRecipeManager.Match> removedMachines = machineRecipes.values().stream()
            .filter(previous -> !Objects.equals(previous, nextMachines.get(previous.id())))
            .toList();
        final List<MachineRecipeManager.Match> addedMachines = nextMachines.values().stream()
            .filter(next -> !Objects.equals(next, machineRecipes.get(next.id())))
            .toList();
        updateMachines(recipeManager, removedMachines, true);
        updateMachines(recipeManager, addedMachines, false);
        machineRecipes = nextMachines;

        final Map<Identifier, RitualManager.Entry> nextRituals = byRitualId(visibleRituals());
        final List<RitualManager.Entry> removedRituals = rituals.values().stream()
            .filter(previous -> !Objects.equals(previous, nextRituals.get(previous.id())))
            .toList();
        final List<RitualManager.Entry> addedRituals = nextRituals.values().stream()
            .filter(next -> !Objects.equals(next, rituals.get(next.id())))
            .toList();
        if (!removedRituals.isEmpty()) {
            recipeManager.hideRecipes(WarlockeryJeiRecipeTypes.RITUALS, removedRituals);
        }
        if (!addedRituals.isEmpty()) {
            recipeManager.addRecipes(WarlockeryJeiRecipeTypes.RITUALS, addedRituals);
        }
        rituals = nextRituals;
    }

    private static List<RitualManager.Entry> visibleRituals() {
        final List<RitualManager.Entry> loaded = RitualManager.INSTANCE.all();
        return (loaded.isEmpty() ? PackagedJeiCatalog.rituals() : loaded).stream()
            .filter(entry -> entry.definition().visible())
            .toList();
    }

    private static List<MachineRecipeManager.Match> availableMachines() {
        final List<MachineRecipeManager.Match> loaded = MachineRecipeManager.INSTANCE.all();
        return loaded.isEmpty() ? PackagedJeiCatalog.machines() : loaded;
    }

    private static Map<Identifier, MachineRecipeManager.Match> byMachineId(
        final List<MachineRecipeManager.Match> recipes
    ) {
        return recipes.stream().collect(Collectors.toUnmodifiableMap(
            MachineRecipeManager.Match::id,
            match -> match,
            (_, replacement) -> replacement
        ));
    }

    private static Map<Identifier, RitualManager.Entry> byRitualId(final List<RitualManager.Entry> entries) {
        return entries.stream().collect(Collectors.toUnmodifiableMap(
            RitualManager.Entry::id,
            entry -> entry,
            (_, replacement) -> replacement
        ));
    }

    private static void updateMachines(
        final mezz.jei.api.recipe.IRecipeManager recipeManager,
        final List<MachineRecipeManager.Match> recipes,
        final boolean hide
    ) {
        final Map<String, List<MachineRecipeManager.Match>> byMachine = recipes.stream()
            .collect(Collectors.groupingBy(match -> match.recipe().machine()));
        WarlockeryJeiRecipeTypes.MACHINES.forEach((machine, type) -> {
            final List<MachineRecipeManager.Match> matches = byMachine.getOrDefault(machine, List.of());
            if (matches.isEmpty()) {
                return;
            }
            if (hide) {
                recipeManager.hideRecipes(type, matches);
            } else {
                recipeManager.addRecipes(type, matches);
            }
        });
    }

    private static void registerInformation(final IRecipeRegistration registration) {
        registration.addIngredientInfo(
            ModBlocks.ALL.get("altar").get(),
            Component.translatable("manual.warlockery.circles.ritual_ui"),
            Component.translatable("manual.warlockery.circles.power")
        );
        registration.addIngredientInfo(
            ModBlocks.ALL.get("alchemical_oven").get(),
            Component.translatable("manual.warlockery.fumes.oven"),
            Component.translatable("manual.warlockery.fumes.jars"),
            Component.translatable("manual.warlockery.fumes.funnels")
        );
        registration.addIngredientInfo(
            ModBlocks.ALL.get("distilleryidle").get(),
            Component.translatable("manual.warlockery.distilling.inputs"),
            Component.translatable("manual.warlockery.distilling.outputs"),
            Component.translatable("manual.warlockery.distilling.automation")
        );
        registration.addIngredientInfo(
            ModBlocks.ALL.get("cauldron").get(),
            Component.translatable("jei.warlockery.info.custom_brewing"),
            Component.translatable("manual.warlockery.codex.delivery"),
            Component.translatable("manual.warlockery.codex.diagnostics")
        );
    }
}
