package com.kadamitas.warlockery.crafting;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SilverVatFurnaceObserver {
    private final Map<Long, Integer> progressByFurnace = new HashMap<>();

    public int observe(final Collection<FurnaceCycle> furnaces) {
        final Set<Long> adjacent = furnaces.stream().map(FurnaceCycle::position).collect(Collectors.toUnmodifiableSet());
        progressByFurnace.keySet().retainAll(adjacent);
        return furnaces.stream().mapToInt(this::advance).sum();
    }

    public Map<Long, Integer> snapshot() {
        return Map.copyOf(progressByFurnace);
    }

    public void restore(final Map<Long, Integer> snapshot) {
        progressByFurnace.clear();
        snapshot.forEach((position, progress) -> progressByFurnace.put(position, Math.max(0, progress)));
    }

    private int advance(final FurnaceCycle furnace) {
        if (!furnace.active()) {
            final int previous = progressByFurnace.getOrDefault(furnace.position(), 0);
            progressByFurnace.put(furnace.position(), 0);
            return furnace.inputDepleted() && previous + 1 >= Math.max(1, furnace.cookingTime()) ? 1 : 0;
        }
        final int duration = Math.max(1, furnace.cookingTime());
        final int accumulated = progressByFurnace.getOrDefault(furnace.position(), 0) + 1;
        progressByFurnace.put(furnace.position(), accumulated % duration);
        return accumulated / duration;
    }

    public record FurnaceCycle(long position, boolean active, boolean inputDepleted, int cookingTime) {
    }
}
