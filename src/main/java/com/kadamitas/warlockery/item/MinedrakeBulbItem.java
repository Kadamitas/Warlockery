package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.MinedrakeCombatRules;
import com.kadamitas.warlockery.entity.DreamrootEntity;
import com.kadamitas.warlockery.entity.LivingRootsRules;
import com.kadamitas.warlockery.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class MinedrakeBulbItem extends BlockItem {
    public MinedrakeBulbItem(final Block block, final Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean onEntityItemUpdate(final ItemStack stack, final ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)
            || !MinedrakeCombatRules.bulbReady(entity.getAge(), stack.getCount(), true)) {
            return false;
        }
        int spawned = 0;
        final int requested = stack.getCount();
        final int batch = Math.min(requested, MinedrakeCombatRules.BULB_PER_WAKE_BATCH);
        for (int index = 0; index < batch; index++) {
            if (!LivingRootsRules.quota(level).bulb()) {
                break;
            }
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
            if (minedrake instanceof DreamrootEntity dreamroot
                && entity.getOwner() instanceof net.minecraft.world.entity.player.Player owner
                && owner.isAlive() && !owner.isSpectator() && !owner.isCreative()) {
                dreamroot.setBulbOwnerHint(owner.getUUID());
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

}
