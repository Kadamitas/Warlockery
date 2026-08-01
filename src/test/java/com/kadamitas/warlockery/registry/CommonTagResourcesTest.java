package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class CommonTagResourcesTest {
    private static final Path DATA = Path.of("src/main/resources/data");
    private static final Path LANG = Path.of("src/main/resources/assets/warlockery/lang");
    private static final List<String> LOCALES = List.of(
        "en_us", "fr_fr", "es_es", "pt_br", "de_de", "pl_pl", "ja_jp", "zh_tw"
    );
    private static final List<TagExpectation> METAL_TAGS = List.of(
        tag("c/tags/item/ingots/silver.json", "warlockery:silver_ingot"),
        tag("c/tags/item/raw_materials/silver.json", "warlockery:raw_silver"),
        tag("c/tags/item/dusts/silver.json", "warlockery:ingredient_silverdust"),
        tag("c/tags/item/ores/silver.json", "warlockery:silver_ore", "warlockery:deepslate_silver_ore"),
        tag("c/tags/item/storage_blocks/silver.json", "warlockery:silver_block"),
        tag("c/tags/item/storage_blocks/raw_silver.json", "warlockery:raw_silver_block"),
        tag("c/tags/item/ingots/delvealloy.json", "warlockery:ingredient_delvealloyingot"),
        tag("c/tags/item/raw_materials/delvealloy.json", "warlockery:raw_delvealloy"),
        tag("c/tags/item/dusts/delvealloy.json", "warlockery:ingredient_delvealloydust"),
        tag("c/tags/item/nuggets/delvealloy.json", "warlockery:ingredient_delvealloynugget"),
        tag("c/tags/item/ores/delvealloy.json", "warlockery:delvealloy_ore", "warlockery:deepslate_delvealloy_ore"),
        tag("c/tags/item/storage_blocks/delvealloy.json", "warlockery:delvealloy_block"),
        tag("c/tags/item/storage_blocks/raw_delvealloy.json", "warlockery:raw_delvealloy_block"),
        tag("c/tags/block/ores/silver.json", "warlockery:silver_ore", "warlockery:deepslate_silver_ore"),
        tag("c/tags/block/ores/delvealloy.json", "warlockery:delvealloy_ore", "warlockery:deepslate_delvealloy_ore"),
        tag("c/tags/block/storage_blocks/silver.json", "warlockery:silver_block"),
        tag("c/tags/block/storage_blocks/raw_silver.json", "warlockery:raw_silver_block"),
        tag("c/tags/block/storage_blocks/delvealloy.json", "warlockery:delvealloy_block"),
        tag("c/tags/block/storage_blocks/raw_delvealloy.json", "warlockery:raw_delvealloy_block")
    );

    @TestFactory
    Stream<DynamicTest> everyMetalFormPublishesCanonicalCommonTags() {
        return METAL_TAGS.stream().map(expectation -> DynamicTest.dynamicTest(
            expectation.path().toString(),
            () -> assertTag(expectation)
        ));
    }

    @Test
    void parentTagsExplicitlyAggregateCustomMaterialTags() {
        assertTag(tag("c/tags/item/ingots.json", "#c:ingots/silver", "#c:ingots/delvealloy"));
        assertTag(tag("c/tags/item/raw_materials.json", "#c:raw_materials/silver", "#c:raw_materials/delvealloy"));
        assertTag(tag("c/tags/item/dusts.json", "#c:dusts/silver", "#c:dusts/delvealloy"));
        assertTag(tag("c/tags/item/nuggets.json", "#c:nuggets/delvealloy"));
        assertTag(tag("c/tags/item/ores.json", "#c:ores/silver", "#c:ores/delvealloy"));
        assertTag(tag(
            "c/tags/item/storage_blocks.json",
            "#c:storage_blocks/silver",
            "#c:storage_blocks/raw_silver",
            "#c:storage_blocks/delvealloy",
            "#c:storage_blocks/raw_delvealloy"
        ));
        assertTag(tag("c/tags/block/ores.json", "#c:ores/silver", "#c:ores/delvealloy"));
    }

    @Test
    void hexwoodPublishesVanillaLogAndPlankEquivalence() {
        assertTag(tag("minecraft/tags/block/logs.json", "warlockery:hex_log"));
        assertTag(tag("minecraft/tags/item/logs.json", "warlockery:hex_log"));
        assertTag(tag("minecraft/tags/block/planks.json", "warlockery:hexwood"));
        assertTag(tag("minecraft/tags/item/planks.json", "warlockery:hexwood"));
    }

    @Test
    void equipmentPublishesVanillaAndCommonBehaviorTags() {
        assertTag(tag("minecraft/tags/item/head_armor.json", "warlockery:delvealloyhelm", "warlockery:witchhat"));
        assertTag(tag("minecraft/tags/item/chest_armor.json", "warlockery:deathsrobe", "warlockery:werewolf_hunter_coat"));
        assertTag(tag("minecraft/tags/item/leg_armor.json", "warlockery:forgewardens_girdle", "warlockery:vampirelegs"));
        assertTag(tag("minecraft/tags/item/foot_armor.json", "warlockery:iceslippers", "warlockery:vampireboots"));
        assertTag(tag("minecraft/tags/item/swords.json", "warlockery:ritual_knife", "warlockery:silversword"));
        assertTag(tag("minecraft/tags/item/spears.json", "warlockery:thorn_spear"));
        assertTag(tag("minecraft/tags/item/pickaxes.json", "warlockery:delvealloypickaxe"));
        assertTag(tag("c/tags/item/armors.json", "#c:armors/humanoid"));
        assertTag(tag("c/tags/item/armors/humanoid.json", "warlockery:delvealloyhelm", "warlockery:iceslippers"));
    }

    @Test
    void silverBoltsUseTheVanillaArrowFamilyWithoutAParallelCrossbow() {
        assertTag(tag("minecraft/tags/item/arrows.json", "warlockery:ingredient_bolt_silver"));
        assertTag(tag("warlockery/tags/item/silver_projectiles.json", "warlockery:ingredient_bolt_silver"));
        assertFalse(Files.exists(DATA.resolve("warlockery/tags/item/silver_repeater_bases.json")));
        assertFalse(Files.exists(DATA.resolve("warlockery/recipe/silver_repeater.json")));
    }

    @Test
    void ritualTargetFamiliesAreDataPackExtensible() {
        assertTag(tag("warlockery/tags/block/ritual_ores.json", "#c:ores"));
        assertTag(tag("warlockery/tags/block/ritual_crops.json", "#minecraft:crops"));
        assertTag(tag("warlockery/tags/block/ritual_logs.json", "#minecraft:logs"));
        assertTag(tag("warlockery/tags/block/ritual_leaves.json", "#minecraft:leaves"));
        assertTag(tag("warlockery/tags/block/ritual_saplings.json", "#minecraft:saplings"));
        assertTag(tag("warlockery/tags/entity_type/demons.json", "warlockery:demon", "warlockery:imp"));
        assertTag(tag("warlockery/tags/entity_type/vampires.json", "warlockery:vampire"));
        assertTag(tag("warlockery/tags/entity_type/werewolves.json", "warlockery:werewolf"));
        assertTag(tag("warlockery/tags/entity_type/ritual_beasts.json", "#minecraft:followable_friendly_mobs"));
        assertTrue(readString(DATA.resolve("warlockery/ritual/call_beasts.json")).contains("\"action\": \"call_beasts\""));
    }

    @Test
    void recipesUseFabricConventionalTagsWithoutLegacyForgeIngredients() throws IOException {
        try (var paths = Files.walk(DATA.resolve("warlockery"))) {
            final List<Path> jsonFiles = paths.filter(path -> path.toString().endsWith(".json")).toList();
            assertTrue(jsonFiles.stream().noneMatch(path -> readString(path).contains("#forge:")));
        }
        assertTrue(readString(DATA.resolve("warlockery/recipe/alchemical_oven.json"))
            .contains("#c:player_workstations/furnaces"));
        assertTrue(readString(DATA.resolve("warlockery/warlockery_machine/oven_logs.json"))
            .contains("#minecraft:logs"));
        assertTrue(readString(DATA.resolve("warlockery/warlockery_machine/spin_wool.json"))
            .contains("#c:strings"));
    }

    @Test
    void legacyForgeNamespaceTagResourcesAreGone() throws IOException {
        final Path legacy = DATA.resolve("forge/tags");
        if (!Files.exists(legacy)) {
            return;
        }
        try (var paths = Files.walk(legacy)) {
            assertEquals(0, paths.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void publicItemTagsHaveRecipeViewerNamesInEverySupportedLocale() throws IOException {
        final List<String> keys = Stream.of("c", "warlockery").flatMap(namespace -> {
            final Path root = DATA.resolve(namespace).resolve("tags/item");
            try {
                return Files.walk(root)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(path -> "tag.item." + namespace + "." + root.relativize(path).toString()
                        .replace('\\', '.')
                        .replace('/', '.')
                        .replaceFirst("\\.json$", ""));
            } catch (IOException exception) {
                throw new UncheckedIOException(root.toString(), exception);
            }
        }).sorted().toList();

        assertFalse(keys.isEmpty());
        for (String locale : LOCALES) {
            final JsonObject translations = readJson(LANG.resolve(locale + ".json"));
            keys.forEach(key -> {
                assertTrue(translations.has(key), () -> locale + " lacks " + key);
                assertFalse(translations.get(key).getAsString().isBlank(), () -> locale + " has a blank " + key);
            });
        }
    }

    private static void assertTag(final TagExpectation expectation) {
        final JsonObject json = readJson(DATA.resolve(expectation.path()));
        assertFalse(json.get("replace").getAsBoolean());
        final Set<String> values = json.getAsJsonArray("values").asList().stream()
            .filter(value -> value.isJsonPrimitive())
            .map(value -> value.getAsString())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertTrue(values.containsAll(expectation.values()), () -> expectation.path() + " lacks " + expectation.values());
    }

    private static JsonObject readJson(final Path path) {
        return JsonParser.parseString(readString(path)).getAsJsonObject();
    }

    private static String readString(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static TagExpectation tag(final String path, final String... values) {
        return new TagExpectation(Path.of(path), Set.of(values));
    }

    private record TagExpectation(Path path, Set<String> values) {
    }
}
