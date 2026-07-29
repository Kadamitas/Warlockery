package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.item.ResourceCompatibilityTags;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.ModItems;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class BloodPoppyBlock extends BushBlock {
    private static final String SAMPLE_POSITION = "WarlockeryBloodPoppyPosition";
    private static final String SAMPLE_TIME = "WarlockeryBloodPoppyTime";

    public BloodPoppyBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void entityInside(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Entity entity,
        final InsideBlockEffectApplier effectApplier,
        final boolean isPrecise
    ) {
        entity.makeStuckInBlock(state, new Vec3(0.45, 0.8, 0.45));
        if (entity instanceof LivingEntity living && !level.isClientSide()) {
            living.getPersistentData().putLong(SAMPLE_POSITION, pos.asLong());
            living.getPersistentData().putLong(SAMPLE_TIME, level.getGameTime());
        }
    }

    @Override
    protected InteractionResult useItemOn(
        final ItemStack stack,
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final InteractionHand hand,
        final BlockHitResult hitResult
    ) {
        if (!stack.is(ModItems.ALL.get("sympathetic_vial").get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        final Optional<LivingEntity> victim = recentVictim(serverLevel, pos);
        if (victim.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.blood_poppy.missing_sample"));
            return InteractionResult.FAIL;
        }
        final LivingEntity target = victim.orElseThrow();
        SympatheticBinding.from(target).write(stack);
        stack.set(DataComponents.LORE, new ItemLore(java.util.List.of(
            Component.translatable("tooltip.warlockery.blood_poppy.sample", target.getDisplayName())
        )));
        player.sendOverlayMessage(Component.translatable(
            "message.warlockery.blood_poppy.collected",
            target.getDisplayName()
        ));
        return InteractionResult.SUCCESS;
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
            final boolean ready = level instanceof ServerLevel serverLevel && recentVictim(serverLevel, pos).isPresent();
            player.sendOverlayMessage(Component.translatable(ready
                ? "message.warlockery.blood_poppy.ready"
                : "message.warlockery.blood_poppy.missing_sample"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void playerDestroy(
        final Level level,
        final Player player,
        final BlockPos pos,
        final BlockState state,
        final @Nullable BlockEntity blockEntity,
        final ItemStack destroyedWith
    ) {
        if (destroyedWith.is(ResourceCompatibilityTags.Items.SAFE_MAGICAL_PLANT_TOOLS)) {
            super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
            return;
        }
        player.causeFoodExhaustion(0.05F);
        if (level instanceof ServerLevel serverLevel) {
            player.hurtServer(serverLevel, player.damageSources().sweetBerryBush(), 2.0F);
            player.sendOverlayMessage(Component.translatable("message.warlockery.blood_poppy.unsafe_harvest"));
        }
    }

    private static Optional<LivingEntity> recentVictim(final ServerLevel level, final BlockPos pos) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(2.0), entity -> {
            final long markedPosition = entity.getPersistentData().getLongOr(SAMPLE_POSITION, Long.MIN_VALUE);
            final long markedTime = entity.getPersistentData().getLongOr(SAMPLE_TIME, -1L);
            return markedPosition == pos.asLong() && BloodPoppyRules.sampleIsFresh(level.getGameTime(), markedTime);
        }).stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(Vec3.atCenterOf(pos))));
    }
}
