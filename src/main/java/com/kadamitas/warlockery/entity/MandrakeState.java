package com.kadamitas.warlockery.entity;

import net.minecraft.nbt.CompoundTag;

public record MandrakeState(int schemaVersion, int wailCooldownRemaining, int episodeCooldownRemaining) {
    public static final int SCHEMA_VERSION = 1;
    public MandrakeState {
        schemaVersion = SCHEMA_VERSION;
        wailCooldownRemaining = MandrakeRules.clampRemaining(wailCooldownRemaining, MandrakeRules.WAIL_COOLDOWN_TICKS);
        episodeCooldownRemaining = MandrakeRules.clampRemaining(episodeCooldownRemaining, MandrakeRules.EPISODE_TICKS);
    }
    public static MandrakeState empty() { return new MandrakeState(SCHEMA_VERSION, 0, 0); }
    public CompoundTag write() { var tag = new CompoundTag(); tag.putInt("Version", SCHEMA_VERSION); tag.putInt("WailCooldownRemaining", wailCooldownRemaining); tag.putInt("EpisodeCooldownRemaining", episodeCooldownRemaining); return tag; }
    public static MandrakeState read(CompoundTag tag) { return tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION ? empty() : new MandrakeState(SCHEMA_VERSION, tag.getIntOr("WailCooldownRemaining", 0), tag.getIntOr("EpisodeCooldownRemaining", 0)); }
}
