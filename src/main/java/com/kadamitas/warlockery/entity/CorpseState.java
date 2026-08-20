package com.kadamitas.warlockery.entity;

import net.minecraft.nbt.CompoundTag;

/**
 * Version-1 durable Corpse semantics: schema plus three bounded integers.
 * Dormancy is derived from zero cohesion and is never stored separately.
 * No UUID, path, live object, client, or collection state may live here.
 */
public record CorpseState(int cohesion, int decayRemainder, int groundMealCooldown) {
    public static final int SCHEMA_VERSION = CorpseRules.SCHEMA_VERSION;
    private static final String VERSION_KEY = "Version";
    private static final String COHESION_KEY = "Cohesion";
    private static final String REMAINDER_KEY = "DecayRemainder";
    private static final String COOLDOWN_KEY = "GroundMealCooldown";

    public CorpseState {
        cohesion = Math.clamp(cohesion, 0, CorpseRules.MAX_COHESION);
        decayRemainder = Math.clamp(decayRemainder, 0, CorpseRules.MAX_DECAY_REMAINDER);
        groundMealCooldown = Math.clamp(groundMealCooldown, 0, CorpseRules.GROUND_MEAL_COOLDOWN_TICKS);
    }

    public static CorpseState fresh() {
        return new CorpseState(CorpseRules.MAX_COHESION, 0, 0);
    }

    public boolean dormant() {
        return CorpseRules.dormant(cohesion);
    }

    public CorpseState withCohesion(final int updated) {
        return new CorpseState(updated, decayRemainder, groundMealCooldown);
    }

    public CorpseState withDecay(final CorpseRules.Decay decay) {
        return new CorpseState(decay.cohesion(), decay.remainder(), groundMealCooldown);
    }

    public CorpseState withCooldown(final int updated) {
        return new CorpseState(cohesion, decayRemainder, updated);
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_KEY, SCHEMA_VERSION);
        tag.putInt(COHESION_KEY, cohesion);
        tag.putInt(REMAINDER_KEY, decayRemainder);
        tag.putInt(COOLDOWN_KEY, groundMealCooldown);
        return tag;
    }

    public static CorpseState read(final CompoundTag tag) {
        if (tag.getIntOr(VERSION_KEY, 0) != SCHEMA_VERSION) {
            return fresh();
        }
        return new CorpseState(
            tag.getIntOr(COHESION_KEY, CorpseRules.MAX_COHESION),
            tag.getIntOr(REMAINDER_KEY, 0),
            tag.getIntOr(COOLDOWN_KEY, 0)
        );
    }
}
