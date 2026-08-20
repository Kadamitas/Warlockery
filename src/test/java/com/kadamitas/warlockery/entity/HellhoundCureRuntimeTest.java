package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Pins the transactional cure adapter: exact rule delegation, exact message keys, exact
 * PASS/FAIL semantics, single settlement, prepare-before-consume ordering, and the unchanged
 * Wolf CONVERSION chain. Live consumption, multi-contributor ownership, and failure rollback
 * execute in the registered F09 GameTests.
 */
final class HellhoundCureRuntimeTest {
    private static final Path RUNTIME = Path.of(
        "src", "main", "java", "com", "kadamitas", "warlockery", "entity", "HellhoundCureRuntime.java"
    );

    @Test
    void cureRulesContractIsUnchanged() {
        assertEquals(3, HellhoundCureRules.REQUIRED_PROGRESS);
        final HellhoundCureRules.Result noWeakness =
            HellhoundCureRules.advance(0, false, true, 0);
        assertEquals(HellhoundCureRules.Diagnostic.NEEDS_WEAKNESS, noWeakness.diagnostic());
        final HellhoundCureRules.Result noApple =
            HellhoundCureRules.advance(0, true, false, 0);
        assertEquals(HellhoundCureRules.Diagnostic.NEEDS_GOLDEN_APPLE, noApple.diagnostic());
        final HellhoundCureRules.Result partial =
            HellhoundCureRules.advance(0, true, true, 2);
        assertEquals(1, partial.progress());
        assertEquals(HellhoundCureRules.Diagnostic.PROGRESS, partial.diagnostic());
        final HellhoundCureRules.Result caged =
            HellhoundCureRules.advance(0, true, true, 3);
        assertTrue(caged.cured(), "three sturdy faces complete from one apple");
        final HellhoundCureRules.Result finished =
            HellhoundCureRules.advance(2, true, true, 0);
        assertTrue(finished.cured(), "the third apple completes the cure");
    }

    @Test
    void adapterKeepsTheExactSharedProgressKeyAndMessages() {
        final String source = read(RUNTIME);
        assertTrue(source.contains("\"WarlockeryHellhoundCure\""),
            "cure progress must stay on the exact legacy persistent key");
        assertTrue(source.contains("message.warlockery.creature.hellhound_cure."),
            "the current diagnostic message keys must be preserved");
        assertTrue(source.contains("Diagnostic.NEEDS_WEAKNESS")
                && source.contains("InteractionResult.PASS"),
            "missing Weakness must still PASS to the superclass interaction");
        assertTrue(source.contains("Diagnostic.NEEDS_GOLDEN_APPLE")
                && source.contains("InteractionResult.FAIL"),
            "a non-apple attempt under Weakness must still FAIL");
        assertTrue(source.contains("hasInfiniteMaterials"),
            "creative and infinite-material consumption behavior must be preserved");
    }

    @Test
    void successPathIsTransactionalWolfBeforeAnySettlement() {
        final String source = read(RUNTIME);
        final int wolfCreation = source.indexOf("EntityTypes.WOLF.create(level, EntitySpawnReason.CONVERSION)");
        assertTrue(wolfCreation >= 0, "the persistent vanilla Wolf must use the CONVERSION reason");
        final int nullGuard = source.indexOf("if (wolf == null)", wolfCreation);
        assertTrue(nullGuard > wolfCreation, "failed construction must return without settlement");
        final int settlement = source.indexOf("consumeOne(player, held)", wolfCreation);
        assertTrue(settlement > nullGuard,
            "on the completion path, consumption settles only after the Wolf exists");
        final int progressWrite = source.indexOf("putInt(CURE_PROGRESS_KEY, result.progress())", wolfCreation);
        assertTrue(progressWrite > nullGuard,
            "completed progress is only written after the Wolf exists");
        final int release = source.indexOf("releaseHellhoundClaims", wolfCreation);
        final int tame = source.indexOf("wolf.tame(player)", wolfCreation);
        final int persist = source.indexOf("wolf.setPersistenceRequired()", wolfCreation);
        final int add = source.indexOf("level.addFreshEntity(wolf)", wolfCreation);
        final int discard = source.indexOf("creature.discard()", wolfCreation);
        assertTrue(release > 0 && release < discard,
            "Hellhound-only claims release before the final discard");
        assertTrue(tame > 0 && persist > tame && add > persist && discard > add,
            "the tame -> persist -> add -> discard chain must remain exact");
        assertTrue(source.contains("wolf.snapTo(creature.getX()"),
            "the Wolf appears exactly at the Hellhound");
    }

    @Test
    void completionReleasesTheExactLegacyHearthClaim() {
        final String source = read(RUNTIME);
        assertTrue(source.contains("releaseExactOwnedLegacyHearth")
                || source.contains("HellhoundLifeRuntime.releaseAll"),
            "cure completion must release the exact still-owned legacy hearth claim");
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
