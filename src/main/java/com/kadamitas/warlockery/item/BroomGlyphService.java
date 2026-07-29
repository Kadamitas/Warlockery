package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;

public final class BroomGlyphService {
    private static final int MAX_RADIUS = 2;

    private BroomGlyphService() {
    }

    public static InteractionResult clear(final UseOnContext context, final int radius) {
        if (radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Glyph-clearing radius must be between zero and two");
        }
        final Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        final var level = context.getLevel();
        final BlockPos center = context.getClickedPos();
        final List<BlockPos> glyphs = BlockPos.betweenClosedStream(
                center.offset(-radius, 0, -radius),
                center.offset(radius, 0, radius)
            )
            .filter(level::isLoaded)
            .filter(pos -> level.getBlockState(pos).is(WarlockeryTags.Blocks.CHALK_GLYPHS))
            .limit(maxCandidates(radius))
            .map(BlockPos::immutable)
            .toList();
        if (glyphs.isEmpty()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.broom.no_glyphs")
                    .withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final long cleared = glyphs.stream().filter(pos -> level.destroyBlock(pos, false, player)).count();
        if (cleared == 0) {
            return InteractionResult.FAIL;
        }
        if (!player.hasInfiniteMaterials()) {
            context.getItemInHand().hurtAndBreak(1, player, context.getHand());
        }
        player.sendOverlayMessage(Component.translatable("message.warlockery.broom.glyphs_cleared", cleared)
            .withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    static int maxCandidates(final int radius) {
        if (radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Glyph-clearing radius must be between zero and two");
        }
        final int width = radius * 2 + 1;
        return width * width;
    }
}
