package com.kadamitas.warlockery.compat.viewer;

import com.kadamitas.warlockery.Warlockery;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecipeViewerRefreshSignal {
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private RecipeViewerRefreshSignal() { }

    public static void subscribe(final Runnable listener) {
        LISTENERS.add(Objects.requireNonNull(listener, "listener"));
    }

    public static void publish() {
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                Warlockery.LOGGER.warn("Unable to refresh a recipe viewer", exception);
            }
        }
    }
}
