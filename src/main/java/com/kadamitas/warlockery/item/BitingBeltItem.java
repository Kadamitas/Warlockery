package com.kadamitas.warlockery.item;

import java.util.Optional;
import java.util.stream.StreamSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

public final class BitingBeltItem extends Item {
    public BitingBeltItem(final Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack belt = player.getItemInHand(hand);
        final ItemStack source = player.getItemInHand(hand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND);
        final PotionContents contents = source.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        final var effects = StreamSupport.stream(contents.getAllEffects().spliterator(), false).toList();
        if (effects.isEmpty()) {
            show(player, UtilityDecision.failure("missing_potion"));
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            final Optional<BitingBeltState.StoredEffect> helpful = effects.stream()
                .filter(effect -> effect.getEffect().value().isBeneficial())
                .findFirst()
                .map(BitingBeltItem::store);
            final Optional<BitingBeltState.StoredEffect> harmful = effects.stream()
                .filter(effect -> !effect.getEffect().value().isBeneficial())
                .findFirst()
                .map(BitingBeltItem::store);
            new BitingBeltState(helpful, harmful).write(belt);
            if (!player.hasInfiniteMaterials()) {
                source.shrink(1);
            }
            show(player, UtilityDecision.success("effects_stored"));
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean applyHelpful(final ItemStack belt, final LivingEntity wearer) {
        return BitingBeltState.read(belt).helpful().flatMap(BitingBeltState.StoredEffect::resolve)
            .map(wearer::addEffect).orElse(false);
    }

    public static boolean applyHarmful(final ItemStack belt, final LivingEntity attacker) {
        return BitingBeltState.read(belt).harmful().flatMap(BitingBeltState.StoredEffect::resolve)
            .map(attacker::addEffect).orElse(false);
    }

    private static BitingBeltState.StoredEffect store(final MobEffectInstance effect) {
        return new BitingBeltState.StoredEffect(
            BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()),
            effect.getDuration(),
            effect.getAmplifier()
        );
    }

    private static void show(final Player player, final UtilityDecision decision) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable(decision.messageKey("biting_belt"))
                .withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
