package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.AlluringSkullRules.Diagnostic;
import com.kadamitas.warlockery.entity.EldritchWatcherEntity;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AlluringSkullBlock extends Block {
    public static final MapCodec<AlluringSkullBlock> CODEC = simpleCodec(AlluringSkullBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final int LURE_INTERVAL = 20;
    private static final int LURE_RADIUS = 16;
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 12.0, 13.0);

    public AlluringSkullBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
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
    protected InteractionResult useItemOn(
        final ItemStack itemStack,
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final InteractionHand hand,
        final BlockHitResult hitResult
    ) {
        final boolean activator = itemStack.is(WarlockeryTags.Items.ALLURING_SKULL_ACTIVATORS);
        final Diagnostic diagnostic = AlluringSkullRules.diagnostic(state.getValue(ACTIVE), activator, false);
        if (level.isClientSide()) {
            return activator ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (!activator) {
            show(player, diagnostic);
            return InteractionResult.FAIL;
        }
        final boolean active = !state.getValue(ACTIVE);
        level.setBlockAndUpdate(pos, state.setValue(ACTIVE, active));
        if (active) {
            level.scheduleTick(pos, this, LURE_INTERVAL);
        }
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.8F, active ? 1.2F : 0.7F);
        show(player, diagnostic);
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
            show(player, AlluringSkullRules.diagnostic(state.getValue(ACTIVE), false, true));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void tick(
        final BlockState state,
        final ServerLevel level,
        final BlockPos pos,
        final RandomSource random
    ) {
        if (!state.getValue(ACTIVE)) {
            return;
        }
        final var targets = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(LURE_RADIUS), mob ->
            AlluringSkullRules.canLure(
                true,
                mob.typeHolder().is(WarlockeryTags.EntityTypes.ALLURING_SKULL_TARGETS),
                mob.isAlive()
            )
        );
        targets.forEach(mob -> {
            if (mob instanceof EldritchWatcherEntity watcher) {
                watcher.acceptExternalLure(level, pos);
                return;
            }
            mob.getNavigation().moveTo(
                pos.getX() + 0.5,
                pos.getY() + 0.25,
                pos.getZ() + 0.5,
                1.0
            );
        });
        if (!targets.isEmpty()) {
            level.sendParticles(
                ParticleTypes.SOUL,
                pos.getX() + 0.5,
                pos.getY() + 0.8,
                pos.getZ() + 0.5,
                Math.min(12, targets.size() * 2),
                0.3,
                0.25,
                0.3,
                0.01
            );
        }
        level.scheduleTick(pos, this, LURE_INTERVAL);
    }

    private static void show(final Player player, final Diagnostic diagnostic) {
        final String key = switch (diagnostic) {
            case INACTIVE -> "message.warlockery.alluring_skull.inactive";
            case ACTIVE -> "message.warlockery.alluring_skull.active";
            case WRONG_FOCUS -> "message.warlockery.alluring_skull.wrong_focus";
            case WILL_ENABLE -> "message.warlockery.alluring_skull.enabled";
            case WILL_DISABLE -> "message.warlockery.alluring_skull.disabled";
        };
        final ChatFormatting color = switch (diagnostic) {
            case ACTIVE, WILL_ENABLE -> ChatFormatting.GREEN;
            case INACTIVE, WILL_DISABLE -> ChatFormatting.YELLOW;
            case WRONG_FOCUS -> ChatFormatting.RED;
        };
        player.sendOverlayMessage(Component.translatable(key).withStyle(color));
    }
}
