package com.kadamitas.warlockery.compat.rrv;

import cc.cassian.rrv.api.ReliableRecipeViewerClientPlugin;
import cc.cassian.rrv.api.recipe.ItemView;
import cc.cassian.rrv.api.recipe.ReliableClientRecipe;
import cc.cassian.rrv.client.recipe.ClientRecipeCache;
import cc.cassian.rrv.common.extra.FluidStack;
import cc.cassian.rrv.common.recipe.inventory.SlotContent;
import com.kadamitas.warlockery.client.ManualRitualInstructions;
import com.kadamitas.warlockery.compat.jei.CustomBrewJeiRecipe;
import com.kadamitas.warlockery.compat.jei.WorldInteractionJeiRecipe;
import com.kadamitas.warlockery.compat.viewer.RecipeViewerCatalog;
import com.kadamitas.warlockery.compat.viewer.RecipeViewerIngredients;
import com.kadamitas.warlockery.compat.viewer.RecipeViewerRefreshSignal;
import com.kadamitas.warlockery.crafting.BrazierEffectRuntime;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.crafting.PowerMode;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import com.kadamitas.warlockery.ritual.RitualDefinition;
import com.kadamitas.warlockery.ritual.RitualManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class WarlockeryRrvPlugin implements ReliableRecipeViewerClientPlugin {
    private final Map<String, WarlockeryRrvType> types = new LinkedHashMap<>();

    @Override public void onIntegrationInitialize() {
        ItemView.addClientRecipeProvider(this::provide);
        com.kadamitas.warlockery.compat.viewer.RecipeViewerNavigation.register(20, machine -> {
            var type = types.get(machine);
            if (type == null) return false;
            cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay.INSTANCE.openRecipeView(type);
            return true;
        });
        RecipeViewerRefreshSignal.subscribe(() -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().level != null) ClientRecipeCache.INSTANCE.buildRecipeCache(true);
        }));
    }

    private void provide(List<ReliableClientRecipe> recipes) {
        types.clear();
        for (var machine : RecipeViewerCatalog.machines()) recipes.add(machine(machine));
        for (var ritual : RecipeViewerCatalog.rituals()) recipes.add(ritual(ritual));
        for (var brew : RecipeViewerCatalog.customBrews()) recipes.add(brew(brew));
        var conversion = WorldInteractionJeiRecipe.ANOINT_CAULDRON;
        recipes.add(new WarlockeryRrvRecipe(conversion.id(), type("world_interactions", Component.translatable("jei.warlockery.world.title"), "cauldron"),
            List.of(item(conversion.ingredient(), 1), item(conversion.placedBlock(), 1)), List.of(item(conversion.result(), 1)), List.of(),
            Component.translatable("jei.warlockery.world.anoint_cauldron"), null, List.of(), false));
        addInformation(recipes);
    }

    private WarlockeryRrvType type(String path, Component title, String... stations) {
        return types.computeIfAbsent(path, _ -> new WarlockeryRrvType(path, title,
            java.util.Arrays.stream(stations).map(id -> RecipeViewerIngredients.directItem("warlockery:" + id, 1))
                .flatMap(java.util.Optional::stream).toList()));
    }

    private WarlockeryRrvRecipe machine(MachineRecipeManager.Match match) {
        var recipe = match.recipe();
        var profile = MachineProfiles.forRecipeType(recipe.machine()).orElseThrow();
        var category = types.computeIfAbsent(recipe.machine(), _ -> new WarlockeryRrvType(recipe.machine(),
            Component.translatable(ModBlocks.ALL.get(profile.displayBlock()).get().getDescriptionId()),
            MachineProfiles.blockIds().stream().filter(id -> MachineProfiles.forBlock(id).recipeType().equals(recipe.machine()))
                .map(id -> new ItemStack(ModBlocks.ALL.get(id).get())).toList()));
        var inputs = new ArrayList<SlotContent>();
        for (int i = 0; i < recipe.inputs().size(); i++) inputs.add(SlotContent.of(RecipeViewerIngredients.machineInputs(match, i)));
        recipe.fluid().ifPresent(fluid -> inputs.add(SlotContent.ofFluidList(RecipeViewerIngredients.fluids(fluid.ingredient()).stream()
            .map(value -> new FluidStack(value, fluid.amount())).toList())));
        var outputs = recipe.outputs().stream().map(output -> item(output.item(), output.count())).toList();
        var text = Component.translatable("jei.warlockery.machine.processing_time", Math.max(1, recipe.processingTime() / 20));
        if (recipe.altarPower() > 0) paragraph(text, Component.translatable("jei.warlockery.machine.altar_power", recipe.altarPower()));
        if (recipe.requiresFuel()) paragraph(text, Component.translatable("jei.warlockery.machine.fuel"));
        if (profile.requiresExternalHeat()) paragraph(text, Component.translatable("jei.warlockery.machine.heat"));
        if (recipe.powerMode() == PowerMode.CONTINUOUS) paragraph(text, Component.translatable("manual.warlockery.machine_recipe.continuous_power"));
        if (recipe.machine().equals("brazier")) paragraph(text, Component.translatable("manual.warlockery.machine_recipe.ignite"));
        BrazierEffectRuntime.Effect.fromRecipe(match.id()).ifPresent(effect -> paragraph(text, Component.translatable("jei.warlockery.effect." + effect.recipePath())));
        return new WarlockeryRrvRecipe(match.id(), category, inputs, outputs, List.of(), text, match, List.of(), false);
    }

    private WarlockeryRrvRecipe brew(CustomBrewJeiRecipe brew) {
        var text = brew.role().copy();
        paragraph(text, brew.details());
        paragraph(text, Component.translatable("jei.warlockery.custom.setup"));
        paragraph(text, Component.translatable("jei.warlockery.custom.order"));
        return new WarlockeryRrvRecipe(brew.id(), type("custom_brews", Component.translatable("jei.warlockery.custom.title"), "cauldron"),
            List.of(item(brew.definition().ingredient(), 1)), List.of(), List.of(item("warlockery:cauldron", 1)), text, null, List.of(), true);
    }

    private WarlockeryRrvRecipe ritual(RitualManager.Entry entry) {
        var definition = entry.definition();
        var inputs = definition.requirements().ingredients().stream().filter(RitualDefinition.Ingredient::consume)
            .map(i -> item(i.ingredient(), i.count())).toList();
        var tools = new ArrayList<SlotContent>();
        definition.requirements().ingredients().stream().filter(i -> !i.consume()).forEach(i -> tools.add(item(i.ingredient(), i.count())));
        tools.add(item("warlockery:chalkheart", 1));
        tools.add(item("warlockery:arcane_focus", 1));
        var rings = ChalkCircleLayout.rings(definition.glyphs());
        var text = Component.translatable(definition.title());
        paragraph(text, Component.translatable(definition.description()));
        paragraph(text, Component.translatable("jei.warlockery.ritual.power", definition.power()));
        paragraph(text, Component.translatable("jei.warlockery.ritual.casting_time", Math.max(1, definition.castingTime() / 20)));
        for (var ring : rings) {
            String chalk = switch (ring.glyph()) {
                case "circleglyphinfernal" -> "chalkinfernal";
                case "circleglyph_veil" -> "chalk_veil";
                case "circleglyphgolden" -> "chalkheart";
                default -> "chalkritual";
            };
            tools.add(item("warlockery:" + chalk, 1));
            paragraph(text, RecipeViewerIngredients.directItem("warlockery:" + chalk, 1).orElseThrow().getHoverName().copy()
                .append(": ").append(Component.translatable("jei.warlockery.ritual.chalk_marks", ring.requiredCount())));
        }
        var req = definition.requirements();
        for (var condition : List.of(new Condition(definition.nightOnly(), "night"), new Condition(req.dayOnly(), "day"),
            new Condition(req.fullMoon(), "full_moon"), new Condition(req.raining(), "rain"), new Condition(req.thundering(), "thunder"))) {
            if (condition.enabled()) paragraph(text, Component.translatable("screen.warlockery.ritual.requirement." + condition.key()));
        }
        if (!req.dimension().isBlank()) paragraph(text, Component.translatable("screen.warlockery.ritual.requirement." + req.dimension().replace(':', '.')));
        if (req.minimumPlayers() > 1) paragraph(text, Component.translatable("screen.warlockery.ritual.requirement_count",
            Component.translatable("screen.warlockery.ritual.requirement.coven"), req.minimumPlayers(), req.minimumPlayers()));
        req.entities().forEach(entity -> {
            var id = Identifier.tryParse(entity.entity());
            Component name = id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                ? Component.translatable(BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescriptionId())
                : Component.literal(entity.entity().replace('#', ' ').replace('_', ' '));
            paragraph(text, Component.translatable("screen.warlockery.ritual.requirement_count", name, entity.count(), entity.count()));
        });
        for (String key : ManualRitualInstructions.keys(entry.id().getPath(), definition.action(), definition.target())) {
            paragraph(text, Component.translatable("manual.warlockery.ritual.setup." + key, definition.radius(), Math.clamp(definition.radius() * 2, 8, 24)));
        }
        String result = switch (definition.action()) {
            case "summon_item", "bind_item", "bind_circle", "bind_fetish" -> definition.target();
            case "bind_waystone", "copy_waystone" -> "warlockery:ingredient_waystone_bound";
            default -> "";
        };
        List<SlotContent> outputs = result.isBlank() ? List.of() : List.of(item(result, definition.action().equals("bind_fetish") ? 1 : definition.count()));
        return new WarlockeryRrvRecipe(entry.id(), type("rituals", Component.translatable("screen.warlockery.ritual.title"), "arcane_focus", "chalkheart", "altar"),
            inputs, outputs, tools, text, null, rings, outputs.isEmpty());
    }

    private void addInformation(List<ReliableClientRecipe> recipes) {
        var information = new LinkedHashMap<String, List<String>>();
        for (String seed : List.of("seedsbelladonna", "seedsmandrake", "seedswormwood", "seedswolfsbane", "seedsartichoke", "seedssnowbell"))
            information.put(seed, List.of("manual.warlockery.onboarding.seeds"));
        for (String item : List.of("ingredient_gypsum", "chalkheart")) information.put(item, List.of("manual.warlockery.circles.golden_chalk"));
        for (String item : List.of("chalkritual", "chalkinfernal", "chalk_veil")) information.put(item, List.of("manual.warlockery.circles.chalk", "manual.warlockery.circles.ritual_ui"));
        information.put("arcane_focus", List.of("manual.warlockery.circles.ritual_ui"));
        information.put("ritual_knife", List.of("manual.warlockery.circles.arthana", "jei.warlockery.info.arthana_harvest"));
        for (String item : List.of("ingredient_bat_wool", "ingredient_dog_tongue", "ingredient_owlets_wing", "ingredient_toe_of_frog", "ingredient_creeper_heart", "ingredient_spectral_dust"))
            information.put(item, List.of("jei.warlockery.info.arthana_harvest"));
        information.put("altar", List.of("manual.warlockery.circles.ritual_ui", "manual.warlockery.circles.power"));
        information.put("alchemical_oven", List.of("manual.warlockery.fumes.oven", "manual.warlockery.fumes.jars", "manual.warlockery.fumes.funnels"));
        information.put("distilleryidle", List.of("manual.warlockery.distilling.inputs", "manual.warlockery.distilling.outputs", "manual.warlockery.distilling.automation"));
        information.put("cauldron", List.of("jei.warlockery.info.custom_brewing", "manual.warlockery.codex.delivery", "manual.warlockery.codex.diagnostics"));
        var type = type("information", Component.translatable("viewer.warlockery.information"), "ingredient_book_circle_magic");
        information.forEach((item, keys) -> {
            var text = Component.empty();
            keys.forEach(key -> paragraph(text, Component.translatable(key)));
            var content = item("warlockery:" + item, 1);
            recipes.add(new WarlockeryRrvRecipe(Identifier.fromNamespaceAndPath("warlockery", "/information/" + item), type,
                List.of(content), List.of(content), List.of(), text, null, List.of(), true));
        });
    }

    private static SlotContent item(String ingredient, int count) { return SlotContent.of(RecipeViewerIngredients.itemStacks(ingredient, count)); }
    private static void paragraph(MutableComponent text, Component paragraph) {
        if (!text.getString().isEmpty()) text.append("\n\n");
        text.append(paragraph);
    }
    private record Condition(boolean enabled, String key) { }
}
