package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.mutation.AdvancedMutationResolver;
import com.kadamitas.warlockery.mutation.AdvancedMutationTags;
import com.kadamitas.warlockery.mutation.MutatingSprigRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MutatingSprigItem extends Item implements BlockBreakBehavior {
    public static final int DURABILITY = 128;

    public MutatingSprigItem(final Properties properties) {
        super(properties.stacksTo(1).durability(DURABILITY));
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final var center = AdvancedMutationResolver.findCenter(context.getLevel(), context.getClickedPos());
        if (center.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final Player player = context.getPlayer();
        if (!(context.getLevel() instanceof ServerLevel level) || player == null) {
            return InteractionResult.FAIL;
        }
        final AdvancedMutationResolver.Outcome outcome = AdvancedMutationResolver.attempt(
            level,
            center.orElseThrow(),
            player
        );
        if (outcome.success() && !player.hasInfiniteMaterials()) {
            context.getItemInHand().hurtAndBreak(1, player, context.getHand());
        }
        return outcome.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override
    public boolean beforeBlockBreak(final ItemStack stack, final BlockPos pos, final Player player) {
        final BlockState state = player.level().getBlockState(pos);
        final boolean immersed = player.level().getFluidState(pos.above())
            .is(AdvancedMutationTags.Fluids.MUTATION_WATER);
        final MutatingSprigRules.Transformation transformation = MutatingSprigRules.transformation(
            state.is(AdvancedMutationTags.Blocks.SPRIG_DIRT),
            state.is(AdvancedMutationTags.Blocks.SPRIG_MYCELIUM),
            state.is(AdvancedMutationTags.Blocks.SPRIG_CLAY),
            immersed
        );
        final Block replacement = switch (transformation) {
            case DIRT -> Blocks.DIRT;
            case MYCELIUM -> Blocks.MYCELIUM;
            case CLAY -> Blocks.CLAY;
            case NONE -> null;
        };
        if (replacement == null) {
            return false;
        }
        if (!player.level().isClientSide()) {
            player.level().setBlockAndUpdate(pos, replacement.defaultBlockState());
            if (!player.hasInfiniteMaterials()) {
                stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
        }
        return true;
    }
}
