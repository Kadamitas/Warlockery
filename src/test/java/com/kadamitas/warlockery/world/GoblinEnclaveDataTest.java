package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.Intent;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.RelationEvent;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Caps, claims, relations, eviction refusal, and conservative 1.4 migration for enclave storage. */
final class GoblinEnclaveDataTest {
    private static final long KEY = GoblinEnclaveRules.enclaveKey(0, 0, CreatureKind.GOBLIN);
    private static final long OTHER_KEY = GoblinEnclaveRules.enclaveKey(512, 512, CreatureKind.GOBLIN);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void anUnknownKeyReadsAsAnEmptyRecordWithoutAllocatingStorage() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        final GoblinEnclaveData.EnclaveRecord record = data.record(KEY);
        assertEquals(KEY, record.key());
        assertTrue(record.members().isEmpty());
        assertTrue(record.huts().isEmpty());
        assertEquals(0, record.ownedEdits());
        assertEquals(0, data.recordCount());
        assertFalse(data.exists(KEY));
    }

    @Test
    void membershipIsCappedAtEightAndIsIdempotentPerMember() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        final UUID first = new UUID(0L, 1L);
        assertTrue(data.joinEnclave(KEY, first));
        assertTrue(data.joinEnclave(KEY, first));
        assertEquals(1, data.population(KEY));
        IntStream.rangeClosed(2, 8).forEach(index ->
            assertTrue(data.joinEnclave(KEY, new UUID(0L, index))));
        assertEquals(GoblinEnclaveRules.MAX_MEMBERS, data.population(KEY));
        assertFalse(data.joinEnclave(KEY, new UUID(0L, 9L)));
        assertFalse(data.joinEnclave(KEY, null));
        data.leaveEnclave(KEY, first);
        assertEquals(7, data.population(KEY));
        assertTrue(data.joinEnclave(KEY, new UUID(0L, 9L)));
    }

    @Test
    void oneClaimPerGoblinOnePerWorksiteAndAtMostEightPerEnclave() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        final UUID claimant = new UUID(1L, 1L);
        final Optional<UUID> first = data.claim(KEY, Intent.MINE, claimant, Optional.of(BlockPos.ZERO));
        assertTrue(first.isPresent());
        assertTrue(data.holdsClaim(KEY, first.orElseThrow()));
        // The same claimant cannot hold a second lease.
        assertTrue(data.claim(KEY, Intent.DEPOSIT, claimant, Optional.empty()).isEmpty());
        // A different claimant cannot take the same worksite.
        assertTrue(data.claim(KEY, Intent.MINE, new UUID(1L, 2L), Optional.of(BlockPos.ZERO)).isEmpty());
        assertTrue(data.claim(KEY, Intent.MINE, new UUID(1L, 2L),
            Optional.of(new BlockPos(5, 5, 5))).isPresent());
        IntStream.rangeClosed(3, 8).forEach(index -> assertTrue(data.claim(
            KEY, Intent.PATROL, new UUID(1L, index), Optional.of(new BlockPos(index, 0, 0))
        ).isPresent()));
        assertEquals(GoblinEnclaveRules.MAX_CLAIMS, data.record(KEY).claims().size());
        assertTrue(data.claim(KEY, Intent.PATROL, new UUID(1L, 99L), Optional.empty()).isEmpty());
    }

    @Test
    void claimsReleaseIndividuallyAndByClaimant() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        final UUID claimant = new UUID(2L, 1L);
        final UUID claim = data.claim(KEY, Intent.MINE, claimant, Optional.empty()).orElseThrow();
        data.releaseClaim(KEY, claim);
        assertFalse(data.holdsClaim(KEY, claim));
        assertTrue(data.record(KEY).claims().isEmpty());
        data.claim(KEY, Intent.ALARM_PRESS, claimant, Optional.empty());
        assertEquals(1, data.defenderCount(KEY));
        data.releaseClaimsOf(KEY, claimant);
        assertEquals(0, data.defenderCount(KEY));
        // Releasing an unknown claim or claimant is a no-op rather than an error.
        data.releaseClaim(KEY, UUID.randomUUID());
        data.releaseClaimsOf(KEY, UUID.randomUUID());
        data.releaseClaim(KEY, null);
    }

    @Test
    void aLeaseAgesOnlyOncePerLoadedTickNoMatterHowManyMembersTick() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        final UUID claimant = new UUID(3L, 1L);
        data.joinEnclave(KEY, claimant);
        final UUID claim = data.claim(KEY, Intent.MINE, claimant, Optional.empty())
            .orElseThrow();
        final int initial = data.record(KEY).claims().getFirst().remainingLeaseTicks();
        assertEquals(GoblinEnclaveRules.CLAIM_LEASE_TICKS, initial);
        // Eight co-loaded members reporting the same world tick may age it exactly once.
        IntStream.range(0, 8).forEach(index -> data.advanceLoadedTick(KEY, 100L));
        assertEquals(initial - 1, data.record(KEY).claims().getFirst().remainingLeaseTicks());
        data.advanceLoadedTick(KEY, 101L);
        assertEquals(initial - 2, data.record(KEY).claims().getFirst().remainingLeaseTicks());
        IntStream.range(0, GoblinEnclaveRules.CLAIM_LEASE_TICKS).forEach(index ->
            data.advanceLoadedTick(KEY, 200L + index));
        assertFalse(data.holdsClaim(KEY, claim));
        assertTrue(data.record(KEY).claims().isEmpty());
    }

    @Test
    void aRolledBackHutReturnsBothItsSlotAndItsExactEditBudget() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        final BlockPos center = new BlockPos(0, 64, 0);
        assertTrue(data.reserveHut(KEY, center));
        assertEquals(1, data.record(KEY).huts().size());
        assertEquals(GoblinEnclaveRules.HUT_MAX_EDITS, data.record(KEY).ownedEdits());
        data.releaseHut(KEY, center);
        assertEquals(0, data.record(KEY).huts().size());
        assertEquals(0, data.record(KEY).ownedEdits(),
            "a rolled-back hut must not burn edits for a hut that never existed");
        // The freed slot is genuinely reusable, including at the same position.
        assertTrue(data.reserveHut(KEY, center));
        // Releasing something that was never reserved is a no-op, not a refund.
        data.releaseHut(KEY, new BlockPos(500, 64, 500));
        assertEquals(GoblinEnclaveRules.HUT_MAX_EDITS, data.record(KEY).ownedEdits());
        data.releaseHut(KEY, null);
    }

    @Test
    void aRolledBackTunnelAndLooseEditReturnTheirExactCharges() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        final BlockPos entrance = new BlockPos(0, 60, 0);
        assertTrue(data.reserveTunnel(KEY, entrance, 7));
        assertEquals(7, data.record(KEY).ownedEdits());
        data.releaseTunnel(KEY, entrance, 7);
        assertEquals(0, data.record(KEY).tunnels().size());
        assertEquals(0, data.record(KEY).ownedEdits());
        assertTrue(data.reserveTunnel(KEY, entrance, 7), "the one tunnel slot is reusable");

        assertTrue(data.recordEdits(KEY, 5));
        assertEquals(12, data.record(KEY).ownedEdits());
        data.releaseEdits(KEY, 5);
        assertEquals(7, data.record(KEY).ownedEdits());
        // A refund can never drive the budget negative.
        data.releaseEdits(KEY, 999);
        assertEquals(0, data.record(KEY).ownedEdits());
        data.releaseEdits(KEY, 5);
        assertEquals(0, data.record(KEY).ownedEdits());
    }

    @Test
    void repeatedReserveAndRollbackCyclesNeverLeakTheLifetimeEditBudget() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        final BlockPos center = new BlockPos(0, 64, 0);
        for (int cycle = 0; cycle < 20; cycle++) {
            assertTrue(data.reserveHut(KEY, center), "cycle " + cycle + " must still be able to plan");
            data.releaseHut(KEY, center);
        }
        assertEquals(0, data.record(KEY).ownedEdits());
        assertEquals(0, data.record(KEY).huts().size());
    }

    @Test
    void membershipIsALeaseThatDepartureAndExpiryBothEnd() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        final UUID resident = new UUID(7L, 1L);
        assertTrue(data.joinEnclave(KEY, resident));
        assertTrue(data.hasMember(KEY, resident));
        assertEquals(1, data.population(KEY));

        // Death or removal frees the seat immediately and takes the leases with it.
        data.claim(KEY, Intent.MINE, resident, Optional.empty());
        assertEquals(1, data.record(KEY).claims().size());
        data.leaveEnclave(KEY, resident);
        assertEquals(0, data.population(KEY));
        assertTrue(data.record(KEY).claims().isEmpty(),
            "a departing member takes every lease it held with it");
        data.leaveEnclave(KEY, resident);
        data.leaveEnclave(KEY, null);
        assertEquals(0, data.population(KEY));
    }

    @Test
    void anUnloadedMemberAgesOutInsteadOfInflatingThePopulationForever() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        final UUID staying = new UUID(8L, 1L);
        final UUID leaving = new UUID(8L, 2L);
        data.joinEnclave(KEY, staying);
        data.joinEnclave(KEY, leaving);
        assertEquals(2, data.population(KEY));
        // The loaded member keeps re-joining on its reconciliation cadence; the other does not.
        for (int tick = 0; tick < GoblinEnclaveRules.MEMBER_EXPIRY_TICKS + 10; tick++) {
            data.advanceLoadedTick(KEY, tick);
            if (tick % GoblinEnclaveRules.MEMBER_INTERVAL_TICKS == 0) {
                data.joinEnclave(KEY, staying);
            }
        }
        assertTrue(data.hasMember(KEY, staying), "a heartbeating member keeps its seat");
        assertFalse(data.hasMember(KEY, leaving), "a silent member ages out of the record");
        assertEquals(1, data.population(KEY));
    }

    @Test
    void afterEightGoblinsHaveEverJoinedTheEnclaveStillAdmitsAReplacement() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        for (int index = 0; index < GoblinEnclaveRules.MAX_MEMBERS; index++) {
            assertTrue(data.joinEnclave(KEY, new UUID(9L, index)));
        }
        assertFalse(data.joinEnclave(KEY, new UUID(9L, 99L)));
        // One resident dies; the seat must become available rather than being lost forever.
        data.leaveEnclave(KEY, new UUID(9L, 0L));
        assertEquals(7, data.population(KEY));
        assertTrue(data.joinEnclave(KEY, new UUID(9L, 99L)),
            "a replacement resident can anchor once a seat is freed");
        assertEquals(GoblinEnclaveRules.MAX_MEMBERS, data.population(KEY));
    }

    @Test
    void aLeaseHeldByAMemberThatIsGoneIsDroppedOnTheNextLoadedTick() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        final UUID resident = new UUID(10L, 1L);
        data.joinEnclave(KEY, resident);
        final UUID claim = data.claim(KEY, Intent.BUILD_HUT, resident, Optional.empty()).orElseThrow();
        assertTrue(data.holdsClaim(KEY, claim));
        data.record(KEY).members().forEach(member -> assertTrue(member.remainingTicks() > 0));
        // Simulate the member vanishing without a clean departure, then let the record tick.
        for (int tick = 0; tick < GoblinEnclaveRules.MEMBER_EXPIRY_TICKS + 1; tick++) {
            data.advanceLoadedTick(KEY, tick);
        }
        assertFalse(data.hasMember(KEY, resident));
        assertFalse(data.holdsClaim(KEY, claim),
            "a lease can never outlive the membership that justified it");
    }

    @Test
    void anEmptiedRecordBecomesEvictableAgainInsteadOfPinningTheDimensionCap() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        final UUID resident = new UUID(11L, 1L);
        data.joinEnclave(KEY, resident);
        assertFalse(data.record(KEY).safelyEvictable());
        data.leaveEnclave(KEY, resident);
        for (int tick = 0; tick < GoblinEnclaveRules.PROVISIONAL_EXPIRY_TICKS; tick++) {
            data.advanceLoadedTick(KEY, tick);
        }
        assertTrue(data.record(KEY).safelyEvictable(),
            "a record that lost every member must be able to expire");
    }

    @Test
    void anUnknownKeyIsNeverCreatedMerelyByAdvancingIt() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        data.advanceLoadedTick(KEY, 1L);
        assertEquals(0, data.recordCount());
    }

    @Test
    void structureCapsAndSpacingHoldAndTheSharedEditBudgetIsRespected() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        assertTrue(data.reserveHut(KEY, new BlockPos(0, 64, 0)));
        // A second hut inside the eight-block spacing is refused.
        assertFalse(data.reserveHut(KEY, new BlockPos(4, 64, 0)));
        assertTrue(data.reserveHut(KEY, new BlockPos(20, 64, 0)));
        assertTrue(data.reserveHut(KEY, new BlockPos(40, 64, 0)));
        assertFalse(data.reserveHut(KEY, new BlockPos(60, 64, 0)));
        assertEquals(GoblinEnclaveRules.MAX_HUTS, data.record(KEY).huts().size());
        assertEquals(3 * GoblinEnclaveRules.HUT_MAX_EDITS, data.record(KEY).ownedEdits());
        assertTrue(data.reserveTunnel(KEY, new BlockPos(0, 60, 0), 10));
        assertFalse(data.reserveTunnel(KEY, new BlockPos(300, 60, 0), 10));
        assertEquals(GoblinEnclaveRules.MAX_TUNNELS, data.record(KEY).tunnels().size());
        assertFalse(data.reserveTunnel(KEY, null, 10));
        assertFalse(data.reserveHut(KEY, null));
    }

    @Test
    void loggedEditsStopExactlyAtTheOwnedEditCap() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        assertTrue(data.recordEdits(KEY, GoblinEnclaveRules.MAX_OWNED_EDITS - 1));
        assertTrue(data.recordEdits(KEY, 1));
        assertEquals(GoblinEnclaveRules.MAX_OWNED_EDITS, data.record(KEY).ownedEdits());
        assertFalse(data.recordEdits(KEY, 1));
        assertFalse(data.recordEdits(KEY, 0));
        assertFalse(data.recordEdits(KEY, -5));
    }

    @Test
    void tunnelReservationRejectsSizesOutsideTheDeclaredFourToTenBand() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        assertFalse(data.reserveTunnel(KEY, new BlockPos(0, 60, 0), 3));
        assertFalse(data.reserveTunnel(KEY, new BlockPos(0, 60, 0), 11));
        assertTrue(data.reserveTunnel(KEY, new BlockPos(0, 60, 0), 4));
    }

    @Test
    void threatsAreCappedAtFourHighestUrgencyFirstAndDeduplicated() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        IntStream.rangeClosed(1, 6).forEach(index ->
            data.rememberThreat(KEY, new UUID(4L, index), index % 4));
        assertEquals(GoblinEnclaveRules.MAX_THREATS, data.threats(KEY).size());
        final UUID repeated = new UUID(4L, 1L);
        data.rememberThreat(KEY, repeated, 3);
        assertEquals(1L, data.threats(KEY).stream()
            .filter(threat -> threat.id().equals(repeated)).count());
        assertTrue(data.threats(KEY).getFirst().urgency() >= data.threats(KEY).getLast().urgency());
    }

    @Test
    void relationsAccumulateClampAndEvictDeterministicallyAtTheCap() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        final UUID player = new UUID(5L, 1L);
        data.recordRelation(KEY, player, RelationEvent.TRADE_COMPLETED);
        assertEquals(5, data.relationScore(KEY, player));
        data.recordRelation(KEY, player, RelationEvent.CONTRACT_ACCEPTED);
        assertEquals(15, data.relationScore(KEY, player));
        data.recordRelation(KEY, player, RelationEvent.MEMBER_KILLED);
        assertEquals(-25, data.relationScore(KEY, player));
        IntStream.rangeClosed(2, 9).forEach(index ->
            data.recordRelation(KEY, new UUID(5L, index), RelationEvent.GIFT_RECEIVED));
        assertEquals(GoblinEnclaveRules.MAX_RELATIONS, data.record(KEY).relations().size());
        assertEquals(0, data.relationScore(KEY, new UUID(9L, 9L)));
    }

    @Test
    void theDimensionRecordCapRefusesOverflowRatherThanEvictingAStructureBearingRecord() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        IntStream.range(0, GoblinEnclaveRules.MAX_RECORDS_PER_DIMENSION).forEach(index -> {
            final long key = GoblinEnclaveRules.enclaveKey(index * 128, 0, CreatureKind.GOBLIN);
            assertTrue(data.admit(key));
            assertTrue(data.reserveHut(key, new BlockPos(index * 128, 64, 0)));
        });
        assertEquals(GoblinEnclaveRules.MAX_RECORDS_PER_DIMENSION, data.recordCount());
        final long overflow = GoblinEnclaveRules.enclaveKey(0, 4_096, CreatureKind.GOBLIN);
        assertFalse(data.admit(overflow));
        assertFalse(data.exists(overflow));
        assertEquals(GoblinEnclaveRules.MAX_RECORDS_PER_DIMENSION, data.recordCount());
        assertFalse(data.joinEnclave(overflow, UUID.randomUUID()));
        assertTrue(data.claim(overflow, Intent.MINE, UUID.randomUUID(), Optional.empty()).isEmpty());
    }

    @Test
    void onlyAnExpiredStructurelessProvisionalRecordIsEverEvictable() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        data.admit(KEY);
        assertFalse(data.record(KEY).safelyEvictable());
        IntStream.range(0, GoblinEnclaveRules.PROVISIONAL_EXPIRY_TICKS).forEach(index ->
            data.advanceLoadedTick(KEY, index));
        assertTrue(data.record(KEY).safelyEvictable());
        data.reserveHut(KEY, new BlockPos(0, 64, 0));
        assertFalse(data.record(KEY).safelyEvictable());
    }

    @Test
    void aCommittedRecordRefreshesItsProvisionalCountdownInsteadOfExpiring() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        data.joinEnclave(KEY, UUID.randomUUID());
        IntStream.range(0, 1_000).forEach(index -> data.advanceLoadedTick(KEY, index));
        assertEquals(GoblinEnclaveRules.PROVISIONAL_EXPIRY_TICKS,
            data.record(KEY).provisionalRemainingTicks());
        assertFalse(data.record(KEY).safelyEvictable());
    }

    @Test
    void legacySettlementRecordsMigrateStructuresOnlyAndNeverInventFacts() {
        final GoblinSettlementLifeData legacy = new GoblinSettlementLifeData();
        legacy.reserveHut(KEY, new BlockPos(0, 64, 0));
        legacy.reserveHut(KEY, new BlockPos(20, 64, 0));
        legacy.reserveTunnel(KEY, new BlockPos(0, 60, 0), 6);
        final GoblinEnclaveData data = new GoblinEnclaveData();
        assertEquals(3, data.migrateFrom(legacy, KEY));
        final GoblinEnclaveData.EnclaveRecord migrated = data.record(KEY);
        assertEquals(2, migrated.huts().size());
        assertEquals(1, migrated.tunnels().size());
        assertTrue(migrated.ownedEdits() > 0);
        assertTrue(migrated.members().isEmpty());
        assertTrue(migrated.claims().isEmpty());
        assertTrue(migrated.threats().isEmpty());
        assertTrue(migrated.relations().isEmpty());
        // Migration never overwrites a record the new system already owns, and tolerates no source.
        assertEquals(0, data.migrateFrom(legacy, KEY));
        assertEquals(0, data.migrateFrom(null, OTHER_KEY));
    }

    @Test
    void anEmptyLegacyKeyMigratesToAnEmptyButAdmittedRecord() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        assertEquals(0, data.migrateFrom(new GoblinSettlementLifeData(), OTHER_KEY));
        assertTrue(data.exists(OTHER_KEY));
        assertTrue(data.record(OTHER_KEY).huts().isEmpty());
    }

    @Test
    void everyKeyStaysIndependentOfEveryOtherKey() {
        final GoblinEnclaveData data = new GoblinEnclaveData();
        data.joinEnclave(KEY, new UUID(6L, 1L));
        data.reserveHut(KEY, new BlockPos(0, 64, 0));
        assertEquals(0, data.population(OTHER_KEY));
        assertTrue(data.record(OTHER_KEY).huts().isEmpty());
        assertNotEquals(KEY, OTHER_KEY);
        data.clearForGameTest(KEY);
        assertFalse(data.exists(KEY));
        assertEquals(0, data.population(KEY));
    }
}
