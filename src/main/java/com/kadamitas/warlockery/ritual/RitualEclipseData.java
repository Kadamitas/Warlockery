package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class RitualEclipseData extends SavedData {
    private static final Codec<RitualEclipseData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("active", false).forGetter(data -> data.active),
        Codec.LONG.optionalFieldOf("previous_ticks", 0L).forGetter(data -> data.previousTicks),
        Codec.LONG.optionalFieldOf("night_ticks", 0L).forGetter(data -> data.nightTicks),
        Codec.LONG.optionalFieldOf("started", 0L).forGetter(data -> data.started),
        Codec.LONG.optionalFieldOf("expiration", 0L).forGetter(data -> data.expiration)
    ).apply(instance, RitualEclipseData::new));
    public static final SavedDataType<RitualEclipseData> TYPE = new SavedDataType<>(
        Identifier.parse(Warlockery.MOD_ID + ":ritual_eclipse"),
        RitualEclipseData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private boolean active;
    private long previousTicks;
    private long nightTicks;
    private long started;
    private long expiration;

    public RitualEclipseData() {
    }

    private RitualEclipseData(
        final boolean active,
        final long previousTicks,
        final long nightTicks,
        final long started,
        final long expiration
    ) {
        this.active = active;
        this.previousTicks = previousTicks;
        this.nightTicks = nightTicks;
        this.started = started;
        this.expiration = expiration;
    }

    public static RitualEclipseData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void begin(final ServerLevel level, final int duration) {
        level.dimensionType().defaultClock().ifPresent(clock -> {
            final long now = level.getGameTime();
            if (!active) {
                previousTicks = level.clockManager().getTotalTicks(clock);
                started = now;
                level.clockManager().moveToTimeMarker(clock, ClockTimeMarkers.NIGHT);
                nightTicks = level.clockManager().getTotalTicks(clock);
                active = true;
            }
            expiration = extendedExpiration(expiration, now, duration);
            setDirty();
        });
    }

    public void tick(final ServerLevel level) {
        if (!active) {
            return;
        }
        level.dimensionType().defaultClock().ifPresentOrElse(clock -> {
            final long now = level.getGameTime();
            if (shouldExpire(now, expiration)) {
                final boolean advances = level.getGameRules().get(GameRules.ADVANCE_TIME);
                level.clockManager().setTotalTicks(clock, restoreTicks(previousTicks, started, now, advances));
                active = false;
                setDirty();
            } else if (now % 20L == 0L) {
                level.clockManager().setTotalTicks(clock, nightTicks);
            }
        }, () -> {
            active = false;
            setDirty();
        });
    }

    static long extendedExpiration(final long current, final long now, final int duration) {
        return Math.max(current, now + Math.max(1, duration));
    }

    static boolean shouldExpire(final long now, final long expiration) {
        return now >= expiration;
    }

    static long restoreTicks(
        final long previous,
        final long started,
        final long now,
        final boolean advances
    ) {
        return previous + (advances ? Math.max(0L, now - started) : 0L);
    }
}
