package com.kadamitas.warlockery.block;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class DemonHeartBlock extends Block {
    private static final Map<String, net.minecraft.core.Holder<MobEffect>> EFFECTS = Map.of(
        "hunger", MobEffects.HUNGER,
        "speed", MobEffects.SPEED,
        "health_boost", MobEffects.HEALTH_BOOST,
        "strength", MobEffects.STRENGTH,
        "nausea", MobEffects.NAUSEA,
        "regeneration", MobEffects.REGENERATION,
        "fire_resistance", MobEffects.FIRE_RESISTANCE
    );

    public DemonHeartBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final BlockHitResult hitResult
    ) {
        if (!level.isClientSide()) {
            effects().forEach(player::addEffect);
            player.igniteForSeconds(8.0F);
            level.removeBlock(pos, false);
            player.sendOverlayMessage(Component.translatable("message.warlockery.demon_heart.consumed"));
        }
        return InteractionResult.SUCCESS;
    }

    public static List<MobEffectInstance> effects() {
        return DemonHeartRules.EFFECTS.stream()
            .map(spec -> new MobEffectInstance(EFFECTS.get(spec.id()), DemonHeartRules.DURATION, spec.amplifier()))
            .toList();
    }
}
