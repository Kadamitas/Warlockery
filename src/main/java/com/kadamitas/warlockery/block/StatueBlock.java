package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.item.UtilityDecision;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.ritual.hex.HexRuntime;
import com.kadamitas.warlockery.ritual.hex.HexState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class StatueBlock extends Block {
    public static final BooleanProperty ACTIVE = BlockStateProperties.ENABLED;
    private final StatueProfile profile;

    public StatueBlock(final BlockBehaviour.Properties properties, final StatueProfile profile) {
        super(properties);
        this.profile = profile;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    public StatueProfile profile() {
        return profile;
    }

    public boolean occludes(final BlockState state) {
        return profile.effect() == StatueProfile.Effect.OCCLUDE_RITUALS && state.getValue(ACTIVE);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
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
        if (profile.effect() != StatueProfile.Effect.PATRON_BLESSING) {
            return useWithoutItem(state, level, pos, player, hit);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        final var binding = BoundStatueData.get(serverLevel).binding(pos);
        final var target = binding.flatMap(value -> value.resolve(serverLevel.getServer()));
        final UtilityDecision decision = StatueRules.patron(
            binding.isPresent(),
            target.isPresent(),
            stack.is(WarlockeryTags.Items.PATRON_OFFERINGS)
        );
        if (!decision.success()) {
            show(player, decision);
            return InteractionResult.FAIL;
        }
        final LivingEntity beneficiary = target.orElseThrow();
        beneficiary.addEffect(new MobEffectInstance(MobEffects.LUCK, 2_400, 1));
        beneficiary.addEffect(new MobEffectInstance(MobEffects.HASTE, 2_400, 0));
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        player.sendOverlayMessage(Component.translatable(
            "message.warlockery.statue.patron_blessed_target",
            beneficiary.getDisplayName()
        ).withStyle(ChatFormatting.GREEN));
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
        if (profile.effect() == StatueProfile.Effect.OCCLUDE_RITUALS) {
            if (!level.isClientSide()) {
                final boolean active = !state.getValue(ACTIVE);
                level.setBlockAndUpdate(pos, state.setValue(ACTIVE, active));
                show(player, StatueRules.diagnose(profile.effect(), false, false, active));
            }
            return InteractionResult.SUCCESS;
        }
        if (profile.effect() == StatueProfile.Effect.PATRON_BLESSING) {
            if (level instanceof ServerLevel serverLevel) {
                final var binding = BoundStatueData.get(serverLevel).binding(pos);
                if (binding.isPresent()) {
                    player.sendOverlayMessage(Component.translatable(
                        "message.warlockery.statue.patron_bound",
                        binding.orElseThrow().targetName()
                    ).withStyle(ChatFormatting.GREEN));
                } else {
                    show(player, UtilityDecision.failure("missing_binding"));
                }
            }
            return InteractionResult.SUCCESS;
        }
        final var activeHexes = HexState.active(player);
        final UtilityDecision decision = StatueRules.diagnose(
            profile.effect(),
            !activeHexes.isEmpty(),
            false,
            false
        );
        if (!decision.success()) {
            show(player, decision);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            activeHexes.forEach(active -> HexRuntime.remove(player, active.kind()));
            show(player, UtilityDecision.success("cleansed"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(
        final Level level,
        final BlockPos pos,
        final BlockState state,
        final LivingEntity placer,
        final ItemStack stack
    ) {
        if (profile.effect() != StatueProfile.Effect.PATRON_BLESSING || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        final BoundStatueData data = BoundStatueData.get(serverLevel);
        SympatheticBinding.read(stack).ifPresentOrElse(
            binding -> data.bind(pos, binding),
            () -> data.remove(pos)
        );
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
        if (profile.effect() != StatueProfile.Effect.PATRON_BLESSING || !(level instanceof ServerLevel serverLevel)) {
            super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
            return;
        }
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
        final var binding = BoundStatueData.get(serverLevel).binding(pos);
        if (!player.hasInfiniteMaterials()) {
            final ItemStack dropped = new ItemStack(this);
            binding.ifPresent(value -> value.write(dropped));
            popResource(level, pos, dropped);
        }
        BoundStatueData.get(serverLevel).remove(pos);
    }

    private static void show(final Player player, final UtilityDecision decision) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable(decision.messageKey("statue"))
                .withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
