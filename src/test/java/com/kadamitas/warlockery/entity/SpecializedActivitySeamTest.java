package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards the specialization seam of the three families whose entity classes replace part of the
 * generic behavior stack. Every generic layer a family drops must be retired from the data that
 * declares it, and every layer the data still declares must be reached by that family's entity.
 */
final class SpecializedActivitySeamTest {
    private static final Path MAIN = Path.of("src", "main", "java", "com", "kadamitas", "warlockery");

    @Test
    void theWerewolfReachesTheAmbientVigilItStillDeclares() {
        final String entity = read(MAIN.resolve("entity/WerewolfEntity.java"));
        assertTrue(entity.contains("AmbientActivityRuntime.tick(this, level, CreatureKind.WEREWOLF)"),
            "MOON_GAZE declares the WEREWOLF kind, so the specialized seam must reach the ambient "
                + "runtime instead of dropping it silently");
        assertTrue(entity.contains("LycanPackRuntime.tick(this, level)"),
            "the dedicated pack runtime stays the first specialized activity");
        assertFalse(entity.contains("TacticalCombatRuntime.tick"),
            "LycanPackRuntime owns navigation, so the generic tactical layer stays out and its "
                + "doctrine row is retired rather than left unreachable");
        assertFalse(AmbientActivityProfile.forKind(ArcaneCreature.CreatureKind.WEREWOLF).isEmpty(),
            "the seam above is only correct while the Werewolf still declares an ambient profile");
    }

    @Test
    void theVampireCourtDropsBothGenericLayersAndDeclaresNeither() {
        final String entity = read(MAIN.resolve("entity/VampireCourtEntity.java"));
        assertFalse(entity.contains("TacticalCombatRuntime.tick"),
            "the court runtime owns combat navigation outright");
        assertFalse(entity.contains("AmbientActivityRuntime.tick"),
            "SEEK_SHELTER replaced the generic daylight retreat");
        assertTrue(entity.contains("VampireCourtRuntime.tick(this, level)"),
            "the dedicated runtime is the only specialized activity");
        assertTrue(AmbientActivityProfile.forKind(ArcaneCreature.CreatureKind.VAMPIRE).isEmpty()
                && AmbientActivityProfile.forKind(ArcaneCreature.CreatureKind.BLOOD_THRALL).isEmpty(),
            "no ambient row may declare a court kind that cannot reach the ambient runtime");
        assertFalse(TacticalCombatRules.usesGenericTacticalLayer(ArcaneCreature.CreatureKind.VAMPIRE));
        assertFalse(TacticalCombatRules.usesGenericTacticalLayer(ArcaneCreature.CreatureKind.BLOOD_THRALL));
    }

    @Test
    void theLycanSentinelDropsBothGenericLayersAndDeclaresNeither() {
        final String entity = read(MAIN.resolve("entity/LycanVillagerEntity.java"));
        assertFalse(entity.contains("TacticalCombatRuntime.tick"),
            "the sentinel runtime owns its intercept and withdraw navigation under a path budget");
        assertFalse(entity.contains("AmbientActivityRuntime.tick"),
            "BOUNDARY_WATCH and MOON_WATCH replaced the generic patrol and the generic moon vigil");
        assertTrue(entity.contains("LycanVillagerRuntime.tick(this, level)"),
            "the dedicated runtime is the only specialized activity");
        assertTrue(AmbientActivityProfile.forKind(ArcaneCreature.CreatureKind.LYCAN_VILLAGER).isEmpty(),
            "no ambient row may declare a kind that cannot reach the ambient runtime");
        assertFalse(TacticalCombatRules.usesGenericTacticalLayer(ArcaneCreature.CreatureKind.LYCAN_VILLAGER));
    }

    @Test
    void everyKindThatStillDeclaresAnAmbientRowIsReachedByItsEntityClass() {
        final String arcaneMob = read(MAIN.resolve("entity/ArcaneMob.java"));
        assertTrue(arcaneMob.contains("AmbientActivityRuntime.tick(this, level, kind)"),
            "the shared base seam is the dispatch path for every non-specialized kind");
        final String hunter = read(MAIN.resolve("entity/WerewolfHunterEntity.java"));
        assertTrue(hunter.contains("AmbientActivityRuntime.tick(this, level, CreatureKind.WEREWOLF_HUNTER)"),
            "VILLAGE_WATCH still declares WEREWOLF_HUNTER, so its Pillager-based entity must reach "
                + "the ambient runtime");
        assertTrue(hunter.contains("TacticalCombatRuntime.tick(this, level, CreatureKind.WEREWOLF_HUNTER)"),
            "the RANGED doctrine row for the hunter is reached from its own entity tick");
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
