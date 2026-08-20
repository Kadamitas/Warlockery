package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.MimicryRules.Act;
import com.kadamitas.warlockery.entity.MimicryRules.Candidate;
import com.kadamitas.warlockery.entity.MimicryRules.Decision;
import com.kadamitas.warlockery.entity.MimicryRules.Facts;
import com.kadamitas.warlockery.entity.MimicryRules.Phase;
import com.kadamitas.warlockery.entity.MimicryRules.Quota;
import com.kadamitas.warlockery.entity.MimicryRules.Species;
import com.kadamitas.warlockery.entity.behavior.Cadence;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MimicryRulesTest {

    @Test
    void likenessBandOwnsEveryExactMovementBoundary() {
        assertEquals(MimicryRules.LikenessBand.CONTACT, MimicryRules.likenessBand(4));
        assertEquals(MimicryRules.LikenessBand.RETREAT, MimicryRules.likenessBand(5));
        assertEquals(MimicryRules.LikenessBand.HOLD, MimicryRules.likenessBand(6));
        assertEquals(MimicryRules.LikenessBand.HOLD, MimicryRules.likenessBand(12));
        assertEquals(MimicryRules.LikenessBand.APPROACH, MimicryRules.likenessBand(16));
        assertEquals(MimicryRules.LikenessBand.OUTER, MimicryRules.likenessBand(24));
        assertEquals(MimicryRules.LikenessBand.RELEASED, MimicryRules.likenessBand(24.01));
    }

    private static UUID id(final long value) {
        return new UUID(0L, value);
    }

    // ---------------------------------------------------------------- identity

    @Test
    void exactlyFourKindsAreOwnedByTheTwoMimicryFamilies() {
        assertEquals(4, Species.values().length);
        assertEquals(
            Set.of(
                CreatureKind.ILLUSION_CREEPER, CreatureKind.ILLUSION_SPIDER,
                CreatureKind.ILLUSION_ZOMBIE, CreatureKind.GLASS_DOPPELGANGER
            ),
            Set.of(
                Species.HOLLOW_FUSE.kind(), Species.THRESHOLD_WEAVER.kind(),
                Species.HOLLOW_DECOY.kind(), Species.PRESENTED_LIKENESS.kind()
            )
        );
        for (final CreatureKind kind : CreatureKind.values()) {
            final boolean mimic = MimicryRules.speciesOf(kind).isPresent();
            assertEquals(
                kind == CreatureKind.ILLUSION_CREEPER || kind == CreatureKind.ILLUSION_SPIDER
                    || kind == CreatureKind.ILLUSION_ZOMBIE || kind == CreatureKind.GLASS_DOPPELGANGER,
                mimic,
                kind.name()
            );
        }
    }

    @Test
    void theActVocabulariesIntersectOnlyInTheTwoDeliberatelySharedArms() {
        for (final Act act : Act.values()) {
            int owners = 0;
            for (final Species species : Species.values()) {
                if (MimicryRules.permits(species, act)) {
                    owners++;
                }
            }
            final boolean shared = act == Act.IDLE || act == Act.ESCAPE_HAZARD;
            assertEquals(shared ? Species.values().length : 1, owners, act.name());
        }
    }

    @Test
    void thePhaseVocabulariesIntersectOnlyInTheSharedHazardPhase() {
        for (final Phase phase : Phase.values()) {
            int owners = 0;
            for (final Species species : Species.values()) {
                if (MimicryRules.owns(species, phase)) {
                    owners++;
                }
            }
            assertEquals(phase == Phase.ESCAPE ? Species.values().length : 1, owners, phase.name());
        }
    }

    @Test
    void everySpeciesRoutineAndSpentPhaseBelongsToThatSpeciesAndIsNotAnEpisodePhase() {
        for (final Species species : Species.values()) {
            assertTrue(MimicryRules.owns(species, species.routine()), species.name());
            assertTrue(MimicryRules.owns(species, species.spent()), species.name());
            assertFalse(MimicryRules.inEpisode(species, species.routine()), species.name());
            assertFalse(MimicryRules.inEpisode(species, species.spent()), species.name());
            assertNotEquals(species.routine(), species.spent(), species.name());
        }
    }

    @Test
    void aSpeciesCannotBeAskedAboutAnotherSpeciesPhase() {
        assertThrows(IllegalArgumentException.class, () -> facts(
            Species.HOLLOW_FUSE, Phase.SHADOWING, builder -> builder
        ));
    }

    // ---------------------------------------------------------------- the divergence proof

    private static final int TRACE_STEPS = 240;

    /** Markers for the two arms every species shares, and for a refusal, inside a shape element. */
    private static final int SHARED_IDLE = -2;
    private static final int SHARED_ESCAPE_HAZARD = -3;
    private static final int REFUSED = -1;
    private static final int ESCAPE_PHASE = -1;

    /**
     * One decision with every name a reskin could rewrite stripped out of it: the act becomes its
     * position inside its own species' vocabulary, the phase becomes its position inside its own
     * species' phase list, and only the reason, which all four species share, keeps its name.
     */
    private record Shape(int phaseIndex, int actIndex, MimicryRules.Reason reason) {
    }

    /**
     * The behavioural distinctness proof, stated so that a reskin actually fails it.
     *
     * <p>The previous form compared raw act lists, and it could not fail. {@link
     * MimicryRules#permits} partitions the twenty species-specific acts into four disjoint blocks
     * keyed on species identity, and {@link MimicryRules#owns} partitions the phases the same way,
     * so two species are necessarily driven through disjoint inputs and necessarily emit disjoint
     * act sets. Two lists with disjoint element sets are unequal whatever the scheduling does, so
     * making two species byte identical and renaming only their acts still passed all six
     * comparisons.</p>
     *
     * <p>What is compared here instead is the <em>shape</em> of the decision stream under exactly
     * the species-to-species mapping a reskin would be: the reason sequence, when the phase index
     * changes and to which index, and where the refusals fall. Two species that schedule the same
     * thing at the same time under different names now produce the same shape and this assertion
     * fails, which is what it claims to check.</p>
     */
    @Test
    void theFourDecisionShapesDifferAndAReskinCannotSurviveTheComparison() {
        final List<List<Shape>> shapes = new ArrayList<>();
        for (final Species species : Species.values()) {
            final List<Shape> shape = shape(species);
            assertEquals(TRACE_STEPS, shape.size(), species.name());
            shapes.add(shape);
        }
        for (int left = 0; left < shapes.size(); left++) {
            for (int right = left + 1; right < shapes.size(); right++) {
                assertNotEquals(
                    shapes.get(left), shapes.get(right),
                    Species.values()[left] + " and " + Species.values()[right]
                        + " drive one identical decision shape: the same reason sequence, the same"
                        + " phase transition timing and the same refusal pattern, so one is a reskin"
                        + " of the other under a rename"
                );
            }
        }
    }

    /**
     * Why the comparison above had to change shape, pinned so nobody restores the old one. Every
     * species-specific act belongs to exactly one species, so comparing act lists across two
     * species compares two disjoint alphabets and is satisfied by construction.
     */
    @Test
    void comparingRawActsAcrossSpeciesCouldNeverHaveFailedBecauseTheAlphabetsAreDisjoint() {
        for (final Species left : Species.values()) {
            for (final Act act : ownActsOf(left)) {
                for (final Species right : Species.values()) {
                    assertEquals(left == right, MimicryRules.permits(right, act),
                        act + " is owned by " + left + ", so " + right + " can never emit it");
                }
            }
        }
    }

    @Test
    void noSpeciesEverSchedulesAnActOutsideItsOwnVocabularyAcrossThatRun() {
        for (final Species species : Species.values()) {
            for (final Optional<Act> act : trace(species)) {
                act.ifPresent(value -> assertTrue(
                    MimicryRules.permits(species, value),
                    species + " scheduled " + value + ", which belongs to another species"
                ));
            }
        }
    }

    @Test
    void everySpeciesReachesAtLeastFourOfItsOwnActsAcrossThatRun() {
        for (final Species species : Species.values()) {
            final EnumSet<Act> reached = EnumSet.noneOf(Act.class);
            trace(species).forEach(act -> act.ifPresent(reached::add));
            assertTrue(reached.size() >= 4,
                species + " only ever scheduled " + reached + ", which is not a behaviour");
        }
    }

    /** Every species-specific act carries an index inside its own five-act vocabulary. */
    private static int actIndex(final Species species, final Optional<Act> act) {
        return act.map(value -> switch (value) {
            case IDLE -> SHARED_IDLE;
            case ESCAPE_HAZARD -> SHARED_ESCAPE_HAZARD;
            case OBSERVE, APPROACH_OBSERVER, TELEGRAPH, HOLD_STILL, COLLAPSE_QUIETLY,
                 THRESHOLD_WATCH, LURE_STILL, RESOLVE_COMMIT, SNARE_HOLD, BREAK_SNARE,
                 COMPANION_SCAN, TAKE_STATION, DRAW_ATTENTION, ABSORB_HIT, UNMASK_SELF,
                 BIND_SUBJECT, SETTLE_PRESENTATION, HOLD_BAND, WITHDRAW, CONFRONT_ATTACKER ->
                ownActsOf(species).indexOf(value);
        }).orElse(REFUSED);
    }

    private static int phaseIndex(final Species species, final Phase phase) {
        return phase == Phase.ESCAPE ? ESCAPE_PHASE : phasesOf(species).indexOf(phase);
    }

    private static List<Shape> shape(final Species species) {
        return decisions(species).stream()
            .map(decision -> new Shape(
                phaseIndex(species, decision.phase()),
                actIndex(species, decision.act()),
                decision.reason()
            ))
            .toList();
    }

    private static List<Optional<Act>> trace(final Species species) {
        return decisions(species).stream().map(Decision::act).toList();
    }

    private static List<Decision> decisions(final Species species) {
        final List<Phase> phases = phasesOf(species);
        final List<Decision> taken = new ArrayList<>();
        for (int step = 0; step < TRACE_STEPS; step++) {
            final Phase phase = phases.get(step % phases.size());
            taken.add(MimicryRules.next(new Facts(
                species,
                phase,
                step % 7 * 10,
                step % 11 * 30,
                false,
                step % 5 != 0,
                step % 3 != 0,
                (double) (step % 13) * 4.0D,
                step % 17 == 0,
                step % 19 == 0 ? 2 : step % 4,
                step % 23,
                step % 29 == 0 ? MimicryRules.RECOGNITION_CERTAIN : step % 300,
                step % 6 == 0 ? 0 : 40,
                step % 2 == 0,
                step % 31 == 0 ? 3 : 0,
                step % 37 == 0
            )));
        }
        return List.copyOf(taken);
    }

    private static List<Phase> phasesOf(final Species species) {
        final List<Phase> phases = new ArrayList<>();
        for (final Phase phase : Phase.values()) {
            if (phase != Phase.ESCAPE && MimicryRules.owns(species, phase)) {
                phases.add(phase);
            }
        }
        return List.copyOf(phases);
    }

    private static List<Act> ownActsOf(final Species species) {
        final List<Act> acts = new ArrayList<>();
        for (final Act act : Act.values()) {
            if (act != Act.IDLE && act != Act.ESCAPE_HAZARD && MimicryRules.permits(species, act)) {
                acts.add(act);
            }
        }
        return List.copyOf(acts);
    }

    // ---------------------------------------------------------------- priority ladder

    @Test
    void hazardOutranksEveryBandForEverySpeciesFromEveryPhase() {
        for (final Species species : Species.values()) {
            for (final Phase phase : phasesOf(species)) {
                final Decision decision = MimicryRules.next(facts(species, phase, builder ->
                    builder.hazard(true).freshAttribution(true).candidateFound(true)));
                assertEquals(Optional.of(Act.ESCAPE_HAZARD), decision.act(), species + " " + phase);
                assertEquals(Phase.ESCAPE, decision.phase(), species + " " + phase);
            }
        }
    }

    @Test
    void onlyThePresentedLikenessEverSchedulesAnAttack() {
        for (final Species species : Species.values()) {
            final boolean reachesConfront = trace(species).stream()
                .flatMap(Optional::stream)
                .anyMatch(act -> act == Act.CONFRONT_ATTACKER);
            assertEquals(species == Species.PRESENTED_LIKENESS, reachesConfront, species.name());
        }
    }

    @Test
    void aFreshAttributionCollapsesTheFuseAndBreaksTheWeaverButOnlyAbsorbsForTheDecoy() {
        assertEquals(
            Optional.of(Act.COLLAPSE_QUIETLY),
            MimicryRules.next(facts(Species.HOLLOW_FUSE, Phase.TELL, builder ->
                builder.freshAttribution(true))).act()
        );
        assertEquals(
            Optional.of(Act.BREAK_SNARE),
            MimicryRules.next(facts(Species.THRESHOLD_WEAVER, Phase.SNARE, builder ->
                builder.freshAttribution(true))).act()
        );
        assertEquals(
            Optional.of(Act.ABSORB_HIT),
            MimicryRules.next(facts(Species.HOLLOW_DECOY, Phase.DRAW, builder ->
                builder.freshAttribution(true))).act()
        );
        assertEquals(
            Optional.of(Act.CONFRONT_ATTACKER),
            MimicryRules.next(facts(Species.PRESENTED_LIKENESS, Phase.SHADOWING, builder ->
                builder.freshAttribution(true))).act()
        );
    }

    @Test
    void aCooldownThatHasNotExpiredRefusesAnEpisodeInEverySpeciesRoutinePhase() {
        for (final Species species : Species.values()) {
            final Decision decision = MimicryRules.next(facts(species, species.routine(), builder ->
                builder.primaryCooldown(1).candidateFound(true)));
            assertEquals(Optional.empty(), decision.act(), species.name());
            assertEquals(species.routine(), decision.phase(), species.name());
            assertEquals(MimicryRules.Reason.COOLDOWN, decision.reason(), species.name());
        }
    }

    @Test
    void theDecisiveSecondHitUnmasksTheDecoyBeforeItsWindowElapses() {
        assertEquals(
            Optional.of(Act.UNMASK_SELF),
            MimicryRules.next(facts(Species.HOLLOW_DECOY, Phase.ABSORB, builder ->
                builder.acceptedHits(MimicryRules.DECOY_DECISIVE_HITS).phaseTicks(1))).act()
        );
        assertEquals(
            Optional.of(Act.ABSORB_HIT),
            MimicryRules.next(facts(Species.HOLLOW_DECOY, Phase.ABSORB, builder ->
                builder.acceptedHits(MimicryRules.DECOY_DECISIVE_HITS - 1).phaseTicks(1))).act()
        );
    }

    @Test
    void theWeaverCommitThatQualifiesNothingStillEndsTheEpisodeRatherThanRetryingForever() {
        final Decision decision = MimicryRules.next(facts(
            Species.THRESHOLD_WEAVER, Phase.RESOLVE, builder -> builder
                .phaseTicks(MimicryRules.WEAVER_RESOLVE_TICKS)
                .boundPresent(false)
        ));
        assertEquals(Optional.of(Act.IDLE), decision.act());
        assertEquals(Phase.SLACK, decision.phase());
    }

    // ---------------------------------------------------------------- clocks

    @Test
    void zeroReadsAsDueAndIsNeverTreatedAsRecentlyFired() {
        assertTrue(MimicryRules.due(0));
        assertTrue(MimicryRules.due(-1));
        assertFalse(MimicryRules.due(1));
    }

    @Test
    void attributionFreshnessHoldsExactlyAtItsDeclaredBoundary() {
        assertTrue(MimicryRules.attributionFresh(0));
        assertTrue(MimicryRules.attributionFresh(MimicryRules.ATTRIBUTION_FRESHNESS_TICKS));
        assertFalse(MimicryRules.attributionFresh(MimicryRules.ATTRIBUTION_FRESHNESS_TICKS + 1));
        assertFalse(MimicryRules.attributionFresh(-1));
    }

    @Test
    void staggerIsStablePerIdentityAndNeverConsultsWorldTime() {
        final UUID identity = id(4242L);
        assertEquals(
            MimicryRules.stagger(identity, MimicryRules.CHECK_CADENCE_TICKS),
            MimicryRules.stagger(identity, MimicryRules.CHECK_CADENCE_TICKS)
        );
        assertTrue(MimicryRules.stagger(identity, MimicryRules.CHECK_CADENCE_TICKS)
            < MimicryRules.CHECK_CADENCE_TICKS);
    }

    @Test
    void loadedDecrementNeverFallsBelowZeroAndNeverCatchesUp() {
        assertEquals(0, MimicryRules.decrementLoaded(0));
        assertEquals(0, MimicryRules.decrementLoaded(-5));
        assertEquals(9, MimicryRules.decrementLoaded(10));
    }

    // ---------------------------------------------------------------- charged perception

    @Test
    void anUnchargedCandidateCannotEvenBeConstructed() {
        assertThrows(IllegalArgumentException.class,
            () -> new Candidate(id(1L), true, true, 1.0D, false));
    }

    @Test
    void everyInspectedCandidateIsChargedBeforeAnyFilterCanRejectIt() {
        final List<Candidate> inspected = List.of(
            new Candidate(id(1L), false, false, 1.0D, true),
            new Candidate(id(2L), true, false, 2.0D, true),
            new Candidate(id(3L), true, true, 3.0D, true)
        );
        for (final Candidate candidate : inspected) {
            assertTrue(candidate.charged(), candidate.identity().toString());
        }
        assertEquals(Optional.of(inspected.get(2)), MimicryRules.bind(inspected, 100.0D));
    }

    @Test
    void bindingIsNearestThenIdentityAndNeverDependsOnScanOrder() {
        final Candidate far = new Candidate(id(1L), true, true, 9.0D, true);
        final Candidate nearHighId = new Candidate(id(9L), true, true, 4.0D, true);
        final Candidate nearLowId = new Candidate(id(2L), true, true, 4.0D, true);
        assertEquals(Optional.of(nearLowId), MimicryRules.bind(List.of(far, nearHighId, nearLowId), 100.0D));
        assertEquals(Optional.of(nearLowId), MimicryRules.bind(List.of(nearLowId, nearHighId, far), 100.0D));
    }

    @Test
    void aCandidateOutsideTheBindRadiusIsNeverBoundEvenWhenItIsTheOnlyOne() {
        final Candidate outside = new Candidate(id(1L), true, true, 400.0D, true);
        assertEquals(Optional.empty(), MimicryRules.bind(List.of(outside), 100.0D));
    }

    @Test
    void boundingBoxMembershipAloneNeverBindsWithoutSight() {
        final Candidate unseen = new Candidate(id(1L), true, false, 1.0D, true);
        assertEquals(Optional.empty(), MimicryRules.bind(List.of(unseen), 100.0D));
    }

    // ---------------------------------------------------------------- cadence and routes

    @Test
    void aCheckThatQualifiesNothingStillArmsItsCadence() {
        final Cadence due = MimicryRules.checkCadence();
        assertTrue(due.due(), "a fresh check must be offered at once");
        final Cadence armed = due.arm();
        assertFalse(armed.due(), "arming records that the check ran, not that it found anything");
        assertEquals(MimicryRules.CHECK_CADENCE_TICKS, armed.untilDue());
    }

    @Test
    void theThirdConsecutiveRouteFailureOpensTheSharedBackoffAndArmsTheCadenceEveryTime() {
        RouteRequest request = MimicryRules.routeRequest();
        assertTrue(request.mayRequest());
        for (int failure = 1; failure <= MimicryRules.MAX_ROUTE_FAILURES; failure++) {
            request = request.failed(MimicryRules.ROUTE_BACKOFF);
            assertFalse(request.cadence().due(), "failure " + failure + " must still arm the cadence");
            assertEquals(failure, request.consecutiveFailures());
        }
        assertEquals(MimicryRules.ROUTE_BACKOFF_TICKS, request.backoffRemaining());
        assertFalse(request.mayRequest());
    }

    @Test
    void anAcceptedRouteClearsTheFailureRunAndTheBackoffTogether() {
        final RouteRequest request = MimicryRules.routeRequest()
            .failed(MimicryRules.ROUTE_BACKOFF)
            .failed(MimicryRules.ROUTE_BACKOFF)
            .failed(MimicryRules.ROUTE_BACKOFF)
            .succeeded();
        assertEquals(0, request.consecutiveFailures());
        assertEquals(0, request.backoffRemaining());
    }

    // ---------------------------------------------------------------- recognition

    @Test
    void recognitionRisesDecaysAndClampsExactlyAtItsDeclaredThresholds() {
        assertEquals(MimicryRules.RECOGNITION_GAIN_WATCHED,
            MimicryRules.recognitionAfter(0, true, false, false));
        assertEquals(MimicryRules.RECOGNITION_GAIN_BESIDE_SUBJECT,
            MimicryRules.recognitionAfter(0, false, true, false));
        assertEquals(0, MimicryRules.recognitionAfter(0, false, false, false));
        assertEquals(0, MimicryRules.recognitionAfter(MimicryRules.RECOGNITION_DECAY - 1, false, false, false));
        assertEquals(MimicryRules.RECOGNITION_CERTAIN,
            MimicryRules.recognitionAfter(0, false, false, true));
        assertEquals(MimicryRules.RECOGNITION_MAX,
            MimicryRules.recognitionAfter(MimicryRules.RECOGNITION_MAX, false, true, false));
    }

    @Test
    void twoPulsesBesideTheRealSubjectReachCertainty() {
        final int afterFirst = MimicryRules.recognitionAfter(0, true, true, false);
        final int afterSecond = MimicryRules.recognitionAfter(afterFirst, true, true, false);
        assertTrue(afterFirst < MimicryRules.RECOGNITION_CERTAIN);
        assertEquals(MimicryRules.RECOGNITION_CERTAIN, afterSecond);
    }

    @Test
    void facingHoldsExactlyAtItsDeclaredThreshold() {
        assertFalse(MimicryRules.facing(0.84D));
        assertTrue(MimicryRules.facing(MimicryRules.FACING_DOT));
        assertTrue(MimicryRules.facing(0.86D));
        assertFalse(MimicryRules.facing(Double.NaN));
    }

    // ---------------------------------------------------------------- per-level quota

    @Test
    void theQuotaResetsOnTheServerTickCounterAndDeniesPastItsCap() {
        Quota quota = Quota.fresh(10);
        for (int spent = 0; spent < Quota.MAX_TOKENS; spent++) {
            assertTrue(quota.tokenAvailable());
            quota = quota.spendToken();
        }
        assertFalse(quota.tokenAvailable());
        assertEquals(quota, quota.spendToken(), "a denied token must change nothing at all");
        final Quota next = quota.forServerTick(11);
        assertTrue(next.tokenAvailable());
        assertEquals(11, next.serverTick());
        assertEquals(quota, quota.forServerTick(10), "the same server tick must not reopen a budget");
    }

    /**
     * The per-level raw-visit arm must be able to be the arm that denies.
     *
     * <p>{@code MimicryRuntime.runBoundedCheck} gates a check on
     * {@code grantToken(...) || reserveVisits(...)}, short-circuit, one call of each per check. One
     * token per check against sixteen tokens denies on check seventeen. Eight reserved visits per
     * check against an allowance of one hundred and twenty eight also denied on check seventeen, and
     * because the token arm is evaluated first the visit arm could never be reached at its own
     * threshold. Reserving visits was called, so the member was no longer unreachable, but it was
     * unfalsifiable, which is not better.</p>
     */
    @Test
    void theRawVisitAllowanceDeniesWhileTheTokenAllowanceStillHasRoom() {
        Quota quota = Quota.fresh(7);
        int scansAffordable = 0;
        while (quota.tokenAvailable() && quota.visitsAvailable(MimicryRules.MAX_RAW_VISITS_PER_CHECK)) {
            quota = quota.spendToken().spendVisits(MimicryRules.MAX_RAW_VISITS_PER_CHECK);
            scansAffordable++;
        }
        assertEquals(
            Quota.MAX_RAW_VISITS_PER_TICK / MimicryRules.MAX_RAW_VISITS_PER_CHECK, scansAffordable
        );
        assertFalse(quota.visitsAvailable(MimicryRules.MAX_RAW_VISITS_PER_CHECK),
            "the raw visit allowance must be the arm that ran out");
        assertTrue(quota.tokenAvailable(),
            "and it must run out while the token arm still has room, or the visit arm is shadowed by"
                + " the arm tested before it and can never deny a single check");
        assertEquals(Quota.MAX_RAW_VISITS_PER_TICK, quota.rawVisits());
        assertEquals(scansAffordable, quota.tokens());
    }

    @Test
    void thePerLevelVisitAllowanceIsStrictlyTighterThanTokensTimesThePerCheckScan() {
        assertTrue(
            Quota.MAX_RAW_VISITS_PER_TICK
                < Quota.MAX_TOKENS * MimicryRules.MAX_RAW_VISITS_PER_CHECK,
            "an allowance of at least tokens times per-check visits is dominated by the token cap,"
                + " so the visit arm can never be the one that denies"
        );
    }

    @Test
    void aDeniedVisitReservationChangesNothingAndAnAllowedOneAccumulates() {
        final Quota fresh = Quota.fresh(3);
        assertEquals(0, fresh.rawVisits());
        assertEquals(MimicryRules.MAX_RAW_VISITS_PER_CHECK,
            fresh.spendVisits(MimicryRules.MAX_RAW_VISITS_PER_CHECK).rawVisits());
        assertEquals(2 * MimicryRules.MAX_RAW_VISITS_PER_CHECK,
            fresh.spendVisits(MimicryRules.MAX_RAW_VISITS_PER_CHECK)
                .spendVisits(MimicryRules.MAX_RAW_VISITS_PER_CHECK).rawVisits());
        final Quota full = fresh.spendVisits(Quota.MAX_RAW_VISITS_PER_TICK);
        assertFalse(full.visitsAvailable(1));
        assertEquals(full, full.spendVisits(1), "a denied reservation must change nothing at all");
        assertTrue(full.visitsAvailable(0), "a zero-cost reservation is always affordable");
    }

    @Test
    void theQuotaHoldsOnlyPrimitivesSoItCanNeverKeepALevelOrEntityAlive() {
        for (final var component : Quota.class.getRecordComponents()) {
            assertEquals(int.class, component.getType(), component.getName());
        }
    }

    // ---------------------------------------------------------------- fact builder

    private interface Tweak {
        FactsBuilder apply(FactsBuilder builder);
    }

    private static Facts facts(final Species species, final Phase phase, final Tweak tweak) {
        return tweak.apply(new FactsBuilder(species, phase)).build();
    }

    private static final class FactsBuilder {
        private final Species species;
        private final Phase phase;
        private int phaseTicks;
        private int episodeTicks;
        private boolean hazard;
        private boolean boundPresent = true;
        private boolean boundVisible = true;
        private double boundDistanceSquared = 1.0D;
        private boolean freshAttribution;
        private int acceptedHits;
        private int facingDwellTicks;
        private int recognition;
        private int primaryCooldown;
        private boolean candidateFound;
        private int routeFailures;
        private boolean subjectActed;

        private FactsBuilder(final Species species, final Phase phase) {
            this.species = species;
            this.phase = phase;
        }

        private FactsBuilder phaseTicks(final int value) {
            phaseTicks = value;
            return this;
        }

        private FactsBuilder hazard(final boolean value) {
            hazard = value;
            return this;
        }

        private FactsBuilder boundPresent(final boolean value) {
            boundPresent = value;
            return this;
        }

        private FactsBuilder freshAttribution(final boolean value) {
            freshAttribution = value;
            return this;
        }

        private FactsBuilder acceptedHits(final int value) {
            acceptedHits = value;
            return this;
        }

        private FactsBuilder primaryCooldown(final int value) {
            primaryCooldown = value;
            return this;
        }

        private FactsBuilder candidateFound(final boolean value) {
            candidateFound = value;
            return this;
        }

        private Facts build() {
            return new Facts(
                species, phase, phaseTicks, episodeTicks, hazard, boundPresent, boundVisible,
                boundDistanceSquared, freshAttribution, acceptedHits, facingDwellTicks, recognition,
                primaryCooldown, candidateFound, routeFailures, subjectActed
            );
        }
    }
}

