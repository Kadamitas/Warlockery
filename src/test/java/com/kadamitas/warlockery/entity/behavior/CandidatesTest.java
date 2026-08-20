package com.kadamitas.warlockery.entity.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CandidatesTest {

    private record Seen(UUID id, double distanceSquared) {}

    @Test
    void requiredCandidatesAreNeverEvictedByGenericOnes() {
        final List<String> retained =
            Candidates.retain(List.of("attacker", "quarry"), List.of("a", "b", "c", "d"), 4);
        assertEquals(List.of("attacker", "quarry", "a", "b"), retained);
    }

    @Test
    void theCapBindsEvenWhenEverythingIsRequired() {
        assertEquals(List.of("a", "b"), Candidates.retain(List.of("a", "b", "c"), List.of(), 2));
        assertEquals(List.of(), Candidates.retain(List.of("a"), List.of("b"), 0));
        assertEquals(List.of(), Candidates.retain(List.of("a"), List.of("b"), -3));
    }

    @Test
    void aCandidateAppearingInBothListsIsKeptOnce() {
        assertEquals(List.of("a", "b"), Candidates.retain(List.of("a"), List.of("a", "b"), 8));
    }

    @Test
    void optionalRequiredIdentitiesFlattenIntoTheSameRule() {
        final List<String> retained = Candidates.retaining(3, List.of("x", "y", "z"),
            Optional.of("attacker"), Optional.empty(), Optional.of("owner"));
        assertEquals(List.of("attacker", "owner", "x"), retained);
    }

    @Test
    void identityOrderIsTotalAndUnsigned() {
        final UUID low = new UUID(0L, 0L);
        final UUID high = new UUID(-1L, -1L);
        final Comparator<UUID> order = Candidates.unsignedUuidOrder();
        assertTrue(order.compare(low, high) < 0,
            "all bits set is the largest identity, not the smallest");
        assertEquals(0, order.compare(low, new UUID(0L, 0L)));

        final List<UUID> shuffled = new ArrayList<>(List.of(high, low, new UUID(0L, 5L)));
        shuffled.sort(order);
        assertEquals(List.of(low, new UUID(0L, 5L), high), shuffled);
    }

    @Test
    void equalRangeCandidatesAreBrokenByIdentitySoTheChoiceCannotFlicker() {
        final UUID first = new UUID(0L, 1L);
        final UUID second = new UUID(0L, 2L);
        final Comparator<Seen> order =
            Candidates.byDistanceThenIdentity(Seen::distanceSquared, Seen::id);

        final List<Seen> oneWay = new ArrayList<>(
            List.of(new Seen(second, 9.0), new Seen(first, 9.0)));
        final List<Seen> theOther = new ArrayList<>(
            List.of(new Seen(first, 9.0), new Seen(second, 9.0)));
        oneWay.sort(order);
        theOther.sort(order);
        assertEquals(oneWay, theOther, "input order cannot change the answer");
        assertEquals(first, oneWay.getFirst().id());
    }

    @Test
    void nearerAlwaysBeatsFurtherRegardlessOfIdentity() {
        final Comparator<Seen> order =
            Candidates.byDistanceThenIdentity(Seen::distanceSquared, Seen::id);
        final Seen near = new Seen(new UUID(-1L, -1L), 1.0);
        final Seen far = new Seen(new UUID(0L, 0L), 100.0);
        assertTrue(order.compare(near, far) < 0);
    }
}
