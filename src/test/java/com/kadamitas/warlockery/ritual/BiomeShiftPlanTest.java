package com.kadamitas.warlockery.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class BiomeShiftPlanTest {
    @Test
    void unempoweredCastChangesOnlyItsCenterChunk() {
        final BiomeShiftPlan plan = BiomeShiftPlan.create(false, 0);
        assertEquals(0, plan.chunkRadius());
        assertEquals(1, plan.chunkCount());
        assertEquals(List.of(new ChunkPos(4, -2)), plan.chunksAround(new ChunkPos(4, -2)));
    }

    @Test
    void empowermentAddsTheFirstSquareRing() {
        final BiomeShiftPlan plan = BiomeShiftPlan.create(true, 0);
        assertEquals(1, plan.chunkRadius());
        assertEquals(9, plan.chunkCount());
        assertTrue(plan.chunksAround(new ChunkPos(0, 0)).containsAll(List.of(
            new ChunkPos(-1, -1), new ChunkPos(0, 0), new ChunkPos(1, 1)
        )));
    }

    @Test
    void eachOfThreeNetherStarsAddsOneSquareRing() {
        IntStream.rangeClosed(1, BiomeShiftPlan.MAX_NETHER_STARS).forEach(stars -> {
            final BiomeShiftPlan unempowered = BiomeShiftPlan.create(false, stars);
            final BiomeShiftPlan empowered = BiomeShiftPlan.create(true, stars);
            assertEquals(stars, unempowered.chunkRadius());
            assertEquals(stars + 1, empowered.chunkRadius());
            assertEquals(squareDiameter(stars), unempowered.chunkCount());
            assertEquals(squareDiameter(stars + 1), empowered.chunkCount());
        });
    }

    @Test
    void moreThanThreeNetherStarsCannotAddFurtherRings() {
        final BiomeShiftPlan plan = BiomeShiftPlan.create(true, 64);
        assertEquals(BiomeShiftPlan.MAX_NETHER_STARS, plan.netherStars());
        assertEquals(BiomeShiftPlan.MAX_CHUNK_RADIUS, plan.chunkRadius());
        assertEquals(81, plan.chunkCount());
    }

    private static int squareDiameter(final int radius) {
        final int diameter = radius * 2 + 1;
        return diameter * diameter;
    }
}

