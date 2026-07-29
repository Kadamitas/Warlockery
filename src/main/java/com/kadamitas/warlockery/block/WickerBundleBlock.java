package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.item.ResourceCompatibilityTags;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class WickerBundleBlock extends Block {
    public static final BooleanProperty BLOODIED = BooleanProperty.create("bloodied");
    private static final String STORED_BLOODIED = "WarlockeryBloodiedWicker";

    public WickerBundleBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BLOODIED, false));
    }

    @Override
    protected InteractionResult useItemOn(
        final ItemStack stack,
        final BlockState state,
        final Level level,
        final net.minecraft.core.BlockPos pos,
        final Player player,
        final InteractionHand hand,
        final BlockHitResult hitResult
    ) {
        if (!stack.is(ResourceCompatibilityTags.Items.BLOOD_SOURCES)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.literal(
                    state.getValue(BLOODIED) ? "\u2713 Wicker bundle is bloodied" : "Missing a tagged blood source"
                ));
            }
            return InteractionResult.FAIL;
        }
        if (state.getValue(BLOODIED)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.literal("\u2713 Wicker bundle is already bloodied"));
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            level.setBlockAndUpdate(pos, state.setValue(BLOODIED, true));
            stack.consume(1, player);
            player.sendOverlayMessage(Component.literal("\u2713 Bloodied wicker bundle is ready"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final net.minecraft.core.BlockPos pos,
        final Player player,
        final BlockHitResult hitResult
    ) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.literal(
                state.getValue(BLOODIED) ? "\u2713 Bloodied wicker bundle is ready" : "Missing a tagged blood source"
            ));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        final CompoundTag data = context.getItemInHand()
            .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag();
        return defaultBlockState().setValue(BLOODIED, data.getBooleanOr(STORED_BLOODIED, false));
    }

    @Override
    protected List<ItemStack> getDrops(final BlockState state, final LootParams.Builder params) {
        final List<ItemStack> drops = super.getDrops(state, params);
        if (state.getValue(BLOODIED)) {
            drops.forEach(stack -> CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                data -> data.putBoolean(STORED_BLOODIED, true)
            ));
        }
        return drops;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BLOODIED);
    }
}
