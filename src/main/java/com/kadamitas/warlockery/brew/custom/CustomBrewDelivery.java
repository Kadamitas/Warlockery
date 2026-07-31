package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import com.mojang.serialization.Codec;
import java.util.Optional;

public enum CustomBrewDelivery implements StringIdentified {
    DRINKABLE("drinkable", false, 0.0F, 0, 0, 0.0F),
    THROWABLE("throwable", false, 0.0F, 0, 0, 0.0F),
    GAS("gas", true, 1.0F, 400, 0, -0.25F),
    LIQUID("liquid", true, 0.8F, 800, 0, -0.10F),
    TRIGGER("trigger", false, 0.0F, 0, 0, 0.0F);

    private static final EnumLookup<CustomBrewDelivery> LOOKUP = EnumLookup.create("custom brew delivery", values());
    public static final Codec<CustomBrewDelivery> CODEC = LOOKUP.codec();

    private final String id;
    private final boolean lingering;
    private final float radiusFactor;
    private final int duration;
    private final int waitTime;
    private final float radiusOnUse;

    CustomBrewDelivery(
        final String id,
        final boolean lingering,
        final float radiusFactor,
        final int duration,
        final int waitTime,
        final float radiusOnUse
    ) {
        this.id = id;
        this.lingering = lingering;
        this.radiusFactor = radiusFactor;
        this.duration = duration;
        this.waitTime = waitTime;
        this.radiusOnUse = radiusOnUse;
    }

    public String id() {
        return id;
    }

    public static Optional<CustomBrewDelivery> find(final String id) {
        return LOOKUP.find(id);
    }

    public boolean lingering() {
        return lingering;
    }

    public boolean triggered() {
        return this == TRIGGER;
    }

    public float cloudRadius(final float formulaRadius) {
        return Math.clamp(formulaRadius * radiusFactor, 0.5F, 12.0F);
    }

    public int cloudDuration(final int lingeringLevel) {
        return duration * Math.clamp(lingeringLevel, 1, 4);
    }

    public int cloudWaitTime() {
        return waitTime;
    }

    public float cloudRadiusOnUse(final float radius) {
        return this == TRIGGER ? -radius : radiusOnUse;
    }
}
