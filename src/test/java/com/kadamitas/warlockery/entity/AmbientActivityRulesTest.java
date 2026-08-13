package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.AmbientActivityProfile.ActivityType;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AmbientActivityRulesTest {
    @Test
    void catalogContainsAtLeastSevenDistinctMobSpecificActivities() {
        assertTrue(AmbientActivityProfile.all().size() >= 7);
        assertEquals(AmbientActivityProfile.all().size(), AmbientActivityProfile.all().stream()
            .map(AmbientActivityProfile::type).distinct().count());
        assertTrue(AmbientActivityProfile.forKind(CreatureKind.DEMON).stream()
            .anyMatch(profile -> profile.type() == ActivityType.WINTER_HEARTH));
        assertTrue(AmbientActivityProfile.forKind(CreatureKind.ENT).stream()
            .anyMatch(profile -> profile.type() == ActivityType.GROVE_TENDING));
        assertTrue(AmbientActivityProfile.forKind(CreatureKind.IMP).stream()
            .anyMatch(profile -> profile.type() == ActivityType.SHINY_CURIOSITY));
    }

    @Test
    void profilesAreRareCappedAndCooldownProtected() {
        AmbientActivityProfile.all().forEach(profile -> {
            assertTrue(profile.checkIntervalTicks() >= 100);
            assertTrue(profile.chanceDenominator() >= 3);
            assertTrue(profile.cooldownTicks() >= profile.checkIntervalTicks());
            assertTrue(profile.localChangeCap() <= 1);
        });
    }

    @Test
    void activityNeverStartsDuringCombatHazardOrInvalidState() {
        assertTrue(AmbientActivityRules.canStart(true, false, false, false, false));
        assertFalse(AmbientActivityRules.canStart(true, false, true, false, false));
        assertFalse(AmbientActivityRules.canStart(true, false, false, true, false));
        assertFalse(AmbientActivityRules.canStart(false, false, false, false, false));
        assertFalse(AmbientActivityRules.canStart(true, true, false, false, false));
        assertFalse(AmbientActivityRules.canStart(true, false, false, false, true));
    }

    @Test
    void timeAndClimateRulesAreReadableAndDeterministic() {
        assertTrue(AmbientActivityRules.isNight(13_000));
        assertTrue(AmbientActivityRules.isNight(23_000));
        assertFalse(AmbientActivityRules.isNight(6_000));
        assertTrue(AmbientActivityRules.isColdBiomeId("minecraft:snowy_taiga"));
        assertTrue(AmbientActivityRules.isColdBiomeId("minecraft:frozen_peaks"));
        assertFalse(AmbientActivityRules.isColdBiomeId("minecraft:desert"));
        assertEquals(
            AmbientActivityRules.passesRareRoll(10_000, 41, ActivityType.WINTER_HEARTH, 18),
            AmbientActivityRules.passesRareRoll(10_000, 41, ActivityType.WINTER_HEARTH, 18)
        );
    }

    @Test
    void invalidProfilesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AmbientActivityProfile(
            ActivityType.WINTER_HEARTH,
            Set.of(),
            1,
            0,
            0,
            8
        ));
    }

    @Test
    void everyCreatureKindInThisSubsystemHasAnAmbientProfile() {
        final Set<CreatureKind> delegated = Set.of(
            CreatureKind.GOBLIN,
            CreatureKind.HOBGOBLIN,
            CreatureKind.NAAMAH
        );
        final Set<CreatureKind> missing = java.util.Arrays.stream(CreatureKind.values())
            .filter(kind -> !delegated.contains(kind))
            .filter(kind -> AmbientActivityProfile.forKind(kind).isEmpty())
            .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(), missing);
    }

    @Test
    void commonMaterialActivitiesUseCanonicalTagsAndExplicitExtensionPoints() {
        assertEquals("c:storage_blocks", AmbientActivityTags.SHINY_STORAGE_BLOCKS.location().toString());
        assertEquals("c:bookshelves", AmbientActivityTags.BOOKSHELVES.location().toString());
        assertEquals("c:player_workstations/crafting_tables",
            AmbientActivityTags.CRAFTING_WORKSTATIONS.location().toString());
        assertEquals("c:player_workstations/furnaces",
            AmbientActivityTags.FURNACE_WORKSTATIONS.location().toString());
        assertEquals("c:glass_blocks", AmbientActivityTags.GLASS_BLOCKS.location().toString());
        assertEquals("c:storage_blocks/wheat", AmbientActivityTags.HAY_BLOCKS.location().toString());
        assertEquals("warlockery:ambient/thorny_plants", AmbientActivityTags.THORNY_PLANTS.location().toString());
        assertEquals("warlockery:ambient/soul_lights", AmbientActivityTags.SOUL_LIGHTS.location().toString());
        assertTrue(AmbientActivityTags.forActivity(ActivityType.ARCANE_STUDY)
            .contains(AmbientActivityTags.FURNACE_WORKSTATIONS));
        assertTrue(AmbientActivityTags.forActivity(ActivityType.MIRROR_GAZE)
            .contains(AmbientActivityTags.GLASS_BLOCKS));
    }
}
