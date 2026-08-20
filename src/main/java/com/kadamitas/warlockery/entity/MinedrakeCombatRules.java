package com.kadamitas.warlockery.entity;

import net.minecraft.world.level.Level;

public final class MinedrakeCombatRules {
    public static final int BULB_WAKE_TICKS = 60;
    public static final int BULB_PER_WAKE_BATCH = 4;
    public static final double TARGET_RANGE = 32.0;
    public static final int BLAST_COOLDOWN_TICKS = 20;
    public static final float BLAST_RADIUS = 1.75F;
    public static final Level.ExplosionInteraction EXPLOSION_INTERACTION = Level.ExplosionInteraction.NONE;

    private MinedrakeCombatRules() {
    }

    public static boolean bulbReady(final int age, final int count, final boolean serverSide) {
        return serverSide && age >= BULB_WAKE_TICKS && count > 0;
    }

    public static boolean blastReady(
        final boolean hasPreviousBlast,
        final long previousBlast,
        final long gameTime
    ) {
        return !hasPreviousBlast || gameTime - previousBlast >= BLAST_COOLDOWN_TICKS;
    }
}
