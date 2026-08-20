package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import com.kadamitas.warlockery.item.EquipmentSetEffects;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.world.VillageGuardRules;
import com.kadamitas.warlockery.world.VillageGuardRuntime;
import java.util.Comparator;
import java.util.stream.StreamSupport;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

public final class CreatureCombat {
    private CreatureCombat() {
    }

    public static void handleDamage(final LivingDamageEvent event) {
        if (event.getAmount() <= 0.0F) {
            return;
        }
        final ItemStack projectile = event.getSource().getDirectEntity() instanceof AbstractArrow arrow
            ? arrow.getPickupItemStackOrigin() : ItemStack.EMPTY;
        final ItemStack weapon = event.getSource().getWeaponItem();
        final boolean silver = projectile.is(WarlockeryTags.Items.SILVER_PROJECTILES)
            || weapon != null && weapon.is(WarlockeryTags.Items.SILVER_WEAPONS)
            || VillageGuardRules.isSilverClassifiedAttack(
                event.getSource().getEntity() != null
                    && VillageGuardRuntime.isSettlementGuard(event.getSource().getEntity())
            );
        final boolean wooden = projectile.is(ModItems.ALL.get("ingredient_bolt_stake").get());
        final boolean holy = projectile.is(ModItems.ALL.get("ingredient_bolt_holy").get());
        final boolean antiMagic = projectile.is(ModItems.ALL.get("ingredient_bolt_anti_magic").get());

        if (event.getEntity() instanceof ArcaneCreature creature) {
            event.setAmount(adjustedDamage(
                creature.creatureKind(),
                event.getAmount(),
                silver,
                wooden,
                holy,
                creature instanceof SpiritMob || creature instanceof EldritchWatcherEntity
                    || creature instanceof UmbralSigilEntity,
                isWerewolfTarget(event.getEntity()),
                LycanDamageTypes.isHarmWerewolves(event.getSource())
            ));
            applyPairedPatronProtection(event, creature.creatureKind());
        } else if (silver && isWerewolfTarget(event.getEntity())) {
            event.setAmount(event.getAmount() * 2.0F);
        }
        final boolean nullifyingHunterShot = antiMagic
            && event.getSource().getEntity() instanceof Player shooter
            && EquipmentSetEffects.wearsCompleteHunterSet(shooter);
        if (nullifyingHunterShot) {
            event.getEntity().getActiveEffects().stream()
                .map(effect -> effect.getEffect())
                .filter(effect -> !persistsThroughNullification(effect))
                .toList()
                .forEach(event.getEntity()::removeEffect);
            if (event.getEntity() instanceof Player target) {
                MagicPathState.active(target).forEach(path -> {
                    final int reserve = MagicPathState.reserve(target, path);
                    MagicPathState.spend(target, path, (reserve + 2) / 3);
                });
            }
        }
        final ItemStack meleeWeapon = event.getSource().getWeaponItem();
        if (meleeWeapon != null
            && meleeWeapon.is(ModItems.ALL.get("ingredient_stake").get())
            && isVampireTarget(event.getEntity())
            && event.getEntity().isSleeping()) {
            event.setAmount(Math.max(event.getAmount(), event.getEntity().getMaxHealth() * 100.0F));
        }
        transferDamageToFamiliar(event);
    }

    public static float adjustedDamage(
        final ArcaneCreature.CreatureKind kind,
        final float baseDamage,
        final boolean silver,
        final boolean wooden,
        final boolean holy,
        final boolean spirit
    ) {
        return adjustedDamage(kind, baseDamage, silver, wooden, holy, spirit, isWerewolfKind(kind), false);
    }

    public static float adjustedDamage(
        final ArcaneCreature.CreatureKind kind,
        final float baseDamage,
        final boolean silver,
        final boolean wooden,
        final boolean holy,
        final boolean spirit,
        final boolean werewolfTarget,
        final boolean antiWerewolfTyped
    ) {
        final boolean silverWeakness = silver && werewolfTarget;
        final boolean woodenWeakness = wooden && kind.isWoodenVulnerable();
        final boolean consecratedWeakness = holy
            && (kind.isUndead() || kind.isDemonic() || spirit
                || kind == ArcaneCreature.CreatureKind.HEX_BAT);
        final boolean typedBypass = antiWerewolfTyped && werewolfTarget;
        float damage = kind.isSupernatural()
            && !silverWeakness && !woodenWeakness && !consecratedWeakness && !typedBypass
            ? Math.max(0.25F, baseDamage * 0.15F)
            : baseDamage;
        if (silverWeakness) damage *= 2.0F;
        if (woodenWeakness) damage *= 2.0F;
        if (consecratedWeakness) damage *= 1.5F;
        return kind == ArcaneCreature.CreatureKind.DEATH ? DeathCombatRules.capIncoming(damage) : damage;
    }

    public static boolean isNullifyingHunterShot(final LivingDamageEvent event) {
        return event.getSource().getDirectEntity() instanceof AbstractArrow arrow
            && arrow.getPickupItemStackOrigin().is(ModItems.ALL.get("ingredient_bolt_anti_magic").get())
            && event.getSource().getEntity() instanceof Player shooter
            && EquipmentSetEffects.wearsCompleteHunterSet(shooter);
    }

    static boolean persistsThroughNullification(final Holder<MobEffect> effect) {
        return effect == MobEffects.POISON || effect == MobEffects.WITHER;
    }

    public static boolean isWerewolfTarget(final LivingEntity target) {
        return target.typeHolder().is(WarlockeryTags.EntityTypes.WEREWOLVES)
            || target instanceof Player player && SupernaturalState.getForm(player) == SupernaturalForm.WEREWOLF
            || target instanceof ArcaneCreature creature && isWerewolfKind(creature.creatureKind());
    }

    public static boolean isVampireTarget(final LivingEntity target) {
        return target.typeHolder().is(WarlockeryTags.EntityTypes.VAMPIRES)
            || target instanceof Player player && SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE
            || target instanceof ArcaneCreature creature && creature.creatureKind().isVampiric();
    }

    public static void capDeathDamage(final LivingDamageEvent event) {
        if (event.getEntity() instanceof ArcaneCreature creature
            && creature.creatureKind() == ArcaneCreature.CreatureKind.DEATH) {
            event.setAmount(DeathCombatRules.capIncoming(event.getAmount()));
        }
    }

    private static boolean isWerewolfKind(final ArcaneCreature.CreatureKind kind) {
        return kind == ArcaneCreature.CreatureKind.WEREWOLF || kind == ArcaneCreature.CreatureKind.LYCAN_VILLAGER;
    }

    public static boolean isSilverDamage(final LivingDamageEvent event) {
        final ItemStack projectile = event.getSource().getDirectEntity() instanceof AbstractArrow arrow
            ? arrow.getPickupItemStackOrigin() : ItemStack.EMPTY;
        final ItemStack weapon = event.getSource().getWeaponItem();
        return projectile.is(WarlockeryTags.Items.SILVER_PROJECTILES)
            || weapon != null && weapon.is(WarlockeryTags.Items.SILVER_WEAPONS);
    }

    private static void applyPairedPatronProtection(
        final LivingDamageEvent event,
        final ArcaneCreature.CreatureKind kind
    ) {
        // The F12 ward stance is the complete protection model for the dedicated patron bodies,
        // so the 1.4 symmetric reduction must not compose with it: nobody chose the combined
        // number. The guard is deliberately the narrowest available, an exact instanceof against
        // the F12 body contract, so a patron that is still the shared 1.4 body keeps 1.4
        // behaviour exactly and nothing outside this one family is touched.
        if (event.getEntity() instanceof GoblinPatronRuntime.PatronBody) {
            return;
        }
        final var counterpart = GoblinBossRules.counterpart(kind);
        if (counterpart.isEmpty() || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        final double distanceSquared = level.getEntitiesOfClass(
            LivingEntity.class,
            event.getEntity().getBoundingBox().inflate(16.0),
            candidate -> candidate != event.getEntity()
                && candidate instanceof ArcaneCreature arcane
                && arcane.creatureKind() == counterpart.orElseThrow()
        ).stream().mapToDouble(event.getEntity()::distanceToSqr).min().orElse(Double.POSITIVE_INFINITY);
        event.setAmount(event.getAmount() * GoblinBossRules.pairedDamageMultiplier(distanceSquared));
    }

    private static void transferDamageToFamiliar(final LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
            || !(player.level() instanceof ServerLevel playerLevel)
            || event.getAmount() <= 0.0F) {
            return;
        }
        final var target = StreamSupport.stream(playerLevel.getServer().getAllLevels().spliterator(), false)
            .flatMap(level -> StreamSupport.stream(level.getAllEntities().spliterator(), false)
                .filter(Mob.class::isInstance)
                .map(Mob.class::cast)
                .map(mob -> new FamiliarTarget(level, mob)))
            .filter(candidate -> candidate.mob() instanceof ArcaneCreature familiar
                && FamiliarBondRules.isClassicFamiliar(familiar.creatureKind())
                && CreatureBehaviorState.isOwnedBy(candidate.mob(), player.getUUID()))
            .min(Comparator.comparingDouble(candidate -> candidate.priority(player)))
            .orElse(null);
        if (target == null) {
            return;
        }
        final boolean sameDimension = target.level() == playerLevel;
        final double distanceSquared = sameDimension
            ? player.distanceToSqr(target.mob())
            : Double.POSITIVE_INFINITY;
        final float transferred = event.getAmount()
            * FamiliarBondRules.transferredDamageFraction(sameDimension, distanceSquared);
        event.setAmount(event.getAmount() - transferred);
        target.mob().hurtServer(target.level(), target.mob().damageSources().generic(), transferred);
    }

    private record FamiliarTarget(ServerLevel level, Mob mob) {
        private double priority(final Player player) {
            return level == player.level() ? player.distanceToSqr(mob) : Double.MAX_VALUE;
        }
    }
}
