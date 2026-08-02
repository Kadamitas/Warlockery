package com.kadamitas.warlockery.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class BeastSpeechCharmItem extends Item {
    private final boolean infernal;

    public BeastSpeechCharmItem(final Properties properties, final boolean infernal) {
        super(properties.stacksTo(1).durability(BeastSpeechRules.durability(infernal)));
        this.infernal = infernal;
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack charm,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        final ItemStack offering = player.getItemInHand(
            hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND
        );
        final BeastSpeechTradeCatalog.Partner partner = BeastSpeechTradeCatalog.partner(target);
        final BeastSpeechRules.Audience audience = switch (partner) {
            case DEMON -> BeastSpeechRules.Audience.DEMON;
            case INVALID -> BeastSpeechRules.Audience.INVALID;
            default -> BeastSpeechRules.Audience.ANIMAL;
        };
        final boolean accepted = BeastSpeechTradeCatalog.acceptsOffering(target, offering);
        final UtilityDecision decision = BeastSpeechRules.diagnose(infernal, audience, accepted);
        if (!decision.success()) {
            show(player, decision);
            return InteractionResult.FAIL;
        }
        if (player.level() instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer) {
            if (!player.hasInfiniteMaterials()) {
                offering.shrink(1);
            }
            final long seed = BeastSpeechTradeSeed.next(level, serverPlayer, target);
            final ItemStack reward = BeastSpeechTradeCatalog.exchange(partner, true, seed).orElseThrow();
            if (!player.getInventory().add(reward)) {
                player.drop(reward, false);
            }
            charm.hurtAndBreak(1, level, serverPlayer, _ -> { });
            if (target instanceof Mob mob) {
                mob.setTarget(null);
            }
            show(player, decision);
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean pacifiesDemon(final Player player, final Mob demon) {
        final boolean carried = player.getInventory().contains(stack ->
            stack.getItem() instanceof BeastSpeechCharmItem charm && charm.infernal
        );
        return carried && Math.floorMod(demon.tickCount + demon.getId(), 4) != 0;
    }

    private static void show(final Player player, final UtilityDecision decision) {
        if (!player.level().isClientSide()) {
            final String messageKey = switch (decision.diagnostic()) {
                case "trade_complete" -> "message.warlockery.beast_speech.trade_complete";
                case "wrong_offering" -> "message.warlockery.beast_speech.wrong_offering";
                default -> "message.warlockery.beast_speech.no_voice";
            };
            player.sendOverlayMessage(Component.translatable(messageKey).withStyle(
                decision.success() ? ChatFormatting.GREEN : ChatFormatting.RED
            ));
        }
    }
}
