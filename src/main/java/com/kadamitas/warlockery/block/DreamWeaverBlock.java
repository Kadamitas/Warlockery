package com.kadamitas.warlockery.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class DreamWeaverBlock extends Block {
    public static final MapCodec<DreamWeaverBlock> CODEC = simpleCodec(DreamWeaverBlock::new);
    public static final EnumProperty<DreamWeaverMode> MODE = EnumProperty.create("mode", DreamWeaverMode.class);

    public DreamWeaverBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MODE, DreamWeaverMode.RESTORATION));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
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
        final boolean focus = itemStack.is(WitchcraftCompatibilityTags.CONFIGURATION_FOCI);
        if (level.isClientSide()) {
            return focus ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (!focus) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.dream_weaver.wrong_focus")
                .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        final DreamWeaverMode next = state.getValue(MODE).next();
        level.setBlockAndUpdate(pos, state.setValue(MODE, next));
        show(player, next, true);
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
            show(player, state.getValue(MODE), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static void show(final Player player, final DreamWeaverMode mode, final boolean ready) {
        player.sendOverlayMessage(Component.translatable(
            ready ? "message.warlockery.dream_weaver.ready" : "message.warlockery.dream_weaver.missing",
            Component.translatable("dream_weaver_mode.warlockery." + mode.getSerializedName())
        ).withStyle(ready ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
