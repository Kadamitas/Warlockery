package com.kadamitas.warlockery.ritual.hex;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;

public final class ToadRainHex {
    private ToadRainHex() {
    }

    public static ToadRainReport apply(
        final ServerLevel level,
        final BlockPos center,
        final int radius,
        final int count,
        final int durationTicks
    ) {
        final int safeCount = Math.clamp(count, 1, 16);
        final int safeRadius = Math.clamp(radius, 2, 16);
        final int[] roles = new int[ToadRainRules.ToadRole.values().length];
        final int spawned = (int) IntStream.range(0, safeCount)
            .filter(index -> spawn(level, center, safeRadius, safeCount, durationTicks, index, roles))
            .count();
        return new ToadRainReport(spawned, roles[0], roles[1]);
    }

    private static boolean spawn(
        final ServerLevel level,
        final BlockPos center,
        final int radius,
        final int total,
        final int durationTicks,
        final int index,
        final int[] roles
    ) {
        final LivingEntity toad = BuiltInRegistries.ENTITY_TYPE
            .getRandomElementOf(WarlockeryTags.EntityTypes.HEX_TOADS, level.getRandom())
            .map(holder -> holder.value().create(level, EntitySpawnReason.EVENT))
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .orElse(null);
        if (toad == null) {
            return false;
        }
        final double angle = Math.PI * 2.0 * index / total;
        final double distance = 1.0 + index % Math.max(2, radius - 1);
        toad.snapTo(
            center.getX() + 0.5 + Math.cos(angle) * distance,
            center.getY() + 6.0 + index % 4,
            center.getZ() + 0.5 + Math.sin(angle) * distance
        );
        if (!level.isLoaded(toad.blockPosition()) || !level.noCollision(toad)) {
            toad.discard();
            return false;
        }
        final ToadRainRules.ToadRole role = ToadRainRules.roleFor(index);
        final long gameTime = level.getGameTime();
        HexEntityMarkers.markToad(
            toad,
            role,
            gameTime + Math.max(ToadRainRules.EXPLOSION_DELAY_TICKS + 1, durationTicks),
            role == ToadRainRules.ToadRole.EXPLOSIVE
                ? gameTime + ToadRainRules.EXPLOSION_DELAY_TICKS + index % 20
                : Long.MAX_VALUE
        );
        final boolean added = level.addFreshEntity(toad);
        if (added) {
            roles[role.ordinal()]++;
        }
        return added;
    }

    public record ToadRainReport(int spawned, int poisonous, int explosive) {
        public ToadRainReport {
            if (spawned < 0 || poisonous < 0 || explosive < 0 || poisonous + explosive > spawned) {
                throw new IllegalArgumentException("Toad rain report counts must be consistent");
            }
        }

        public boolean complete(final int requested) {
            return spawned >= Math.clamp(requested, 1, 16)
                && poisonous > 0
                && explosive > 0;
        }
    }
}
