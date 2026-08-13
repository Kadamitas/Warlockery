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
        assertTrue(runtime.contains("ItemStorage.SIDED.find"));
        assertTrue(runtime.contains("Transaction.openOuter()"));
        assertTrue(runtime.contains("RecipeType.SMELTING"));
    }

    @Test
    void marriageRemainsExclusiveToPlayersAndNamiWhileNaamahHasNoSpouseHooks() throws java.io.IOException {
        final String rituals = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/ritual/RitualManager.java"
        ));
        assertTrue(rituals.contains(
            ".filter(target -> target instanceof ServerPlayer || target instanceof NamiEntity)"
        ));
        assertTrue(rituals.contains("} else if (partner instanceof NamiEntity nami) {"));
        assertTrue(rituals.contains("final Optional<NamiEntity> candidate = unmarriedNami("));
        assertTrue(rituals.contains(
            "nami -> nami.isAlive() && marriages.ownerForNami(nami.getUUID()).isEmpty()"
        ));

        final String naamah = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/entity/NaamahEntity.java"
        ));
        assertTrue(!naamah.contains("SpouseAmbientRuntime"));
        assertTrue(!naamah.contains("MarriageData"));
        assertTrue(!naamah.contains("acceptMarriage"));
    }
}
