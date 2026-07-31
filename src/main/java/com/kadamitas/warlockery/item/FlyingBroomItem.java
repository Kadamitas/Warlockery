package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.BroomEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class FlyingBroomItem extends Item implements GlyphClearingTool {
    private static final String LEGACY_ACTIVE = "WarlockeryBroomFlight";
    private static final String LEGACY_PREVIOUS_MAY_FLY = "WarlockeryBroomPreviousMayFly";
    private static final String LEGACY_PREVIOUS_SPEED = "WarlockeryBroomPreviousSpeed";

    public FlyingBroomItem(final Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return true;
    }

    @Override
    public int glyphRadius() {
        return 2;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return player.isSecondaryUseActive()
            ? clearGlyphs(context)
            : use(context.getLevel(), player, context.getHand());
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        cleanLegacyFlight(serverPlayer);
        if (player.getVehicle() instanceof BroomEntity broom) {
            player.stopRiding();
            broom.discard();
            serverPlayer.sendOverlayMessage(Component.translatable("message.warlockery.broom.disabled")
                .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        if (player.isPassenger()) {
            return InteractionResult.FAIL;
        }
        final ItemStack carried = player.getItemInHand(hand);
        final BroomEntity broom = new BroomEntity(ModEntities.BROOM.get(), serverLevel);
        broom.snapTo(player.getX(), player.getY() + 0.1D, player.getZ(), player.getYRot(), player.getXRot());
        broom.takeBroom(carried, hand);
        broom.setDeltaMovement(player.getLookAngle().scale(0.16D));
        player.setItemInHand(hand, ItemStack.EMPTY);
        if (!serverLevel.addFreshEntity(broom)) {
            broom.returnBroomTo(player);
            return InteractionResult.FAIL;
        }
        if (!player.startRiding(broom, true, true)) {
            broom.returnBroomTo(player);
            return InteractionResult.FAIL;
        }
        serverPlayer.sendOverlayMessage(Component.translatable("message.warlockery.broom.enabled")
            .withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    public static void setControls(
        final ServerPlayer player,
        final FlyingBroomRules.ControlInput input,
        final boolean gliding
    ) {
        if (player.getVehicle() instanceof BroomEntity broom && broom.getControllingPassenger() == player) {
            broom.setControlInput(input);
            broom.setGliding(gliding);
        }
    }

    public static boolean isFlying(final Player player) {
        return player.getVehicle() instanceof BroomEntity broom && broom.getControllingPassenger() == player;
    }

    public static void handleLogin(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cleanLegacyFlight(player);
        }
    }

    public static void handleLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        cleanLegacyFlight(player);
        if (player.getVehicle() instanceof BroomEntity broom) {
            player.stopRiding();
            broom.discard();
        }
    }

    public static void handleDeath(final LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getVehicle() instanceof BroomEntity broom) {
            broom.returnBroomTo(player);
        }
    }

    private static void cleanLegacyFlight(final ServerPlayer player) {
        final var data = player.getPersistentData();
        if (data.getBooleanOr(LEGACY_ACTIVE, false)) {
            final boolean privileged = player.isCreative() || player.isSpectator();
            final boolean batFlight = SupernaturalProgression.batSwarmUntil(player) > player.level().getGameTime();
            final boolean previous = data.getBooleanOr(LEGACY_PREVIOUS_MAY_FLY, false);
            player.getAbilities().mayfly = privileged || batFlight || previous;
            if (!player.getAbilities().mayfly) {
                player.getAbilities().flying = false;
            }
            if (data.contains(LEGACY_PREVIOUS_SPEED)) {
                player.getAbilities().setFlyingSpeed(data.getFloatOr(LEGACY_PREVIOUS_SPEED, 0.05F));
            }
            player.onUpdateAbilities();
        }
        data.remove(LEGACY_ACTIVE);
        data.remove(LEGACY_PREVIOUS_MAY_FLY);
        data.remove(LEGACY_PREVIOUS_SPEED);
    }
}
