package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class ModernBlockResourcesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/warlockery");
    private static final Path DATA = Path.of("src/main/resources/data");

    @TestFactory
    Stream<DynamicTest> everyShapedBlockHasCompleteClientAndLootResources() {
        return ModernBlockFactory.supportedIds().stream().sorted().map(id -> DynamicTest.dynamicTest(id, () -> {
            final JsonObject state = readJson(ASSETS.resolve("blockstates/" + id + ".json"));
            assertTrue(state.has("variants") || state.has("multipart"));
            assertTrue(Files.isRegularFile(ASSETS.resolve("models/block/" + id + ".json")));
            assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/" + id + ".json")));
            assertTrue(Files.isRegularFile(ASSETS.resolve("items/" + id + ".json")));
            assertTrue(Files.isRegularFile(DATA.resolve("warlockery/loot_table/blocks/" + id + ".json")));
        }));
    }

    @Test
    void stateVariantsCoverEveryModernShape() {
        assertEquals(32, variants("alderwooddoor"));
        assertEquals(2, variants("icepressureplate"));
        assertEquals(16, variants("icefencegate"));
        assertEquals(3, variants("iceslab"));
        assertEquals(40, variants("icestairs"));
        assertEquals(4, variants("hex_ladder"));
        assertEquals(5, readJson(ASSETS.resolve("blockstates/icefence.json")).getAsJsonArray("multipart").size());
    }

    @Test
    void removedVanillaDuplicatesHaveNoResources() {
        Stream.of(
            "cbuttonstone", "cbuttonwood", "csnowpressureplate",
            "cstonepressureplate", "cwoodendoor", "cwoodpressureplate"
        ).forEach(id -> {
            assertTrue(Files.notExists(ASSETS.resolve("blockstates/" + id + ".json")), id);
            assertTrue(Files.notExists(ASSETS.resolve("items/" + id + ".json")), id);
        });
    }

    @Test
    void vanillaFamilyAndMiningTagsExposeTheShapes() {
        assertContains("minecraft/tags/block/doors.json", "warlockery:rowanwooddoor");
        assertContains("minecraft/tags/item/fences.json", "warlockery:stockade");
        assertContains("minecraft/tags/block/slabs.json", "warlockery:iceslab");
        assertContains("minecraft/tags/item/stairs.json", "warlockery:stairswoodhawthorn");
        assertContains("minecraft/tags/block/climbable.json", "warlockery:hex_ladder");
        assertContains("minecraft/tags/block/mineable/pickaxe.json", "warlockery:icefencegate");
        assertContains("minecraft/tags/block/mineable/axe.json", "warlockery:alderwooddoor");
        assertContains("minecraft/tags/block/mineable/shovel.json", "warlockery:snowstairs");
    }

    private static int variants(final String id) {
        return readJson(ASSETS.resolve("blockstates/" + id + ".json")).getAsJsonObject("variants").size();
    }

    private static void assertContains(final String path, final String id) {
        final var values = readJson(DATA.resolve(path)).getAsJsonArray("values");
        assertTrue(Stream.iterate(0, index -> index < values.size(), index -> index + 1)
            .map(values::get)
            .anyMatch(value -> id.equals(value.getAsString())), path + " lacks " + id);
    }

    private static JsonObject readJson(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException(path.toString(), exception);
        }
    }
}
