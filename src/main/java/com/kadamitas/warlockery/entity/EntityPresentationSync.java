package com.kadamitas.warlockery.entity;

/** Small, client-safe encoding helpers for primitive entity presentation fields. */
final class EntityPresentationSync {
    private EntityPresentationSync() {
    }

    static byte encode(final Enum<?> value) {
        return (byte) value.ordinal();
    }

    static <E extends Enum<E>> E decode(final int stored, final E fallback) {
        final E[] values = fallback.getDeclaringClass().getEnumConstants();
        return stored >= 0 && stored < values.length ? values[stored] : fallback;
    }

    static boolean flag(final byte flags, final int mask) {
        return (flags & mask) != 0;
    }
}
