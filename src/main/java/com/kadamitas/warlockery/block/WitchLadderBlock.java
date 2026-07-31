package com.kadamitas.warlockery.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class WitchLadderBlock extends LadderBlock {
    public static final EnumProperty<FetishMode> MODE = EnumProperty.create("mode", FetishMode.class);
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");
    public static final BooleanProperty ALARM = BooleanProperty.create("alarm");
    public static final BooleanProperty BOUND = BooleanProperty.create("bound");

    public WitchLadderBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(MODE, FetishMode.DISORIENTATION)
            .setValue(ENABLED, false)
            .setValue(ALARM, false)
            .setValue(BOUND, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE, ENABLED, ALARM, BOUND);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final BlockState ordinary = super.getStateForPlacement(context);
        if (ordinary == null) {
            return null;
        }
        return FetishBindingState.read(context.getItemInHand())
            .map(mode -> ordinary.setValue(MODE, mode).setValue(ENABLED, true).setValue(BOUND, true))
            .orElse(ordinary);
    }

    @Override
    protected void onPlace(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final BlockState oldState,
        final boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && state.getValue(BOUND) && state.getValue(ENABLED)) {
            level.scheduleTick(pos, this, FetishRules.TICK_INTERVAL);
        }
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
        final boolean focus = stack.is(WitchcraftCompatibilityTags.CONFIGURATION_FOCI);
        if (level.isClientSide()) {
            return focus ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (!focus || !state.getValue(BOUND)) {
            show(player, !focus ? FetishRules.Diagnostic.WRONG_FOCUS : FetishRules.Diagnostic.UNBOUND, state.getValue(MODE));
            return InteractionResult.FAIL;
        }
        final BlockState next;
        final FetishRules.Diagnostic diagnostic;
        if (player.isSecondaryUseActive()) {
            next = state.setValue(MODE, state.getValue(MODE).next()).setValue(ENABLED, true).setValue(ALARM, false);
            diagnostic = FetishRules.Diagnostic.READY;
        } else {
            final boolean enabled = !state.getValue(ENABLED);
            next = state.setValue(ENABLED, enabled).setValue(ALARM, false);
            diagnostic = enabled ? FetishRules.Diagnostic.WILL_ENABLE : FetishRules.Diagnostic.WILL_DISABLE;
        }
        level.setBlockAndUpdate(pos, next);
        if (next.getValue(ENABLED)) {
            level.scheduleTick(pos, this, FetishRules.TICK_INTERVAL);
        }
        show(player, diagnostic, next.getValue(MODE));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void tick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
        if (!state.getValue(BOUND) || !state.getValue(ENABLED)) {
            return;
        }
        final boolean alarm = FetishRuntime.tick(level, pos, state.getValue(MODE), true);
        if (alarm != state.getValue(ALARM)) {
            level.setBlockAndUpdate(pos, state.setValue(ALARM, alarm));
            level.updateNeighborsAt(pos, this);
        }
        level.scheduleTick(pos, this, FetishRules.TICK_INTERVAL);
    }

    @Override
    protected boolean isSignalSource(final BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(final BlockState state, final BlockGetter level, final BlockPos pos, final Direction direction) {
        return state.getValue(ENABLED)
            && state.getValue(MODE) == FetishMode.SHRIEKING
            && state.getValue(ALARM) ? 15 : 0;
    }

    @Override
    protected java.util.List<ItemStack> getDrops(
        final BlockState state,
        final net.minecraft.world.level.storage.loot.LootParams.Builder builder
    ) {
        final ItemStack drop = new ItemStack(this);
        if (state.getValue(BOUND)) {
            FetishBindingState.write(drop, state.getValue(MODE));
        }
        return java.util.List.of(drop);
    }

    public static boolean isActiveProtection(final BlockState state) {
        return state.getBlock() instanceof WitchLadderBlock
            && state.getValue(BOUND)
            && state.getValue(ENABLED)
            && state.getValue(MODE) == FetishMode.VOODOO_PROTECTION;
    }

    private static void show(
        final Player player,
        final FetishRules.Diagnostic diagnostic,
        final FetishMode mode
    ) {
        final String key = switch (diagnostic) {
            case UNBOUND -> "message.warlockery.fetish.unbound";
            case DISABLED -> "message.warlockery.fetish.disabled";
            case WRONG_FOCUS -> "message.warlockery.fetish.wrong_focus";
            case WILL_ENABLE -> "message.warlockery.fetish.enabled";
            case WILL_DISABLE -> "message.warlockery.fetish.disabled";
            case READY -> "message.warlockery.fetish.ready";
            case ALARM -> "message.warlockery.fetish.alarm";
        };
        final ChatFormatting color = switch (diagnostic) {
            case READY, WILL_ENABLE -> ChatFormatting.GREEN;
            case DISABLED, WILL_DISABLE -> ChatFormatting.YELLOW;
            case UNBOUND, WRONG_FOCUS, ALARM -> ChatFormatting.RED;
        };
        player.sendOverlayMessage(Component.translatable(
            key,
            Component.translatable("fetish_mode.warlockery." + mode.getSerializedName())
        ).withStyle(color));
    }
}
