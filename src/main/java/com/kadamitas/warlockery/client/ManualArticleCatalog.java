package com.kadamitas.warlockery.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.ritual.ChalkCircleLayout;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

final class ManualArticleCatalog {
    private static final String RITUAL_PREFIX = "rite_";
    private static final String BREW_PREFIX = "brew_entry_";
    private static final String BIOME_PREFIX = "biome_entry_";
    private static final String MACHINE_RECIPE_PREFIX = "machine_recipe_";

    private ManualArticleCatalog() {
    }

    static Article article(final ManualProfile manual, final String section) {
        if (section.startsWith(RITUAL_PREFIX)) {
            return ritual(section.substring(RITUAL_PREFIX.length()));
        }
        if (section.startsWith(BREW_PREFIX)) {
            return brew(section.substring(BREW_PREFIX.length()));
        }
        if (section.startsWith(BIOME_PREFIX)) {
            return biome(section.substring(BIOME_PREFIX.length()));
        }
        if (section.startsWith(MACHINE_RECIPE_PREFIX)) {
            return machineRecipe(section.substring(MACHINE_RECIPE_PREFIX.length()));
        }
        return new Article(
            Component.translatable(manual.translatedSectionKey(section)),
            Map.of(),
            manualPictograms(manual, section)
        );
    }

    private static Article biome(final String id) {
        return new Article(Component.translatable(
            "manual.warlockery.biome.entry",
            Component.translatable("biome.minecraft." + id)
        ), Map.of(), List.of());
    }

    private static Article machineRecipe(final String id) {
        final JsonObject recipe = resource("/data/warlockery/warlockery_machine/" + id + ".json");
        final MutableComponent body = Component.translatable(
            "manual.warlockery.machine_recipe.entry",
            humanize(recipe.get("machine").getAsString())
        ).copy();
        final JsonArray inputs = recipe.getAsJsonArray("inputs");
        appendIngredients(body, inputs);
        final java.util.List<String> workings = new java.util.ArrayList<>();
        final JsonArray outputs = recipe.getAsJsonArray("outputs");
        if (outputs != null) {
            java.util.stream.StreamSupport.stream(outputs.spliterator(), false).forEach(element -> {
                final JsonObject output = element.getAsJsonObject();
                final int count = output.has("count") ? output.get("count").getAsInt() : 1;
                workings.add("Produces " + count + "x " + ingredientName(output.get("item").getAsString()));
            });
        }
        if (recipe.has("processing_time")) {
            workings.add("Tends for " + decimal(recipe.get("processing_time").getAsInt() / 20.0F) + " seconds");
        }
        if (recipe.has("requires_fuel") && recipe.get("requires_fuel").getAsBoolean()) {
            workings.add("Requires fuel");
        }
        append(body, "manual.warlockery.entry.workings", workings);
        if (recipe.has("fluid")) {
            final JsonObject fluid = recipe.getAsJsonObject("fluid");
            append(body, "manual.warlockery.entry.fluid", java.util.List.of(
                fluid.get("amount").getAsString() + " mB " + ingredientName(fluid.get("ingredient").getAsString())
            ));
        }
        return new Article(body, Map.of(), pictograms(inputs));
    }

    private static Article ritual(final String id) {
        final JsonObject ritual = resource("/data/warlockery/ritual/" + id + ".json");
        final MutableComponent body = Component.translatable(ritual.get("description").getAsString()).copy();
        final Map<String, Integer> glyphs = ChalkCircleLayout.canonicalGlyphs(
            integers(ritual.getAsJsonObject("glyphs"))
        );
        if (ritual.has("power")) {
            append(body, "manual.warlockery.entry.altar_power", java.util.List.of(ritual.get("power").getAsString()));
        }
        final JsonObject requirements = ritual.has("requirements")
            ? ritual.getAsJsonObject("requirements")
            : new JsonObject();
        final JsonArray ingredients = requirements.getAsJsonArray("ingredients");
        final JsonArray entities = requirements.getAsJsonArray("entities");
        appendIngredients(body, ingredients);
        appendEntities(body, entities);
        appendConditions(body, ritual, requirements);
        if ("glyph_transform".equals(ritual.get("action").getAsString())) {
            body.append("\n").append(Component.translatable("manual.warlockery.glyph_transform.sizes"));
        }
        return new Article(body, glyphs, ritualPictograms(ritual, requirements, ingredients, entities));
    }

    private static Article brew(final String id) {
        final BrewKind kind = BrewKind.require(id);
        final MutableComponent body = Component.translatable("manual.warlockery.brew.effect_intro").copy();
        final java.util.List<String> workings = java.util.stream.Stream.concat(
            kind.effects().stream().map(effect -> effectName(effect.effect()) + " " + roman(effect.amplifier() + 1)
                + " (" + Math.max(1, effect.duration() / 20) + "s)"),
            kind.behaviors().stream().map(behavior -> humanize(behavior.id()))
        ).toList();
        append(body, "manual.warlockery.entry.workings", workings);
        body.append("\n");
        body.append(Component.translatable("manual.warlockery.brew.reach", decimal(kind.radius()), decimal(kind.potency())));
        final JsonObject recipe = resource("/data/warlockery/warlockery_machine/kettle_brew_" + id + ".json");
        final JsonArray inputs = recipe.getAsJsonArray("inputs");
        appendIngredients(body, inputs);
        if (recipe.has("fluid")) {
            final JsonObject fluid = recipe.getAsJsonObject("fluid");
            append(body, "manual.warlockery.entry.fluid", java.util.List.of(
                fluid.get("amount").getAsString() + " mB " + ingredientName(fluid.get("ingredient").getAsString())
            ));
        }
        if (recipe.has("altar_power") && recipe.get("altar_power").getAsInt() > 0) {
            append(body, "manual.warlockery.entry.altar_power", java.util.List.of(recipe.get("altar_power").getAsString()));
        }
        return new Article(body, Map.of(), pictograms(inputs));
    }

    private static List<Pictogram> ritualPictograms(
        final JsonObject ritual,
        final JsonObject requirements,
        final JsonArray ingredients,
        final JsonArray entities
    ) {
        return java.util.stream.Stream.concat(
            java.util.stream.Stream.concat(
                java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(picture("warlockery:ritual_knife", 1)),
                    conditionPictograms(ritual, requirements).stream()
                ),
                pictograms(ingredients).stream()
            ),
            entityPictograms(entities).stream()
        ).toList();
    }

    private static List<Pictogram> conditionPictograms(
        final JsonObject ritual,
        final JsonObject requirements
    ) {
        final java.util.ArrayList<Pictogram> pictures = new java.util.ArrayList<>();
        if ((ritual.has("night_only") && ritual.get("night_only").getAsBoolean())
            || (requirements.has("full_moon") && requirements.get("full_moon").getAsBoolean())) {
            pictures.add(picture("minecraft:clock", 1));
        }
        if (requirements.has("day_only") && requirements.get("day_only").getAsBoolean()) {
            pictures.add(picture("minecraft:sunflower", 1));
        }
        if (requirements.has("raining") && requirements.get("raining").getAsBoolean()) {
            pictures.add(picture("minecraft:water_bucket", 1));
        }
        if (requirements.has("thundering") && requirements.get("thundering").getAsBoolean()) {
            pictures.add(picture("minecraft:lightning_rod", 1));
        }
        return List.copyOf(pictures);
    }

    private static List<Pictogram> pictograms(final JsonArray ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(ingredients.spliterator(), false)
            .map(element -> {
                final JsonObject ingredient = element.getAsJsonObject();
                final String raw = ingredient.get("ingredient").getAsString();
                final int count = ingredient.has("count") ? ingredient.get("count").getAsInt() : 1;
                return picture(representativeItem(raw), count);
            })
            .toList();
    }

    private static List<Pictogram> entityPictograms(final JsonArray entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(entities.spliterator(), false)
            .map(element -> {
                final JsonObject entity = element.getAsJsonObject();
                final String raw = entity.get("entity").getAsString();
                final int count = entity.has("count") ? entity.get("count").getAsInt() : 1;
                return picture(representativeEntity(raw), count);
            })
            .toList();
    }

    private static String representativeEntity(final String raw) {
        if (raw.startsWith("#warlockery:death_binding/")) {
            final String family = raw.substring(raw.lastIndexOf('/') + 1);
            final String singular = family.endsWith("s") ? family.substring(0, family.length() - 1) : family;
            return "warlockery:" + singular + "_spawn_egg";
        }
        final Identifier id = Identifier.tryParse(raw);
        return id == null
            ? "minecraft:egg"
            : id.getNamespace() + ":" + id.getPath() + "_spawn_egg";
    }

    private static List<Pictogram> manualPictograms(final ManualProfile manual, final String section) {
        if ("vampirebook".equals(manual.id())) {
            return immortalPictograms(section);
        }
        if (section.startsWith("fetish_")) {
            return List.of(picture(fetishItem(section), 1));
        }
        if (section.startsWith("plant_")) {
            return List.of(picture(plantItem(section.substring("plant_".length())), 1));
        }
        return List.of();
    }

    private static List<Pictogram> immortalPictograms(final String section) {
        return switch (section) {
            case "nami" -> List.of(
                picture("warlockery:nami_spawn_egg", 1),
                picture("warlockery:wedding_ring", 1)
            );
            case "blood_audience", "vampire_level_1" -> List.of(
                picture("warlockery:nami_spawn_egg", 1),
                picture("minecraft:clock", 1),
                picture("warlockery:ingredient_necro_stone", 1),
                picture("warlockery:ingredient_drop_of_luck", 1),
                picture("minecraft:wither_rose", 1),
                picture("minecraft:ghast_tear", 1)
            );
            case "vampire_level_2" -> List.of(
                picture("warlockery:ingredient_vbook_page", 1),
                picture("warlockery:glassgoblet", 1)
            );
            case "vampire_level_3" -> List.of(
                picture("warlockery:ingredient_vbook_page", 2),
                picture("minecraft:villager_spawn_egg", 5)
            );
            case "vampire_level_4" -> List.of(
                picture("warlockery:ingredient_vbook_page", 3),
                picture("minecraft:clock", 4)
            );
            case "vampire_level_5" -> List.of(
                picture("warlockery:ingredient_vbook_page", 4),
                picture("warlockery:sungrenade", 10)
            );
            case "vampire_level_6" -> List.of(
                picture("warlockery:ingredient_vbook_page", 5),
                picture("minecraft:blaze_spawn_egg", 20)
            );
            case "vampire_level_7" -> List.of(
                picture("warlockery:ingredient_vbook_page", 6),
                picture("warlockery:nami_spawn_egg", 1),
                picture("minecraft:poppy", 1)
            );
            case "vampire_level_8" -> List.of(
                picture("warlockery:ingredient_vbook_page", 7),
                picture("minecraft:bell", 4)
            );
            case "vampire_level_9" -> List.of(
                picture("warlockery:ingredient_vbook_page", 8),
                picture("minecraft:minecart", 5)
            );
            case "vampire_level_10" -> List.of(
                picture("warlockery:ingredient_vbook_page", 9),
                picture("warlockery:glassgoblet", 1),
                picture("warlockery:coffin", 1)
            );
            default -> List.of();
        };
    }

    private static String plantItem(final String id) {
        return switch (id) {
            case "artichoke" -> "warlockery:seedsartichoke";
            case "belladonna" -> "warlockery:seedsbelladonna";
            case "garlic" -> "warlockery:garlic";
            case "mandrake" -> "warlockery:seedsmandrake";
            case "dreamroot" -> "warlockery:seedsdreamroot";
            case "snowbell" -> "warlockery:seedssnowbell";
            case "wolfsbane" -> "warlockery:seedswolfsbane";
            case "wormwood" -> "warlockery:seedswormwood";
            case "somnian_cotton" -> "warlockery:somniancotton";
            case "leaping_lily" -> "warlockery:leapinglily";
            case "blood_rose" -> "warlockery:bloodrose";
            case "void_bramble" -> "warlockery:voidbramble";
            case "critter_snare" -> "warlockery:crittersnare";
            default -> "warlockery:" + id.replace("_", "");
        };
    }

    private static String fetishItem(final String section) {
        return switch (section) {
            case "fetish_scarecrow" -> "warlockery:scarecrow";
            case "fetish_trent_effigy" -> "warlockery:trent";
            case "fetish_alluring_skull" -> "warlockery:alluringskull";
            case "fetish_statue_goddess" -> "warlockery:statuegoddess";
            case "fetish_statue_worship" -> "warlockery:statueofworship";
            case "fetish_statue_broken_hexes" -> "warlockery:broken_hexes_statue";
            case "fetish_statue_occluded_summons" -> "warlockery:occluded_summons_statue";
            case "fetish_doll_shelf" -> "warlockery:doll_shelf";
            default -> "warlockery:" + section.substring("fetish_".length());
        };
    }

    private static String representativeItem(final String raw) {
        if (!raw.startsWith("#")) {
            return raw;
        }
        final String tag = raw.substring(1);
        if (tag.contains("ingots/gold")) {
            return "minecraft:gold_ingot";
        }
        if (tag.contains("ingots/iron")) {
            return "minecraft:iron_ingot";
        }
        if (tag.contains("ingots/silver")) {
            return "warlockery:silver_ingot";
        }
        if (tag.contains("rods/wooden")) {
            return "minecraft:stick";
        }
        if (tag.contains("logs")) {
            return "minecraft:oak_log";
        }
        if (tag.contains("sapling")) {
            return "minecraft:oak_sapling";
        }
        if (tag.contains("flower")) {
            return "minecraft:poppy";
        }
        if (tag.contains("leather")) {
            return "minecraft:leather";
        }
        return "minecraft:paper";
    }

    private static Pictogram picture(final String itemId, final int count) {
        return new Pictogram(itemId, count);
    }

    private static void appendIngredients(final MutableComponent body, final JsonArray ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return;
        }
        append(body, "manual.warlockery.entry.ingredients", java.util.stream.StreamSupport.stream(
            ingredients.spliterator(), false
        ).map(element -> {
            final JsonObject ingredient = element.getAsJsonObject();
            final int count = ingredient.has("count") ? ingredient.get("count").getAsInt() : 1;
            final boolean consumed = !ingredient.has("consume") || ingredient.get("consume").getAsBoolean();
            final String name = ingredientName(ingredient.get("ingredient").getAsString());
            return count + "x " + name + (consumed ? "" : " (kept)");
        }).toList());
    }

    private static void appendEntities(final MutableComponent body, final JsonArray entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        append(body, "manual.warlockery.entry.offerings", java.util.stream.StreamSupport.stream(
            entities.spliterator(), false
        ).map(element -> {
            final JsonObject entity = element.getAsJsonObject();
            final int count = entity.has("count") ? entity.get("count").getAsInt() : 1;
            return count + "x " + ingredientName(entity.get("entity").getAsString());
        }).toList());
    }

    private static void appendConditions(
        final MutableComponent body,
        final JsonObject ritual,
        final JsonObject requirements
    ) {
        final java.util.List<String> conditions = new java.util.ArrayList<>();
        if (ritual.has("night_only") && ritual.get("night_only").getAsBoolean()) {
            conditions.add("Night");
        }
        addCondition(requirements, conditions, "day_only", "Daylight");
        addCondition(requirements, conditions, "full_moon", "Full moon");
        addCondition(requirements, conditions, "raining", "Rain");
        addCondition(requirements, conditions, "thundering", "Thunderstorm");
        if (requirements.has("dimension") && !requirements.get("dimension").getAsString().isBlank()) {
            conditions.add(ingredientName(requirements.get("dimension").getAsString()));
        }
        if (requirements.has("minimum_players") && requirements.get("minimum_players").getAsInt() > 1) {
            conditions.add(requirements.get("minimum_players").getAsInt() + " participants");
        }
        append(body, "manual.warlockery.entry.conditions", conditions);
    }

    private static void addCondition(
        final JsonObject requirements,
        final java.util.List<String> conditions,
        final String key,
        final String name
    ) {
        if (requirements.has(key) && requirements.get(key).getAsBoolean()) {
            conditions.add(name);
        }
    }

    private static void append(final MutableComponent body, final String heading, final java.util.List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        body.append("\n\n");
        body.append(Component.translatable(heading).withStyle(ChatFormatting.DARK_PURPLE));
        body.append("\n" + String.join("; ", values));
    }

    private static String ingredientName(final String raw) {
        if (raw.startsWith("#")) {
            return humanizeTag(raw.substring(1));
        }
        final Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            return humanize(raw);
        }
        return BuiltInRegistries.ITEM.get(id)
            .map(holder -> Component.translatable(holder.value().getDescriptionId()).getString())
            .orElseGet(() -> humanize(raw));
    }

    private static String effectName(final String raw) {
        final Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            return humanize(raw);
        }
        return BuiltInRegistries.MOB_EFFECT.get(id)
            .map(holder -> Component.translatable(holder.value().getDescriptionId()).getString())
            .orElseGet(() -> humanize(raw));
    }

    private static String humanize(final String raw) {
        final String path = raw.substring(Math.max(raw.lastIndexOf(':'), raw.lastIndexOf('/')) + 1);
        final String words = path.replace('_', ' ').replace('-', ' ').strip();
        if (words.isEmpty()) {
            return raw;
        }
        return words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }

    private static String humanizeTag(final String raw) {
        final String path = raw.substring(raw.indexOf(':') + 1);
        final String[] parts = path.split("/");
        if (parts.length < 2) {
            return humanize(path);
        }
        final String category = parts[parts.length - 2];
        final String material = humanize(parts[parts.length - 1]);
        final String singular = category.endsWith("s") ? category.substring(0, category.length() - 1) : category;
        return material + " " + humanize(singular).toLowerCase(Locale.ROOT);
    }

    private static String roman(final int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> Integer.toString(value);
        };
    }

    private static String decimal(final float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static Map<String, Integer> integers(final JsonObject object) {
        if (object == null) {
            return Map.of();
        }
        final Map<String, Integer> values = new LinkedHashMap<>();
        object.entrySet().forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsInt()));
        return Map.copyOf(values);
    }

    private static JsonObject resource(final String path) {
        try (var stream = ManualArticleCatalog.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing manual source " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    record Article(Component body, Map<String, Integer> glyphs, List<Pictogram> pictograms) {
        Article {
            glyphs = Map.copyOf(glyphs);
            pictograms = List.copyOf(pictograms);
        }

        boolean hasDiagram() {
            return !glyphs.isEmpty();
        }

        boolean hasPictograms() {
            return !pictograms.isEmpty();
        }
    }

    record Pictogram(String itemId, int count) {
        Pictogram {
            if (Identifier.tryParse(itemId) == null || count < 1) {
                throw new IllegalArgumentException("Manual pictograms require an item and count");
            }
        }
    }
}
