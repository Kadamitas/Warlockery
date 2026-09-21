package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Schema tests, not a replacement for real registered-block growth/ore GameTests. */
final class WorldgenResourceCodecTest {
    private static final Path WORLDGEN = Path.of("src/main/resources/data/warlockery/worldgen");
    private static final List<String> TREES = List.of("alder", "hawthorn", "rowan", "hex");

    @BeforeAll
    static void bootstrapVanillaCodecs() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void allFourTreesRetainIdsHeightsAndDecodeWithThe263FeatureCodec() {
        final Map<String, List<Integer>> heights = Map.of(
            "alder", List.of(4, 2, 0), "hawthorn", List.of(5, 2, 0),
            "rowan", List.of(6, 2, 1), "hex", List.of(4, 2, 0)
        );
        for (String tree : TREES) {
            JsonObject feature = resource("feature/" + tree + "_tree.json");
            assertFalse(Files.exists(WORLDGEN.resolve("configured_feature/" + tree + "_tree.json")));
            assertFalse(feature.has("config"));
            assertEquals("warlockery:" + tree + "_log", feature.getAsJsonObject("trunk_provider").get("id").getAsString());
            assertEquals("warlockery:" + tree + "_leaves", feature.getAsJsonObject("foliage_provider").get("id").getAsString());
            JsonObject trunk = feature.getAsJsonObject("trunk_placer");
            assertEquals(heights.get(tree), List.of(trunk.get("base_height").getAsInt(),
                trunk.get("height_rand_a").getAsInt(), trunk.get("height_rand_b").getAsInt()));
            assertEquals("minecraft:rule_based", feature.getAsJsonObject("below_trunk_provider").get("type").getAsString());
            assertInstanceOf(TreeFeature.class, Feature.DIRECT_CODEC.parse(JsonOps.INSTANCE, vanillaStandIns(feature)).getOrThrow());
        }
    }

    @Test
    void oreFeaturesRetainVeinSizesReplacementTargetsAndExposureRules() {
        for (String ore : List.of("silver", "delvealloy")) {
            JsonObject feature = resource("feature/" + ore + "_ore.json");
            assertFalse(Files.exists(WORLDGEN.resolve("configured_feature/" + ore + "_ore.json")));
            assertFalse(feature.has("config"));
            assertEquals(ore.equals("silver") ? 7 : 5, feature.get("size").getAsInt());
            assertEquals(ore.equals("silver") ? 0.0 : 0.25, feature.get("discard_chance_on_air_exposure").getAsDouble());
            var targets = feature.getAsJsonArray("targets");
            assertEquals(2, targets.size());
            assertEquals("warlockery:" + ore + "_ore", targets.get(0).getAsJsonObject().getAsJsonObject("state").get("id").getAsString());
            assertEquals("warlockery:deepslate_" + ore + "_ore", targets.get(1).getAsJsonObject().getAsJsonObject("state").get("id").getAsString());
            assertEquals("minecraft:stone_ore_replaceables", targets.get(0).getAsJsonObject().getAsJsonObject("target").get("tag").getAsString());
            assertEquals("minecraft:deepslate_ore_replaceables", targets.get(1).getAsJsonObject().getAsJsonObject("target").get("tag").getAsString());
            assertInstanceOf(OreFeature.class, Feature.DIRECT_CODEC.parse(JsonOps.INSTANCE, vanillaStandIns(feature)).getOrThrow());
        }
    }

    @Test
    void placedFeaturesKeepStableReferencesAndDecodeTheUnchangedPlacementRules() {
        final Map<String, Integer> frequency = Map.of(
            "alder_tree", 48, "hawthorn_tree", 56, "rowan_tree", 64, "silver_ore", 8, "delvealloy_ore", 4
        );
        for (var entry : frequency.entrySet()) {
            String id = entry.getKey();
            JsonObject placed = resource("placed_feature/" + id + ".json");
            assertEquals("warlockery:" + id, placed.get("feature").getAsString());
            JsonObject first = placed.getAsJsonArray("placement").get(0).getAsJsonObject();
            assertEquals(entry.getValue().intValue(), first.get(id.endsWith("_tree") ? "chance" : "count").getAsInt());
            // Unit bootstrap has no dynamic feature registry: inline the exact
            // referenced feature for schema validation; assert its stable ID above.
            placed.add("feature", resource("feature/" + id + ".json"));
            assertInstanceOf(PlacedFeature.class, PlacedFeature.DIRECT_CODEC.parse(JsonOps.INSTANCE, vanillaStandIns(placed)).getOrThrow());
        }
    }

    private static JsonObject resource(String path) {
        try {
            return JsonParser.parseString(Files.readString(WORLDGEN.resolve(path))).getAsJsonObject();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static JsonObject vanillaStandIns(JsonObject resource) {
        // Actual Warlockery IDs are checked separately. Only these block names
        // are replaced in the unit fixture, since vanilla bootstrap does not
        // register the mod's blocks. Every provider/predicate/config field is
        // still decoded by the actual Minecraft 26.3 codec.
        String json = resource.toString();
        for (String tree : TREES) {
            for (String part : List.of("log", "leaves", "sapling")) {
                json = json.replace("\"warlockery:" + tree + "_" + part + "\"", "\"minecraft:oak_" + part + "\"");
            }
        }
        for (String ore : List.of("silver", "delvealloy")) {
            json = json.replace("\"warlockery:" + ore + "_ore\"", "\"minecraft:iron_ore\"");
            json = json.replace("\"warlockery:deepslate_" + ore + "_ore\"", "\"minecraft:deepslate_iron_ore\"");
        }
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
