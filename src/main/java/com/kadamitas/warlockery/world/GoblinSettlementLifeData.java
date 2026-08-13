package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.GoblinSettlementLifeRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class GoblinSettlementLifeData extends SavedData {
    private static final Codec<SettlementState> STATE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("key").forGetter(SettlementState::key),
        Codec.LONG.listOf().optionalFieldOf("huts", List.of()).forGetter(SettlementState::huts),
        Codec.LONG.listOf().optionalFieldOf("tunnels", List.of()).forGetter(SettlementState::tunnels),
        Codec.INT.optionalFieldOf("world_edits", 0).forGetter(SettlementState::worldEdits)
    ).apply(instance, SettlementState::new));
    private static final Codec<GoblinSettlementLifeData> CODEC = STATE_CODEC.listOf()
        .optionalFieldOf("settlements", List.of())
        .xmap(GoblinSettlementLifeData::new, GoblinSettlementLifeData::entries)
        .codec();
    public static final SavedDataType<GoblinSettlementLifeData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "goblin_settlement_life"),
        GoblinSettlementLifeData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<Long, SettlementState> settlements;

    public GoblinSettlementLifeData() {
        this(List.of());
    }

    private GoblinSettlementLifeData(final List<SettlementState> entries) {
        settlements = new HashMap<>();
        entries.forEach(state -> settlements.put(state.key(), state.normalized()));
    }

    public static GoblinSettlementLifeData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public SettlementState state(final long key) {
        return settlements.getOrDefault(key, SettlementState.empty(key));
    }

    public boolean reserveHut(final long key, final BlockPos center) {
        if (!canReserveHut(key, center)) {
            return false;
        }
        final SettlementState state = state(key);
        put(state.withHut(center));
        return true;
    }

    public boolean canReserveHut(final long key, final BlockPos center) {
        final SettlementState state = state(key);
        return GoblinSettlementLifeRules.canReserveHut(state.huts().size(), state.worldEdits())
            && !state.hasHutNear(center, 8.0);
    }

    public boolean reserveTunnel(final long key, final BlockPos entrance, final int edits) {
        if (!canReserveTunnel(key, entrance, edits)) {
            return false;
        }
        final SettlementState state = state(key);
        put(state.withTunnel(entrance, edits));
        return true;
    }

    public boolean canReserveTunnel(final long key, final BlockPos entrance, final int edits) {
        final SettlementState state = state(key);
        return GoblinSettlementLifeRules.canReserveTunnel(state.tunnels().size(), state.worldEdits(), edits)
            && !state.hasTunnelNear(entrance, 48.0);
    }

    public boolean recordNaturalBlockGathered(final long key) {
        final SettlementState state = state(key);
        if (!GoblinSettlementLifeRules.canGatherNaturalBlock(state.worldEdits())) {
            return false;
        }
        put(state.withWorldEdits(1));
        return true;
    }

    void clearForGameTest(final long key) {
        if (settlements.remove(key) != null) {
            setDirty();
        }
    }

    private void put(final SettlementState state) {
        settlements.put(state.key(), state);
        setDirty();
    }

    private List<SettlementState> entries() {
        return List.copyOf(settlements.values());
    }

    public record SettlementState(long key, List<Long> huts, List<Long> tunnels, int worldEdits) {
        public SettlementState {
            huts = List.copyOf(huts);
            tunnels = List.copyOf(tunnels);
            if (worldEdits < 0) {
                throw new IllegalArgumentException("World edits cannot be negative");
            }
        }

        static SettlementState empty(final long key) {
            return new SettlementState(key, List.of(), List.of(), 0);
        }

        SettlementState normalized() {
            return new SettlementState(
                key,
                huts.stream().distinct().limit(GoblinSettlementLifeRules.HUT_CAP).toList(),
                tunnels.stream().distinct().limit(GoblinSettlementLifeRules.TUNNEL_CAP).toList(),
                Math.min(worldEdits, GoblinSettlementLifeRules.WORLD_EDIT_CAP)
            );
        }

        SettlementState withHut(final BlockPos center) {
            return new SettlementState(
                key,
                java.util.stream.Stream.concat(huts.stream(), java.util.stream.Stream.of(center.asLong())).toList(),
                tunnels,
                worldEdits + GoblinSettlementLifeRules.HUT_EDIT_COST
            );
        }

        SettlementState withTunnel(final BlockPos entrance, final int edits) {
            return new SettlementState(
                key,
                huts,
                java.util.stream.Stream.concat(tunnels.stream(), java.util.stream.Stream.of(entrance.asLong())).toList(),
                worldEdits + edits
            );
        }

        SettlementState withWorldEdits(final int edits) {
            return new SettlementState(key, huts, tunnels, worldEdits + edits);
        }

        boolean hasHutNear(final BlockPos position, final double radius) {
            return huts.stream()
                .map(BlockPos::of)
                .anyMatch(site -> site.closerThan(position, radius));
        }

        boolean hasTunnelNear(final BlockPos position, final double radius) {
            return tunnels.stream()
                .map(BlockPos::of)
                .anyMatch(site -> site.closerThan(position, radius));
        }
    }
}
