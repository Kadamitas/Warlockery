package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class GoblinRaidData extends SavedData {
    private static final Codec<RaidState> RAID_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("center").forGetter(state -> state.center().asLong()),
        Codec.INT.fieldOf("wave").forGetter(RaidState::wave),
        Codec.LONG.fieldOf("next_wave").forGetter(RaidState::nextWaveTime),
        Codec.LONG.fieldOf("expires").forGetter(RaidState::expiresAt),
        Codec.BOOL.fieldOf("awaiting_clear").forGetter(RaidState::awaitingClear)
    ).apply(instance, (center, wave, nextWave, expires, awaitingClear) ->
        new RaidState(BlockPos.of(center), wave, nextWave, expires, awaitingClear)
    ));
    private static final Codec<GoblinRaidData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.optionalFieldOf("next_attempt", 0L).forGetter(GoblinRaidData::nextAttempt),
        RAID_CODEC.optionalFieldOf("active").forGetter(GoblinRaidData::active)
    ).apply(instance, GoblinRaidData::new));
    public static final SavedDataType<GoblinRaidData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "goblin_raids"),
        GoblinRaidData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private long nextAttempt;
    private Optional<RaidState> active;

    public GoblinRaidData() {
        this(0L, Optional.empty());
    }

    private GoblinRaidData(final long nextAttempt, final Optional<RaidState> active) {
        this.nextAttempt = Math.max(0L, nextAttempt);
        this.active = active;
    }

    public static GoblinRaidData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public long nextAttempt() {
        return nextAttempt;
    }

    public Optional<RaidState> active() {
        return active;
    }

    public void scheduleNext(final long gameTime, final long roll) {
        nextAttempt = gameTime + GoblinRaidRules.nextDelay(roll);
        setDirty();
    }

    public boolean begin(final BlockPos center, final long gameTime) {
        if (active.isPresent()) {
            return false;
        }
        active = Optional.of(new RaidState(
            center.immutable(),
            0,
            gameTime,
            gameTime + GoblinRaidRules.RAID_DURATION_TICKS,
            false
        ));
        setDirty();
        return true;
    }

    public void update(final RaidState state) {
        active = Optional.of(state);
        setDirty();
    }

    public void finish(final long gameTime, final long roll) {
        active = Optional.empty();
        scheduleNext(gameTime, roll);
    }

    public record RaidState(
        BlockPos center,
        int wave,
        long nextWaveTime,
        long expiresAt,
        boolean awaitingClear
    ) {
        public RaidState {
            center = center.immutable();
            if (wave < 0 || wave > GoblinRaidRules.WAVE_COUNT || nextWaveTime < 0L || expiresAt < 0L) {
                throw new IllegalArgumentException("Invalid goblin raid state");
            }
        }

        public RaidState waveSpawned(final int nextWave) {
            return new RaidState(center, nextWave, nextWaveTime, expiresAt, true);
        }

        public RaidState waveCleared(final long gameTime) {
            return new RaidState(
                center,
                wave,
                gameTime + GoblinRaidRules.INTERMISSION_TICKS,
                expiresAt,
                false
            );
        }

        public RaidState retryAt(final long gameTime) {
            return new RaidState(
                center,
                wave,
                gameTime + GoblinRaidRules.INTERMISSION_TICKS,
                expiresAt,
                false
            );
        }
    }
}
