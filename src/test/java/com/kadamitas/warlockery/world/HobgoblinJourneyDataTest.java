package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.HobgoblinJourneyRules;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.CampPhase;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Caravan leases, deterministic leadership, claim caps, and the reversible camp edit journal. */
final class HobgoblinJourneyDataTest {
    private static final long KEY = HobgoblinJourneyRules.caravanKey(0, 0);
    private static final long OTHER_KEY = HobgoblinJourneyRules.caravanKey(512, 512);
    private static final long CAMP = HobgoblinJourneyRules.campKey(KEY);
    private static final BlockPos ANCHOR = new BlockPos(4, 64, 4);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---------------------------------------------------------------- membership

    @Test
    void membershipIsALeaseThatDepartureAndExpiryBothEnd() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        final UUID member = new UUID(1L, 1L);
        assertTrue(data.joinCaravan(KEY, member));
        assertEquals(1, data.population(KEY));
        data.leaveCaravan(KEY, member);
        assertEquals(0, data.population(KEY));

        assertTrue(data.joinCaravan(KEY, member));
        for (int tick = 0; tick <= HobgoblinJourneyRules.MEMBER_EXPIRY_TICKS; tick++) {
            data.advanceLoadedTick(tick);
        }
        assertEquals(0, data.population(KEY), "a silent member ages out instead of inflating forever");
    }

    @Test
    void aLoadedMemberRefreshesItsOwnLeaseByRejoining() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        final UUID member = new UUID(2L, 2L);
        data.joinCaravan(KEY, member);
        for (int tick = 1; tick <= HobgoblinJourneyRules.MEMBER_EXPIRY_TICKS * 2; tick++) {
            data.advanceLoadedTick(tick);
            if (tick % HobgoblinJourneyRules.GROUP_INTERVAL_TICKS == 0) {
                data.joinCaravan(KEY, member);
            }
        }
        assertEquals(1, data.population(KEY), "the heartbeat must keep a live member seated");
    }

    @Test
    void aCaravanNeverExceedsFourMembersAndAdmitsAReplacementAfterDeparture() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        IntStream.range(0, HobgoblinJourneyRules.MAX_CARAVAN_MEMBERS)
            .forEach(index -> assertTrue(data.joinCaravan(KEY, new UUID(0L, index))));
        assertEquals(HobgoblinJourneyRules.MAX_CARAVAN_MEMBERS, data.population(KEY));
        assertFalse(data.joinCaravan(KEY, new UUID(9L, 9L)), "a fifth member is refused");
        data.leaveCaravan(KEY, new UUID(0L, 0L));
        assertTrue(data.joinCaravan(KEY, new UUID(9L, 9L)),
            "a departed seat must become available again");
    }

    @Test
    void aLoadedTickIsAppliedOnlyOnceNoMatterHowManyMembersTickIt() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        final UUID member = new UUID(3L, 3L);
        data.joinCaravan(KEY, member);
        final UUID claim = data.claim("WORK_COMMIT", member, Optional.of(ANCHOR)).orElseThrow();
        for (int repeat = 0; repeat < 8; repeat++) {
            data.advanceLoadedTick(1_000L);
        }
        assertTrue(data.holdsClaim(claim),
            "eight co-loaded members must not age one shared 200-tick lease eight times");
    }

    // ---------------------------------------------------------------- leadership

    @Test
    void leadershipIsDeterministicStableAndSurvivesABrieflyMissingLeader() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        final UUID low = new UUID(0L, 0L);
        final UUID high = new UUID(Long.MIN_VALUE, 0L);
        data.joinCaravan(KEY, low);
        data.joinCaravan(KEY, high);
        assertEquals(low, data.electLeader(KEY, List.of(high, low)).orElseThrow());
        assertEquals(low, data.electLeader(KEY, List.of(low, high)).orElseThrow());
        // The stabilization delay stops a one-tick absence from flapping the route.
        assertEquals(low, data.electLeader(KEY, List.of(high)).orElseThrow());
        for (int tick = 1; tick <= HobgoblinJourneyRules.LEADER_STABILIZE_TICKS + 1; tick++) {
            data.advanceLoadedTick(tick);
        }
        assertEquals(high, data.electLeader(KEY, List.of(high)).orElseThrow(),
            "after the delay a genuinely lost leader is replaced");
        // An empty adult list is the same "briefly missing" case, so the delay applies to it too:
        // the caravan keeps its leader for the window and only goes leaderless once it elapses.
        assertEquals(high, data.electLeader(KEY, List.of()).orElseThrow(),
            "a leader that vanished this tick is held for the stabilization window");
        for (int tick = HobgoblinJourneyRules.LEADER_STABILIZE_TICKS + 2;
             tick <= HobgoblinJourneyRules.LEADER_STABILIZE_TICKS * 2 + 4; tick++) {
            data.advanceLoadedTick(tick);
        }
        assertTrue(data.electLeader(KEY, List.of()).isEmpty(),
            "once the window elapses with nobody present the caravan is genuinely leaderless");
    }

    @Test
    void aDepartingLeaderReleasesTheRoleWithoutStrandingTheCaravan() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        final UUID leader = new UUID(5L, 5L);
        final UUID follower = new UUID(6L, 6L);
        data.joinCaravan(KEY, leader);
        data.joinCaravan(KEY, follower);
        data.electLeader(KEY, List.of(leader, follower));
        assertTrue(data.leader(KEY).isPresent());
        data.leaveCaravan(KEY, data.leader(KEY).orElseThrow());
        assertTrue(data.leader(KEY).isEmpty());
        assertEquals(1, data.population(KEY));
    }

    @Test
    void theSharedWaypointIsGroupDataRatherThanAPerMemberPath() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        data.joinCaravan(KEY, new UUID(7L, 7L));
        assertTrue(data.waypoint(KEY).isEmpty());
        data.setWaypoint(KEY, ANCHOR);
        assertEquals(ANCHOR, data.waypoint(KEY).orElseThrow());
        assertTrue(data.waypoint(OTHER_KEY).isEmpty(), "a waypoint never leaks between caravans");
    }

    // ---------------------------------------------------------------- claims

    @Test
    void aClaimantHoldsAtMostOneClaimAndAWorksiteAdmitsAtMostOneClaimant() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        final UUID first = new UUID(8L, 8L);
        final UUID second = new UUID(9L, 9L);
        assertTrue(data.claim("MINE", first, Optional.of(ANCHOR)).isPresent());
        assertTrue(data.claim("MINE", first, Optional.of(new BlockPos(9, 9, 9))).isEmpty(),
            "one live claim per claimant");
        assertTrue(data.claim("MINE", second, Optional.of(ANCHOR)).isEmpty(),
            "two travelers can never mutate the same worksite");
        assertTrue(data.siteClaimed(ANCHOR));
        assertFalse(data.siteClaimed(new BlockPos(99, 99, 99)));
        assertTrue(data.claim("MINE", null, Optional.of(ANCHOR)).isEmpty());
        assertTrue(data.claim(null, second, Optional.of(ANCHOR)).isEmpty());
    }

    @Test
    void aClaimIsALeaseThatLapsesReleasesAndCanBeReclaimed() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        final UUID claimant = new UUID(10L, 10L);
        final UUID claim = data.claim("MINE", claimant, Optional.of(ANCHOR)).orElseThrow();
        assertTrue(data.holdsClaim(claim));
        data.releaseClaim(claim);
        assertFalse(data.holdsClaim(claim));
        assertEquals(0, data.claimCount());

        final UUID reclaimed = data.claim("MINE", claimant, Optional.of(ANCHOR)).orElseThrow();
        for (int tick = 1; tick <= HobgoblinJourneyRules.CLAIM_LEASE_TICKS + 1; tick++) {
            data.advanceLoadedTick(tick);
        }
        assertFalse(data.holdsClaim(reclaimed), "an abandoned worksite never stays reserved forever");
        assertTrue(data.siteClaimed(ANCHOR) == false);
    }

    @Test
    void departureReleasesEveryClaimThatMemberHeld() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        final UUID member = new UUID(11L, 11L);
        data.joinCaravan(KEY, member);
        final UUID claim = data.claim("MINE", member, Optional.of(ANCHOR)).orElseThrow();
        data.leaveCaravan(KEY, member);
        assertFalse(data.holdsClaim(claim));
        data.releaseClaimsOf(null);
        assertEquals(0, data.claimCount());
    }

    @Test
    void theDimensionClaimCapIsExact() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        IntStream.range(0, HobgoblinJourneyRules.MAX_CLAIM_RECORDS).forEach(index ->
            assertTrue(data.claim("MINE", new UUID(20L, index),
                Optional.of(new BlockPos(index, 0, 0))).isPresent()));
        assertEquals(HobgoblinJourneyRules.MAX_CLAIM_RECORDS, data.claimCount());
        assertTrue(data.claim("MINE", new UUID(21L, 0L), Optional.of(new BlockPos(0, 9, 0))).isEmpty());
    }

    // ---------------------------------------------------------------- camps

    @Test
    void aCampIsReservedBeforeAnyBlockIsTouchedAndIsOnePerCaravan() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        data.joinCaravan(KEY, new UUID(12L, 12L));
        assertFalse(data.caravanHasCamp(KEY));
        assertTrue(data.openCamp(CAMP, KEY, ANCHOR,
            HobgoblinJourneyRules.CAMP_DIRT_COST, HobgoblinJourneyRules.CAMP_LOG_COST));
        assertTrue(data.caravanHasCamp(KEY));
        assertEquals(CampPhase.RESERVE, data.camp(CAMP).phase(),
            "a fresh record is never born active");
        assertEquals(HobgoblinJourneyRules.CAMP_DIRT_COST, data.camp(CAMP).reservedDirt());
        assertFalse(data.openCamp(CAMP + 1, KEY, ANCHOR, 0, 0), "one camp per caravan");
        assertFalse(data.openCamp(CAMP, KEY, null, 0, 0));
        assertEquals(1, data.campCount());
    }

    @Test
    void theEditJournalIsCappedRemovableAndExactlyReversible() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        data.joinCaravan(KEY, new UUID(13L, 13L));
        data.openCamp(CAMP, KEY, ANCHOR, 0, 0);
        for (int index = 0; index < HobgoblinJourneyRules.CAMP_MAX_EDITS; index++) {
            assertTrue(data.recordCampEdit(CAMP, new BlockPos(index, 64, 0), "minecraft:dirt"));
        }
        assertEquals(HobgoblinJourneyRules.CAMP_MAX_EDITS, data.campJournal(CAMP).size());
        assertFalse(data.recordCampEdit(CAMP, new BlockPos(99, 64, 0), "minecraft:dirt"),
            "the journal cap is exact");
        assertFalse(data.recordCampEdit(CAMP, null, "minecraft:dirt"));
        assertFalse(data.recordCampEdit(CAMP + 5, ANCHOR, "minecraft:dirt"), "no record, no ownership");

        final BlockPos first = data.campJournal(CAMP).get(0).position();
        assertEquals(new BlockPos(0, 64, 0), first);
        assertEquals("minecraft:dirt", data.campJournal(CAMP).get(0).placedBlockId());
        data.removeCampEdit(CAMP, first);
        assertEquals(HobgoblinJourneyRules.CAMP_MAX_EDITS - 1, data.campJournal(CAMP).size());
    }

    @Test
    void aCampRecordAgesHoldsForALiveEventAndIsClosedExactlyOnce() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        data.joinCaravan(KEY, new UUID(14L, 14L));
        data.openCamp(CAMP, KEY, ANCHOR, 0, 0);
        data.setCampPhase(CAMP, CampPhase.ACTIVE);
        assertEquals(CampPhase.ACTIVE, data.camp(CAMP).phase());
        assertFalse(data.camp(CAMP).eventHeld());
        data.holdCampForEvent(CAMP);
        assertTrue(data.camp(CAMP).eventHeld());
        data.advanceLoadedTick(1L);
        assertTrue(data.camp(CAMP).expiryRemainingTicks() < HobgoblinJourneyRules.CAMP_EXPIRY_TICKS);
        data.closeCamp(CAMP);
        assertEquals(0, data.campCount());
        assertFalse(data.caravanHasCamp(KEY));
        data.closeCamp(CAMP);
        assertEquals(0, data.campCount(), "closing twice is idempotent");
        data.setCampPhase(CAMP, CampPhase.ACTIVE);
        assertEquals(CampPhase.NONE, data.camp(CAMP).phase(), "a missing record stays missing");
    }

    @Test
    void aCampWhoseCaravanIsGoneBecomesTearDownEligibleRatherThanAnOrphan() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        final UUID member = new UUID(15L, 15L);
        data.joinCaravan(KEY, member);
        data.openCamp(CAMP, KEY, ANCHOR, 0, 0);
        data.setCampPhase(CAMP, CampPhase.ACTIVE);
        data.leaveCaravan(KEY, member);
        for (int tick = 1; tick <= 3; tick++) {
            data.advanceLoadedTick(tick);
        }
        assertEquals(CampPhase.EXPIRE, data.camp(CAMP).phase(),
            "an unowned camp must reach teardown instead of surviving unresolved");
    }

    @Test
    void theDimensionCampCapIsExactAndAnEmptyCaravanRecordIsEvictable() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        for (int index = 0; index < HobgoblinJourneyRules.MAX_CAMP_RECORDS; index++) {
            final long caravan = HobgoblinJourneyRules.caravanKey(index * 256, 0);
            data.joinCaravan(caravan, new UUID(30L, index));
            assertTrue(data.openCamp(HobgoblinJourneyRules.campKey(caravan), caravan, ANCHOR, 0, 0));
        }
        assertEquals(HobgoblinJourneyRules.MAX_CAMP_RECORDS, data.campCount());
        final long overflow = HobgoblinJourneyRules.caravanKey(999_999, 0);
        data.joinCaravan(overflow, new UUID(31L, 0L));
        assertFalse(data.openCamp(HobgoblinJourneyRules.campKey(overflow), overflow, ANCHOR, 0, 0),
            "the dimension cap refuses rather than evicting a camp that owns blocks");
    }

    @Test
    void theCaravanRecordCapRefusesRatherThanEvictingAnOccupiedRecord() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        for (int index = 0; index < HobgoblinJourneyRules.MAX_CARAVAN_RECORDS; index++) {
            assertTrue(data.joinCaravan(
                HobgoblinJourneyRules.caravanKey(index * 256, 0), new UUID(40L, index)));
        }
        assertEquals(HobgoblinJourneyRules.MAX_CARAVAN_RECORDS, data.caravanCount());
        assertFalse(data.joinCaravan(
            HobgoblinJourneyRules.caravanKey(999_999, 999_999), new UUID(41L, 0L)),
            "an occupied record is never evicted to admit a new caravan");
    }

    @Test
    void anAbsentKeyReadsAsAnEmptyRecordWithoutAllocatingStorage() {
        final HobgoblinJourneyData data = new HobgoblinJourneyData();
        assertEquals(0, data.population(OTHER_KEY));
        assertTrue(data.members(OTHER_KEY).isEmpty());
        assertTrue(data.leader(OTHER_KEY).isEmpty());
        assertFalse(data.camp(CAMP).present());
        assertEquals(0, data.caravanCount());
        assertEquals(0, data.campCount());
    }
}
