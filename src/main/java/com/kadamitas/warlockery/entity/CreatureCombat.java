package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class CreatureCombat {
    private CreatureCombat() {
    }

    public static void handleDamage(final LivingDamageEvent.Pre event) {
        final ItemStack projectile = event.getSource().getDirectEntity() instanceof AbstractArrow arrow
            ? arrow.getPickupItemStackOrigin() : ItemStack.EMPTY;
        final ItemStack weapon = event.getSource().getWeaponItem();
        final boolean silver = projectile.is(WarlockeryTags.Items.SILVER_PROJECTILES)
            || weapon != null && weapon.is(WarlockeryTags.Items.SILVER_WEAPONS);
        final boolean stake = projectile.is(ModItems.ALL.get("ingredient_bolt_stake").get());
        final boolean holy = projectile.is(ModItems.ALL.get("ingredient_bolt_holy").get());
        final boolean antiMagic = projectile.is(ModItems.ALL.get("ingredient_bolt_anti_magic").get());

        if (event.getEntity() instanceof ArcaneCreature creature) {
            event.setNewDamage(adjustedDamage(
                creature.creatureKind(),
                event.getNewDamage(),
                silver,
                stake,
                holy,
                creature instanceof SpiritMob,
                isWerewolfTarget(event.getEntity())
            ));
        } else if (silver && isWerewolfTarget(event.getEntity())) {
            event.setNewDamage(event.getNewDamage() * 2.0F);
        }
        if (antiMagic) {
            event.getEntity().removeAllEffects();
        }
    }

    public static float adjustedDamage(
        final ArcaneCreature.CreatureKind kind,
        final float baseDamage,
        final boolean silver,
        final boolean stake,
        final boolean holy,
        final boolean spirit
    ) {
        return adjustedDamage(kind, baseDamage, silver, stake, holy, spirit, isWerewolfKind(kind));
    }

    private static float adjustedDamage(
        final ArcaneCreature.CreatureKind kind,
        final float baseDamage,
        final boolean silver,
        final boolean stake,
        final boolean holy,
        final boolean spirit,
        final boolean werewolfTarget
    ) {
        final boolean silverWeakness = silver && werewolfTarget;
        final boolean stakeWeakness = stake && kind.isVampiric();
        float damage = kind.isSupernatural() && !silverWeakness && !stakeWeakness
            ? Math.max(0.25F, baseDamage * 0.15F)
            : baseDamage;
        if (silverWeakness) damage *= 2.0F;
        if (stake && kind.isVampiric()) damage *= 2.5F;
        if (stake && kind.isWoodenVulnerable()) damage *= 2.0F;
        if (holy && (kind.isUndead() || kind.isDemonic() || spirit)) damage *= 2.0F;
        return damage;
    }

    private static boolean isWerewolfTarget(final LivingEntity target) {
        return target.typeHolder().is(WarlockeryTags.EntityTypes.WEREWOLVES)
            || target instanceof Player player && SupernaturalState.getForm(player) == SupernaturalForm.WEREWOLF
            || target instanceof ArcaneCreature creature && isWerewolfKind(creature.creatureKind());
    }

    private static boolean isWerewolfKind(final ArcaneCreature.CreatureKind kind) {
        return kind == ArcaneCreature.CreatureKind.WEREWOLF || kind == ArcaneCreature.CreatureKind.LYCAN_VILLAGER;
    }

    public static boolean isSilverDamage(final LivingDamageEvent.Pre event) {
        final ItemStack projectile = event.getSource().getDirectEntity() instanceof AbstractArrow arrow
            ? arrow.getPickupItemStackOrigin() : ItemStack.EMPTY;
        final ItemStack weapon = event.getSource().getWeaponItem();
        return projectile.is(WarlockeryTags.Items.SILVER_PROJECTILES)
            || weapon != null && weapon.is(WarlockeryTags.Items.SILVER_WEAPONS);
    }
}
