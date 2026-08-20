package com.kadamitas.warlockery.entity.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

/**
 * A precomputed centre-out box envelope with a windowing rule that keeps a budget-constrained scan
 * complete.
 *
 * <p>Every family that hand-rolled this walked the box in raster or ring order and stopped when the
 * read budget ran out. Because every real budget is a fraction of its own box volume, such a scan
 * spends the whole budget on the innermost ring or the first corner, and the far envelope, sometimes
 * including the entity's own level, is never evaluated at all.</p>
 *
 * <p>A window is therefore split in two: a fixed <em>near anchor</em> of the first {@code readCap/2}
 * offsets, which is present in every single window and always contains the origin and its immediate
 * neighbourhood, and a <em>rotating page</em> of the remaining budget over the far tail, whose
 * cursor advances by exactly one page per scan. The union of {@link #scansToCover} successive
 * windows is the whole envelope, far corners included.</p>
 *
 * @param horizontalRadius half extent on x and z
 * @param verticalRadius half extent on y
 * @param offsets every offset in the box, sorted by squared distance then y, x, z
 */
public record ScanEnvelope(int horizontalRadius, int verticalRadius, List<BlockPos> offsets) {

    private static final Map<Long, ScanEnvelope> CACHE = new ConcurrentHashMap<>();

    public ScanEnvelope {
        if (horizontalRadius < 0 || verticalRadius < 0) {
            throw new IllegalArgumentException(
                "radii must not be negative: " + horizontalRadius + ", " + verticalRadius);
        }
        offsets = List.copyOf(offsets);
    }

    /** The shared immutable envelope for a box shape. Identical shapes always share one instance. */
    public static ScanEnvelope of(final int horizontalRadius, final int verticalRadius) {
        if (horizontalRadius < 0 || verticalRadius < 0) {
            throw new IllegalArgumentException(
                "radii must not be negative: " + horizontalRadius + ", " + verticalRadius);
        }
        return CACHE.computeIfAbsent((long) horizontalRadius << 32 | verticalRadius,
            _ -> new ScanEnvelope(horizontalRadius, verticalRadius,
                enumerate(horizontalRadius, verticalRadius)));
    }

    private static List<BlockPos> enumerate(final int horizontal, final int vertical) {
        final List<BlockPos> offsets = new ArrayList<>(volume(horizontal, vertical));
        for (int dy = -vertical; dy <= vertical; dy++) {
            for (int dx = -horizontal; dx <= horizontal; dx++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    offsets.add(new BlockPos(dx, dy, dz));
                }
            }
        }
        offsets.sort(Comparator
            .comparingInt((BlockPos offset) -> offset.getX() * offset.getX()
                + offset.getY() * offset.getY() + offset.getZ() * offset.getZ())
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getZ));
        return offsets;
    }

    private static int volume(final int horizontal, final int vertical) {
        return (2 * horizontal + 1) * (2 * horizontal + 1) * (2 * vertical + 1);
    }

    public int size() {
        return offsets.size();
    }

    /** The offsets present in every window, so the origin neighbourhood can never rotate away. */
    public int anchorSize(final int readCap) {
        return Math.min(Math.max(0, readCap) / 2, size());
    }

    /** The budget left for the rotating page over the far tail. */
    public int pageSize(final int readCap) {
        final int anchor = anchorSize(readCap);
        return Math.min(Math.max(0, readCap - anchor), size() - anchor);
    }

    public int tailSize(final int readCap) {
        return size() - anchorSize(readCap);
    }

    /**
     * The exact offsets one scan evaluates. Pure and world free, so the coverage contract is
     * directly testable without a level.
     */
    public List<BlockPos> window(final int readCap, final int cursor) {
        final int anchor = anchorSize(readCap);
        final int tail = size() - anchor;
        final int page = pageSize(readCap);
        final List<BlockPos> window = new ArrayList<>(anchor + page);
        window.addAll(offsets.subList(0, anchor));
        if (tail > 0) {
            final int start = Math.floorMod(cursor, tail);
            for (int index = 0; index < page; index++) {
                window.add(offsets.get(anchor + (start + index) % tail));
            }
        }
        return List.copyOf(window);
    }

    /**
     * The cursor the next scan of the same budget must use for the union to keep growing.
     *
     * <p>The incoming cursor is reduced into the tail before the page is added. Adding first
     * overflows for a cursor near {@link Integer#MAX_VALUE}, which wraps to a negative and can land
     * back on the page just scanned, so the rotation stalls and the envelope is never covered. A
     * family that stores a plain counter reaches that range eventually.</p>
     */
    public int advanceCursor(final int readCap, final int cursor) {
        final int tail = tailSize(readCap);
        if (tail <= 0) {
            return 0;
        }
        return Math.floorMod(Math.floorMod(cursor, tail) + pageSize(readCap), tail);
    }

    /** How many successive windows are needed before their union is the whole envelope. */
    public int scansToCover(final int readCap) {
        final int tail = tailSize(readCap);
        final int page = pageSize(readCap);
        if (tail <= 0) {
            return 1;
        }
        if (page <= 0) {
            throw new IllegalArgumentException(
                "a read cap of " + readCap + " cannot cover an envelope of " + size());
        }
        return Math.ceilDiv(tail, page);
    }

    /**
     * A per-entity starting cursor. Unseeded cursors all start at zero, which makes every entity of
     * a family scan the same page on the same tick, so spreading them by identity is what keeps the
     * far envelope reached promptly across a crowd.
     */
    public int seedCursor(final UUID identity, final int readCap) {
        return Ticks.stableOffset(identity, tailSize(readCap));
    }
}
