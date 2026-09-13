package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.ConnectedGlyphGeometry.Side;
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

public final class ConnectedGlyphLinks extends SavedData {
    public static final Codec<ConnectedGlyphLinks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Edge.CODEC.listOf().optionalFieldOf("disabled_edges", List.of()).forGetter(links -> List.copyOf(links.disabled))
    ).apply(instance, ConnectedGlyphLinks::new));
    public static final SavedDataType<ConnectedGlyphLinks> TYPE = new SavedDataType<>(
        Identifier.parse("warlockery:chalk_connections"), ConnectedGlyphLinks::new,
        CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );
    private final Set<Edge> disabled;

    public ConnectedGlyphLinks() { this(List.of()); }

    private ConnectedGlyphLinks(final List<Edge> disabled) { this.disabled = new HashSet<>(disabled); }

    public static ConnectedGlyphLinks get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean disabled(final BlockPos pos, final Side side) { return disabled.contains(edge(pos, side)); }

    public boolean toggle(final BlockPos pos, final Side side) {
        final Edge edge = edge(pos, side);
        final boolean disconnected = !disabled.remove(edge);
        if (disconnected) disabled.add(edge);
        setDirty();
        return disconnected;
    }

    public void remove(final BlockPos pos) {
        boolean changed = false;
        for (final Side side : Side.values()) changed |= disabled.remove(edge(pos, side));
        if (changed) setDirty();
    }

    public static Optional<Side> clickedSide(final double x, final double z) {
        final double dx = x - 0.5;
        final double dz = z - 0.5;
        if (!Double.isFinite(x) || !Double.isFinite(z) || x < 0 || x > 1 || z < 0 || z > 1
            || Math.max(Math.abs(dx), Math.abs(dz)) <= 2.0 / 16.0) return Optional.empty();
        Side nearest = null;
        double distance = 1.5 / 16.0;
        for (final Side side : Side.values()) {
            if (dx * side.dx() + dz * side.dz() <= 0) continue;
            final double perpendicular = Math.abs(dx * side.dz() - dz * side.dx())
                / Math.sqrt(side.dx() * side.dx() + side.dz() * side.dz());
            if (perpendicular < distance) {
                nearest = side;
                distance = perpendicular;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static Edge edge(final BlockPos pos, final Side side) {
        return new Edge(pos.asLong(), pos.offset(side.dx(), 0, side.dz()).asLong());
    }

    private record Edge(long first, long second) {
        private static final Codec<Edge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("first").forGetter(Edge::first),
            Codec.LONG.fieldOf("second").forGetter(Edge::second)
        ).apply(instance, Edge::new));

        private Edge {
            if (first > second) {
                final long swap = first;
                first = second;
                second = swap;
            }
        }
    }
}
