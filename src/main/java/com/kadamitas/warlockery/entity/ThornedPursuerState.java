package com.kadamitas.warlockery.entity;

import net.minecraft.nbt.CompoundTag;

public record ThornedPursuerState(
    int schemaVersion,
    int snareCooldownRemaining,
    int escortCooldownRemaining,
    int episodeCooldownRemaining
) {
    public static final int SCHEMA_VERSION = 1;

    public ThornedPursuerState {
        schemaVersion = SCHEMA_VERSION;
        snareCooldownRemaining = bounded(snareCooldownRemaining, ThornedPursuerRules.SNARE_COOLDOWN);
        escortCooldownRemaining = bounded(escortCooldownRemaining, ThornedPursuerRules.ESCORT_COOLDOWN);
        episodeCooldownRemaining = bounded(episodeCooldownRemaining, ThornedPursuerRules.EPISODE_COOLDOWN);
    }

    public static ThornedPursuerState defaults() { return new ThornedPursuerState(SCHEMA_VERSION, 0, 0, 0); }

    public ThornedPursuerState tickLoaded() {
        return new ThornedPursuerState(SCHEMA_VERSION, decrement(snareCooldownRemaining),
            decrement(escortCooldownRemaining), decrement(episodeCooldownRemaining));
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        tag.putInt("SnareCooldownRemaining", snareCooldownRemaining);
        tag.putInt("EscortCooldownRemaining", escortCooldownRemaining);
        tag.putInt("EpisodeCooldownRemaining", episodeCooldownRemaining);
        return tag;
    }

    public static ThornedPursuerState read(CompoundTag tag) {
        if (tag == null || tag.getInt("SchemaVersion").orElse(0) != SCHEMA_VERSION) return defaults();
        return new ThornedPursuerState(SCHEMA_VERSION,
            tag.getInt("SnareCooldownRemaining").orElse(0),
            tag.getInt("EscortCooldownRemaining").orElse(0),
            tag.getInt("EpisodeCooldownRemaining").orElse(0));
    }

    private static int bounded(int value, int maximum) { return value > 0 && value <= maximum ? value : 0; }
    private static int decrement(int value) { return Math.max(0, value - 1); }
}
