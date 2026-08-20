package com.kadamitas.warlockery.mixin;

import com.kadamitas.warlockery.fabric.WarlockeryFabricEvents;
import com.kadamitas.warlockery.fabric.event.LivingDamageContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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
    private List<ItemEntity> warlockery$deathDrops;

    @Inject(method = "tick", at = @At("TAIL"))
    private void warlockery$tickRuntime(final CallbackInfo callback) {
        WarlockeryFabricEvents.dispatchLivingTick((LivingEntity) (Object) this);
    }

    @WrapMethod(method = "hurtServer")
    private boolean warlockery$modifyDamage(
        final ServerLevel level,
        final DamageSource source,
        final float amount,
        final Operation<Boolean> original
    ) {
        final LivingEntity entity = (LivingEntity) (Object) this;
        final LivingDamageContext context = WarlockeryFabricEvents.dispatchDamage(entity, source, amount);
        if (context.isCanceled()) {
            return false;
        }
        return original.call(level, source, context.getAmount());
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
