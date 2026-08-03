package com.kadamitas.warlockery.mixin;

import com.kadamitas.warlockery.fabric.WarlockeryFabricEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void warlockery$modifyDestroySpeed(
        final BlockState state,
        final CallbackInfoReturnable<Float> callback
    ) {
        final Player player = (Player) (Object) this;
        final float speed = player.level().isClientSide()
            ? com.kadamitas.warlockery.client.ClientSupernaturalState.modifyBreakSpeed(
                player,
                state,
                callback.getReturnValue()
            )
            : WarlockeryFabricEvents.dispatchBreakSpeed(player, state, callback.getReturnValue());
        callback.setReturnValue(speed);
    }
}
