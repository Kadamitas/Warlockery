package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class ProgressionTokenItem extends Item {
    public ProgressionTokenItem(final Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final boolean vampire = player.isSecondaryUseActive();
        final SupernaturalProgression.Path path = vampire
            ? SupernaturalProgression.Path.VAMPIRE
            : SupernaturalProgression.Path.WEREWOLF;
        final int next = ProgressionTokenRules.next(SupernaturalProgression.level(player, path));
        final UtilityDecision decision = ProgressionTokenRules.diagnose(player.hasInfiniteMaterials(), next);
        if (!decision.success()) {
            show(player, decision, next, vampire);
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            SupernaturalProgression.setLevel(player, path, next);
            SupernaturalState.setForm(player, next == 0
                ? SupernaturalForm.NONE
                : path.form());
            show(player, decision, next, vampire);
        }
        return InteractionResult.SUCCESS;
    }

    private static void show(
        final Player player,
        final UtilityDecision decision,
        final int level,
        final boolean vampire
    ) {
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable(
                decision.messageKey("progression_token"),
                vampire ? "vampire" : "werewolf",
                level
            ).withStyle(decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
