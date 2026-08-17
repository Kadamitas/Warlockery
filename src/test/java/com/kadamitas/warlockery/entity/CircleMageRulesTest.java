package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.CircleMageRules.Action;
import com.kadamitas.warlockery.entity.CircleMageRules.Candidate;
import com.kadamitas.warlockery.entity.CircleMageRules.Priority;
import com.kadamitas.warlockery.entity.CircleMageRules.RecruitmentResult;
import com.kadamitas.warlockery.entity.CircleMageRules.RelationFacts;
import com.kadamitas.warlockery.entity.CircleMageRules.RouteResult;
import com.kadamitas.warlockery.entity.CircleMageRules.TargetSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure truth tables for recruitment, relationships, formation, aura, peer report, study, conclave,
 * bolt, movement, hazard, retry, deadline, and every declared bound.
 */
final class CircleMageRulesTest {
    private static UUID id(final int seed) {
        return new UUID(0L, seed);
    }

    private static RelationFacts legalAttacker() {
        return new RelationFacts(
            true, true, true, false, false, false, false, false, false, false, false, true
        );
    }

    @Test
    void everyDeclaredBudgetMatchesTheApprovedDesign() {
        assertEquals(6, CircleMageRules.MAX_COVEN_MAGES);
        assertEquals(10, CircleMageRules.OWNER_CHECK_INTERVAL_TICKS);
        assertEquals(9, CircleMageRules.FORMATION_RADIUS);
        assertEquals(8, CircleMageRules.MAX_FORMATION_PEERS_VISITED);
        assertEquals(32, CircleMageRules.SAFE_STEP_DISTANCE);
        assertEquals(100, CircleMageRules.SAFE_STEP_INTERVAL_TICKS);
        assertEquals(25, CircleMageRules.MAX_SAFE_STEP_CANDIDATES);
        assertEquals(128, CircleMageRules.MAX_SAFE_STEP_READS);
        assertEquals(20, CircleMageRules.AURA_INTERVAL_TICKS);
        assertEquals(60, CircleMageRules.AURA_DURATION_TICKS);
        assertEquals(16, CircleMageRules.AURA_RADIUS);
        assertEquals(40, CircleMageRules.PEER_SCAN_INTERVAL_TICKS);
        assertEquals(16, CircleMageRules.PEER_RADIUS);
        assertEquals(8, CircleMageRules.MAX_PEERS_VISITED);
        assertEquals(16, CircleMageRules.TARGET_RADIUS);
        assertEquals(2, CircleMageRules.MAX_PEERS_NOTIFIED);
        assertEquals(80, CircleMageRules.REPORT_EXPIRY_TICKS);
        assertEquals(12, CircleMageRules.BOLT_WINDUP_TICKS);
        assertEquals(50, CircleMageRules.BOLT_RECOVERY_TICKS);
        assertEquals(5.0F, CircleMageRules.BOLT_DAMAGE);
        assertEquals(7.0F, CircleMageRules.BOLT_FOCUSED_DAMAGE);
        assertEquals(3, CircleMageRules.BOLT_MIN_RANGE);
        assertEquals(16, CircleMageRules.BOLT_MAX_RANGE);
        assertEquals(120, CircleMageRules.STUDY_SEARCH_INTERVAL_TICKS);
        assertEquals(1_200, CircleMageRules.STUDY_COOLDOWN_TICKS);
        assertEquals(60, CircleMageRules.REHEARSAL_TICKS);
        assertEquals(100, CircleMageRules.SESSION_TIMEOUT_TICKS);
        assertEquals(3, CircleMageRules.MAX_SESSION_SIZE);
        assertEquals(2, CircleMageRules.MAX_ACCEPTED_PEERS);
        assertEquals(12, CircleMageRules.CONCLAVE_RADIUS);
        assertEquals(64, CircleMageRules.MAX_WORKSTATION_CANDIDATES);
        assertEquals(128, CircleMageRules.MAX_WORKSTATION_READS);
        assertEquals(20, CircleMageRules.HAZARD_INTERVAL_TICKS);
        assertEquals(27, CircleMageRules.MAX_HAZARD_READS);
        assertEquals(24, CircleMageRules.MAX_SAFE_CANDIDATES);
        assertEquals(256, CircleMageRules.MAX_CHARGED_READS);
        assertEquals(20, CircleMageRules.PATH_INTERVAL_TICKS);
        assertEquals(3, CircleMageRules.MAX_ROUTE_FAILURES);
        assertEquals(100, CircleMageRules.ROUTE_BACKOFF_TICKS);
        assertEquals(0.20F, CircleMageRules.WITHDRAW_HEALTH_FRACTION);
        assertEquals(100, CircleMageRules.WITHDRAW_TICKS);
    }

    @Test
    void theTwoPractitionersNeverShareAMotiveOrAHealthGate() {
        assertNotEquals(HedgeCroneRules.WITHDRAW_HEALTH_FRACTION, CircleMageRules.WITHDRAW_HEALTH_FRACTION,
            "the Crone withdraws at a quarter health and the Mage at a fifth");
        assertNotEquals(HedgeCroneRules.HEX_WINDUP_TICKS, CircleMageRules.BOLT_WINDUP_TICKS,
            "the telegraphed hex and the telegraphed bolt are deliberately distinct");
        assertNotEquals(HedgeCroneRules.CAST_RECOVERY_TICKS, CircleMageRules.BOLT_RECOVERY_TICKS);
        assertNotEquals(HedgeCroneRules.CAST_MAX_RANGE, CircleMageRules.BOLT_MAX_RANGE);
    }

    @Test
    void recruitmentOrderingPreservesEveryExistingPrerequisiteAndOutcome() {
        assertEquals(RecruitmentResult.NOT_AN_OFFERING, CircleMageRules.recruitmentDecision(
            Optional.empty(), id(1), false, true, 0));
        assertEquals(RecruitmentResult.COVEN_FULL, CircleMageRules.recruitmentDecision(
            Optional.empty(), id(1), true, true, 6));
        assertEquals(RecruitmentResult.FAMILIAR_REQUIRED, CircleMageRules.recruitmentDecision(
            Optional.empty(), id(1), true, false, 5));
        assertEquals(RecruitmentResult.RECRUITED, CircleMageRules.recruitmentDecision(
            Optional.empty(), id(1), true, true, 5));
        assertEquals(RecruitmentResult.COVEN_FULL, CircleMageRules.recruitmentDecision(
            Optional.empty(), id(1), true, false, 6),
            "the existing cap-before-familiar feedback ordering is preserved");
    }

    @Test
    void aSameOwnerRepeatIsIdempotentSuccessThatConsumesNothing() {
        final RecruitmentResult repeat = CircleMageRules.recruitmentDecision(
            Optional.of(id(1)), id(1), true, false, 6);
        assertEquals(RecruitmentResult.ALREADY_BOUND_TO_PLAYER, repeat);
        assertTrue(repeat.succeeded());
        assertFalse(repeat.consumesOffering(), "the audited duplicate-consumption defect is fixed");
    }

    @Test
    void aDifferentOwnerCanNeitherStealTheMageNorSpendAnOffering() {
        final RecruitmentResult stolen = CircleMageRules.recruitmentDecision(
            Optional.of(id(1)), id(2), true, true, 0);
        assertEquals(RecruitmentResult.BOUND_ELSEWHERE, stolen);
        assertFalse(stolen.succeeded());
        assertFalse(stolen.consumesOffering());
    }

    @Test
    void onlyRecruitmentEverConsumesAnOffering() {
        assertEquals(
            List.of(RecruitmentResult.RECRUITED),
            java.util.Arrays.stream(RecruitmentResult.values())
                .filter(RecruitmentResult::consumesOffering)
                .toList()
        );
    }

    @Test
    void relationLegalityExcludesEveryProtectedAndSameSpeciesRelation() {
        assertTrue(CircleMageRules.relationLegal(legalAttacker()));
        assertFalse(CircleMageRules.relationLegal(new RelationFacts(
            true, true, true, false, false, true, false, false, false, false, false, true)),
            "the owner is never a legal target");
        assertFalse(CircleMageRules.relationLegal(new RelationFacts(
            true, true, true, false, false, false, true, false, false, false, false, true)),
            "a same-owner creature is never a legal target");
        assertFalse(CircleMageRules.relationLegal(new RelationFacts(
            true, true, true, false, false, false, false, true, false, false, false, true)),
            "another Circle Mage is never a legal target");
        assertFalse(CircleMageRules.relationLegal(new RelationFacts(
            true, true, true, false, false, false, false, false, true, false, false, true)),
            "creative and spectator players are never legal targets");
        assertFalse(CircleMageRules.relationLegal(new RelationFacts(
            true, true, true, false, false, false, false, false, false, true, false, true)),
            "villagers, golems, and passive animals are never legal targets");
        assertFalse(CircleMageRules.relationLegal(new RelationFacts(
            true, true, true, false, false, false, false, false, false, false, true, true)),
            "familiars and other players' creatures are never legal targets");
        assertFalse(CircleMageRules.relationLegal(new RelationFacts(
            true, true, true, false, false, false, false, false, false, false, false, false)),
            "a direct or owner attacker still has to pass canAttack and enemy visibility");
        assertFalse(CircleMageRules.relationLegal(new RelationFacts(
            true, true, false, false, false, false, false, false, false, false, false, true)),
            "no cross-dimension target");
        assertFalse(CircleMageRules.relationLegal(new RelationFacts(
            true, true, true, true, false, false, false, false, false, false, false, true)),
            "self is never a target");
    }

    @Test
    void motivePriorityIsDirectThenOwnerThenPeerReport() {
        assertTrue(CircleMageRules.motivePriority(TargetSource.DIRECT)
            < CircleMageRules.motivePriority(TargetSource.OWNER));
        assertTrue(CircleMageRules.motivePriority(TargetSource.OWNER)
            < CircleMageRules.motivePriority(TargetSource.PEER_REPORT));
        assertEquals(Integer.MAX_VALUE, CircleMageRules.motivePriority(TargetSource.NONE));

        final List<Candidate> inspected = List.of(
            new Candidate(id(9), TargetSource.PEER_REPORT, 1.0D, true),
            new Candidate(id(8), TargetSource.OWNER, 900.0D, true),
            new Candidate(id(7), TargetSource.DIRECT, 400.0D, true),
            new Candidate(id(6), TargetSource.NONE, 0.0D, true)
        );
        assertEquals(List.of(id(7), id(8), id(9)),
            CircleMageRules.rank(inspected).stream().map(Candidate::id).toList());
        assertEquals(id(7), CircleMageRules.select(inspected).orElseThrow().id());
    }

    @Test
    void candidateRetentionRespectsTheVisitAndRetentionCeilings() {
        final List<Candidate> inspected = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            inspected.add(new Candidate(id(index), TargetSource.DIRECT, index, true));
        }
        assertEquals(CircleMageRules.MAX_RETAINED_CANDIDATES, CircleMageRules.rank(inspected).size());
        assertTrue(CircleMageRules.select(List.of(
            new Candidate(id(1), TargetSource.DIRECT, 1.0D, false))).isEmpty(),
            "an unseen candidate is never selected");
    }

    @Test
    void ownerDistanceBandsAndSafeStepUseTheExactThresholds() {
        assertTrue(CircleMageRules.withinFormation(81.0D));
        assertFalse(CircleMageRules.withinFormation(81.1D));
        assertTrue(CircleMageRules.safeStepAllowed(1_100.0D, 0));
        assertFalse(CircleMageRules.safeStepAllowed(1_024.0D, 0), "exactly 32 blocks is not beyond 32");
        assertFalse(CircleMageRules.safeStepAllowed(1_100.0D, 1), "the 100-tick cadence must be due");
    }

    @Test
    void formationSlotsAndAuraProviderAreStableLowestUuidOrdering() {
        final List<UUID> peers = List.of(id(5), id(3), id(9));
        assertEquals(0, CircleMageRules.formationSlot(id(1), peers));
        assertEquals(2, CircleMageRules.formationSlot(id(5), List.of(id(1), id(3), id(9))));
        assertTrue(CircleMageRules.auraProvider(id(1), peers));
        assertFalse(CircleMageRules.auraProvider(id(9), peers));
        assertTrue(CircleMageRules.auraProvider(id(4), List.of()),
            "a solo Mage is its own aura provider");
        assertEquals(CircleMageRules.formationSlot(id(1), peers),
            CircleMageRules.formationSlot(id(1), peers), "slot assignment is stable");
    }

    @Test
    void formationInspectionNeverVisitsMoreThanEightPeers() {
        final List<UUID> crowd = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            crowd.add(id(100 + index));
        }
        assertEquals(0, CircleMageRules.formationSlot(id(1), crowd),
            "only the bounded inspected set can order a slot");
    }

    @Test
    void auraRequiresALivingSameLevelOwnerInsideSixteenBlocks() {
        assertTrue(CircleMageRules.auraEligible(true, true, true, 256.0D));
        assertFalse(CircleMageRules.auraEligible(true, true, true, 256.1D));
        assertFalse(CircleMageRules.auraEligible(false, true, true, 4.0D));
        assertFalse(CircleMageRules.auraEligible(true, false, true, 4.0D));
        assertFalse(CircleMageRules.auraEligible(true, true, false, 4.0D),
            "no cross-dimension aura");
    }

    @Test
    void peerReportsAreOneHopCappedAtTwoAndOrderedStably() {
        assertTrue(CircleMageRules.mayEmitReport(TargetSource.DIRECT, 0, false));
        assertTrue(CircleMageRules.mayEmitReport(TargetSource.OWNER, 0, false));
        assertFalse(CircleMageRules.mayEmitReport(TargetSource.PEER_REPORT, 0, false),
            "a report can never recursively emit another report");
        assertFalse(CircleMageRules.mayEmitReport(TargetSource.DIRECT, 1, false));
        assertFalse(CircleMageRules.mayEmitReport(TargetSource.DIRECT, 0, true));

        final List<Candidate> peers = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            peers.add(new Candidate(id(20 - index), TargetSource.NONE, 100.0D - index, true));
        }
        peers.add(new Candidate(id(2), TargetSource.NONE, 1.0D, true));
        final List<UUID> recipients = CircleMageRules.reportRecipients(peers);
        assertEquals(CircleMageRules.MAX_PEERS_NOTIFIED, recipients.size());
        assertEquals(recipients, CircleMageRules.reportRecipients(peers));
    }

    @Test
    void receivedReportsAreIndependentlyRevalidated() {
        assertTrue(CircleMageRules.reportAcceptable(true, true, true, 1));
        assertFalse(CircleMageRules.reportAcceptable(false, true, true, 80));
        assertFalse(CircleMageRules.reportAcceptable(true, false, true, 80));
        assertFalse(CircleMageRules.reportAcceptable(true, true, false, 80));
        assertFalse(CircleMageRules.reportAcceptable(true, true, true, 0), "an expired report is dropped");
    }

    @Test
    void studySearchRequiresACalmSafeTargetlessMageWithBothCooldownsDue() {
        assertTrue(CircleMageRules.studySearchAllowed(0, 0, true, true, true, false));
        assertFalse(CircleMageRules.studySearchAllowed(1, 0, true, true, true, false));
        assertFalse(CircleMageRules.studySearchAllowed(0, 1, true, true, true, false));
        assertFalse(CircleMageRules.studySearchAllowed(0, 0, false, true, true, false));
        assertFalse(CircleMageRules.studySearchAllowed(0, 0, true, false, true, false));
        assertFalse(CircleMageRules.studySearchAllowed(0, 0, true, true, false, false));
        assertFalse(CircleMageRules.studySearchAllowed(0, 0, true, true, true, true));
    }

    @Test
    void conclaveAdmissionAndSizeAreBoundedWithoutQuorumOrRank() {
        assertTrue(CircleMageRules.conclaveAdmits(true, true, true, true, false, 100.0D));
        assertFalse(CircleMageRules.conclaveAdmits(false, true, true, true, false, 1.0D));
        assertFalse(CircleMageRules.conclaveAdmits(true, false, true, true, false, 1.0D));
        assertFalse(CircleMageRules.conclaveAdmits(true, true, false, true, false, 1.0D));
        assertFalse(CircleMageRules.conclaveAdmits(true, true, true, false, false, 1.0D));
        assertFalse(CircleMageRules.conclaveAdmits(true, true, true, true, true, 1.0D));
        assertFalse(CircleMageRules.conclaveAdmits(true, true, true, true, false, 145.0D));

        final List<UUID> eligible = List.of(id(9), id(4), id(7), id(2), id(6), id(8), id(3), id(5), id(1));
        final List<UUID> accepted = CircleMageRules.acceptPeers(eligible);
        assertEquals(CircleMageRules.MAX_ACCEPTED_PEERS, accepted.size());
        assertEquals(accepted, CircleMageRules.acceptPeers(eligible));
    }

    @Test
    void theLowestUuidCoordinatesOneSessionAndSlotsAreDeterministic() {
        assertEquals(id(1), CircleMageRules.coordinator(id(5), List.of(id(3), id(1))));
        assertEquals(id(5), CircleMageRules.coordinator(id(5), List.of()));
        final List<UUID> accepted = List.of(id(3), id(9));
        assertEquals(0, CircleMageRules.sessionSlot(id(1), id(1), accepted));
        assertEquals(1, CircleMageRules.sessionSlot(id(3), id(1), accepted));
        assertEquals(2, CircleMageRules.sessionSlot(id(9), id(1), accepted));
    }

    @Test
    void aSessionReleasesOnAnyInvalidatingFactAndIsNeverReplayed() {
        assertFalse(CircleMageRules.sessionReleased(true, true, true, true));
        assertTrue(CircleMageRules.sessionReleased(false, true, true, true));
        assertTrue(CircleMageRules.sessionReleased(true, false, true, true));
        assertTrue(CircleMageRules.sessionReleased(true, true, false, true));
        assertTrue(CircleMageRules.sessionReleased(true, true, true, false));
        // The timeout is deliberately absent: tick dispatch ends timed-out phases in one place, so
        // folding the timer in here would give the transition two owners and strand one of them.
        assertEquals(4, java.util.Arrays.stream(CircleMageRules.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("sessionReleased"))
            .findFirst().orElseThrow().getParameterCount());
    }

    @Test
    void theBoltUsesTheExactBandDamageAndFocusConsumptionRules() {
        assertTrue(CircleMageRules.boltEligible(true, true, 9.0D));
        assertTrue(CircleMageRules.boltEligible(true, true, 256.0D));
        assertFalse(CircleMageRules.boltEligible(true, true, 8.9D));
        assertFalse(CircleMageRules.boltEligible(true, true, 256.1D));
        assertFalse(CircleMageRules.boltEligible(false, true, 100.0D));
        assertFalse(CircleMageRules.boltEligible(true, false, 100.0D));

        assertEquals(5.0F, CircleMageRules.boltDamage(false));
        assertEquals(7.0F, CircleMageRules.boltDamage(true));
        assertTrue(CircleMageRules.consumesFocus(true, true));
        assertFalse(CircleMageRules.consumesFocus(true, false),
            "rejected damage consumes nothing and produces no success rider");
        assertFalse(CircleMageRules.consumesFocus(false, true));
    }

    @Test
    void immutableBoltTargetsAreNeverReplacedDuringAWindup() {
        assertFalse(CircleMageRules.mayRetarget(Action.BOLT, id(1), id(2)));
        assertTrue(CircleMageRules.mayRetarget(Action.BOLT, id(1), id(1)));
        assertTrue(CircleMageRules.mayRetarget(Action.NONE, id(1), id(2)));
    }

    @Test
    void lowHealthWithdrawalUsesTheExactFifthHealthGate() {
        assertTrue(CircleMageRules.shouldWithdraw(CircleMageRules.healthFraction(4.0F, 20.0F)));
        assertFalse(CircleMageRules.shouldWithdraw(CircleMageRules.healthFraction(5.0F, 20.0F)));
        assertEquals(1.0F, CircleMageRules.healthFraction(1.0F, 0.0F));
    }

    @Test
    void strictPriorityPlacesSeerRecallReconciliationAboveEveryOtherBranch() {
        assertEquals(Priority.RECALL, CircleMageRules.priority(
            true, true, true, true, true, true, true, true, true));
        assertEquals(Priority.HAZARD, CircleMageRules.priority(
            false, true, true, true, true, true, true, true, true));
        assertEquals(Priority.ACTION, CircleMageRules.priority(
            false, false, true, true, true, true, true, true, true));
        assertEquals(Priority.DEFENSE, CircleMageRules.priority(
            false, false, false, true, true, true, true, true, true));
        assertEquals(Priority.WITHDRAW, CircleMageRules.priority(
            false, false, false, false, true, true, true, true, true));
        assertEquals(Priority.OWNER_FOLLOW, CircleMageRules.priority(
            false, false, false, false, false, true, true, true, true));
        assertEquals(Priority.PEER_DEFENSE, CircleMageRules.priority(
            false, false, false, false, false, false, true, true, true));
        assertEquals(Priority.ACTIVE_STUDY, CircleMageRules.priority(
            false, false, false, false, false, false, false, true, true));
        assertEquals(Priority.STUDY_PROPOSAL, CircleMageRules.priority(
            false, false, false, false, false, false, false, false, true));
        assertEquals(Priority.ANCHOR_RETURN, CircleMageRules.priority(
            false, false, false, false, false, false, false, false, false));
    }

    @Test
    void navigationIsLeasedAtMostOncePerTwentyTicksAndBacksOffOnTheThirdFailure() {
        assertTrue(CircleMageRules.pathRequestAllowed(0, 0));
        assertFalse(CircleMageRules.pathRequestAllowed(1, 0));
        assertFalse(CircleMageRules.pathRequestAllowed(0, 1));
        final RouteResult failure = new RouteResult(true, true, false);
        assertFalse(failure.success());
        assertEquals(1, CircleMageRules.routeFailuresAfter(0, failure));
        assertEquals(3, CircleMageRules.routeFailuresAfter(9, failure));
        assertEquals(0, CircleMageRules.routeFailuresAfter(2, new RouteResult(true, true, true)));
        assertTrue(CircleMageRules.routeExhausted(3));
        assertEquals(CircleMageRules.ROUTE_BACKOFF_TICKS, CircleMageRules.routeBackoffAfter(3));
        assertEquals(0, CircleMageRules.routeBackoffAfter(1));
    }

    @Test
    void deadlineClampingIsBoundedInBothDirectionsWithoutSentinelOverflow() {
        assertEquals(0, CircleMageRules.clampRemaining(-1, 100));
        assertEquals(100, CircleMageRules.clampRemaining(10_000, 100));
        assertEquals(0, CircleMageRules.decrementLoaded(0));
        assertEquals(9, CircleMageRules.decrementLoaded(10));
    }

    @Test
    void everyBoundedSearchEnvelopeIsGenuinelyReachableInsideItsBudget() {
        // Same regression as the Crone: the duplicated enumeration exhausted its budget on the
        // inner rings at dy=0, so the Mage could not see a workstation one block up or down and
        // its owner safe step could never change height.
        for (int seed = 0; seed < 16; seed++) {
            final UUID mage = new UUID(seed * 13L + 3L, seed);
            assertEnvelope(CircleMageRules.workstationOffsets(
                    mage, CircleMageRules.WORKSTATION_HORIZONTAL_RADIUS,
                    CircleMageRules.WORKSTATION_VERTICAL_RADIUS,
                    CircleMageRules.MAX_WORKSTATION_CANDIDATES),
                CircleMageRules.WORKSTATION_HORIZONTAL_RADIUS,
                CircleMageRules.WORKSTATION_VERTICAL_RADIUS,
                CircleMageRules.MAX_WORKSTATION_CANDIDATES, "workstation seed " + seed);
            assertEnvelope(CircleMageRules.safeSearchOffsets(
                    mage, 6, 2, CircleMageRules.MAX_SAFE_CANDIDATES),
                6, 2, CircleMageRules.MAX_SAFE_CANDIDATES, "safe destination seed " + seed);
            assertEnvelope(CircleMageRules.safeStepOffsets(mage, 3),
                3, 1, CircleMageRules.MAX_SAFE_STEP_CANDIDATES, "owner safe step seed " + seed);
        }
    }

    @Test
    void theMageEnvelopesDelegateToTheOneSharedGeometricPrimitive() {
        // The duplicated copy was the direct cause of the unreachable-envelope defect, so the two
        // practitioners now share exactly one pure geometry helper and nothing else.
        final UUID id = id(21);
        assertEquals(
            HedgeCroneRules.safeSearchOffsets(id, 6, 2, CircleMageRules.MAX_SAFE_CANDIDATES).stream()
                .map(offset -> offset.dx() + "," + offset.dy() + "," + offset.dz()).toList(),
            CircleMageRules.safeSearchOffsets(id, 6, 2, CircleMageRules.MAX_SAFE_CANDIDATES).stream()
                .map(offset -> offset.dx() + "," + offset.dy() + "," + offset.dz()).toList(),
            "one enumeration, so the defect cannot be fixed in one place and left in the other");
    }

    private static void assertEnvelope(
        final List<CircleMageRules.SearchOffset> offsets,
        final int horizontalRadius,
        final int verticalRadius,
        final int budget,
        final String label
    ) {
        assertEquals(budget, offsets.size(), label + ": the whole budget is used");
        assertEquals(offsets.size(), java.util.Set.copyOf(offsets).size(), label + ": no duplicates");
        assertTrue(offsets.stream().noneMatch(
            offset -> offset.dx() == 0 && offset.dy() == 0 && offset.dz() == 0), label + ": origin free");
        final java.util.Set<Integer> layers = offsets.stream()
            .map(CircleMageRules.SearchOffset::dy)
            .collect(java.util.stream.Collectors.toSet());
        for (int layer = -verticalRadius; layer <= verticalRadius; layer++) {
            assertTrue(layers.contains(layer), label + ": vertical layer " + layer + " reachable");
        }
        final java.util.Set<Integer> rings = offsets.stream()
            .map(offset -> Math.max(Math.abs(offset.dx()), Math.abs(offset.dz())))
            .collect(java.util.stream.Collectors.toSet());
        for (int ring = 1; ring <= horizontalRadius; ring++) {
            assertTrue(rings.contains(ring), label + ": ring " + ring + " reachable");
        }
        assertEquals(8, offsets.stream()
            .map(offset -> Integer.signum(offset.dx()) + ":" + Integer.signum(offset.dz()))
            .collect(java.util.stream.Collectors.toSet()).size(), label + ": all eight directions");
    }

    @Test
    void stableOffsetStaggersCadenceWithoutAbsoluteWorldTime() {
        assertEquals(0, CircleMageRules.stableOffset(null, 20));
        assertEquals(0, CircleMageRules.stableOffset(id(1), 0));
        final int offset = CircleMageRules.stableOffset(id(1), 40);
        assertTrue(offset >= 0 && offset < 40);
        assertEquals(offset, CircleMageRules.stableOffset(id(1), 40));
    }
}
