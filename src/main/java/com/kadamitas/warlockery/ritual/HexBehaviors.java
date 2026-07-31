package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.ritual.hex.HexKind;
import com.kadamitas.warlockery.ritual.hex.HexRuntime;
import com.kadamitas.warlockery.ritual.hex.HexState;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

public final class HexBehaviors {
    private static final Supplier<HexBehavior> DEFAULT = () -> status(effect(MobEffects.BAD_OMEN, 0));
    private static final Map<String, Supplier<HexBehavior>> FACTORIES = Map.of(
        "misfortune", () -> new PersistentHex(HexKind.MISFORTUNE),
        "insanity", () -> new PersistentHex(HexKind.INSANITY),
        "sinking", () -> new PersistentHex(HexKind.SINKING),
        "overheating", () -> new PersistentHex(HexKind.OVERHEATING),
        "nightmare", () -> new PersistentHex(HexKind.WAKING_NIGHTMARE),
        "blindness", () -> status(effect(MobEffects.BLINDNESS, 0)),
        "corrupt_doll", CorruptDollHex::new,
        "wolf", () -> new TransformationHex(SupernaturalForm.WEREWOLF)
    );
    private static final ConcurrentMap<String, HexBehavior> INSTANCES = new ConcurrentHashMap<>();

    private HexBehaviors() {
    }

    public static HexBehavior forTarget(final String target) {
        final Supplier<HexBehavior> factory = FACTORIES.getOrDefault(target, DEFAULT);
        final String cacheKey = FACTORIES.containsKey(target) ? target : "";
        return INSTANCES.computeIfAbsent(cacheKey, _ -> factory.get());
    }

    public static boolean supports(final String target) {
        return FACTORIES.containsKey(target);
    }

    public static boolean isActive(final LivingEntity target, final String id) {
        return HexKind.find(id).map(kind -> HexState.isActive(target, kind)).orElse(false);
    }

    private static HexBehavior status(final EffectSpec... effects) {
        return new StatusHex(List.of(effects), false);
    }

    private static EffectSpec effect(final Holder<MobEffect> effect, final int amplifier) {
        return new EffectSpec(effect, amplifier);
    }

    private record EffectSpec(Holder<MobEffect> effect, int amplifier) {
    }

    private record StatusHex(List<EffectSpec> effects, boolean ignites) implements HexBehavior {
        private StatusHex {
            effects = List.copyOf(effects);
        }

        @Override
        public void apply(final LivingEntity target, final int duration) {
            effects.forEach(effect -> target.addEffect(
                new MobEffectInstance(effect.effect(), duration, effect.amplifier())
            ));
            if (ignites) {
                target.igniteForSeconds(Math.min(12.0F, duration / 100.0F));
            }
        }

        @Override
        public void remove(final LivingEntity target) {
            effects.forEach(effect -> target.removeEffect(effect.effect()));
            if (ignites) {
                target.clearFire();
            }
        }
    }

    private record TransformationHex(SupernaturalForm form) implements HexBehavior {
        @Override
        public void apply(final LivingEntity target, final int duration) {
            if (target instanceof Player player && SupernaturalState.getForm(player) == SupernaturalForm.NONE) {
                SupernaturalState.setForm(player, form);
            }
        }

        @Override
        public void remove(final LivingEntity target) {
            if (target instanceof Player player && SupernaturalState.getForm(player) == form) {
                SupernaturalState.setForm(player, SupernaturalForm.NONE);
            }
        }
    }

    private record PersistentHex(HexKind kind) implements HexBehavior {
        @Override
        public void apply(final LivingEntity target, final int duration) {
            HexRuntime.apply(target, kind, duration);
        }

        @Override
        public void remove(final LivingEntity target) {
            HexRuntime.remove(target, kind);
        }
    }

    private static final class CorruptDollHex implements HexBehavior {
        @Override
        public void apply(final LivingEntity target, final int duration) {
            if (target instanceof ServerPlayer player) {
                DollItem.corruptProtectiveDolls(player);
            }
        }

        @Override
        public void remove(final LivingEntity target) {
        }
    }
}
