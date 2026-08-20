package com.kadamitas.warlockery.crafting;

import java.util.Optional;
import java.util.UUID;

public record KettleBrewerContext(
    Optional<UUID> pendingBrewer,
    long pendingUntil,
    Optional<UUID> activeBrewer
) {
    public static final long CLAIM_WINDOW_TICKS = 20L * 60L;
    public static final KettleBrewerContext EMPTY = new KettleBrewerContext(Optional.empty(), 0L, Optional.empty());

    public KettleBrewerContext {
        pendingBrewer = pendingBrewer == null ? Optional.empty() : pendingBrewer;
        activeBrewer = activeBrewer == null ? Optional.empty() : activeBrewer;
    }

    public KettleBrewerContext claim(final UUID brewer, final long gameTime) {
        return activeBrewer.isPresent()
            ? this
            : new KettleBrewerContext(Optional.of(brewer), gameTime + CLAIM_WINDOW_TICKS, Optional.empty());
    }

    public KettleBrewerContext begin(final long gameTime) {
        if (activeBrewer.isPresent()) {
            return this;
        }
        final Optional<UUID> active = gameTime <= pendingUntil ? pendingBrewer : Optional.empty();
        return new KettleBrewerContext(Optional.empty(), 0L, active);
    }

    public Optional<UUID> brewer(final long gameTime) {
        if (activeBrewer.isPresent()) {
            return activeBrewer;
        }
        return gameTime <= pendingUntil ? pendingBrewer : Optional.empty();
    }

    public KettleBrewerContext clear() {
        return EMPTY;
    }

    public static KettleBrewerContext restored(final Optional<UUID> activeBrewer) {
        return new KettleBrewerContext(Optional.empty(), 0L, activeBrewer);
    }
}

