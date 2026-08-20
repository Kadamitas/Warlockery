package com.kadamitas.warlockery.entity;

import net.minecraft.nbt.CompoundTag;

public record DreamrootState(int schemaVersion, int dreamCooldownRemaining, int sustainRemainder, int episodeCooldownRemaining) {
    public static final int SCHEMA_VERSION = 1;
    public static final String LEGACY_LAST_BLAST_KEY = "WarlockeryMinedrakeLastBlast";
    public DreamrootState {
        schemaVersion = SCHEMA_VERSION;
        dreamCooldownRemaining = Math.clamp(dreamCooldownRemaining, 0, DreamrootRules.DREAM_COOLDOWN_TICKS);
        sustainRemainder = Math.clamp(sustainRemainder, 0, DreamrootRules.SUSTAIN_CADENCE_TICKS - 1);
        episodeCooldownRemaining = Math.clamp(episodeCooldownRemaining, 0, 200);
    }
    public static DreamrootState empty() { return new DreamrootState(SCHEMA_VERSION, 0, 0, 0); }
    public CompoundTag write() { var tag = new CompoundTag(); tag.putInt("Version", SCHEMA_VERSION); tag.putInt("DreamCooldownRemaining", dreamCooldownRemaining); tag.putInt("SustainRemainder", sustainRemainder); tag.putInt("EpisodeCooldownRemaining", episodeCooldownRemaining); return tag; }
    public static DreamrootState read(CompoundTag tag) { return tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION ? empty() : new DreamrootState(SCHEMA_VERSION, tag.getIntOr("DreamCooldownRemaining", 0), tag.getIntOr("SustainRemainder", 0), tag.getIntOr("EpisodeCooldownRemaining", 0)); }
    public static DreamrootState migrate(CompoundTag owner) { var state = read(owner.getCompoundOrEmpty(DreamrootEntity.STATE_KEY)); if (!owner.contains(DreamrootEntity.STATE_KEY) && owner.contains(LEGACY_LAST_BLAST_KEY)) { long stamp = owner.getLongOr(LEGACY_LAST_BLAST_KEY, 0L); state = new DreamrootState(SCHEMA_VERSION, stamp <= 0L ? 0 : DreamrootRules.DREAM_COOLDOWN_TICKS, 0, 0); owner.put(DreamrootEntity.STATE_KEY, state.write()); owner.remove(LEGACY_LAST_BLAST_KEY); } return state; }
}
