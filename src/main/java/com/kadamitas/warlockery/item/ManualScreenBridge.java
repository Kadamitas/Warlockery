package com.kadamitas.warlockery.item;

import java.util.Objects;
import java.util.function.Consumer;

public final class ManualScreenBridge {
    private static Consumer<ManualView> openHandler = view -> {
    };

    private ManualScreenBridge() {
    }

    public static void setOpenHandler(final Consumer<ManualView> handler) {
        openHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void open(final ManualView view) {
        openHandler.accept(view);
    }
}
