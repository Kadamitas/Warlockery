package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.MinedrakeCombatRules;
import com.kadamitas.warlockery.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class MinedrakeBulbItem extends BlockItem implements DroppedItemBehavior {
    public MinedrakeBulbItem(final Block block, final Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean tickDroppedItem(final ItemStack stack, final ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)
            || !MinedrakeCombatRules.bulbReady(entity.getAge(), stack.getCount(), true)) {
            return false;
        }
        final Player target = target(level, entity);
        int spawned = 0;
        final int requested = stack.getCount();
        for (int index = 0; index < requested; index++) {
            final Entity created = ModEntities.ALL.get("dreamroot").get().create(level, EntitySpawnReason.EVENT);
            if (!(created instanceof Mob minedrake)) {
                continue;
            }
            final double angle = Math.PI * 2.0 * index / requested;
            minedrake.snapTo(
                entity.getX() + Math.cos(angle) * 0.4,
                entity.getY(),
                entity.getZ() + Math.sin(angle) * 0.4
            );
            minedrake.setPersistenceRequired();
            if (target != null && minedrake.canAttack(target)) {
                minedrake.setTarget(target);
            }
            if (level.addFreshEntity(minedrake)) {
                spawned++;
            }
        }
        if (spawned == 0) {
            return false;
        }
        stack.shrink(spawned);
        if (stack.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(stack);
        }
        return true;
    }

    private static Player target(final ServerLevel level, final ItemEntity entity) {
        if (entity.getOwner() instanceof Player owner
            && owner.isAlive()
            && !owner.isSpectator()
            && !owner.isCreative()) {
            return owner;
        }
        return level.getNearestPlayer(
            entity.getX(),
            entity.getY(),
            entity.getZ(),
            MinedrakeCombatRules.TARGET_RANGE,
            candidate -> candidate instanceof Player player
                && player.isAlive()
                && !player.isSpectator()
                && !player.isCreative()
        );
    }
}
