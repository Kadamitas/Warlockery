package com.kadamitas.warlockery.item;

import java.util.List;
import net.minecraft.core.BlockPos;

public final class SpiritLocatorRules {
    public static final int ATTUNEMENT_TICKS = 60;
    public static final int SEARCH_RADIUS_CHUNKS = 128;
    public static final int SEARCH_RADIUS_BLOCKS = SEARCH_RADIUS_CHUNKS * 16;
    public static final double MESSAGE_RANGE = 32.0D;

    private static final List<BlockPos> RING_OFFSETS = List.of(
        new BlockPos(-1, 0, -1),
        new BlockPos(0, 0, -1),
        new BlockPos(1, 0, -1),
        new BlockPos(-1, 0, 0),
        new BlockPos(1, 0, 0),
        new BlockPos(-1, 0, 1),
        new BlockPos(0, 0, 1),
        new BlockPos(1, 0, 1)
    );

    private static final List<String> DIRECTION_KEYS = List.of(
        "east",
        "southeast",
        "south",
        "southwest",
        "west",
        "northwest",
        "north",
        "northeast"
    );

    private SpiritLocatorRules() {
    }

    public static List<BlockPos> ringOffsets() {
        return RING_OFFSETS;
    }

    public static String directionKey(final double deltaX, final double deltaZ) {
        if (Math.abs(deltaX) < 0.5D && Math.abs(deltaZ) < 0.5D) {
            return "here";
        }
        final double eighthTurns = Math.atan2(deltaZ, deltaX) / (Math.PI / 4.0D);
        return DIRECTION_KEYS.get(Math.floorMod((int) Math.round(eighthTurns), DIRECTION_KEYS.size()));
    }

    public static int horizontalDistance(final BlockPos origin, final BlockPos target) {
        final long deltaX = (long) target.getX() - origin.getX();
        final long deltaZ = (long) target.getZ() - origin.getZ();
        return (int) Math.round(Math.hypot(deltaX, deltaZ));
    }
}
