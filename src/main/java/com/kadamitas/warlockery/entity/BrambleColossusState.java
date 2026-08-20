package com.kadamitas.warlockery.entity;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record BrambleColossusState(int schemaVersion, boolean posted, int postX, int postY, int postZ,
                                   int nerve, int leg, int displayCooldownRemaining,
                                   int circuitCooldownRemaining) {
    public static final int SCHEMA_VERSION = 1;
    public BrambleColossusState {
        schemaVersion = SCHEMA_VERSION;
        nerve = BrambleColossusRules.clampNerve(nerve);
        leg = Math.clamp(leg, 0, 3);
        displayCooldownRemaining = Math.clamp(displayCooldownRemaining, 0, BrambleColossusRules.DISPLAY_COOLDOWN_TICKS);
        circuitCooldownRemaining = Math.clamp(circuitCooldownRemaining, 0, BrambleColossusRules.CIRCUIT_COOLDOWN_TICKS);
        if (!posted) { postX = 0; postY = 0; postZ = 0; }
    }
    public static BrambleColossusState empty() { return new BrambleColossusState(1, false, 0, 0, 0, 100, 0, 0, 0); }
    public Optional<BlockPos> post() { return posted ? Optional.of(new BlockPos(postX, postY, postZ)) : Optional.empty(); }
    public BrambleColossusState postedAt(BlockPos pos) { return new BrambleColossusState(1, true, pos.getX(), pos.getY(), pos.getZ(), nerve, leg, displayCooldownRemaining, circuitCooldownRemaining); }
    public BrambleColossusState withoutPost() { return new BrambleColossusState(1, false, 0, 0, 0, nerve, leg, displayCooldownRemaining, circuitCooldownRemaining); }
    public BrambleColossusState withNerve(int value) { return new BrambleColossusState(1, posted, postX, postY, postZ, value, leg, displayCooldownRemaining, circuitCooldownRemaining); }
    public BrambleColossusState withLeg(int value) { return new BrambleColossusState(1, posted, postX, postY, postZ, nerve, value, displayCooldownRemaining, circuitCooldownRemaining); }
    public BrambleColossusState withDisplayCooldown(int value) { return new BrambleColossusState(1, posted, postX, postY, postZ, nerve, leg, value, circuitCooldownRemaining); }
    public BrambleColossusState withCircuitCooldown(int value) { return new BrambleColossusState(1, posted, postX, postY, postZ, nerve, leg, displayCooldownRemaining, value); }
    public BrambleColossusState tickCooldowns() { return withDisplayCooldown(displayCooldownRemaining - 1).withCircuitCooldown(circuitCooldownRemaining - 1); }
    public CompoundTag write() {
        var tag = new CompoundTag(); tag.putInt("SchemaVersion", 1); tag.putBoolean("Posted", posted);
        tag.putInt("PostX", postX); tag.putInt("PostY", postY); tag.putInt("PostZ", postZ);
        tag.putInt("Nerve", nerve); tag.putInt("Leg", leg);
        tag.putInt("DisplayCooldownRemaining", displayCooldownRemaining);
        tag.putInt("CircuitCooldownRemaining", circuitCooldownRemaining); return tag;
    }
    public static BrambleColossusState read(CompoundTag tag) {
        if (tag == null || tag.getIntOr("SchemaVersion", 0) != 1) return empty();
        return new BrambleColossusState(1, tag.getBooleanOr("Posted", false), tag.getIntOr("PostX",0),
            tag.getIntOr("PostY",0), tag.getIntOr("PostZ",0), tag.getIntOr("Nerve",100),
            tag.getIntOr("Leg",0), tag.getIntOr("DisplayCooldownRemaining",0), tag.getIntOr("CircuitCooldownRemaining",0));
    }
}
