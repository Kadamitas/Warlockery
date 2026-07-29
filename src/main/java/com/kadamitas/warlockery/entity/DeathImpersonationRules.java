package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

public final class DeathImpersonationRules {
    private DeathImpersonationRules() {
    }

    public static boolean qualifies(
        final boolean hood,
        final boolean robe,
        final boolean footwear,
        final boolean hand
    ) {
        return hood && robe && footwear && hand;
    }

    public static boolean isComplete(final LivingEntity entity) {
        return qualifies(
            entity.getItemBySlot(EquipmentSlot.HEAD).is(WarlockeryTags.Items.DEATH_DISGUISE_ARMOR),
            entity.getItemBySlot(EquipmentSlot.CHEST).is(WarlockeryTags.Items.DEATH_DISGUISE_ARMOR),
            entity.getItemBySlot(EquipmentSlot.FEET).is(WarlockeryTags.Items.DEATH_DISGUISE_ARMOR),
            entity.getMainHandItem().is(WarlockeryTags.Items.DEATH_WEAPONS)
                || entity.getOffhandItem().is(WarlockeryTags.Items.DEATH_WEAPONS)
        );
    }
}
