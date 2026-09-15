package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * Bolts are crossbow ammunition only: they are absent from the vanilla arrows tag, so a bow never
 * draws them, and a crossbow finds them here (off hand first, then main hand, then the inventory)
 * because the vanilla supported-projectile search no longer sees them.
 */
public final class CrossbowBoltAmmo {
    private CrossbowBoltAmmo() {
    }

    public static ItemStack select(final LivingEntity shooter, final ItemStack weapon, final ItemStack current) {
        if (!(weapon.getItem() instanceof CrossbowItem)) {
            return current;
        }
        final ItemStack offhand = shooter.getOffhandItem();
        if (offhand.is(WarlockeryTags.Items.CROSSBOW_BOLTS)) {
            return offhand;
        }
        final ItemStack mainHand = shooter.getMainHandItem();
        if (mainHand.is(WarlockeryTags.Items.CROSSBOW_BOLTS)) {
            return mainHand;
        }
        if (!current.isEmpty()) {
            return current;
        }
        if (shooter instanceof Player player) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                final ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(WarlockeryTags.Items.CROSSBOW_BOLTS)) {
                    return stack;
                }
            }
        }
        return current;
    }
}
