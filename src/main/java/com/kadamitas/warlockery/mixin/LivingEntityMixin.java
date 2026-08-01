package com.kadamitas.warlockery.mixin;

import com.kadamitas.warlockery.fabric.WarlockeryFabricEvents;
import com.kadamitas.warlockery.fabric.event.LivingDamageContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Unique
    private boolean warlockery$redispatchingDamage;
    @Unique
    private List<ItemEntity> warlockery$deathDrops;

    @Inject(method = "tick", at = @At("TAIL"))
    private void warlockery$tickRuntime(final CallbackInfo callback) {
        WarlockeryFabricEvents.dispatchLivingTick((LivingEntity) (Object) this);
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void warlockery$modifyDamage(
        final ServerLevel level,
        final DamageSource source,
        final float amount,
        final CallbackInfoReturnable<Boolean> callback
    ) {
        if (warlockery$redispatchingDamage) {
            return;
        }
        final LivingEntity entity = (LivingEntity) (Object) this;
        final LivingDamageContext context = WarlockeryFabricEvents.dispatchDamage(entity, source, amount);
        if (context.isCanceled()) {
            callback.setReturnValue(false);
            return;
        }
        if (Float.compare(context.getAmount(), amount) == 0) {
            return;
        }
        warlockery$redispatchingDamage = true;
        try {
            callback.setReturnValue(entity.hurtServer(level, source, context.getAmount()));
        } finally {
            warlockery$redispatchingDamage = false;
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void warlockery$beginCollectingDrops(
        final ServerLevel level,
        final DamageSource source,
        final CallbackInfo callback
    ) {
        warlockery$deathDrops = new ArrayList<>();
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void warlockery$collectDeathDrop(
        final ItemStack stack,
        final boolean randomOffset,
        final boolean includeThrower,
        final CallbackInfoReturnable<ItemEntity> callback
    ) {
        if (warlockery$deathDrops == null || stack.isEmpty()) {
            return;
        }
        final LivingEntity entity = (LivingEntity) (Object) this;
        final ItemEntity drop = new ItemEntity(
            entity.level(),
            entity.getX(),
            entity.getY() + 0.25,
            entity.getZ(),
            stack.copy()
        );
        drop.setDefaultPickUpDelay();
        warlockery$deathDrops.add(drop);
        callback.setReturnValue(drop);
    }

    @Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
    private void warlockery$finishCollectingDrops(
        final ServerLevel level,
        final DamageSource source,
        final CallbackInfo callback
    ) {
        final List<ItemEntity> drops = warlockery$deathDrops;
        warlockery$deathDrops = null;
        if (drops == null) {
            return;
        }
        final LivingEntity entity = (LivingEntity) (Object) this;
        WarlockeryFabricEvents.dispatchDrops(entity, source, drops);
        drops.stream().filter(drop -> !drop.getItem().isEmpty()).forEach(level::addFreshEntity);
    }

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void warlockery$finishItemUse(final CallbackInfo callback) {
        final LivingEntity entity = (LivingEntity) (Object) this;
        WarlockeryFabricEvents.dispatchFinishedItemUse(entity, entity.getUseItem().copy());
    }

    @Inject(method = "randomTeleport", at = @At("HEAD"), cancellable = true)
    private void warlockery$blockRandomTeleport(
        final double x,
        final double y,
        final double z,
        final boolean showParticles,
        final CallbackInfoReturnable<Boolean> callback
    ) {
        if (WarlockeryFabricEvents.blocksTeleport((LivingEntity) (Object) this)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "getProjectile", at = @At("RETURN"), cancellable = true)
    private void warlockery$selectProjectile(
        final ItemStack weapon,
        final CallbackInfoReturnable<ItemStack> callback
    ) {
        callback.setReturnValue(WarlockeryFabricEvents.dispatchProjectileSelection(
            (LivingEntity) (Object) this,
            callback.getReturnValue()
        ));
    }
}
