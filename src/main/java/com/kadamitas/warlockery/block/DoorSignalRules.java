package com.kadamitas.warlockery.block;

public final class DoorSignalRules {
    private DoorSignalRules() {
    }

    public static int signalForOpen(final boolean open) {
        return open ? 15 : 0;
    }
}
