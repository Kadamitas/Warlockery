package com.kadamitas.warlockery.ritual;

import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.world.level.ChunkPos;

public record BiomeShiftPlan(boolean empowered, int netherStars) {
    public static final int MAX_NETHER_STARS = 3;
    public static final int MAX_CHUNK_RADIUS = MAX_NETHER_STARS + 1;

    public BiomeShiftPlan {
        netherStars = Math.clamp(netherStars, 0, MAX_NETHER_STARS);
    }

    public static BiomeShiftPlan create(final boolean empowered, final int offeredNetherStars) {
        return new BiomeShiftPlan(empowered, offeredNetherStars);
    }

    public int chunkRadius() {
        return (empowered ? 1 : 0) + netherStars;
    }

    public List<ChunkPos> chunksAround(final ChunkPos center) {
        return chunksAround(center, chunkRadius());
    }

    public static List<ChunkPos> chunksAround(final ChunkPos center, final int requestedRadius) {
        final int radius = Math.clamp(requestedRadius, 0, MAX_CHUNK_RADIUS);
        return IntStream.rangeClosed(-radius, radius)
            .boxed()
            .flatMap(xOffset -> IntStream.rangeClosed(-radius, radius)
                .mapToObj(zOffset -> new ChunkPos(center.x() + xOffset, center.z() + zOffset)))
            .toList();
    }

    public int chunkCount() {
        final int diameter = chunkRadius() * 2 + 1;
        return diameter * diameter;
    }
}
