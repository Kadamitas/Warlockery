package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.kadamitas.warlockery.registry.SilverMaterials;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.item.ToolMaterial;
import org.junit.jupiter.api.Test;

final class SilverWeaponParityTest {
    private static final Path DATA = Path.of("src/main/resources/data");

    @Test
    void silverEquipmentHasPracticalDurabilityAndRepairsFromCommonSilver() {
        assertEquals(ToolMaterial.GOLD.incorrectBlocksForDrops(), SilverMaterials.TOOL.incorrectBlocksForDrops());
        assertEquals(384, SilverMaterials.TOOL.durability());
        assertEquals(7.0F, SilverMaterials.TOOL.speed());
        assertEquals(2.0F, SilverMaterials.TOOL.attackDamageBonus());
        assertTrue(SilverMaterials.TOOL.enchantmentValue() < ToolMaterial.GOLD.enchantmentValue());
        assertEquals("c:ingots/silver", SilverMaterials.TOOL.repairItems().location().toString());
    }

    @Test
    void silverSwordRecipeUsesCommonSilverAndWoodenRodTags() throws IOException {
        final var recipe = JsonParser.parseString(Files.readString(
            DATA.resolve("warlockery/recipe/silversword.json")
        )).getAsJsonObject();
        final var key = recipe.getAsJsonObject("key");
        assertEquals("#c:ingots/silver", key.get("S").getAsString());
        assertEquals("#c:rods/wooden", key.get("T").getAsString());
        assertEquals("warlockery:silversword", recipe.getAsJsonObject("result").get("id").getAsString());
    }
}
