package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class StatueWardData extends SavedData {
    public static final int WARD_RADIUS = 64;
    private static final Codec<StatueWardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Ward.CODEC.listOf().optionalFieldOf("wards", List.of()).forGetter(data -> List.copyOf(data.wards.values()))
    ).apply(instance, StatueWardData::new));
    public static final SavedDataType<StatueWardData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "statue_wards"),
        StatueWardData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<Long, Ward> wards;

    public StatueWardData() {
        wards = new HashMap<>();
    }

    private StatueWardData(final List<Ward> entries) {
        wards = new HashMap<>();
        entries.forEach(entry -> wards.put(entry.position(), entry));
    }

    public static StatueWardData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void register(
        final BlockPos pos,
        final WardKind kind,
        final Optional<UUID> owner,
        final boolean active
    ) {
        wards.put(pos.asLong(), new Ward(pos.asLong(), kind, owner.map(UUID::toString).orElse(""), active));
        setDirty();
    }

    public boolean setActive(final BlockPos pos, final ServerPlayer player, final boolean active) {
        final Ward ward = wards.get(pos.asLong());
        if (ward == null || !ward.permits(player)) {
            return false;
        }
        wards.put(pos.asLong(), new Ward(ward.position(), ward.kind(), ward.owner(), active));
        setDirty();
        return true;
    }

    public boolean permits(final BlockPos pos, final ServerPlayer player) {
        final Ward ward = wards.get(pos.asLong());
        return ward == null || ward.permits(player);
    }

    public boolean protectsHex(final BlockPos target) {
        return withinActiveWard(target, WardKind.HEXES);
    }

    public boolean occludesSummoning(final BlockPos target) {
        return withinActiveWard(target, WardKind.SUMMONING);
    }

    public void remove(final BlockPos pos) {
        if (wards.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public void tick(final ServerLevel level) {
        if (level.getGameTime() % 200L != 0L) {
            return;
        }
        final boolean changed = wards.entrySet().removeIf(entry -> {
            final BlockPos pos = BlockPos.of(entry.getKey());
            if (!level.isLoaded(pos)) {
                return false;
            }
            if (!(level.getBlockState(pos).getBlock() instanceof StatueBlock statue)) {
                return true;
            }
            return entry.getValue().kind() != WardKind.forProfile(statue.profile()).orElse(null);
        });
        if (changed) {
            setDirty();
        }
    }

    private boolean withinActiveWard(final BlockPos target, final WardKind kind) {
        final long radiusSquared = (long) WARD_RADIUS * WARD_RADIUS;
        return wards.values().stream()
            .filter(Ward::active)
            .filter(ward -> ward.kind() == kind)
            .map(ward -> BlockPos.of(ward.position()))
            .anyMatch(origin -> origin.distSqr(target) <= radiusSquared);
    }

    public enum WardKind {
        HEXES,
        SUMMONING;

        public static Optional<WardKind> forProfile(final StatueProfile profile) {
            return switch (profile.id()) {
                case "broken_hexes_statue" -> Optional.of(HEXES);
                case "occluded_summons_statue" -> Optional.of(SUMMONING);
                default -> Optional.empty();
            };
        }
    }

    private record Ward(long position, WardKind kind, String owner, boolean active) {
        private static final Codec<Ward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("position").forGetter(Ward::position),
            Codec.STRING.xmap(WardKind::valueOf, WardKind::name).fieldOf("kind").forGetter(Ward::kind),
            Codec.STRING.optionalFieldOf("owner", "").forGetter(Ward::owner),
            Codec.BOOL.optionalFieldOf("active", false).forGetter(Ward::active)
        ).apply(instance, Ward::new));

        private boolean permits(final ServerPlayer player) {
            return player.hasInfiniteMaterials() || owner.equals(player.getUUID().toString());
        }
    }
}
