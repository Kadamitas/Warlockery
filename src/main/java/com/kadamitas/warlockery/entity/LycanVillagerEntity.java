package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

public final class LycanVillagerEntity extends Villager implements ArcaneCreature {
    public LycanVillagerEntity(final EntityType<? extends Villager> type, final Level level) {
        super(type, level);
        getOffers().add(new MerchantOffer(
            new ItemCost(Items.EMERALD, 4),
            new ItemStack(ModItems.ALL.get("ingredient_silverdust").get(), 2),
            12,
            2,
            0.05F
        ));
        getOffers().add(new MerchantOffer(
            new ItemCost(Items.EMERALD, 7),
            new ItemStack(ModItems.ALL.get("ingredient_wolfsbane").get(), 3),
            8,
            4,
            0.05F
        ));
        getOffers().add(new MerchantOffer(
            new ItemCost(ModItems.ALL.get("raw_silver").get(), 3),
            new ItemStack(Items.EMERALD, 1),
            12,
            3,
            0.05F
        ));
    }

    public static boolean canTrade(final SupernaturalForm form) {
        return form == SupernaturalForm.WEREWOLF;
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.LYCAN_VILLAGER;
    }

    @Override
    public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        if (!canTrade(SupernaturalState.getForm(player))) {
            if (!level().isClientSide()) {
                player.sendSystemMessage(Component.translatable("message.warlockery.lycan_villager.werewolf_only"));
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
}
