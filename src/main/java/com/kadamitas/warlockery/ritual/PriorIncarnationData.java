package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class PriorIncarnationData extends SavedData {
    private static final Codec<PriorIncarnationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        DeathRecord.CODEC.listOf().optionalFieldOf("deaths", List.of()).forGetter(data -> data.deaths)
    ).apply(instance, PriorIncarnationData::new));

    public static final SavedDataType<PriorIncarnationData> TYPE = new SavedDataType<>(
        Identifier.parse(Warlockery.MOD_ID + ":prior_incarnations"),
        PriorIncarnationData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final List<DeathRecord> deaths;

    public PriorIncarnationData() {
        deaths = new ArrayList<>();
    }

    private PriorIncarnationData(final List<DeathRecord> deaths) {
        this.deaths = new ArrayList<>(deaths);
    }

    public static PriorIncarnationData get(final ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void record(
        final UUID player,
        final Identifier dimension,
        final BlockPos position,
        final long deathTime
    ) {
        deaths.removeIf(record -> record.player().equals(player.toString()));
        deaths.add(new DeathRecord(player.toString(), dimension.toString(), position.asLong(), deathTime));
        setDirty();
    }

    public Optional<DeathRecord> find(final UUID player) {
        return deaths.stream().filter(record -> record.player().equals(player.toString())).findFirst();
    }

    public void clear(final UUID player) {
        if (deaths.removeIf(record -> record.player().equals(player.toString()))) {
            setDirty();
        }
    }

    public record DeathRecord(String player, String dimension, long position, long deathTime) {
        private static final Codec<DeathRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("player").forGetter(DeathRecord::player),
            Codec.STRING.fieldOf("dimension").forGetter(DeathRecord::dimension),
            Codec.LONG.fieldOf("position").forGetter(DeathRecord::position),
            Codec.LONG.fieldOf("death_time").forGetter(DeathRecord::deathTime)
        ).apply(instance, DeathRecord::new));
    }
}
