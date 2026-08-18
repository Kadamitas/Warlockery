package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Action;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Candidate;
import com.kadamitas.warlockery.entity.GoblinPatronRules.CombatFacts;
import com.kadamitas.warlockery.entity.GoblinPatronRules.CounterpartCandidate;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Decision;
import com.kadamitas.warlockery.entity.GoblinPatronRules.OfferingEvent;
import com.kadamitas.warlockery.entity.GoblinPatronRules.OfferingFact;
import com.kadamitas.warlockery.entity.GoblinPatronRules.OfferingResult;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Phase;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Reason;
import com.kadamitas.warlockery.entity.GoblinPatronRules.ReleaseReason;
import com.kadamitas.warlockery.entity.GoblinPatronRules.RouteFailure;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Complete truth tables for the pure F12 patron decision surface. No Minecraft world is touched and
 * every assertion names a reason code.
 */
final class GoblinPatronRulesTest {
    private static final UUID SELF = new UUID(0L, 100L);
    private static final UUID LOW = new UUID(0L, 1L);
    private static final UUID MID = new UUID(0L, 2L);
    private static final UUID HIGH = new UUID(0L, 3L);

    // ---------------------------------------------------------------- identity

    @Test
    void onlyTheTwoExactPatronKindsAreOwnedByThisFamily() {
        assertTrue(GoblinPatronRules.isPatron(CreatureKind.STONEBROKER));
        assertTrue(GoblinPatronRules.isPatron(CreatureKind.FORGEWARDEN));
        assertFalse(GoblinPatronRules.isPatron(CreatureKind.GOBLIN));
        assertFalse(GoblinPatronRules.isPatron(CreatureKind.HOBGOBLIN));
        assertFalse(GoblinPatronRules.isPatron(null));
    }

    @Test
    void theCounterpartTableIsTheOneInGoblinBossRulesRatherThanASecondCopy() {
        assertEquals(
            Optional.of(CreatureKind.FORGEWARDEN),
            GoblinPatronRules.counterpartOf(CreatureKind.STONEBROKER)
        );
        assertEquals(
            Optional.of(CreatureKind.STONEBROKER),
            GoblinPatronRules.counterpartOf(CreatureKind.FORGEWARDEN)
        );
        assertTrue(GoblinPatronRules.counterpartOf(CreatureKind.GOBLIN).isEmpty());
        assertTrue(GoblinPatronRules.areOppositeKinds(CreatureKind.STONEBROKER, CreatureKind.FORGEWARDEN));
        assertFalse(GoblinPatronRules.areOppositeKinds(CreatureKind.STONEBROKER, CreatureKind.STONEBROKER));
    }

    @Test
    void theTwoActionVocabulariesAreDisjointApartFromIdleAndTrade() {
        final List<Action> broker = GoblinPatronRules.vocabulary(CreatureKind.STONEBROKER);
        final List<Action> warden = GoblinPatronRules.vocabulary(CreatureKind.FORGEWARDEN);
        assertTrue(broker.contains(Action.LEDGER_VOLLEY));
        assertTrue(broker.contains(Action.CLAIM_SHIFT));
        assertTrue(broker.contains(Action.PARLEY));
        assertTrue(warden.contains(Action.HAMMER_COMMIT));
        assertTrue(warden.contains(Action.FORGE_SURGE));
        assertTrue(warden.contains(Action.COMMISSION));
        final List<Action> shared = new ArrayList<>(broker);
        shared.retainAll(warden);
        assertEquals(List.of(Action.IDLE, Action.TRADE_HOLD), shared,
            "the only shared actions are the two inert ones");
        assertFalse(GoblinPatronRules.permits(CreatureKind.STONEBROKER, Action.FORGE_SURGE));
        assertFalse(GoblinPatronRules.permits(CreatureKind.FORGEWARDEN, Action.LEDGER_VOLLEY));
        assertFalse(GoblinPatronRules.permits(CreatureKind.GOBLIN, Action.LEDGER_VOLLEY));
    }

    @Test
    void eachPatronPublishesExactlyItsOwnResultKindAndOnlyForgewardenIgnoresFire() {
        assertEquals(
            GoblinPatronRules.DirectiveKind.BROKERED_WORK,
            GoblinPatronRules.directiveKind(CreatureKind.STONEBROKER)
        );
        assertEquals(
            GoblinPatronRules.DirectiveKind.FORGE_WARD,
            GoblinPatronRules.directiveKind(CreatureKind.FORGEWARDEN)
        );
        assertTrue(GoblinPatronRules.immuneToFireHazard(CreatureKind.FORGEWARDEN));
        assertFalse(GoblinPatronRules.immuneToFireHazard(CreatureKind.STONEBROKER));
    }

    // ---------------------------------------------------------------- phases

    @Test
    void phaseThresholdsAreSharedSoOneBossBarReadsTheSame() {
        assertEquals(Phase.PHASE_ONE, GoblinPatronRules.phase(400.0F, 400.0F));
        assertEquals(Phase.PHASE_ONE, GoblinPatronRules.phase(280.0F, 400.0F));
        assertEquals(Phase.PHASE_TWO, GoblinPatronRules.phase(268.0F, 400.0F));
        assertEquals(Phase.PHASE_TWO, GoblinPatronRules.phase(140.0F, 400.0F));
        assertEquals(Phase.PHASE_THREE, GoblinPatronRules.phase(130.0F, 400.0F));
        assertEquals(Phase.PHASE_THREE, GoblinPatronRules.phase(0.0F, 400.0F));
        assertEquals(Phase.PHASE_THREE, GoblinPatronRules.phase(10.0F, 0.0F));
    }

    @Test
    void withdrawalUsesHysteresisAndNeverOscillatesOnTheThreshold() {
        assertFalse(GoblinPatronRules.withdrawing(false, 100.0F, 400.0F, true),
            "25 percent has not reached the entry threshold");
        assertTrue(GoblinPatronRules.withdrawing(false, 80.0F, 400.0F, true));
        assertFalse(GoblinPatronRules.withdrawing(false, 80.0F, 400.0F, false),
            "entering withdrawal needs a safe point to withdraw to");
        assertTrue(GoblinPatronRules.withdrawing(true, 120.0F, 400.0F, true),
            "30 percent is above entry but below the recovery threshold, so it stays");
        assertFalse(GoblinPatronRules.withdrawing(true, 160.0F, 400.0F, true),
            "40 percent releases the withdrawal");
    }

    // ---------------------------------------------------------------- challenger selection

    @Test
    void everyInspectedCandidateIsChargedBeforeAnyFilterCanRejectIt() {
        final List<Candidate> crowd = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            crowd.add(new Candidate(new UUID(1L, index), false, false, false, false, index));
        }
        final GoblinPatronRules.Selection selection =
            GoblinPatronRules.selectChallenger(crowd, GoblinPatronRules.MAX_CHALLENGER_INSPECTIONS);
        assertTrue(selection.challenger().isEmpty());
        assertEquals(Reason.BUDGET_EXHAUSTED, selection.reason());
        assertEquals(GoblinPatronRules.MAX_CHALLENGER_INSPECTIONS, selection.inspected(),
            "forty dead candidates must still cost the full declared budget");
    }

    @Test
    void aRecentAttackerIsPreseededAheadOfACrowdAndStillCostsBudget() {
        final List<Candidate> crowd = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            crowd.add(new Candidate(new UUID(1L, index), true, false, false, false, 1.0D));
        }
        crowd.add(new Candidate(HIGH, true, false, true, false, 400.0D));
        final GoblinPatronRules.Selection selection =
            GoblinPatronRules.selectChallenger(crowd, GoblinPatronRules.MAX_CHALLENGER_INSPECTIONS);
        assertEquals(Optional.of(HIGH), selection.challenger(),
            "the distant recent attacker outranks thirty closer strangers");
        assertEquals(Reason.OK, selection.reason());
        assertEquals(1, selection.inspected());
    }

    @Test
    void aStableChallengerIsRetainedAheadOfAMarginallyCloserNewcomer() {
        final GoblinPatronRules.Selection selection = GoblinPatronRules.selectChallenger(List.of(
            new Candidate(LOW, true, false, false, false, 4.0D),
            new Candidate(MID, true, false, false, true, 9.0D)
        ), GoblinPatronRules.MAX_CHALLENGER_INSPECTIONS);
        assertEquals(Optional.of(MID), selection.challenger());
        assertEquals(Reason.OK, selection.reason());
    }

    @Test
    void protectedAndDeadCandidatesAreRejectedButStillCounted() {
        final GoblinPatronRules.Selection selection = GoblinPatronRules.selectChallenger(List.of(
            new Candidate(LOW, true, true, false, false, 1.0D),
            new Candidate(MID, false, false, false, false, 2.0D),
            new Candidate(HIGH, true, false, false, false, 3.0D)
        ), GoblinPatronRules.MAX_CHALLENGER_INSPECTIONS);
        assertEquals(Optional.of(HIGH), selection.challenger());
        assertEquals(3, selection.inspected());
    }

    @Test
    void anEmptyCrowdAndAZeroBudgetNameDifferentReasons() {
        assertEquals(Reason.NO_CANDIDATE, GoblinPatronRules.selectChallenger(List.of(), 16).reason());
        assertEquals(Reason.BUDGET_EXHAUSTED, GoblinPatronRules.selectChallenger(
            List.of(new Candidate(LOW, true, false, false, false, 1.0D)), 0
        ).reason());
    }

    @Test
    void challengerReleaseNamesTheExactCause() {
        assertEquals(ReleaseReason.DIED, GoblinPatronRules.releaseReason(true, false, true, false, false));
        assertEquals(ReleaseReason.INVALID, GoblinPatronRules.releaseReason(false, true, true, false, false));
        assertEquals(ReleaseReason.TRADE, GoblinPatronRules.releaseReason(true, true, true, true, false));
        assertEquals(ReleaseReason.PARLEY, GoblinPatronRules.releaseReason(true, true, true, false, true));
        assertEquals(ReleaseReason.OUT_OF_RANGE, GoblinPatronRules.releaseReason(true, true, false, false, false));
        assertEquals(ReleaseReason.NONE, GoblinPatronRules.releaseReason(true, true, true, false, false));
    }

    // ---------------------------------------------------------------- schedules

    @Test
    void theTwoCombatTracesAreDifferentAcrossALongDeterministicRun() {
        final List<Optional<Action>> broker = trace(CreatureKind.STONEBROKER);
        final List<Optional<Action>> warden = trace(CreatureKind.FORGEWARDEN);
        assertEquals(broker.size(), warden.size());
        assertNotEquals(broker, warden, "the two patrons must not produce one identical trace");
        assertTrue(broker.stream().flatMap(Optional::stream)
                .allMatch(action -> GoblinPatronRules.permits(CreatureKind.STONEBROKER, action)),
            "Stonebroker never schedules an action outside its own vocabulary");
        assertTrue(warden.stream().flatMap(Optional::stream)
                .allMatch(action -> GoblinPatronRules.permits(CreatureKind.FORGEWARDEN, action)),
            "Forgewarden never schedules an action outside its own vocabulary");
    }

    private static List<Optional<Action>> trace(final CreatureKind kind) {
        final List<Optional<Action>> actions = new ArrayList<>();
        for (int step = 0; step < 60; step++) {
            final Phase phase = Phase.values()[step % Phase.values().length];
            actions.add(GoblinPatronRules.nextAction(new CombatFacts(
                kind, phase, true, step % 5 != 0, 4.0D + step, step % 3 == 0,
                step % 7 == 0 ? 0 : 20, step % 4 == 0 ? 0 : 20, false, step % 11 == 0
            )).action());
        }
        return List.copyOf(actions);
    }

    @Test
    void stonebrokerVolleysOnlyInsideItsDeclaredRangeAndRepositionsWhenBlocked() {
        assertEquals(Decision.of(Action.LEDGER_VOLLEY), GoblinPatronRules.nextAction(new CombatFacts(
            CreatureKind.STONEBROKER, Phase.PHASE_ONE, true, true, 100.0D, false, 0, 20, false, false
        )));
        assertEquals(Reason.OUT_OF_RANGE, GoblinPatronRules.nextAction(new CombatFacts(
            CreatureKind.STONEBROKER, Phase.PHASE_ONE, true, true, 400.0D, false, 0, 20, false, false
        )).reason());
        assertEquals(Decision.of(Action.CLAIM_SHIFT), GoblinPatronRules.nextAction(new CombatFacts(
            CreatureKind.STONEBROKER, Phase.PHASE_TWO, true, false, 100.0D, false, 0, 0, false, false
        )));
        assertEquals(Reason.NO_LINE_OF_SIGHT, GoblinPatronRules.nextAction(new CombatFacts(
            CreatureKind.STONEBROKER, Phase.PHASE_ONE, true, false, 100.0D, false, 0, 0, false, false
        )).reason());
    }

    @Test
    void stonebrokerKeepsRealMeleeWindowsInsteadOfStarvingThemBehindTheVolley() {
        final Decision decision = GoblinPatronRules.nextAction(new CombatFacts(
            CreatureKind.STONEBROKER, Phase.PHASE_THREE, true, true, 4.0D, true, 40, 40, false, false
        ));
        assertTrue(decision.action().isEmpty());
        assertEquals(Reason.OK, decision.reason(),
            "an in-reach patron on cooldown reports OK so the plain attack executor runs");
    }

    @Test
    void withdrawalAndRegroupOutrankEverySignatureAction() {
        assertEquals(Decision.of(Action.ORDERLY_WITHDRAWAL), GoblinPatronRules.nextAction(new CombatFacts(
            CreatureKind.STONEBROKER, Phase.PHASE_THREE, true, true, 16.0D, false, 0, 0, true, false
        )));
        assertEquals(Decision.of(Action.REGROUP), GoblinPatronRules.nextAction(new CombatFacts(
            CreatureKind.FORGEWARDEN, Phase.PHASE_THREE, true, true, 4.0D, true, 0, 0, true, true
        )));
    }

    @Test
    void forgewardenNeverSurgesInPhaseOneAndInterposesForAThreatenedSubject() {
        assertEquals(Decision.of(Action.INTERPOSE), GoblinPatronRules.nextAction(new CombatFacts(
            CreatureKind.FORGEWARDEN, Phase.PHASE_TWO, true, true, 25.0D, false, 0, 0, false, true
        )));
        final Decision phaseOne = GoblinPatronRules.nextAction(new CombatFacts(
            CreatureKind.FORGEWARDEN, Phase.PHASE_ONE, true, true, 4.0D, true, 0, 20, false, false
        ));
        assertTrue(phaseOne.action().isEmpty(), "phase one is direct pressure, never a surge");
        assertEquals(Decision.of(Action.FORGE_SURGE), GoblinPatronRules.nextAction(new CombatFacts(
            CreatureKind.FORGEWARDEN, Phase.PHASE_TWO, true, true, 4.0D, true, 0, 20, false, false
        )));
        assertEquals(Decision.of(Action.HAMMER_COMMIT), GoblinPatronRules.nextAction(new CombatFacts(
            CreatureKind.FORGEWARDEN, Phase.PHASE_THREE, true, true, 4.0D, true, 40, 0, false, false
        )));
    }

    @Test
    void anAbsentChallengerAndAForeignKindBothRefuseWithNamedReasons() {
        assertEquals(Reason.NO_CANDIDATE, GoblinPatronRules.nextAction(new CombatFacts(
            CreatureKind.STONEBROKER, Phase.PHASE_ONE, false, true, 1.0D, true, 0, 0, false, false
        )).reason());
        assertEquals(Reason.NOT_A_PATRON, GoblinPatronRules.nextAction(new CombatFacts(
            CreatureKind.GOBLIN, Phase.PHASE_ONE, true, true, 1.0D, true, 0, 0, false, false
        )).reason());
        assertEquals(Reason.NOT_A_PATRON, GoblinPatronRules.nextAction(null).reason());
    }

    @Test
    void signatureSpacingAndArrowCountsMatchTheDeclaredPhaseSchedule() {
        assertEquals(80, GoblinPatronRules.signatureGapTicks(CreatureKind.STONEBROKER, Phase.PHASE_ONE));
        assertEquals(60, GoblinPatronRules.signatureGapTicks(CreatureKind.STONEBROKER, Phase.PHASE_TWO));
        assertEquals(80, GoblinPatronRules.signatureGapTicks(CreatureKind.STONEBROKER, Phase.PHASE_THREE));
        assertEquals(100, GoblinPatronRules.signatureGapTicks(CreatureKind.FORGEWARDEN, Phase.PHASE_TWO));
        assertEquals(140, GoblinPatronRules.signatureGapTicks(CreatureKind.FORGEWARDEN, Phase.PHASE_THREE));
        assertEquals(0, GoblinPatronRules.signatureGapTicks(CreatureKind.GOBLIN, Phase.PHASE_ONE));
        assertEquals(1, GoblinPatronRules.volleyArrows(Phase.PHASE_ONE));
        assertEquals(2, GoblinPatronRules.volleyArrows(Phase.PHASE_TWO));
        assertEquals(3, GoblinPatronRules.volleyArrows(Phase.PHASE_THREE));
    }

    @Test
    void everyHighImpactActionIsTelegraphedAndHasARecoveryWindow() {
        for (final Action action : List.of(
            Action.LEDGER_VOLLEY, Action.CLAIM_SHIFT, Action.HAMMER_COMMIT, Action.FORGE_SURGE
        )) {
            assertTrue(GoblinPatronRules.isTelegraphed(action), action.name());
            assertTrue(GoblinPatronRules.tellTicks(action) > 0, action.name());
            assertTrue(GoblinPatronRules.recoveryTicks(action) > 0, action.name());
        }
        assertFalse(GoblinPatronRules.isTelegraphed(Action.IDLE));
        assertFalse(GoblinPatronRules.isTelegraphed(Action.WATCH_CLAIM));
        assertEquals(0, GoblinPatronRules.tellTicks(Action.PARLEY));
    }

    @Test
    void anOfferingWindowNeverBlocksTheVeryTradeItExistsToOpen() {
        assertFalse(GoblinPatronRules.blocksTrade(Action.PARLEY),
            "a parley permits trade; committed-ness alone is the wrong test");
        assertFalse(GoblinPatronRules.blocksTrade(Action.COMMISSION));
        assertFalse(GoblinPatronRules.blocksTrade(Action.WATCH_CLAIM));
        assertFalse(GoblinPatronRules.blocksTrade(Action.WARD_STANCE));
        assertFalse(GoblinPatronRules.blocksTrade(Action.QUIET_LEDGER));
        assertFalse(GoblinPatronRules.blocksTrade(Action.IDLE));
        assertFalse(GoblinPatronRules.blocksTrade(null));
        assertTrue(GoblinPatronRules.blocksTrade(Action.LEDGER_VOLLEY));
        assertTrue(GoblinPatronRules.blocksTrade(Action.FORGE_SURGE));
        assertTrue(GoblinPatronRules.blocksTrade(Action.HAMMER_COMMIT));
        assertTrue(GoblinPatronRules.blocksTrade(Action.CLAIM_SHIFT));
        assertTrue(GoblinPatronRules.blocksTrade(Action.INTERPOSE));
        assertTrue(GoblinPatronRules.blocksTrade(Action.ORDERLY_WITHDRAWAL));
        assertTrue(GoblinPatronRules.blocksTrade(Action.REGROUP));
    }

    // ---------------------------------------------------------------- offering

    @Test
    void heartOfferingsApplyExactDeltasThroughFiveAndRefuseTheSixth() {
        int level = 0;
        for (int index = 0; index < GoblinPatronRules.MAX_EMPOWERMENT; index++) {
            final OfferingResult result = GoblinPatronRules.offerHeart(CreatureKind.STONEBROKER, level);
            assertTrue(result.accepted(), "offering " + index);
            assertEquals(Reason.OK, result.reason());
            assertEquals(4.0D, result.healthDelta());
            assertEquals(1.0D, result.attackDelta());
            assertEquals(GoblinPatronRules.PARLEY_TICKS, result.windowTicks());
            level = result.empowermentAfter();
        }
        assertEquals(5, level);
        final OfferingResult refused = GoblinPatronRules.offerHeart(CreatureKind.STONEBROKER, level);
        assertFalse(refused.accepted());
        assertEquals(Reason.EMPOWERMENT_FULL, refused.reason());
        assertEquals(0.0D, refused.healthDelta());
    }

    @Test
    void theTwoPatronsOpenDifferentWindowsFromTheSameOffering() {
        assertEquals(GoblinPatronRules.PARLEY_TICKS,
            GoblinPatronRules.offerHeart(CreatureKind.STONEBROKER, 0).windowTicks());
        assertEquals(GoblinPatronRules.COMMISSION_TICKS,
            GoblinPatronRules.offerHeart(CreatureKind.FORGEWARDEN, 0).windowTicks());
        assertEquals(Action.PARLEY, GoblinPatronRules.windowAction(CreatureKind.STONEBROKER));
        assertEquals(Action.COMMISSION, GoblinPatronRules.windowAction(CreatureKind.FORGEWARDEN));
        assertEquals(Reason.NOT_A_PATRON,
            GoblinPatronRules.offerHeart(CreatureKind.GOBLIN, 0).reason());
    }

    @Test
    void relationshipFactsAreBoundedAndEvictedDeterministically() {
        List<OfferingFact> facts = List.of();
        for (int index = 0; index < 12; index++) {
            facts = GoblinPatronRules.recordFact(facts, new UUID(5L, index), OfferingEvent.OFFERED);
        }
        assertEquals(GoblinPatronRules.MAX_OFFERING_FACTS, facts.size());
        final UUID existing = facts.getFirst().player();
        final List<OfferingFact> repeated =
            GoblinPatronRules.recordFact(facts, existing, OfferingEvent.TRADED);
        assertEquals(GoblinPatronRules.MAX_OFFERING_FACTS, repeated.size(),
            "updating an existing player replaces its fact rather than adding a ninth");
        assertEquals(1, repeated.stream()
            .filter(fact -> fact.player().equals(existing)).count());
    }

    @Test
    void standingIsClampedAndABreachIsTheOnlyNegativeEvent() {
        List<OfferingFact> facts = GoblinPatronRules.recordFact(List.of(), LOW, OfferingEvent.OFFERED);
        assertEquals(2, facts.getFirst().standing());
        for (int index = 0; index < 6; index++) {
            facts = GoblinPatronRules.recordFact(facts, LOW, OfferingEvent.OFFERED);
        }
        assertEquals(GoblinPatronRules.MAX_STANDING, facts.getFirst().standing());
        for (int index = 0; index < 6; index++) {
            facts = GoblinPatronRules.recordFact(facts, LOW, OfferingEvent.BREACHED);
        }
        assertEquals(GoblinPatronRules.MIN_STANDING, facts.getFirst().standing());
        assertEquals(OfferingEvent.BREACHED, facts.getFirst().event());
    }

    @Test
    void onlyTheWindowHolderCanBreachItsOwnWindow() {
        assertTrue(GoblinPatronRules.breaches(Optional.of(LOW), LOW));
        assertFalse(GoblinPatronRules.breaches(Optional.of(LOW), MID));
        assertFalse(GoblinPatronRules.breaches(Optional.empty(), LOW));
    }

    // ---------------------------------------------------------------- accord

    @Test
    void anAccordFormsOnlyWhenBothPatronsChooseEachOther() {
        final GoblinPatronRules.AccordSelection mutual = GoblinPatronRules.selectCounterpart(
            SELF, CreatureKind.STONEBROKER, Optional.empty(),
            List.of(new CounterpartCandidate(
                MID, CreatureKind.FORGEWARDEN, true, true, 100.0D, Optional.of(SELF)
            )),
            GoblinPatronRules.MAX_COUNTERPART_INSPECTIONS
        );
        assertEquals(Optional.of(MID), mutual.counterpart());
        assertEquals(Reason.OK, mutual.reason());

        final GoblinPatronRules.AccordSelection oneSided = GoblinPatronRules.selectCounterpart(
            SELF, CreatureKind.STONEBROKER, Optional.empty(),
            List.of(new CounterpartCandidate(
                MID, CreatureKind.FORGEWARDEN, true, true, 100.0D, Optional.of(HIGH)
            )),
            GoblinPatronRules.MAX_COUNTERPART_INSPECTIONS
        );
        assertTrue(oneSided.counterpart().isEmpty());
        assertEquals(Reason.ACCORD_NOT_MUTUAL, oneSided.reason());
    }

    @Test
    void accordSelectionRejectsSameKindWrongDimensionAndTooFarWithNamedReasons() {
        assertEquals(Reason.WRONG_KIND, GoblinPatronRules.selectCounterpart(
            SELF, CreatureKind.STONEBROKER, Optional.empty(),
            List.of(new CounterpartCandidate(
                MID, CreatureKind.STONEBROKER, true, true, 4.0D, Optional.of(SELF)
            )), 8
        ).reason());
        assertEquals(Reason.DIMENSION_MISMATCH, GoblinPatronRules.selectCounterpart(
            SELF, CreatureKind.STONEBROKER, Optional.empty(),
            List.of(new CounterpartCandidate(
                MID, CreatureKind.FORGEWARDEN, true, false, 4.0D, Optional.of(SELF)
            )), 8
        ).reason());
        assertEquals(Reason.ACCORD_TOO_FAR, GoblinPatronRules.selectCounterpart(
            SELF, CreatureKind.STONEBROKER, Optional.empty(),
            List.of(new CounterpartCandidate(
                MID, CreatureKind.FORGEWARDEN, true, true, 4_096.0D, Optional.of(SELF)
            )), 8
        ).reason());
    }

    @Test
    void counterpartTraversalIsChargedBeforeAnyFilterAndTheCurrentPairingIsPreferred() {
        final List<CounterpartCandidate> crowd = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            crowd.add(new CounterpartCandidate(
                new UUID(9L, index), CreatureKind.STONEBROKER, true, true, index, Optional.of(SELF)
            ));
        }
        final GoblinPatronRules.AccordSelection exhausted = GoblinPatronRules.selectCounterpart(
            SELF, CreatureKind.STONEBROKER, Optional.empty(), crowd,
            GoblinPatronRules.MAX_COUNTERPART_INSPECTIONS
        );
        assertEquals(GoblinPatronRules.MAX_COUNTERPART_INSPECTIONS, exhausted.inspected());
        assertEquals(Reason.WRONG_KIND, exhausted.reason());

        final GoblinPatronRules.AccordSelection retained = GoblinPatronRules.selectCounterpart(
            SELF, CreatureKind.STONEBROKER, Optional.of(HIGH),
            List.of(
                new CounterpartCandidate(MID, CreatureKind.FORGEWARDEN, true, true, 1.0D, Optional.of(SELF)),
                new CounterpartCandidate(HIGH, CreatureKind.FORGEWARDEN, true, true, 400.0D, Optional.of(SELF))
            ), 8
        );
        assertEquals(Optional.of(HIGH), retained.counterpart(),
            "the current valid counterpart is evaluated first even when it is farther away");
    }

    @Test
    void everyAccordGateHasItsOwnNamedRefusal() {
        assertEquals(Reason.OK, GoblinPatronRules.accordUsable(true, true, true, 100, 4.0D, 12.0D));
        assertEquals(Reason.TARGET_INVALID,
            GoblinPatronRules.accordUsable(false, true, true, 100, 4.0D, 12.0D));
        assertEquals(Reason.DIMENSION_MISMATCH,
            GoblinPatronRules.accordUsable(true, false, true, 100, 4.0D, 12.0D));
        assertEquals(Reason.EPOCH_MISMATCH,
            GoblinPatronRules.accordUsable(true, true, false, 100, 4.0D, 12.0D));
        assertEquals(Reason.ACCORD_EXPIRED,
            GoblinPatronRules.accordUsable(true, true, true, 0, 4.0D, 12.0D));
        assertEquals(Reason.ACCORD_TOO_FAR,
            GoblinPatronRules.accordUsable(true, true, true, 100, 400.0D, 12.0D));
    }

    @Test
    void theSharedMarkGivesExactlyFourDamageAndOnlyToForgewardenAgainstThatChallenger() {
        assertEquals(4.0F, GoblinPatronRules.sharedChallengerBonus(
            CreatureKind.FORGEWARDEN, Optional.of(LOW), LOW, Reason.OK
        ));
        assertEquals(0.0F, GoblinPatronRules.sharedChallengerBonus(
            CreatureKind.FORGEWARDEN, Optional.of(LOW), MID, Reason.OK
        ), "the mark applies to one exact challenger, not to everything nearby");
        assertEquals(0.0F, GoblinPatronRules.sharedChallengerBonus(
            CreatureKind.STONEBROKER, Optional.of(LOW), LOW, Reason.OK
        ), "Stonebroker publishes the mark and never consumes it");
        assertEquals(0.0F, GoblinPatronRules.sharedChallengerBonus(
            CreatureKind.FORGEWARDEN, Optional.of(LOW), LOW, Reason.ACCORD_EXPIRED
        ));
    }

    @Test
    void wardStanceReducesOnlyStonebrokerDamageAndNeverRecurses() {
        assertEquals(0.25F, GoblinPatronRules.wardReduction(
            CreatureKind.STONEBROKER, true, Reason.OK, true
        ));
        assertEquals(0.0F, GoblinPatronRules.wardReduction(
            CreatureKind.FORGEWARDEN, true, Reason.OK, true
        ), "the ward never reduces the warden's own damage");
        assertEquals(0.0F, GoblinPatronRules.wardReduction(
            CreatureKind.STONEBROKER, false, Reason.OK, true
        ));
        assertEquals(0.0F, GoblinPatronRules.wardReduction(
            CreatureKind.STONEBROKER, true, Reason.ACCORD_TOO_FAR, true
        ));
        assertEquals(0.0F, GoblinPatronRules.wardReduction(
            CreatureKind.STONEBROKER, true, Reason.OK, false
        ));
    }

    // ---------------------------------------------------------------- trade and route

    @Test
    void tradeAndRestockEligibilityNameTheBlockingCondition() {
        assertEquals(Reason.OK, GoblinPatronRules.tradeEligibility(true, false, false, false, false));
        assertEquals(Reason.TARGET_INVALID,
            GoblinPatronRules.tradeEligibility(false, false, false, false, false));
        assertEquals(Reason.TRADING,
            GoblinPatronRules.tradeEligibility(true, true, false, false, false));
        assertEquals(Reason.HAZARD_PREEMPTS,
            GoblinPatronRules.tradeEligibility(true, false, true, false, false));
        assertEquals(Reason.BUSY_WITH_ACTION,
            GoblinPatronRules.tradeEligibility(true, false, false, true, false));
        assertEquals(Reason.WITHDRAWING,
            GoblinPatronRules.tradeEligibility(true, false, false, false, true));
        assertEquals(Reason.RESTOCK_CAPPED, GoblinPatronRules.restockEligibility(2, 0, true, false, false));
        assertEquals(Reason.CADENCE_NOT_DUE, GoblinPatronRules.restockEligibility(0, 40, true, false, false));
        assertEquals(Reason.OK, GoblinPatronRules.restockEligibility(0, 0, true, false, false));
    }

    @Test
    void merchantLevelsAndOfferSeedsSeparateTheTwoPatrons() {
        assertEquals(1, GoblinPatronRules.levelForXp(0));
        assertEquals(2, GoblinPatronRules.levelForXp(10));
        assertEquals(5, GoblinPatronRules.levelForXp(10_000));
        assertEquals(1, GoblinPatronRules.clampMerchantLevel(-4));
        assertEquals(5, GoblinPatronRules.clampMerchantLevel(9));
        assertNotEquals(
            GoblinPatronRules.offerSeed(SELF, CreatureKind.STONEBROKER, 3, 0L),
            GoblinPatronRules.offerSeed(SELF, CreatureKind.FORGEWARDEN, 3, 0L),
            "one identity must not roll the same catalog for both kinds"
        );
        assertEquals(
            GoblinPatronRules.offerSeed(SELF, CreatureKind.STONEBROKER, 3, 7L),
            GoblinPatronRules.offerSeed(SELF, CreatureKind.STONEBROKER, 3, 7L),
            "the seed is deterministic"
        );
    }

    @Test
    void threeClassifiedFailuresEstablishTheLongBackoffAndSuccessClearsIt() {
        assertEquals(Reason.ROUTE_BACKOFF, GoblinPatronRules.routeEligibility(0, 40));
        assertEquals(Reason.CADENCE_NOT_DUE, GoblinPatronRules.routeEligibility(10, 0));
        assertEquals(Reason.OK, GoblinPatronRules.routeEligibility(0, 0));
        int failures = 0;
        failures = GoblinPatronRules.nextFailureCount(failures, RouteFailure.NO_PATH);
        assertEquals(GoblinPatronRules.ROUTE_RETRY_TICKS, GoblinPatronRules.backoffTicks(failures));
        failures = GoblinPatronRules.nextFailureCount(failures, RouteFailure.REJECTED);
        failures = GoblinPatronRules.nextFailureCount(failures, RouteFailure.UNREACHABLE);
        assertEquals(3, failures);
        assertEquals(GoblinPatronRules.ROUTE_BACKOFF_TICKS, GoblinPatronRules.backoffTicks(failures));
        assertEquals(0, GoblinPatronRules.nextFailureCount(failures, RouteFailure.NONE));
    }

    @Test
    void aScanThatQualifiesNothingStillArmsItsFullCadence() {
        assertEquals(GoblinPatronRules.BLOCK_SCAN_INTERVAL_TICKS,
            GoblinPatronRules.nextScanCadence(false, GoblinPatronRules.BLOCK_SCAN_INTERVAL_TICKS));
        assertEquals(GoblinPatronRules.BLOCK_SCAN_INTERVAL_TICKS,
            GoblinPatronRules.nextScanCadence(true, GoblinPatronRules.BLOCK_SCAN_INTERVAL_TICKS));
        assertEquals(1, GoblinPatronRules.nextScanCadence(false, 0),
            "a zero interval still costs one tick rather than retrying forever inside one tick");
    }

    // ---------------------------------------------------------------- save normalization

    @Test
    void coupledActionFieldsAreValidatedTogether() {
        assertTrue(GoblinPatronRules.actionFieldsCoupled(
            Action.IDLE, Optional.empty(), 0, 0, 0
        ));
        assertFalse(GoblinPatronRules.actionFieldsCoupled(
            Action.IDLE, Optional.of(LOW), 0, 0, 0
        ), "an idle action cannot keep a target");
        assertFalse(GoblinPatronRules.actionFieldsCoupled(
            Action.IDLE, Optional.empty(), 12, 0, 0
        ), "an idle action cannot keep a running tell");
        assertTrue(GoblinPatronRules.actionFieldsCoupled(
            Action.FORGE_SURGE, Optional.of(LOW), 24, 60, 100
        ));
        assertFalse(GoblinPatronRules.actionFieldsCoupled(
            Action.FORGE_SURGE, Optional.of(LOW), 999, 60, 100
        ), "a tell longer than its own declared window is malformed");
    }

    @Test
    void deadlinesAreClampedRatherThanTrusted() {
        assertEquals(0, GoblinPatronRules.clampDeadline(-500));
        assertEquals((int) GoblinPatronRules.FAR_FUTURE_TICKS,
            GoblinPatronRules.clampDeadline(Integer.MAX_VALUE));
        assertEquals(120, GoblinPatronRules.clampDeadline(120));
        assertTrue(GoblinPatronRules.FAR_FUTURE_TICKS < Long.MAX_VALUE / 4L);
        assertTrue(GoblinPatronRules.isDue(0));
        assertTrue(GoblinPatronRules.isDue(-1));
        assertFalse(GoblinPatronRules.isDue(1));
    }

    @Test
    void stableOffsetsAreDeterministicBoundedAndNonNegative() {
        for (int index = 0; index < 64; index++) {
            final int offset = GoblinPatronRules.stableOffset(new UUID(index, -index), 40);
            assertTrue(offset >= 0 && offset < 40, "offset " + offset);
        }
        assertEquals(
            GoblinPatronRules.stableOffset(SELF, 40),
            GoblinPatronRules.stableOffset(SELF, 40)
        );
        assertEquals(0, GoblinPatronRules.stableOffset(null, 40));
        assertEquals(0, GoblinPatronRules.stableOffset(SELF, 0));
    }

    @Test
    void theDeclaredStructuralCapsAreTheOnesTheRuntimeIsHeldTo() {
        assertEquals(24, GoblinPatronRules.CHALLENGER_RADIUS);
        assertEquals(16, GoblinPatronRules.MAX_CHALLENGER_INSPECTIONS);
        assertEquals(32, GoblinPatronRules.COUNTERPART_RADIUS);
        assertEquals(8, GoblinPatronRules.MAX_COUNTERPART_INSPECTIONS);
        assertEquals(24, GoblinPatronRules.DIRECTIVE_RADIUS);
        assertEquals(16, GoblinPatronRules.MAX_DIRECTIVE_INSPECTIONS);
        assertEquals(4, GoblinPatronRules.SURGE_RADIUS);
        assertEquals(16, GoblinPatronRules.MAX_SURGE_INSPECTIONS);
        assertEquals(12, GoblinPatronRules.SCAN_HORIZONTAL);
        assertEquals(4, GoblinPatronRules.SCAN_VERTICAL);
        assertEquals(256, GoblinPatronRules.scanReadCap());
        assertEquals(8, GoblinPatronRules.retentionCap());
    }
}
