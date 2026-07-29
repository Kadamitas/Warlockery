package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SeerStoneItem extends Item {
    public SeerStoneItem(final Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND;
        final ItemStack catalyst = player.getItemInHand(otherHand);
        if (catalyst.is(WarlockeryTags.Items.DIVINATION_CATALYSTS)) {
            final DivinationRules.Prediction prediction = DivinationRuntime.predict(level, player.blockPosition());
            if (!player.hasInfiniteMaterials()) {
                catalyst.shrink(1);
            }
            player.sendSystemMessage(Component.translatable(
                "message.warlockery.divination.prediction." + prediction.name().toLowerCase(java.util.Locale.ROOT)
            ).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        final DivinationRuntime.Progression progression = DivinationRuntime.progression(player);
        final Component paths = progression.paths().isEmpty()
            ? Component.translatable("message.warlockery.divination.no_paths")
            : pathList(progression.paths());
        player.sendSystemMessage(Component.translatable(
            "message.warlockery.divination.progression",
            Component.translatable(
                "supernatural_form.warlockery."
                    + progression.form().name().toLowerCase(java.util.Locale.ROOT)
            ),
            progression.supernaturalReserve(),
            paths,
            progression.totalPathReserve()
        ).withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }

    private static Component pathList(final List<MagicPath> paths) {
        final var result = Component.empty();
        for (int index = 0; index < paths.size(); index++) {
            if (index > 0) {
                result.append(Component.literal(", "));
            }
            result.append(Component.translatable("magic_path.warlockery." + paths.get(index).id()));
        }
        return result;
    }
}
