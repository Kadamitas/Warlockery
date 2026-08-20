package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.UmbralSigilRuntime.Seal;
import com.kadamitas.warlockery.entity.UmbralSigilRuntime.TransientState;
import com.kadamitas.warlockery.entity.behavior.Cadence;
import com.kadamitas.warlockery.entity.behavior.ReadBudget;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import com.kadamitas.warlockery.entity.behavior.Ticks;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Deterministic guards for the parts of the one Umbral Sigil controller that can be proved without
 * a server. The live behavior itself is asserted by the six spawned self-ticking GameTest fixtures;
 * these pin the invariants a live fixture would only catch by accident.
 */
final class UmbralSigilRuntimeTest {

    private static final String DIMENSION = "minecraft:overworld";

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---------------------------------------------------------------- the identity reconcile

    /**
     * Identity shape, not timer shape. The seal's three components are one identity, so a
     * half-written one collapses to none; no duration is involved, so no tick branch loses the
     * ending it owns.
     */
    @Test
    void aHalfWrittenSealCollapsesToNoneAndAWholeOneSurvives() {
        final UUID subject = UUID.randomUUID();
        final BlockPos centre = new BlockPos(4, 64, 9);
        final Seal whole = Seal.of(subject, DIMENSION, centre);
        assertTrue(whole.appointed());
        assertEquals(Optional.of(subject), whole.subject());
        assertEquals(Optional.of(centre), whole.centre());

        assertFalse(new Seal(Optional.of(subject), Optional.empty(), Optional.of(centre))
            .appointed(), "a subject in no dimension names nobody");
        assertFalse(new Seal(Optional.of(subject), Optional.of(DIMENSION), Optional.empty())
            .appointed(), "a subject with no centre is enclosed by nothing");
        assertFalse(new Seal(Optional.empty(), Optional.of(DIMENSION), Optional.of(centre))
            .appointed(), "a centre around nobody encloses nobody");
        assertFalse(new Seal(Optional.of(subject), Optional.of("  "), Optional.of(centre))
            .appointed(), "a blank dimension is not a dimension");
        // Collapsing is total: no component survives alone.
        final Seal collapsed =
            new Seal(Optional.of(subject), Optional.empty(), Optional.of(centre));
        assertEquals(Optional.empty(), collapsed.subject());
        assertEquals(Optional.empty(), collapsed.centre());
        assertEquals(Seal.none(), collapsed);
    }

    @Test
    void loadScratchIsFullyClearedSoNoSealSurvivesAReload() {
        final TransientState scratch = new TransientState();
        scratch.reconciled = true;
        scratch.hazardActive = true;
        scratch.hazardCooldownTicks = 17;
        scratch.destination = new BlockPos(1, 2, 3);
        scratch.seal = Seal.of(UUID.randomUUID(), DIMENSION, BlockPos.ZERO);
        scratch.selectCadence = Cadence.armed(UmbralSigilRules.SELECT_INTERVAL_TICKS);
        assertTrue(scratch.appointed());

        scratch.resetForLoad();
        assertFalse(scratch.reconciled);
        assertFalse(scratch.hazardActive());
        assertEquals(0, scratch.hazardCooldownTicks);
        assertEquals(null, scratch.destination);
        assertFalse(scratch.appointed(), "a reloaded Sigil is sealing nobody");
        assertEquals(Optional.empty(), scratch.centre());
    }

    // ---------------------------------------------------------------- charged reads

    /**
     * The budget defect, reproduced against the exact appointment ceiling. A budget that only
     * charged accepted candidates would let all eight rejections cost real reads for free, and the
     * declared cap could never bind. Here the reader is charged first, so rejecting every candidate
     * costs exactly what accepting every candidate costs.
     */
    @Test
    void aRejectedAppointmentCandidateCostsExactlyWhatAnAcceptedOneCosts() {
        final ReadBudget rejecting = ReadBudget.of(UmbralSigilRules.MAX_APPOINTMENT_READS);
        final ReadBudget accepting = ReadBudget.of(UmbralSigilRules.MAX_APPOINTMENT_READS);
        for (int candidate = 0; candidate < UmbralSigilRules.MAX_PLAYER_CANDIDATES; candidate++) {
            assertFalse(rejecting.accepts(() -> "candidate", String::isEmpty),
                "this candidate is genuinely rejected");
            assertTrue(accepting.accepts(() -> "candidate", value -> !value.isEmpty()),
                "this candidate is genuinely accepted");
        }
        assertEquals(accepting.spent(), rejecting.spent());
        assertEquals(UmbralSigilRules.MAX_PLAYER_CANDIDATES, rejecting.spent());
        assertEquals(UmbralSigilRules.MAX_LINE_OF_SIGHT_CHECKS, rejecting.remaining(),
            "what is left is exactly the sight-walk allowance");
    }

    @Test
    void theAppointmentBudgetGenuinelyBindsAndStopsSpending() {
        final ReadBudget budget = ReadBudget.of(UmbralSigilRules.MAX_APPOINTMENT_READS);
        for (int read = 0; read < UmbralSigilRules.MAX_APPOINTMENT_READS; read++) {
            assertTrue(budget.charge(), "read " + read + " is inside the declared ceiling");
        }
        assertTrue(budget.exhausted());
        assertFalse(budget.charge(), "the ceiling binds; nothing beyond it may be read");
        assertEquals(UmbralSigilRules.MAX_APPOINTMENT_READS, budget.spent());
    }

    // ---------------------------------------------------------------- route ledger

    /**
     * A request that qualified nothing costs the same real work as one that failed to path, so it
     * must arm the same cadence and count the same failure. Without that a Sigil facing an unusable
     * vertex would re-qualify the same block on every single tick and the cap could never bind.
     */
    @Test
    void aRequestThatQualifiedNothingArmsTheCadenceExactlyLikeAFailedPath() {
        final RouteRequest.RouteBackoff policy = UmbralSigilRules.routeBackoff();
        final RouteRequest fromNoCandidate = UmbralSigilRules.freshRoute().failed(policy);
        final RouteRequest fromNoPath = UmbralSigilRules.freshRoute().failed(policy);
        assertEquals(fromNoPath, fromNoCandidate);
        assertFalse(fromNoCandidate.mayRequest(), "the cadence is armed whatever the reason");
        assertEquals(1, fromNoCandidate.consecutiveFailures());
        // Every non-accepted outcome the runtime can produce is a failure, including the one the
        // hand-rolled versions dropped on the floor.
        assertEquals(RouteRequest.Outcome.NO_PATH, RouteRequest.outcomeOf(false, false, false));
        assertEquals(RouteRequest.Outcome.UNREACHABLE, RouteRequest.outcomeOf(true, false, false));
        assertEquals(RouteRequest.Outcome.REFUSED, RouteRequest.outcomeOf(true, true, false));
        assertEquals(RouteRequest.Outcome.ACCEPTED, RouteRequest.outcomeOf(true, true, true));
        assertFalse(RouteRequest.Outcome.NO_CANDIDATE.accepted());
        assertFalse(RouteRequest.Outcome.NO_PATH.accepted());
        assertFalse(RouteRequest.Outcome.UNREACHABLE.accepted());
        assertFalse(RouteRequest.Outcome.REFUSED.accepted());
        assertTrue(RouteRequest.Outcome.ACCEPTED.accepted());
    }

    @Test
    void theThirdConsecutiveFailureOpensExactlyOneBackoffWindow() {
        final RouteRequest.RouteBackoff policy = UmbralSigilRules.routeBackoff();
        RouteRequest request = UmbralSigilRules.freshRoute();
        for (int failure = 1; failure < UmbralSigilRules.MAX_ROUTE_FAILURES; failure++) {
            request = request.failed(policy);
            assertEquals(0, request.backoffRemaining(),
                "failure " + failure + " is still inside the allowance");
            assertFalse(UmbralSigilRules.routeExhausted(request.consecutiveFailures()));
        }
        request = request.failed(policy);
        assertEquals(UmbralSigilRules.MAX_ROUTE_FAILURES, request.consecutiveFailures());
        assertTrue(UmbralSigilRules.routeExhausted(request.consecutiveFailures()));
        assertEquals(UmbralSigilRules.ROUTE_BACKOFF_TICKS, request.backoffRemaining());
        assertFalse(request.mayRequest());
        // Flat, not geometric: a released seal must not compound its successor's wait.
        assertEquals(UmbralSigilRules.ROUTE_BACKOFF_TICKS,
            policy.windowAfter(UmbralSigilRules.MAX_ROUTE_FAILURES + 5));

        final RouteRequest recovered = request.succeeded();
        assertEquals(0, recovered.consecutiveFailures());
        assertEquals(0, recovered.backoffRemaining());
    }

    @Test
    void anOpenBackoffDecaysOnlyWhileTheSigilIsLoaded() {
        RouteRequest request = UmbralSigilRules.freshRoute()
            .failed(UmbralSigilRules.routeBackoff())
            .failed(UmbralSigilRules.routeBackoff())
            .failed(UmbralSigilRules.routeBackoff());
        for (int tick = 0; tick < UmbralSigilRules.ROUTE_BACKOFF_TICKS; tick++) {
            assertFalse(request.mayRequest(), "still inside the backoff at tick " + tick);
            request = request.step();
        }
        assertEquals(0, request.backoffRemaining());
        assertTrue(request.mayRequest(), "the window closes after exactly its declared ticks");
        assertEquals(0, Ticks.decrementLoaded(0), "a countdown never goes below zero");
    }

    // ---------------------------------------------------------------- cadence

    @Test
    void theSelectionCadenceIsArmedByRunningNotBySucceeding() {
        Cadence cadence = Cadence.every(UmbralSigilRules.SELECT_INTERVAL_TICKS);
        assertTrue(cadence.due(), "an offered cadence starts due");
        cadence = cadence.arm();
        for (int tick = 0; tick < UmbralSigilRules.SELECT_INTERVAL_TICKS; tick++) {
            assertFalse(cadence.due(), "a sweep that found nobody still costs a full period");
            cadence = cadence.step();
        }
        assertTrue(cadence.due());
    }

    @Test
    void theSelectionStaggerIsDeterministicAndStaysInsideOnePeriod() {
        final UUID identity = UUID.fromString("2a4a0d5c-9b2e-4f1a-9d31-6e5b7c8a0f42");
        final int first = Ticks.stableOffset(identity, UmbralSigilRules.SELECT_INTERVAL_TICKS);
        assertEquals(first, Ticks.stableOffset(identity, UmbralSigilRules.SELECT_INTERVAL_TICKS));
        assertTrue(first >= 0 && first < UmbralSigilRules.SELECT_INTERVAL_TICKS);
        final Cadence staggered = new Cadence(UmbralSigilRules.SELECT_INTERVAL_TICKS, first);
        assertFalse(staggered.due(), "a fresh Sigil never sweeps on its very first loaded tick");
    }

    // ---------------------------------------------------------------- hazards

    @Test
    void theContactHazardPredicateCoversTheDeclaredBlocksAndNothingHarmless() {
        assertTrue(UmbralSigilRuntime.isHazardBlock(Blocks.FIRE.defaultBlockState()));
        assertTrue(UmbralSigilRuntime.isHazardBlock(Blocks.SOUL_FIRE.defaultBlockState()));
        assertTrue(UmbralSigilRuntime.isHazardBlock(Blocks.CAMPFIRE.defaultBlockState()));
        assertTrue(UmbralSigilRuntime.isHazardBlock(Blocks.SOUL_CAMPFIRE.defaultBlockState()));
        assertTrue(UmbralSigilRuntime.isHazardBlock(Blocks.LAVA.defaultBlockState()));
        assertFalse(UmbralSigilRuntime.isHazardBlock(Blocks.AIR.defaultBlockState()));
        assertFalse(UmbralSigilRuntime.isHazardBlock(Blocks.STONE.defaultBlockState()));
        assertFalse(UmbralSigilRuntime.isHazardBlock(Blocks.SOUL_LANTERN.defaultBlockState()),
            "a soul lantern is scenery to this species, not a hazard and not an errand");
        assertNotNull(UmbralSigilRuntime.CONTACT_HAZARDS);
    }

    /**
     * The innermost-ring trace, done by enumeration rather than by argument. The hazard budget
     * equals the neighbourhood volume, so the loops cannot be truncated: the Sigil's own block and
     * every far corner are reachable within one sample.
     */
    @Test
    void theHazardNeighbourhoodEnumeratesItsOwnBlockAndEveryFarCorner() {
        int reads = 0;
        boolean sawOwnBlock = false;
        int corners = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    assertTrue(reads < UmbralSigilRules.MAX_HAZARD_READS,
                        "the ceiling must never cut the sweep short");
                    reads++;
                    sawOwnBlock |= dx == 0 && dy == 0 && dz == 0;
                    corners += Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 3 ? 1 : 0;
                }
            }
        }
        assertEquals(UmbralSigilRules.MAX_HAZARD_READS, reads);
        assertTrue(sawOwnBlock, "the entity's own position is evaluated, never skipped");
        assertEquals(8, corners, "all eight far corners are inside one sample");
    }

    // ---------------------------------------------------------------- counters

    @Test
    void freshCountersStartAtZeroSoNoFixtureCanReadAPrimedNumber() {
        final UmbralSigilRuntime.Counters counters = new UmbralSigilRuntime.Counters();
        assertEquals(0L, counters.appointmentSweeps());
        assertEquals(0L, counters.appointmentCandidateVisits());
        assertEquals(0L, counters.appointmentReads());
        assertEquals(0L, counters.lineOfSightChecks());
        assertEquals(0L, counters.appointmentFailures());
        assertEquals(0L, counters.sealsStarted());
        assertEquals(0L, counters.sealsEnded());
        assertEquals(0L, counters.verticesReached());
        assertEquals(0L, counters.strikes());
        assertEquals(0L, counters.hazardSamples());
        assertEquals(0L, counters.hazardInterruptions());
        assertEquals(0L, counters.blockReads());
        assertEquals(0L, counters.navigationRequests());
        assertEquals(0L, counters.routeFailures());
        assertEquals(0L, counters.unroutableRequests());
        assertEquals(0L, counters.pathStartsDenied());
    }
}
