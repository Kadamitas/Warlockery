package com.kadamitas.warlockery.fabric.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockBreakContext {
    private final ServerLevel level;
    private final ServerPlayer player;
    private final BlockPos pos;
    private final BlockState state;
    private boolean canceled;

    public BlockBreakContext(
        final ServerLevel level,
        final ServerPlayer player,
        final BlockPos pos,
        final BlockState state
    ) {
        this.level = level;
        this.player = player;
        this.pos = pos;
        this.state = state;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    public void cancel() {
        canceled = true;
    }

    public boolean isCanceled() {
        return canceled;
    }
}
