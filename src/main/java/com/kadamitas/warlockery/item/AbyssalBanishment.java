package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.Warlockery;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public final class AbyssalBanishment {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(
        Registries.DIMENSION,
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "abyss")
    );

    private AbyssalBanishment() {
    }

    public static boolean canBanish(final LivingEntity target) {
        return target.isAlive() && !(target instanceof Player);
    }

    public static BlockPos arrivalFor(final UUID targetId) {
        final int hash = targetId.hashCode();
        final int x = (Math.floorMod(hash, 257) - 128) * 32;
        final int z = (Math.floorMod(Integer.rotateLeft(hash, 13), 257) - 128) * 32;
        return new BlockPos(x, 64, z);
    }

    public static boolean banish(final ServerLevel source, final LivingEntity target) {
        if (!canBanish(target)) {
            return false;
        }
        final ServerLevel destination = source.getServer().getLevel(DIMENSION);
        if (destination == null) {
            return false;
        }
        final BlockPos arrival = arrivalFor(target.getUUID());
        prepareArrival(destination, arrival);
        return target.teleport(new TeleportTransition(
            destination,
            Vec3.atBottomCenterOf(arrival),
            Vec3.ZERO,
            target.getYRot(),
            target.getXRot(),
            TeleportTransition.PLAY_PORTAL_SOUND
        )) != null;
    }

    private static void prepareArrival(final ServerLevel level, final BlockPos arrival) {
        BlockPos.betweenClosedStream(arrival.offset(-2, -1, -2), arrival.offset(2, -1, 2))
            .forEach(pos -> level.setBlockAndUpdate(pos, Blocks.BLACKSTONE.defaultBlockState()));
        BlockPos.betweenClosedStream(arrival.offset(-1, 0, -1), arrival.offset(1, 2, 1))
            .forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
    }
}
