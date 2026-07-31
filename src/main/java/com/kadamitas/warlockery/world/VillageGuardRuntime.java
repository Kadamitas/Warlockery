package com.kadamitas.warlockery.world;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public final class VillageGuardRuntime {
    private VillageGuardRuntime() {
    }

    public static void registerEvents() {
        PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(VillageGuardRuntime::handleInteract);
    }

    private static boolean handleInteract(final PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !(event.getTarget() instanceof Villager villager)
            || !isCommissionableTarget(villager)) {
            return false;
        }
        final boolean eligible = VillageGuardRules.canCommission(
            player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE),
            player.level().isVillage(villager.blockPosition()),
            !villager.isBaby(),
            event.getItemStack().is(Items.LEATHER_CHESTPLATE)
        );
        if (!eligible) {
            return false;
        }
        final IronGolem guard = EntityTypes.IRON_GOLEM.create(player.level(), EntitySpawnReason.CONVERSION);
        if (guard == null) {
            return false;
        }
        guard.snapTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
        guard.setPlayerCreated(true);
        guard.setPersistenceRequired();
        if (villager.hasCustomName()) {
            guard.setCustomName(villager.getCustomName());
        }
        player.level().addFreshEntity(guard);
        villager.discard();
        if (!player.hasInfiniteMaterials()) {
            event.getItemStack().shrink(1);
        }
        player.sendOverlayMessage(Component.translatable("message.warlockery.village.guard_commissioned")
            .withStyle(ChatFormatting.GREEN));
        event.setCancellationResult(InteractionResult.SUCCESS);
        return true;
    }

    static boolean isCommissionableTarget(final Villager villager) {
        return villager.getType() == EntityTypes.VILLAGER;
    }
}
