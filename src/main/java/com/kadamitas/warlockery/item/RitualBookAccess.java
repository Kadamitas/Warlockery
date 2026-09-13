package com.kadamitas.warlockery.item;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.kadamitas.warlockery.registry.ModBlocks;

public final class RitualBookAccess {
    public static final String BOOK = "ingredient_book_circle_magic";

    private RitualBookAccess() { }

    public static Optional<ItemStack> find(final Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof ManualItem manual
                && BOOK.equals(manual.profile().id())) return Optional.of(stack);
        }
        return Optional.empty();
    }

    public static boolean require(final Player player) {
        if (find(player).isPresent()) return true;
        player.sendOverlayMessage(Component.translatable("message.warlockery.ritual.book_required",
            Component.translatable(ManualProfile.find(BOOK).orElseThrow().translatedTitleKey())));
        return false;
    }

    public static Optional<BlockPos> nearbyHeart(final Level level, final Player player) {
        BlockPos nearest = null;
        double distance = 64.0;
        for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-8, -3, -8),
            player.blockPosition().offset(8, 3, 8))) {
            final double candidate = player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos));
            if (candidate <= distance && level.isLoaded(pos)
                && level.getBlockState(pos).is(ModBlocks.ALL.get("circle").get())) {
                nearest = pos.immutable();
                distance = candidate;
            }
        }
        return Optional.ofNullable(nearest);
    }
}
