package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

final class AmbientActivityHearthData extends SavedData {
    private static final Codec<Hearth> HEARTH_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("position").forGetter(Hearth::position),
        Codec.STRING.fieldOf("owner").forGetter(Hearth::owner),
        Codec.INT.fieldOf("state").forGetter(Hearth::state)
    ).apply(instance, Hearth::new));
    private static final Codec<AmbientActivityHearthData> CODEC = HEARTH_CODEC.listOf()
        .optionalFieldOf("hearths", List.of())
        .xmap(AmbientActivityHearthData::new, AmbientActivityHearthData::entries)
        .codec();
    static final SavedDataType<AmbientActivityHearthData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "ambient_hearths"),
        AmbientActivityHearthData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<Long, Hearth> hearths;

    AmbientActivityHearthData() {
        this(List.of());
    }

    private AmbientActivityHearthData(final List<Hearth> entries) {
        hearths = new HashMap<>();
        entries.forEach(hearth -> hearths.put(hearth.position(), hearth));
    }

    static AmbientActivityHearthData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    void claim(final BlockPos position, final UUID owner, final BlockState state) {
        hearths.put(position.asLong(), new Hearth(position.asLong(), owner.toString(), Block.getId(state)));
        setDirty();
    }

    boolean owns(final BlockPos position, final UUID owner, final BlockState state) {
        final Hearth hearth = hearths.get(position.asLong());
        return hearth != null
            && hearth.owner().equals(owner.toString())
            && hearth.state() == Block.getId(state);
    }

    void release(final BlockPos position, final UUID owner) {
        final Hearth hearth = hearths.get(position.asLong());
        if (hearth != null && hearth.owner().equals(owner.toString())) {
            hearths.remove(position.asLong());
            setDirty();
        }
    }

    private List<Hearth> entries() {
        return List.copyOf(hearths.values());
    }

    private record Hearth(long position, String owner, int state) {
    }
}
