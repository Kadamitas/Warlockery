package com.kadamitas.warlockery.entity.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The coverage contract for {@link ScanEnvelope}, stated generically rather than per family.
 *
 * <p>Five families shipped an envelope search that exhausted its budget in the innermost ring, so
 * the far envelope and sometimes the entity's own level were never evaluated. Those were caught, if
 * at all, by per family tests naming that family's radii. The properties below are quantified over
 * a grid of radii, budgets and starting cursors, so a family adopting this primitive inherits the
 * proof instead of restating it.</p>
 */
final class ScanEnvelopeCoverageTest {

    /** Radii and budgets spanning the shapes the fifteen committed families actually use. */
    private static Stream<Arguments> shapes() {
        final List<Arguments> shapes = new ArrayList<>();
        for (final int horizontal : new int[] {0, 1, 2, 3, 4, 5, 6, 8, 12}) {
            for (final int vertical : new int[] {0, 1, 2, 3, 4}) {
                for (final int readCap : new int[] {1, 2, 3, 7, 16, 32, 64, 128, 256, 1_000}) {
                    shapes.add(Arguments.of(horizontal, vertical, readCap));
                }
            }
        }
        return shapes.stream();
    }

    /** The starting cursors probed for every shape, including deliberately hostile ones. */
    private static final int[] CURSORS = {0, 1, 5, 61, 137, 999, 12_345, Integer.MAX_VALUE, -1, -7,
        Integer.MIN_VALUE};

    @ParameterizedTest
    @MethodSource("shapes")
    void everyWindowContainsTheOriginNeighbourhood(
        final int horizontal, final int vertical, final int readCap
    ) {
        final ScanEnvelope envelope = ScanEnvelope.of(horizontal, vertical);
        // A budget of one can only afford the origin itself, so the neighbourhood claim is scoped
        // to the offsets the anchor can actually hold.
        final int anchor = envelope.anchorSize(readCap);
        final List<BlockPos> guaranteed = envelope.offsets().subList(0, anchor);
        for (final int cursor : CURSORS) {
            final Set<BlockPos> window = new HashSet<>(envelope.window(readCap, cursor));
            assertTrue(window.containsAll(guaranteed),
                "the near anchor is present in every window, h=" + horizontal + " v=" + vertical
                    + " cap=" + readCap + " cursor=" + cursor);
            if (anchor >= 1) {
                assertTrue(window.contains(BlockPos.ZERO),
                    "the entity's own position is evaluated on every scan");
            }
        }
    }

    @ParameterizedTest
    @MethodSource("shapes")
    void theUnionAcrossSuccessiveScansCoversTheWholeEnvelope(
        final int horizontal, final int vertical, final int readCap
    ) {
        final ScanEnvelope envelope = ScanEnvelope.of(horizontal, vertical);
        if (envelope.pageSize(readCap) <= 0 && envelope.tailSize(readCap) > 0) {
            // A cap of one leaves nothing for the rotating page; the envelope is then genuinely
            // uncoverable and scansToCover says so rather than looping forever.
            assertThrowsIllegalArgument(() -> envelope.scansToCover(readCap));
            return;
        }
        final int scansNeeded = envelope.scansToCover(readCap);
        for (final int start : CURSORS) {
            final Set<BlockPos> seen = new HashSet<>();
            int cursor = start;
            for (int scan = 0; scan < scansNeeded; scan++) {
                seen.addAll(envelope.window(readCap, cursor));
                cursor = envelope.advanceCursor(readCap, cursor);
            }
            assertEquals(envelope.size(), seen.size(),
                "h=" + horizontal + " v=" + vertical + " cap=" + readCap + " from cursor " + start
                    + " must be fully covered in " + scansNeeded + " scans");
            assertTrue(seen.contains(new BlockPos(horizontal, vertical, horizontal))
                    && seen.contains(new BlockPos(-horizontal, -vertical, -horizontal)),
                "both far corners are evaluated");
        }
    }

    @ParameterizedTest
    @MethodSource("shapes")
    void aWindowNeverExceedsItsBudgetAndNeverRepeatsAnOffset(
        final int horizontal, final int vertical, final int readCap
    ) {
        final ScanEnvelope envelope = ScanEnvelope.of(horizontal, vertical);
        for (final int cursor : CURSORS) {
            final List<BlockPos> window = envelope.window(readCap, cursor);
            assertTrue(window.size() <= readCap, "a scan never exceeds its declared cap");
            assertTrue(window.size() <= envelope.size());
            assertEquals(window.size(), new HashSet<>(window).size(),
                "a scan never evaluates the same offset twice, h=" + horizontal + " v=" + vertical
                    + " cap=" + readCap + " cursor=" + cursor);
        }
    }

    @ParameterizedTest
    @MethodSource("shapes")
    void theEnvelopeIsOrderedCentreOutAndIsExactlyTheBox(
        final int horizontal, final int vertical, final int readCap
    ) {
        final ScanEnvelope envelope = ScanEnvelope.of(horizontal, vertical);
        assertEquals((2 * horizontal + 1) * (2 * horizontal + 1) * (2 * vertical + 1),
            envelope.size());
        assertEquals(envelope.size(), new HashSet<>(envelope.offsets()).size());
        assertEquals(BlockPos.ZERO, envelope.offsets().getFirst());
        int previous = -1;
        for (final BlockPos offset : envelope.offsets()) {
            assertTrue(Math.abs(offset.getX()) <= horizontal
                && Math.abs(offset.getZ()) <= horizontal
                && Math.abs(offset.getY()) <= vertical, "every offset lies inside the box");
            final int distance = offset.getX() * offset.getX() + offset.getY() * offset.getY()
                + offset.getZ() * offset.getZ();
            assertTrue(distance >= previous, "the envelope is ordered centre out");
            previous = distance;
        }
        assertEquals(readCap > 0 ? Math.min(readCap / 2, envelope.size()) : 0,
            envelope.anchorSize(readCap));
    }

    @Test
    void identicalShapesShareOneImmutableEnvelope() {
        assertEquals(ScanEnvelope.of(5, 2), ScanEnvelope.of(5, 2));
        assertEquals(ScanEnvelope.of(5, 2).offsets(), ScanEnvelope.of(5, 2).offsets());
        assertNotEquals(ScanEnvelope.of(5, 2), ScanEnvelope.of(5, 3));
        assertThrowsUnsupported(() -> ScanEnvelope.of(2, 1).offsets().add(BlockPos.ZERO));
    }

    @Test
    void aBudgetLargerThanTheEnvelopeDegradesToOneCompleteScan() {
        final ScanEnvelope envelope = ScanEnvelope.of(1, 0);
        assertEquals(9, envelope.size());
        assertEquals(1, envelope.scansToCover(1_000));
        assertEquals(9, new HashSet<>(envelope.window(1_000, 0)).size());
        assertEquals(0, envelope.advanceCursor(1_000, 7));
    }

    @Test
    void seedingSpreadsEntitiesAcrossTheTailWithoutLeavingIt() {
        final ScanEnvelope envelope = ScanEnvelope.of(6, 3);
        final Set<Integer> seeds = IntStream.range(0, 200)
            .map(_ -> envelope.seedCursor(UUID.randomUUID(), 128))
            .boxed()
            .collect(HashSet::new, Set::add, Set::addAll);
        assertTrue(seeds.size() > 1, "identities must not all start on the same page");
        assertTrue(seeds.stream().allMatch(seed -> seed >= 0 && seed < envelope.tailSize(128)));
    }

    /**
     * The historical defect, reproduced. A raster or ring walk truncated at the budget, which is
     * what F14, F15, F16, F10 and F13 each shipped, misses most of its envelope and in particular
     * never reaches the far corner. The same budget through the rotating window reaches everything.
     */
    @Test
    void redTruncatedRasterMissesTheFarEnvelopeThatTheRotatingWindowReaches() {
        final ScanEnvelope envelope = ScanEnvelope.of(6, 4);
        final int readCap = 128;
        assertTrue(readCap < envelope.size(), "the premise is a budget below the volume");

        final Set<BlockPos> truncated = new HashSet<>(envelope.offsets().subList(0, readCap));
        int cursor = 0;
        for (int scan = 0; scan < envelope.scansToCover(readCap); scan++) {
            cursor = envelope.advanceCursor(readCap, cursor);
        }
        assertFalse(truncated.contains(new BlockPos(6, 4, 6)),
            "the defective truncated walk never reaches the far corner");
        assertEquals(readCap, truncated.size());

        final Set<BlockPos> rotated = new HashSet<>();
        int rotating = 0;
        for (int scan = 0; scan < envelope.scansToCover(readCap); scan++) {
            rotated.addAll(envelope.window(readCap, rotating));
            rotating = envelope.advanceCursor(readCap, rotating);
        }
        assertTrue(rotated.contains(new BlockPos(6, 4, 6)));
        assertEquals(envelope.size(), rotated.size());
    }

    /**
     * The other half of the same defect: a walk that rotates the <em>whole</em> window, rather than
     * anchoring the near envelope, does eventually cover everything but stops evaluating the
     * entity's own position. F13's odometer has this shape, and it is why a hazard directly under
     * the entity could go unseen on most ticks.
     */
    @Test
    void redFullyRotatingWindowLosesTheOriginThatTheAnchorKeeps() {
        final ScanEnvelope envelope = ScanEnvelope.of(5, 2);
        final int readCap = 128;
        final List<BlockPos> offsets = envelope.offsets();

        final int page = readCap;
        boolean originMissedAtLeastOnce = false;
        for (int scan = 1; scan < 5; scan++) {
            final Set<BlockPos> naive = new HashSet<>();
            for (int index = 0; index < page; index++) {
                naive.add(offsets.get((scan * page + index) % offsets.size()));
            }
            originMissedAtLeastOnce |= !naive.contains(BlockPos.ZERO);
        }
        assertTrue(originMissedAtLeastOnce,
            "a fully rotating window drops the entity's own position on later scans");

        for (int scan = 0; scan < 40; scan++) {
            assertTrue(envelope.window(readCap, scan * envelope.pageSize(readCap))
                .contains(BlockPos.ZERO), "the anchored window never drops it");
        }
    }

    private static void assertThrowsIllegalArgument(final Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (final IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    private static void assertThrowsUnsupported(final Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (final UnsupportedOperationException expected) {
            assertTrue(true);
        }
    }
}
