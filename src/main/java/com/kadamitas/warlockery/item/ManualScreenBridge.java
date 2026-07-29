package com.kadamitas.warlockery.item;

import java.util.Objects;
import java.util.function.Consumer;

public final class ManualScreenBridge {
    private static Consumer<ManualProfile> openHandler = profile -> {
    };

    private ManualScreenBridge() {
    }

    public static void setOpenHandler(final Consumer<ManualProfile> handler) {
        openHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void open(final ManualProfile profile) {
        openHandler.accept(profile);
    }
}
