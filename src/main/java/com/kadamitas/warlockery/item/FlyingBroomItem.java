package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.block.WitchcraftCompatibilityTags;
import com.kadamitas.warlockery.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class FlyingBroomItem extends Item implements GlyphClearingTool {
    private static final String ACTIVE = "WarlockeryBroomFlight";
    private static final String PREVIOUS_MAY_FLY = "WarlockeryBroomPreviousMayFly";
    private static final String PREVIOUS_SPEED = "WarlockeryBroomPreviousSpeed";

    public FlyingBroomItem(final Properties properties) {
        super(properties);
    }

    @Override
    public int glyphRadius() {
        return 2;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        return clearGlyphs(context);
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (isActive(serverPlayer)) {
            deactivate(serverPlayer);
            serverPlayer.sendOverlayMessage(Component.translatable("message.warlockery.broom.disabled")
                .withStyle(ChatFormatting.YELLOW));
        } else {
            activate(serverPlayer);
            serverPlayer.sendOverlayMessage(Component.translatable("message.warlockery.broom.enabled")
                .withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.SUCCESS;
    }

    public static void tickFlight(final Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !isActive(serverPlayer)) {
            return;
        }
        final ItemStack broom = heldBroom(serverPlayer);
        final boolean holding = !broom.isEmpty();
        final boolean soaring = serverPlayer.hasEffect(ModEffects.SOARING.getHolder().orElseThrow());
        final FlyingBroomRules.FlightDecision decision = FlyingBroomRules.decide(
            true,
            holding,
            serverPlayer.isCreative() || serverPlayer.isSpectator(),
            soaring
        );
        if (!decision.active()) {
            deactivate(serverPlayer);
            serverPlayer.sendOverlayMessage(Component.translatable("message.warlockery.broom.missing")
                .withStyle(ChatFormatting.RED));
            return;
        }
        final var abilities = serverPlayer.getAbilities();
        final boolean changed = !abilities.mayfly || abilities.getFlyingSpeed() != decision.speed();
        abilities.mayfly = decision.mayFly();
        abilities.setFlyingSpeed(decision.speed());
        if (changed) {
            serverPlayer.onUpdateAbilities();
        }
        if (serverPlayer.tickCount % 20 == 0) {
            final InteractionHand hand = serverPlayer.getMainHandItem() == broom
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
            broom.hurtAndBreak(1, serverPlayer, hand);
            if (broom.isEmpty()) {
                deactivate(serverPlayer);
            }
        }
    }

    private static void activate(final ServerPlayer player) {
        final var data = player.getPersistentData();
        data.putBoolean(PREVIOUS_MAY_FLY, player.getAbilities().mayfly);
        data.putFloat(PREVIOUS_SPEED, player.getAbilities().getFlyingSpeed());
        data.putBoolean(ACTIVE, true);
        player.getAbilities().mayfly = true;
        player.getAbilities().setFlyingSpeed(FlyingBroomRules.NORMAL_SPEED);
        player.onUpdateAbilities();
    }

    private static void deactivate(final ServerPlayer player) {
        final var data = player.getPersistentData();
        final boolean privileged = player.isCreative() || player.isSpectator();
        player.getAbilities().mayfly = privileged || data.getBooleanOr(PREVIOUS_MAY_FLY, false);
        if (!player.getAbilities().mayfly) {
            player.getAbilities().flying = false;
        }
        player.getAbilities().setFlyingSpeed(data.getFloatOr(PREVIOUS_SPEED, FlyingBroomRules.NORMAL_SPEED));
        data.remove(ACTIVE);
        data.remove(PREVIOUS_MAY_FLY);
        data.remove(PREVIOUS_SPEED);
        player.onUpdateAbilities();
    }

    private static boolean isActive(final ServerPlayer player) {
        return player.getPersistentData().getBooleanOr(ACTIVE, false);
    }

    private static ItemStack heldBroom(final Player player) {
        if (player.getMainHandItem().is(WitchcraftCompatibilityTags.FLYING_BROOMS)) {
            return player.getMainHandItem();
        }
        return player.getOffhandItem().is(WitchcraftCompatibilityTags.FLYING_BROOMS)
            ? player.getOffhandItem()
            : ItemStack.EMPTY;
    }
}
