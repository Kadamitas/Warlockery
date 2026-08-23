package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.entity.CreatureCombat;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class SupernaturalState {
    private static final String FORM_KEY = "WarlockerySupernaturalForm";
    private static final String LEGACY_RESERVE_KEY = "WarlockerySupernaturalReserve";

    private SupernaturalState() {
    }

    public static SupernaturalForm getForm(final Player player) {
        final String stored = player.getPersistentData().getStringOr(FORM_KEY, SupernaturalForm.NONE.name());
        return SupernaturalForm.parse(stored);
    }

    public static void setForm(final Player player, final SupernaturalForm form) {
        if (form == SupernaturalForm.NONE) {
            SupernaturalProgression.cure(player);
            return;
        }
        SupernaturalProgression.Path.forForm(form).ifPresent(path -> SupernaturalProgression.beginPath(player, path));
    }

    static void setIdentity(final Player player, final SupernaturalForm form) {
        final CompoundTag data = player.getPersistentData();
        data.putString(FORM_KEY, form.name());
        if (form == SupernaturalForm.NONE) {
            data.putInt(LEGACY_RESERVE_KEY, 0);
        }
    }

    public static int getReserve(final Player player) {
        return SupernaturalProgression.Path.forForm(getForm(player))
            .map(path -> SupernaturalProgression.resource(player, path))
            .orElse(0);
    }

    public static int getMaximumReserve(final Player player) {
        return SupernaturalProgression.Path.forForm(getForm(player))
            .map(path -> SupernaturalProgression.maximumResource(path, SupernaturalProgression.level(player, path)))
            .orElse(0);
    }

    public static void addReserve(final Player player, final int amount) {
        SupernaturalProgression.Path.forForm(getForm(player))
            .ifPresent(path -> SupernaturalProgression.addResource(player, path, amount));
    }

    public static void handleDamage(final LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final SupernaturalForm form = getForm(player);
        final var currentPath = SupernaturalProgression.Path.forForm(form);
        if (currentPath.isEmpty()) {
            return;
        }
        final SupernaturalProgression.Path path = currentPath.orElseThrow();
        if (isWeakness(form, event)) {
            return;
        }
        if (form == SupernaturalForm.WEREWOLF
            && SupernaturalProgression.werewolfShape(player) == WerewolfShape.HUMAN) {
            return;
        }
        final int reserve = SupernaturalProgression.resource(player, path);
        if (reserve <= 0) {
            return;
        }
        final int cost = Math.max(1, (int) Math.ceil(event.getAmount() * form.damageReserveMultiplier()));
        final float maximumProtection = form.maximumDamageReduction();
        final float protection = Math.min(maximumProtection, reserve / (float) cost * maximumProtection);
        SupernaturalProgression.spend(player, path, Math.min(reserve, cost));
        final float protectedDamage = Math.max(0.25F, event.getAmount() * (1.0F - protection));
        if (activateDeathWard(event, player, path, form, protectedDamage)) {
            return;
        }
        event.setAmount(protectedDamage);
    }

    private static boolean activateDeathWard(
        final LivingDamageEvent event,
        final Player player,
        final SupernaturalProgression.Path path,
        final SupernaturalForm form,
        final float damage
    ) {
        if (damage < player.getHealth()
            || !SupernaturalProgression.spend(player, path, form.deathWardCost())) {
            return false;
        }
        if (form == SupernaturalForm.VAMPIRE) {
            event.setAmount(Math.max(0.0F, player.getHealth() - 1.0F));
            return true;
        }
        event.setAmount(0.0F);
        player.clearFire();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0, true, false));
        return true;
    }

    public static void tick(final Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        final SupernaturalForm form = getForm(player);
        if (form == SupernaturalForm.NONE) {
            return;
        }
        final var path = SupernaturalProgression.Path.forForm(form).orElseThrow();
        final int level = SupernaturalProgression.level(player, path);
        if (level == 0) {
            SupernaturalProgression.beginPath(player, path);
        }
        switch (form) {
            case VAMPIRE -> tickVampire(player, Math.max(1, level));
            case WEREWOLF -> tickWerewolf(player, Math.max(1, level));
            case NONE -> {
            }
        }
    }

    public static void copyAfterClone(final PlayerEvent.Clone event) {
        final Player oldPlayer = event.getOriginal();
        final Player newPlayer = event.getEntity();
        newPlayer.getPersistentData().putString(FORM_KEY, getForm(oldPlayer).name());
        SupernaturalProgression.copy(oldPlayer, newPlayer);
    }

    private static void tickVampire(final Player player, final int level) {
        if (level >= 2 && player.tickCount % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, true, false));
        }
        if (player.tickCount % 20 == 0) {
            player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(12.0),
                target -> target != player && target.isAlive() && target.getHealth() < target.getMaxHealth()
            ).forEach(target -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false)));
        }
        if (player.tickCount % 20 == 0 && VampireSunlightRules.exposed(new VampireSunlightRules.Exposure(
            player.level().environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, player.position())
                || player.level().dimensionTypeRegistration().is(VampireSunlightRules.SUNLIGHT),
            player.level().dimensionTypeRegistration().is(VampireSunlightRules.SUNLIGHT_EXEMPT),
            player.level().canSeeSky(player.blockPosition()),
            player.level().environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_LEVEL, player.position()) > 8.0F,
            player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
            player.isInWaterOrRain(),
            player.level().isRainingAt(player.blockPosition()),
            player.level().getBlockState(player.blockPosition()).is(Blocks.POWDER_SNOW)
        ))) {
            applyVampireSunlight(player, level);
        }
        if (SupernaturalProgression.batSwarmUntil(player) > player.level().getGameTime()) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, true, false));
        }
    }

    static void applyVampireSunlight(final Player player, final int level) {
        final int blood = SupernaturalProgression.resource(player, SupernaturalProgression.Path.VAMPIRE);
        final boolean chargeDue = player.tickCount % 40 == 0;
        final VampireSunlightRules.Protection protection = VampireSunlightRules.protection(
            level,
            blood,
            SupernaturalProgression.maximumResource(SupernaturalProgression.Path.VAMPIRE, level),
            chargeDue
        );
        final boolean paid = protection.bloodCost() == 0 || SupernaturalProgression.spend(
            player, SupernaturalProgression.Path.VAMPIRE, protection.bloodCost()
        );
        if (protection.preventsDamage() && paid) {
            if (protection.bloodCost() > 0) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 3, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 0, true, false));
            }
            return;
        }
        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            player.hurtServer(serverLevel, VampireDamageTypes.sunlight(serverLevel), 1.0F);
        }
        player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 8));
    }

    private static void tickWerewolf(final Player player, final int level) {
        final boolean fullMoon = player.level().environmentAttributes()
            .getValue(EnvironmentAttributes.MOON_PHASE, player.position()) == MoonPhase.FULL_MOON
            && player.level().isDarkOutside();
        final boolean hasMoonCharm = player.getInventory().contains(
            stack -> stack.is(ModItems.ALL.get("mooncharm").get())
        );
        if (WerewolfMoonRules.forcesWolfForm(level, fullMoon, hasMoonCharm)) {
            SupernaturalProgression.setWerewolfShape(player, WerewolfShape.WOLF);
        } else if (level == 1 && !hasMoonCharm) {
            SupernaturalProgression.setWerewolfShape(player, WerewolfShape.HUMAN);
        }
        if (player.hasEffect(MobEffects.POISON)) {
            player.removeEffect(MobEffects.POISON);
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        final WerewolfShape shape = SupernaturalProgression.werewolfShape(player);
        if (shape != WerewolfShape.HUMAN) {
            final int levelTier = Math.min(2, Math.max(0, (level - 1) / 3));
            final int speed = Math.min(3, levelTier + (shape == WerewolfShape.WOLF ? 1 : 0));
            final int strength = Math.min(3, levelTier + (shape == WerewolfShape.WOLFMAN ? 1 : 0));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, speed, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, strength, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 40, Math.min(2, levelTier), true, false));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, true, false));
        }
        if (player.level().isDarkOutside()) {
            SupernaturalProgression.addResource(player, SupernaturalProgression.Path.WEREWOLF, shape == WerewolfShape.HUMAN ? 2 : 5);
        }
    }

    private static boolean isWeakness(final SupernaturalForm form, final LivingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        if (form == SupernaturalForm.VAMPIRE && event.getSource().is(DamageTypeTags.IS_FIRE)) {
            return true;
        }
        if (form == SupernaturalForm.VAMPIRE && event.getSource().is(VampireDamageTypes.VAMPIRE_SUNLIGHT)) {
            return true;
        }
        if (CreatureCombat.isSilverDamage(event)) {
            return true;
        }
        if (form == SupernaturalForm.WEREWOLF
            && com.kadamitas.warlockery.entity.LycanDamageTypes.isHarmWerewolves(event.getSource())) {
            return true;
        }
        final ItemStack weapon = event.getSource().getWeaponItem();
        return weapon != null && weapon.is(WarlockeryTags.Items.SUPERNATURAL_WEAKNESSES);
    }
}
