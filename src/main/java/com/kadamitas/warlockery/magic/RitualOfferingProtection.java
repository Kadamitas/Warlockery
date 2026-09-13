package com.kadamitas.warlockery.magic;

import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.ritual.RitualManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

/** Finds physical offering zones locally without loading chunks or maintaining another world index. */
final class RitualOfferingProtection {
    private RitualOfferingProtection() { }

    static List<AABB> nearbyZones(final ServerLevel level, final AABB pickupArea) {
        final AABB search = pickupArea.inflate(RitualManager.OFFERING_RADIUS + 1.0);
        final int minX = Mth.floor(search.minX), maxX = Mth.floor(search.maxX);
        final int minY = Mth.floor(search.minY), maxY = Mth.floor(search.maxY);
        final int minZ = Mth.floor(search.minZ), maxZ = Mth.floor(search.maxZ);
        final var heart = ModBlocks.ALL.get("circle").get();
        final List<AABB> zones = new ArrayList<>();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (!(level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
                final var sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    final int bottom = chunk.getSectionYFromSectionIndex(index) << 4;
                    if (bottom > maxY || bottom + 15 < minY) continue;
                    final var section = sections[index];
                    if (!section.maybeHas(state -> state.is(heart))) continue;
                    for (int x = Math.max(minX, chunkX << 4); x <= Math.min(maxX, (chunkX << 4) + 15); x++) {
                        for (int z = Math.max(minZ, chunkZ << 4); z <= Math.min(maxZ, (chunkZ << 4) + 15); z++) {
                            for (int y = Math.max(minY, bottom); y <= Math.min(maxY, bottom + 15); y++) {
                                if (section.getBlockState(x & 15, y & 15, z & 15).is(heart)) {
                                    final AABB zone = new AABB(new BlockPos(x, y, z)).inflate(RitualManager.OFFERING_RADIUS);
                                    zones.add(zone);
                                }
                            }
                        }
                    }
                }
            }
        }
        return zones;
    }
}
