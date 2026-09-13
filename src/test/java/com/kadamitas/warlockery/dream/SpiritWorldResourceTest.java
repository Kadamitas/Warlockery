package com.kadamitas.warlockery.dream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

final class SpiritWorldResourceTest {
    private static final Path DATA = Path.of("src/main/resources/data/warlockery");

    @Test
    void spiritWorldUsesAnOverworldGeneratorAndItsOwnFixedClock() throws IOException {
        final JsonObject dimension = json("dimension/spirit_world.json");
        assertEquals("warlockery:spirit_world", dimension.get("type").getAsString());
        assertEquals(
            "minecraft:overworld",
            dimension.getAsJsonObject("generator").get("settings").getAsString()
        );
        final JsonObject type = json("dimension_type/spirit_world.json");
        assertTrue(type.get("has_fixed_time").getAsBoolean());
        assertEquals("warlockery:spirit_world", type.get("default_clock").getAsString());
        assertTrue(Files.isRegularFile(DATA.resolve("world_clock/spirit_world.json")));
    }

    @Test
    void entryToolsAndCottonAreDistinctFromCuratedSpiritCreatureExports() throws IOException {
        final Set<String> carryIn = values("tags/item/spirit_world_carry_in.json");
        final Set<String> exports = values("tags/item/spirit_world_exports.json");
        assertEquals(Set.of("warlockery:ingredient_icy_needle", "warlockery:ingredient_verdant_catalyst",
            "warlockery:ingredient_verdant_catalyst_prime", "warlockery:somniancotton",
            "warlockery:ingredient_disturbed_cotton"), carryIn);
        final Set<String> expectedExports = new java.util.HashSet<>(carryIn);
        expectedExports.addAll(Set.of("warlockery:ingredient_subdued_spirit",
            "warlockery:ingredient_mellifluous_hunger", "minecraft:gunpowder",
            "minecraft:magma_cream", "warlockery:demonheart"));
        assertEquals(expectedExports, exports);
    }

    private static JsonObject json(final String relative) throws IOException {
        return JsonParser.parseString(Files.readString(DATA.resolve(relative))).getAsJsonObject();
    }

    private static Set<String> values(final String relative) throws IOException {
        return StreamSupport.stream(json(relative).getAsJsonArray("values").spliterator(), false)
            .map(value -> value.getAsString())
            .collect(Collectors.toUnmodifiableSet());
    }
}
