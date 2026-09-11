package com.kadamitas.warlockery.compat.rei;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.client.MachineScreen;
import com.kadamitas.warlockery.compat.viewer.RecipeViewerCatalog;
import com.kadamitas.warlockery.compat.viewer.RecipeViewerNavigation;
import com.kadamitas.warlockery.compat.viewer.RecipeViewerRefreshSignal;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.menu.MachineUiLayout;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.impl.ClientInternals;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class WarlockeryReiPlugin implements REIClientPlugin {
    static final List<String> MACHINE_IDS = List.of(
        "alchemical_oven", "distillery", "kettle", "cauldron", "silvervat", "spinningwheel", "brazier"
    );
    private static final Map<String, CategoryIdentifier<WarlockeryReiDisplay>> MACHINES = machineCategories();
    public static final CategoryIdentifier<WarlockeryReiDisplay> RITUALS = category("circle_rites");
    public static final CategoryIdentifier<WarlockeryReiDisplay> CUSTOM_BREWS = category("custom_brewing");
    public static final CategoryIdentifier<WarlockeryReiDisplay> WORLD_INTERACTIONS = category("world_interactions");
    public static final CategoryIdentifier<WarlockeryReiDisplay> INFORMATION = category("information");

    public WarlockeryReiPlugin() {
        RecipeViewerNavigation.register(30, WarlockeryReiPlugin::openMachine);
        RecipeViewerRefreshSignal.subscribe(WarlockeryReiPlugin::queueOverlayRefresh);
    }

    @Override
    public void registerCategories(final CategoryRegistry registry) {
        if (jeiBridgeImportsWarlockery()) return;
        for (final String machine : MACHINE_IDS) {
            registry.add(WarlockeryReiCategory.machine(machine));
            MachineProfiles.forRecipeType(machine).map(profile -> ModBlocks.ALL.get(profile.displayBlock()))
                .ifPresent(block -> registry.addWorkstations(machineCategory(machine), EntryStacks.of(block.get())));
        }
        registry.add(WarlockeryReiCategory.rituals());
        registry.add(WarlockeryReiCategory.customBrews());
        registry.add(WarlockeryReiCategory.worldInteractions());
        registry.add(WarlockeryReiCategory.information());
        registry.addWorkstations(CUSTOM_BREWS, EntryStacks.of(ModBlocks.ALL.get("cauldron").get()));
        registry.addWorkstations(RITUALS,
            EntryStacks.of(ModBlocks.ALL.get("altar").get()),
            EntryStacks.of(ModItems.ALL.get("arcane_focus").get()),
            EntryStacks.of(ModItems.ALL.get("chalkheart").get()),
            EntryStacks.of(ModItems.ALL.get("chalkritual").get()),
            EntryStacks.of(ModItems.ALL.get("chalkinfernal").get()),
            EntryStacks.of(ModItems.ALL.get("chalk_veil").get())
        );
    }

    @Override
    public void registerDisplays(final DisplayRegistry registry) {
        if (jeiBridgeImportsWarlockery()) return;
        RecipeViewerCatalog.machines().stream().map(WarlockeryReiDisplay::machine).forEach(registry::add);
        RecipeViewerCatalog.rituals().stream().map(WarlockeryReiDisplay::ritual).forEach(registry::add);
        RecipeViewerCatalog.customBrews().stream().map(WarlockeryReiDisplay::customBrew).forEach(registry::add);
        registry.add(WarlockeryReiDisplay.worldInteraction(
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "anoint_cauldron"),
            "warlockery:ingredient_annointing_paste",
            "minecraft:cauldron",
            "warlockery:cauldron"
        ));
        registerInformation(registry);
    }

    @Override
    public void registerScreens(final ScreenRegistry registry) {
        if (jeiBridgeImportsWarlockery()) return;
        for (final String machine : MACHINE_IDS) {
            final CategoryIdentifier<?>[] categories = machine.equals("cauldron")
                ? new CategoryIdentifier<?>[] {machineCategory(machine), CUSTOM_BREWS}
                : new CategoryIdentifier<?>[] {machineCategory(machine)};
            registry.registerContainerClickArea(screen -> clickArea(screen, machine), MachineScreen.class, categories);
        }
    }

    @Override
    public void registerTransferHandlers(final TransferHandlerRegistry registry) {
        if (jeiBridgeImportsWarlockery()) return;
        MACHINE_IDS.forEach(machine -> registry.register(new WarlockeryReiTransferHandler(machine)));
    }

    public static CategoryIdentifier<WarlockeryReiDisplay> machineCategory(final String machine) {
        final CategoryIdentifier<WarlockeryReiDisplay> category = MACHINES.get(machine);
        if (category == null) throw new IllegalArgumentException("Unknown machine recipe type: " + machine);
        return category;
    }

    static boolean jeiBridgeImportsWarlockery() {
        try {
            return ClientInternals.getJeiCompatMods().contains(Warlockery.MOD_ID);
        } catch (final IllegalStateException ignored) {
            return false;
        }
    }

    private static boolean openMachine(final String machine) {
        if (!MACHINES.containsKey(machine)) return false;
        return ViewSearchBuilder.builder()
            .addCategory(machineCategory(machine))
            .setPreferredOpenedCategory(machineCategory(machine))
            .open();
    }

    private static Rectangle clickArea(final MachineScreen screen, final String machine) {
        if (!screen.getMenu().kind().equals(machine)) return new Rectangle(-1_000, -1_000, 0, 0);
        final MachineUiLayout layout = screen.getMenu().layout();
        return new Rectangle(25, layout.statusY(), layout.width() - 42, 16);
    }

    private static void queueOverlayRefresh() {
        final Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            try {
                REIRuntime.getInstance().getOverlay(false, false).ifPresent(overlay -> {
                    overlay.queueReloadSearch();
                    overlay.queueReloadOverlay();
                });
            } catch (final IllegalStateException ignored) {
                // REI has not created its client runtime yet; its normal plugin reload will read the snapshot.
            }
        });
    }

    private static void registerInformation(final DisplayRegistry registry) {
        for (final String seed : List.of(
            "seedsbelladonna", "seedsmandrake", "seedswormwood", "seedswolfsbane", "seedsartichoke", "seedssnowbell"
        )) {
            addInformation(registry, "item/" + seed, ModItems.ALL.get(seed).get(),
                Component.translatable("manual.warlockery.onboarding.seeds"));
        }
        for (final String item : List.of("ingredient_gypsum", "chalkheart")) {
            addInformation(registry, "item/" + item, ModItems.ALL.get(item).get(),
                Component.translatable("manual.warlockery.circles.golden_chalk"));
        }
        for (final String item : List.of("chalkritual", "chalkinfernal", "chalk_veil")) {
            addInformation(registry, "item/" + item, ModItems.ALL.get(item).get(),
                Component.translatable("manual.warlockery.circles.chalk"),
                Component.translatable("manual.warlockery.circles.ritual_ui"));
        }
        addInformation(registry, "item/arcane_focus", ModItems.ALL.get("arcane_focus").get(),
            Component.translatable("manual.warlockery.circles.ritual_ui"));
        addInformation(registry, "item/ritual_knife", ModItems.ALL.get("ritual_knife").get(),
            Component.translatable("manual.warlockery.circles.arthana"),
            Component.translatable("jei.warlockery.info.arthana_harvest"));
        for (final String item : List.of(
            "ingredient_bat_wool", "ingredient_dog_tongue", "ingredient_owlets_wing",
            "ingredient_toe_of_frog", "ingredient_creeper_heart", "ingredient_spectral_dust"
        )) {
            addInformation(registry, "item/" + item, ModItems.ALL.get(item).get(),
                Component.translatable("jei.warlockery.info.arthana_harvest"));
        }
        addInformation(registry, "block/altar", ModBlocks.ALL.get("altar").get(),
            Component.translatable("manual.warlockery.circles.ritual_ui"),
            Component.translatable("manual.warlockery.circles.power"));
        addInformation(registry, "block/alchemical_oven", ModBlocks.ALL.get("alchemical_oven").get(),
            Component.translatable("manual.warlockery.fumes.oven"),
            Component.translatable("manual.warlockery.fumes.jars"),
            Component.translatable("manual.warlockery.fumes.funnels"));
        addInformation(registry, "block/distilleryidle", ModBlocks.ALL.get("distilleryidle").get(),
            Component.translatable("manual.warlockery.distilling.inputs"),
            Component.translatable("manual.warlockery.distilling.outputs"),
            Component.translatable("manual.warlockery.distilling.automation"));
        addInformation(registry, "block/cauldron", ModBlocks.ALL.get("cauldron").get(),
            Component.translatable("jei.warlockery.info.custom_brewing"),
            Component.translatable("manual.warlockery.codex.delivery"),
            Component.translatable("manual.warlockery.codex.diagnostics"));
    }

    private static void addInformation(
        final DisplayRegistry registry,
        final String path,
        final ItemLike ingredient,
        final Component... text
    ) {
        for (int index = 0; index < text.length; index++) {
            registry.add(WarlockeryReiDisplay.information(
                Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "information/" + path + "_" + index),
                new ItemStack(ingredient),
                List.of(text[index])
            ));
        }
    }

    private static CategoryIdentifier<WarlockeryReiDisplay> category(final String path) {
        return CategoryIdentifier.of(Warlockery.MOD_ID, path);
    }

    private static Map<String, CategoryIdentifier<WarlockeryReiDisplay>> machineCategories() {
        final Map<String, CategoryIdentifier<WarlockeryReiDisplay>> categories = new LinkedHashMap<>();
        MACHINE_IDS.forEach(machine -> categories.put(machine, category("machine/" + machine)));
        return Map.copyOf(categories);
    }
}
