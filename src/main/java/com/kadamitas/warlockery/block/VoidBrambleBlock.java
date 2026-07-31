package com.kadamitas.warlockery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class VoidBrambleBlock extends BushBlock {
    private static final String TELEPORT_COOLDOWN = "WarlockeryVoidBrambleCooldown";

    public VoidBrambleBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(
        final Level level,
        final BlockPos pos,
        final BlockState state,
        final LivingEntity placer,
        final ItemStack placedStack
    ) {
        super.setPlacedBy(level, pos, state, placer, placedStack);
        if (level instanceof ServerLevel serverLevel && placer instanceof Player player) {
            VoidBrambleOwnershipData.get(serverLevel).claim(pos, player.getUUID());
        }
    }

    @Override
    protected float getDestroyProgress(
        final BlockState state,
        final Player player,
        final net.minecraft.world.level.BlockGetter level,
        final BlockPos pos
    ) {
        if (level instanceof ServerLevel serverLevel
            && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
            && !VoidBrambleOwnershipData.get(serverLevel).permits(pos, serverPlayer)) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected void entityInside(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Entity entity,
        final InsideBlockEffectApplier effects,
        final boolean precise
    ) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
        living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        final long cooldownUntil = living.getPersistentData().getLongOr(TELEPORT_COOLDOWN, 0L);
        if (!VoidBrambleRules.teleportReady(level.getGameTime(), cooldownUntil)) {
            return;
        }
        final int targetX = VoidBrambleRules.targetCoordinate(
            pos.getX(),
            level.getRandom().nextIntBetweenInclusive(-VoidBrambleRules.TELEPORT_RADIUS, VoidBrambleRules.TELEPORT_RADIUS)
        );
        final int targetZ = VoidBrambleRules.targetCoordinate(
            pos.getZ(),
            level.getRandom().nextIntBetweenInclusive(-VoidBrambleRules.TELEPORT_RADIUS, VoidBrambleRules.TELEPORT_RADIUS)
        );
        final int targetY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ) + 1;
        if (living.randomTeleport(targetX + 0.5, targetY, targetZ + 0.5, true)) {
            living.getPersistentData().putLong(
                TELEPORT_COOLDOWN,
                level.getGameTime() + VoidBrambleRules.TELEPORT_COOLDOWN_TICKS
            );
        }
    }

    public static boolean suppressesMagic(final Level level, final BlockPos center) {
        final int radius = VoidBrambleRules.MAGIC_SUPPRESSION_RADIUS;
        return BlockPos.betweenClosedStream(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius)
            )
            .filter(position -> VoidBrambleRules.suppressesMagic(position.distSqr(center)))
            .anyMatch(position -> level.getBlockState(position).getBlock() instanceof VoidBrambleBlock);
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final BlockHitResult hit
    ) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.void_bramble.active"));
        }
        return InteractionResult.SUCCESS;
    }
}
