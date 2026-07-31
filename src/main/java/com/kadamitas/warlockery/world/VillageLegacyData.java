package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class VillageLegacyData extends SavedData {
    private static final Codec<VillageLegacyData> CODEC = Codec.LONG.listOf().xmap(VillageLegacyData::new, VillageLegacyData::entries);
    public static final SavedDataType<VillageLegacyData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "village_legacy_sites"),
        VillageLegacyData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Set<Long> enriched;

    public VillageLegacyData() {
        enriched = new HashSet<>();
    }

    private VillageLegacyData(final List<Long> entries) {
        enriched = new HashSet<>(entries);
    }

    public static VillageLegacyData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean contains(final BlockPos bell) {
        return enriched.contains(bell.asLong());
    }

    public void mark(final BlockPos bell) {
        if (enriched.add(bell.asLong())) {
            setDirty();
        }
    }

    private List<Long> entries() {
        return List.copyOf(enriched);
    }
}
