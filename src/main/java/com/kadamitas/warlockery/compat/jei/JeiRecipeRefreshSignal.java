package com.kadamitas.warlockery.compat.jei;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class JeiRecipeRefreshSignal {
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private JeiRecipeRefreshSignal() {
    }

    public static void subscribe(final Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void publish() {
        LISTENERS.forEach(Runnable::run);
    }
}
