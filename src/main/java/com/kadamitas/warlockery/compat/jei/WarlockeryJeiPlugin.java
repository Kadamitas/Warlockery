package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.compat.viewer.RecipeViewerCatalog;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.RitualManager;
import java.util.List;
import java.util.Map;
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
    private Map<Identifier, CustomBrewJeiRecipe> customBrews = Map.of();
    private IJeiRuntime runtime;

    public WarlockeryJeiPlugin() {
        JeiRecipeRefreshSignal.subscribe(this::scheduleRefresh);
        com.kadamitas.warlockery.compat.viewer.RecipeViewerNavigation.register(40, machine -> {
            if (runtime == null) return false;
            final var type = WarlockeryJeiRecipeTypes.MACHINES.get(machine);
            if (type == null) return false;
            runtime.getRecipesGui().showTypes(List.of(type));
            return true;
        });
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
        registration.addRecipeCategories(new CustomBrewRecipeCategory(guiHelper));
        registration.addRecipeCategories(new WorldInteractionRecipeCategory(guiHelper));
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
        final List<CustomBrewJeiRecipe> components = availableCustomBrews();
        registration.addRecipes(WarlockeryJeiRecipeTypes.CUSTOM_BREWS, components);
        registration.addRecipes(WarlockeryJeiRecipeTypes.WORLD_INTERACTIONS, List.of(WorldInteractionJeiRecipe.ANOINT_CAULDRON));
        customBrews = components.stream().collect(Collectors.toUnmodifiableMap(CustomBrewJeiRecipe::id, recipe -> recipe));
        registerInformation(registration);
    }

    @Override
    public void registerRecipeCatalysts(final IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(WarlockeryJeiRecipeTypes.CUSTOM_BREWS, ModBlocks.ALL.get("cauldron").get());
        WarlockeryJeiRecipeTypes.MACHINES.forEach((machine, type) -> MachineProfiles.forRecipeType(machine)
            .map(profile -> ModBlocks.ALL.get(profile.displayBlock()))
            .ifPresent(block -> registration.addCraftingStation(type, block.get())));
        registration.addCraftingStation(
            WarlockeryJeiRecipeTypes.RITUALS,
            ModBlocks.ALL.get("altar").get(),
            ModItems.ALL.get("arcane_focus").get(),
            ModItems.ALL.get("chalkheart").get(),
            ModItems.ALL.get("chalkritual").get(),
            ModItems.ALL.get("chalkinfernal").get(),
            ModItems.ALL.get("chalk_veil").get()
        );
    }

    @Override
    public void registerGuiHandlers(final mezz.jei.api.registration.IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(com.kadamitas.warlockery.client.MachineScreen.class, new MachineJeiGuiHandler());
    }

    @Override
    public void registerRecipeTransferHandlers(final mezz.jei.api.registration.IRecipeTransferRegistration registration) {
        WarlockeryJeiRecipeTypes.MACHINE_IDS.forEach(machine -> registration.addRecipeTransferHandler(new MachineJeiTransferInfo(machine)));
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
        customBrews = Map.of();
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
            .toList();
        final List<MachineRecipeManager.Match> addedMachines = nextMachines.values().stream()
            .toList();
        updateMachines(recipeManager, removedMachines, true);
        updateMachines(recipeManager, addedMachines, false);
        machineRecipes = nextMachines;

        final Map<Identifier, RitualManager.Entry> nextRituals = byRitualId(visibleRituals());
        final List<RitualManager.Entry> removedRituals = rituals.values().stream()
            .toList();
        final List<RitualManager.Entry> addedRituals = nextRituals.values().stream()
            .toList();
        if (!removedRituals.isEmpty()) {
            recipeManager.hideRecipes(WarlockeryJeiRecipeTypes.RITUALS, removedRituals);
        }
        if (!addedRituals.isEmpty()) {
            recipeManager.addRecipes(WarlockeryJeiRecipeTypes.RITUALS, addedRituals);
        }
        rituals = nextRituals;
        final Map<Identifier, CustomBrewJeiRecipe> nextCustomBrews = availableCustomBrews().stream()
            .collect(Collectors.toUnmodifiableMap(CustomBrewJeiRecipe::id, recipe -> recipe));
        final List<CustomBrewJeiRecipe> removedComponents = customBrews.values().stream().toList();
        final List<CustomBrewJeiRecipe> addedComponents = nextCustomBrews.values().stream().toList();
        if (!removedComponents.isEmpty()) recipeManager.hideRecipes(WarlockeryJeiRecipeTypes.CUSTOM_BREWS, removedComponents);
        if (!addedComponents.isEmpty()) recipeManager.addRecipes(WarlockeryJeiRecipeTypes.CUSTOM_BREWS, addedComponents);
        customBrews = nextCustomBrews;
    }

    private static List<CustomBrewJeiRecipe> availableCustomBrews() {
        return RecipeViewerCatalog.customBrews().stream()
            .map(recipe -> new CustomBrewJeiRecipe(recipe.id(), recipe.definition())).toList();
    }

    private static List<RitualManager.Entry> visibleRituals() {
        return RecipeViewerCatalog.rituals().stream()
            .map(entry -> new RitualManager.Entry(entry.id(), entry.definition())).toList();
    }

    private static List<MachineRecipeManager.Match> availableMachines() {
        return RecipeViewerCatalog.machines().stream()
            .map(match -> new MachineRecipeManager.Match(match.id(), match.recipe())).toList();
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
        for (final String seed : List.of("seedsbelladonna", "seedsmandrake", "seedswormwood", "seedswolfsbane", "seedsartichoke", "seedssnowbell")) {
            registration.addIngredientInfo(ModItems.ALL.get(seed).get(), Component.translatable("manual.warlockery.onboarding.seeds"));
        }
        for (final String item : List.of("ingredient_gypsum", "chalkheart")) {
            registration.addIngredientInfo(
                ModItems.ALL.get(item).get(),
                Component.translatable("manual.warlockery.circles.golden_chalk")
            );
        }
        for (final String item : List.of("chalkritual", "chalkinfernal", "chalk_veil")) {
            registration.addIngredientInfo(
                ModItems.ALL.get(item).get(),
                Component.translatable("manual.warlockery.circles.chalk"),
                Component.translatable("manual.warlockery.circles.ritual_ui")
            );
        }
        registration.addIngredientInfo(
            ModItems.ALL.get("arcane_focus").get(),
            Component.translatable("manual.warlockery.circles.ritual_ui")
        );
        registration.addIngredientInfo(
            ModItems.ALL.get("ritual_knife").get(),
            Component.translatable("manual.warlockery.circles.arthana"),
            Component.translatable("jei.warlockery.info.arthana_harvest")
        );
        for (final String item : List.of(
            "ingredient_bat_wool", "ingredient_dog_tongue", "ingredient_owlets_wing",
            "ingredient_toe_of_frog", "ingredient_creeper_heart", "ingredient_spectral_dust"
        )) {
            registration.addIngredientInfo(
                ModItems.ALL.get(item).get(),
                Component.translatable("jei.warlockery.info.arthana_harvest")
            );
        }
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
