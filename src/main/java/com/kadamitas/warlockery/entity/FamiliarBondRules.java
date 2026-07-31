package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

public final class FamiliarBondRules {
    public static final int MAX_COVEN_MAGES = 6;
    public static final double NEARBY_DISTANCE_SQUARED = 256.0;

    private FamiliarBondRules() {
    }

    public static boolean isClassicFamiliar(final CreatureKind kind) {
        return kind == CreatureKind.CAT || kind == CreatureKind.OWL || kind == CreatureKind.TOAD;
    }

    public static float transferredDamageFraction(final boolean sameDimension, final double distanceSquared) {
        return sameDimension && distanceSquared <= NEARBY_DISTANCE_SQUARED ? 0.1F : 0.01F;
    }

    public static boolean ignoresEnvironmentalDamage(final DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE)
            || source.is(DamageTypeTags.IS_FALL)
            || source.is(DamageTypeTags.IS_DROWNING);
    }

    public static boolean canRecruitCovenMage(final int currentCount) {
        return currentCount >= 0 && currentCount < MAX_COVEN_MAGES;
    }
}
