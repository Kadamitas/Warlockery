package com.kadamitas.warlockery.entity;

public final class HellhoundCureRules {
    public static final int REQUIRED_PROGRESS = 3;

    private HellhoundCureRules() {
    }

    public static Result advance(
        final int currentProgress,
        final boolean weakened,
        final boolean goldenApple,
        final int enclosingWalls
    ) {
        if (!weakened) {
            return new Result(Math.clamp(currentProgress, 0, REQUIRED_PROGRESS), false, Diagnostic.NEEDS_WEAKNESS);
        }
        if (!goldenApple) {
            return new Result(Math.clamp(currentProgress, 0, REQUIRED_PROGRESS), false, Diagnostic.NEEDS_GOLDEN_APPLE);
        }
        final int amount = enclosingWalls >= 3 ? REQUIRED_PROGRESS : 1;
        final int next = Math.min(REQUIRED_PROGRESS, Math.max(0, currentProgress) + amount);
        return new Result(next, next == REQUIRED_PROGRESS, next == REQUIRED_PROGRESS
            ? Diagnostic.CURED
            : Diagnostic.PROGRESS);
    }

    public enum Diagnostic {
        NEEDS_WEAKNESS,
        NEEDS_GOLDEN_APPLE,
        PROGRESS,
        CURED
    }

    public record Result(int progress, boolean cured, Diagnostic diagnostic) {
    }
}
