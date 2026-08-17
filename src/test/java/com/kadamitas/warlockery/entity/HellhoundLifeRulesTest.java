package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.HellhoundLifeRules.BiteFacts;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.Evidence;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.EvidenceKind;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.PackRole;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.TargetFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class HellhoundLifeRulesTest {
    private static final long NOW = 12_000L;

    @Test
    void cadenceRadiusAndBudgetConstantsMatchTheApprovedDesign() {
        assertEquals(10, HellhoundLifeRules.DECISION_INTERVAL_TICKS);
        assertEquals(20, HellhoundLifeRules.OWNER_REFRESH_INTERVAL_TICKS);
        assertEquals(20, HellhoundLifeRules.EVIDENCE_SCAN_INTERVAL_TICKS);
        assertEquals(40, HellhoundLifeRules.PACK_REFRESH_INTERVAL_TICKS);
        assertEquals(40, HellhoundLifeRules.PACK_CALL_INTERVAL_TICKS);
        assertEquals(100, HellhoundLifeRules.PATROL_SEARCH_INTERVAL_TICKS);
        assertEquals(200, HellhoundLifeRules.HEAT_SEARCH_INTERVAL_TICKS);
        assertEquals(20, HellhoundLifeRules.NAVIGATION_INTERVAL_TICKS);
        assertEquals(40, HellhoundLifeRules.EVENT_FEEDBACK_INTERVAL_TICKS);
        assertEquals(200, HellhoundLifeRules.AMBIENT_FEEDBACK_INTERVAL_TICKS);
        assertEquals(16, HellhoundLifeRules.EVIDENCE_SCAN_RADIUS);
        assertEquals(16, HellhoundLifeRules.SCENT_RADIUS);
        assertEquals(8, HellhoundLifeRules.MAX_RETAINED_CANDIDATES);
        assertEquals(4, HellhoundLifeRules.MAX_EVIDENCE_RECORDS);
        assertEquals(40, HellhoundLifeRules.ATTRIBUTION_FRESHNESS_TICKS);
        assertEquals(60, HellhoundLifeRules.SNIFF_SEARCH_TICKS);
        assertEquals(12, HellhoundLifeRules.PATROL_RADIUS);
        assertEquals(12, HellhoundLifeRules.WARNING_TRIGGER_RADIUS);
        assertEquals(20, HellhoundLifeRules.WARNING_GRACE_TICKS);
        assertEquals(14, HellhoundLifeRules.WARNING_COMMIT_RADIUS);
        assertEquals(24, HellhoundLifeRules.TERRITORY_PURSUIT_LEASH);
        assertEquals(32, HellhoundLifeRules.SELF_DEFENSE_LEASH);
        assertEquals(200, HellhoundLifeRules.SELF_DEFENSE_LEASH_TICKS);
        assertEquals(4, HellhoundLifeRules.MAX_PATROL_POINTS);
        assertEquals(20, HellhoundLifeRules.PATROL_DWELL_MIN_TICKS);
        assertEquals(60, HellhoundLifeRules.PATROL_DWELL_MAX_TICKS);
        assertEquals(20, HellhoundLifeRules.PACK_REFRESH_RADIUS);
        assertEquals(20, HellhoundLifeRules.PACK_CALL_RADIUS);
        assertEquals(4, HellhoundLifeRules.MAX_PACK_MEMBERS);
        assertEquals(1, HellhoundLifeRules.NATURAL_GROUP_MIN);
        assertEquals(3, HellhoundLifeRules.NATURAL_GROUP_MAX);
        assertEquals(3, HellhoundLifeRules.SECTOR_MIN_RADIUS);
        assertEquals(5, HellhoundLifeRules.SECTOR_MAX_RADIUS);
        assertEquals(40, HellhoundLifeRules.SECTOR_SETUP_TICKS);
        assertEquals(8, HellhoundLifeRules.HEAT_RADIUS);
        assertEquals(128, HellhoundLifeRules.HEAT_MAX_BLOCK_READS);
        assertEquals(400, HellhoundLifeRules.HEAT_POINT_TICKS);
        assertEquals(8, HellhoundLifeRules.BITE_WINDUP_TICKS);
        assertEquals(2.4D, HellhoundLifeRules.BITE_COMMIT_RANGE);
        assertEquals(20, HellhoundLifeRules.BITE_RECOVERY_TICKS);
        assertEquals(0.25F, HellhoundLifeRules.RETREAT_LATCH_HEALTH_FRACTION);
        assertEquals(0.40F, HellhoundLifeRules.RETREAT_RELEASE_HEALTH_FRACTION);
        assertEquals(0.60F, HellhoundLifeRules.ISOLATION_HEALTH_FRACTION);
        assertEquals(100, HellhoundLifeRules.REGROUP_MAX_TICKS);
        assertEquals(3, HellhoundLifeRules.MAX_ROUTE_FAILURES);
        assertEquals(100, HellhoundLifeRules.ROUTE_BACKOFF_TICKS);
        assertEquals(20_000L, HellhoundLifeRules.MAX_FUTURE_HORIZON_TICKS);
        assertEquals(600L, HellhoundLifeRules.LOAD_DEADLINE_CLAMP_TICKS);
        assertEquals(32, HellhoundLifeRules.STRESS_POPULATION);
        assertEquals(4, HellhoundLifeRules.OWNER_PERIMETER_NEAR);
        assertEquals(8, HellhoundLifeRules.OWNER_PERIMETER_FAR);
        assertEquals(10, HellhoundLifeRules.OWNER_FOLLOW_DISTANCE);
        assertEquals(32, HellhoundLifeRules.OWNER_MAX_FOLLOW_DISTANCE);
    }

    @Test
    void evidenceLifetimesMatchTheApprovedObservationTypes() {
        assertEquals(200L, HellhoundLifeRules.evidenceLifetimeTicks(EvidenceKind.DIRECT_ATTACK));
        assertEquals(200L, HellhoundLifeRules.evidenceLifetimeTicks(EvidenceKind.OWNER_THREAT));
        assertEquals(100L, HellhoundLifeRules.evidenceLifetimeTicks(EvidenceKind.SIGHT));
        assertEquals(60L, HellhoundLifeRules.evidenceLifetimeTicks(EvidenceKind.SCENT));
        assertEquals(200L, HellhoundLifeRules.evidenceLifetimeTicks(EvidenceKind.TERRITORY_INTRUSION));
        assertEquals(100L, HellhoundLifeRules.evidenceLifetimeTicks(EvidenceKind.PACK_CALL));
    }

    @Test
    void evidenceConfidenceIsBoundedAndOrderedByStrength() {
        for (final EvidenceKind kind : EvidenceKind.values()) {
            final int confidence = HellhoundLifeRules.initialConfidence(kind);
            assertTrue(confidence >= 0 && confidence <= 100, kind.name());
        }
        assertTrue(HellhoundLifeRules.initialConfidence(EvidenceKind.DIRECT_ATTACK)
            >= HellhoundLifeRules.initialConfidence(EvidenceKind.TERRITORY_INTRUSION));
        assertTrue(HellhoundLifeRules.initialConfidence(EvidenceKind.TERRITORY_INTRUSION)
            > HellhoundLifeRules.initialConfidence(EvidenceKind.SIGHT));
        assertTrue(HellhoundLifeRules.initialConfidence(EvidenceKind.SIGHT)
            > HellhoundLifeRules.initialConfidence(EvidenceKind.SCENT));
    }

    @Test
    void evidencePriorityKeepsAuthorityAheadOfGenericSenses() {
        assertTrue(HellhoundLifeRules.evidencePriority(EvidenceKind.DIRECT_ATTACK)
            < HellhoundLifeRules.evidencePriority(EvidenceKind.SIGHT));
        assertTrue(HellhoundLifeRules.evidencePriority(EvidenceKind.OWNER_THREAT)
            < HellhoundLifeRules.evidencePriority(EvidenceKind.SIGHT));
        assertTrue(HellhoundLifeRules.evidencePriority(EvidenceKind.TERRITORY_INTRUSION)
            < HellhoundLifeRules.evidencePriority(EvidenceKind.SCENT));
        assertTrue(HellhoundLifeRules.evidencePriority(EvidenceKind.SIGHT)
            < HellhoundLifeRules.evidencePriority(EvidenceKind.SCENT));
    }

    @Test
    void evidenceRecordsDedupeAndTruncateDeterministically() {
        final UUID intruder = new UUID(1L, 1L);
        List<Evidence> ledger = List.of();
        ledger = HellhoundLifeRules.recordEvidence(ledger, HellhoundLifeRules.createEvidence(
            EvidenceKind.SCENT, Optional.of(intruder), Optional.of("minecraft:the_nether"),
            Optional.of(77L), NOW), NOW);
        ledger = HellhoundLifeRules.recordEvidence(ledger, HellhoundLifeRules.createEvidence(
            EvidenceKind.SCENT, Optional.of(intruder), Optional.of("minecraft:the_nether"),
            Optional.of(78L), NOW + 5L), NOW + 5L);
        assertEquals(1, ledger.size(), "a refreshed scent replaces its own record");
        assertEquals(Optional.of(78L), ledger.get(0).packedPosition(),
            "refresh keeps only the latest last-known position");
        for (int index = 0; index < 6; index++) {
            ledger = HellhoundLifeRules.recordEvidence(ledger, HellhoundLifeRules.createEvidence(
                EvidenceKind.SIGHT, Optional.of(new UUID(2L, index)),
                Optional.of("minecraft:the_nether"), Optional.of((long) index), NOW), NOW);
        }
        assertEquals(HellhoundLifeRules.MAX_EVIDENCE_RECORDS, ledger.size(),
            "the durable ledger keeps at most four records");
        final Evidence attack = HellhoundLifeRules.createEvidence(
            EvidenceKind.DIRECT_ATTACK, Optional.of(intruder),
            Optional.of("minecraft:the_nether"), Optional.of(1L), NOW);
        ledger = HellhoundLifeRules.recordEvidence(ledger, attack, NOW);
        assertTrue(ledger.stream().anyMatch(entry -> entry.kind() == EvidenceKind.DIRECT_ATTACK),
            "stronger evidence is never evicted by weaker generic senses");
        final List<Evidence> first = HellhoundLifeRules.truncate(new ArrayList<>(ledger), NOW);
        final List<Evidence> second = HellhoundLifeRules.truncate(new ArrayList<>(ledger), NOW);
        assertEquals(first, second, "truncation is deterministic");
    }

    @Test
    void expiredEvidenceIsPruned() {
        final Evidence scent = HellhoundLifeRules.createEvidence(
            EvidenceKind.SCENT, Optional.of(new UUID(3L, 3L)),
            Optional.of("minecraft:the_nether"), Optional.of(9L), NOW);
        assertTrue(scent.valid(NOW + 59L));
        assertFalse(scent.valid(NOW + 60L), "scent expires after exactly sixty ticks");
        assertTrue(HellhoundLifeRules.pruneExpired(List.of(scent), NOW + 60L).isEmpty());
    }

    @Test
    void packCallCopiesAreNeverStrongerLongerOrRebroadcast() {
        final Evidence source = HellhoundLifeRules.createEvidence(
            EvidenceKind.TERRITORY_INTRUSION, Optional.of(new UUID(4L, 4L)),
            Optional.of("minecraft:the_nether"), Optional.of(12L), NOW);
        final Evidence copy = HellhoundLifeRules.packCallCopy(source, NOW + 150L);
        assertEquals(EvidenceKind.PACK_CALL, copy.kind());
        assertEquals(source.sourceId(), copy.sourceId());
        assertEquals(source.packedPosition(), copy.packedPosition());
        assertTrue(copy.expiresAt() <= source.expiresAt(), "a copy never outlives its source");
        assertTrue(copy.expiresAt() <= NOW + 150L + 100L, "a copy lives at most one hundred ticks");
        assertTrue(copy.confidence() <= source.confidence(), "a copy is never stronger");
        assertTrue(HellhoundLifeRules.shareableWithPack(EvidenceKind.TERRITORY_INTRUSION));
        assertTrue(HellhoundLifeRules.shareableWithPack(EvidenceKind.DIRECT_ATTACK));
        assertFalse(HellhoundLifeRules.shareableWithPack(EvidenceKind.PACK_CALL),
            "one-hop copies are never rebroadcast");
    }

    @Test
    void packRolesAreUniquePerSizeAndDeterministicByUuid() {
        assertEquals(List.of(PackRole.PRESSURE), HellhoundLifeRules.rolesForSize(1));
        assertEquals(List.of(PackRole.PRESSURE, PackRole.CUTOFF), HellhoundLifeRules.rolesForSize(2));
        assertEquals(List.of(PackRole.PRESSURE, PackRole.LEFT, PackRole.RIGHT),
            HellhoundLifeRules.rolesForSize(3));
        assertEquals(List.of(PackRole.PRESSURE, PackRole.LEFT, PackRole.RIGHT, PackRole.CUTOFF),
            HellhoundLifeRules.rolesForSize(4));
        final List<UUID> members = List.of(new UUID(9L, 9L), new UUID(1L, 1L),
            new UUID(5L, 5L), new UUID(3L, 3L));
        final Map<UUID, PackRole> roles = HellhoundLifeRules.deriveRoles(members);
        assertEquals(4, roles.size());
        assertEquals(4, roles.values().stream().distinct().count(), "roles are unique");
        assertEquals(PackRole.PRESSURE, roles.get(new UUID(1L, 1L)),
            "the lowest unsigned UUID always presses, without any alpha");
        assertEquals(roles, HellhoundLifeRules.deriveRoles(List.of(new UUID(3L, 3L),
            new UUID(5L, 5L), new UUID(9L, 9L), new UUID(1L, 1L))),
            "role derivation ignores encounter order");
        final Map<UUID, PackRole> reduced = HellhoundLifeRules.deriveRoles(List.of(
            new UUID(9L, 9L), new UUID(5L, 5L), new UUID(3L, 3L)));
        assertEquals(3, reduced.size(), "member loss simply reduces the loaded role set");
        assertEquals(PackRole.PRESSURE, reduced.get(new UUID(3L, 3L)));
        final Map<UUID, PackRole> capped = HellhoundLifeRules.deriveRoles(List.of(
            new UUID(1L, 1L), new UUID(2L, 2L), new UUID(3L, 3L),
            new UUID(4L, 4L), new UUID(5L, 5L)));
        assertEquals(HellhoundLifeRules.MAX_PACK_MEMBERS, capped.size(),
            "the defensive migration cap holds at four members");
    }

    @Test
    void targetEligibilityRefusesEveryProtectedClass() {
        final TargetFacts eligible = new TargetFacts(
            true, true, true, false, false, false, false, false, false, false, false);
        assertTrue(HellhoundLifeRules.eligibleTarget(eligible));
        assertFalse(HellhoundLifeRules.eligibleTarget(new TargetFacts(
            false, true, true, false, false, false, false, false, false, false, false)), "dead");
        assertFalse(HellhoundLifeRules.eligibleTarget(new TargetFacts(
            true, false, true, false, false, false, false, false, false, false, false)), "unloaded");
        assertFalse(HellhoundLifeRules.eligibleTarget(new TargetFacts(
            true, true, false, false, false, false, false, false, false, false, false)), "cross-dimension");
        assertFalse(HellhoundLifeRules.eligibleTarget(new TargetFacts(
            true, true, true, true, false, false, false, false, false, false, false)), "owner");
        assertFalse(HellhoundLifeRules.eligibleTarget(new TargetFacts(
            true, true, true, false, true, false, false, false, false, false, false)), "owner ally");
        assertFalse(HellhoundLifeRules.eligibleTarget(new TargetFacts(
            true, true, true, false, false, true, false, false, false, false, false)), "scoreboard ally");
        assertFalse(HellhoundLifeRules.eligibleTarget(new TargetFacts(
            true, true, true, false, false, false, true, false, false, false, false)), "same pack");
        assertFalse(HellhoundLifeRules.eligibleTarget(new TargetFacts(
            true, true, true, false, false, false, false, true, false, false, false)), "creative");
        assertFalse(HellhoundLifeRules.eligibleTarget(new TargetFacts(
            true, true, true, false, false, false, false, false, true, false, false)), "spectator");
        assertFalse(HellhoundLifeRules.eligibleTarget(new TargetFacts(
            true, true, true, false, false, false, false, false, false, true, false)), "invulnerable");
        assertFalse(HellhoundLifeRules.eligibleTarget(new TargetFacts(
            true, true, true, false, false, false, false, false, false, false, true)),
            "protected progression participant");
    }

    @Test
    void territoryWarningTriggersGracePeriodsAndCommitsExactly() {
        assertTrue(HellhoundLifeRules.warningTriggered(true, true, 100.0D, false, false, false));
        assertFalse(HellhoundLifeRules.warningTriggered(false, true, 100.0D, false, false, false),
            "a bound hound replaces territory with the owner perimeter");
        assertFalse(HellhoundLifeRules.warningTriggered(true, false, 100.0D, false, false, false));
        assertFalse(HellhoundLifeRules.warningTriggered(true, true, 145.0D, false, false, false),
            "the warning trigger is twelve blocks");
        assertFalse(HellhoundLifeRules.warningTriggered(true, true, 100.0D, true, false, false), "hazard");
        assertFalse(HellhoundLifeRules.warningTriggered(true, true, 100.0D, false, true, false), "retreat");
        assertFalse(HellhoundLifeRules.warningTriggered(true, true, 100.0D, false, false, true), "combat");
        assertFalse(HellhoundLifeRules.warningGraceElapsed(NOW, NOW + 19L));
        assertTrue(HellhoundLifeRules.warningGraceElapsed(NOW, NOW + 20L));
        assertTrue(HellhoundLifeRules.warningCommits(true, true, true, true, 196.0D));
        assertFalse(HellhoundLifeRules.warningCommits(true, true, true, true, 197.0D),
            "the commit revalidation radius is fourteen blocks");
        assertFalse(HellhoundLifeRules.warningCommits(false, true, true, true, 100.0D));
        assertFalse(HellhoundLifeRules.warningCommits(true, false, true, true, 100.0D));
        assertFalse(HellhoundLifeRules.warningCommits(true, true, false, true, 100.0D));
        assertFalse(HellhoundLifeRules.warningCommits(true, true, true, false, 100.0D));
    }

    @Test
    void pursuitLeashesAreTwentyFourAndThirtyTwoBlocks() {
        assertEquals(24, HellhoundLifeRules.pursuitLeash(false));
        assertEquals(32, HellhoundLifeRules.pursuitLeash(true));
        assertFalse(HellhoundLifeRules.leashExceeded(24.0D * 24.0D, 24));
        assertTrue(HellhoundLifeRules.leashExceeded(24.1D * 24.1D, 24));
        assertFalse(HellhoundLifeRules.leashExceeded(32.0D * 32.0D, 32));
        assertTrue(HellhoundLifeRules.leashExceeded(32.1D * 32.1D, 32));
    }

    @Test
    void retreatHysteresisLatchesAndReleasesAtDistinctThresholds() {
        assertTrue(HellhoundLifeRules.retreatLatches(0.25F, 0, false, false), "quarter health latches");
        assertFalse(HellhoundLifeRules.retreatLatches(0.26F, 0, false, false));
        assertTrue(HellhoundLifeRules.retreatLatches(1.0F, 3, false, false), "three route failures latch");
        assertTrue(HellhoundLifeRules.retreatLatches(0.60F, 0, true, true),
            "an isolated committed member latches at sixty percent");
        assertFalse(HellhoundLifeRules.retreatLatches(0.61F, 0, true, true));
        assertFalse(HellhoundLifeRules.retreatLatches(0.60F, 0, false, true),
            "isolation only latches after committing with pack support");
        assertFalse(HellhoundLifeRules.retreatReleases(0.39F, false, false),
            "the latch holds below forty percent");
        assertTrue(HellhoundLifeRules.retreatReleases(0.40F, false, false));
        assertTrue(HellhoundLifeRules.retreatReleases(0.10F, true, false),
            "a direct attacker within three blocks forces one defense");
        assertTrue(HellhoundLifeRules.retreatReleases(0.10F, false, true),
            "immediate valid owner defense permits a single intercept");
    }

    @Test
    void biteSequenceRequiresEveryFactAndExactWindows() {
        assertTrue(HellhoundLifeRules.mayCommitBite(new BiteFacts(true, true, true, true, true)));
        assertFalse(HellhoundLifeRules.mayCommitBite(new BiteFacts(false, true, true, true, true)));
        assertFalse(HellhoundLifeRules.mayCommitBite(new BiteFacts(true, false, true, true, true)));
        assertFalse(HellhoundLifeRules.mayCommitBite(new BiteFacts(true, true, false, true, true)));
        assertFalse(HellhoundLifeRules.mayCommitBite(new BiteFacts(true, true, true, false, true)));
        assertFalse(HellhoundLifeRules.mayCommitBite(new BiteFacts(true, true, true, true, false)));
        assertTrue(HellhoundLifeRules.withinCommitRange(2.4D * 2.4D));
        assertFalse(HellhoundLifeRules.withinCommitRange(2.5D * 2.5D));
    }

    @Test
    void routeFailuresCountClampAndBackOff() {
        assertEquals(1, HellhoundLifeRules.nextRouteFailures(0));
        assertEquals(3, HellhoundLifeRules.nextRouteFailures(2));
        assertEquals(3, HellhoundLifeRules.nextRouteFailures(9));
        assertEquals(0L, HellhoundLifeRules.routeBackoffUntil(2, NOW));
        assertEquals(NOW + 100L, HellhoundLifeRules.routeBackoffUntil(3, NOW));
        assertEquals(6, HellhoundLifeRules.RouteFailure.values().length,
            "all six failure classes are represented");
    }

    @Test
    void zeroSentinelsAlwaysReadAsDue() {
        assertTrue(HellhoundLifeRules.due(0L, NOW));
        assertTrue(HellhoundLifeRules.due(-5L, NOW));
        assertTrue(HellhoundLifeRules.due(NOW, NOW));
        assertFalse(HellhoundLifeRules.due(NOW + 1L, NOW));
    }

    @Test
    void deadlinesClampToBoundedHorizonsAndNeverLongMax() {
        assertEquals(0L, HellhoundLifeRules.clampDeadline(0L, NOW, 200L));
        assertEquals(NOW + 200L, HellhoundLifeRules.clampDeadline(Long.MAX_VALUE, NOW, 200L));
        assertEquals(NOW + HellhoundLifeRules.MAX_FUTURE_HORIZON_TICKS,
            HellhoundLifeRules.clampDeadline(Long.MAX_VALUE, NOW, Long.MAX_VALUE),
            "no deadline may exceed the twenty-thousand-tick horizon");
        assertEquals(NOW + 600L, HellhoundLifeRules.clampLoadedDeadline(Long.MAX_VALUE, NOW, 20_000L),
            "loaded deadlines clamp to six hundred ticks beyond current time");
        assertEquals(NOW + HellhoundLifeRules.BITE_WINDUP_TICKS,
            HellhoundLifeRules.clampLoadedDeadline(Long.MAX_VALUE, NOW,
                HellhoundLifeRules.BITE_WINDUP_TICKS),
            "bite windows additionally clamp to their exact maximum");
        assertEquals(NOW + 50L, HellhoundLifeRules.clampLoadedDeadline(NOW + 50L, NOW, 600L));
        assertEquals(Long.MAX_VALUE, HellhoundLifeRules.saturatingAdd(Long.MAX_VALUE - 1L, 5L));
    }

    @Test
    void patrolDwellIsDeterministicAndBounded() {
        for (long seed = 0; seed < 200; seed++) {
            final int dwell = HellhoundLifeRules.dwellTicks(seed);
            assertTrue(dwell >= HellhoundLifeRules.PATROL_DWELL_MIN_TICKS
                && dwell <= HellhoundLifeRules.PATROL_DWELL_MAX_TICKS);
            assertEquals(dwell, HellhoundLifeRules.dwellTicks(seed));
        }
    }

    @Test
    void stableOffsetsStayInsideTheirModulus() {
        for (long bits = 0; bits < 64; bits++) {
            final int offset = HellhoundLifeRules.stableOffset(new UUID(bits, bits * 31L), 10);
            assertTrue(offset >= 0 && offset < 10);
        }
        assertNotEquals(0, HellhoundLifeRules.unsignedUuidOrder()
            .compare(new UUID(0L, 1L), new UUID(0L, 2L)));
    }

    @Test
    void ownerPerimeterFollowAndHold() {
        assertFalse(HellhoundLifeRules.followOwner(10.0D));
        assertTrue(HellhoundLifeRules.followOwner(10.1D));
        assertTrue(HellhoundLifeRules.ownerPerimeterWatch(4.0D));
        assertTrue(HellhoundLifeRules.ownerPerimeterWatch(8.0D));
        assertFalse(HellhoundLifeRules.ownerPerimeterWatch(3.9D));
        assertFalse(HellhoundLifeRules.ownerPerimeterWatch(8.1D));
    }

    /**
     * The warning ladder is only reachable when generic senses do not authorize combat by
     * themselves: SIGHT and SCENT feed warning/sniff/stalk, while combat engagement requires
     * an attributed record (direct attack, owner threat, committed intrusion, or a one-hop
     * pack copy of one of those).
     */
    @Test
    void onlyAttributedEvidenceKindsAuthorizeEngagement() {
        assertTrue(HellhoundLifeRules.engageableEvidence(EvidenceKind.DIRECT_ATTACK));
        assertTrue(HellhoundLifeRules.engageableEvidence(EvidenceKind.OWNER_THREAT));
        assertTrue(HellhoundLifeRules.engageableEvidence(EvidenceKind.TERRITORY_INTRUSION));
        assertTrue(HellhoundLifeRules.engageableEvidence(EvidenceKind.PACK_CALL));
        assertFalse(HellhoundLifeRules.engageableEvidence(EvidenceKind.SIGHT),
            "mere sight warns and stalks; it never authorizes a bite by itself");
        assertFalse(HellhoundLifeRules.engageableEvidence(EvidenceKind.SCENT),
            "scent tracks; it never authorizes a bite by itself");
    }

    /**
     * Decide-order pin: sniffing is for something you can no longer observe. A visible eligible
     * player inside warning range must fall through to the warning ladder (and, once committed,
     * to engagement) instead of being shadowed at the sniff rung by an eternally fresh SIGHT
     * record.
     */
    @Test
    void visibleEligiblePlayersReachTheWarningLadderNotSniff() {
        assertFalse(HellhoundLifeRules.sniffAtLastKnown(true, NOW, NOW),
            "a currently observable source is warned or engaged, never sniffed");
        assertFalse(HellhoundLifeRules.sniffAtLastKnown(true, NOW - 30L, NOW));
        assertTrue(HellhoundLifeRules.sniffAtLastKnown(
                false, NOW - HellhoundLifeRules.SNIFF_SEARCH_TICKS, NOW),
            "a lost refresh opens the bounded sniff window");
        assertFalse(HellhoundLifeRules.sniffAtLastKnown(
                false, NOW - HellhoundLifeRules.SNIFF_SEARCH_TICKS - 1L, NOW),
            "the sniff window is at most sixty ticks after the last observation");
    }

    @Test
    void intentCatalogMatchesTheApprovedStateMachine() {
        assertEquals(16, HellhoundLifeRules.Intent.values().length);
        assertTrue(HellhoundLifeRules.Intent.IDLE.resumesFromDisk());
        assertTrue(HellhoundLifeRules.Intent.PATROL.resumesFromDisk());
        assertTrue(HellhoundLifeRules.Intent.RETURN.resumesFromDisk());
        assertTrue(HellhoundLifeRules.Intent.RETREAT.resumesFromDisk());
        assertFalse(HellhoundLifeRules.Intent.WARN.resumesFromDisk(),
            "warning commitment never resumes from disk");
        assertFalse(HellhoundLifeRules.Intent.BITE_WINDUP.resumesFromDisk());
        assertFalse(HellhoundLifeRules.Intent.PRESS.resumesFromDisk());
        assertFalse(HellhoundLifeRules.Intent.PACK_SETUP.resumesFromDisk());
        assertFalse(HellhoundLifeRules.Intent.HAZARD_ESCAPE.resumesFromDisk());
    }

    @Test
    void packCallCadenceIsRateLimitedPerSender() {
        assertTrue(HellhoundLifeRules.mayBroadcastPackCall(0L, NOW));
        assertFalse(HellhoundLifeRules.mayBroadcastPackCall(NOW + 1L, NOW));
        assertTrue(HellhoundLifeRules.mayBroadcastPackCall(NOW, NOW));
    }
}
