package com.kadamitas.warlockery.util;

import java.util.List;

public record WeightedPool<T>(List<Entry<T>> entries) {
    public WeightedPool {
        entries = List.copyOf(entries);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("A weighted pool needs at least one entry");
        }
    }

    @SafeVarargs
    public static <T> WeightedPool<T> of(final Entry<T>... entries) {
        return new WeightedPool<>(List.of(entries));
    }

    public T select(final long seed) {
        final int totalWeight = entries.stream().mapToInt(Entry::weight).sum();
        int cursor = Math.floorMod(mix(seed), totalWeight);
        for (final Entry<T> entry : entries) {
            if (cursor < entry.weight()) {
                return entry.value();
            }
            cursor -= entry.weight();
        }
        throw new IllegalStateException("A validated weighted pool could not select an entry");
    }

    public int totalWeight() {
        return entries.stream().mapToInt(Entry::weight).sum();
    }

    public static <T> Entry<T> entry(final T value, final int weight) {
        return new Entry<>(value, weight);
    }

    private static int mix(final long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53l;
        mixed ^= mixed >>> 33;
        return (int) (mixed ^ mixed >>> 32);
    }

    public record Entry<T>(T value, int weight) {
        public Entry {
            if (value == null || weight < 1) {
                throw new IllegalArgumentException("Weighted entries need a value and a positive weight");
            }
        }
    }
}
