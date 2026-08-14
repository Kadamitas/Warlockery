package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.WerewolfHunterRules;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.HuntFailure;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.HuntStage;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class SilverHuntDataTest {
    private static final long NOW = 30_000L;

    @Test
    void reservationHonorsTheEightRecordDimensionCap() {
        final SilverHuntData data = new SilverHuntData();
        for (int index = 0; index < WerewolfHunterRules.MAX_HUNT_RECORDS; index++) {
            assertTrue(data.reserve(new BlockPos(index * 300, 64, 0), NOW).isPresent(),
                "widely separated anchors reserve up to eight records");
        }
        assertEquals(WerewolfHunterRules.MAX_HUNT_RECORDS, data.records().size());
        assertTrue(data.reserve(new BlockPos(9_000, 64, 0), NOW).isEmpty(),
            "the ninth reservation is rejected");
    }

    @Test
    void reservationDeduplicatesWithinOneHundredTwentyEightBlocks() {
        final SilverHuntData data = new SilverHuntData();
        final Optional<UUID> first = data.reserve(new BlockPos(0, 64, 0), NOW);
        assertTrue(first.isPresent());
        assertTrue(data.reserve(new BlockPos(100, 64, 0), NOW).isEmpty(),
            "a second non-cleanup hunt within one hundred twenty-eight blocks is rejected");
        assertTrue(data.reserve(new BlockPos(200, 64, 0), NOW).isPresent(),
            "a distant anchor reserves normally");
        assertTrue(data.markCleanup(first.orElseThrow(), HuntFailure.PARTICIPANT_MISSING, NOW));
        assertTrue(data.reserve(new BlockPos(30, 64, 0), NOW).isPresent(),
            "cleanup records no longer occupy the local area");
    }

    @Test
    void activationRequiresAReservedOrPreparingStage() {
        final SilverHuntData data = new SilverHuntData();
        final UUID huntId = data.reserve(new BlockPos(0, 64, 0), NOW).orElseThrow();
        final UUID hunter = new UUID(1L, 1L);
        final UUID quarry = new UUID(2L, 2L);
        assertTrue(data.activate(huntId, hunter, quarry));
        final SilverHuntData.HuntRecord record = data.record(huntId).orElseThrow();
        assertEquals(HuntStage.ACTIVE, record.stage());
        assertEquals(Optional.of(hunter), record.hunterId());
        assertEquals(Optional.of(quarry), record.quarryId());
        assertFalse(data.activate(huntId, hunter, quarry), "an active record cannot re-activate");
        assertFalse(data.activate(new UUID(9L, 9L), hunter, quarry), "unknown hunts cannot activate");
    }

    @Test
    void discardRollsBackTheReservedRecordCompletely() {
        final SilverHuntData data = new SilverHuntData();
        final UUID huntId = data.reserve(new BlockPos(0, 64, 0), NOW).orElseThrow();
        assertTrue(data.discard(huntId));
        assertTrue(data.records().isEmpty());
        assertFalse(data.discard(huntId), "a rolled-back record cannot be removed twice");
        assertTrue(data.reserve(new BlockPos(0, 64, 0), NOW).isPresent(),
            "rollback frees the local area for a later attempt");
    }

    @Test
    void reconcileExpiresRecordsAndRunsNoFasterThanItsCadence() {
        final SilverHuntData data = new SilverHuntData();
        final UUID huntId = data.reserve(new BlockPos(0, 64, 0), NOW).orElseThrow();
        assertEquals(0, data.reconcile(NOW), "a live record needs no cleanup work");
        assertEquals(0, data.reconcile(NOW + WerewolfHunterRules.HUNT_CLEANUP_INTERVAL_TICKS - 1L),
            "reconcile runs no faster than every two hundred ticks");
        final long expiredAt = NOW + WerewolfHunterRules.HUNT_RECORD_TICKS;
        assertEquals(1, data.reconcile(expiredAt + 1L), "an expired record moves to cleanup");
        assertEquals(HuntStage.CLEANUP, data.record(huntId).orElseThrow().stage());
        assertEquals(HuntFailure.EXPIRED, data.record(huntId).orElseThrow().failure());
        final long removalTime = data.record(huntId).orElseThrow().cleanupDeadline()
            + WerewolfHunterRules.HUNT_CLEANUP_INTERVAL_TICKS;
        assertEquals(1, data.reconcile(removalTime), "an elapsed cleanup deadline removes the record");
        assertTrue(data.records().isEmpty());
    }

    @Test
    void recordsCarryTheVersionedSchemaAndExpiry() {
        final SilverHuntData data = new SilverHuntData();
        final UUID huntId = data.reserve(new BlockPos(5, 64, 5), NOW).orElseThrow();
        final SilverHuntData.HuntRecord record = data.record(huntId).orElseThrow();
        assertEquals(SilverHuntData.SCHEMA_VERSION, record.version());
        assertEquals(NOW, record.createdAt());
        assertEquals(NOW + WerewolfHunterRules.HUNT_RECORD_TICKS, record.expiresAt(),
            "a record expires after at most six thousand ticks");
        assertEquals(HuntStage.RESERVED, record.stage());
        assertEquals(HuntFailure.NONE, record.failure());
        assertTrue(record.hunterId().isEmpty() && record.quarryId().isEmpty(),
            "reservation precedes any participant identity");
    }
}
