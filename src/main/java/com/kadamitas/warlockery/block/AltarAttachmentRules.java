package com.kadamitas.warlockery.block;

import java.util.List;

public final class AltarAttachmentRules {
    public static final int CAPACITY = 4;

    private AltarAttachmentRules() {
    }

    public static Decision evaluate(
        final boolean supported,
        final boolean conflicts,
        final int occupiedSlots
    ) {
        if (!supported) {
            return Decision.rejection("unsupported");
        }
        if (conflicts) {
            return Decision.rejection("duplicate");
        }
        if (occupiedSlots >= CAPACITY) {
            return Decision.rejection("full");
        }
        return Decision.ready();
    }

    public static int lastOccupiedSlot(final List<Boolean> occupiedSlots) {
        for (int slot = Math.min(occupiedSlots.size(), CAPACITY) - 1; slot >= 0; slot--) {
            if (occupiedSlots.get(slot)) {
                return slot;
            }
        }
        return -1;
    }

    public record Decision(boolean accepted, String diagnostic) {
        private static Decision ready() {
            return new Decision(true, "ready");
        }

        private static Decision rejection(final String diagnostic) {
            return new Decision(false, diagnostic);
        }
    }
}
