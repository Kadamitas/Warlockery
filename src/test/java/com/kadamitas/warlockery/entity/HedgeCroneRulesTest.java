package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.HedgeCroneRules.Action;
import com.kadamitas.warlockery.entity.HedgeCroneRules.Candidate;
import com.kadamitas.warlockery.entity.HedgeCroneRules.Hex;
import com.kadamitas.warlockery.entity.HedgeCroneRules.HexContext;
import com.kadamitas.warlockery.entity.HedgeCroneRules.Mode;
import com.kadamitas.warlockery.entity.HedgeCroneRules.Priority;
import com.kadamitas.warlockery.entity.HedgeCroneRules.RelationFacts;
import com.kadamitas.warlockery.entity.HedgeCroneRules.RouteResult;
import com.kadamitas.warlockery.entity.HedgeCroneRules.ThreatClass;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure truth tables for every Hedge Crone constant, relation, warning, priority, hex, ward, work,
 * action, movement, hazard, retry, deadline, and bound. Nothing here touches a level or entity.
 */
final class HedgeCroneRulesTest {
    private static UUID id(final int seed) {
        return new UUID(0L, seed);
    }

    private static RelationFacts legalIntruder() {
        return new RelationFacts(
            true, true, true, false, false, true, false, false, false, false, false, false
        );
    }

    @Test
    void everyDeclaredBudgetMatchesTheApprovedDesign() {
        assertEquals(20, HedgeCroneRules.WARNING_TICKS);
        assertEquals(20, HedgeCroneRules.HEX_WINDUP_TICKS);
        assertEquals(60, HedgeCroneRules.CAST_RECOVERY_TICKS);
        assertEquals(12, HedgeCroneRules.BOUNDARY_RADIUS);
        assertEquals(18, HedgeCroneRules.THREAT_RELEASE_RADIUS);
        assertEquals(16, HedgeCroneRules.PERCEPTION_RADIUS);
        assertEquals(200, HedgeCroneRules.THREAT_TICKS);
        assertEquals(60, HedgeCroneRules.LOST_SIGHT_RELEASE_TICKS);
        assertEquals(20, HedgeCroneRules.SCAN_INTERVAL_TICKS);
        assertEquals(16, HedgeCroneRules.MAX_CANDIDATES_VISITED);
        assertEquals(8, HedgeCroneRules.MAX_RETAINED_CANDIDATES);
        assertEquals(8, HedgeCroneRules.MAX_LINE_OF_SIGHT_CHECKS);
        assertEquals(100, HedgeCroneRules.WORKSTATION_INTERVAL_TICKS);
        assertEquals(64, HedgeCroneRules.MAX_WORKSTATION_CANDIDATES);
        assertEquals(128, HedgeCroneRules.MAX_WORKSTATION_READS);
        assertEquals(8, HedgeCroneRules.WORKSTATION_HORIZONTAL_RADIUS);
        assertEquals(2, HedgeCroneRules.WORKSTATION_VERTICAL_RADIUS);
        assertEquals(60, HedgeCroneRules.PREPARATION_TICKS);
        assertEquals(1_200, HedgeCroneRules.WARD_COOLDOWN_TICKS);
        assertEquals(16, HedgeCroneRules.ANCHOR_RETURN_RADIUS);
        assertEquals(100, HedgeCroneRules.ANCHOR_ADOPT_DELAY_TICKS);
        assertEquals(1_200, HedgeCroneRules.ANCHOR_REPLACE_TICKS);
        assertEquals(20, HedgeCroneRules.HAZARD_INTERVAL_TICKS);
        assertEquals(27, HedgeCroneRules.MAX_HAZARD_READS);
        assertEquals(24, HedgeCroneRules.MAX_SAFE_CANDIDATES);
        assertEquals(256, HedgeCroneRules.MAX_CHARGED_READS);
        assertEquals(20, HedgeCroneRules.PATH_INTERVAL_TICKS);
        assertEquals(3, HedgeCroneRules.MAX_ROUTE_FAILURES);
        assertEquals(100, HedgeCroneRules.ROUTE_BACKOFF_TICKS);
        assertEquals(0.25F, HedgeCroneRules.WITHDRAW_HEALTH_FRACTION);
        assertEquals(100, HedgeCroneRules.WITHDRAW_TICKS);
        assertEquals(3, HedgeCroneRules.CAST_MIN_RANGE);
        assertEquals(14, HedgeCroneRules.CAST_MAX_RANGE);
        assertEquals(8, HedgeCroneRules.MAX_FEEDBACK_PARTICLES);
        assertEquals(20_000, HedgeCroneRules.MAX_DEADLINE_TICKS);
    }

    @Test
    void onlyLegalNonProtectedLivingCandidatesMayBeConsidered() {
        assertTrue(HedgeCroneRules.relationLegal(legalIntruder()));
        assertFalse(HedgeCroneRules.relationLegal(new RelationFacts(
            false, true, true, false, false, true, false, false, false, false, false, false)),
            "a non-living candidate is never legal");
        assertFalse(HedgeCroneRules.relationLegal(new RelationFacts(
            true, false, true, false, false, true, false, false, false, false, false, false)),
            "a dead or removed candidate is never legal");
        assertFalse(HedgeCroneRules.relationLegal(new RelationFacts(
            true, true, false, false, false, true, false, false, false, false, false, false)),
            "a cross-dimension candidate is never legal");
        assertFalse(HedgeCroneRules.relationLegal(new RelationFacts(
            true, true, true, true, false, true, false, false, false, false, false, false)),
            "self is never legal");
        assertFalse(HedgeCroneRules.relationLegal(new RelationFacts(
            true, true, true, false, true, true, false, false, false, false, false, false)),
            "an invulnerable or unattackable candidate is never legal");
        assertFalse(HedgeCroneRules.relationLegal(new RelationFacts(
            true, true, true, false, false, true, true, false, false, false, false, false)),
            "creative and spectator players are never legal");
        assertFalse(HedgeCroneRules.relationLegal(new RelationFacts(
            true, true, true, false, false, false, false, true, false, false, false, false)),
            "another Hedge Crone is never legal");
        assertFalse(HedgeCroneRules.relationLegal(new RelationFacts(
            true, true, true, false, false, false, false, false, true, false, false, false)),
            "passive animals, villagers, and golems are never proactive prey");
        assertFalse(HedgeCroneRules.relationLegal(new RelationFacts(
            true, true, true, false, false, false, false, false, false, true, false, false)),
            "familiars, Owls, and owned creatures are never proactive prey");
    }

    @Test
    void aCircleMageIsNonPreyUnlessItIsALegalDirectAttacker() {
        final RelationFacts passingMage = new RelationFacts(
            true, true, true, false, false, false, false, false, false, false, true, false
        );
        assertFalse(HedgeCroneRules.relationLegal(passingMage),
            "species-wide Circle Mage non-prey is preferred");
        final RelationFacts attackingMage = new RelationFacts(
            true, true, true, false, false, false, false, false, false, false, true, true
        );
        assertTrue(HedgeCroneRules.relationLegal(attackingMage),
            "an accepted direct attacker overrides the species preference");
    }

    @Test
    void onlyDirectDefenseOrCompletedEscalationAcquiresATarget() {
        assertTrue(HedgeCroneRules.motiveAcquires(ThreatClass.DIRECT));
        assertTrue(HedgeCroneRules.motiveAcquires(ThreatClass.BOUNDARY_ESCALATED));
        assertFalse(HedgeCroneRules.motiveAcquires(ThreatClass.BOUNDARY_WARNED));
        assertFalse(HedgeCroneRules.motiveAcquires(ThreatClass.NONE));
    }

    @Test
    void boundaryCandidacyRequiresVisibilityInsideTheTwelveBlockBoundary() {
        assertTrue(HedgeCroneRules.boundaryCandidate(true, true, 121.0D));
        assertFalse(HedgeCroneRules.boundaryCandidate(true, true, 145.0D),
            "beyond twelve blocks from the anchor is outside the boundary");
        assertFalse(HedgeCroneRules.boundaryCandidate(false, true, 4.0D),
            "only survival players are boundary intruders");
        assertFalse(HedgeCroneRules.boundaryCandidate(true, false, 4.0D),
            "an unseen player is never warned");
    }

    @Test
    void warningEscalatesOnlyWhenTheSameCandidateStillQualifiesAtExecution() {
        assertTrue(HedgeCroneRules.warningEscalates(true, true, true, 100.0D));
        assertFalse(HedgeCroneRules.warningEscalates(false, true, true, 100.0D));
        assertFalse(HedgeCroneRules.warningEscalates(true, false, true, 100.0D));
        assertFalse(HedgeCroneRules.warningEscalates(true, true, false, 100.0D));
        assertFalse(HedgeCroneRules.warningEscalates(true, true, true, 145.0D));
    }

    @Test
    void escalatedThreatsReleaseOnRangeSightExpiryOrInvalidity() {
        assertTrue(HedgeCroneRules.threatReleases(ThreatClass.BOUNDARY_ESCALATED, false, 4.0D, 0, 100));
        assertTrue(HedgeCroneRules.threatReleases(ThreatClass.BOUNDARY_ESCALATED, true, 400.0D, 0, 100),
            "beyond eighteen blocks releases immediately");
        assertTrue(HedgeCroneRules.threatReleases(
            ThreatClass.BOUNDARY_ESCALATED, true, 4.0D, HedgeCroneRules.LOST_SIGHT_RELEASE_TICKS, 100),
            "sixty consecutive ticks without loaded line of sight releases");
        assertFalse(HedgeCroneRules.threatReleases(ThreatClass.BOUNDARY_ESCALATED, true, 4.0D, 59, 100));
        assertTrue(HedgeCroneRules.threatReleases(ThreatClass.DIRECT, true, 4.0D, 0, 0),
            "a direct attacker releases only on expiry or invalidity");
        assertFalse(HedgeCroneRules.threatReleases(ThreatClass.DIRECT, true, 400.0D, 600, 100),
            "a direct attacker does not release on range or sight alone");
    }

    @Test
    void directAndStableCandidatesArePreseededAheadOfGenericIterationOrder() {
        final List<Candidate> inspected = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            inspected.add(new Candidate(id(100 + index), false, false, 1.0D, true));
        }
        inspected.add(new Candidate(id(1), true, false, 900.0D, true));
        inspected.add(new Candidate(id(2), false, true, 400.0D, true));
        final List<Candidate> ranked = HedgeCroneRules.rank(inspected);
        assertEquals(HedgeCroneRules.MAX_RETAINED_CANDIDATES, ranked.size());
        assertEquals(id(1), ranked.getFirst().id(), "the direct attacker outranks every generic candidate");
        assertEquals(id(2), ranked.get(1).id(), "the already warned candidate is stable in second place");
        assertEquals(id(1), HedgeCroneRules.select(inspected).orElseThrow().id());
    }

    @Test
    void rankingIsStableAcrossDistanceThenUuid() {
        final List<Candidate> inspected = List.of(
            new Candidate(id(9), false, false, 25.0D, true),
            new Candidate(id(3), false, false, 25.0D, true),
            new Candidate(id(7), false, false, 4.0D, true)
        );
        assertEquals(List.of(id(7), id(3), id(9)), HedgeCroneRules.rank(inspected).stream()
            .map(Candidate::id).toList());
    }

    @Test
    void invisibleCandidatesRankButAreNeverSelected() {
        final List<Candidate> inspected = List.of(new Candidate(id(4), false, false, 1.0D, false));
        assertEquals(1, HedgeCroneRules.rank(inspected).size());
        assertTrue(HedgeCroneRules.select(inspected).isEmpty());
    }

    @Test
    void contextualHexSelectionFollowsTheExactPriorityTable() {
        assertEquals(Hex.VEIL, HedgeCroneRules.selectHex(new HexContext(true, false, false, false, true)));
        assertEquals(Hex.VEIL, HedgeCroneRules.selectHex(new HexContext(false, true, true, true, true)));
        assertEquals(Hex.BINDING, HedgeCroneRules.selectHex(new HexContext(false, false, true, true, true)));
        assertEquals(Hex.ENFEEBLE, HedgeCroneRules.selectHex(new HexContext(false, false, false, true, true)));
        assertEquals(Hex.WITHER, HedgeCroneRules.selectHex(new HexContext(false, false, false, false, true)));
        assertEquals(Hex.WITHER, HedgeCroneRules.selectHex(new HexContext(false, false, false, false, false)),
            "the escalated intruder hex is the safe default label");
    }

    @Test
    void everyExistingHexEffectDurationAndAmplifierRemainsExact() {
        assertEquals(80, HedgeCroneRules.hexDurationTicks(Hex.VEIL));
        assertEquals(0, HedgeCroneRules.hexAmplifier(Hex.VEIL));
        assertEquals(160, HedgeCroneRules.hexDurationTicks(Hex.BINDING));
        assertEquals(1, HedgeCroneRules.hexAmplifier(Hex.BINDING));
        assertEquals(160, HedgeCroneRules.hexDurationTicks(Hex.ENFEEBLE));
        assertEquals(1, HedgeCroneRules.hexAmplifier(Hex.ENFEEBLE));
        assertEquals(120, HedgeCroneRules.hexDurationTicks(Hex.WITHER));
        assertEquals(0, HedgeCroneRules.hexAmplifier(Hex.WITHER));
    }

    @Test
    void castingRequiresLineOfSightAndTheExactThreeToFourteenBand() {
        assertTrue(HedgeCroneRules.castEligible(true, true, 9.0D));
        assertTrue(HedgeCroneRules.castEligible(true, true, 196.0D));
        assertFalse(HedgeCroneRules.castEligible(true, true, 8.9D), "closer than three blocks is out of band");
        assertFalse(HedgeCroneRules.castEligible(true, true, 196.1D), "beyond fourteen blocks is out of band");
        assertFalse(HedgeCroneRules.castEligible(false, true, 100.0D), "no line of sight, no cast");
        assertFalse(HedgeCroneRules.castEligible(true, false, 100.0D), "an illegal relation never casts");
    }

    @Test
    void immutableActionTargetsAreNeverReplacedDuringAWindup() {
        assertFalse(HedgeCroneRules.mayRetarget(Action.HEX, id(1), id(2)),
            "a closer candidate cannot steal an in-flight hex");
        assertTrue(HedgeCroneRules.mayRetarget(Action.HEX, id(1), id(1)));
        assertTrue(HedgeCroneRules.mayRetarget(Action.NONE, id(1), id(2)));
    }

    @Test
    void wardPreparationAndDischargeFollowTheExactResourceContract() {
        assertTrue(HedgeCroneRules.wardPreparationAllowed(false, 0, true, false, false));
        assertFalse(HedgeCroneRules.wardPreparationAllowed(true, 0, true, false, false),
            "a prepared ward blocks another preparation");
        assertFalse(HedgeCroneRules.wardPreparationAllowed(false, 1, true, false, false),
            "the 1,200-tick cooldown must be due");
        assertFalse(HedgeCroneRules.wardPreparationAllowed(false, 0, false, false, false),
            "preparation requires a safe calm Crone");
        assertFalse(HedgeCroneRules.wardPreparationAllowed(false, 0, true, true, false),
            "a live threat cancels preparation eligibility");
        assertFalse(HedgeCroneRules.wardPreparationAllowed(false, 0, true, false, true),
            "withdrawal cancels preparation eligibility");
    }

    @Test
    void wardDischargeUsesTheExactThornsFormulaAndConsumesOnlyOnce() {
        assertEquals(2.0F, HedgeCroneRules.wardDamage(0.0F));
        assertEquals(3.0F, HedgeCroneRules.wardDamage(4.0F));
        assertEquals(6.0F, HedgeCroneRules.wardDamage(16.0F));
        assertEquals(6.0F, HedgeCroneRules.wardDamage(1_000.0F), "the discharge is capped at six");
        assertEquals(2.0F, HedgeCroneRules.wardDamage(Float.NaN), "a non-finite amount falls back to the base");

        assertTrue(HedgeCroneRules.wardDischarges(true, true, 4.0F, false));
        assertFalse(HedgeCroneRules.wardDischarges(false, true, 4.0F, false), "no prepared ward, no discharge");
        assertFalse(HedgeCroneRules.wardDischarges(true, false, 4.0F, false),
            "environmental, null-source, or invalid-relation damage never discharges");
        assertFalse(HedgeCroneRules.wardDischarges(true, true, 0.0F, false), "rejected or zero damage never discharges");
        assertFalse(HedgeCroneRules.wardDischarges(true, true, 4.0F, true), "the recursion guard blocks ward-on-ward");
    }

    @Test
    void lowHealthWithdrawalUsesTheExactQuarterHealthGate() {
        assertTrue(HedgeCroneRules.shouldWithdraw(HedgeCroneRules.healthFraction(15.0F, 60.0F)));
        assertFalse(HedgeCroneRules.shouldWithdraw(HedgeCroneRules.healthFraction(16.0F, 60.0F)));
        assertEquals(1.0F, HedgeCroneRules.healthFraction(10.0F, 0.0F), "a degenerate maximum reads as healthy");
        assertEquals(1.0F, HedgeCroneRules.healthFraction(Float.NaN, 60.0F));
    }

    @Test
    void strictPriorityPlacesHazardAboveEveryOtherBranch() {
        assertEquals(Priority.HAZARD, HedgeCroneRules.priority(
            true, true, true, true, true, true, true));
        assertEquals(Priority.ACTION, HedgeCroneRules.priority(
            false, true, true, true, true, true, true));
        assertEquals(Priority.DIRECT_DEFENSE, HedgeCroneRules.priority(
            false, false, true, true, true, true, true));
        assertEquals(Priority.WITHDRAW, HedgeCroneRules.priority(
            false, false, false, true, true, true, true));
        assertEquals(Priority.ESCALATED_THREAT, HedgeCroneRules.priority(
            false, false, false, false, true, true, true));
        assertEquals(Priority.WARNING, HedgeCroneRules.priority(
            false, false, false, false, false, true, true));
        assertEquals(Priority.WARD_PREPARATION, HedgeCroneRules.priority(
            false, false, false, false, false, false, true));
        assertEquals(Priority.ANCHOR_RETURN, HedgeCroneRules.priority(
            false, false, false, false, false, false, false));
    }

    @Test
    void navigationIsLeasedAtMostOncePerTwentyTicksAndBacksOffOnTheThirdFailure() {
        assertTrue(HedgeCroneRules.pathRequestAllowed(0, 0));
        assertFalse(HedgeCroneRules.pathRequestAllowed(1, 0));
        assertFalse(HedgeCroneRules.pathRequestAllowed(0, 1));

        final RouteResult failure = new RouteResult(false, false, false);
        final RouteResult success = new RouteResult(true, true, true);
        assertTrue(success.success());
        assertFalse(new RouteResult(true, true, false).success(), "a rejected moveTo is a route failure");
        assertFalse(new RouteResult(true, false, true).success(), "an unreachable path is a route failure");

        assertEquals(1, HedgeCroneRules.routeFailuresAfter(0, failure));
        assertEquals(3, HedgeCroneRules.routeFailuresAfter(2, failure));
        assertEquals(3, HedgeCroneRules.routeFailuresAfter(3, failure), "failures saturate at three");
        assertEquals(0, HedgeCroneRules.routeFailuresAfter(2, success));
        assertTrue(HedgeCroneRules.routeExhausted(3));
        assertFalse(HedgeCroneRules.routeExhausted(2));
        assertEquals(HedgeCroneRules.ROUTE_BACKOFF_TICKS, HedgeCroneRules.routeBackoffAfter(3));
        assertEquals(0, HedgeCroneRules.routeBackoffAfter(2));
    }

    @Test
    void anchorReturnAndReplacementFollowTheDeclaredDistancesAndDelays() {
        assertTrue(HedgeCroneRules.anchorReturnRequired(true, 289.0D));
        assertFalse(HedgeCroneRules.anchorReturnRequired(true, 256.0D));
        assertFalse(HedgeCroneRules.anchorReturnRequired(false, 10_000.0D),
            "a Crone that is not calm never patrols");
        assertTrue(HedgeCroneRules.mayAdoptReplacementAnchor(HedgeCroneRules.ANCHOR_REPLACE_TICKS, true));
        assertFalse(HedgeCroneRules.mayAdoptReplacementAnchor(HedgeCroneRules.ANCHOR_REPLACE_TICKS - 1, true));
        assertFalse(HedgeCroneRules.mayAdoptReplacementAnchor(HedgeCroneRules.ANCHOR_REPLACE_TICKS, false));
        assertTrue(HedgeCroneRules.mayAdoptAfterDimensionChange(HedgeCroneRules.ANCHOR_ADOPT_DELAY_TICKS));
        assertFalse(HedgeCroneRules.mayAdoptAfterDimensionChange(HedgeCroneRules.ANCHOR_ADOPT_DELAY_TICKS - 1));
    }

    @Test
    void deadlineClampingIsBoundedInBothDirectionsWithoutSentinelOverflow() {
        assertEquals(0, HedgeCroneRules.clampRemaining(-5, 100));
        assertEquals(100, HedgeCroneRules.clampRemaining(1_000, 100));
        assertEquals(50, HedgeCroneRules.clampRemaining(50, 100));
        assertTrue(HedgeCroneRules.MAX_DEADLINE_TICKS > 0
                && HedgeCroneRules.MAX_DEADLINE_TICKS < Integer.MAX_VALUE,
            "the bounded future sentinel is never Long.MAX_VALUE and a zero always reads as due");
        assertEquals(0, HedgeCroneRules.decrementLoaded(0));
        assertEquals(4, HedgeCroneRules.decrementLoaded(5));
    }

    @Test
    void everyDeclaredSearchEnvelopeIsGenuinelyReachableInsideItsBudget() {
        // Regression: the previous enumeration advanced the vertical layer only after eight full
        // rings, so the candidate budget was always exhausted at dy=0 and the declared vertical
        // delta was unreachable. A workstation one block above or below the Crone's feet was
        // invisible and hazard escape could never route up or down. Seed independent, so this
        // asserts every rotation rather than one lucky UUID.
        for (int seed = 0; seed < 16; seed++) {
            final UUID crone = new UUID(seed, seed * 31L + 7L);

            assertEnvelope(
                HedgeCroneRules.workstationOffsets(
                    crone, HedgeCroneRules.WORKSTATION_HORIZONTAL_RADIUS,
                    HedgeCroneRules.WORKSTATION_VERTICAL_RADIUS,
                    HedgeCroneRules.MAX_WORKSTATION_CANDIDATES),
                HedgeCroneRules.WORKSTATION_HORIZONTAL_RADIUS,
                HedgeCroneRules.WORKSTATION_VERTICAL_RADIUS,
                HedgeCroneRules.MAX_WORKSTATION_CANDIDATES,
                "workstation seed " + seed);

            assertEnvelope(
                HedgeCroneRules.safeSearchOffsets(crone, 6, 2, HedgeCroneRules.MAX_SAFE_CANDIDATES),
                6, 2, HedgeCroneRules.MAX_SAFE_CANDIDATES, "safe destination seed " + seed);
        }
    }

    @Test
    void theSharedEnvelopeIsDeterministicAndOriginFree() {
        final UUID id = id(11);
        final List<HedgeCroneRules.SearchOffset> first = HedgeCroneRules.workstationOffsets(
            id, HedgeCroneRules.WORKSTATION_HORIZONTAL_RADIUS,
            HedgeCroneRules.WORKSTATION_VERTICAL_RADIUS, HedgeCroneRules.MAX_WORKSTATION_CANDIDATES);
        assertEquals(first, HedgeCroneRules.workstationOffsets(
            id, HedgeCroneRules.WORKSTATION_HORIZONTAL_RADIUS,
            HedgeCroneRules.WORKSTATION_VERTICAL_RADIUS, HedgeCroneRules.MAX_WORKSTATION_CANDIDATES),
            "the same Crone always sweeps the same deterministic order");
        assertTrue(first.stream().noneMatch(
            offset -> offset.dx() == 0 && offset.dy() == 0 && offset.dz() == 0));
        assertEquals(first.size(), java.util.Set.copyOf(first).size(),
            "no candidate may be wasted on a duplicate");
    }

    /**
     * The whole declared envelope must be reachable: the full budget is emitted, every candidate
     * is distinct, every ring up to the horizontal radius appears, every vertical layer in the
     * delta appears, and all eight compass directions appear.
     */
    private static void assertEnvelope(
        final List<HedgeCroneRules.SearchOffset> offsets,
        final int horizontalRadius,
        final int verticalRadius,
        final int budget,
        final String label
    ) {
        assertEquals(budget, offsets.size(), label + ": the whole budget is used");
        assertEquals(offsets.size(), java.util.Set.copyOf(offsets).size(), label + ": no duplicates");
        assertTrue(offsets.stream().noneMatch(
            offset -> offset.dx() == 0 && offset.dy() == 0 && offset.dz() == 0),
            label + ": the origin is never a candidate");

        final java.util.Set<Integer> layers = offsets.stream()
            .map(HedgeCroneRules.SearchOffset::dy)
            .collect(java.util.stream.Collectors.toSet());
        for (int layer = -verticalRadius; layer <= verticalRadius; layer++) {
            assertTrue(layers.contains(layer),
                label + ": vertical layer " + layer + " must be reachable inside the budget");
        }

        final java.util.Set<Integer> rings = offsets.stream()
            .map(offset -> Math.max(Math.abs(offset.dx()), Math.abs(offset.dz())))
            .collect(java.util.stream.Collectors.toSet());
        for (int ring = 1; ring <= horizontalRadius; ring++) {
            assertTrue(rings.contains(ring),
                label + ": ring " + ring + " must be reachable inside the budget");
        }

        final java.util.Set<String> directions = offsets.stream()
            .map(offset -> Integer.signum(offset.dx()) + ":" + Integer.signum(offset.dz()))
            .collect(java.util.stream.Collectors.toSet());
        assertEquals(8, directions.size(), label + ": all eight compass directions are swept");

        assertTrue(offsets.stream().allMatch(offset ->
                Math.abs(offset.dx()) <= horizontalRadius
                    && Math.abs(offset.dz()) <= horizontalRadius
                    && Math.abs(offset.dy()) <= verticalRadius),
            label + ": no candidate leaves the declared envelope");
    }

    @Test
    void perEntityCadenceStaggerIsBoundedAndDeterministic() {
        assertEquals(0, HedgeCroneRules.stableOffset(null, 20));
        assertEquals(0, HedgeCroneRules.stableOffset(id(3), 0));
        final int offset = HedgeCroneRules.stableOffset(id(3), 20);
        assertTrue(offset >= 0 && offset < 20);
        assertEquals(offset, HedgeCroneRules.stableOffset(id(3), 20));
    }

    @Test
    void modeAndActionEnumsExposeExactlyTheApprovedSemanticStates() {
        assertEquals(
            List.of("IDLE", "WARNING", "CASTING", "PREPARING", "WITHDRAWING", "RETURNING"),
            java.util.Arrays.stream(Mode.values()).map(Enum::name).toList()
        );
        assertEquals(List.of("NONE", "HEX", "WARD_PREPARATION"),
            java.util.Arrays.stream(Action.values()).map(Enum::name).toList());
        assertEquals(List.of("NONE", "BOUNDARY_WARNED", "BOUNDARY_ESCALATED", "DIRECT"),
            java.util.Arrays.stream(ThreatClass.values()).map(Enum::name).toList());
    }
}
