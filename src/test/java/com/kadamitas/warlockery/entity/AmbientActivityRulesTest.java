package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void hexBatNoLongerReceivesGenericNightPerchAndOwlSemanticsAreUnchanged() {
        assertTrue(AmbientActivityProfile.forKind(CreatureKind.HEX_BAT).isEmpty(),
            "the dedicated Hex Bat owns its roost behavior and receives no generic ambient authority");
        final AmbientActivityProfile nightPerch = AmbientActivityProfile.forType(ActivityType.NIGHT_PERCH);
        assertEquals(Set.of(CreatureKind.OWL), nightPerch.kinds(),
            "Owl keeps NIGHT_PERCH exactly as before");
        assertEquals(300, nightPerch.checkIntervalTicks());
        assertEquals(8, nightPerch.chanceDenominator());
        assertEquals(3_600, nightPerch.cooldownTicks());
        assertEquals(0, nightPerch.localChangeCap());
    }

    @Test
    void hauntedBellBelongsOnlyToThePoltergeistWithExactScheduling() {
        final AmbientActivityProfile hauntedBell = AmbientActivityProfile.forType(ActivityType.HAUNTED_BELL);
        assertEquals(Set.of(CreatureKind.POLTERGEIST), hauntedBell.kinds(),
            "the Banshee no longer receives the haunted-bell routine; the Poltergeist keeps it");
        assertEquals(400, hauntedBell.checkIntervalTicks());
        assertEquals(14, hauntedBell.chanceDenominator());
        assertEquals(6_000, hauntedBell.cooldownTicks());
        assertEquals(0, hauntedBell.localChangeCap());
        assertTrue(AmbientActivityProfile.forKind(CreatureKind.BANSHEE).isEmpty(),
            "Banshee ambient presentation is owned by its dedicated vigil runtime");
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
            CreatureKind.NAAMAH,
            CreatureKind.ELDRITCH_WATCHER,
            CreatureKind.CORPSE,
            // F15: the dedicated HexBatRuntime owns roost/sortie behavior.
            CreatureKind.HEX_BAT,
            CreatureKind.BANSHEE,
            // F18: the dedicated DeathRuntime owns every Death schedule; Death never communes
            // with soul lanterns.
            CreatureKind.DEATH,
            // F19: the dedicated LostSoulRuntime and SpiritRuntime own memorial
            // petition and soul-light attendance respectively.
            CreatureKind.LOST_SOUL,
            CreatureKind.SPIRIT,
            // F13: the dedicated Crone and Mage runtimes own their bounded workstation work.
            CreatureKind.HEDGE_CRONE,
            CreatureKind.CIRCLE_MAGE,
            // F21: the dedicated EchoShadeRuntime and SpectreRuntime own the echo and the
            // haunting; neither kind communes with soul lanterns any more.
            CreatureKind.ECHO_SHADE,
            CreatureKind.SPECTRE
        );
        final Set<CreatureKind> missing = java.util.Arrays.stream(CreatureKind.values())
            .filter(kind -> !delegated.contains(kind))
            .filter(kind -> AmbientActivityProfile.forKind(kind).isEmpty())
            .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(), missing);
    }

    @Test
    void everyPractitionerDelegatesArcaneStudyToItsDedicatedRuntime() {
        assertTrue(AmbientActivityProfile.forKind(CreatureKind.ELDRITCH_WATCHER).isEmpty(),
            "the dedicated Watcher runtime owns its focus-inspection schedule");
        assertTrue(AmbientActivityProfile.forKind(CreatureKind.HEDGE_CRONE).isEmpty(),
            "F13: the dedicated Hedge Crone runtime owns its bounded ward preparation");
        assertTrue(AmbientActivityProfile.forKind(CreatureKind.CIRCLE_MAGE).isEmpty(),
            "F13: the dedicated Circle Mage runtime owns its bounded solo and conclave study");
        assertNull(AmbientActivityProfile.forType(ActivityType.ARCANE_STUDY),
            "no kind remains on the generic ARCANE_STUDY dispatch");
        assertTrue(AmbientActivityProfile.all().stream()
                .noneMatch(profile -> profile.type() == ActivityType.ARCANE_STUDY),
            "the retired profile is gone from the dispatch table");
        assertFalse(AmbientActivityTags.forActivity(ActivityType.ARCANE_STUDY).isEmpty(),
            "the shared workstation block predicate both dedicated runtimes reuse stays registered");
    }

    @Test
    void aRetiredActivityRowNeverThrowsThroughTheGenericDispatch() {
        // Regression: forType is a plain map lookup, so retiring the ARCANE_STUDY row made it
        // return null while executeNow still dereferenced it unguarded at its sole call site.
        assertNull(AmbientActivityProfile.forType(ActivityType.ARCANE_STUDY));
        assertFalse(AmbientActivityRuntime.executeNow(
            null, null, CreatureKind.CIRCLE_MAGE, ActivityType.ARCANE_STUDY),
            "a retired activity row declines instead of throwing");
        assertFalse(AmbientActivityRuntime.executeNow(
            null, null, CreatureKind.HEDGE_CRONE, ActivityType.ARCANE_STUDY));
        assertFalse(AmbientActivityRuntime.executeNow(
            null, null, CreatureKind.ELDRITCH_WATCHER, ActivityType.ARCANE_STUDY));
    }

    @Test
    void corpseIsDelegatedToItsDedicatedRuntimeWhileLouseKeepsExactGraveScavenge() {
        assertTrue(AmbientActivityProfile.forKind(CreatureKind.CORPSE).isEmpty(),
            "the Corpse no longer shares the ambient scavenge profile");
        final AmbientActivityProfile scavenge =
            AmbientActivityProfile.forType(ActivityType.GRAVE_SCAVENGE);
        assertEquals(Set.of(CreatureKind.LOUSE), scavenge.kinds());
        assertEquals(300, scavenge.checkIntervalTicks());
        assertEquals(12, scavenge.chanceDenominator());
        assertEquals(4_800, scavenge.cooldownTicks());
        assertEquals(1, scavenge.localChangeCap());
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

    @Test
    void echoShadeAndSpectreAreDelegatedWhileUmbralSigilKeepsTheExactVigil() {
        assertTrue(AmbientActivityProfile.forKind(CreatureKind.ECHO_SHADE).isEmpty(),
            "the dedicated Echo Shade runtime owns its own ambient schedule");
        assertTrue(AmbientActivityProfile.forKind(CreatureKind.SPECTRE).isEmpty(),
            "the dedicated Spectre runtime owns its own ambient schedule");
        final AmbientActivityProfile vigil =
            AmbientActivityProfile.forType(ActivityType.SOUL_LANTERN_VIGIL);
        assertEquals(Set.of(CreatureKind.UMBRAL_SIGIL), vigil.kinds());
        assertEquals(400, vigil.checkIntervalTicks());
        assertEquals(10, vigil.chanceDenominator());
        assertEquals(4_800, vigil.cooldownTicks());
        assertEquals(0, vigil.localChangeCap());
    }
}
