package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class LegacySiteIndex extends SavedData {
    private static final Codec<LegacySiteIndex> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.listOf().optionalFieldOf("circle_centers", List.of()).forGetter(LegacySiteIndex::circleEntries),
        Codec.LONG.listOf().optionalFieldOf("camp_regions", List.of()).forGetter(LegacySiteIndex::campEntries),
        Codec.LONG.listOf().optionalFieldOf("village_bells", List.of()).forGetter(LegacySiteIndex::bellEntries),
        Codec.LONG.listOf().optionalFieldOf("village_scans", List.of()).forGetter(LegacySiteIndex::scanEntries)
    ).apply(instance, LegacySiteIndex::new));
    public static final SavedDataType<LegacySiteIndex> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "legacy_site_index"),
        LegacySiteIndex::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Set<Long> circleCenters;
    private final Set<Long> campRegions;
    private final Set<Long> villageBells;
    private final Set<Long> villageScans;

    public LegacySiteIndex() {
        this(List.of(), List.of(), List.of(), List.of());
    }

    private LegacySiteIndex(
        final List<Long> circleCenters,
        final List<Long> campRegions,
        final List<Long> villageBells,
        final List<Long> villageScans
    ) {
        this.circleCenters = new HashSet<>(circleCenters);
        this.campRegions = new HashSet<>(campRegions);
        this.villageBells = new HashSet<>(villageBells);
        this.villageScans = new HashSet<>(villageScans);
    }

    public static LegacySiteIndex get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void registerCircle(final BlockPos center) {
        mark(circleCenters, center.asLong());
    }

    public List<BlockPos> nearbyCircles(final BlockPos origin, final double radius) {
        return circleCenters.stream()
            .map(BlockPos::of)
            .filter(center -> center.closerThan(origin, radius))
            .toList();
    }

    public boolean containsCamp(final long region) {
        return campRegions.contains(region);
    }

    public void registerCamp(final long region) {
        mark(campRegions, region);
    }

    public Optional<BlockPos> nearestBell(final BlockPos origin, final double radius) {
        return villageBells.stream()
            .map(BlockPos::of)
            .filter(bell -> bell.closerThan(origin, radius))
            .min(java.util.Comparator.comparingDouble(bell -> bell.distSqr(origin)));
    }

    public void registerBell(final BlockPos bell) {
        mark(villageBells, bell.asLong());
    }

    public boolean scannedVillageRegion(final long region) {
        return villageScans.contains(region);
    }

    public void markVillageRegionScanned(final long region) {
        mark(villageScans, region);
    }

    private void mark(final Set<Long> entries, final long value) {
        if (entries.add(value)) {
            setDirty();
        }
    }

    private List<Long> circleEntries() {
        return List.copyOf(circleCenters);
    }

    private List<Long> campEntries() {
        return List.copyOf(campRegions);
    }

    private List<Long> bellEntries() {
        return List.copyOf(villageBells);
    }

    private List<Long> scanEntries() {
        return List.copyOf(villageScans);
    }
}
