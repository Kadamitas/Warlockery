package com.kadamitas.warlockery.entity.behavior;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Retaining and ordering perception candidates.
 *
 * <p>Three families write {@code retainCandidates} as the same idiom: seed a {@link LinkedHashSet}
 * with the identities that must survive whatever else is found, then fill to a cap from the generic
 * list. Five families define {@code unsignedUuidOrder} identically, and several then sort by
 * distance with that order as the tiebreak so two candidates at the same range cannot swap between
 * ticks and make the entity oscillate.</p>
 */
public final class Candidates {

    private static final Comparator<UUID> UNSIGNED_UUID_ORDER =
        Comparator.<UUID>comparingLong(id -> Long.MIN_VALUE ^ id.getMostSignificantBits())
            .thenComparingLong(id -> Long.MIN_VALUE ^ id.getLeastSignificantBits());

    private Candidates() {
    }

    /**
     * Total order over identities that agrees on every server. Signed comparison of the raw bits
     * would order two identities differently depending on their sign bit, which is stable but
     * surprising; this compares them as unsigned.
     */
    public static Comparator<UUID> unsignedUuidOrder() {
        return UNSIGNED_UUID_ORDER;
    }

    /**
     * Nearest first, with identity breaking ties so the answer never flickers between two candidates
     * at equal range.
     */
    public static <T> Comparator<T> byDistanceThenIdentity(
        final ToDoubleFunction<T> distanceSquared,
        final Function<T, UUID> identity
    ) {
        return Comparator.comparingDouble(distanceSquared)
            .thenComparing(identity, unsignedUuidOrder());
    }

    /**
     * The candidates that survive a retention cap: everything required, in order, then as much of
     * the generic list as still fits. Required entries are never evicted, which is the point of the
     * idiom, and duplicates between the two lists are kept once.
     */
    public static <T> List<T> retain(
        final List<T> required,
        final List<T> generic,
        final int cap
    ) {
        if (cap <= 0) {
            return List.of();
        }
        final LinkedHashSet<T> retained = new LinkedHashSet<>();
        for (final T entry : required) {
            if (retained.size() >= cap) {
                break;
            }
            retained.add(entry);
        }
        for (final T entry : generic) {
            if (retained.size() >= cap) {
                break;
            }
            retained.add(entry);
        }
        return List.copyOf(retained);
    }

    /** The same, for the common shape where the required entries are optional identities. */
    @SafeVarargs
    public static <T> List<T> retaining(
        final int cap,
        final List<T> generic,
        final Optional<T>... required
    ) {
        return retain(java.util.Arrays.stream(required).flatMap(Optional::stream).toList(),
            generic, cap);
    }
}
