package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class LegacyStructureRulesTest {
    @Test
    void wildernessLandmarksCycleDeterministically() {
        assertEquals(LegacyStructureRules.Landmark.STONE_CIRCLE, LegacyStructureRules.select(0));
        assertEquals(LegacyStructureRules.Landmark.STRAW_IDOL, LegacyStructureRules.select(1));
        assertEquals(LegacyStructureRules.Landmark.ABANDONED_SHACK, LegacyStructureRules.select(2));
        assertEquals(LegacyStructureRules.Landmark.ABANDONED_SHACK, LegacyStructureRules.select(-1));
    }

    @Test
    void landmarksRemainSparseAndOutsideVillages() {
        assertTrue(LegacyStructureRules.canGenerate(true, false, false, true));
        assertFalse(LegacyStructureRules.canGenerate(false, false, false, true));
        assertFalse(LegacyStructureRules.canGenerate(true, true, false, true));
        assertFalse(LegacyStructureRules.canGenerate(true, false, true, true));
        assertFalse(LegacyStructureRules.canGenerate(true, false, false, false));
        assertEquals(
            LegacyStructureRules.regionKey(0, 0),
            LegacyStructureRules.regionKey(127, 127)
        );
    }

    @Test
    void onlyUnboundStrawmenAttractLivingNearbyZombies() {
        assertTrue(LegacyStructureRules.attractsZombie(false, true, 24.0 * 24.0));
        assertFalse(LegacyStructureRules.attractsZombie(true, true, 1.0));
        assertFalse(LegacyStructureRules.attractsZombie(false, false, 1.0));
        assertFalse(LegacyStructureRules.attractsZombie(false, true, 24.0 * 24.0 + 1.0));
    }

    @Test
    void everyArchivedStructureHasAFunctionalModernMapping() {
        final Set<String> names = LegacyStructureRules.archivedMappings().stream()
            .map(LegacyStructureRules.Mapping::archivedFeature)
            .collect(Collectors.toSet());
        assertEquals(9, names.size());
        assertEquals(Set.of(
            "Stone circles",
            "Apothecary and shop",
            "Strawmen",
            "Abandoned shacks",
            "Hobgoblin huts",
            "Town Walls",
            "Town Keeps",
            "Village Book shoppes",
            "Village witch huts"
        ), names);
        assertTrue(LegacyStructureRules.archivedMappings().stream()
            .allMatch(mapping -> !mapping.modernImplementation().isBlank()));
    }
}
