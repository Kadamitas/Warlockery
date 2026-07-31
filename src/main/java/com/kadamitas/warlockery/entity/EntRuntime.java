package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

public final class EntRuntime {
    private static boolean registered;

    private EntRuntime() {
    }

    public static void registerEvents() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> handleLogBreak(event));
    }

    public static void handleLogBreak(final BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
            || !(event.getPlayer() instanceof ServerPlayer player)
            || !event.getState().is(WarlockeryTags.Blocks.ENT_SPAWNING_LOGS)) {
            return;
        }
        final int neighboringLogs = neighboringLogCount(level, event.getPos());
        if (!EntRules.shouldSpawn(neighboringLogs, level.getRandom().nextDouble())) {
            return;
        }
        findSpawnPosition(level, event.getPos()).ifPresent(position -> spawn(level, player, event.getPos(), position));
    }

    static int neighboringLogCount(final ServerLevel level, final BlockPos brokenLog) {
        return (int) BlockPos.betweenClosedStream(brokenLog.offset(-1, -1, -1), brokenLog.offset(1, 1, 1))
            .filter(position -> !position.equals(brokenLog))
            .filter(position -> level.getBlockState(position).is(WarlockeryTags.Blocks.ENT_SPAWNING_LOGS))
            .count();
    }

    private static Optional<BlockPos> findSpawnPosition(final ServerLevel level, final BlockPos origin) {
        for (int attempt = 0; attempt < 12; attempt++) {
            final int x = EntRules.horizontalOffset(level.getRandom().nextInt(9), level.getRandom().nextBoolean());
            final int z = EntRules.horizontalOffset(level.getRandom().nextInt(9), level.getRandom().nextBoolean());
            for (int y = 0; y <= EntRules.MAX_VERTICAL_SPAWN_OFFSET; y++) {
                final BlockPos candidate = origin.offset(x, EntRules.verticalOffset(y), z);
                if (canSpawnAt(level, candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean canSpawnAt(final ServerLevel level, final BlockPos position) {
        if (!level.getWorldBorder().isWithinBounds(position)
            || !level.getBlockState(position.below()).isFaceSturdy(level, position.below(), Direction.UP)) {
            return false;
        }
        for (int height = 0; height < 4; height++) {
            if (!level.getBlockState(position.above(height)).getCollisionShape(level, position.above(height)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void spawn(
        final ServerLevel level,
        final ServerPlayer player,
        final BlockPos origin,
        final BlockPos position
    ) {
        final EntEntity ent = ModEntities.ENT.get().spawn(level, position, EntitySpawnReason.EVENT);
        if (ent == null) {
            return;
        }
        ent.setPersistenceRequired();
        ent.setTarget(player);
        level.sendParticles(
            ParticleTypes.WITCH,
            position.getX() + 0.5D,
            position.getY() + 1.5D,
            position.getZ() + 0.5D,
            36,
            1.2D,
            1.5D,
            1.2D,
            0.04D
        );
        level.sendParticles(
            ParticleTypes.SMOKE,
            origin.getX() + 0.5D,
            origin.getY() + 0.5D,
            origin.getZ() + 0.5D,
            18,
            0.5D,
            0.8D,
            0.5D,
            0.02D
        );
        level.playSound(null, origin, SoundEvents.SKELETON_HORSE_DEATH, SoundSource.HOSTILE, 0.8F, 0.7F);
        level.playSound(null, position, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.HOSTILE, 1.0F, 0.55F);
    }
}
