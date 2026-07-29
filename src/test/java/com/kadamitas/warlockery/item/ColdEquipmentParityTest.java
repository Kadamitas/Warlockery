package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ColdEquipmentParityTest {
    @Test
    void icySlippersUseTheFrozenHeartAndCommonString() throws IOException {
        final var recipe = JsonParser.parseString(Files.readString(
            Path.of("src/main/resources/data/warlockery/recipe/iceslippers.json")
        )).getAsJsonObject();
        final var key = recipe.getAsJsonObject("key");
        assertEquals("warlockery:ingredient_frozen_heart", key.get("H").getAsString());
        assertEquals("warlockery:ingredient_impregnated_leather", key.get("I").getAsString());
        assertEquals("warlockery:ingredient_diamond_vapour", key.get("D").getAsString());
        assertEquals("#c:strings", key.get("S").getAsString());
        assertEquals("warlockery:iceslippers", recipe.getAsJsonObject("result").get("id").getAsString());
        assertTrue(Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/registry/ModItems.java"
        )).contains("case \"iceslippers\" -> enchanted(equipmentProperties, Enchantments.FROST_WALKER, 2)"));
    }
}
