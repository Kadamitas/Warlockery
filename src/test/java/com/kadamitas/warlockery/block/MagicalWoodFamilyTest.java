package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.registry.ContentCatalog;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class MagicalWoodFamilyTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path DATA = Path.of("src/main/resources/data");
    private static final Set<String> FAMILIES = Set.of("alder", "hawthorn", "rowan");
    private static final Set<String> PARTS = Set.of("log", "planks", "leaves", "sapling");
    private static final Map<String, String> OVEN_ESSENCES = Map.of(
        "alder", "warlockery:ingredient_reek_of_misfortune",
        "hawthorn", "warlockery:ingredient_odour_of_purity",
        "rowan", "warlockery:ingredient_whiff_of_magic"
    );

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void factoryAndCatalogExposeEveryStableWoodId() {
        final Set<String> expected = FAMILIES.stream()
            .flatMap(family -> PARTS.stream().map(part -> family + "_" + part))
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(expected, MagicalWoodBlockFactory.ids());
        final Set<String> catalog = ContentCatalog.BLOCKS.stream()
            .map(ContentCatalog::modernize)
            .filter(expected::contains)
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(expected, catalog);
    }

    @Test
    void factoryUsesModernVanillaWoodBlockMechanics() {
        final String source = readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/block/MagicalWoodBlockFactory.java"
        ));
        assertTrue(source.contains("new RotatedPillarBlock"));
        assertTrue(source.contains("new TintedParticleLeavesBlock"));
        assertTrue(source.contains("new SaplingBlock"));
        assertTrue(source.contains("definition.family().treeGrower()"));
    }

    @Test
    void eachSaplingTargetsItsConfiguredTreeFeature() {
        Arrays.stream(MagicalTreeFamily.values()).forEach(family -> {
            assertEquals("warlockery:" + family.id() + "_tree", family.configuredFeature().identifier().toString());
            assertTrue(MagicalTreeFamily.find(family.id()).isPresent());
        });
    }

    @Test
    void everyBlockHasBlockstateModelsItemDefinitionLootAndPixelTexture() {
        FAMILIES.forEach(family -> PARTS.forEach(part -> {
            final String id = family + "_" + part;
            assertFile(ASSETS.resolve("blockstates/" + id + ".json"));
            assertFile(ASSETS.resolve("models/block/" + id + ".json"));
            assertFile(ASSETS.resolve("models/item/" + id + ".json"));
            assertFile(ASSETS.resolve("items/" + id + ".json"));
            assertFile(DATA.resolve("warlockery/loot_table/blocks/" + id + ".json"));
            if ("log".equals(part)) {
                assertFile(ASSETS.resolve("models/block/" + id + "_horizontal.json"));
            }
            final Path texture = ASSETS.resolve("textures/block/" + id + ".png");
            final BufferedImage image = readImage(texture);
            assertEquals(16, image.getWidth(), texture::toString);
            assertEquals(16, image.getHeight(), texture::toString);
            final Set<Integer> visibleColors = pixels(image, pixel -> alpha(pixel) > 0);
            assertTrue(visibleColors.size() >= 2 && visibleColors.size() <= 7, texture::toString);
            if (Set.of("leaves", "sapling").contains(part)) {
                assertTrue(pixels(image, pixel -> alpha(pixel) == 0).size() > 0, texture::toString);
            } else {
                assertTrue(pixels(image, pixel -> alpha(pixel) < 255).isEmpty(), texture::toString);
            }
        }));
    }

    @Test
    void logsCraftIntoFourMatchingPlanks() {
        FAMILIES.forEach(family -> {
            final JsonObject recipe = readJson(DATA.resolve("warlockery/recipe/" + family + "_planks.json"));
            assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
            assertEquals("#warlockery:" + family + "_logs", recipe.getAsJsonArray("ingredients").get(0).getAsString());
            assertEquals("warlockery:" + family + "_planks", recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals(4, recipe.getAsJsonObject("result").get("count").getAsInt());
        });
    }

    @Test
    void magicalLogsProduceTheirDocumentedOvenEssences() {
        FAMILIES.forEach(family -> {
            final String recipeName = "oven_essence_" + family;
            assertTrue(recipeName.compareTo("oven_logs") < 0);
            final JsonObject recipe = readJson(DATA.resolve("warlockery/warlockery_machine/" + recipeName + ".json"));
            final String json = recipe.toString();
            assertTrue(json.contains("#warlockery:" + family + "_logs"), family);
            assertTrue(json.contains("warlockery:ingredient_clay_jar"), family);
            assertTrue(json.contains("warlockery:ingredient_ash_wood"), family);
            assertTrue(json.contains(OVEN_ESSENCES.get(family)), family);
            assertTrue(recipe.get("requires_fuel").getAsBoolean());
        });
    }

    @Test
    void configuredTreesGrowAndGenerateRenewablyInFamilyBiomes() {
        final String worldGeneration = readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/fabric/WarlockeryWorldGeneration.java"
        ));
        assertTrue(worldGeneration.contains("BiomeModifications.addFeature"));
        FAMILIES.forEach(family -> {
            final String configured = readString(DATA.resolve(
                "warlockery/worldgen/configured_feature/" + family + "_tree.json"
            ));
            assertTrue(configured.contains("warlockery:" + family + "_log"), family);
            assertTrue(configured.contains("warlockery:" + family + "_leaves"), family);
            final String placed = readString(DATA.resolve(
                "warlockery/worldgen/placed_feature/" + family + "_tree.json"
            ));
            assertTrue(placed.contains("warlockery:" + family + "_sapling"), family);
            assertTrue(placed.contains("minecraft:would_survive"), family);
            assertTrue(worldGeneration.contains(
                "addFeature(WarlockeryTags.Biomes.HAS_" + family.toUpperCase()
                    + "_TREES, \"" + family + "_tree\""
            ), family);
            final JsonObject biomes = readJson(DATA.resolve(
                "warlockery/tags/worldgen/biome/has_" + family + "_trees.json"
            ));
            assertTrue(!biomes.getAsJsonArray("values").isEmpty(), family);
        });
    }

    @Test
    void woodFamiliesPublishVanillaAndCommonCompatibilityTags() {
        FAMILIES.forEach(family -> {
            final String log = "warlockery:" + family + "_log";
            final String familyTag = "#warlockery:" + family + "_logs";
            assertTrue(tagValues("warlockery/tags/block/" + family + "_logs.json").contains(log));
            assertTrue(tagValues("warlockery/tags/item/" + family + "_logs.json").contains(log));
            assertTrue(tagValues("minecraft/tags/block/logs.json").contains(familyTag));
            assertTrue(tagValues("minecraft/tags/block/logs_that_burn.json").contains(familyTag));
            assertTrue(tagValues("minecraft/tags/item/logs.json").contains(familyTag));
            assertTrue(tagValues("minecraft/tags/item/logs_that_burn.json").contains(familyTag));
            assertTrue(tagValues("c/tags/block/natural_logs/overworld.json").contains(log));
            assertTrue(tagValues("c/tags/item/natural_logs/overworld.json").contains(log));
            assertTaggedBlockAndItem("planks", family + "_planks");
            assertTaggedBlockAndItem("leaves", family + "_leaves");
            assertTaggedBlockAndItem("saplings", family + "_sapling");
        });
    }

    @Test
    void everyFamilyHasClearEnglishNames() {
        final JsonObject translations = readJson(ASSETS.resolve("lang/en_us.json"));
        FAMILIES.forEach(family -> PARTS.forEach(part -> {
            final String key = "block.warlockery." + family + "_" + part;
            assertTrue(translations.has(key), key);
            assertTrue(!translations.get(key).getAsString().isBlank(), key);
        }));
    }

    private static void assertTaggedBlockAndItem(final String tag, final String id) {
        final String value = "warlockery:" + id;
        assertTrue(tagValues("minecraft/tags/block/" + tag + ".json").contains(value));
        assertTrue(tagValues("minecraft/tags/item/" + tag + ".json").contains(value));
    }

    private static Set<String> tagValues(final String relativePath) {
        return readJson(DATA.resolve(relativePath)).getAsJsonArray("values").asList().stream()
            .map(value -> value.getAsString())
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<Integer> pixels(final BufferedImage image, final Predicate<Integer> filter) {
        return IntStream.range(0, image.getWidth() * image.getHeight())
            .map(index -> image.getRGB(index % image.getWidth(), index / image.getWidth()))
            .boxed()
            .filter(filter)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static int alpha(final int pixel) {
        return pixel >>> 24;
    }

    private static BufferedImage readImage(final Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
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

    private static void assertFile(final Path path) {
        assertTrue(Files.isRegularFile(path), path::toString);
    }
}
