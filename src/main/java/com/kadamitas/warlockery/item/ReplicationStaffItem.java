package com.kadamitas.warlockery.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public final class ReplicationStaffItem extends Item {
    public ReplicationStaffItem(final Properties properties) {
        super(properties.stacksTo(1).durability(512));
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final ItemStack stack = context.getItemInHand();
        if (context.getPlayer() != null && context.getPlayer().isSecondaryUseActive()) {
            ReplicationSelection.clear(stack);
            show(context, UtilityDecision.success("cleared"));
            return InteractionResult.SUCCESS;
        }
        final var current = ReplicationSelection.read(stack);
        if (current.isEmpty()) {
            new ReplicationSelection(
                context.getLevel().dimension().identifier(),
                context.getClickedPos().immutable(),
                java.util.Optional.empty()
            ).write(stack);
            show(context, UtilityDecision.failure("missing_second_corner"));
            return InteractionResult.SUCCESS;
        }
        final ReplicationSelection selection = current.orElseThrow();
        if (selection.second().isEmpty()) {
            final ReplicationSelection completed = selection.withSecond(context.getClickedPos());
            completed.write(stack);
            final UtilityDecision decision = completed.diagnose(context.getLevel().dimension().identifier());
            show(context, decision);
            return decision.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        final UtilityDecision decision = selection.diagnose(context.getLevel().dimension().identifier());
        if (!decision.success() || !(context.getLevel() instanceof ServerLevel level)) {
            show(context, decision);
            return decision.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        final int copied = copy(level, selection, context.getClickedPos().relative(context.getClickedFace()));
        final UtilityDecision result = copied > 0
            ? UtilityDecision.success("copied")
            : UtilityDecision.failure("nothing_copyable");
        if (copied > 0 && context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            stack.hurtAndBreak(1, level, serverPlayer, _ -> { });
        }
        show(context, result);
        return result.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    static int copy(
        final ServerLevel level,
        final ReplicationSelection selection,
        final BlockPos destination
    ) {
        final BlockPos second = selection.second().orElseThrow();
        final BlockPos minimum = new BlockPos(
            Math.min(selection.first().getX(), second.getX()),
            Math.min(selection.first().getY(), second.getY()),
            Math.min(selection.first().getZ(), second.getZ())
        );
        return (int) BlockPos.betweenClosedStream(selection.first(), second).filter(source -> {
            final var state = level.getBlockState(source);
            if (state.isAir() || state.hasBlockEntity()) {
                return false;
            }
            final BlockPos target = destination.offset(
                source.getX() - minimum.getX(),
                source.getY() - minimum.getY(),
                source.getZ() - minimum.getZ()
            );
            return level.isInWorldBounds(target) && level.setBlockAndUpdate(target, state);
        }).count();
    }

    private static void show(final UseOnContext context, final UtilityDecision decision) {
        if (context.getPlayer() != null && !context.getLevel().isClientSide()) {
            context.getPlayer().sendOverlayMessage(Component.translatable(decision.messageKey("replication_staff"))
                .withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
