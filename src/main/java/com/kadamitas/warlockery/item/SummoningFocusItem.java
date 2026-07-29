package com.kadamitas.warlockery.item;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class SummoningFocusItem extends Item {
    private static final String OWNER = "WarlockerySummoningOwner";
    private final Supplier<? extends EntityType<?>> entityType;
    private final boolean toggle;

    public SummoningFocusItem(
        final Properties properties,
        final Supplier<? extends EntityType<?>> entityType,
        final boolean toggle
    ) {
        super(properties);
        this.entityType = entityType;
        this.toggle = toggle;
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        final String owner = player.getUUID().toString();
        if (toggle) {
            final var existing = serverLevel.getEntities(
                (Entity) null,
                new AABB(player.blockPosition()).inflate(64),
                entity -> entity.getType() == entityType()
                    && owner.equals(entity.getPersistentData().getStringOr(OWNER, ""))
            );
            if (!existing.isEmpty()) {
                existing.forEach(Entity::discard);
                return InteractionResult.SUCCESS;
            }
        }
        final BlockPos spawn = player.blockPosition().relative(player.getDirection(), 2).above();
        final Entity summoned = entityType().spawn(serverLevel, spawn, EntitySpawnReason.EVENT);
        if (summoned == null) {
            return InteractionResult.FAIL;
        }
        summoned.getPersistentData().putString(OWNER, owner);
        if (!toggle && !player.hasInfiniteMaterials()) {
            player.getItemInHand(hand).shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    private EntityType<?> entityType() {
        return entityType.get();
    }
}
