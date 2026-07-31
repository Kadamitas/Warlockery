package com.kadamitas.warlockery.ritual.hex;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class HeatMetalRules {
    private static final List<EquipmentSlot> WORN = List.of(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    );

    private HeatMetalRules() {
    }

    public static boolean carriesAffectedMetal(final LivingEntity target) {
        return affected(target.getMainHandItem())
            || affected(target.getOffhandItem())
            || WORN.stream().map(target::getItemBySlot).anyMatch(HeatMetalRules::affected);
    }

    public static boolean affected(final ItemStack stack) {
        return stack.is(WarlockeryTags.Items.HEAT_METAL_EQUIPMENT)
            && !stack.is(WarlockeryTags.Items.HEAT_METAL_EXEMPT);
    }
}
