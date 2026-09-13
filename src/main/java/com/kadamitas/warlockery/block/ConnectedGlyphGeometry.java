package com.kadamitas.warlockery.block;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

public final class ConnectedGlyphGeometry {
    public static final Set<String> IDS = Set.of(
        "circle",
        "circleglyphgolden",
        "circleglyphritual",
        "circleglyphinfernal",
        "circleglyph_veil"
    );
    public static final Bounds CENTER = new Bounds(6.0, 0.0, 6.0, 10.0, 0.25, 10.0);
    public static final double DIAGONAL_SURFACE = 0.15625;
    public static final Map<Side, Bounds> ARMS = Map.of(
        Side.NORTH, new Bounds(7.0, 0.0, 0.0, 9.0, 0.125, 6.0),
        Side.EAST, new Bounds(10.0, 0.0, 7.0, 16.0, 0.125, 9.0),
        Side.SOUTH, new Bounds(7.0, 0.0, 10.0, 9.0, 0.125, 16.0),
        Side.WEST, new Bounds(0.0, 0.0, 7.0, 6.0, 0.125, 9.0)
    );

    public static final Map<Side, List<Bounds>> DIAGONALS = Map.of(
        Side.NORTH_EAST, diagonal(Side.NORTH_EAST),
        Side.SOUTH_EAST, diagonal(Side.SOUTH_EAST),
        Side.SOUTH_WEST, diagonal(Side.SOUTH_WEST),
        Side.NORTH_WEST, diagonal(Side.NORTH_WEST)
    );

    private ConnectedGlyphGeometry() {
    }

    public static Bounds bounds(final Set<Side> connections) {
        return connections.stream().flatMap(side -> parts(side).stream()).reduce(CENTER, Bounds::union);
    }

    public static List<Bounds> parts(final Side side) {
        return side.diagonal() ? DIAGONALS.get(side) : List.of(ARMS.get(side));
    }

    public static double surface(final Side side) {
        return !side.diagonal() ? 0.125
            : side.dx() == side.dz() ? DIAGONAL_SURFACE : DIAGONAL_SURFACE + 0.03125;
    }

    private static List<Bounds> diagonal(final Side side) {
        return IntStream.range(0, 8).mapToObj(step -> {
            final double x0 = step;
            final double x1 = x0 + 1.0;
            final double z0 = Math.max(0.0, x0 - 1.0);
            final double z1 = Math.min(8.0, x0 + 2.0);
            return new Bounds(
                side.dx() < 0 ? x0 : 16.0 - x1, 0.0,
                side.dz() < 0 ? z0 : 16.0 - z1,
                side.dx() < 0 ? x1 : 16.0 - x0, 0.125,
                side.dz() < 0 ? z1 : 16.0 - z0
            );
        }).toList();
    }

    public enum Side {
        NORTH(0, -1), NORTH_EAST(1, -1), EAST(1, 0), SOUTH_EAST(1, 1),
        SOUTH(0, 1), SOUTH_WEST(-1, 1), WEST(-1, 0), NORTH_WEST(-1, -1);

        private final int dx;
        private final int dz;

        Side(final int dx, final int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public int dx() { return dx; }
        public int dz() { return dz; }
        public boolean diagonal() { return dx != 0 && dz != 0; }
        public String id() { return name().toLowerCase(Locale.ROOT); }
        public Side rotateQuarterTurns(final int turns) { return values()[Math.floorMod(ordinal() + turns * 2, 8)]; }
        public Side mirrorX() { return values()[Math.floorMod(-ordinal(), 8)]; }
        public Side mirrorZ() { return values()[Math.floorMod(4 - ordinal(), 8)]; }
    }

    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Bounds union(final Bounds other) {
            return new Bounds(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY),
                Math.max(maxZ, other.maxZ)
            );
        }
    }
}
