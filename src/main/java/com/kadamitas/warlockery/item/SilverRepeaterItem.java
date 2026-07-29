package com.kadamitas.warlockery.item;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class SilverRepeaterItem extends CrossbowItem {
    public SilverRepeaterItem(final Properties properties) {
        super(properties);
    }

    @Override
    protected void shoot(final ServerLevel level, final LivingEntity shooter, final InteractionHand hand,
                         final ItemStack weapon, final List<ItemStack> projectiles, final float power,
                         final float uncertainty, final boolean critical, final @Nullable LivingEntity target) {
        final List<ItemStack> fired = projectiles.size() == 1
            && projectiles.getFirst().is(com.kadamitas.warlockery.registry.ModItems.ALL.get("ingredient_bolt_splitting").get())
            ? List.of(projectiles.getFirst().copy(), projectiles.getFirst().copy(), projectiles.getFirst().copy())
            : projectiles;
        super.shoot(level, shooter, hand, weapon, fired, power, uncertainty, critical, target);
    }
}
