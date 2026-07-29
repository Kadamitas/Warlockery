package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.item.ResourceCompatibilityTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class CritterSnareBlock extends BushBlock {
    public static final EnumProperty<CritterSnarePayload> PAYLOAD = EnumProperty.create("payload", CritterSnarePayload.class);
    private static final String STORED_CRITTER = "WarlockeryStoredCritter";

    public CritterSnareBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PAYLOAD, CritterSnarePayload.EMPTY));
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
        if (!(level instanceof ServerLevel)
            || state.getValue(PAYLOAD).occupied()
            || !entity.typeHolder().is(ResourceCompatibilityTags.EntityTypes.CRITTER_SNARE_TARGETS)) {
            return;
        }
        CritterSnarePayload.from(entity).ifPresent(payload -> {
            level.setBlockAndUpdate(pos, state.setValue(PAYLOAD, payload));
            entity.discard();
        });
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final BlockHitResult hitResult
    ) {
        final CritterSnarePayload payload = state.getValue(PAYLOAD);
        if (!payload.occupied()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.critter_snare.empty"));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            payload.create(serverLevel).ifPresent(critter -> {
                critter.snapTo(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5);
                serverLevel.addFreshEntity(critter);
            });
            level.setBlockAndUpdate(pos, state.setValue(PAYLOAD, CritterSnarePayload.EMPTY));
            player.sendOverlayMessage(Component.translatable(
                "message.warlockery.critter_snare.released",
                Component.translatable("entity.minecraft." + payload.getSerializedName())
            ));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        final CompoundTag data = context.getItemInHand()
            .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag();
        return defaultBlockState().setValue(
            PAYLOAD,
            CritterSnarePayload.byId(data.getStringOr(STORED_CRITTER, "empty"))
        );
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
        player.causeFoodExhaustion(0.005F);
        if (player.hasInfiniteMaterials()) {
            return;
        }
        final ItemStack dropped = new ItemStack(this);
        final CritterSnarePayload payload = state.getValue(PAYLOAD);
        if (payload.occupied()) {
            CustomData.update(DataComponents.CUSTOM_DATA, dropped,
                data -> data.putString(STORED_CRITTER, payload.getSerializedName()));
        }
        Block.popResource(level, pos, dropped);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PAYLOAD);
    }
}
