package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.block.PlantMineRules.Diagnostic;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class PlantMineBlock extends BushBlock {
    public static final EnumProperty<PlantMinePayload> PAYLOAD = EnumProperty.create("payload", PlantMinePayload.class);

    public PlantMineBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PAYLOAD, PlantMinePayload.UNARMED));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PAYLOAD);
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
        final PlantMinePayload current = state.getValue(PAYLOAD);
        final var offered = PlantMinePayload.from(itemStack);
        final Diagnostic diagnostic = PlantMineRules.diagnostic(current, offered, false);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (diagnostic == Diagnostic.WRONG) {
            show(player, diagnostic, current);
            return InteractionResult.FAIL;
        }
        if (current.isArmed()) {
            show(player, Diagnostic.READY, current);
            return InteractionResult.SUCCESS;
        }
        final PlantMinePayload payload = offered.orElseThrow();
        level.setBlockAndUpdate(pos, state.setValue(PAYLOAD, payload));
        if (!player.hasInfiniteMaterials()) {
            itemStack.shrink(1);
        }
        show(player, Diagnostic.READY, payload);
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
            final PlantMinePayload payload = state.getValue(PAYLOAD);
            show(player, PlantMineRules.diagnostic(payload, java.util.Optional.empty(), true), payload);
        }
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
        final PlantMinePayload payload = state.getValue(PAYLOAD);
        if (!PlantMineRules.canTrigger(
            payload,
            entity instanceof LivingEntity,
            entity.isAlive(),
            entity.typeHolder().is(WarlockeryTags.EntityTypes.PLANT_MINE_IMMUNE),
            entity instanceof Player player && player.isSpectator()
        )) {
            return;
        }
        if (entity instanceof Player player) {
            player.sendOverlayMessage(Component.translatable(
                "message.warlockery.plant_mine.triggered",
                payloadName(payload)
            ).withStyle(ChatFormatting.GOLD));
        }
        PlantMineEffects.activate(serverLevel, pos, payload);
        serverLevel.destroyBlock(pos, false);
    }

    private static void show(
        final Player player,
        final Diagnostic diagnostic,
        final PlantMinePayload payload
    ) {
        final String key = switch (diagnostic) {
            case UNARMED -> "message.warlockery.plant_mine.unarmed";
            case WRONG -> "message.warlockery.plant_mine.wrong";
            case READY -> "message.warlockery.plant_mine.ready";
        };
        final ChatFormatting color = switch (diagnostic) {
            case UNARMED -> ChatFormatting.YELLOW;
            case WRONG -> ChatFormatting.RED;
            case READY -> ChatFormatting.GREEN;
        };
        player.sendOverlayMessage(Component.translatable(key, payloadName(payload)).withStyle(color));
    }

    private static Component payloadName(final PlantMinePayload payload) {
        return Component.translatable("plant_mine_payload.warlockery." + payload.getSerializedName());
    }
}
