package com.kadamitas.warlockery.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
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

final class MobLootResourcesTest {
    private static final Path LOOT = Path.of(
        "src", "main", "resources", "data", "warlockery", "loot_table", "entities"
    );
    private static final Path ITEM_MODELS = Path.of(
        "src", "main", "resources", "assets", "warlockery", "items"
    );
    private static final List<DropExpectation> DOCUMENTED_DROPS = List.of(
        drop("abyssal_regent", "warlockery:demonheart", true),
        drop("banshee", "warlockery:ingredient_spectral_dust", true),
        drop("bramble_colossus", "minecraft:poppy", true),
        drop("circle_mage", "warlockery:arcane_focus", true),
        drop("death", "warlockery:deathshand", true),
        drop("demon", "warlockery:demonheart", true),
        drop("dreamroot", "warlockery:seedsdreamroot", true),
        drop("emberhorn_archfiend", "warlockery:archfiends_urn", true),
        drop("ent", "warlockery:ingredient_heartwood_splinter", true),
        drop("hedge_crone", "warlockery:hedge_crones_hat", false),
        drop("hellhound", "warlockery:ingredient_dog_tongue", true),
        drop("hex_bat", "warlockery:ingredient_bat_wool", true),
        drop("illusion_creeper", "warlockery:ingredient_creeper_heart", true),
        drop("mandrake", "warlockery:ingredient_mandrake_root", true),
        drop("nightmare", "warlockery:ingredient_mellifluous_hunger", true),
        drop("owl", "warlockery:ingredient_owlets_wing", true),
        drop("spectral_familiar", "warlockery:ingredient_spectral_dust", true),
        drop("spectre", "warlockery:ingredient_spectral_dust", true),
        drop("spirit", "warlockery:ingredient_subdued_spirit", false),
        drop("stonebroker", "warlockery:stonebrokers_quiver", false),
        drop("storm_simian", "minecraft:feather", true),
        drop("thorned_pursuer", "warlockery:thorn_spear", true),
        drop("toad", "warlockery:ingredient_toe_of_frog", true)
    );

    @TestFactory
    Stream<DynamicTest> everyDocumentedMobDropHasAnAcquisitionTable() {
        return DOCUMENTED_DROPS.stream().map(expectation -> DynamicTest.dynamicTest(
            expectation.entity() + " -> " + expectation.item(),
            () -> {
                final JsonObject table = read(expectation.path());
                assertTrue(itemNames(table).contains(expectation.item()));
                assertFalse(table.getAsJsonArray("pools").isEmpty());
                if (expectation.lootingAware()) {
                    assertTrue(Files.readString(expectation.path()).contains("minecraft:looting"));
                }
            }
        ));
    }

    @Test
    void everyWarlockeryDropResolvesToAnItemModel() throws IOException {
        try (var paths = Files.list(LOOT)) {
            final Set<String> drops = paths
                .filter(path -> path.toString().endsWith(".json"))
                .flatMap(path -> itemNames(read(path)).stream())
                .filter(name -> name.startsWith("warlockery:"))
                .map(name -> name.substring("warlockery:".length()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            assertTrue(drops.stream().allMatch(id -> Files.exists(ITEM_MODELS.resolve(id + ".json"))));
        }
    }

    @Test
    void acquisitionLootUsesNoLegacyForgeTags() throws IOException {
        try (var paths = Files.list(LOOT)) {
            assertTrue(paths
                .filter(path -> path.toString().endsWith(".json"))
                .noneMatch(path -> readString(path).contains("forge:")));
        }
    }

    private static Set<String> itemNames(final JsonElement root) {
        return descendants(root)
            .filter(JsonElement::isJsonObject)
            .map(JsonElement::getAsJsonObject)
            .filter(object -> object.has("type") && object.has("name"))
            .filter(object -> "minecraft:item".equals(object.get("type").getAsString()))
            .map(object -> object.get("name").getAsString())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Stream<JsonElement> descendants(final JsonElement element) {
        if (element.isJsonArray()) {
            return element.getAsJsonArray().asList().stream().flatMap(MobLootResourcesTest::descendants);
        }
        if (element.isJsonObject()) {
            return Stream.concat(
                Stream.of(element),
                element.getAsJsonObject().entrySet().stream().flatMap(entry -> descendants(entry.getValue()))
            );
        }
        return Stream.of(element);
    }

    private static JsonObject read(final Path path) {
        return JsonParser.parseString(readString(path)).getAsJsonObject();
    }

    private static String readString(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private static DropExpectation drop(final String entity, final String item, final boolean lootingAware) {
        return new DropExpectation(entity, item, lootingAware);
    }

    private record DropExpectation(String entity, String item, boolean lootingAware) {
        private Path path() {
            return LOOT.resolve(entity + ".json");
        }
    }
}
