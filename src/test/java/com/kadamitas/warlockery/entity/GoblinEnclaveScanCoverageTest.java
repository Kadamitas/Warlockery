package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Coverage proof for the charged block-scan envelope.
 *
 * <p>Every read cap is far below its own box volume, so a naive raster spends its whole budget on
 * one corner and never reaches the entity's own Y level or the opposite quadrant. These tests assert
 * the two properties that make the search sound: the near envelope including the entity's own level
 * is evaluated on <em>every</em> scan, and the entire far envelope including the opposite quadrant is
 * evaluated within a small bounded number of scans.</p>
 */
final class GoblinEnclaveScanCoverageTest {
    /** The exact (horizontal, vertical, readCap) triples used by the five real callers. */
    private static final List<int[]> CALLERS = List.of(
        new int[] {5, 2, GoblinEnclaveRules.MAX_MINING_BLOCK_READS},
        new int[] {6, 4, GoblinEnclaveRules.MAX_WORK_BLOCK_READS},
        new int[] {6, 3, GoblinEnclaveRules.MAX_WORK_BLOCK_READS},
        new int[] {4, 1, GoblinEnclaveRules.MAX_CHILD_BLOCK_READS},
        new int[] {8, 4, GoblinEnclaveRules.MAX_SITE_BLOCK_READS}
    );

    @Test
    void everyCallerIsGenuinelyBudgetConstrained() {
        // If a cap ever exceeded its volume the rotation would be pointless; assert the premise.
        CALLERS.forEach(caller -> {
            final int volume = volume(caller[0], caller[1]);
            assertTrue(caller[2] < volume,
                "caller h=" + caller[0] + " v=" + caller[1] + " must be budget constrained");
            assertEquals(volume, GoblinEnclaveRuntime.envelope(caller[0], caller[1]).size());
        });
        assertEquals(605, volume(5, 2));
        assertEquals(1_521, volume(6, 4));
        assertEquals(1_183, volume(6, 3));
        assertEquals(243, volume(4, 1));
        assertEquals(2_601, volume(8, 4));
    }

    @Test
    void theEnvelopeIsOrderedCentreOutAndStartsAtTheEntityItself() {
        final List<BlockPos> offsets = GoblinEnclaveRuntime.envelope(5, 2);
        assertEquals(BlockPos.ZERO, offsets.getFirst());
        int previous = -1;
        for (final BlockPos offset : offsets) {
            final int distance = offset.getX() * offset.getX()
                + offset.getY() * offset.getY() + offset.getZ() * offset.getZ();
            assertTrue(distance >= previous, "the envelope must be ordered centre-out");
            previous = distance;
        }
        // Deterministic: the same shape always produces the identical order.
        assertEquals(offsets, GoblinEnclaveRuntime.envelope(5, 2));
    }

    @Test
    void everyScanEvaluatesTheEntityOwnLevelAndItsImmediateNeighbourhood() {
        CALLERS.forEach(caller -> {
            final List<BlockPos> offsets = GoblinEnclaveRuntime.envelope(caller[0], caller[1]);
            // Probe several rotations: the anchor must never rotate away.
            for (int cursor = 0; cursor < 2_000; cursor += 137) {
                final Set<BlockPos> window =
                    new HashSet<>(GoblinEnclaveRuntime.scanWindow(offsets, caller[2], cursor));
                assertTrue(window.contains(BlockPos.ZERO),
                    "the entity's own position is evaluated on every scan");
                assertTrue(window.contains(new BlockPos(1, 0, 0))
                        && window.contains(new BlockPos(-1, 0, 0))
                        && window.contains(new BlockPos(0, 0, 1))
                        && window.contains(new BlockPos(0, 0, -1)),
                    "all four neighbours on the entity's own Y level are evaluated on every scan");
                assertTrue(window.stream().anyMatch(offset -> offset.getY() == 0
                        && offset.getX() > 0 && offset.getZ() > 0),
                    "the near +x/+z quadrant is evaluated on every scan");
            }
        });
    }

    @Test
    void theWholeEnvelopeIncludingTheFarQuadrantIsCoveredWithinABoundedNumberOfScans() {
        CALLERS.forEach(caller -> {
            final List<BlockPos> offsets = GoblinEnclaveRuntime.envelope(caller[0], caller[1]);
            final int anchor = GoblinEnclaveRuntime.anchorSize(offsets.size(), caller[2]);
            final int page = GoblinEnclaveRuntime.pageSize(offsets.size(), caller[2]);
            final int tail = offsets.size() - anchor;
            final int scansNeeded = Math.ceilDiv(tail, page);
            final Set<BlockPos> seen = new HashSet<>();
            int cursor = 0;
            for (int scan = 0; scan < scansNeeded; scan++) {
                seen.addAll(GoblinEnclaveRuntime.scanWindow(offsets, caller[2], cursor));
                cursor = Math.floorMod(cursor + page, tail);
            }
            assertEquals(offsets.size(), seen.size(),
                "h=" + caller[0] + " v=" + caller[1] + " must be fully covered in " + scansNeeded
                    + " scans");
            // The far corner of the opposite quadrant is the worst case; name it explicitly.
            assertTrue(seen.contains(new BlockPos(caller[0], caller[1], caller[0])),
                "the far +x/+y/+z corner is evaluated");
            assertTrue(seen.contains(new BlockPos(-caller[0], -caller[1], -caller[0])),
                "the far -x/-y/-z corner is evaluated");
        });
    }

    @Test
    void theWorkedMiningAndBedCasesMatchTheDocumentedTrace() {
        final List<BlockPos> mining = GoblinEnclaveRuntime.envelope(5, 2);
        assertEquals(605, mining.size());
        assertEquals(64, GoblinEnclaveRuntime.anchorSize(mining.size(), 128));
        assertEquals(64, GoblinEnclaveRuntime.pageSize(mining.size(), 128));
        assertEquals(9, Math.ceilDiv(605 - 64, 64));

        final List<BlockPos> bed = GoblinEnclaveRuntime.envelope(8, 4);
        assertEquals(2_601, bed.size());
        assertEquals(128, GoblinEnclaveRuntime.anchorSize(bed.size(), 256));
        assertEquals(128, GoblinEnclaveRuntime.pageSize(bed.size(), 256));
        assertEquals(20, Math.ceilDiv(2_601 - 128, 128));
    }

    @Test
    void aScanNeverExceedsItsDeclaredReadBudget() {
        CALLERS.forEach(caller -> {
            final List<BlockPos> offsets = GoblinEnclaveRuntime.envelope(caller[0], caller[1]);
            for (int cursor = 0; cursor < 500; cursor += 61) {
                final List<BlockPos> window =
                    GoblinEnclaveRuntime.scanWindow(offsets, caller[2], cursor);
                assertTrue(window.size() <= caller[2],
                    "a scan never evaluates more positions than its declared cap");
                assertEquals(window.size(), new HashSet<>(window).size(),
                    "a scan never evaluates the same offset twice");
            }
        });
    }

    @Test
    void aBudgetLargerThanTheEnvelopeDegradesToASingleCompleteScan() {
        final List<BlockPos> offsets = GoblinEnclaveRuntime.envelope(1, 0);
        assertEquals(9, offsets.size());
        final List<BlockPos> window = GoblinEnclaveRuntime.scanWindow(offsets, 1_000, 0);
        assertEquals(9, new HashSet<>(window).size());
        assertFalse(window.isEmpty());
    }

    private static int volume(final int horizontal, final int vertical) {
        return (2 * horizontal + 1) * (2 * horizontal + 1) * (2 * vertical + 1);
    }
}
