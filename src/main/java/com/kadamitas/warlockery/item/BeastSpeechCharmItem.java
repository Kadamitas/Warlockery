package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.CreatureBehaviorTags;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class BeastSpeechCharmItem extends Item {
    private static final List<String> HERBAL_REWARDS = List.of(
        "seedsbelladonna",
        "seedsmandrake",
        "seedsartichoke",
        "seedssnowbell",
        "seedswormwood"
    );

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
        final BeastSpeechRules.Audience audience = audience(target);
        final boolean accepted = audience == BeastSpeechRules.Audience.DEMON
            ? offering.is(CreatureBehaviorTags.Items.DEMON_BARTER)
            : audience == BeastSpeechRules.Audience.ANIMAL && acceptsAnimalOffering(target, offering);
        final UtilityDecision decision = BeastSpeechRules.diagnose(infernal, audience, accepted);
        if (!decision.success()) {
            show(player, decision);
            return InteractionResult.FAIL;
        }
        if (player.level() instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer) {
            if (!player.hasInfiniteMaterials()) {
                offering.shrink(1);
            }
            final ItemStack reward = reward(target, level);
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

    private static BeastSpeechRules.Audience audience(final LivingEntity target) {
        if (target.typeHolder().is(WarlockeryTags.EntityTypes.DEMONS)) {
            return BeastSpeechRules.Audience.DEMON;
        }
        if (target instanceof Animal || target.getType() == EntityTypes.BAT || target instanceof Spider) {
            return BeastSpeechRules.Audience.ANIMAL;
        }
        return BeastSpeechRules.Audience.INVALID;
    }

    private static boolean acceptsAnimalOffering(final LivingEntity target, final ItemStack offering) {
        if (target instanceof Animal animal && animal.isFood(offering)) {
            return true;
        }
        return target.getType() == EntityTypes.BAT && offering.is(Items.SWEET_BERRIES)
            || target instanceof Spider && (offering.is(Items.SPIDER_EYE) || offering.is(Items.ROTTEN_FLESH));
    }

    private static ItemStack reward(final LivingEntity target, final Level level) {
        if (target.typeHolder().is(WarlockeryTags.EntityTypes.DEMONS)) {
            return new ItemStack(ModItems.ALL.get("ingredient_infernal_blood").get());
        }
        if (target.getType() == EntityTypes.BAT) {
            return new ItemStack(ModItems.ALL.get("ingredient_bat_wool").get());
        }
        if (target.getType() == EntityTypes.WOLF) {
            return new ItemStack(ModItems.ALL.get("ingredient_dog_tongue").get());
        }
        if (target instanceof Spider) {
            return new ItemStack(Items.COBWEB);
        }
        if (target.getType() == EntityTypes.COW) {
            return new ItemStack(Items.LEATHER);
        }
        if (target.getType() == EntityTypes.CHICKEN) {
            return new ItemStack(Items.FEATHER, 2);
        }
        final String id = HERBAL_REWARDS.get(Math.floorMod(target.getId() + (int) level.getGameTime(), HERBAL_REWARDS.size()));
        return new ItemStack(ModItems.ALL.get(id).get());
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
