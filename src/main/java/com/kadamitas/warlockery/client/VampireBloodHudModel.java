package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.transformation.VampireSustenanceRules;

public final class VampireBloodHudModel {
    private VampireBloodHudModel() {
    }

    public static int filledHeight(final int blood, final int maximumBlood, final int height) {
        if (maximumBlood <= 0 || height <= 0) {
            return 0;
        }
        return Math.round(Math.max(0, height) * Math.clamp(blood, 0, maximumBlood) / (float) maximumBlood);
    }

    public static float pulseAlpha(final VampireSustenanceRules.Status status, final long ticks) {
        if (status == VampireSustenanceRules.Status.SATED) {
            return 1.0F;
        }
        final float wave = (float) ((Math.sin(ticks * (status == VampireSustenanceRules.Status.STARVED ? 0.18 : 0.08)) + 1.0) * 0.5);
        return status == VampireSustenanceRules.Status.STARVED ? 0.70F + wave * 0.20F : 0.82F + wave * 0.18F;
    }

    public static String statusKey(final VampireSustenanceRules.Status status) {
        return "overlay.warlockery.vampire_blood." + status.name().toLowerCase(java.util.Locale.ROOT);
    }
}
