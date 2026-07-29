package com.kadamitas.warlockery.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

public interface LethalDollBehavior {
    boolean protectsAgainst(DamageSource source);

    void recover(ServerPlayer player, DamageSource source);
}
