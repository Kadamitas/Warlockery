package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.item.AnointingPasteRules.Diagnostic;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class AnointingPasteItem extends Item {
    public AnointingPasteItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final BlockPos pos = context.getClickedPos();
        final BlockState state = context.getLevel().getBlockState(pos);
        final Block cauldron = ModBlocks.ALL.get("cauldron").get();
        final Diagnostic diagnostic = AnointingPasteRules.diagnostic(
            state.is(WarlockeryTags.Blocks.ANOINTABLE_CAULDRONS),
            state.is(cauldron)
        );
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return diagnostic == Diagnostic.READY ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        final Player player = context.getPlayer();
        if (diagnostic != Diagnostic.READY) {
            if (player != null) {
                show(player, diagnostic);
            }
            return InteractionResult.FAIL;
        }
        level.setBlockAndUpdate(pos, cauldron.defaultBlockState());
        if (player == null || !player.hasInfiniteMaterials()) {
            context.getItemInHand().shrink(1);
        }
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.9F, 1.15F);
        level.sendParticles(
            ParticleTypes.WITCH,
            pos.getX() + 0.5,
            pos.getY() + 0.65,
            pos.getZ() + 0.5,
            24,
            0.45,
            0.35,
            0.45,
            0.02
        );
        if (player != null) {
            show(player, Diagnostic.READY);
        }
        return InteractionResult.SUCCESS;
    }

    private static void show(final Player player, final Diagnostic diagnostic) {
        final String key = switch (diagnostic) {
            case NOT_ANOINTABLE -> "message.warlockery.anointing_paste.not_anointable";
            case ALREADY_ANOINTED -> "message.warlockery.anointing_paste.already_anointed";
            case READY -> "message.warlockery.anointing_paste.success";
        };
        final ChatFormatting color = switch (diagnostic) {
            case NOT_ANOINTABLE -> ChatFormatting.RED;
            case ALREADY_ANOINTED -> ChatFormatting.YELLOW;
            case READY -> ChatFormatting.GREEN;
        };
        player.sendOverlayMessage(Component.translatable(key).withStyle(color));
    }
}
