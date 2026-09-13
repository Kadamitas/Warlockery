package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class BrambleColossusResourceTest {
    @Test void immutableAcquisitionAndLootResourcesRemainPresent() {
        var loot=readJson("src/main/resources/data/warlockery/loot_table/entities/bramble_colossus.json");
        assertEquals("minecraft:entity",loot.get("type").getAsString());
        assertEquals("minecraft:poppy",loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("warlockery:entities/bramble_colossus",loot.get("random_sequence").getAsString());
        var recipe=readJson("src/main/resources/data/warlockery/recipe/ingredient_bramble_colossus_seed.json");
        assertEquals("minecraft:crafting_shaped",recipe.get("type").getAsString());
        assertEquals("VRVMAEVTV",recipe.getAsJsonArray("pattern").get(0).getAsString()+recipe.getAsJsonArray("pattern").get(1).getAsString()+recipe.getAsJsonArray("pattern").get(2).getAsString());
        assertEquals("warlockery:ingredient_bramble_colossus_seed",recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(36, BrambleColossusEntity.BASE_MAX_HEALTH);
        assertEquals(7, BrambleColossusEntity.BASE_ATTACK_DAMAGE);
        assertEquals(.3, BrambleColossusEntity.BASE_MOVEMENT_SPEED);
    }
    private static com.google.gson.JsonObject readJson(String path) {
        try { return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject(); }
        catch (java.io.IOException failure) { throw new AssertionError(path,failure); }
    }
}
