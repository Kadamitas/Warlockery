package com.kadamitas.warlockery.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

public final class ResourceFoodItem extends Item {
    public ResourceFoodItem(final Properties properties, final Profile profile) {
        super(configure(properties, profile));
    }

    private static Properties configure(final Properties properties, final Profile profile) {
        final Consumable.Builder consumable = Consumable.builder()
            .consumeSeconds(1.6F)
            .animation(ItemUseAnimation.EAT)
            .sound(SoundEvents.GENERIC_EAT)
            .hasConsumeParticles(true);
        if (profile == Profile.WORMY_APPLE) {
            consumable.onConsume(new ApplyStatusEffectsConsumeEffect(
                new MobEffectInstance(MobEffects.POISON, 160, 0)
            ));
        }
        return properties
            .food(new FoodProperties.Builder()
                .nutrition(profile.nutrition())
                .saturationModifier(profile.saturation())
                .build())
            .component(DataComponents.CONSUMABLE, consumable.build());
    }

    public enum Profile {
        ROWAN_BERRIES(1, 0.1F),
        WORMY_APPLE(2, 0.1F);

        private final int nutrition;
        private final float saturation;

        Profile(final int nutrition, final float saturation) {
            this.nutrition = nutrition;
            this.saturation = saturation;
        }

        public int nutrition() {
            return nutrition;
        }

        public float saturation() {
            return saturation;
        }
    }
}
