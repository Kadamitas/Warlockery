package com.kadamitas.warlockery.crafting;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import com.mojang.serialization.Codec;

public enum MachineStatus implements StringIdentified {
    EMPTY("empty", false, false),
    INVALID("invalid", false, false),
    INCOMPLETE("incomplete", false, false),
    NO_HEAT("no_heat", false, false),
    NO_FUEL("no_fuel", false, false),
    NO_FAMILIAR("no_familiar", false, false),
    NO_IGNITION("no_ignition", false, false),
    OUTPUT_BLOCKED("output_blocked", false, false),
    READY("ready", true, false),
    PROCESSING("processing", true, true);

    private static final EnumLookup<MachineStatus> LOOKUP = EnumLookup.create("machine status", values());
    public static final Codec<MachineStatus> CODEC = LOOKUP.fallbackCodec(EMPTY);

    private final String id;
    private final boolean canRun;
    private final boolean active;

    MachineStatus(final String id, final boolean canRun, final boolean active) {
        this.id = id;
        this.canRun = canRun;
        this.active = active;
    }

    public String id() {
        return id;
    }

    public boolean canRun() {
        return canRun;
    }

    public boolean active() {
        return active;
    }

    public static MachineStatus fromId(final String id) {
        return LOOKUP.findOrElse(id, EMPTY);
    }
}
