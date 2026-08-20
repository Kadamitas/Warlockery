package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.behavior.ReadBudget;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The hazard escape read budget, proved to be the real cost rather than a decoration.
 *
 * <p>The sweep used to charge exactly one read per candidate while every candidate performed up to
 * eight real world reads, and a sixteen entry window against a one hundred and twenty eight read
 * budget meant the guard could never bind. These tests count the probe calls the sweep actually
 * makes and compare them against what the budget was charged, which is the only comparison that can
 * tell an honest budget from a decorative one.</p>
 */
final class MimicryEscapeBudgetTest {

    private record CandidateProbe(boolean loaded, boolean border, boolean feetAir,
                                  boolean headAir, boolean dry, boolean floor, boolean occupied)
        implements MimicryRuntime.DestinationProbe {
        @Override public boolean loaded(BlockPos p) { return loaded; }
        @Override public boolean withinBorder(BlockPos p) { return border; }
        @Override public boolean air(BlockPos p) {
            if (p.getY() == FLOOR_Y + 1) return headAir;
            if (p.getY() == FLOOR_Y - 1) return !floor;
            return feetAir;
        }
        @Override public boolean fluidFree(BlockPos p) { return dry; }
        @Override public boolean occupied(BlockPos p) { return occupied; }
    }

    private static final int FLOOR_Y = 64;
    private static final BlockPos ORIGIN = new BlockPos(0, FLOOR_Y, 0);

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * A probe that records every read it is asked for. The world it describes is one flat layer:
     * everything at {@link #FLOOR_Y} is open air, everything below it is solid, and the block below
     * is flooded everywhere except at the positions named as dry. A candidate is therefore rejected
     * only at its very last read unless it is one of the dry ones, which is what makes the
     * per-candidate cost the honest worst case rather than an early exit.
     */
    private static final class CountingProbe implements MimicryRuntime.DestinationProbe {

        private final List<BlockPos> dryBelow;
        private final boolean occupied;
        private final List<BlockPos> reads = new ArrayList<>();

        private CountingProbe(final List<BlockPos> dryBelow) {
            this(dryBelow, false);
        }

        private CountingProbe(final List<BlockPos> dryBelow, final boolean occupied) {
            this.dryBelow = List.copyOf(dryBelow);
            this.occupied = occupied;
        }

        @Override
        public boolean loaded(final BlockPos position) {
            reads.add(position.immutable());
            return true;
        }

        @Override
        public boolean withinBorder(final BlockPos position) {
            reads.add(position.immutable());
            return true;
        }

        @Override
        public boolean air(final BlockPos position) {
            reads.add(position.immutable());
            return position.getY() == FLOOR_Y || position.getY() == FLOOR_Y + 1;
        }

        @Override
        public boolean fluidFree(final BlockPos position) {
            reads.add(position.immutable());
            return position.getY() == FLOOR_Y || dryBelow.contains(position);
        }

        @Override
        public boolean occupied(final BlockPos position) {
            reads.add(position.immutable());
            return occupied;
        }

        private int readCount() {
            return reads.size();
        }

        private long candidateCount() {
            return reads.stream()
                .filter(position -> position.getY() == FLOOR_Y)
                .distinct()
                .count();
        }
    }

    /** A flat window, so a candidate and the block below it can never be the same position. */
    private static List<BlockPos> flatWindow(final int size) {
        final List<BlockPos> window = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            window.add(new BlockPos(index + 1, 0, 0));
        }
        return List.copyOf(window);
    }

    @Test
    void everyWorldReadADestinationCandidatePerformsIsChargedToTheBudget() {
        final CountingProbe probe = new CountingProbe(List.of());
        final ReadBudget budget = ReadBudget.of(MimicryRules.MAX_DESTINATION_READS);

        final Optional<BlockPos> chosen = MimicryRuntime.chooseEscapeDestination(
            probe, budget, ORIGIN, flatWindow(MimicryRules.destinationCandidateCap() + 6)
        );

        assertEquals(Optional.empty(), chosen, "no candidate in this world qualifies");
        assertEquals(probe.readCount(), budget.spent(),
            "the budget must be charged for every world read the sweep performed, or its cap bounds"
                + " nothing it can observe and the guard is dead");
        assertTrue(budget.spent() <= MimicryRules.MAX_DESTINATION_READS,
            "and it must never be charged past its own cap");
    }

    @Test
    void oneFullyEvaluatedCandidateCostsExactlyTheDeclaredPerCandidateReads() {
        final List<BlockPos> dry = flatWindow(40).stream().map(ORIGIN::offset)
            .map(BlockPos::below).toList();
        final CountingProbe probe = new CountingProbe(dry, true);
        final ReadBudget budget = ReadBudget.of(MimicryRules.MAX_DESTINATION_READS);

        MimicryRuntime.chooseEscapeDestination(probe, budget, ORIGIN, flatWindow(1));

        assertEquals(MimicryRules.READS_PER_DESTINATION_CANDIDATE, probe.readCount(),
            "READS_PER_DESTINATION_CANDIDATE is the arithmetic the candidate cap is derived from, so"
                + " a read added to the candidate test without a matching bump silently doubles the"
                + " sweep's real cost");
        assertEquals(probe.readCount(), budget.spent());
    }

    @Test
    void anExhaustedBudgetStopsTheSweepRatherThanSpinningTheRestOfTheWindow() {
        final int affordable = 2;
        final List<BlockPos> dry = flatWindow(40).stream().map(ORIGIN::offset)
            .map(BlockPos::below).toList();
        final CountingProbe probe = new CountingProbe(dry, true);
        final ReadBudget budget = ReadBudget.of(
            affordable * MimicryRules.READS_PER_DESTINATION_CANDIDATE + 2
        );

        MimicryRuntime.chooseEscapeDestination(probe, budget, ORIGIN, flatWindow(40));

        assertEquals(affordable, probe.candidateCount(),
            "an exhausted budget must break out of the window, not continue through it charging and"
                + " reading for the remaining thirty eight candidates doing nothing");
        assertEquals(affordable * MimicryRules.READS_PER_DESTINATION_CANDIDATE, budget.spent());
        assertEquals(2, budget.remaining(),
            "the tail that cannot fund one whole candidate is left unspent rather than half"
                + " evaluating one");
    }

    @Test
    void theFirstQualifyingCandidateWinsAndNothingAfterItIsRead() {
        final BlockPos winner = ORIGIN.offset(new BlockPos(3, 0, 0));
        final CountingProbe probe = new CountingProbe(List.of(winner.below()));
        final ReadBudget budget = ReadBudget.of(MimicryRules.MAX_DESTINATION_READS);

        final Optional<BlockPos> chosen =
            MimicryRuntime.chooseEscapeDestination(probe, budget, ORIGIN, flatWindow(20));

        assertEquals(Optional.of(winner), chosen);
        assertEquals(3, probe.candidateCount(), "the two rejects before it, and it");
        assertEquals(probe.readCount(), budget.spent());
    }

    @Test
    void theCandidateCapNeverAsksForMoreReadsThanTheBudgetCanPayFor() {
        assertTrue(
            MimicryRules.destinationCandidateCap() * MimicryRules.READS_PER_DESTINATION_CANDIDATE
                <= MimicryRules.MAX_DESTINATION_READS,
            "the window is sized from the budget, so a cap that overspends it would make every sweep"
                + " end on an exhausted budget instead of on a decision"
        );
        assertTrue(MimicryRules.destinationCandidateCap() > 0);
    }

    @Test
    void unloadedBorderBlockedFluidOccupiedAndNoFloorCandidatesAreAllRejected() {
        final List<CandidateProbe> rejected = List.of(
            new CandidateProbe(false, true, true, true, true, true, false),
            new CandidateProbe(true, false, true, true, true, true, false),
            new CandidateProbe(true, true, false, true, true, true, false),
            new CandidateProbe(true, true, true, false, true, true, false),
            new CandidateProbe(true, true, true, true, false, true, false),
            new CandidateProbe(true, true, true, true, true, false, false),
            new CandidateProbe(true, true, true, true, true, true, true));
        for (final CandidateProbe probe : rejected) {
            assertEquals(Optional.empty(), MimicryRuntime.chooseEscapeDestination(probe,
                ReadBudget.of(MimicryRules.MAX_DESTINATION_READS), ORIGIN, flatWindow(1)));
        }
        final CandidateProbe safe = new CandidateProbe(true, true, true, true, true, true, false);
        assertTrue(MimicryRuntime.chooseEscapeDestination(safe,
            ReadBudget.of(MimicryRules.MAX_DESTINATION_READS), ORIGIN, flatWindow(1)).isPresent());
    }
}

