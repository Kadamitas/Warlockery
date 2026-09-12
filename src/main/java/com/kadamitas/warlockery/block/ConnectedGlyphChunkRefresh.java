package com.kadamitas.warlockery.block;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Refreshes saved glyph states after their chunks become available. */
public final class ConnectedGlyphChunkRefresh {
    private static final int CHUNKS_PER_TICK = 8;
    private static final Map<ServerLevel, Pending> PENDING = new WeakHashMap<>();

    private ConnectedGlyphChunkRefresh() {
    }

    public static void queue(final ServerLevel level, final ChunkPos pos) {
        synchronized (PENDING) {
            PENDING.computeIfAbsent(level, ignored -> new Pending()).incoming.add(pos);
        }
    }

    public static void tick(final ServerLevel level) {
        final List<ChunkPos> batch = new ArrayList<>(CHUNKS_PER_TICK);
        synchronized (PENDING) {
            final Pending pending = PENDING.get(level);
            if (pending == null) return;
            final var iterator = pending.ready.iterator();
            while (iterator.hasNext() && batch.size() < CHUNKS_PER_TICK) {
                batch.add(iterator.next());
                iterator.remove();
            }
            // Load callbacks can run before FULL promotion. Wait through one complete level tick.
            pending.ready.addAll(pending.incoming);
            pending.incoming.clear();
            if (pending.ready.isEmpty()) PENDING.remove(level);
        }
        for (final ChunkPos pos : batch) refreshLoadedChunk(level, pos);
    }

    private static void refreshLoadedChunk(final ServerLevel level, final ChunkPos pos) {
        if (loadedChunk(level, pos.x(), pos.z()) == null) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final LevelChunk chunk = loadedChunk(level, pos.x() + dx, pos.z() + dz);
                if (chunk == null) continue;
                // An arriving neighbor can complete connections along an earlier chunk's edge.
                refreshColumns(level, chunk, dx < 0 ? 15 : 0, dx > 0 ? 0 : 15,
                    dz < 0 ? 15 : 0, dz > 0 ? 0 : 15);
            }
        }
    }

    private static void refreshColumns(final ServerLevel level, final LevelChunk chunk,
        final int minX, final int maxX, final int minZ, final int maxZ) {
        final LevelChunkSection[] sections = chunk.getSections();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            final LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(ConnectedGlyphBlock::connectsTo)) continue;
            final int bottomY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = 0; y < 16; y++) {
                        final BlockState state = section.getBlockState(x, y, z);
                        if (!(state.getBlock() instanceof ConnectedGlyphBlock glyph)) continue;
                        pos.set(chunk.getPos().getMinBlockX() + x, bottomY + y, chunk.getPos().getMinBlockZ() + z);
                        if (!hasLoadedNeighborhood(level, pos)) continue;
                        final BlockState connected = glyph.connectedState(level, pos);
                        if (state != connected) {
                            // Only rendering bits change. Avoid neighbor callbacks reaching unloaded chunks.
                            level.setBlock(pos, connected, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    }
                }
            }
        }
    }

    private static boolean hasLoadedNeighborhood(final ServerLevel level, final BlockPos pos) {
        for (int x = (pos.getX() - 1) >> 4; x <= (pos.getX() + 1) >> 4; x++) {
            for (int z = (pos.getZ() - 1) >> 4; z <= (pos.getZ() + 1) >> 4; z++) {
                if (loadedChunk(level, x, z) == null) return false;
            }
        }
        return true;
    }

    private static LevelChunk loadedChunk(final ServerLevel level, final int x, final int z) {
        return level.getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk ? chunk : null;
    }

    private static final class Pending {
        private final LinkedHashSet<ChunkPos> incoming = new LinkedHashSet<>();
        private final LinkedHashSet<ChunkPos> ready = new LinkedHashSet<>();
    }
}
