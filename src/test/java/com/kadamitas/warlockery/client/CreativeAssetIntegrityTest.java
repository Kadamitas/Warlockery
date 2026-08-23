package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class CreativeAssetIntegrityTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path ITEM_DEFINITIONS = ASSETS.resolve("items");
    private static final Path ITEM_MODELS = ASSETS.resolve("models/item");
    private static final Path BLOCK_MODELS = ASSETS.resolve("models/block");
    private static final Path BLOCK_STATES = ASSETS.resolve("blockstates");
    private static final Path TEXTURES = ASSETS.resolve("textures");
    private static final Set<String> SCULPTED_MACHINE_MODELS = Set.of(
        "daylightcollector",
        "distilleryburning",
        "distilleryidle",
        "doll_shelf",
        "filteredfumefunnel",
        "fumefunnel"
    );
    private static final Map<String, String> VANILLA_STYLE_BLOCK_ITEM_MODELS = Map.ofEntries(
        blockItem("abyssal_stone"),
        blockItem("alder_leaves"),
        blockItem("alder_log"),
        blockItem("alder_planks"),
        blockItem("bloodedwool"),
        blockItem("deepslate_delvealloy_ore"),
        blockItem("deepslate_silver_ore"),
        blockItem("delvealloy_block"),
        blockItem("delvealloy_ore"),
        blockItem("hawthorn_leaves"),
        blockItem("hawthorn_log"),
        blockItem("hawthorn_planks"),
        blockItem("hex_ladder"),
        blockItem("hex_leaves"),
        blockItem("hex_log"),
        blockItem("hexwood"),
        blockItem("hexwoodslab"),
        Map.entry("icefence", "warlockery:block/icefence_inventory"),
        blockItem("icefencegate"),
        blockItem("icepressureplate"),
        blockItem("iceslab"),
        blockItem("icestairs"),
        Map.entry("icestockade", "warlockery:block/icestockade_inventory"),
        blockItem("perpetualice"),
        blockItem("pitdirt"),
        blockItem("pitgrass"),
        blockItem("raw_delvealloy_block"),
        blockItem("raw_silver_block"),
        blockItem("rowan_leaves"),
        blockItem("rowan_log"),
        blockItem("rowan_planks"),
        blockItem("shadedglass"),
        blockItem("shadedglass_active"),
        blockItem("silver_block"),
        blockItem("silver_ore"),
        blockItem("snowpressureplate"),
        blockItem("snowslab"),
        blockItem("snowstairs"),
        blockItem("stairswoodalder"),
        blockItem("stairswoodhawthorn"),
        blockItem("stairswoodrowan"),
        blockItem("stockade"),
        blockItem("web"),
        blockItem("wickerbundle")
    );
    @Test
    void everyCreativeDefinitionResolvesItsModelAndWarlockeryTextures() throws IOException {
        final List<Path> definitions = jsonFiles(ITEM_DEFINITIONS);
        assertTrue(definitions.size() >= 500, "the complete creative inventory must remain modeled");
        for (Path definition : definitions) {
            final JsonObject model = json(definition).getAsJsonObject("model");
            assertNotNull(model, definition.toString());
            collectStrings(model)
                .filter(value -> value.startsWith("warlockery:item/"))
                .forEach(value -> assertTrue(Files.exists(modelPath(value)), value + " referenced by " + definition));
        }

        try (Stream<Path> models = Files.walk(ASSETS.resolve("models"))) {
            models.filter(path -> path.toString().endsWith(".json"))
                .forEach(model -> textureReferences(json(model))
                    .filter(value -> value.startsWith("warlockery:item/") || value.startsWith("warlockery:block/"))
                    .forEach(value -> assertTrue(Files.exists(texturePath(value)), value + " referenced by " + model)));
        }
    }

    @Test
    void creativeItemTexturesAreNotPlaceholderCopies() throws IOException {
        final Set<Path> referencedTextures;
        try (Stream<Path> models = Files.walk(ASSETS.resolve("models"))) {
            referencedTextures = models.filter(path -> path.toString().endsWith(".json"))
                .flatMap(path -> textureReferences(json(path)))
                .filter(value -> value.startsWith("warlockery:item/"))
                .map(CreativeAssetIntegrityTest::texturePath)
                .collect(Collectors.toUnmodifiableSet());
        }
        final Map<String, List<Path>> byDigest;
        try (Stream<Path> paths = Files.list(TEXTURES.resolve("item"))) {
            byDigest = paths.filter(path -> path.toString().endsWith(".png"))
                .filter(referencedTextures::contains)
                .collect(Collectors.groupingBy(CreativeAssetIntegrityTest::sha256));
        }
        final List<List<Path>> duplicates = byDigest.values().stream()
            .filter(group -> group.size() > 1)
            .toList();
        assertTrue(duplicates.isEmpty(), () -> "creative textures share placeholder images: " + duplicates);

        final String altar = "warlockery:block/altar";
        try (Stream<Path> models = Files.walk(BLOCK_MODELS)) {
            final List<Path> copiedAltars = models.filter(path -> path.toString().endsWith(".json"))
                .filter(Predicate.not(path -> path.getFileName().toString().equals("altar.json")))
                .filter(path -> textureReferences(json(path)).anyMatch(altar::equals))
                .toList();
            assertTrue(copiedAltars.isEmpty(), () -> "models still borrowing the altar texture: " + copiedAltars);
        }
    }

    @Test
    void spawnEggsKeepAnEggSilhouetteAndUseIndividualMobPalettes() throws IOException {
        final List<Path> eggs;
        try (Stream<Path> paths = Files.list(TEXTURES.resolve("item"))) {
            eggs = paths.filter(path -> path.getFileName().toString().endsWith("_spawn_egg.png")).toList();
        }
        assertEquals(48, eggs.size());
        for (Path egg : eggs) {
            final String id = egg.getFileName().toString().replaceFirst("\\.png$", "");
            final JsonObject definition = json(ITEM_DEFINITIONS.resolve(id + ".json")).getAsJsonObject("model");
            assertEquals("warlockery:item/" + id, definition.get("model").getAsString(), id);
            final JsonObject model = json(ITEM_MODELS.resolve(id + ".json"));
            assertEquals("warlockery:item/" + id, model.getAsJsonObject("textures").get("layer0").getAsString(), id);

            final BufferedImage image = image(egg);
            final int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            assertTrue(Arrays.stream(pixels).anyMatch(pixel -> pixel >>> 24 == 0), id + " needs a transparent outline");
            assertTrue(Arrays.stream(pixels).anyMatch(pixel -> pixel >>> 24 > 0), id + " needs opaque egg pixels");
            final long colors = Arrays.stream(pixels).filter(pixel -> pixel >>> 24 > 0).distinct().count();
            assertTrue(colors >= 4, id + " needs a readable mob palette");
        }
        assertEquals(eggs.size(), eggs.stream().map(CreativeAssetIntegrityTest::sha256).distinct().count());
    }

    @Test
    void doorsGrassAndStatuesUsePurposeBuiltModels() {
        for (String door : List.of("alderwooddoor", "rowanwooddoor", "icedoor")) {
            final JsonObject itemModel = json(ITEM_MODELS.resolve(door + ".json"));
            assertEquals("minecraft:item/generated", itemModel.get("parent").getAsString());
            assertEquals("warlockery:item/" + door, itemModel.getAsJsonObject("textures").get("layer0").getAsString());
            assertTrue(Files.exists(TEXTURES.resolve("block/" + door + "_bottom.png")));
            assertTrue(Files.exists(TEXTURES.resolve("block/" + door + "_top.png")));
        }
        assertEquals("minecraft:block/cube_bottom_top", json(BLOCK_MODELS.resolve("pitgrass.json"))
            .get("parent").getAsString());
        for (String statue : List.of("broken_hexes_statue", "occluded_summons_statue")) {
            assertTrue(json(BLOCK_MODELS.resolve(statue + ".json")).getAsJsonArray("elements").size() >= 3, statue);
        }
    }

    @Test
    void placeableContentUsesPurposeBuiltInventoryModels() throws IOException {
        final List<Path> definitions = jsonFiles(ITEM_DEFINITIONS).stream()
            .filter(path -> Files.isRegularFile(BLOCK_STATES.resolve(path.getFileName())))
            .toList();
        assertTrue(definitions.size() >= 100, "the complete placeable catalog needs inventory art");
        for (Path definition : definitions) {
            final String id = definition.getFileName().toString().replaceFirst("\\.json$", "");
            final JsonObject model = json(ITEM_MODELS.resolve(id + ".json"));
            final Path blockModel = BLOCK_MODELS.resolve(id + ".json");
            if (id.equals("demonheart")) {
                assertEquals("minecraft:item/generated", model.get("parent").getAsString());
                assertEquals("warlockery:item/demonheart", model.getAsJsonObject("textures").get("layer0").getAsString());
                assertTrue(Files.isRegularFile(TEXTURES.resolve("item/demonheart.png")));
                continue;
            }
            if (Files.isRegularFile(blockModel) && json(blockModel).has("elements")) {
                assertEquals("warlockery:block/" + id, model.get("parent").getAsString(), id);
                continue;
            }
            if (VANILLA_STYLE_BLOCK_ITEM_MODELS.containsKey(id)) {
                assertEquals(VANILLA_STYLE_BLOCK_ITEM_MODELS.get(id), model.get("parent").getAsString(), id);
                continue;
            }
            assertEquals("minecraft:item/generated", model.get("parent").getAsString(), id);
            assertEquals("warlockery:item/" + id, model.getAsJsonObject("textures").get("layer0").getAsString(), id);
            final Path texture = TEXTURES.resolve("item/" + id + ".png");
            assertTrue(Files.isRegularFile(texture), id + " needs its own inventory sprite");
            final BufferedImage image = image(texture);
            final int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            assertTrue(Arrays.stream(pixels).anyMatch(pixel -> pixel >>> 24 == 0), id + " icon needs transparency");
            assertTrue(Arrays.stream(pixels).anyMatch(pixel -> pixel >>> 24 > 0), id + " icon needs visible pixels");
        }
    }

    @Test
    void machinesAndDollShelfUseTheirDistinctSculptedBlockModelsInInventory() {
        SCULPTED_MACHINE_MODELS.forEach(id -> {
            final JsonObject itemModel = json(ITEM_MODELS.resolve(id + ".json"));
            assertEquals("warlockery:block/" + id, itemModel.get("parent").getAsString(), id);
            assertTrue(Files.isRegularFile(BLOCK_MODELS.resolve(id + ".json")), id);
        });

        assertEquals("minecraft:block/oak_planks",
            json(BLOCK_MODELS.resolve("doll_shelf.json")).getAsJsonObject("textures").get("wood").getAsString());
        final Set<Set<String>> machineTextures = Set.of(
            "daylightcollector",
            "distilleryidle",
            "fumefunnel",
            "filteredfumefunnel"
        ).stream()
            .map(id -> textureReferences(json(BLOCK_MODELS.resolve(id + ".json")))
                .collect(Collectors.toSet()))
            .collect(Collectors.toSet());
        assertEquals(4, machineTextures.size(), "machine block models need distinct texture palettes");
    }

    @Test
    void vanillaShapedBuildingBlocksRenderTheirBlockGeometryInInventory() {
        VANILLA_STYLE_BLOCK_ITEM_MODELS.forEach((id, parent) -> {
            final JsonObject itemModel = json(ITEM_MODELS.resolve(id + ".json"));
            assertEquals(parent, itemModel.get("parent").getAsString(), id);
            final String blockModel = parent.substring("warlockery:block/".length());
            assertTrue(Files.isRegularFile(BLOCK_MODELS.resolve(blockModel + ".json")), id);
        });
    }

    @Test
    void ornamentalAndFunctionalFixturesHaveSculptedGeometry() {
        final Map<String, Integer> minimumElements = Map.ofEntries(
            Map.entry("altar", 9),
            Map.entry("alluringskull", 6),
            Map.entry("bloodcrucible", 8),
            Map.entry("brazier", 8),
            Map.entry("candelabra", 9),
            Map.entry("cauldron", 10),
            Map.entry("chalice", 7),
            Map.entry("crystalball", 5),
            Map.entry("daylightcollector", 6),
            Map.entry("demonheart", 6),
            Map.entry("distilleryidle", 8),
            Map.entry("doll_shelf", 8),
            Map.entry("dreamcatcher", 9),
            Map.entry("filteredfumefunnel", 4),
            Map.entry("glowglobe", 6),
            Map.entry("kettle", 10),
            Map.entry("mirrorblock", 6),
            Map.entry("mirrorwall", 6),
            Map.entry("paradox_egg", 6),
            Map.entry("scarecrow", 8),
            Map.entry("silvervat", 7),
            Map.entry("spinningwheel", 8),
            Map.entry("spiritportal", 7),
            Map.entry("statuegoddess", 9),
            Map.entry("statueofworship", 11),
            Map.entry("trent", 7),
            Map.entry("voidbramble", 5),
            Map.entry("web", 4),
            Map.entry("wolfaltar", 8),
            Map.entry("wolfhead", 7),
            Map.entry("wolftrap", 12)
        );
        minimumElements.forEach((id, count) -> {
            final JsonObject model = json(BLOCK_MODELS.resolve(id + ".json"));
            assertFalse(model.has("parent") && "minecraft:block/cube_all".equals(model.get("parent").getAsString()), id);
            assertTrue(model.getAsJsonArray("elements").size() >= count, id);
        });
        assertEquals("warlockery:block/brazier_lit", json(BLOCK_STATES.resolve("brazier.json"))
            .getAsJsonObject("variants").getAsJsonObject("lit=true").get("model").getAsString());
    }

    @Test
    void lycansAndGoblinsHaveDistinctClothedSkins() {
        final Map<String, int[]> clothed = Map.of(
            "lycan_villager", new int[]{64, 64},
            "goblin", new int[]{128, 128},
            "hobgoblin", new int[]{192, 128},
            "stonebroker", new int[]{192, 160},
            "forgewarden", new int[]{192, 160}
        );
        final List<Path> skins = clothed.keySet().stream()
            .map(id -> TEXTURES.resolve("entity/" + id + ".png"))
            .toList();
        clothed.forEach((id, dimensions) -> {
            final Path path = TEXTURES.resolve("entity/" + id + ".png");
            assertTrue(Files.exists(path), path.toString());
            final BufferedImage image = image(path);
            assertEquals(dimensions[0], image.getWidth(), path.toString());
            assertEquals(dimensions[1], image.getHeight(), path.toString());
            final long colors = Arrays.stream(image.getRGB(
                0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()
            )).distinct().count();
            assertTrue(colors >= 8, path + " needs clothing and skin detail");
        });
        assertEquals(skins.size(), skins.stream().map(CreativeAssetIntegrityTest::sha256).distinct().count());
    }

    private static Map.Entry<String, String> blockItem(final String id) {
        return Map.entry(id, "warlockery:block/" + id);
    }

    private static List<Path> jsonFiles(final Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> path.toString().endsWith(".json")).toList();
        }
    }

    private static Stream<String> collectStrings(final JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return Stream.of(element.getAsString());
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().asList().stream().flatMap(CreativeAssetIntegrityTest::collectStrings);
        }
        if (element.isJsonObject()) {
            return element.getAsJsonObject().entrySet().stream()
                .flatMap(entry -> collectStrings(entry.getValue()));
        }
        return Stream.empty();
    }

    private static Stream<String> textureReferences(final JsonObject model) {
        return model.has("textures") ? collectStrings(model.get("textures")) : Stream.empty();
    }

    private static Path modelPath(final String value) {
        return ASSETS.resolve("models/" + value.substring("warlockery:".length()) + ".json");
    }

    private static Path texturePath(final String value) {
        return TEXTURES.resolve(value.substring("warlockery:".length()) + ".png");
    }

    private static JsonObject json(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static BufferedImage image(final Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static String sha256(final Path path) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
