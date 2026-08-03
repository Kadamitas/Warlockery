package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.world.VillageAssaultRules.AssaultKind;
import com.kadamitas.warlockery.world.VillageAssaultRules.SettlementKind;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class VillageAssaultData extends SavedData {
    private static final Codec<AssaultKind> KIND_CODEC = Codec.STRING.xmap(
        AssaultKind::fromSerializedName,
        AssaultKind::serializedName
    );
    private static final Codec<SettlementKind> SETTLEMENT_CODEC = Codec.STRING.xmap(
        SettlementKind::fromSerializedName,
        SettlementKind::serializedName
    );
    private static final Codec<AssaultState> ASSAULT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("center").forGetter(state -> state.center().asLong()),
        KIND_CODEC.fieldOf("kind").forGetter(AssaultState::kind),
        SETTLEMENT_CODEC.fieldOf("settlement").forGetter(AssaultState::settlement),
        Codec.INT.fieldOf("wave").forGetter(AssaultState::wave),
        Codec.LONG.fieldOf("next_wave").forGetter(AssaultState::nextWaveTime),
        Codec.LONG.fieldOf("expires").forGetter(AssaultState::expiresAt),
        Codec.BOOL.fieldOf("awaiting_clear").forGetter(AssaultState::awaitingClear),
        Codec.STRING.listOf().optionalFieldOf("participants", List.of()).forGetter(AssaultState::participants),
        Codec.INT.optionalFieldOf("objective_progress", 0).forGetter(AssaultState::objectiveProgress),
        Codec.INT.optionalFieldOf("objective_quota", 0).forGetter(AssaultState::objectiveQuota),
        Codec.STRING.listOf().optionalFieldOf("objective_victims", List.of()).forGetter(AssaultState::objectiveVictims),
        Codec.BOOL.optionalFieldOf("raiders_retreating", false).forGetter(AssaultState::raidersRetreating),
        Codec.STRING.listOf().optionalFieldOf("raider_ids", List.of()).forGetter(AssaultState::raiderIds)
    ).apply(instance, (center, kind, settlement, wave, nextWave, expires, awaitingClear, participants,
        objectiveProgress, objectiveQuota, objectiveVictims, raidersRetreating, raiderIds) ->
        new AssaultState(
            BlockPos.of(center), kind, settlement, wave, nextWave, expires, awaitingClear, participants,
            objectiveProgress, objectiveQuota, objectiveVictims, raidersRetreating, raiderIds
        )
    ));
    private static final Codec<VillageAssaultData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.optionalFieldOf("next_attempt", 0L).forGetter(VillageAssaultData::nextAttempt),
        ASSAULT_CODEC.optionalFieldOf("active").forGetter(VillageAssaultData::active)
    ).apply(instance, VillageAssaultData::new));
    public static final SavedDataType<VillageAssaultData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "village_assaults"),
        VillageAssaultData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private long nextAttempt;
    private Optional<AssaultState> active;

    public VillageAssaultData() {
        this(0L, Optional.empty());
    }

    private VillageAssaultData(final long nextAttempt, final Optional<AssaultState> active) {
        this.nextAttempt = Math.max(0L, nextAttempt);
        this.active = active;
    }

    public static VillageAssaultData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public long nextAttempt() {
        return nextAttempt;
    }

    public Optional<AssaultState> active() {
        return active;
    }

    public void scheduleNext(final long gameTime, final long roll, final double frequencyMultiplier) {
        nextAttempt = gameTime + VillageAssaultRules.nextDelay(roll, frequencyMultiplier);
        setDirty();
    }

    public boolean begin(
        final BlockPos center,
        final AssaultKind kind,
        final SettlementKind settlement,
        final long gameTime
    ) {
        if (active.isPresent()) {
            return false;
        }
        active = Optional.of(new AssaultState(
            center.immutable(),
            kind,
            settlement,
            0,
            gameTime,
            gameTime + VillageAssaultRules.ASSAULT_DURATION_TICKS,
            false,
            List.of(),
            0,
            VillageAssaultRules.objectiveQuota(kind),
            List.of(),
            false,
            List.of()
        ));
        setDirty();
        return true;
    }

    public void update(final AssaultState state) {
        active = Optional.of(state);
        setDirty();
    }

    public void finish(final long gameTime, final long roll, final double frequencyMultiplier) {
        active = Optional.empty();
        scheduleNext(gameTime, roll, frequencyMultiplier);
    }

    public record AssaultState(
        BlockPos center,
        AssaultKind kind,
        SettlementKind settlement,
        int wave,
        long nextWaveTime,
        long expiresAt,
        boolean awaitingClear,
        List<String> participants,
        int objectiveProgress,
        int objectiveQuota,
        List<String> objectiveVictims,
        boolean raidersRetreating,
        List<String> raiderIds
    ) {
        public AssaultState(
            final BlockPos center,
            final AssaultKind kind,
            final SettlementKind settlement,
            final int wave,
            final long nextWaveTime,
            final long expiresAt,
            final boolean awaitingClear,
            final List<String> participants
        ) {
            this(
                center,
                kind,
                settlement,
                wave,
                nextWaveTime,
                expiresAt,
                awaitingClear,
                participants,
                0,
                VillageAssaultRules.objectiveQuota(kind),
                List.of(),
                false,
                List.of()
            );
        }

        public AssaultState(
            final BlockPos center,
            final AssaultKind kind,
            final SettlementKind settlement,
            final int wave,
            final long nextWaveTime,
            final long expiresAt,
            final boolean awaitingClear,
            final List<String> participants,
            final int objectiveProgress,
            final int objectiveQuota,
            final List<String> objectiveVictims,
            final boolean raidersRetreating
        ) {
            this(
                center, kind, settlement, wave, nextWaveTime, expiresAt, awaitingClear, participants,
                objectiveProgress, objectiveQuota, objectiveVictims, raidersRetreating, List.of()
            );
        }

        public AssaultState {
            center = center.immutable();
            if (wave < 0 || wave > VillageAssaultRules.WAVE_COUNT
                || nextWaveTime < 0L || expiresAt < 0L
                || objectiveProgress < 0 || objectiveQuota < 0 || objectiveProgress > objectiveQuota) {
                throw new IllegalArgumentException("Invalid village assault state");
            }
            participants = List.copyOf(new LinkedHashSet<>(participants));
            objectiveVictims = List.copyOf(new LinkedHashSet<>(objectiveVictims));
            raiderIds = List.copyOf(new LinkedHashSet<>(raiderIds));
            if (objectiveProgress != objectiveVictims.size()) {
                objectiveProgress = Math.min(objectiveQuota, objectiveVictims.size());
            }
        }

        public AssaultState waveSpawned(final int nextWave) {
            return new AssaultState(
                center, kind, settlement, nextWave, nextWaveTime, expiresAt, true, participants,
                objectiveProgress, objectiveQuota, objectiveVictims, raidersRetreating, raiderIds
            );
        }

        public AssaultState waveCleared(final long gameTime) {
            return new AssaultState(
                center,
                kind,
                settlement,
                wave,
                gameTime + VillageAssaultRules.INTERMISSION_TICKS,
                expiresAt,
                false,
                participants,
                objectiveProgress,
                objectiveQuota,
                objectiveVictims,
                raidersRetreating,
                raiderIds
            );
        }

        public AssaultState retryAt(final long gameTime) {
            return new AssaultState(
                center,
                kind,
                settlement,
                wave,
                gameTime + VillageAssaultRules.INTERMISSION_TICKS,
                expiresAt,
                false,
                participants,
                objectiveProgress,
                objectiveQuota,
                objectiveVictims,
                raidersRetreating,
                raiderIds
            );
        }

        public AssaultState addParticipants(final Set<String> additions) {
            final LinkedHashSet<String> combined = new LinkedHashSet<>(participants);
            if (!combined.addAll(additions)) {
                return this;
            }
            return new AssaultState(
                center, kind, settlement, wave, nextWaveTime, expiresAt, awaitingClear, List.copyOf(combined),
                objectiveProgress, objectiveQuota, objectiveVictims, raidersRetreating, raiderIds
            );
        }

        public AssaultState recordObjectiveVictim(final String victimId) {
            if (raidersRetreating || objectiveQuota == 0 || objectiveVictims.contains(victimId)) {
                return this;
            }
            final LinkedHashSet<String> victims = new LinkedHashSet<>(objectiveVictims);
            victims.add(victimId);
            final int progress = Math.min(objectiveQuota, victims.size());
            return new AssaultState(
                center, kind, settlement, wave, nextWaveTime, expiresAt, awaitingClear, participants,
                progress, objectiveQuota, List.copyOf(victims), false, raiderIds
            );
        }

        public boolean objectiveSatisfied() {
            return VillageAssaultRules.objectiveSatisfied(kind, objectiveProgress, objectiveQuota);
        }

        public AssaultState beginRaiderRetreat(final long gameTime) {
            if (!objectiveSatisfied()) {
                throw new IllegalStateException("Raiders cannot retreat before satisfying their objective");
            }
            return new AssaultState(
                center,
                kind,
                settlement,
                wave,
                gameTime + VillageAssaultRules.ESCAPE_LIFETIME_TICKS,
                expiresAt,
                true,
                participants,
                objectiveProgress,
                objectiveQuota,
                objectiveVictims,
                true,
                raiderIds
            );
        }

        public AssaultState addRaiders(final Set<String> additions) {
            final LinkedHashSet<String> combined = new LinkedHashSet<>(raiderIds);
            if (!combined.addAll(additions)) {
                return this;
            }
            return new AssaultState(
                center, kind, settlement, wave, nextWaveTime, expiresAt, awaitingClear, participants,
                objectiveProgress, objectiveQuota, objectiveVictims, raidersRetreating, List.copyOf(combined)
            );
        }

        public AssaultState removeRaider(final String raiderId) {
            if (!raiderIds.contains(raiderId)) {
                return this;
            }
            final LinkedHashSet<String> remaining = new LinkedHashSet<>(raiderIds);
            remaining.remove(raiderId);
            return new AssaultState(
                center, kind, settlement, wave, nextWaveTime, expiresAt, awaitingClear, participants,
                objectiveProgress, objectiveQuota, objectiveVictims, raidersRetreating, List.copyOf(remaining)
            );
        }

        public AssaultState replaceRaider(final String previousId, final String replacementId) {
            if (!raiderIds.contains(previousId)) {
                return addRaiders(Set.of(replacementId));
            }
            final LinkedHashSet<String> updated = new LinkedHashSet<>(raiderIds);
            updated.remove(previousId);
            updated.add(replacementId);
            return new AssaultState(
                center, kind, settlement, wave, nextWaveTime, expiresAt, awaitingClear, participants,
                objectiveProgress, objectiveQuota, objectiveVictims, raidersRetreating, List.copyOf(updated)
            );
        }
    }
}
