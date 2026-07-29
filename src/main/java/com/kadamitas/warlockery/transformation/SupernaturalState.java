package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.entity.CreatureCombat;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class SupernaturalState {
    private static final String FORM_KEY = "WarlockerySupernaturalForm";
    private static final String RESERVE_KEY = "WarlockerySupernaturalReserve";
    private static final int MAX_RESERVE = 100;
    private SupernaturalState() {
    }

    public static SupernaturalForm getForm(final Player player) {
        final String stored = player.getPersistentData().getStringOr(FORM_KEY, SupernaturalForm.NONE.name());
        try {
            return SupernaturalForm.valueOf(stored.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SupernaturalForm.NONE;
        }
    }

    public static void setForm(final Player player, final SupernaturalForm form) {
        final CompoundTag data = player.getPersistentData();
        data.putString(FORM_KEY, form.name());
        data.putInt(RESERVE_KEY, form == SupernaturalForm.NONE ? 0 : MAX_RESERVE);
    }

    public static int getReserve(final Player player) {
        return Math.clamp(player.getPersistentData().getIntOr(RESERVE_KEY, 0), 0, MAX_RESERVE);
    }

    public static void addReserve(final Player player, final int amount) {
        setReserve(player, getReserve(player) + amount);
    }

    public static void handleDamage(final LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final SupernaturalForm form = getForm(player);
        if (form == SupernaturalForm.NONE || isWeakness(form, event)) {
            return;
        }

        final int reserve = getReserve(player);
        if (reserve <= 0) {
            return;
        }
        final int cost = Math.max(1, (int) Math.ceil(event.getNewDamage() * 5.0F));
        final float protection = Math.min(0.9F, reserve / (float) cost * 0.9F);
        setReserve(player, reserve - Math.min(reserve, cost));
        event.setNewDamage(Math.max(0.25F, event.getNewDamage() * (1.0F - protection)));
    }

    public static void tick(final Player player) {
        if (player.level().isClientSide() || player.tickCount % 20 != 0) {
            return;
        }
        final SupernaturalForm form = getForm(player);
        if (form == SupernaturalForm.NONE) {
            return;
        }

        final long dayTime = player.level().getOverworldClockTime() % 24_000L;
        final boolean night = dayTime >= 13_000L && dayTime <= 23_000L;
        if (form == SupernaturalForm.VAMPIRE) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, true, false));
            if (!night && player.level().canSeeSky(player.blockPosition()) && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                player.igniteForSeconds(3.0F);
                setReserve(player, getReserve(player) - 3);
                return;
            }
        } else if (night) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 0, true, false));
        }
        if (night) {
            setReserve(player, getReserve(player) + (form == SupernaturalForm.WEREWOLF ? 2 : 1));
        }
    }

    public static void copyAfterClone(final PlayerEvent.Clone event) {
        final Player oldPlayer = event.getOriginal();
        final Player newPlayer = event.getEntity();
        newPlayer.getPersistentData().putString(FORM_KEY, getForm(oldPlayer).name());
        newPlayer.getPersistentData().putInt(RESERVE_KEY, getReserve(oldPlayer));
        SupernaturalProgression.copy(oldPlayer, newPlayer);
    }

    private static boolean isWeakness(final SupernaturalForm form, final LivingDamageEvent.Pre event) {
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        if (form == SupernaturalForm.VAMPIRE && event.getSource().is(DamageTypeTags.IS_FIRE)) {
            return true;
        }
        if (CreatureCombat.isSilverDamage(event)) {
            return true;
        }
        final ItemStack weapon = event.getSource().getWeaponItem();
        return weapon != null && weapon.is(WarlockeryTags.Items.SUPERNATURAL_WEAKNESSES);
    }

    private static void setReserve(final Player player, final int reserve) {
        player.getPersistentData().putInt(RESERVE_KEY, Math.clamp(reserve, 0, MAX_RESERVE));
    }
}
