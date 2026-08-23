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

    public static int statusColor(final VampireSustenanceRules.Status status) {
        return switch (status) {
            case STARVED -> 0xFFFFA0AE;
            case SATED -> 0xFFF6DCE3;
            case SANGUINE -> 0xFFFFE5B5;
        };
    }

    public static String statusKey(final VampireSustenanceRules.Status status) {
        return "overlay.warlockery.vampire_blood." + status.name().toLowerCase(java.util.Locale.ROOT);
    }
}
