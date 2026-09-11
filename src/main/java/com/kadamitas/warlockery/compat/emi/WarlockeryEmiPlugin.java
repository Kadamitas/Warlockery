package com.kadamitas.warlockery.compat.emi;

import com.kadamitas.warlockery.client.MachineScreen;
import com.kadamitas.warlockery.compat.viewer.RecipeViewerCatalog;
import com.kadamitas.warlockery.compat.viewer.RecipeViewerRefreshSignal;
import com.kadamitas.warlockery.compat.viewer.RecipeViewerNavigation;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.ModMenus;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.runtime.EmiReloadManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@EmiEntrypoint
public final class WarlockeryEmiPlugin implements EmiPlugin {
    static final List<String> MACHINES = EmiMachineTransferPlan.MACHINES;
    private static final AtomicBoolean SUBSCRIBED = new AtomicBoolean();
    private static final AtomicBoolean RELOAD_QUEUED = new AtomicBoolean();

    public WarlockeryEmiPlugin() {
        if (SUBSCRIBED.compareAndSet(false, true)) RecipeViewerRefreshSignal.subscribe(WarlockeryEmiPlugin::scheduleReload);
    }

    @Override public void register(EmiRegistry registry) {
        Map<String, EmiRecipeCategory> machines = new LinkedHashMap<>();
        for (String machine : MACHINES) {
            var profile = MachineProfiles.forRecipeType(machine).orElseThrow();
            var block = ModBlocks.ALL.get(profile.displayBlock()).get();
            var category = category("machine/" + machine, EmiStack.of(block), Component.translatable(block.getDescriptionId()));
            registry.addCategory(category);
            registry.addWorkstation(category, EmiStack.of(block));
            machines.put(machine, category);
            registry.addRecipeHandler(ModMenus.machine(machine).get(), new WarlockeryEmiRecipeHandler(machine));
        }
        var rituals = category("rituals", EmiStack.of(ModItems.ALL.get("arcane_focus").get()), Component.translatable("screen.warlockery.ritual.title"));
        var custom = category("custom_brews", EmiStack.of(ModBlocks.ALL.get("cauldron").get()), Component.translatable("jei.warlockery.custom.title"));
        var world = category("world_interactions", EmiStack.of(ModBlocks.ALL.get("cauldron").get()), Component.translatable("jei.warlockery.world.title"));
        registry.addCategory(rituals);
        registry.addCategory(custom);
        registry.addCategory(world);
        registry.addWorkstation(custom, EmiStack.of(ModBlocks.ALL.get("cauldron").get()));
        registry.addWorkstation(rituals, EmiStack.of(ModBlocks.ALL.get("altar").get()));
        for (String tool : List.of("arcane_focus", "chalkheart", "chalkritual", "chalkinfernal", "chalk_veil")) {
            registry.addWorkstation(rituals, EmiStack.of(ModItems.ALL.get(tool).get()));
        }
        RecipeViewerCatalog.machines().forEach(recipe -> registry.addRecipe(WarlockeryEmiRecipe.machine(machines.get(recipe.recipe().machine()), recipe)));
        RecipeViewerCatalog.rituals().forEach(recipe -> registry.addRecipe(WarlockeryEmiRecipe.ritual(rituals, recipe)));
        RecipeViewerCatalog.customBrews().forEach(recipe -> registry.addRecipe(WarlockeryEmiRecipe.component(custom, recipe)));
        registry.addRecipe(WarlockeryEmiRecipe.anoint(world));
        var information = category("information", EmiStack.of(net.minecraft.world.item.Items.BOOK), Component.translatable("viewer.warlockery.information"));
        registry.addCategory(information);
        registerInformation(registry, information);
        RecipeViewerNavigation.register(10, machine -> {
            var category = machines.get(machine);
            if (category == null || !EmiReloadManager.isLoaded() || EmiApi.getRecipeManager().getRecipes(category).isEmpty()) return false;
            EmiApi.displayRecipeCategory(category);
            return true;
        });
        registry.addScreenBoundsProvider(MachineScreen.class, WarlockeryEmiPlugin::machineBounds);
        registry.addStackProvider(MachineScreen.class, (screen, x, y) -> {
            var layout = screen.getMenu().layout();
            var bounds = machineBounds(screen);
            var area = new Bounds(bounds.x() + 25, bounds.y() + layout.statusY(), layout.width() - 42, 16);
            if (!area.contains(x, y)) return EmiStackInteraction.EMPTY;
            var profile = MachineProfiles.forRecipeType(screen.getMenu().kind()).orElseThrow();
            return new EmiStackInteraction(EmiStack.of(ModBlocks.ALL.get(profile.displayBlock()).get()));
        });
    }

    private static EmiRecipeCategory category(String path, EmiStack icon, Component title) {
        return new EmiRecipeCategory(Identifier.fromNamespaceAndPath("warlockery", path), icon) {
            @Override public Component getName() { return title; }
        };
    }

    private static Bounds machineBounds(MachineScreen screen) {
        var layout = screen.getMenu().layout();
        return new Bounds((screen.width - layout.width()) / 2, (screen.height - layout.height()) / 2, layout.width(), layout.height());
    }

    private static void scheduleReload() {
        if (!RELOAD_QUEUED.compareAndSet(false, true)) return;
        Minecraft.getInstance().execute(() -> {
            RELOAD_QUEUED.set(false);
            if (Minecraft.getInstance().level != null) EmiReloadManager.reload();
        });
    }

    private static void information(EmiRegistry registry, EmiRecipeCategory category, String item, String... keys) {
        registry.addRecipe(WarlockeryEmiRecipe.information(category, "warlockery:" + item,
            java.util.Arrays.stream(keys).map(key -> (Component) Component.translatable(key)).toList()));
    }

    private static void registerInformation(EmiRegistry registry, EmiRecipeCategory category) {
        for (String seed : List.of("seedsbelladonna", "seedsmandrake", "seedswormwood", "seedswolfsbane", "seedsartichoke", "seedssnowbell")) {
            information(registry, category, seed, "manual.warlockery.onboarding.seeds");
        }
        for (String item : List.of("ingredient_gypsum", "chalkheart")) information(registry, category, item, "manual.warlockery.circles.golden_chalk");
        for (String item : List.of("chalkritual", "chalkinfernal", "chalk_veil")) {
            information(registry, category, item, "manual.warlockery.circles.chalk", "manual.warlockery.circles.ritual_ui");
        }
        information(registry, category, "arcane_focus", "manual.warlockery.circles.ritual_ui");
        information(registry, category, "ritual_knife", "manual.warlockery.circles.arthana", "jei.warlockery.info.arthana_harvest");
        for (String item : List.of("ingredient_bat_wool", "ingredient_dog_tongue", "ingredient_owlets_wing", "ingredient_toe_of_frog",
                "ingredient_creeper_heart", "ingredient_spectral_dust")) information(registry, category, item, "jei.warlockery.info.arthana_harvest");
        information(registry, category, "altar", "manual.warlockery.circles.ritual_ui", "manual.warlockery.circles.power");
        information(registry, category, "alchemical_oven", "manual.warlockery.fumes.oven", "manual.warlockery.fumes.jars", "manual.warlockery.fumes.funnels");
        information(registry, category, "distilleryidle", "manual.warlockery.distilling.inputs", "manual.warlockery.distilling.outputs", "manual.warlockery.distilling.automation");
        information(registry, category, "cauldron", "jei.warlockery.info.custom_brewing", "manual.warlockery.codex.delivery", "manual.warlockery.codex.diagnostics");
    }
}
