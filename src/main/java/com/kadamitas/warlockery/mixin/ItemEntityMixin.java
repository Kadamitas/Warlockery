package com.kadamitas.warlockery.mixin;

import com.kadamitas.warlockery.item.DroppedItemBehavior;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void warlockery$tickDroppedItem(final CallbackInfo callback) {
        final ItemEntity entity = (ItemEntity) (Object) this;
        final ItemStack stack = entity.getItem();
        if (stack.getItem() instanceof DroppedItemBehavior behavior
            && behavior.tickDroppedItem(stack, entity)) {
            callback.cancel();
        }
    }
}
