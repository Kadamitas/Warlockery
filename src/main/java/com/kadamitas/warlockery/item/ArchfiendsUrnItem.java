package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.brew.BrewFactory;
import com.kadamitas.warlockery.brew.BrewItem;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.brew.BrewRuntime;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ArchfiendsUrnItem extends Item {
    private static final int CAST_COOLDOWN_TICKS = 60;
    private static final double CAST_RANGE = 24.0D;

    public ArchfiendsUrnItem(final Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack urn = player.getItemInHand(hand);
        final ItemStack offered = player.getItemInHand(other(hand));
        if (player.isSecondaryUseActive() && offered.getItem() instanceof BrewItem brewItem) {
            return store(level, player, offered, urn, brewItem.kind());
        }
        final ArchfiendsUrnState state = ArchfiendsUrnState.read(urn);
        if (state.brews().isEmpty()) {
            show(player, getName(urn), false);
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            final var hit = player.pick(CAST_RANGE, 0.0F, false);
            state.resolvedBrews().forEach(brew -> cast(serverLevel, player, brew, hit.getLocation()));
            player.getCooldowns().addCooldown(urn, CAST_COOLDOWN_TICKS);
            show(player, getName(urn), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return !ArchfiendsUrnState.read(stack).brews().isEmpty() || super.isFoil(stack);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(
        final ItemStack stack,
        final TooltipContext context,
        final TooltipDisplay display,
        final Consumer<Component> builder,
        final TooltipFlag flag
    ) {
        ArchfiendsUrnState.read(stack).resolvedBrews().stream()
            .map(BrewFactory::itemId)
            .map(id -> Component.translatable("item.warlockery." + id).withStyle(ChatFormatting.DARK_PURPLE))
            .forEach(builder);
    }

    private static InteractionResult store(
        final Level level,
        final Player player,
        final ItemStack offered,
        final ItemStack urn,
        final BrewKind brew
    ) {
        final ArchfiendsUrnState.AddResult result = ArchfiendsUrnState.read(urn).add(brew);
        if (!result.changed()) {
            show(player, Component.translatable("item.warlockery." + BrewFactory.itemId(brew)), false);
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            result.state().write(urn);
            offered.consume(1, player);
            show(player, Component.translatable("item.warlockery." + BrewFactory.itemId(brew)), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionHand other(final InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static void cast(
        final ServerLevel level,
        final Player player,
        final BrewKind brew,
        final Vec3 center
    ) {
        BrewRuntime.handleImpact(level, brew, center, player, player);
        final double radius = brew.radius();
        final AABB area = AABB.ofSize(center, radius * 2.0D, radius * 1.5D, radius * 2.0D);
        level.getEntitiesOfClass(
            LivingEntity.class,
            area,
            target -> target.isAlive()
                && target.isAffectedByPotions()
                && target.distanceToSqr(center) <= radius * radius
        ).forEach(target -> brew.effects().stream()
            .map(effect -> effect.resolve())
            .forEach(effect -> applyEffect(level, player, target, effect)));
    }

    private static void applyEffect(
        final ServerLevel level,
        final Player player,
        final LivingEntity target,
        final MobEffectInstance effect
    ) {
        if (effect.getEffect().value().isInstantaneous()) {
            effect.getEffect().value().applyInstantaneousEffect(
                level,
                player,
                player,
                target,
                effect.getAmplifier(),
                1.0D
            );
            return;
        }
        target.addEffect(new MobEffectInstance(effect), player);
    }

    private static void show(final Player player, final Component subject, final boolean success) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.empty()
                .append(success ? Component.literal("✓ ") : Component.literal("✗ "))
                .append(subject)
                .withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
