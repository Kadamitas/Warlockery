package com.kadamitas.warlockery.block;

import java.util.Map;
import java.util.Set;

public final class ConnectedGlyphGeometry {
    public static final Set<String> IDS = Set.of(
        "circle",
        "circleglyphritual",
        "circleglyphinfernal",
        "circleglyph_veil"
    );
    public static final Bounds CENTER = new Bounds(5.0, 0.0, 5.0, 11.0, 1.0, 11.0);
    public static final Map<Side, Bounds> ARMS = Map.of(
        Side.NORTH, new Bounds(6.0, 0.0, 0.0, 10.0, 1.0, 8.0),
        Side.EAST, new Bounds(8.0, 0.0, 6.0, 16.0, 1.0, 10.0),
        Side.SOUTH, new Bounds(6.0, 0.0, 8.0, 10.0, 1.0, 16.0),
        Side.WEST, new Bounds(0.0, 0.0, 6.0, 8.0, 1.0, 10.0)
    );

    private ConnectedGlyphGeometry() {
    }

    public static Bounds bounds(final Set<Side> connections) {
        return connections.stream().map(ARMS::get).reduce(CENTER, Bounds::union);
    }

    public enum Side {
        NORTH,
        EAST,
        SOUTH,
        WEST
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
