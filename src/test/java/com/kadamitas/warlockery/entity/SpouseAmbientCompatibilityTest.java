package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SpouseAmbientCompatibilityTest {
    @Test
    void rawMeatSelectionUsesTheCanonicalCommonFoodTag() {
        assertEquals("c:foods/raw_meat", SpouseAmbientTags.COOKABLE_RAW_MEATS.location().toString());
    }

    @Test
    void furnaceDiscoveryUsesTheCanonicalCommonWorkstationTag() throws java.io.IOException {
        assertEquals("c:player_workstations/furnaces",
            SpouseAmbientTags.FURNACE_WORKSTATIONS.location().toString());
        final String runtime = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/entity/SpouseCookingMachine.java"
        ));
        assertTrue(runtime.contains("ForgeCapabilities.ITEM_HANDLER"));
        assertTrue(runtime.contains("RecipeType.SMELTING"));
    }
}
