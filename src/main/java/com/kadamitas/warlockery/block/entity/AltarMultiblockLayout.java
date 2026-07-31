package com.kadamitas.warlockery.block.entity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;

public final class AltarMultiblockLayout {
    private static final int REQUIRED_BLOCKS = 6;

    private AltarMultiblockLayout() {
    }

    public static Result inspect(final BlockPos origin, final Predicate<BlockPos> isAltar) {
        final int connectedBlocks = connectedCount(origin, isAltar);
        if (connectedBlocks != REQUIRED_BLOCKS) {
            return new Result(false, connectedBlocks);
        }
        final boolean rectangle = fits(origin, isAltar, 3, 2) || fits(origin, isAltar, 2, 3);
        return new Result(rectangle, connectedBlocks);
    }

    private static boolean fits(
        final BlockPos origin,
        final Predicate<BlockPos> isAltar,
        final int width,
        final int depth
    ) {
        return IntStream.range(0, width).anyMatch(originX ->
            IntStream.range(0, depth).anyMatch(originZ -> {
                final BlockPos corner = origin.offset(-originX, 0, -originZ);
                return IntStream.range(0, width).allMatch(x ->
                    IntStream.range(0, depth).allMatch(z -> isAltar.test(corner.offset(x, 0, z)))
                );
            })
        );
    }

    private static int connectedCount(final BlockPos origin, final Predicate<BlockPos> isAltar) {
        if (!isAltar.test(origin)) {
            return 0;
        }
        final Set<BlockPos> visited = new HashSet<>();
        final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(origin.immutable());
        while (!frontier.isEmpty() && visited.size() <= REQUIRED_BLOCKS) {
            final BlockPos current = frontier.removeFirst();
            if (visited.contains(current) || !isAltar.test(current)) {
                continue;
            }
            visited.add(current);
            frontier.add(current.north());
            frontier.add(current.south());
            frontier.add(current.east());
            frontier.add(current.west());
        }
        return visited.size();
    }

    public record Result(boolean valid, int connectedBlocks) {
    }
}
