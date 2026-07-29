package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.AltarChaliceRules.Diagnostic;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AltarChaliceBlock extends Block {
    public static final MapCodec<AltarChaliceBlock> CODEC = simpleCodec(AltarChaliceBlock::new);
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 12.0, 12.0);

    public AltarChaliceBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FILLED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
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
        final boolean filler = itemStack.is(WarlockeryTags.Items.CHALICE_FILLERS);
        final Diagnostic diagnostic = AltarChaliceRules.diagnostic(state.getValue(FILLED), filler, false);
        if (level.isClientSide()) {
            return filler || state.getValue(FILLED) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (diagnostic == Diagnostic.WRONG_FILLER) {
            show(player, diagnostic);
            return InteractionResult.FAIL;
        }
        if (diagnostic == Diagnostic.FILLED) {
            show(player, diagnostic);
            return InteractionResult.SUCCESS;
        }
        level.setBlockAndUpdate(pos, state.setValue(FILLED, true));
        if (!player.hasInfiniteMaterials()) {
            itemStack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8F, 0.9F);
        show(player, Diagnostic.FILLED);
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
            show(player, AltarChaliceRules.diagnostic(state.getValue(FILLED), false, true));
        }
        return InteractionResult.SUCCESS;
    }

    private static void show(final Player player, final Diagnostic diagnostic) {
        final String key = switch (diagnostic) {
            case EMPTY -> "message.warlockery.chalice.empty";
            case WRONG_FILLER -> "message.warlockery.chalice.wrong_filler";
            case CAN_FILL -> "message.warlockery.chalice.can_fill";
            case FILLED -> "message.warlockery.chalice.filled";
        };
        final ChatFormatting color = switch (diagnostic) {
            case EMPTY -> ChatFormatting.YELLOW;
            case WRONG_FILLER -> ChatFormatting.RED;
            case CAN_FILL, FILLED -> ChatFormatting.GREEN;
        };
        player.sendOverlayMessage(Component.translatable(key).withStyle(color));
    }
}
