package com.kadamitas.warlockery.block;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class FetishBlock extends Block {
    public static final MapCodec<FetishBlock> CODEC = simpleCodec(FetishBlock::new);
    public static final EnumProperty<FetishMode> MODE = EnumProperty.create("mode", FetishMode.class);
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");
    public static final BooleanProperty ALARM = BooleanProperty.create("alarm");
    public static final BooleanProperty BOUND = BooleanProperty.create("bound");
    public static final EnumProperty<DyeColor> ROBE = EnumProperty.create("robe", DyeColor.class);

    public FetishBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(MODE, FetishMode.DISORIENTATION)
            .setValue(ENABLED, false)
            .setValue(ALARM, false)
            .setValue(BOUND, false)
            .setValue(ROBE, DyeColor.BROWN));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE, ENABLED, ALARM, BOUND, ROBE);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return FetishBindingState.read(context.getItemInHand())
            .map(mode -> defaultBlockState()
                .setValue(MODE, mode)
                .setValue(ENABLED, true)
                .setValue(BOUND, true))
            .orElse(defaultBlockState());
    }

    @Override
    protected void onPlace(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final BlockState oldState,
        final boolean movedByPiston
    ) {
        if (!level.isClientSide() && (!state.getValue(BOUND) || state.getValue(ENABLED))) {
            level.scheduleTick(pos, this, FetishRules.TICK_INTERVAL);
        }
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
        final boolean dye = itemStack.getItem() instanceof DyeItem;
        if (level.isClientSide()) {
            return focus || dye ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (itemStack.getItem() instanceof DyeItem dyeItem) {
            final String dyeId = BuiltInRegistries.ITEM.getKey(dyeItem).getPath();
            final String colorName = dyeId.endsWith("_dye") ? dyeId.substring(0, dyeId.length() - 4) : dyeId;
            level.setBlockAndUpdate(pos, state.setValue(ROBE, DyeColor.byName(colorName, DyeColor.BROWN)));
            if (!player.hasInfiniteMaterials()) {
                itemStack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        if (!focus) {
            show(player, FetishRules.Diagnostic.WRONG_FOCUS, state.getValue(MODE));
            return InteractionResult.FAIL;
        }
        if (!state.getValue(BOUND)) {
            show(player, FetishRules.Diagnostic.UNBOUND, state.getValue(MODE));
            return InteractionResult.FAIL;
        }
        final BlockState next;
        final FetishRules.Diagnostic diagnostic;
        if (player.isSecondaryUseActive()) {
            final FetishMode mode = state.getValue(MODE).next();
            next = state.setValue(MODE, mode).setValue(ENABLED, true).setValue(ALARM, false);
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
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Player player,
        final BlockHitResult hitResult
    ) {
        if (!level.isClientSide()) {
            show(player, FetishRules.diagnostic(
                state.getValue(BOUND),
                state.getValue(ENABLED),
                state.getValue(MODE),
                state.getValue(ALARM),
                false,
                true
            ), state.getValue(MODE));
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
        if (!state.getValue(BOUND)) {
            FetishRuntime.attractZombies(level, pos);
            level.scheduleTick(pos, this, FetishRules.TICK_INTERVAL);
            return;
        }
        if (!state.getValue(ENABLED)) {
            return;
        }
        final boolean alarm = FetishRuntime.tick(level, pos, state.getValue(MODE));
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
    protected int getSignal(
        final BlockState state,
        final BlockGetter level,
        final BlockPos pos,
        final Direction direction
    ) {
        return state.getValue(ENABLED)
            && state.getValue(MODE) == FetishMode.SHRIEKING
            && state.getValue(ALARM) ? 15 : 0;
    }

    @Override
    public void playerDestroy(
        final Level level,
        final Player player,
        final BlockPos pos,
        final BlockState state,
        final net.minecraft.world.level.block.entity.BlockEntity blockEntity,
        final ItemStack destroyedWith
    ) {
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
        if (player.hasInfiniteMaterials()) {
            return;
        }
        final ItemStack dropped = new ItemStack(this);
        if (state.getValue(BOUND)) {
            FetishBindingState.write(dropped, state.getValue(MODE));
        }
        popResource(level, pos, dropped);
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
