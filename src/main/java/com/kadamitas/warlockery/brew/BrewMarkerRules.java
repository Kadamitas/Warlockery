package com.kadamitas.warlockery.brew;

public final class BrewMarkerRules {
    public static final int MAX_ABSORBED_MAGIC = 100;

    private BrewMarkerRules() {
    }

    public static boolean isActive(final long gameTime, final long expiration) {
        return expiration > gameTime;
    }

    public static int remainingTicks(final long gameTime, final long expiration) {
        return isActive(gameTime, expiration)
            ? (int) Math.min(Integer.MAX_VALUE, expiration - gameTime)
            : 0;
    }

    public static int addAbsorbedMagic(final int stored, final float preventedDamage) {
        final int gained = Math.max(1, (int) Math.ceil(Math.max(0.0F, preventedDamage) * 5.0F));
        return Math.clamp(stored + gained, 0, MAX_ABSORBED_MAGIC);
    }

    public static float absorbedDamage(final float incomingDamage) {
        return Math.max(0.0F, incomingDamage) * 0.75F;
    }

    public static float moonshineDamage(final float incomingDamage) {
        return Math.max(0.0F, incomingDamage) * 0.5F;
    }

    public static float moonshineExhaustion() {
        return 0.25F;
    }

    public static int contagionLimit(final BrewMarkerKind kind) {
        return kind == BrewMarkerKind.DISEASE ? 8 : 4;
    }

    public static int season(final long dayTime) {
        return (int) Math.floorMod(dayTime / 24_000L, 4L);
    }
}
