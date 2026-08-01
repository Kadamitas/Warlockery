package com.kadamitas.warlockery.fabric.event;

import net.minecraft.server.level.ServerPlayer;

public record PlayerCloneContext(ServerPlayer original, ServerPlayer player, boolean wasDeath) {
    public ServerPlayer getOriginal() {
        return original;
    }

    public ServerPlayer getEntity() {
        return player;
    }

    public boolean isWasDeath() {
        return wasDeath;
    }
}
