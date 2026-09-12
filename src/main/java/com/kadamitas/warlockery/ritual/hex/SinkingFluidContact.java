package com.kadamitas.warlockery.ritual.hex;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

public final class SinkingFluidContact {
    private SinkingFluidContact() {
    }

    public static double height(final LivingEntity target) {
        final AABB contact = target.getFluidInteractionBox();
        if (contact == null) {
            return 0.0;
        }
        final Level level = target.level();
        final int minX = Mth.floor(contact.minX);
        final int minY = Mth.floor(contact.minY);
        final int minZ = Mth.floor(contact.minZ);
        final int maxX = Mth.ceil(contact.maxX) - 1;
        final int maxY = Mth.ceil(contact.maxY) - 1;
        final int maxZ = Mth.ceil(contact.maxZ) - 1;
        if (!hasLoadedContactArea(level, minX - 1, minZ - 1, maxX + 1, maxZ + 1)) {
            return 0.0;
        }
        final double feet = target.getBoundingBox().minY;
        final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        double height = 0.0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    position.set(x, y, z);
                    final var fluid = level.getFluidState(position);
                    if (!fluid.isEmpty() && fluid.is(WarlockeryTags.Fluids.SINKING_FLUIDS)) {
                        final double surface = y + fluid.getHeight(level, position);
                        if (surface >= contact.minY) {
                            height = Math.max(height, surface - feet);
                        }
                    }
                }
            }
        }
        return height;
    }

    private static boolean hasLoadedContactArea(
        final Level level, final int minX, final int minZ, final int maxX, final int maxZ
    ) {
        for (int chunkX = SectionPos.blockToSectionCoord(minX);
            chunkX <= SectionPos.blockToSectionCoord(maxX); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(minZ);
                chunkZ <= SectionPos.blockToSectionCoord(maxZ); chunkZ++) {
                if (level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                    return false;
                }
            }
        }
        return true;
    }
}
