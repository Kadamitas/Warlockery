package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class LegacyLandmarkData extends SavedData {
    private static final Codec<LegacyLandmarkData> CODEC = Codec.LONG.listOf().xmap(
        LegacyLandmarkData::new,
        LegacyLandmarkData::entries
    );
    public static final SavedDataType<LegacyLandmarkData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "legacy_landmarks"),
        LegacyLandmarkData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Set<Long> generatedRegions;

    public LegacyLandmarkData() {
        generatedRegions = new HashSet<>();
    }

    private LegacyLandmarkData(final List<Long> regions) {
        generatedRegions = new HashSet<>(regions);
    }

    public static LegacyLandmarkData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean contains(final long region) {
        return generatedRegions.contains(region);
    }

    public void mark(final long region) {
        if (generatedRegions.add(region)) {
            setDirty();
        }
    }

    private List<Long> entries() {
        return List.copyOf(generatedRegions);
    }
}
