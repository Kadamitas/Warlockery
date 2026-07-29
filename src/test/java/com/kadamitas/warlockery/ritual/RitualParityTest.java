package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.testutil.JsonFixtureLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class RitualParityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "warlockery");
    private static final Map<String, RitualDefinition> RITUALS = JsonFixtureLoader
        .load(DATA.resolve("ritual"), RitualDefinition.CODEC)
        .stream()
        .collect(Collectors.toUnmodifiableMap(JsonFixtureLoader.Fixture::id, JsonFixtureLoader.Fixture::value));

    @Test
    void fertilityDeclaresTaggedGrowthAndDocumentedCures() {
        final RitualDefinition fertility = RITUALS.get("fertility");
        assertEquals(RitualAction.FERTILITY.id(), fertility.action());
        assertTrue(fertility.description().contains("poison"));
        assertTrue(fertility.description().contains("nausea"));
        assertTrue(fertility.description().contains("blindness"));
        assertTrue(tagContains("block", "ritual_growables", "#warlockery:ritual_crops"));
        assertTrue(tagContains("entity_type", "fertility_familiars", "warlockery:familiar_cat"));
    }

    @Test
    void naturesPowerUsesRepairableSoilAndVegetationContracts() {
        assertEquals(RitualAction.NATURES_POWER.id(), RITUALS.get("natures_power").action());
        assertTrue(tagContains("block", "nature_repairable_soils", "minecraft:coarse_dirt"));
        assertTrue(tagContains("block", "nature_damaged_vegetation", "minecraft:dead_bush"));
    }

    @Test
    void brokenEarthPlanIsDirectionalAndDeep() {
        assertEquals(RitualAction.BROKEN_EARTH.id(), RITUALS.get("part_earth").action());
        final var positions = RitualTerrainPlan.fissure(BlockPos.ZERO, Direction.NORTH, 6);
        assertTrue(positions.stream().allMatch(pos -> pos.getZ() < 0 && pos.getY() < 0));
        assertTrue(positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow() <= -12);
        assertTrue(positions.stream().mapToInt(BlockPos::getY).min().orElseThrow() <= -6);
        assertTrue(tagContains("block", "fissure_breakables", "#minecraft:overworld_carver_replaceables"));
    }

    @Test
    void earthsWrathRequiresExtensibleVolcanicFluid() {
        assertEquals(RitualAction.EARTHS_WRATH.id(), RITUALS.get("volcano").action());
        assertTrue(tagContains("fluid", "volcanic_fluids", "#minecraft:lava"));
        assertTrue(readJson(ASSETS.resolve("lang/en_us.json"))
            .has("screen.warlockery.ritual.requirement.nearby_volcanic_fluid"));
    }

    @Test
    void skysWrathDeclaresWeatherAndTargetedLightning() {
        final RitualDefinition storm = RITUALS.get("storm");
        assertEquals(RitualAction.SKYS_WRATH.id(), storm.action());
        assertEquals(RitualAction.Outcome.WEATHER_AND_EFFECT, RitualAction.require(storm.action()).outcome());
        assertTrue(storm.description().contains("target"));
    }

    @Test
    void hellOnEarthUsesTaggedDemonsAndContainedFire() {
        assertEquals(RitualAction.HELL_ON_EARTH.id(), RITUALS.get("hell_on_earth").action());
        assertTrue(tagContains("entity_type", "demons", "warlockery:demon"));
        assertTrue(tagContains("block", "controlled_fire_supports", "#warlockery:ritual_stones"));
        assertTrue(RitualTerrainPlan.fireRing(BlockPos.ZERO, 9, 12).size() >= 8);
    }

    @Test
    void forestationPlacesAConfiguredNumberOfTaggedSaplings() {
        final RitualDefinition forestation = RITUALS.get("forestation");
        assertEquals(RitualAction.FORESTATION.id(), forestation.action());
        assertEquals(12, forestation.count());
        assertTrue(tagContains("block", "ritual_saplings", "#minecraft:saplings"));
        assertTrue(RitualTerrainPlan.forestColumns(BlockPos.ZERO, forestation.radius()).size() >= forestation.count());
    }

    private static com.google.gson.JsonArray tagValues(final String registry, final String id) {
        final Path path = DATA.resolve("tags").resolve(registry).resolve(id + ".json");
        return readJson(path).getAsJsonArray("values");
    }

    private static JsonObject readJson(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static boolean tagContains(final String registry, final String id, final String value) {
        return java.util.stream.StreamSupport.stream(tagValues(registry, id).spliterator(), false)
            .anyMatch(element -> element.getAsString().equals(value));
    }
}
