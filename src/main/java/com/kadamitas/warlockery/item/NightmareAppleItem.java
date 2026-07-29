package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class NightmareAppleItem extends Item {
    private static final String FORCED_NIGHTMARE = "WarlockeryForcedNightmare";

    public NightmareAppleItem(final Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity consumer) {
        final ItemStack result = super.finishUsingItem(stack, level, consumer);
        if (!(level instanceof ServerLevel serverLevel)) {
            return result;
        }
        consumer.getPersistentData().putLong(FORCED_NIGHTMARE, level.getGameTime());
        consumer.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 600, 0));
        consumer.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 1));
        final var created = ModEntities.ALL.get("nightmare").get().create(serverLevel, EntitySpawnReason.EVENT);
        if (created instanceof Mob nightmare) {
            nightmare.snapTo(consumer.getX() + 3.0, consumer.getY(), consumer.getZ() + 1.0);
            if (serverLevel.noCollision(nightmare)) {
                nightmare.setTarget(consumer);
                serverLevel.addFreshEntity(nightmare);
            } else {
                nightmare.discard();
            }
        }
        if (consumer instanceof Player player) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.nightmare_apple.triggered"));
        }
        return result;
    }
}
