package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class BloodGobletItem extends Item {
    public BloodGobletItem(final Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        if (!BloodGobletState.isFull(stack)) {
            if (target.level() instanceof ServerLevel level) {
                target.hurtServer(level, target.damageSources().playerAttack(player), 2.0F);
                SympatheticBinding.from(target).write(stack);
                BloodGobletState.setFull(stack, true);
                show(player, UtilityDecision.success("filled"));
            }
            return InteractionResult.SUCCESS;
        }
        if (SupernaturalState.getForm(player) != SupernaturalForm.VAMPIRE) {
            show(player, UtilityDecision.failure("vampire_required"));
            return InteractionResult.FAIL;
        }
        if (!player.level().isClientSide()) {
            if (target instanceof Player targetPlayer) {
                SupernaturalState.setForm(targetPlayer, SupernaturalForm.VAMPIRE);
            } else {
                target.getPersistentData().putBoolean("WarlockeryVampireConverted", true);
                target.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 6_000, 0));
                target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 0));
            }
            BloodGobletState.setFull(stack, false);
            show(player, UtilityDecision.success("converted"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        final UtilityDecision decision = BloodGobletRules.drink(
            BloodGobletState.isFull(stack),
            SupernaturalState.getForm(player)
        );
        if (!decision.success()) {
            show(player, decision);
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            player.heal(6.0F);
            SupernaturalState.addReserve(player, 30);
            BloodGobletState.setFull(stack, false);
            show(player, decision);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return BloodGobletState.isFull(stack) || super.isFoil(stack);
    }

    private static void show(final Player player, final UtilityDecision decision) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable(decision.messageKey("blood_goblet"))
                .withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
