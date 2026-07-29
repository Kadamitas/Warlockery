package com.kadamitas.warlockery.ritual.hex;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;

public final class ToadRainRules {
    public static final int EXPLOSION_DELAY_TICKS = 40;
    public static final float EXPLOSION_RADIUS = 1.5F;
    public static final Holder<MobEffect> POISON_UI_EFFECT = MobEffects.POISON;
    public static final Level.ExplosionInteraction EXPLOSION_INTERACTION = Level.ExplosionInteraction.NONE;

    private ToadRainRules() {
    }

    public static ToadRole roleFor(final int spawnIndex) {
        if (spawnIndex < 0) {
            throw new IllegalArgumentException("Toad index must be nonnegative");
        }
        return ToadRole.values()[spawnIndex % ToadRole.values().length];
    }

    public enum ToadRole {
        POISONOUS,
        EXPLOSIVE
    }
}
