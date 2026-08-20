package com.kadamitas.warlockery.crafting;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import com.mojang.serialization.Codec;

public enum PowerMode implements StringIdentified {
    NONE("none"),
    ON_COMPLETE("on_complete"),
    CONTINUOUS("continuous");

    private static final EnumLookup<PowerMode> LOOKUP = EnumLookup.create("machine power mode", values());
    public static final Codec<PowerMode> CODEC = LOOKUP.codec();
    private static final int MILLIPOWER = 1_000;

    private final String id;

    PowerMode(final String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public static PowerMode legacyDefault(final int altarPower) {
        return altarPower > 0 ? ON_COMPLETE : NONE;
    }

    public int requiredAvailablePower(final int altarPower) {
        return switch (this) {
            case NONE -> 0;
            case ON_COMPLETE -> altarPower;
            case CONTINUOUS -> altarPower > 0 ? 1 : 0;
        };
    }

    public int powerForAdvance(
        final int altarPower,
        final int processingTime,
        final int currentProgress,
        final int nextProgress
    ) {
        if (this != CONTINUOUS || altarPower <= 0) {
            return 0;
        }
        final int boundedCurrent = Math.clamp(currentProgress, 0, processingTime);
        final int boundedNext = Math.clamp(nextProgress, boundedCurrent, processingTime);
        final long spentBefore = (long) boundedCurrent * altarPower / processingTime;
        final long spentAfter = (long) boundedNext * altarPower / processingTime;
        return Math.toIntExact(spentAfter - spentBefore);
    }

    public int completionCost(final int altarPower) {
        return this == ON_COMPLETE ? altarPower : 0;
    }

    public int millipowerPerTick(final int altarPower, final int processingTime) {
        return this == CONTINUOUS
            ? Math.toIntExact((long) altarPower * MILLIPOWER / processingTime)
            : 0;
    }
}

