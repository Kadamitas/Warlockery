package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.testutil.JsonFixtureLoader;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class FoundationalRitualParityTest {
    private static final Map<String, RitualDefinition> RITUALS = JsonFixtureLoader.load(
        Path.of("src", "main", "resources", "data", "warlockery", "ritual"),
        RitualDefinition.CODEC
    ).stream().collect(Collectors.toUnmodifiableMap(JsonFixtureLoader.Fixture::id, JsonFixtureLoader.Fixture::value));

    @Test
    void beastialCallUsesTheFullLegacyOfferingAndPowerContract() {
        final RitualDefinition rite = RITUALS.get("call_beasts");
        assertEquals(6000, rite.power());
        assertEquals(64, rite.count());
        assertEquals(4, rite.requirements().minimumPlayers());
        assertEquals(6, rite.requirements().ingredients().size());
        assertEquals(ChalkCircleLayout.Size.LARGE, ChalkCircleLayout.rings(rite.glyphs()).getFirst().size());
    }

    @Test
    void shiftingSeasonsUsesARecordedBiomeAndLargeCircle() {
        final RitualDefinition rite = RITUALS.get("climate_change");
        assertEquals(15, rite.radius());
        assertEquals(5, rite.requirements().minimumPlayers());
        assertTrue(rite.requirements().ingredients().stream().anyMatch(ingredient ->
            ingredient.ingredient().equals("warlockery:biomenote") && !ingredient.consume()
        ));
        assertTrue(rite.requirements().ingredients().stream().anyMatch(ingredient ->
            ingredient.ingredient().equals("warlockery:ingredient_seer_stone") && !ingredient.consume()
        ));
    }

    @Test
    void icyExpansionCountsTheCasterAndTwoAdditionalCircleMages() {
        final RitualDefinition rite = RITUALS.get("ice_shell");
        assertEquals(3, rite.requirements().minimumPlayers());
        assertEquals("ice_sphere", rite.action());
    }

    @Test
    void movingEarthProvidesSmallMediumAndLargeCircleVariants() {
        assertEquals(8, RITUALS.get("raise_earth_small").glyphs().get("circleglyphritual"));
        assertEquals(12, RITUALS.get("raise_earth").glyphs().get("circleglyphritual"));
        assertEquals(16, RITUALS.get("raise_earth_large").glyphs().get("circleglyphritual"));
    }

    @Test
    void hellOnEarthRequiresItsSacrificeAndThreeCircleWorking() {
        final RitualDefinition rite = RITUALS.get("hell_on_earth");
        assertEquals(5000, rite.power());
        assertEquals(3, rite.glyphs().size());
        assertEquals("minecraft:overworld", rite.requirements().dimension());
        assertTrue(rite.nightOnly());
        assertTrue(rite.requirements().entities().stream().anyMatch(requirement ->
            requirement.entity().equals("minecraft:villager") && requirement.consume()
        ));
    }
}
