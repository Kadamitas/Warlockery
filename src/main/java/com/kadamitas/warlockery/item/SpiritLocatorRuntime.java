package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.block.ConnectedGlyphBlock;
import com.kadamitas.warlockery.registry.ModBlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

public final class SpiritLocatorRuntime {
    private static final String ATTUNED_AT = "WarlockerySpiritLocatorAttunedAt";
    private static final String USED = "WarlockerySpiritLocatorUsed";
    private static final TagKey<Structure> VILLAGES = structureTag("spirit_locator/villages");
    private static final TagKey<Structure> NETHER_FORTRESSES = structureTag("spirit_locator/nether_fortresses");

    private SpiritLocatorRuntime() {
    }

    public static void tick(final ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        final CompoundTag data = WarlockeryEntityData.get(entity);
        if (data.getBooleanOr(USED, false)) {
            return;
        }
        final Optional<BlockPos> ringCenter = ringCenter(level, entity.blockPosition());
        if (ringCenter.isEmpty()) {
            data.remove(ATTUNED_AT);
            return;
        }
        final long gameTime = level.getGameTime();
        if (!data.contains(ATTUNED_AT)) {
            data.putLong(ATTUNED_AT, gameTime + SpiritLocatorRules.ATTUNEMENT_TICKS);
            return;
        }
        if (gameTime < data.getLongOr(ATTUNED_AT, Long.MAX_VALUE)) {
            return;
        }
        final BlockPos center = ringCenter.orElseThrow();
        markOfferingsUsed(level, center);
        reveal(level, entity, center);
    }

    static Optional<BlockPos> ringCenter(final ServerLevel level, final BlockPos itemPosition) {
        return java.util.stream.IntStream.of(0, -1, 1)
            .mapToObj(deltaY -> itemPosition.offset(0, deltaY, 0))
            .filter(center -> isHollowRitualRing(level, center))
            .findFirst();
    }

    static boolean isHollowRitualRing(final ServerLevel level, final BlockPos center) {
        if (level.getBlockState(center).getBlock() instanceof ConnectedGlyphBlock) {
            return false;
        }
        return SpiritLocatorRules.ringOffsets().stream()
            .map(center::offset)
            .allMatch(position -> level.getBlockState(position).is(ModBlocks.ALL.get("circleglyphritual").get()));
    }

    private static void reveal(final ServerLevel level, final ItemEntity entity, final BlockPos center) {
        final Optional<LocatorTarget> target = LocatorTarget.forDimension(level.dimension());
        if (target.isEmpty()) {
            notifyPlayer(level, entity, Component.translatable("message.warlockery.spirit_locator.unsupported_realm"));
            dissipate(level, center);
            return;
        }
        final LocatorTarget locatorTarget = target.orElseThrow();
        final BlockPos position = level.findNearestMapStructure(
            locatorTarget.structures(),
            center,
            SpiritLocatorRules.SEARCH_RADIUS_CHUNKS,
            false
        );
        if (position == null) {
            notifyPlayer(level, entity, Component.translatable(
                "message.warlockery.spirit_locator.not_found." + locatorTarget.messageSuffix(),
                SpiritLocatorRules.SEARCH_RADIUS_BLOCKS
            ));
            dissipate(level, center);
            return;
        }
        final String direction = SpiritLocatorRules.directionKey(
            position.getX() - center.getX(),
            position.getZ() - center.getZ()
        );
        notifyPlayer(level, entity, Component.translatable(
            "message.warlockery.spirit_locator.found." + locatorTarget.messageSuffix(),
            Component.translatable("message.warlockery.spirit_locator.direction." + direction),
            SpiritLocatorRules.horizontalDistance(center, position)
        ));
        pointToward(level, center, position);
    }

    private static void markOfferingsUsed(final ServerLevel level, final BlockPos center) {
        level.getEntitiesOfClass(
            ItemEntity.class,
            new AABB(center).inflate(0.25D, 0.75D, 0.25D),
            item -> item.getItem().getItem() instanceof AttunedStoneItem
                || item.getItem().getItem() instanceof VillageSpiritItem
        ).forEach(item -> {
            WarlockeryEntityData.get(item).putBoolean(USED, true);
            WarlockeryEntityData.get(item).remove(ATTUNED_AT);
        });
    }

    private static void notifyPlayer(final ServerLevel level, final ItemEntity entity, final Component message) {
        final Entity owner = entity.getOwner();
        final ServerPlayer recipient = owner instanceof ServerPlayer player
            && player.distanceToSqr(entity) <= SpiritLocatorRules.MESSAGE_RANGE * SpiritLocatorRules.MESSAGE_RANGE
            ? player
            : level.getNearestPlayer(
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                SpiritLocatorRules.MESSAGE_RANGE,
                candidate -> candidate instanceof ServerPlayer
            ) instanceof ServerPlayer nearby ? nearby : null;
        if (recipient != null) {
            recipient.sendSystemMessage(message);
        }
    }

    private static void pointToward(final ServerLevel level, final BlockPos center, final BlockPos destination) {
        final double deltaX = destination.getX() - center.getX();
        final double deltaZ = destination.getZ() - center.getZ();
        final double length = Math.max(1.0D, Math.hypot(deltaX, deltaZ));
        final double unitX = deltaX / length;
        final double unitZ = deltaZ / length;
        for (int step = 0; step < 18; step++) {
            final double distance = 0.5D + step * 0.45D;
            level.sendParticles(
                ParticleTypes.SOUL,
                center.getX() + 0.5D + unitX * distance,
                center.getY() + 0.45D + Math.sin(step * 0.7D) * 0.12D,
                center.getZ() + 0.5D + unitZ * distance,
                1,
                0.015D,
                0.015D,
                0.015D,
                0.0D
            );
        }
    }

    private static void dissipate(final ServerLevel level, final BlockPos center) {
        level.sendParticles(
            ParticleTypes.SOUL,
            center.getX() + 0.5D,
            center.getY() + 0.45D,
            center.getZ() + 0.5D,
            12,
            0.7D,
            0.15D,
            0.7D,
            0.01D
        );
    }

    private static TagKey<Structure> structureTag(final String path) {
        return TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, path));
    }

    private record LocatorTarget(TagKey<Structure> structures, String messageSuffix) {
        private static Optional<LocatorTarget> forDimension(final ResourceKey<Level> dimension) {
            if (dimension.equals(Level.OVERWORLD)) {
                return Optional.of(new LocatorTarget(VILLAGES, "village"));
            }
            if (dimension.equals(Level.NETHER)) {
                return Optional.of(new LocatorTarget(NETHER_FORTRESSES, "fortress"));
            }
            return Optional.empty();
        }
    }
}
