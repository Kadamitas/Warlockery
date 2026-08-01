package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

public final class MinedrakeCombat {
    private static final String LAST_BLAST = "WarlockeryMinedrakeLastBlast";
    private static final ThreadLocal<Boolean> DETONATING = ThreadLocal.withInitial(() -> false);

    private MinedrakeCombat() {
    }

    public static boolean detonate(final Mob minedrake, final ServerLevel level) {
        final CompoundTag data = WarlockeryEntityData.get(minedrake);
        final long gameTime = level.getGameTime();
        if (DETONATING.get() || !MinedrakeCombatRules.blastReady(
            data.contains(LAST_BLAST),
            data.getLongOr(LAST_BLAST, Long.MIN_VALUE),
            gameTime
        )) {
            return false;
        }
        data.putLong(LAST_BLAST, gameTime);
        DETONATING.set(true);
        try {
            level.explode(
                minedrake,
                minedrake.getX(),
                minedrake.getY() + minedrake.getBbHeight() * 0.5,
                minedrake.getZ(),
                MinedrakeCombatRules.BLAST_RADIUS,
                MinedrakeCombatRules.EXPLOSION_INTERACTION
            );
            return true;
        } finally {
            DETONATING.set(false);
        }
    }
}
