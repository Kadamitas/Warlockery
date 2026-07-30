package com.kadamitas.warlockery.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.item.ManualProfile;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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

    private ManualArticleCatalog() {
    }

    static Article article(final ManualProfile manual, final String section) {
        if (section.startsWith(RITUAL_PREFIX)) {
            return ritual(section.substring(RITUAL_PREFIX.length()));
        }
        if (section.startsWith(BREW_PREFIX)) {
            return brew(section.substring(BREW_PREFIX.length()));
        }
        return new Article(Component.translatable(manual.translatedSectionKey(section)), Map.of());
    }

    private static Article ritual(final String id) {
        final JsonObject ritual = resource("/data/warlockery/ritual/" + id + ".json");
        final MutableComponent body = Component.translatable(ritual.get("description").getAsString()).copy();
        final Map<String, Integer> glyphs = integers(ritual.getAsJsonObject("glyphs"));
        append(body, "manual.warlockery.entry.glyphs", glyphs.entrySet().stream()
            .map(entry -> humanizedGlyph(entry.getKey()) + " x" + entry.getValue())
            .toList());
        if (ritual.has("power")) {
            append(body, "manual.warlockery.entry.altar_power", java.util.List.of(ritual.get("power").getAsString()));
        }
        final JsonObject requirements = ritual.has("requirements")
            ? ritual.getAsJsonObject("requirements")
            : new JsonObject();
        appendIngredients(body, requirements.getAsJsonArray("ingredients"));
        appendEntities(body, requirements.getAsJsonArray("entities"));
        appendConditions(body, ritual, requirements);
        return new Article(body, glyphs);
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
        appendIngredients(body, recipe.getAsJsonArray("inputs"));
        if (recipe.has("fluid")) {
            final JsonObject fluid = recipe.getAsJsonObject("fluid");
            append(body, "manual.warlockery.entry.fluid", java.util.List.of(
                fluid.get("amount").getAsString() + " mB " + ingredientName(fluid.get("ingredient").getAsString())
            ));
        }
        if (recipe.has("altar_power") && recipe.get("altar_power").getAsInt() > 0) {
            append(body, "manual.warlockery.entry.altar_power", java.util.List.of(recipe.get("altar_power").getAsString()));
        }
        return new Article(body, Map.of());
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

    private static String humanizedGlyph(final String id) {
        return switch (id) {
            case "circleglyphritual" -> "Ritual Chalk";
            case "circleglyphinfernal" -> "Infernal Chalk";
            case "circleglyph_veil" -> "Veil Chalk";
            default -> humanize(id);
        };
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

    record Article(Component body, Map<String, Integer> glyphs) {
        Article {
            glyphs = Map.copyOf(glyphs);
        }

        boolean hasDiagram() {
            return !glyphs.isEmpty();
        }
    }
}
