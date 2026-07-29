package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jspecify.annotations.Nullable;

public final class MagicMachineBlock extends BaseEntityBlock {
    public static final MapCodec<MagicMachineBlock> CODEC = simpleCodec(MagicMachineBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public MagicMachineBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(final BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MagicMachineBlockEntity(pos, state);
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
        if (!(level.getBlockEntity(pos) instanceof MagicMachineBlockEntity machine)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if ("brazier".equals(machine.machineKind())) {
            return useBrazier(itemStack, level, player, hand, machine);
        }
        if (!machine.machineProfile().supportsFluids()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return itemStack.getItem() instanceof BucketItem || itemStack.is(Items.BUCKET)
                ? InteractionResult.SUCCESS
                : InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        return machine.getCapability(ForgeCapabilities.FLUID_HANDLER, hitResult.getDirection())
            .map(handler -> transferBucket(itemStack, handler))
            .orElse(InteractionResult.TRY_WITH_EMPTY_HAND);
    }

    private static InteractionResult useBrazier(
        final ItemStack stack,
        final Level level,
        final Player player,
        final InteractionHand hand,
        final MagicMachineBlockEntity machine
    ) {
        if (!stack.is(WarlockeryTags.Items.BRAZIER_IGNITERS) && !stack.is(Items.WATER_BUCKET)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.WATER_BUCKET)) {
            final int cleared = machine.extinguishBrazier();
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                "message.warlockery.brazier.extinguished",
                cleared
            ));
            return InteractionResult.SUCCESS_SERVER.heldItemTransformedTo(new ItemStack(Items.BUCKET));
        }
        if (!machine.igniteBrazier()) {
            return InteractionResult.FAIL;
        }
        if (stack.is(Items.FIRE_CHARGE)) {
            stack.consume(1, player);
        } else if (stack.isDamageableItem()) {
            stack.hurtAndBreak(1, player, hand);
        }
        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.warlockery.brazier.ignited"));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult transferBucket(final ItemStack itemStack, final IFluidHandler handler) {
        if (itemStack.getItem() instanceof BucketItem bucket && bucket.getFluid() != net.minecraft.world.level.material.Fluids.EMPTY) {
            final FluidStack fluid = new FluidStack(bucket.getFluid(), FluidType.BUCKET_VOLUME);
            if (handler.fill(fluid, IFluidHandler.FluidAction.SIMULATE) == FluidType.BUCKET_VOLUME) {
                handler.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
                return InteractionResult.SUCCESS_SERVER.heldItemTransformedTo(new ItemStack(Items.BUCKET));
            }
            return InteractionResult.FAIL;
        }
        if (itemStack.is(Items.BUCKET)) {
            final FluidStack fluid = handler.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE);
            if (fluid.getAmount() == FluidType.BUCKET_VOLUME && fluid.getFluid().getBucket() != Items.AIR) {
                handler.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.EXECUTE);
                return InteractionResult.SUCCESS_SERVER.heldItemTransformedTo(new ItemStack(fluid.getFluid().getBucket()));
            }
            return InteractionResult.FAIL;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final BlockHitResult hitResult
    ) {
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof MagicMachineBlockEntity machine) {
            player.openMenu(machine);
            player.awardStat(Stats.INTERACT_WITH_FURNACE);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(final BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final net.minecraft.core.Direction direction
    ) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        final Level level,
        final BlockState state,
        final BlockEntityType<T> type
    ) {
        return level.isClientSide()
            ? null
            : createTickerHelper(type, ModBlockEntities.MAGIC_MACHINE.get(), MagicMachineBlockEntity::serverTick);
    }
}
