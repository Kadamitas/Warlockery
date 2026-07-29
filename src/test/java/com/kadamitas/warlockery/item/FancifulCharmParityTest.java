package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FancifulCharmParityTest {
    private static final Path DATA = Path.of("src/main/resources/data/warlockery");

    @Test
    void charmOnlyBlocksNightmareSideEffectsWhileCarried() {
        assertEquals(FancifulCharmRules.Outcome.NOT_NIGHTMARE, FancifulCharmRules.resolve(false, false));
        assertEquals(FancifulCharmRules.Outcome.SIDE_EFFECTS, FancifulCharmRules.resolve(true, false));
        assertEquals(FancifulCharmRules.Outcome.BLOCKED, FancifulCharmRules.resolve(true, true));
    }

    @Test
    void charmHasATagCompatibleSurvivalRecipe() throws IOException {
        final JsonObject recipe = read(DATA.resolve("recipe/ingredient_charm_disrupted_dreams.json"));
        assertEquals("#c:rods/wooden", recipe.getAsJsonObject("key").get("S").getAsString());
        assertEquals(
            "warlockery:ingredient_fanciful_thread",
            recipe.getAsJsonObject("key").get("F").getAsString()
        );
        assertEquals(
            "warlockery:ingredient_charm_disrupted_dreams",
            recipe.getAsJsonObject("result").get("id").getAsString()
        );
        final JsonObject tag = read(DATA.resolve("tags/item/nightmare_guard_charms.json"));
        assertTrue(tag.getAsJsonArray("values").asList().stream().anyMatch(value ->
            value.getAsString().equals("warlockery:ingredient_charm_disrupted_dreams")
        ));
    }

    private static JsonObject read(final Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
