package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BearTrapBlock extends Block {
    public static final MapCodec<BearTrapBlock> CODEC = simpleCodec(BearTrapBlock::new);
    public static final EnumProperty<BearTrapState> TRAP_STATE = EnumProperty.create("trap_state", BearTrapState.class);
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0);

    public BearTrapBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TRAP_STATE, BearTrapState.DISARMED));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TRAP_STATE);
    }

    @Override
    protected VoxelShape getShape(
        final BlockState state,
        final BlockGetter level,
        final BlockPos pos,
        final CollisionContext context
    ) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final BlockHitResult hitResult
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final BearTrapState current = state.getValue(TRAP_STATE);
        final BearTrapState next = BearTrapRules.nextState(current);
        level.setBlockAndUpdate(pos, state.setValue(TRAP_STATE, next));
        level.playSound(
            null,
            pos,
            next == BearTrapState.ARMED ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
            SoundSource.BLOCKS,
            0.8F,
            next == BearTrapState.ARMED ? 1.2F : 0.8F
        );
        final String key = switch (next) {
            case ARMED -> current == BearTrapState.SPRUNG
                ? "message.warlockery.beartrap.reset"
                : "message.warlockery.beartrap.armed";
            case DISARMED -> "message.warlockery.beartrap.disarmed";
            case SPRUNG -> "message.warlockery.beartrap.sprung";
        };
        player.sendOverlayMessage(Component.translatable(key).withStyle(
            next == BearTrapState.ARMED ? ChatFormatting.GREEN : ChatFormatting.YELLOW
        ));
        return InteractionResult.SUCCESS;
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
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        final BearTrapState trapState = state.getValue(TRAP_STATE);
        final boolean living = entity instanceof LivingEntity;
        final boolean immune = entity.typeHolder().is(WarlockeryTags.EntityTypes.BEARTRAP_IMMUNE);
        final boolean spectator = entity instanceof Player player && player.isSpectator();
        if (BearTrapRules.canTrigger(trapState, living, entity.isAlive(), immune, spectator)
            && entity instanceof LivingEntity target) {
            serverLevel.setBlockAndUpdate(pos, state.setValue(TRAP_STATE, BearTrapState.SPRUNG));
            target.hurtServer(serverLevel, serverLevel.damageSources().generic(), 6.0F);
            restrain(target);
            serverLevel.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.65F);
            serverLevel.sendParticles(
                ParticleTypes.CRIT,
                pos.getX() + 0.5,
                pos.getY() + 0.2,
                pos.getZ() + 0.5,
                12,
                0.35,
                0.15,
                0.35,
                0.05
            );
            if (target instanceof Player player) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.beartrap.triggered")
                    .withStyle(ChatFormatting.RED));
            }
            return;
        }
        if (BearTrapRules.canRestrain(trapState, living, entity.isAlive(), immune, spectator)
            && entity instanceof LivingEntity target) {
            restrain(target);
        }
    }

    private static void restrain(final LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 5, true, true));
        target.setDeltaMovement(target.getDeltaMovement().multiply(0.05, 1.0, 0.05));
    }
}
