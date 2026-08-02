package com.kadamitas.warlockery.ritual;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;

final class BiomeShiftRuntime {
    private BiomeShiftRuntime() {
    }

    static int apply(
        final ServerLevel level,
        final BlockPos center,
        final Holder<Biome> target,
        final int chunkRadius
    ) {
        final List<ChunkAccess> chunks = BiomeShiftPlan.chunksAround(ChunkPos.containing(center), chunkRadius).stream()
            .map(position -> (ChunkAccess) level.getChunk(position.x(), position.z()))
            .toList();
        final var sampler = level.getChunkSource().randomState().sampler();
        chunks.forEach(chunk -> {
            chunk.fillBiomesFromNoise((_, _, _, _) -> target, sampler);
            chunk.markUnsaved();
        });
        level.getChunkSource().chunkMap.resendBiomesForChunks(chunks);
        return chunks.size();
    }
}
