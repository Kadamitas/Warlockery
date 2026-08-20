package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
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
        if (level.isClientSide()) {
            return itemStack.getItem() instanceof BucketItem || itemStack.is(Items.BUCKET)
                ? InteractionResult.SUCCESS
                : InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        return machine.getCapability(ForgeCapabilities.FLUID_HANDLER, hitResult.getDirection())
            .map(handler -> {
                final InteractionResult result = transferBucket(itemStack, handler);
                if (result.consumesAction() && "kettle".equals(machine.machineKind())) {
                    machine.claimKettleBrewer(player);
                }
                return result;
            })
            .orElse(InteractionResult.TRY_WITH_EMPTY_HAND);
    }

    private static InteractionResult useBrazier(
        final ItemStack stack,
        final Level level,
        final Player player,
        final InteractionHand hand,
        final MagicMachineBlockEntity machine
    ) {
        final boolean waterBottle = isWaterBottle(stack);
        if (!stack.is(WarlockeryTags.Items.BRAZIER_IGNITERS)
            && !stack.is(Items.WATER_BUCKET)
            && !waterBottle) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.WATER_BUCKET) || waterBottle) {
            final int cleared = machine.extinguishBrazier();
            if (cleared < 0) {
                return InteractionResult.FAIL;
            }
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                "message.warlockery.brazier.extinguished",
                cleared
            ));
            return InteractionResult.SUCCESS_SERVER.heldItemTransformedTo(new ItemStack(
                waterBottle ? Items.GLASS_BOTTLE : Items.BUCKET
            ));
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

    private static boolean isWaterBottle(final ItemStack stack) {
        return stack.is(Items.POTION)
            && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
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
            level.playSound(null, pos, ModSounds.MACHINE_OPEN.get(), SoundSource.BLOCKS, 0.45F, 1.0F);
            player.awardStat(Stats.INTERACT_WITH_FURNACE);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        final double x = pos.getX() + 0.5D;
        final double y = pos.getY() + 0.85D;
        final double z = pos.getZ() + 0.5D;
        if (level.getBlockEntity(pos) instanceof MagicMachineBlockEntity machine
            && "spinningwheel".equals(machine.machineKind())) {
            for (int index = 0; index < 3; index++) {
                final double angle = (level.getGameTime() + index * 7) * 0.25D;
                level.addParticle(ParticleTypes.ENCHANT, x + Math.cos(angle) * 0.35D, y, z + Math.sin(angle) * 0.35D, 0, 0, 0);
            }
            return;
        }
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.025D, 0);
        if (random.nextBoolean()) {
            level.addParticle(ParticleTypes.FLAME, x, y - 0.1D, z, 0, 0.012D, 0);
        }
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
    protected void affectNeighborsAfterRemoval(
        final BlockState state,
        final ServerLevel level,
        final BlockPos pos,
        final boolean moved
    ) {
        if (level.getBlockEntity(pos) instanceof MagicMachineBlockEntity machine) {
            net.minecraft.world.Containers.dropContents(level, pos, machine);
            machine.dropLegacyOverflow(level, pos);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
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
