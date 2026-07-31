package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.item.SunlightRules;
import com.kadamitas.warlockery.item.UtilityDecision;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class SunCollectorBlock extends Block {
    public static final IntegerProperty STRENGTH = BlockStateProperties.POWER;
    private static final int SAMPLE_INTERVAL = 20;

    public SunCollectorBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STRENGTH, 0));
    }

    @Override
    protected InteractionResult useItemOn(
        final ItemStack stack,
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final InteractionHand hand,
        final BlockHitResult hit
    ) {
        final UtilityDecision decision = SunlightRules.collector(
            stack.is(WarlockeryTags.Items.SOLAR_CHARGEABLES),
            SunCollectorRules.canCollect(state.getValue(STRENGTH)),
            level.canSeeSky(pos.above())
        );
        if (!decision.success()) {
            show(player, decision);
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            final ItemStack charged = stack.transmuteCopy(ModItems.ALL.get("sungrenade").get(), stack.getCount());
            CustomData.update(DataComponents.CUSTOM_DATA, charged,
                data -> data.putInt("WarlockerySunlightStrength", state.getValue(STRENGTH)));
            player.setItemInHand(hand, charged);
            level.setBlockAndUpdate(pos, state.setValue(STRENGTH, 0));
            show(player, decision);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final BlockHitResult hit
    ) {
        show(player, SunlightRules.collector(
            false,
            SunCollectorRules.canCollect(state.getValue(STRENGTH)),
            level.canSeeSky(pos.above())
        ));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final BlockState oldState,
        final boolean movedByPiston
    ) {
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, SAMPLE_INTERVAL);
        }
    }

    @Override
    protected void tick(
        final BlockState state,
        final ServerLevel level,
        final BlockPos pos,
        final RandomSource random
    ) {
        final int next = SunCollectorRules.nextStrength(
            state.getValue(STRENGTH),
            adjacentDetectorStrength(level, pos),
            level.getOverworldClockTime(),
            level.canSeeSky(pos.above())
        );
        if (next != state.getValue(STRENGTH)) {
            level.setBlockAndUpdate(pos, state.setValue(STRENGTH, next));
        }
        level.scheduleTick(pos, this, SAMPLE_INTERVAL);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STRENGTH);
    }

    private static int adjacentDetectorStrength(final Level level, final BlockPos pos) {
        return java.util.Arrays.stream(Direction.values())
            .map(pos::relative)
            .map(level::getBlockState)
            .filter(state -> state.is(Blocks.DAYLIGHT_DETECTOR))
            .mapToInt(state -> state.getValue(BlockStateProperties.POWER))
            .max()
            .orElse(0);
    }

    private static void show(final Player player, final UtilityDecision decision) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable(decision.messageKey("sun_collector"))
                .withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
