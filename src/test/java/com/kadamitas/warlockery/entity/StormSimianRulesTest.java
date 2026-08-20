package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.StormSimianRules.Concern;
import com.kadamitas.warlockery.entity.StormSimianRules.Facts;
import com.kadamitas.warlockery.entity.StormSimianRules.Weather;
import com.kadamitas.warlockery.entity.behavior.ScanEnvelope;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** The pure Storm Simian kernel: arbitration, charge, envelope coverage and the declared caps. */
final class StormSimianRulesTest {

    private static Facts facts(final int bits) {
        return new Facts(
            (bits & 1) != 0,
            (bits & 2) != 0,
            (bits & 4) != 0,
            (bits & 8) != 0,
            (bits & 16) != 0,
            (bits & 32) != 0,
            (bits & 64) != 0,
            (bits & 128) != 0
        );
    }

    /** The concerns that apply to a fact set, written independently of {@code select}. */
    private static Set<Concern> applicable(final Facts subject) {
        final Set<Concern> active = EnumSet.of(Concern.IDLE);
        if (!subject.operational()) {
            return EnumSet.of(Concern.INVALID);
        }
        if (subject.hazard()) {
            active.add(Concern.HAZARD);
        }
        if (subject.combat()) {
            active.add(Concern.COMBAT);
        }
        if (subject.alarmDue()) {
            active.add(Concern.ALARM);
        }
        if (subject.ownerBeyondTether()) {
            active.add(Concern.OWNER_TETHER);
        }
        if (subject.gripDue()) {
            active.add(Concern.CANOPY);
        }
        if (subject.observationDue()) {
            active.add(Concern.STORM_WATCH);
        }
        if (subject.curiosityDue()) {
            active.add(Concern.CURIOSITY);
        }
        return active;
    }

    /**
     * The tick uses an explicit chain rather than {@link
     * com.kadamitas.warlockery.entity.behavior.PriorityLadder#select}, because that primitive copies
     * and sorts its rung list on every call and this runs twenty times a second per simian. The
     * ladder stays the specification, and this proves the fast path never drifts from it across
     * every one of the two hundred and fifty six reachable fact combinations.
     */
    @Test
    void theExplicitTickChainAgreesWithTheSharedLadderOnEveryFactCombination() {
        for (int bits = 0; bits < 256; bits++) {
            final Facts subject = facts(bits);
            assertEquals(
                StormSimianRules.CONCERN_LADDER.mostUrgent(applicable(subject)).orElseThrow(),
                StormSimianRules.select(subject),
                "fact bits " + bits
            );
        }
    }

    @Test
    void everyConcernIsRankedExactlyOnceAndUrgencyRunsHazardFirstIdleLast() {
        assertEquals(List.of(Concern.values()), StormSimianRules.CONCERN_LADDER.order());
        assertTrue(StormSimianRules.preempts(Concern.HAZARD, Concern.COMBAT));
        assertTrue(StormSimianRules.preempts(Concern.COMBAT, Concern.CURIOSITY));
        assertTrue(StormSimianRules.preempts(Concern.ALARM, Concern.STORM_WATCH));
        assertTrue(StormSimianRules.preempts(Concern.CANOPY, Concern.IDLE));
        assertFalse(StormSimianRules.preempts(Concern.CURIOSITY, Concern.CANOPY));
        assertFalse(StormSimianRules.preempts(Concern.IDLE, Concern.IDLE));
    }

    @Test
    void onlyTheTwoMovementConcernsMayWriteNavigation() {
        final Set<Concern> writers = EnumSet.noneOf(Concern.class);
        for (final Concern concern : Concern.values()) {
            if (StormSimianRules.writesNavigation(concern)) {
                writers.add(concern);
            }
        }
        assertEquals(EnumSet.of(Concern.CANOPY, Concern.CURIOSITY), writers,
            "the alarm, the observation and the frozen support contracts never write navigation");
    }

    @Test
    void exactlyThreeConcernsOpenAFiniteWindowAndTheRestOpenNone() {
        assertEquals(StormSimianRules.ALARM_WINDOW_TICKS,
            StormSimianRules.windowTicks(Concern.ALARM));
        assertEquals(StormSimianRules.INSPECT_WINDOW_TICKS,
            StormSimianRules.windowTicks(Concern.CURIOSITY));
        assertEquals(StormSimianRules.OBSERVE_WINDOW_TICKS,
            StormSimianRules.windowTicks(Concern.STORM_WATCH));
        for (final Concern concern : List.of(Concern.INVALID, Concern.HAZARD, Concern.COMBAT,
            Concern.OWNER_TETHER, Concern.CANOPY, Concern.IDLE)) {
            assertEquals(0, StormSimianRules.windowTicks(concern), concern.name());
        }
    }

    // ---------------------------------------------------------------- storm charge

    @Test
    void chargeIsReadFromTheWeatherAndNeverFromElapsedTime() {
        assertEquals(Weather.THUNDER, StormSimianRules.weatherOf(true, true));
        assertEquals(Weather.THUNDER, StormSimianRules.weatherOf(false, true));
        assertEquals(Weather.RAIN, StormSimianRules.weatherOf(true, false));
        assertEquals(Weather.CLEAR, StormSimianRules.weatherOf(false, false));
        assertEquals(StormSimianRules.THUNDER_CHARGE_GAIN,
            StormSimianRules.chargeAfterObservation(0, Weather.THUNDER));
        assertEquals(StormSimianRules.RAIN_CHARGE_GAIN,
            StormSimianRules.chargeAfterObservation(0, Weather.RAIN));
        assertEquals(0, StormSimianRules.chargeAfterObservation(0, Weather.CLEAR),
            "clear weather bleeds charge away and can never take it below zero");
        assertEquals(50 - StormSimianRules.CLEAR_CHARGE_DECAY,
            StormSimianRules.chargeAfterObservation(50, Weather.CLEAR));
        assertEquals(StormSimianRules.MAX_CHARGE,
            StormSimianRules.chargeAfterObservation(StormSimianRules.MAX_CHARGE, Weather.THUNDER),
            "one hundred is the ceiling, so a long storm cannot bank an unbounded reserve");
    }

    @Test
    void aChargedGustCostsExactlyOnceAndOnlyWhenItWasReady() {
        assertFalse(StormSimianRules.chargedGustReady(StormSimianRules.CHARGED_GUST_COST - 1));
        assertTrue(StormSimianRules.chargedGustReady(StormSimianRules.CHARGED_GUST_COST));
        assertEquals(0, StormSimianRules.chargeAfterGust(StormSimianRules.CHARGED_GUST_COST));
        assertEquals(39, StormSimianRules.chargeAfterGust(39),
            "an attack below the threshold spends nothing at all");
        assertEquals(StormSimianRules.BASE_GUST_POWER, StormSimianRules.gustPower(39));
        assertEquals(StormSimianRules.CHARGED_GUST_POWER,
            StormSimianRules.gustPower(StormSimianRules.MAX_CHARGE));
        assertTrue(StormSimianRules.CHARGED_GUST_POWER < 2.0F,
            "potency is bounded: the charged gust is still one owned wind charge");
    }

    // ---------------------------------------------------------------- canopy envelope

    /**
     * Recurring defect three, the search that never leaves the innermost ring. Hand traced case: a
     * simian at the origin with a sixteen offset budget over a two hundred and forty five offset
     * envelope. A raster or ring walk that stopped at sixteen would evaluate only the sixteen
     * nearest offsets forever, so the far corner at (3, 2, 3) would never be read even once. Here
     * the union of {@code scansToCover} successive windows is asserted to be the entire envelope,
     * that exact far corner included, while the origin stays in the fixed anchor of every window.
     */
    @Test
    void successiveCanopySweepsCoverTheWholeEnvelopeIncludingItsFarCornerAndItsOwnPosition() {
        final ScanEnvelope envelope = StormSimianRules.gripEnvelope();
        final int cap = StormSimianRules.GRIP_CANDIDATE_CAP;
        assertEquals(
            (2 * StormSimianRules.GRIP_HORIZONTAL_RADIUS + 1)
                * (2 * StormSimianRules.GRIP_HORIZONTAL_RADIUS + 1)
                * (2 * StormSimianRules.GRIP_VERTICAL_RADIUS + 1),
            envelope.size());
        final Set<BlockPos> covered = new HashSet<>();
        int cursor = 0;
        for (int scan = 0; scan < envelope.scansToCover(cap); scan++) {
            final List<BlockPos> window = envelope.window(cap, cursor);
            assertTrue(window.contains(BlockPos.ZERO),
                "the fixed anchor keeps the simian's own position in every single sweep");
            assertTrue(window.size() <= cap, "a sweep never exceeds the sixteen candidate cap");
            covered.addAll(window);
            cursor = envelope.advanceCursor(cap, cursor);
        }
        assertEquals(envelope.size(), covered.size(),
            "the union of successive sweeps is the whole envelope, not the innermost ring");
        assertTrue(covered.contains(new BlockPos(
                StormSimianRules.GRIP_HORIZONTAL_RADIUS,
                StormSimianRules.GRIP_VERTICAL_RADIUS,
                StormSimianRules.GRIP_HORIZONTAL_RADIUS)),
            "the far corner of the envelope is genuinely reached");
        assertEquals(StormSimianRules.gripScansToCover(), envelope.scansToCover(cap));
    }

    @Test
    void theCandidateBudgetTimesTheHonestPerCandidateCostFitsTheDeclaredReadCeiling() {
        assertTrue(
            StormSimianRules.GRIP_CANDIDATE_CAP * StormSimianRules.READS_PER_GRIP_CANDIDATE
                <= StormSimianRules.GRIP_READ_CAP,
            "every visited candidate can afford all four of its charged reads");
        assertEquals(16, StormSimianRules.GRIP_CANDIDATE_CAP);
        assertEquals(64, StormSimianRules.GRIP_READ_CAP);
    }

    @Test
    void differentSimiansStartOnDifferentPagesSoATroopDoesNotScanInLockstep() {
        final Set<Integer> seeds = new HashSet<>();
        for (int index = 0; index < 32; index++) {
            seeds.add(StormSimianRules.seedGripCursor(new UUID(index * 2_654_435_761L, index)));
        }
        assertTrue(seeds.size() > 1, "a UUID seeded cursor must not collapse to one page");
    }

    @Test
    void aGripMustBeLoadedClearAboveAndBelowSupportedBeforeItCounts() {
        assertTrue(StormSimianRules.gripAcceptable(true, true, true, true));
        assertFalse(StormSimianRules.gripAcceptable(false, true, true, true));
        assertFalse(StormSimianRules.gripAcceptable(true, false, true, true));
        assertFalse(StormSimianRules.gripAcceptable(true, true, false, true));
        assertFalse(StormSimianRules.gripAcceptable(true, true, true, false),
            "an unsupported position is a hover, not a grip");
    }

    @Test
    void bothMovementWritersShareOneCadenceAndOneBackoffPolicy() {
        assertEquals(20, StormSimianRules.ROUTE_PERIOD_TICKS);
        assertEquals(3, StormSimianRules.ROUTE_FAILURES_BEFORE_BACKOFF);
        assertEquals(0, StormSimianRules.ROUTE_BACKOFF.windowAfter(2),
            "two failures do not stop a simian trying");
        assertEquals(StormSimianRules.ROUTE_BACKOFF_BASE_TICKS,
            StormSimianRules.ROUTE_BACKOFF.windowAfter(3),
            "the third consecutive failure buys at least a hundred ticks of quiet");
        assertTrue(StormSimianRules.ROUTE_BACKOFF.windowAfter(30)
            <= StormSimianRules.ROUTE_BACKOFF_MAX_TICKS);
    }
}
