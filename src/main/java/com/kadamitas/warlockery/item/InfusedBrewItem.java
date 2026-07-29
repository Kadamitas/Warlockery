package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class InfusedBrewItem extends Item {
    public static final int GRAVE_DURATION = 20 * 60 * 120;

    private final MagicPath path;
    private final int duration;

    public InfusedBrewItem(final Properties properties, final MagicPath path, final int duration) {
        super(properties);
        this.path = path;
        this.duration = Math.max(1, duration);
    }

    @Override
    public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity entity) {
        final ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide() && entity instanceof Player player) {
            MagicPathState.grantTimed(player, path, duration);
        }
        return result;
    }
}
