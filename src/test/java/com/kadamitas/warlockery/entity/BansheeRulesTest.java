package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.BansheeRules.AttackerObservation;
import com.kadamitas.warlockery.entity.BansheeRules.Candidate;
import com.kadamitas.warlockery.entity.BansheeRules.Mode;
import com.kadamitas.warlockery.entity.BansheeRules.ReleaseReason;
import com.kadamitas.warlockery.entity.BansheeRules.RouteResult;
import com.kadamitas.warlockery.entity.BansheeRules.StandoffAction;
import com.kadamitas.warlockery.entity.BansheeRules.SubjectObservation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class BansheeRulesTest {
    private static final UUID LOW = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID MID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID HIGH = UUID.fromString("ffffffff-ffff-4fff-bfff-fffffffffffe");

    @Test
    void everyApprovedConstantIsExact() {
        assertEquals(0.40F, BansheeRules.ACQUIRE_HEALTH_FRACTION);
        assertEquals(0.60F, BansheeRules.RELEASE_HEALTH_FRACTION);
        assertEquals(60, BansheeRules.RECOVERY_TICKS);
        assertEquals(24, BansheeRules.ACQUIRE_RANGE);
        assertEquals(32, BansheeRules.RELEASE_RANGE);
        assertEquals(576.0D, BansheeRules.ACQUIRE_RANGE_SQUARED);
        assertEquals(60, BansheeRules.MISSING_GRACE_TICKS);
        assertEquals(60, BansheeRules.LOST_SIGHT_TICKS);
        assertEquals(400, BansheeRules.EPISODE_TICKS);
        assertEquals(200, BansheeRules.REACQUIRE_COOLDOWN_TICKS);
        assertEquals(40, BansheeRules.DISCOVERY_INTERVAL_TICKS);
        assertEquals(16, BansheeRules.MAX_CANDIDATES_VISITED);
        assertEquals(4, BansheeRules.MAX_RETAINED_CANDIDATES);
        assertEquals(4, BansheeRules.MAX_LINE_OF_SIGHT_CHECKS);
        assertEquals(20, BansheeRules.SUBJECT_SIGHT_INTERVAL_TICKS);
        assertEquals(6, BansheeRules.STANDOFF_MIN);
        assertEquals(10, BansheeRules.STANDOFF_MAX);
        assertEquals(20, BansheeRules.WARNING_HOLD_TICKS);
        assertEquals(80, BansheeRules.WARNING_PULSE_INTERVAL_TICKS);
        assertEquals(3, BansheeRules.MAX_WARNING_PULSES);
        assertEquals(12, BansheeRules.MAX_WARNING_PARTICLES);
        assertEquals(120, BansheeRules.LAMENT_TICKS);
        assertEquals(60, BansheeRules.LAMENT_PULSE_INTERVAL_TICKS);
        assertEquals(2, BansheeRules.MAX_LAMENT_PULSES);
        assertEquals(12, BansheeRules.MAX_LAMENT_PARTICLES);
        assertEquals(4, BansheeRules.LAMENT_STANDOFF_MIN);
        assertEquals(8, BansheeRules.LAMENT_STANDOFF_MAX);
        assertEquals(120, BansheeRules.TABOO_EFFECT_TICKS);
        assertEquals(120, BansheeRules.TABOO_COOLDOWN_TICKS);
        assertEquals(60, BansheeRules.RECOIL_TICKS);
        assertEquals(4, BansheeRules.RECOIL_SEARCH_HORIZONTAL);
        assertEquals(2, BansheeRules.RECOIL_SEARCH_VERTICAL);
        assertEquals(16, BansheeRules.MAX_RECOIL_PARTICLES);
        assertEquals(10, BansheeRules.ANCHOR_COMFORT_HORIZONTAL);
        assertEquals(6, BansheeRules.ANCHOR_COMFORT_VERTICAL);
        assertEquals(16, BansheeRules.ANCHOR_RETURN_HORIZONTAL);
        assertEquals(10, BansheeRules.ANCHOR_RETURN_VERTICAL);
        assertEquals(200, BansheeRules.ANCHOR_UNAVAILABLE_TICKS);
        assertEquals(100, BansheeRules.IDLE_DESTINATION_INTERVAL_TICKS);
        assertEquals(8, BansheeRules.IDLE_RADIUS_HORIZONTAL);
        assertEquals(4, BansheeRules.IDLE_RADIUS_VERTICAL);
        assertEquals(12, BansheeRules.MAX_IDLE_CANDIDATES);
        assertEquals(24, BansheeRules.MAX_SAFE_CANDIDATES);
        assertEquals(256, BansheeRules.MAX_CHARGED_READS);
        assertEquals(20, BansheeRules.HAZARD_INTERVAL_TICKS);
        assertEquals(27, BansheeRules.MAX_HAZARD_READS);
        assertEquals(20, BansheeRules.PATH_INTERVAL_TICKS);
        assertEquals(3, BansheeRules.MAX_ROUTE_FAILURES);
        assertEquals(100, BansheeRules.ROUTE_BACKOFF_TICKS);
        assertEquals(8, BansheeRules.MAX_FEEDBACK_NEIGHBOURS);
        assertEquals(24, BansheeRules.FEEDBACK_RADIUS);
        assertEquals(200, BansheeRules.AMBIENT_INTERVAL_TICKS);
        assertEquals(6, BansheeRules.MAX_AMBIENT_PARTICLES);
        assertEquals(20, BansheeRules.LAMENT_FIRST_PULSE_DELAY_TICKS);
    }

    @Test
    void healthEligibilityUsesInclusiveFortyPercentAndFiniteMaxima() {
        assertTrue(BansheeRules.atRisk(0.40F));
        assertFalse(BansheeRules.atRisk(0.401F));
        assertTrue(BansheeRules.recovered(0.60F));
        assertFalse(BansheeRules.recovered(0.599F));
        assertEquals(1.0F, BansheeRules.healthFraction(5.0F, 0.0F),
            "non-positive max health can never look at risk");
        assertEquals(1.0F, BansheeRules.healthFraction(5.0F, Float.NaN));
        assertEquals(1.0F, BansheeRules.healthFraction(Float.POSITIVE_INFINITY, 20.0F));
        assertEquals(0.25F, BansheeRules.healthFraction(5.0F, 20.0F));
    }

    @Test
    void safeSearchOffsetsSpanTheFullEnvelopeWithinTheCandidateCap() {
        final List<BansheeRules.SafeSearchOffset> offsets =
            BansheeRules.safeSearchOffsets(LOW, 4, 2, BansheeRules.MAX_SAFE_CANDIDATES);
        assertTrue(offsets.size() <= BansheeRules.MAX_SAFE_CANDIDATES);
        assertTrue(offsets.size() >= 16, "the deterministic pattern genuinely uses its budget");
        assertEquals(offsets, BansheeRules.safeSearchOffsets(LOW, 4, 2, BansheeRules.MAX_SAFE_CANDIDATES),
            "offset generation is deterministic per entity");
        assertTrue(offsets.stream().anyMatch(offset -> offset.dx() == 4),
            "the positive-x side of the envelope is always evaluated");
        assertTrue(offsets.stream().anyMatch(offset -> offset.dx() == -4));
        assertTrue(offsets.stream().anyMatch(offset -> offset.dz() == 4));
        assertTrue(offsets.stream().anyMatch(offset -> offset.dz() == -4));
        assertTrue(offsets.stream().anyMatch(offset -> offset.dy() == 2),
            "the vertical envelope is spanned upward");
        assertTrue(offsets.stream().anyMatch(offset -> offset.dy() == -2),
            "the vertical envelope is spanned downward");
        for (final BansheeRules.SafeSearchOffset offset : offsets) {
            assertTrue(Math.abs(offset.dx()) <= 4 && Math.abs(offset.dy()) <= 2
                && Math.abs(offset.dz()) <= 4, "offsets stay inside the declared envelope");
            assertFalse(offset.dx() == 0 && offset.dy() == 0 && offset.dz() == 0,
                "the origin is never a candidate");
        }
        assertEquals(offsets.size(), offsets.stream().distinct().count(),
            "no duplicate candidate wastes the budget");
        final List<BansheeRules.SafeSearchOffset> idle =
            BansheeRules.safeSearchOffsets(LOW, 8, 4, BansheeRules.MAX_IDLE_CANDIDATES);
        assertTrue(idle.size() <= BansheeRules.MAX_IDLE_CANDIDATES,
            "idle roam uses at most twelve of the unified candidate budget");
        assertTrue(idle.stream().anyMatch(offset -> offset.dx() > 0)
                && idle.stream().anyMatch(offset -> offset.dx() < 0),
            "even the reduced idle budget spans both horizontal signs");
    }

    @Test
    void safeCandidatePreferenceIsLexicographicNotWeighted() {
        final var order = BansheeRules.safeCandidatePreference();
        final var farUnsafe = new BansheeRules.SafeCandidate(100.0D, false, 1.0D, 5L);
        final var nearSafe = new BansheeRules.SafeCandidate(9.0D, true, 1.0D, 1L);
        assertTrue(order.compare(farUnsafe, nearSafe) < 0,
            "greater separation from the attacker dominates every later criterion");
        final var safeFar = new BansheeRules.SafeCandidate(100.0D, true, 64.0D, 9L);
        assertTrue(order.compare(safeFar, farUnsafe) < 0,
            "hazard safety is the second criterion at equal separation");
        final var safeNear = new BansheeRules.SafeCandidate(100.0D, true, 4.0D, 9L);
        assertTrue(order.compare(safeNear, safeFar) < 0,
            "shorter displacement is the third criterion");
        final var tieLow = new BansheeRules.SafeCandidate(100.0D, true, 4.0D, 2L);
        assertTrue(order.compare(tieLow, safeNear) < 0,
            "the stable packed position is the final tie-break");
    }

    @Test
    void suppressionDuenessAndEventIdentityAreExact() {
        assertTrue(BansheeRules.neighbourPulseDue(0, 0, 3, false), "a due neighbour suppresses");
        assertFalse(BansheeRules.neighbourPulseDue(40, 0, 3, false),
            "a neighbour whose pulse is not due can never suppress a due pulse");
        assertFalse(BansheeRules.neighbourPulseDue(0, 3, 3, false),
            "an exhausted neighbour can never suppress");
        assertTrue(BansheeRules.neighbourPulseDue(80, 1, 3, true),
            "a neighbour that advanced its schedule this very tick still counts as due");
        assertTrue(BansheeRules.sameWarningEvent(Optional.of(LOW), Optional.of(LOW)));
        assertFalse(BansheeRules.sameWarningEvent(Optional.of(LOW), Optional.of(MID)));
        assertFalse(BansheeRules.sameWarningEvent(Optional.empty(), Optional.empty()),
            "no subject means no shared event");
        assertTrue(BansheeRules.sameDeathEvent(
            Optional.of(7L), Optional.of("minecraft:overworld"),
            Optional.of(7L), Optional.of("minecraft:overworld")));
        assertFalse(BansheeRules.sameDeathEvent(
            Optional.of(7L), Optional.of("minecraft:overworld"),
            Optional.of(8L), Optional.of("minecraft:overworld")),
            "a lament for a different death never suppresses this one");
        assertFalse(BansheeRules.sameDeathEvent(
            Optional.of(7L), Optional.of("minecraft:overworld"),
            Optional.of(7L), Optional.of("minecraft:the_nether")));
        assertFalse(BansheeRules.sameDeathEvent(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    }

    @Test
    void rankingIsOwnerFirstThenHealthThenDistanceThenUuidAndBounded() {
        final Candidate owner = new Candidate(HIGH, true, 0.39F, 500.0D, true);
        final Candidate hurt = new Candidate(MID, false, 0.10F, 400.0D, true);
        final Candidate close = new Candidate(LOW, false, 0.10F, 4.0D, true);
        final List<Candidate> ranked = BansheeRules.rank(List.of(hurt, close, owner));
        assertEquals(owner, ranked.get(0), "owner priority wins over lower health");
        assertEquals(close, ranked.get(1), "equal health prefers shorter distance");
        assertEquals(hurt, ranked.get(2));
        final List<Candidate> tied = BansheeRules.rank(List.of(
            new Candidate(MID, false, 0.2F, 4.0D, true),
            new Candidate(LOW, false, 0.2F, 4.0D, true)
        ));
        assertEquals(LOW, tied.get(0).id(), "UUID is the final deterministic tie-break");
        final List<Candidate> flood = BansheeRules.rank(IntStream.range(0, 40)
            .mapToObj(index -> new Candidate(UUID.nameUUIDFromBytes(new byte[] {(byte) index}),
                false, 0.2F, index, true))
            .toList());
        assertEquals(BansheeRules.MAX_RETAINED_CANDIDATES, flood.size(),
            "at most four preliminary candidates are retained");
        assertTrue(BansheeRules.select(List.of(new Candidate(LOW, false, 0.2F, 4.0D, false))).isEmpty(),
            "an invisible candidate is never selected");
    }

    @Test
    void releaseReasonsFollowTheApprovedOrderAndBoundaries() {
        assertEquals(ReleaseReason.NONE, releaseWith(observation -> observation));
        assertEquals(ReleaseReason.INVALID_PLAYER,
            release(true, true, true, false, true, 0.5F, 100.0D, 0, 0, 0, 100, 0),
            "creative or spectator conversion releases immediately");
        assertEquals(ReleaseReason.INVALID_PLAYER,
            release(true, true, false, false, true, 0.5F, 100.0D, 0, 0, 0, 100, 0));
        assertEquals(ReleaseReason.NONE,
            release(false, true, false, false, false, 1.0F, 0.0D, 0, 0, 0, 100, 0),
            "an unresolved subject is missing, never an invalid identity, and no death is inferred");
        assertEquals(ReleaseReason.DIMENSION,
            release(true, false, true, true, true, 0.5F, 100.0D, 0, 0, 0, 100, 0));
        assertEquals(ReleaseReason.RANGE,
            release(true, true, true, true, true, 0.5F, 1_025.0D, 0, 0, 0, 100, 0));
        assertEquals(ReleaseReason.NONE, releaseMissing(59, 100), "missing grace holds through 59 ticks");
        assertEquals(ReleaseReason.MISSING, releaseMissing(60, 100));
        assertEquals(ReleaseReason.LOST_SIGHT,
            release(true, true, true, true, false, 0.5F, 100.0D, 0, 60, 0, 100, 0));
        assertEquals(ReleaseReason.NONE,
            release(true, true, true, true, true, 0.65F, 100.0D, 0, 0, 59, 100, 0),
            "sixty recovered ticks are required, hysteresis holds through the fifty-ninth");
        assertEquals(ReleaseReason.RECOVERED,
            release(true, true, true, true, true, 0.60F, 100.0D, 0, 0, 60, 100, 0));
        assertEquals(ReleaseReason.NONE,
            release(true, true, true, true, true, 0.55F, 100.0D, 0, 0, 60, 100, 0),
            "hysteresis: sustained ticks without recovered health never release");
        assertEquals(ReleaseReason.EPISODE_EXPIRED,
            release(true, true, true, true, true, 0.5F, 100.0D, 0, 0, 0, 0, 0));
        assertEquals(ReleaseReason.ROUTE_FAILURE,
            release(true, true, true, true, true, 0.5F, 100.0D, 0, 0, 0, 100, 3));
    }

    @Test
    void deathIsOnlyReportableFromADirectSameLevelObservation() {
        assertTrue(BansheeRules.deathReportable(true, true, false, false));
        assertFalse(BansheeRules.deathReportable(false, true, false, false), "missing is not death");
        assertFalse(BansheeRules.deathReportable(true, false, false, false), "cross-dimension is not death");
        assertFalse(BansheeRules.deathReportable(true, true, true, false), "alive is not death");
        assertFalse(BansheeRules.deathReportable(true, true, false, true), "one report only");
    }

    @Test
    void standoffBandsAndHoldFollowTheApprovedGeometry() {
        assertEquals(StandoffAction.APPROACH, BansheeRules.warningStandoff(101.0D));
        assertEquals(StandoffAction.HOLD, BansheeRules.warningStandoff(100.0D));
        assertEquals(StandoffAction.HOLD, BansheeRules.warningStandoff(36.0D));
        assertEquals(StandoffAction.WITHDRAW, BansheeRules.warningStandoff(35.9D));
        assertEquals(StandoffAction.APPROACH, BansheeRules.lamentStandoff(65.0D));
        assertEquals(StandoffAction.HOLD, BansheeRules.lamentStandoff(36.0D));
        assertEquals(StandoffAction.WITHDRAW, BansheeRules.lamentStandoff(15.9D));
        int hold = 0;
        for (int tick = 0; tick < BansheeRules.WARNING_HOLD_TICKS; tick++) {
            assertFalse(BansheeRules.holdArmed(hold));
            hold = BansheeRules.advanceHold(hold, true, true);
        }
        assertTrue(BansheeRules.holdArmed(hold));
        assertEquals(0, BansheeRules.advanceHold(hold, false, true), "leaving the band clears the hold");
        assertEquals(0, BansheeRules.advanceHold(hold, true, false), "lost sight clears the hold");
    }

    @Test
    void pulseScheduleIsCappedAndNeverReadsALoadedZeroAsDue() {
        assertFalse(BansheeRules.pulseDue(1, 0, 3, true));
        assertTrue(BansheeRules.pulseDue(0, 0, 3, true));
        assertFalse(BansheeRules.pulseDue(0, 3, 3, true), "the episode cap is exact");
        assertFalse(BansheeRules.pulseDue(0, 0, 3, false), "an unarmed hold never pulses");
        assertEquals(80, BansheeRules.resetPulseIntervalOnLoad(0, 80),
            "a persisted zero interval restores the full interval instead of replaying");
        assertEquals(80, BansheeRules.resetPulseIntervalOnLoad(-5, 80));
        assertEquals(30, BansheeRules.resetPulseIntervalOnLoad(30, 80));
        assertEquals(80, BansheeRules.resetPulseIntervalOnLoad(4_000, 80), "overflow clamps");
        assertEquals(3, BansheeRules.warningPulsesRemaining(0));
        assertEquals(0, BansheeRules.warningPulsesRemaining(9));
        assertEquals(2, BansheeRules.lamentPulsesRemaining(0));
        assertEquals(0, BansheeRules.lamentPulsesRemaining(2));
    }

    @Test
    void attackerLegalityExcludesEveryProtectedRelationship() {
        assertTrue(BansheeRules.attackerLegal(attacker(true, true, true, false, false, false, true, true, false, false)),
            "a legal survival or adventure player attacker");
        assertTrue(BansheeRules.attackerLegal(attacker(true, true, true, false, false, false, false, false, false, true)),
            "a hostile vanilla Enemy attacker");
        assertFalse(BansheeRules.attackerLegal(attacker(false, true, true, false, false, false, false, false, false, true)), "non-living");
        assertFalse(BansheeRules.attackerLegal(attacker(true, false, true, false, false, false, true, true, false, false)), "dead");
        assertFalse(BansheeRules.attackerLegal(attacker(true, true, false, false, false, false, true, true, false, false)), "cross-dimension");
        assertFalse(BansheeRules.attackerLegal(attacker(true, true, true, true, false, false, false, false, false, true)), "self");
        assertFalse(BansheeRules.attackerLegal(attacker(true, true, true, false, true, false, true, true, false, false)), "spectral stone owner");
        assertFalse(BansheeRules.attackerLegal(attacker(true, true, true, false, false, true, false, false, false, true)), "same-owner mob");
        assertFalse(BansheeRules.attackerLegal(attacker(true, true, true, false, false, false, true, false, false, false)), "creative or spectator");
        assertFalse(BansheeRules.attackerLegal(attacker(true, true, true, false, false, false, true, true, true, false)), "invulnerable player");
        assertFalse(BansheeRules.attackerLegal(attacker(true, true, true, false, false, false, false, false, false, false)),
            "villagers, golems, animals, and passive or neutral mobs are excluded");
    }

    @Test
    void tabooAndTeleportRespectTheSingleResponseWindow() {
        assertTrue(BansheeRules.tabooResponseAllowed(true, 0));
        assertFalse(BansheeRules.tabooResponseAllowed(true, 1));
        assertFalse(BansheeRules.tabooResponseAllowed(false, 0));
        assertTrue(BansheeRules.teleportAllowed(true, false));
        assertFalse(BansheeRules.teleportAllowed(true, true),
            "the persisted attempted bit forbids a second teleport after reload");
        assertFalse(BansheeRules.teleportAllowed(false, false));
    }

    @Test
    void routeCadenceStrictSuccessAndThirdFailureBackoffAreExact() {
        assertTrue(BansheeRules.pathRequestAllowed(0, 0));
        assertFalse(BansheeRules.pathRequestAllowed(1, 0));
        assertFalse(BansheeRules.pathRequestAllowed(0, 1), "backoff blocks new requests");
        final RouteResult accepted = new RouteResult(true, true, true);
        final RouteResult unreachable = new RouteResult(true, false, false);
        final RouteResult rejected = new RouteResult(true, true, false);
        assertTrue(accepted.success());
        assertFalse(unreachable.success(), "a found geometric path alone is never success");
        assertFalse(rejected.success());
        assertEquals(0, BansheeRules.routeFailuresAfter(2, accepted), "success clears failures");
        assertEquals(3, BansheeRules.routeFailuresAfter(2, unreachable));
        assertFalse(BansheeRules.routeExhausted(2));
        assertTrue(BansheeRules.routeExhausted(3));
        assertEquals(0, BansheeRules.routeBackoffAfter(2));
        assertEquals(BansheeRules.ROUTE_BACKOFF_TICKS, BansheeRules.routeBackoffAfter(3));
    }

    @Test
    void anchorRadiiReanchoringAndPriorityAreExact() {
        assertTrue(BansheeRules.withinAnchorComfort(10, 6));
        assertFalse(BansheeRules.withinAnchorComfort(11, 0));
        assertFalse(BansheeRules.anchorReturnRequired(16, 10));
        assertTrue(BansheeRules.anchorReturnRequired(17, 0));
        assertTrue(BansheeRules.anchorReturnRequired(0, 11));
        assertFalse(BansheeRules.reanchorRequired(false, 199));
        assertTrue(BansheeRules.reanchorRequired(false, 200));
        assertTrue(BansheeRules.reanchorRequired(true, 0), "dimension mismatch reanchors once");
        assertTrue(BansheeRules.hazardPreempts(Mode.RECOIL, true));
        assertTrue(BansheeRules.hazardPreempts(Mode.LAMENT, true));
        assertFalse(BansheeRules.hazardPreempts(Mode.VIGIL, false));
        assertTrue(BansheeRules.priority(Mode.RECOIL, false) < BansheeRules.priority(Mode.LAMENT, false));
        assertTrue(BansheeRules.priority(Mode.LAMENT, false) < BansheeRules.priority(Mode.WARNING, false));
        assertTrue(BansheeRules.priority(Mode.WARNING, false) < BansheeRules.priority(Mode.APPROACH, false));
        assertTrue(BansheeRules.priority(Mode.APPROACH, false) < BansheeRules.priority(Mode.RECOVERY, false));
        assertTrue(BansheeRules.priority(Mode.RECOVERY, false) < BansheeRules.priority(Mode.VIGIL, false));
    }

    @Test
    void remainingDurationsClampWithoutConsultingElapsedTime() {
        assertEquals(0, BansheeRules.clampRemaining(-10, 100));
        assertEquals(100, BansheeRules.clampRemaining(4_000, 100));
        assertEquals(55, BansheeRules.clampRemaining(55, 100));
        assertEquals(0, BansheeRules.clampRemaining(55, -5), "a negative maximum clamps safely to zero");
        assertEquals(0, BansheeRules.decrementLoaded(0), "loaded decrement never goes negative");
        assertEquals(4, BansheeRules.decrementLoaded(5));
        final int offset = BansheeRules.stableOffset(LOW, 100);
        assertEquals(offset, BansheeRules.stableOffset(LOW, 100), "per-entity stagger is deterministic");
        assertTrue(offset >= 0 && offset < 100);
        assertEquals(0, BansheeRules.stableOffset(null, 100));
        assertEquals(0, BansheeRules.stableOffset(LOW, 0));
    }

    @Test
    void feedbackSuppressionIsCappedLocalAndAlwaysAdvancesSchedules() {
        assertTrue(BansheeRules.mayEmit(LOW, List.of(MID, HIGH)), "the lowest UUID emits");
        assertFalse(BansheeRules.mayEmit(HIGH, List.of(LOW, MID)), "a higher UUID is locally suppressed");
        assertTrue(BansheeRules.mayEmit(LOW, List.of()), "a lone Banshee always emits");
        final List<UUID> crowd = IntStream.range(0, 20)
            .mapToObj(index -> UUID.nameUUIDFromBytes(new byte[] {0, (byte) index}))
            .toList();
        final UUID globallyLowest = crowd.stream().min(java.util.Comparator.comparing(UUID::toString)).orElseThrow();
        assertTrue(BansheeRules.mayEmit(globallyLowest, crowd),
            "the set is capped at eight inspected entries including self; this is deliberately "
                + "a local best-effort gate, and no global-uniqueness claim is asserted here");
        assertEquals(1, BansheeRules.advanceEmissionCount(0, true));
        assertEquals(0, BansheeRules.advanceEmissionCount(0, false));
        assertEquals(3, BansheeRules.advanceEmissionCount(2, true),
            "every due Banshee advances its own count whether it emitted or was suppressed");
    }

    private static ReleaseReason releaseWith(final java.util.function.UnaryOperator<SubjectObservation> mutate) {
        return BansheeRules.releaseReason(mutate.apply(
            new SubjectObservation(true, true, true, true, false, true, 0.3F, 100.0D, 0, 0, 0, 100, 0)
        ));
    }

    private static ReleaseReason release(
        final boolean resolved, final boolean sameDimension, final boolean alive,
        final boolean eligibleGameMode, final boolean visible, final float healthFraction,
        final double distanceSquared, final int missingTicks, final int lostSightTicks,
        final int recoveryTicks, final int episodeRemainingTicks, final int routeFailures
    ) {
        return BansheeRules.releaseReason(new SubjectObservation(
            resolved, sameDimension, alive, eligibleGameMode, false, visible, healthFraction,
            distanceSquared, missingTicks, lostSightTicks, recoveryTicks, episodeRemainingTicks,
            routeFailures
        ));
    }

    private static ReleaseReason releaseMissing(final int missingTicks, final int episodeRemaining) {
        return BansheeRules.releaseReason(new SubjectObservation(
            false, true, false, false, false, false, 1.0F, 0.0D,
            missingTicks, 0, 0, episodeRemaining, 0
        ));
    }

    private static AttackerObservation attacker(
        final boolean living, final boolean alive, final boolean sameDimension, final boolean self,
        final boolean owner, final boolean sameOwner, final boolean player,
        final boolean eligibleGameMode, final boolean invulnerable, final boolean hostileEnemy
    ) {
        return new AttackerObservation(living, alive, sameDimension, self, owner, sameOwner,
            player, eligibleGameMode, invulnerable, hostileEnemy);
    }
}
