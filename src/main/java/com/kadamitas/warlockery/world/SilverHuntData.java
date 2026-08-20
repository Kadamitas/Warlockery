package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.WerewolfHunterRules;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.HuntFailure;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.HuntStage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class SilverHuntData extends SavedData {
    public static final int SCHEMA_VERSION = 1;

    public record HuntRecord(
        int version,
        UUID huntId,
        BlockPos anchor,
        long createdAt,
        long expiresAt,
        HuntStage stage,
        Optional<UUID> hunterId,
        Optional<UUID> quarryId,
        long cleanupDeadline,
        HuntFailure failure
    ) {
        public HuntRecord withStage(final HuntStage updated) {
            return new HuntRecord(version, huntId, anchor, createdAt, expiresAt, updated,
                hunterId, quarryId, cleanupDeadline, failure);
        }

        public HuntRecord withParticipants(final UUID hunter, final UUID quarry) {
            return new HuntRecord(version, huntId, anchor, createdAt, expiresAt, stage,
                Optional.of(hunter), Optional.of(quarry), cleanupDeadline, failure);
        }

        public HuntRecord toCleanup(final HuntFailure reason, final long deadline) {
            return new HuntRecord(version, huntId, anchor, createdAt, expiresAt, HuntStage.CLEANUP,
                hunterId, quarryId, deadline, reason);
        }
    }

    private static final Codec<HuntRecord> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("version", SCHEMA_VERSION).forGetter(HuntRecord::version),
        UUIDUtil.CODEC.fieldOf("hunt_id").forGetter(HuntRecord::huntId),
        Codec.LONG.fieldOf("anchor").forGetter(record -> record.anchor().asLong()),
        Codec.LONG.fieldOf("created_at").forGetter(HuntRecord::createdAt),
        Codec.LONG.fieldOf("expires_at").forGetter(HuntRecord::expiresAt),
        Codec.STRING.fieldOf("stage").forGetter(record -> record.stage().name()),
        UUIDUtil.CODEC.optionalFieldOf("hunter_id").forGetter(HuntRecord::hunterId),
        UUIDUtil.CODEC.optionalFieldOf("quarry_id").forGetter(HuntRecord::quarryId),
        Codec.LONG.optionalFieldOf("cleanup_deadline", 0L).forGetter(HuntRecord::cleanupDeadline),
        Codec.STRING.optionalFieldOf("failure", HuntFailure.NONE.name()).forGetter(record -> record.failure().name())
    ).apply(instance, (version, huntId, anchor, createdAt, expiresAt, stage, hunterId, quarryId, cleanupDeadline, failure) ->
        new HuntRecord(version, huntId, BlockPos.of(anchor), createdAt, expiresAt,
            parseStage(stage), hunterId, quarryId, cleanupDeadline, parseFailure(failure))
    ));
    private static final Codec<SilverHuntData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        RECORD_CODEC.listOf().optionalFieldOf("records", List.of()).forGetter(SilverHuntData::records),
        Codec.LONG.optionalFieldOf("last_cleanup_at", 0L).forGetter(SilverHuntData::lastCleanupAt)
    ).apply(instance, SilverHuntData::new));
    public static final SavedDataType<SilverHuntData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "silver_hunts"),
        SilverHuntData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final List<HuntRecord> records;
    private long lastCleanupAt;

    public SilverHuntData() {
        this(List.of(), 0L);
    }

    private SilverHuntData(final List<HuntRecord> records, final long lastCleanupAt) {
        this.records = new ArrayList<>(records.stream()
            .filter(record -> record.version() == SCHEMA_VERSION)
            .limit(WerewolfHunterRules.MAX_HUNT_RECORDS)
            .toList());
        this.lastCleanupAt = Math.max(0L, lastCleanupAt);
    }

    public static SilverHuntData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<HuntRecord> records() {
        return List.copyOf(records);
    }

    public long lastCleanupAt() {
        return lastCleanupAt;
    }

    public Optional<HuntRecord> record(final UUID huntId) {
        return records.stream().filter(record -> record.huntId().equals(huntId)).findFirst();
    }

    public boolean localAreaOccupied(final BlockPos anchor) {
        return records.stream()
            .filter(record -> record.stage() != HuntStage.CLEANUP)
            .anyMatch(record -> WerewolfHunterRules.withinDedupRadius(record.anchor().distSqr(anchor)));
    }

    public Optional<UUID> reserve(final BlockPos anchor, final long now) {
        if (!WerewolfHunterRules.mayReserveHunt(records.size(), localAreaOccupied(anchor))) {
            return Optional.empty();
        }
        final UUID huntId = UUID.randomUUID();
        records.add(new HuntRecord(
            SCHEMA_VERSION, huntId, anchor.immutable(), now,
            WerewolfHunterRules.saturatingAdd(now, WerewolfHunterRules.HUNT_RECORD_TICKS),
            HuntStage.RESERVED, Optional.empty(), Optional.empty(), 0L, HuntFailure.NONE
        ));
        setDirty();
        return Optional.of(huntId);
    }

    public boolean activate(final UUID huntId, final UUID hunterId, final UUID quarryId) {
        for (int index = 0; index < records.size(); index++) {
            final HuntRecord record = records.get(index);
            if (!record.huntId().equals(huntId)) continue;
            if (!WerewolfHunterRules.stageAllowsActivation(record.stage())) return false;
            records.set(index, record.withParticipants(hunterId, quarryId).withStage(HuntStage.ACTIVE));
            setDirty();
            return true;
        }
        return false;
    }

    public boolean discard(final UUID huntId) {
        final boolean removed = records.removeIf(record -> record.huntId().equals(huntId));
        if (removed) setDirty();
        return removed;
    }

    public boolean markCleanup(final UUID huntId, final HuntFailure reason, final long now) {
        for (int index = 0; index < records.size(); index++) {
            final HuntRecord record = records.get(index);
            if (!record.huntId().equals(huntId)) continue;
            if (record.stage() == HuntStage.CLEANUP) return false;
            records.set(index, record.toCleanup(
                reason, WerewolfHunterRules.saturatingAdd(now, WerewolfHunterRules.HUNT_CLEANUP_INTERVAL_TICKS)
            ));
            setDirty();
            return true;
        }
        return false;
    }

    public int reconcile(final long now) {
        if (!WerewolfHunterRules.cleanupDue(lastCleanupAt, now)) return 0;
        lastCleanupAt = Math.max(1L, now);
        int touched = 0;
        for (int index = records.size() - 1; index >= 0; index--) {
            final HuntRecord record = records.get(index);
            if (record.stage() == HuntStage.CLEANUP) {
                if (record.cleanupDeadline() <= now) {
                    records.remove(index);
                    touched++;
                }
            } else if (WerewolfHunterRules.huntRecordExpired(record.expiresAt(), now)) {
                records.set(index, record.toCleanup(
                    HuntFailure.EXPIRED,
                    WerewolfHunterRules.saturatingAdd(now, WerewolfHunterRules.HUNT_CLEANUP_INTERVAL_TICKS)
                ));
                touched++;
            }
        }
        if (touched > 0) setDirty();
        return touched;
    }

    private static HuntStage parseStage(final String value) {
        for (final HuntStage stage : HuntStage.values()) {
            if (stage.name().equalsIgnoreCase(value)) return stage;
        }
        return HuntStage.CLEANUP;
    }

    private static HuntFailure parseFailure(final String value) {
        for (final HuntFailure failure : HuntFailure.values()) {
            if (failure.name().equalsIgnoreCase(value)) return failure;
        }
        return HuntFailure.NONE;
    }
}
