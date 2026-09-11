package com.kadamitas.warlockery.compat.viewer;

import com.kadamitas.warlockery.compat.jei.CustomBrewJeiRecipe;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.ritual.RitualManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class RecipeViewerCatalog {
    private static final AtomicReference<Snapshot> SERVER = new AtomicReference<>();

    private RecipeViewerCatalog() { }

    public static List<MachineRecipeManager.Match> machines() { return snapshot().machines(); }
    public static List<RitualManager.Entry> rituals() { return snapshot().rituals(); }
    public static List<CustomBrewJeiRecipe> customBrews() { return snapshot().customBrews(); }

    public static Snapshot snapshot() {
        final Snapshot server = SERVER.get();
        return server == null ? Packaged.SNAPSHOT : server;
    }

    static void beginConnection() { publish(Snapshot.EMPTY); }

    static void publish(final Snapshot snapshot) {
        SERVER.set(java.util.Objects.requireNonNull(snapshot, "snapshot"));
        RecipeViewerRefreshSignal.publish();
    }

    static void disconnect() {
        SERVER.set(null);
        RecipeViewerRefreshSignal.publish();
    }

    public record Snapshot(
        List<MachineRecipeManager.Match> machines,
        List<RitualManager.Entry> rituals,
        List<CustomBrewJeiRecipe> customBrews
    ) {
        public static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), List.of());

        public Snapshot {
            machines = List.copyOf(machines);
            rituals = rituals.stream().filter(entry -> entry.definition().visible()).toList();
            customBrews = List.copyOf(customBrews);
        }
    }

    private static final class Packaged {
        private static final Snapshot SNAPSHOT = new Snapshot(
            PackagedRecipeViewerCatalog.machines(),
            PackagedRecipeViewerCatalog.rituals(),
            PackagedRecipeViewerCatalog.customBrews()
        );
    }
}
