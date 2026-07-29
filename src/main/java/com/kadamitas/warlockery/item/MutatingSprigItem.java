package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.mutation.AdvancedMutationResolver;
import com.kadamitas.warlockery.mutation.AdvancedMutationTags;
import com.kadamitas.warlockery.mutation.MutatingSprigRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

@EventBusSubscriber(modid = Warlockery.MOD_ID)
public final class MutatingSprigItem extends Item {
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

    @SubscribeEvent
    public static void handleBlockBreak(final BreakBlockEvent event) {
        final Player player = event.getPlayer();
        final ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof MutatingSprigItem)) {
            return;
        }
        final BlockPos pos = event.getPos();
        final BlockState state = event.getState();
        final boolean immersed = event.getLevel().getFluidState(pos.above())
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
            return;
        }
        event.setCanceled(true);
        if (event.getLevel() instanceof ServerLevel level) {
            level.setBlockAndUpdate(pos, replacement.defaultBlockState());
            if (!player.hasInfiniteMaterials()) {
                stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
        }
    }
}
