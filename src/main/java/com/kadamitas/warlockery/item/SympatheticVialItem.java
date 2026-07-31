package com.kadamitas.warlockery.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

public final class SympatheticVialItem extends Item {
    public SympatheticVialItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        if (!player.level().isClientSide()) {
            bind(stack, player, target);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (!(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof BedBlock)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || context.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        final BlockPos bedHead = bedHead(level, context.getClickedPos());
        final var sleeper = level.getServer().getPlayerList().getPlayers().stream()
            .filter(player -> ownsBed(player, level, bedHead))
            .findFirst();
        if (sleeper.isEmpty()) {
            context.getPlayer().sendSystemMessage(Component.translatable("message.warlockery.sympathetic_vial.bed_unclaimed"));
            return InteractionResult.FAIL;
        }
        bind(context.getItemInHand(), context.getPlayer(), sleeper.orElseThrow());
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        return SympatheticBinding.read(stack).isPresent() || super.isFoil(stack);
    }

    static boolean ownsBed(final ServerPlayer player, final ServerLevel level, final BlockPos bedHead) {
        final ServerPlayer.RespawnConfig config = player.getRespawnConfig();
        return config != null
            && config.respawnData().dimension().equals(level.dimension())
            && config.respawnData().pos().equals(bedHead);
    }

    private static BlockPos bedHead(final ServerLevel level, final BlockPos clicked) {
        final var state = level.getBlockState(clicked);
        return state.getValue(BedBlock.PART) == BedPart.HEAD ? clicked : clicked.relative(state.getValue(BedBlock.FACING));
    }

    private static void bind(final ItemStack stack, final Player player, final LivingEntity target) {
        SympatheticBinding.from(target).write(stack);
        stack.set(DataComponents.LORE, new ItemLore(java.util.List.of(
            Component.translatable("tooltip.warlockery.sympathetic_vial.bound", target.getName())
        )));
        player.sendSystemMessage(Component.translatable("message.warlockery.sympathetic_vial.bound", target.getDisplayName()));
    }
}
