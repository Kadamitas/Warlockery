package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.AnimalFamiliarRules.SearchOutcome;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * The three contracts this family owes another package rather than itself.
 *
 * <p>Each one is a hazard that a compiler cannot see: an override that has to <em>not</em> exist, a
 * counter that has to have an increment site, and a persisted record whose meaning lives in a file
 * this family does not own.</p>
 */
final class SpectralFamiliarRuntimeTest {

    private static final Path MAIN =
        Path.of("src", "main", "java", "com", "kadamitas", "warlockery", "entity");
    private static final Path BODY = MAIN.resolve("SpectralFamiliarEntity.java");
    private static final Path RUNTIME = MAIN.resolve("SpectralFamiliarRuntime.java");
    private static final Path FIXTURES = MAIN.resolve("SpectralFamiliarGameTests.java");

    private static final UUID IDENTITY = UUID.fromString("00000000-0000-0000-0000-00000000dead");

    // =====================================================================================
    // 1. The peaceful sweep is another package's, and this body must not declare a fourteenth
    //    override of it
    // =====================================================================================

    /**
     * The peaceful-despawn package keys its override on OWNERSHIP and asserts its override set is
     * exactly the thirteen bodies it declares. A fourteenth override here fails that assertion in
     * either merge order, and it would also be the weaker remedy: this one keyed on
     * {@code isPersistenceRequired()}, so a name-tagged UNBOUND familiar survived peaceful where
     * vanilla deletes it, and it consulted the persistent-data lookup before the difficulty compare
     * that short-circuits in every non-peaceful world. Their override covers {@code SpiritMob}, this
     * body's own superclass, so the fix is inherited rather than lost.
     */
    @Test
    void theBodyDeclaresNoCheckDespawnOverrideBecauseThePeacefulPackageOwnsThatSweep() {
        assertFalse(declares(SpectralFamiliarEntity.class, "checkDespawn"),
            "the peaceful sweep is fixed once, on SpiritMob, by the peaceful-despawn package, and "
                + "its coverage test asserts the override set is exactly thirteen bodies; a "
                + "fourteenth override here fails that test in either merge order");
        assertFalse(read(BODY).contains("void checkDespawn"),
            "and no declaration of it may survive in the source either");
    }

    /**
     * The half of this family's remedy that is kept, because the inherited fix consults it.
     *
     * <p>Their guard exempts an OWNED body. This override is what makes a bound spectral familiar
     * report as persistent at all, and it consults the vanilla latch rather than replacing it, which
     * is the shape the F10 review accepted.</p>
     */
    @Test
    void theOwnershipGuardThePeacefulSweepConsultsIsStillDeclaredHere() {
        assertTrue(declares(SpectralFamiliarEntity.class, "isPersistenceRequired"),
            "the inherited peaceful guard keys on ownership, and this is where ownership becomes "
                + "persistence for this body");
        assertTrue(declares(SpectralFamiliarEntity.class, "setPersistenceRequired"),
            "and the one-write refusal that keeps the contract reason clearable stays with it");
        final String body = read(BODY);
        assertTrue(body.contains(
                "return super.isPersistenceRequired() || CreatureBehaviorState.owner(this).isPresent();"),
            "the vanilla latch must be CONSULTED rather than discarded: the rejected F10 remedy "
                + "replaced it with a derived predicate and a name-tagged familiar then despawned");
    }

    // =====================================================================================
    // 2. A counter with no increment site makes every assertion that reads it unfailable
    // =====================================================================================

    /**
     * The defect class this audit keeps finding, pinned structurally rather than by hand.
     *
     * <p>A counter that is never incremented cannot fail an assertion, so a fixture that reads one
     * is a fixture that proves nothing. Every field of {@code Counters} must therefore have a real
     * {@code ++} site in the runtime or in the body that owns the decline seams.</p>
     */
    @Test
    void everyCounterTheFixturesCanReadHasARealIncrementSite() {
        final String runtime = read(RUNTIME);
        final String body = read(BODY);
        for (final Field field : SpectralFamiliarRuntime.Counters.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            final String name = field.getName();
            // Both increment shapes this family really uses: ++ for the per-event counters and
            // += for the one that accumulates a per-survey total.
            assertTrue(incremented(runtime, name) || incremented(body, name),
                "counter '" + name + "' is readable by every fixture but is incremented nowhere: "
                    + "an assertion on a counter that can never move is an assertion that can never "
                    + "fail, which is exactly the defect this audit exists to catch");
        }
    }

    /**
     * The counter surface and the fixture surface must agree in the other direction too: a fixture
     * may not read a getter that {@code Counters} no longer exposes, and it may not assert a plain
     * zero on a counter this family never moves.
     */
    @Test
    void noFixtureAssertsOnACounterThisFamilyCannotMove() {
        final String fixtures = read(FIXTURES);
        assertFalse(fixtures.contains("worldEdits() =="),
            "the world-edit claim is proven by comparing the arena's real block states before and "
                + "after, not by asserting a zero on a counter with no increment site");
        assertFalse(fixtures.contains("spectralCounters().worldEdits()"),
            "and no fixture reads that counter at all any more");
        assertTrue(fixtures.contains("assertArenaUnedited("),
            "the claim is not merely dropped: every place that asserted the vacuous zero now "
                + "compares the arena's real block states instead");
        final String runtime = read(RUNTIME);
        assertFalse(runtime.contains("long worldEdits;"),
            "and the field that could never move is gone rather than merely unread");
        assertFalse(runtime.contains("worldEdits()"),
            "and so is the getter that made it readable; what is left in their place is a note in "
                + "prose saying why, which is documentation rather than a surface");
    }

    // =====================================================================================
    // 3. The cross-family persisted contract: two primitives whose meaning lives in F23's file
    // =====================================================================================

    /**
     * FROZEN CROSS-FAMILY CONTRACT.
     *
     * <p>{@link SpectralFamiliarState} embeds {@link SearchOutcome} as a persisted record component,
     * and writes it to disk as exactly two primitives: {@code nextDueAt} under {@code NextSurveyAt}
     * and {@code consecutiveFailures} under {@code SurveyFailures}. Adding a component or renaming an
     * accessor breaks this family's compile, which is loud and fine. The hazard this test exists for
     * is the silent one: if {@code AnimalFamiliarRules} later redefines what
     * {@code consecutiveFailures} <em>counts</em> - a rolling window over the last N searches, say,
     * instead of a run of consecutive failures - then every spectral familiar already on disk carries
     * a number whose meaning changed underneath it, with no compile error and, without this test, no
     * test failure either. The three semantic assertions below are what turns that silent change into
     * a loud one.</p>
     */
    @Test
    void thePersistedCrossFamilyContractOnSearchOutcomeIsFrozenInNameAndInMeaning() {
        final RecordComponent[] components = SearchOutcome.class.getRecordComponents();
        assertEquals(2, components.length,
            "the on-disk format of this family's survey field is exactly two primitives; a third "
                + "component would be persisted by nobody and read back as a default");
        assertEquals("nextDueAt", components[0].getName());
        assertEquals(long.class, components[0].getType());
        assertEquals("consecutiveFailures", components[1].getName());
        assertEquals(int.class, components[1].getType());

        // The MEANING, which is the half a compiler cannot check.
        assertEquals(1, AnimalFamiliarRules.recordSearch(0L, 200, false, 0).consecutiveFailures(),
            "one fruitless search is one failure");
        assertEquals(3, AnimalFamiliarRules.recordSearch(0L, 200, false, 2).consecutiveFailures(),
            "and each further fruitless search adds exactly one to the count it is handed");
        assertEquals(0, AnimalFamiliarRules.recordSearch(0L, 200, true, 2).consecutiveFailures(),
            "ANY success resets the count to zero: it is a run of CONSECUTIVE failures and not a "
                + "rolling window, and this family's persisted SurveyFailures means exactly that");
        assertEquals(AnimalFamiliarRules.MAX_ROUTE_FAILURES,
            AnimalFamiliarRules.recordSearch(0L, 200, false, 99).consecutiveFailures(),
            "and the count saturates at the shared maximum rather than growing without bound");

        // The two primitives really are the two accessors, and the failure count really does cross
        // the reload seam carrying that meaning with it.
        final CompoundTag tag = SpectralFamiliarState.empty(IDENTITY, 1_000L)
            .withSurvey(new SearchOutcome(4_242L, 2), 0L)
            .write();
        assertEquals(4_242L, tag.getLongOr("NextSurveyAt", -1L),
            "nextDueAt is persisted under NextSurveyAt");
        assertEquals(2, tag.getIntOr("SurveyFailures", -1),
            "consecutiveFailures is persisted under SurveyFailures");
        assertEquals(2,
            SpectralFamiliarState.read(tag, IDENTITY, 1_000L).survey().consecutiveFailures(),
            "and it is the one half of the outcome that survives a reload, so its meaning is a "
                + "save-compatibility decision rather than a cosmetic one");
    }

    // ---- helpers ----

    private static boolean incremented(final String source, final String counter) {
        return source.contains(counter + "++") || source.contains(counter + " +=");
    }

    private static boolean declares(
        final Class<?> type,
        final String name,
        final Class<?>... parameters
    ) {
        try {
            type.getDeclaredMethod(name, parameters);
            return true;
        } catch (final NoSuchMethodException absent) {
            return false;
        }
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}


