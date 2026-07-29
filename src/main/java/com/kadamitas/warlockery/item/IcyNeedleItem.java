package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.ritual.hex.HexKind;
import com.kadamitas.warlockery.ritual.hex.HexState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class IcyNeedleItem extends Item {
    public IcyNeedleItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        final var nightmares = serverLevel.getEntitiesOfClass(
            Mob.class,
            new AABB(player.blockPosition()).inflate(24.0),
            entity -> entity.typeHolder().is(WarlockeryTags.EntityTypes.NIGHTMARES)
        );
        final boolean dreamEffects = player.hasEffect(MobEffects.DARKNESS)
            || player.hasEffect(MobEffects.NAUSEA)
            || player.hasEffect(MobEffects.BLINDNESS);
        final DreamWakeRules.Diagnostic diagnostic = DreamWakeRules.diagnose(
            player.isSleeping(),
            HexState.isActive(player, HexKind.WAKING_NIGHTMARE),
            dreamEffects,
            !nightmares.isEmpty()
        );
        if (diagnostic == DreamWakeRules.Diagnostic.NOT_DREAMING) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.icy_needle.not_dreaming"));
            return InteractionResult.FAIL;
        }
        if (player.isSleeping()) {
            player.stopSleeping();
        }
        HexState.remove(player, HexKind.WAKING_NIGHTMARE);
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.NAUSEA);
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.HUNGER);
        nightmares.forEach(Mob::discard);
        final ItemStack stack = player.getItemInHand(hand);
        stack.consume(1, player);
        player.sendOverlayMessage(Component.translatable("message.warlockery.icy_needle.awake"));
        return InteractionResult.SUCCESS;
    }
}
