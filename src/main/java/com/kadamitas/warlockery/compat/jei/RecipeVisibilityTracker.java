package com.kadamitas.warlockery.compat.jei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RecipeVisibilityTracker<T> {
    private final Map<T, T> registered = new HashMap<>();
    private Set<T> visible = new LinkedHashSet<>();

    void reset(final Collection<T> initial) {
        clear();
        for (final T recipe : initial) {
            registered.putIfAbsent(recipe, recipe);
            visible.add(registered.get(recipe));
        }
    }

    void clear() {
        registered.clear();
        visible = new LinkedHashSet<>();
    }

    Change<T> update(final Collection<T> desired) {
        final Set<T> next = new LinkedHashSet<>();
        final List<T> add = new ArrayList<>();
        for (final T recipe : desired) {
            T canonical = registered.get(recipe);
            if (canonical == null) {
                canonical = recipe;
                registered.put(recipe, recipe);
                add.add(recipe);
            }
            next.add(canonical);
        }
        final List<T> hide = visible.stream().filter(recipe -> !next.contains(recipe)).toList();
        final List<T> show = next.stream().filter(recipe -> !visible.contains(recipe) && !add.contains(recipe)).toList();
        visible = next;
        return new Change<>(hide, show, List.copyOf(add));
    }

    record Change<T>(List<T> hide, List<T> show, List<T> add) { }
}
