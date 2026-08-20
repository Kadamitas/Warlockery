package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.LycanPackRules.CarrionFacts;
import com.kadamitas.warlockery.entity.LycanPackRules.CoordinatorCandidate;
import com.kadamitas.warlockery.entity.LycanPackRules.HuntAbortFacts;
import com.kadamitas.warlockery.entity.LycanPackRules.HuntPhase;
import com.kadamitas.warlockery.entity.LycanPackRules.HuntRole;
import com.kadamitas.warlockery.entity.LycanPackRules.PlayerRelation;
import com.kadamitas.warlockery.entity.LycanPackRules.PreyFacts;
import com.kadamitas.warlockery.entity.LycanPackRules.Relation;
import com.kadamitas.warlockery.entity.LycanPackRules.TrailObservation;
import com.kadamitas.warlockery.entity.LycanPackRules.Variant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LycanPackRulesTest {
    private static final UUID LOW_UUID = new UUID(0L, 1L);
    private static final UUID MID_UUID = new UUID(0L, 2L);
    private static final UUID HIGH_UUID = new UUID(0L, 3L);

    private static PreyFacts goodPrey() {
        return new PreyFacts("minecraft:cow", true, false, false, false, false, true, true, true, false);
    }

    private static CarrionFacts goodCarrion() {
        return new CarrionFacts("minecraft:rotten_flesh", true, false, 250, false, false, false, true);
    }

    @Test
    void variantDefaultsAndHungerArithmeticFollowTheApprovedNumbers() {
        assertEquals(300, LycanPackRules.defaultHunger(Variant.WEREWOLF));
        assertEquals(350, LycanPackRules.defaultHunger(Variant.FERAL_LYCAN));
        assertEquals(0, LycanPackRules.DEFAULT_FEAR);
        assertEquals(310, LycanPackRules.reconcileHunger(300, 0L, 400L));
        assertEquals(600, LycanPackRules.reconcileHunger(0, 0L, 480_000L),
            "elapsed hunger arithmetic caps at twenty-four thousand ticks");
        assertEquals(1_000, LycanPackRules.reconcileHunger(950, 0L, 24_000L));
        assertEquals(250, LycanPackRules.hungerAfterKill(Variant.WEREWOLF, 700));
        assertEquals(200, LycanPackRules.hungerAfterKill(Variant.FERAL_LYCAN, 700));
        assertEquals(0, LycanPackRules.hungerAfterKill(Variant.FERAL_LYCAN, 300));
        assertEquals(450, LycanPackRules.hungerAfterCarrion(700));
        assertFalse(LycanPackRules.assaultObjectiveFeedsHunger(),
            "assault victims never change ordinary hunger");
        assertTrue(LycanPackRules.mayWatchPrey(500));
        assertFalse(LycanPackRules.mayWatchPrey(499));
        assertTrue(LycanPackRules.mayStartHuntEpisode(700));
        assertFalse(LycanPackRules.mayStartHuntEpisode(699));
        assertTrue(LycanPackRules.prefersRecovery(250));
        assertFalse(LycanPackRules.prefersRecovery(251));
    }

    @Test
    void fearArithmeticAndForcedRetreatFollowTheApprovedNumbers() {
        assertEquals(120, LycanPackRules.fearAfterOrdinaryDamage(0));
        assertEquals(300, LycanPackRules.fearAfterSilverOrGuardDamage(0));
        assertEquals(1_000, LycanPackRules.fearAfterSilverOrGuardDamage(950));
        assertEquals(80, LycanPackRules.fearAfterHunterSight(0));
        assertFalse(LycanPackRules.sightFearDue(0L, 99L));
        assertTrue(LycanPackRules.sightFearDue(0L, 100L));
        assertEquals(80, LycanPackRules.reconcileFear(100, 0L, 400L));
        assertEquals(0, LycanPackRules.reconcileFear(10, 0L, 480_000L));
        assertTrue(LycanPackRules.forcedRetreat(650, 1.0F, false, 0));
        assertTrue(LycanPackRules.forcedRetreat(0, 0.35F, false, 0));
        assertTrue(LycanPackRules.forcedRetreat(0, 1.0F, true, 0));
        assertTrue(LycanPackRules.forcedRetreat(0, 1.0F, false, 3));
        assertFalse(LycanPackRules.forcedRetreat(649, 0.36F, false, 2));
        assertTrue(LycanPackRules.mayStalk(650, 0, false));
        assertFalse(LycanPackRules.mayStalk(649, 0, false));
        assertFalse(LycanPackRules.mayStalk(1_000, 850, false),
            "hunger never overrides fear at eight hundred fifty");
        assertFalse(LycanPackRules.mayStalk(1_000, 0, true),
            "hunger never overrides a hazard");
    }

    @Test
    void scheduleWindowsMoonQuorumAndWeatherConfidenceAreDistinct() {
        assertTrue(LycanPackRules.nightHuntingEligible(13_000L));
        assertTrue(LycanPackRules.nightHuntingEligible(23_000L));
        assertFalse(LycanPackRules.nightHuntingEligible(12_999L));
        assertFalse(LycanPackRules.nightHuntingEligible(23_001L));
        assertTrue(LycanPackRules.duskStarted(12_000L));
        assertFalse(LycanPackRules.duskStarted(11_999L));
        assertEquals(200, LycanPackRules.MOON_SAMPLE_INTERVAL_TICKS);
        assertEquals(2, LycanPackRules.minimumRecruitmentQuorum(true));
        assertEquals(3, LycanPackRules.minimumRecruitmentQuorum(false));
        assertEquals(24, LycanPackRules.recruitmentRadius(true));
        assertTrue(LycanPackRules.recruitmentRadius(false) < LycanPackRules.recruitmentRadius(true));
        assertTrue(LycanPackRules.maySoloHunt(700, false),
            "a starving solo hunt does not require the full moon");
        assertFalse(LycanPackRules.maySoloHunt(699, true));
        final int clear = LycanPackRules.longRangeConfidence(100, false, true);
        final int rain = LycanPackRules.longRangeConfidence(100, true, true);
        final int blocked = LycanPackRules.longRangeConfidence(100, false, false);
        assertTrue(rain < clear && blocked < clear,
            "weather and blocked sky reduce long-range confidence without cancelling anything");
    }

    @Test
    void livingPreyIsExactlyTheSixTypeGriefSafeAdultAllowlist() {
        for (final String id : List.of("minecraft:cow", "minecraft:pig", "minecraft:sheep",
            "minecraft:chicken", "minecraft:rabbit", "minecraft:goat")) {
            assertTrue(LycanPackRules.isOrdinaryPreyType(id), id);
        }
        for (final String id : List.of("minecraft:wolf", "minecraft:villager", "minecraft:horse",
            "minecraft:player", "warlockery:werewolf", "warlockery:feral_lycan",
            "warlockery:lycan_villager", "minecraft:iron_golem", "minecraft:llama")) {
            assertFalse(LycanPackRules.isOrdinaryPreyType(id), id);
        }
        assertTrue(LycanPackRules.eligibleLivingPrey(goodPrey()));
        assertFalse(LycanPackRules.eligibleLivingPrey(new PreyFacts(
            "minecraft:cow", false, false, false, false, false, true, true, true, false)), "baby");
        assertFalse(LycanPackRules.eligibleLivingPrey(new PreyFacts(
            "minecraft:cow", true, true, false, false, false, true, true, true, false)), "tamed or owned");
        assertFalse(LycanPackRules.eligibleLivingPrey(new PreyFacts(
            "minecraft:cow", true, false, true, false, false, true, true, true, false)), "custom named");
        assertFalse(LycanPackRules.eligibleLivingPrey(new PreyFacts(
            "minecraft:cow", true, false, false, true, false, true, true, true, false)), "leashed");
        assertFalse(LycanPackRules.eligibleLivingPrey(new PreyFacts(
            "minecraft:cow", true, false, false, false, true, true, true, true, false)), "vehicle or passenger");
        assertFalse(LycanPackRules.eligibleLivingPrey(new PreyFacts(
            "minecraft:cow", true, false, false, false, false, false, true, true, false)), "dead or removed");
        assertFalse(LycanPackRules.eligibleLivingPrey(new PreyFacts(
            "minecraft:cow", true, false, false, false, false, true, false, true, false)), "other dimension");
        assertFalse(LycanPackRules.eligibleLivingPrey(new PreyFacts(
            "minecraft:cow", true, false, false, false, false, true, true, false, false)), "out of range");
        assertFalse(LycanPackRules.eligibleLivingPrey(new PreyFacts(
            "minecraft:cow", true, false, false, false, false, true, true, true, true)), "event protected");
    }

    @Test
    void carrionIsExactlyTheSixDroppedItemsWithAgeOwnerAndDelayGuards() {
        for (final String id : List.of("minecraft:rotten_flesh", "minecraft:beef", "minecraft:porkchop",
            "minecraft:mutton", "minecraft:chicken", "minecraft:rabbit")) {
            assertTrue(LycanPackRules.isCarrionItem(id), id);
        }
        for (final String id : List.of("minecraft:cooked_beef", "minecraft:golden_apple",
            "minecraft:bone", "minecraft:cod")) {
            assertFalse(LycanPackRules.isCarrionItem(id), id);
        }
        assertTrue(LycanPackRules.eligibleCarrion(goodCarrion()));
        assertFalse(LycanPackRules.eligibleCarrion(new CarrionFacts(
            "minecraft:rotten_flesh", false, false, 250, false, false, false, true)), "dead item entity");
        assertFalse(LycanPackRules.eligibleCarrion(new CarrionFacts(
            "minecraft:rotten_flesh", true, true, 250, false, false, false, true)), "empty stack");
        assertFalse(LycanPackRules.eligibleCarrion(new CarrionFacts(
            "minecraft:rotten_flesh", true, false, 199, false, false, false, true)), "younger than two hundred ticks");
        assertFalse(LycanPackRules.eligibleCarrion(new CarrionFacts(
            "minecraft:rotten_flesh", true, false, 250, true, false, false, true)), "pickup delayed");
        assertFalse(LycanPackRules.eligibleCarrion(new CarrionFacts(
            "minecraft:rotten_flesh", true, false, 250, false, true, false, true)), "resolved owner");
        assertFalse(LycanPackRules.eligibleCarrion(new CarrionFacts(
            "minecraft:rotten_flesh", true, false, 250, false, false, true, true)), "custom named");
        assertFalse(LycanPackRules.eligibleCarrion(new CarrionFacts(
            "minecraft:rotten_flesh", true, false, 250, false, false, false, false)), "out of range");
        assertEquals(400, LycanPackRules.FORAGE_COOLDOWN_TICKS);
        assertEquals(100, LycanPackRules.CARRION_SCAN_INTERVAL_TICKS);
        assertEquals(6, LycanPackRules.CARRION_SCAN_RADIUS);
        assertEquals(12, LycanPackRules.MAX_RAW_CARRION_VISITS);
        assertTrue(LycanPackRules.mayForage(650, false, false, false, 0L, 0L));
        assertFalse(LycanPackRules.mayForage(649, false, false, false, 0L, 0L));
        assertFalse(LycanPackRules.mayForage(650, true, false, false, 0L, 0L), "hazard");
        assertFalse(LycanPackRules.mayForage(650, false, true, false, 0L, 0L), "direct threat");
        assertFalse(LycanPackRules.mayForage(650, false, false, true, 0L, 0L), "higher action");
        assertFalse(LycanPackRules.mayForage(650, false, false, false, 100L, 50L), "forage cooldown");
    }

    @Test
    void deterministicCandidateOrderUsesDistanceThenUnsignedUuid() {
        assertTrue(LycanPackRules.candidatePreferred(1.0D, LOW_UUID, 4.0D, HIGH_UUID));
        assertFalse(LycanPackRules.candidatePreferred(4.0D, LOW_UUID, 1.0D, HIGH_UUID));
        assertTrue(LycanPackRules.candidatePreferred(1.0D, LOW_UUID, 1.0D, HIGH_UUID),
            "distance ties resolve by unsigned UUID order");
        assertFalse(LycanPackRules.candidatePreferred(1.0D, HIGH_UUID, 1.0D, LOW_UUID));
    }

    @Test
    void playerAttributionWritesTheBoundedThreatThenGrievanceLedger() {
        final List<PlayerRelation> first = LycanPackRules.recordAttributedHit(List.of(), LOW_UUID, 1_000L);
        assertEquals(1, first.size());
        assertEquals(Relation.THREAT, first.get(0).relation());
        assertEquals(1_000L + 2_400L, first.get(0).expiresAt());
        final List<PlayerRelation> promoted = LycanPackRules.recordAttributedHit(first, LOW_UUID, 1_100L);
        assertEquals(1, promoted.size());
        assertEquals(Relation.GRIEVANCE, promoted.get(0).relation());
        assertEquals(1_100L + 12_000L, promoted.get(0).expiresAt());
        final List<PlayerRelation> refreshed = LycanPackRules.recordAttributedHit(promoted, LOW_UUID, 1_200L);
        assertEquals(1, refreshed.size());
        assertEquals(Relation.GRIEVANCE, refreshed.get(0).relation());
        assertEquals(1_200L + 12_000L, refreshed.get(0).expiresAt(),
            "later hits refresh expiry without growing a counter");
        List<PlayerRelation> ledger = List.of();
        for (int index = 0; index < 4; index++) {
            ledger = LycanPackRules.recordAttributedHit(ledger, new UUID(1L, index), 2_000L + index);
        }
        assertEquals(4, ledger.size());
        final List<PlayerRelation> evicted = LycanPackRules.recordAttributedHit(ledger, HIGH_UUID, 2_100L);
        assertEquals(4, evicted.size(), "at most four relationship entries exist");
        assertTrue(evicted.stream().anyMatch(entry -> entry.playerId().equals(HIGH_UUID)));
        assertFalse(evicted.stream().anyMatch(entry -> entry.playerId().equals(new UUID(1L, 0L))),
            "the oldest equal-confidence entry is evicted first");
        final List<PlayerRelation> expiredKept = LycanPackRules.pruneRelations(evicted, 100_000L);
        assertTrue(expiredKept.isEmpty(), "expired entries are pruned during reconciliation");
        assertTrue(LycanPackRules.acceptsAttribution(true, true, true));
        assertFalse(LycanPackRules.acceptsAttribution(false, true, true), "non-player sources write nothing");
        assertFalse(LycanPackRules.acceptsAttribution(true, false, true), "creative or spectator writes nothing");
        assertFalse(LycanPackRules.acceptsAttribution(true, true, false), "a dead attacker writes nothing");
    }

    @Test
    void coordinatorSelectionRolesSectorsPhasesAndAbortsAreDeterministic() {
        final CoordinatorCandidate leaseHolder = new CoordinatorCandidate(
            HIGH_UUID, true, false, true, 0.5F, 100
        );
        final CoordinatorCandidate eventLeader = new CoordinatorCandidate(
            MID_UUID, false, true, true, 1.0F, 900
        );
        final CoordinatorCandidate healthy = new CoordinatorCandidate(
            LOW_UUID, false, false, true, 1.0F, 900
        );
        assertEquals(HIGH_UUID, LycanPackRules.selectCoordinator(
            List.of(healthy, eventLeader, leaseHolder)).orElseThrow().memberId(),
            "a valid existing lease outranks every other coordinator credential");
        assertEquals(MID_UUID, LycanPackRules.selectCoordinator(
            List.of(healthy, eventLeader)).orElseThrow().memberId(),
            "an active event leader marker outranks ordinary credentials");
        final CoordinatorCandidate hungrier = new CoordinatorCandidate(
            HIGH_UUID, false, false, true, 1.0F, 950
        );
        assertEquals(HIGH_UUID, LycanPackRules.selectCoordinator(
            List.of(healthy, hungrier)).orElseThrow().memberId(),
            "equal health prefers higher hunger");
        final CoordinatorCandidate uuidTie = new CoordinatorCandidate(
            MID_UUID, false, false, true, 1.0F, 900
        );
        assertEquals(LOW_UUID, LycanPackRules.selectCoordinator(
            List.of(uuidTie, healthy)).orElseThrow().memberId(),
            "full ties resolve by unsigned UUID order");
        assertTrue(LycanPackRules.selectCoordinator(List.of()).isEmpty());
        assertTrue(LycanPackRules.coordinatorGrantsNoStatBonus());

        final List<UUID> members = List.of(LOW_UUID, MID_UUID, HIGH_UUID,
            new UUID(2L, 1L), new UUID(2L, 2L), new UUID(2L, 3L));
        final Map<UUID, HuntRole> roles = LycanPackRules.assignRoles(members);
        assertEquals(6, roles.size());
        assertEquals(6, roles.values().stream().distinct().count(), "roles must be unique");
        assertEquals(HuntRole.ROUTE_SETTER, roles.get(LOW_UUID));
        final Map<UUID, HuntRole> smaller = LycanPackRules.assignRoles(members.subList(0, 3));
        assertEquals(3, smaller.size());
        assertFalse(smaller.containsValue(HuntRole.REAR_GUARD), "absent roles stay unused");

        final long episode = 77L;
        assertEquals(LycanPackRules.approachSectorDegrees(episode, HuntRole.FLANK_LEFT),
            LycanPackRules.approachSectorDegrees(episode, HuntRole.FLANK_LEFT));
        assertNotEquals(LycanPackRules.approachSectorDegrees(episode, HuntRole.FLANK_LEFT),
            LycanPackRules.approachSectorDegrees(episode, HuntRole.FLANK_RIGHT),
            "each role derives its own approach sector");

        assertEquals(200, LycanPackRules.phaseDeadlineTicks(HuntPhase.RALLY));
        assertEquals(400, LycanPackRules.phaseDeadlineTicks(HuntPhase.TRAIL));
        assertEquals(160, LycanPackRules.phaseDeadlineTicks(HuntPhase.FAN_OUT));
        assertEquals(200, LycanPackRules.phaseDeadlineTicks(HuntPhase.PRESSURE));
        assertEquals(240, LycanPackRules.phaseDeadlineTicks(HuntPhase.STRIKE));
        assertEquals(160, LycanPackRules.phaseDeadlineTicks(HuntPhase.DISENGAGE));
        assertEquals(200, LycanPackRules.phaseDeadlineTicks(HuntPhase.RECOVER));
        assertEquals(6, LycanPackRules.MAX_HUNT_MEMBERS);
        assertEquals(16, LycanPackRules.MAX_RECRUITMENT_CANDIDATES);
        assertEquals(2_400, LycanPackRules.HUNT_EPISODE_TICKS);
        assertEquals(2, LycanPackRules.MAX_TARGET_CHANGES);

        assertFalse(LycanPackRules.shouldAbortHunt(new HuntAbortFacts(
            false, false, false, false, false, false, 1.0F, 0, false, 0)));
        assertTrue(LycanPackRules.shouldAbortHunt(new HuntAbortFacts(
            true, false, false, false, false, false, 1.0F, 0, false, 0)), "dawn outside an event");
        assertTrue(LycanPackRules.shouldAbortHunt(new HuntAbortFacts(
            false, true, false, false, false, false, 1.0F, 0, false, 0)), "invalid target");
        assertTrue(LycanPackRules.shouldAbortHunt(new HuntAbortFacts(
            false, false, true, false, false, false, 1.0F, 0, false, 0)), "coordinator unavailable");
        assertTrue(LycanPackRules.shouldAbortHunt(new HuntAbortFacts(
            false, false, false, true, false, false, 1.0F, 0, false, 0)), "quorum lost");
        assertTrue(LycanPackRules.shouldAbortHunt(new HuntAbortFacts(
            false, false, false, false, true, false, 1.0F, 0, false, 0)), "dimension mismatch");
        assertTrue(LycanPackRules.shouldAbortHunt(new HuntAbortFacts(
            false, false, false, false, false, true, 1.0F, 0, false, 0)), "hazard");
        assertTrue(LycanPackRules.shouldAbortHunt(new HuntAbortFacts(
            false, false, false, false, false, false, 0.20F, 0, false, 0)), "health at or below twenty percent");
        assertTrue(LycanPackRules.shouldAbortHunt(new HuntAbortFacts(
            false, false, false, false, false, false, 1.0F, 3, false, 0)), "three path failures");
        assertTrue(LycanPackRules.shouldAbortHunt(new HuntAbortFacts(
            false, false, false, false, false, false, 1.0F, 0, true, 0)), "episode deadline");
        assertTrue(LycanPackRules.shouldAbortHunt(new HuntAbortFacts(
            false, false, false, false, false, false, 1.0F, 0, false, 3)), "target changes exhausted");
    }

    @Test
    void feralFamiliarityCohortWarningTrailAndRefugeStayBounded() {
        assertEquals(1, LycanPackRules.familiarityAfterObservation(0));
        assertEquals(6, LycanPackRules.familiarityAfterObservation(6), "familiarity points cap at the bond value");
        assertTrue(LycanPackRules.bonded(6));
        assertFalse(LycanPackRules.bonded(5));
        assertEquals(4, LycanPackRules.familiarityAfterFriendlyDamage(5));
        assertEquals(0, LycanPackRules.familiarityAfterFriendlyDamage(0));
        assertEquals(3, LycanPackRules.MAX_FAMILIARITY_ENTRIES);
        assertEquals(3, LycanPackRules.MAX_COHORT_MEMBERS);
        assertEquals(2_400, LycanPackRules.COHORT_EXPIRY_TICKS);
        assertEquals(100, LycanPackRules.FAMILIARITY_OBSERVATION_INTERVAL_TICKS);
        assertEquals(200, LycanPackRules.WARNING_EXPIRY_TICKS);
        assertEquals(2, LycanPackRules.MAX_WARNING_RECIPIENTS);
        assertTrue(LycanPackRules.warningDue(0L, 100L));
        assertTrue(LycanPackRules.warningDue(0L, 1L),
            "a never-warned entity must not be rate limited against world creation time");
        assertFalse(LycanPackRules.warningDue(50L, 149L));
        assertTrue(LycanPackRules.warningDue(50L, 150L));
        assertFalse(LycanPackRules.cohortHasLeader(), "a Feral cohort has no leader and no role list");

        final TrailObservation fresh = new TrailObservation(LOW_UUID, 100, 200L, 1_000L);
        final TrailObservation stale = new TrailObservation(MID_UUID, 100, 100L, 900L);
        final TrailObservation weak = new TrailObservation(HIGH_UUID, 10, 250L, 1_050L);
        final TrailObservation expired = new TrailObservation(new UUID(9L, 9L), 900, 0L, 10L);
        List<TrailObservation> trails = List.of(fresh, stale, weak, expired);
        final TrailObservation incoming = new TrailObservation(new UUID(9L, 10L), 500, 300L, 2_700L);
        final List<TrailObservation> evictedExpired = LycanPackRules.recordTrail(trails, incoming, 300L);
        assertEquals(4, evictedExpired.size(), "trail observations cap at four");
        assertFalse(evictedExpired.contains(expired), "expired observations evict first");
        final List<TrailObservation> evictedWeak = LycanPackRules.recordTrail(
            evictedExpired, new TrailObservation(new UUID(9L, 11L), 500, 310L, 2_710L), 310L
        );
        assertFalse(evictedWeak.contains(weak), "lowest confidence evicts next");
        final List<TrailObservation> evictedOld = LycanPackRules.recordTrail(
            evictedWeak, new TrailObservation(new UUID(9L, 12L), 500, 320L, 2_720L), 320L
        );
        assertFalse(evictedOld.contains(stale), "oldest observation evicts next");
        assertEquals(2_400, LycanPackRules.TRAIL_EXPIRY_TICKS);
        assertTrue(LycanPackRules.decayedTrailConfidence(100, 0L, 2_400L)
            < LycanPackRules.decayedTrailConfidence(100, 0L, 100L),
            "trail confidence decays arithmetically with age");

        assertEquals(200, LycanPackRules.REFUGE_SEARCH_INTERVAL_TICKS);
        assertEquals(8, LycanPackRules.REFUGE_HORIZONTAL_RADIUS);
        assertEquals(4, LycanPackRules.REFUGE_VERTICAL_RADIUS);
        assertEquals(256, LycanPackRules.MAX_REFUGE_BLOCK_INSPECTIONS);
        assertEquals(12_000, LycanPackRules.REFUGE_EXPIRY_TICKS);
        assertEquals(12, LycanPackRules.TERRITORY_WARNING_RADIUS);
        assertEquals(100, LycanPackRules.TERRITORY_INTRUSION_TICKS);
        assertTrue(LycanPackRules.refugeValid(true, true, true, 1_000L, 500L, 0));
        assertFalse(LycanPackRules.refugeValid(false, true, true, 1_000L, 500L, 0), "uncovered");
        assertFalse(LycanPackRules.refugeValid(true, false, true, 1_000L, 500L, 0), "unstandable");
        assertFalse(LycanPackRules.refugeValid(true, true, false, 1_000L, 500L, 0), "unloaded");
        assertFalse(LycanPackRules.refugeValid(true, true, true, 400L, 500L, 0), "expired");
        assertFalse(LycanPackRules.refugeValid(true, true, true, 1_000L, 500L, 3), "three failed routes");
    }

    @Test
    void pounceRetreatRouteClaimAndFeedbackNumbersAreExact() {
        assertTrue(LycanPackRules.mayPounce(3.0D, true, true, false, 0L, 100L));
        assertTrue(LycanPackRules.mayPounce(6.0D, true, true, false, 0L, 100L));
        assertFalse(LycanPackRules.mayPounce(2.9D, true, true, false, 0L, 100L), "too close");
        assertFalse(LycanPackRules.mayPounce(6.1D, true, true, false, 0L, 100L), "too far");
        assertFalse(LycanPackRules.mayPounce(4.0D, false, true, false, 0L, 100L), "no line of sight");
        assertFalse(LycanPackRules.mayPounce(4.0D, true, false, false, 0L, 100L), "no standable landing");
        assertFalse(LycanPackRules.mayPounce(4.0D, true, true, true, 0L, 100L), "collision obstruction");
        assertFalse(LycanPackRules.mayPounce(4.0D, true, true, false, 200L, 100L), "cooldown");
        assertEquals(10, LycanPackRules.POUNCE_TELEGRAPH_TICKS);
        assertEquals(80, LycanPackRules.POUNCE_COOLDOWN_TICKS);
        assertEquals(80, LycanPackRules.HARRY_TICKS);
        assertEquals(20, LycanPackRules.POUNCE_ABORT_RECOVERY_TICKS);
        assertEquals(8, LycanPackRules.MAX_RETREAT_CANDIDATES);

        assertEquals(1, LycanPackRules.routeFailures(0));
        assertEquals(3, LycanPackRules.routeFailures(2));
        assertEquals(3, LycanPackRules.routeFailures(3));
        assertEquals(0L, LycanPackRules.routeBackoffUntil(1, 1_000L));
        assertEquals(1_100L, LycanPackRules.routeBackoffUntil(3, 1_000L),
            "three failed paths impose at least one hundred ticks of backoff");
        assertTrue(LycanPackRules.navigationDue(0L, 20L));
        assertFalse(LycanPackRules.navigationDue(0L, 19L));
        assertTrue(LycanPackRules.claimExpired(100L, 100L));
        assertFalse(LycanPackRules.claimExpired(101L, 100L));
        assertTrue(LycanPackRules.feedbackDue(0L, 0L));
        assertEquals(40, LycanPackRules.FEEDBACK_INTERVAL_TICKS);
        assertEquals(8, LycanPackRules.MAX_FEEDBACK_PARTICLES);
    }

    @Test
    void perceptionPlanAlertAndArmingCapsMatchTheServerSafetyBudget() {
        assertEquals(10, LycanPackRules.DECISION_INTERVAL_TICKS);
        assertEquals(40, LycanPackRules.PLAN_INTERVAL_TICKS);
        assertEquals(80, LycanPackRules.perceptionIntervalTicks(Variant.WEREWOLF));
        assertEquals(60, LycanPackRules.perceptionIntervalTicks(Variant.FERAL_LYCAN));
        assertEquals(24, LycanPackRules.perceptionRadius(Variant.WEREWOLF));
        assertEquals(20, LycanPackRules.perceptionRadius(Variant.FERAL_LYCAN));
        assertEquals(32, LycanPackRules.MAX_SCAN_RESULTS);
        assertEquals(16, LycanPackRules.MAX_RETAINED_CANDIDATES);
        assertEquals(8, LycanPackRules.MAX_LINE_OF_SIGHT_CHECKS);
        assertEquals(5, LycanPackRules.MAX_ALERT_RECIPIENTS);
        assertEquals(24, LycanPackRules.ALERT_RADIUS);
        assertEquals(100, LycanPackRules.ALERT_INTERVAL_TICKS);
        assertEquals(32, LycanPackRules.MAX_RAW_ARMING_VISITS);
        assertEquals(16, LycanPackRules.MAX_RETAINED_ARMING);
        final UUID id = UUID.randomUUID();
        final int offset = LycanPackRules.stableOffset(id, 80);
        assertEquals(offset, LycanPackRules.stableOffset(id, 80), "offsets derive from UUID bits");
        assertTrue(offset >= 0 && offset < 80);
    }

    @Test
    void typedAntiWerewolfBrewBypassesOnlyGenericResistanceAndIsNotSilver() {
        assertEquals(10.0F, CreatureCombat.adjustedDamage(
            CreatureKind.WEREWOLF, 10.0F, false, false, false, false, true, true
        ), "the typed brew bypasses only the generic supernatural reduction");
        assertEquals(20.0F, CreatureCombat.adjustedDamage(
            CreatureKind.WEREWOLF, 10.0F, true, false, false, false, true, false
        ), "silver doubling stays its own contract and the typed source never doubles");
    }
}
