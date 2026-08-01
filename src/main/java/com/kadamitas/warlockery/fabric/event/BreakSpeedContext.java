package com.kadamitas.warlockery.fabric.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public final class BreakSpeedContext {
    private final Player player;
    private final BlockState state;
    private float speed;

    public BreakSpeedContext(final Player player, final BlockState state, final float speed) {
        this.player = player;
        this.state = state;
        this.speed = speed;
    }

    public Player getEntity() {
        return player;
    }

    public BlockState getState() {
        return state;
    }

    public float getNewSpeed() {
        return speed;
    }

    public void setNewSpeed(final float speed) {
        this.speed = speed;
    }
}
