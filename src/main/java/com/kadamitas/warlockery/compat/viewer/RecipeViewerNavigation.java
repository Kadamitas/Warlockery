package com.kadamitas.warlockery.compat.viewer;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public final class RecipeViewerNavigation {
    private static final Map<Integer, Function<String, Boolean>> HANDLERS = new TreeMap<>();

    private RecipeViewerNavigation() { }

    public static void register(int priority, Function<String, Boolean> handler) {
        HANDLERS.put(priority, handler);
    }

    public static boolean available() { return !HANDLERS.isEmpty(); }

    public static boolean openMachine(String machine) {
        return HANDLERS.values().stream().anyMatch(handler -> handler.apply(machine));
    }
}
