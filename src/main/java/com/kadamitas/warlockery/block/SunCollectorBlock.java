package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.item.SunlightRules;
import com.kadamitas.warlockery.item.UtilityDecision;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SunCollectorBlock extends Block {
    public SunCollectorBlock(final BlockBehaviour.Properties properties) {
        super(properties);
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
            !level.isDarkOutside(),
            level.canSeeSky(pos.above())
        );
        if (!decision.success()) {
            show(player, decision);
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            final ItemStack charged = stack.transmuteCopy(ModItems.ALL.get("sungrenade").get(), stack.getCount());
            player.setItemInHand(hand, charged);
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
        show(player, SunlightRules.collector(false, !level.isDarkOutside(), level.canSeeSky(pos.above())));
        return InteractionResult.SUCCESS;
    }

    private static void show(final Player player, final UtilityDecision decision) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable(decision.messageKey("sun_collector"))
                .withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
