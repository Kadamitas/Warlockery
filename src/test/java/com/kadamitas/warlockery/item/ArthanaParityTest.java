package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.crafting.AltarRangeIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class ArthanaParityTest {
    private static final Path DATA = Path.of("src/main/resources/data/warlockery/tags");

    @Test
    void harvestRuntimeCoversEverySpecialistIngredientFamily() {
        assertEquals(Set.of(
            "ingredient_bat_wool",
            "ingredient_dog_tongue",
            "ingredient_owlets_wing",
            "ingredient_toe_of_frog",
            "ingredient_creeper_heart",
            "ingredient_spectral_dust"
        ), ArthanaHarvestRuntime.bonusDropItemIds());
        assertEquals(Set.of(
            "warlockery:arthana_bat_sources",
            "warlockery:arthana_dog_sources",
            "warlockery:arthana_owl_sources",
            "warlockery:arthana_frog_sources",
            "warlockery:arthana_heart_sources",
            "warlockery:arthana_spectral_sources"
        ), ArthanaHarvestRuntime.bonusSourceTagIds());
    }

    @Test
    void harvestChanceUsesLootingAndStaysBounded() {
        assertEquals(0.50F, ArthanaHarvestRuntime.chance(0.50F, 0));
        assertEquals(0.65F, ArthanaHarvestRuntime.chance(0.50F, 3));
        assertEquals(0.95F, ArthanaHarvestRuntime.chance(0.90F, 10));
        assertEquals(0.50F, ArthanaHarvestRuntime.chance(0.50F, -2));
    }

    @Test
    void rangeFocusDoublesAltarDistributionRange() {
        assertEquals(12, AltarRangeIndex.effectiveRange(12, false));
        assertEquals(24, AltarRangeIndex.effectiveRange(12, true));
        assertThrows(IllegalArgumentException.class, () -> AltarRangeIndex.effectiveRange(0, true));
    }

    @Test
    void arthanaAndAltarFocusAreDataPackExtensible() throws IOException {
        assertEquals(Set.of("warlockery:ritual_knife"), values("item/arthanas.json"));
        assertEquals(Set.of("#warlockery:arthanas"), values("item/altar_range_foci.json"));
        assertTrue(values("entity_type/arthana_bat_sources.json").contains("minecraft:bat"));
        assertTrue(values("entity_type/arthana_dog_sources.json").contains("minecraft:wolf"));
        assertTrue(values("entity_type/arthana_owl_sources.json").contains("warlockery:owl"));
        assertTrue(values("entity_type/arthana_frog_sources.json").contains("minecraft:frog"));
        assertTrue(values("entity_type/arthana_heart_sources.json").contains("minecraft:creeper"));
        assertTrue(values("entity_type/arthana_spectral_sources.json").contains("#minecraft:undead"));
    }

    private static Set<String> values(final String path) throws IOException {
        return JsonParser.parseString(Files.readString(DATA.resolve(path)))
            .getAsJsonObject()
            .getAsJsonArray("values")
            .asList()
            .stream()
            .map(JsonElement::getAsString)
            .collect(Collectors.toUnmodifiableSet());
    }
}
