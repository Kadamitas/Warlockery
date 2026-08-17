package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Legacy and versioned codec, invalid/duplicate/overflow sanitation, the exact six cap, idempotent
 * reassignment and registration, unregister, and immutable stable snapshots.
 */
final class CovenRosterDataTest {
    private static UUID id(final int seed) {
        return new UUID(seed, seed);
    }

    private static CovenRosterData.Entry row(final UUID owner, final UUID mage) {
        return new CovenRosterData.Entry(owner.toString(), mage.toString());
    }

    @Test
    void unloadedMagesRemainPartOfTheirOwnersCovenCap() {
        final CovenRosterData roster = new CovenRosterData();
        final UUID owner = UUID.randomUUID();
        final UUID otherOwner = UUID.randomUUID();
        final UUID mage = UUID.randomUUID();

        roster.register(owner, mage);
        assertEquals(1, roster.count(owner));
        roster.register(otherOwner, mage);
        assertEquals(0, roster.count(owner));
        assertEquals(1, roster.count(otherOwner));
        roster.unregister(mage);
        assertEquals(0, roster.count(otherOwner));
    }

    @Test
    void registrationIsIdempotentAndCappedAtExactlySixPerOwner() {
        final CovenRosterData roster = new CovenRosterData();
        final UUID owner = id(1);
        for (int index = 0; index < CovenRosterData.MAX_PER_OWNER; index++) {
            assertTrue(roster.register(owner, id(100 + index)));
        }
        assertEquals(CovenRosterData.MAX_PER_OWNER, roster.count(owner));
        assertTrue(roster.register(owner, id(100)), "re-registering an existing pair is success");
        assertEquals(CovenRosterData.MAX_PER_OWNER, roster.count(owner));
        assertFalse(roster.register(owner, id(999)), "a seventh member is refused");
        assertEquals(CovenRosterData.MAX_PER_OWNER, roster.count(owner));
        assertFalse(roster.members(owner).contains(id(999)));
        assertFalse(roster.register(null, id(1)));
        assertFalse(roster.register(owner, null));
    }

    @Test
    void snapshotsAreImmutableUuidSortedAndCapped() {
        final CovenRosterData roster = new CovenRosterData();
        final UUID owner = id(2);
        final List<UUID> registered = new ArrayList<>();
        for (int index = 0; index < CovenRosterData.MAX_PER_OWNER; index++) {
            final UUID mage = id(90 - index);
            registered.add(mage);
            roster.register(owner, mage);
        }
        final List<UUID> snapshot = roster.members(owner);
        final List<UUID> expected = registered.stream()
            .sorted(Comparator.comparing(UUID::toString))
            .toList();
        assertEquals(expected, snapshot, "snapshots are deterministic UUID order, not insertion order");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(UUID.randomUUID()));
        roster.unregister(expected.getFirst());
        assertEquals(CovenRosterData.MAX_PER_OWNER, snapshot.size(),
            "an earlier snapshot is never a live view of the stored set");
        assertEquals(List.of(), roster.members(null));
    }

    @Test
    void ownerLookupAndUnregisterAreExact() {
        final CovenRosterData roster = new CovenRosterData();
        roster.register(id(3), id(4));
        assertEquals(id(3), roster.ownerOf(id(4)).orElseThrow());
        assertTrue(roster.ownerOf(id(5)).isEmpty());
        assertTrue(roster.ownerOf(null).isEmpty());
        roster.unregister(id(4));
        assertTrue(roster.ownerOf(id(4)).isEmpty());
        roster.unregister(null);
        assertEquals(0, roster.count(id(3)));
        assertEquals(0, roster.count(null));
    }

    @Test
    void theLegacyUnversionedLayoutDecodesOnceIntoVersionedCappedData() {
        final UUID owner = id(6);
        final List<CovenRosterData.Entry> legacy = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            legacy.add(row(owner, id(200 + index)));
        }
        final CovenRosterData decoded = CovenRosterData.decode(0, legacy);
        assertEquals(CovenRosterData.MAX_PER_OWNER, decoded.count(owner),
            "legacy overflow is capped at six");
        final List<UUID> expected = legacy.stream()
            .map(entry -> UUID.fromString(entry.mage()))
            .sorted(Comparator.comparing(UUID::toString))
            .limit(CovenRosterData.MAX_PER_OWNER)
            .toList();
        assertEquals(expected, decoded.members(owner),
            "overflow is UUID-capped deterministically, never by decode order");
        assertEquals(decoded.members(owner),
            CovenRosterData.decode(0, legacy.reversed()).members(owner),
            "the same rows in any order normalize to the same six members");
    }

    @Test
    void malformedAndDuplicateRowsAreDroppedDeterministically() {
        final UUID owner = id(7);
        final UUID mage = id(8);
        final CovenRosterData decoded = CovenRosterData.decode(CovenRosterData.SCHEMA_VERSION, List.of(
            row(owner, mage),
            row(owner, mage),
            new CovenRosterData.Entry("not-a-uuid", mage.toString()),
            new CovenRosterData.Entry(owner.toString(), ""),
            new CovenRosterData.Entry(owner.toString(), "0000")
        ));
        assertEquals(1, decoded.count(owner));
        assertEquals(List.of(mage), decoded.members(owner));
    }

    @Test
    void aMageBelongsToExactlyOneOwnerAcrossDecodedRows() {
        final CovenRosterData decoded = CovenRosterData.decode(CovenRosterData.SCHEMA_VERSION, List.of(
            row(id(10), id(20)),
            row(id(11), id(20))
        ));
        assertEquals(1, decoded.count(id(10)) + decoded.count(id(11)),
            "a duplicate Mage row never creates a second membership");
    }

    @Test
    void aRefusedReassignmentNeverStripsTheMageFromItsRealOwner() {
        // Regression: register removed the Mage from every other owner before checking the cap, so
        // recruiting a Mage to a full owner left it in no roster at all: bound, alive, and
        // permanently uncountable and uncallable.
        final CovenRosterData roster = new CovenRosterData();
        final UUID full = id(40);
        final UUID realOwner = id(41);
        final UUID mage = id(42);
        for (int index = 0; index < CovenRosterData.MAX_PER_OWNER; index++) {
            roster.register(full, id(50 + index));
        }
        roster.register(realOwner, mage);
        assertEquals(1, roster.count(realOwner));

        assertFalse(roster.register(full, mage), "a full owner refuses the admission");
        assertEquals(1, roster.count(realOwner),
            "the refused reassignment leaves the Mage with its real owner");
        assertEquals(realOwner, roster.ownerOf(mage).orElseThrow());
        assertEquals(CovenRosterData.MAX_PER_OWNER, roster.count(full),
            "no legitimate member of the full owner was displaced");
    }

    @Test
    void decodingIsIndependentOfRowOrder() {
        // Regression: the duplicate-Mage guard ran in raw input order before the sort, so the same
        // save decoded to a different owner depending on which row happened to come first.
        final UUID ownerA = id(60);
        final UUID ownerB = id(61);
        final UUID mage = id(62);
        final List<CovenRosterData.Entry> rows = List.of(row(ownerA, mage), row(ownerB, mage));

        final CovenRosterData forward = CovenRosterData.decode(0, rows);
        final CovenRosterData reversed = CovenRosterData.decode(0, rows.reversed());
        assertEquals(forward.ownerOf(mage), reversed.ownerOf(mage),
            "a duplicate Mage resolves to the same owner regardless of decode order");
        assertEquals(forward.entries(), reversed.entries());
        assertEquals(1, forward.count(ownerA) + forward.count(ownerB));
    }

    @Test
    void legacyOverflowTruncationIsDeliberateAndDeterministic() {
        // The approved design fixes the coven at six, but the pre-F13 roster had no cap, so a real
        // 1.4 save can hold more rows for one owner. Truncation is therefore deliberate: the six
        // lexicographically lowest Mage UUIDs are retained, the surplus is dropped and logged, and
        // a dropped Mage stays bound but is no longer countable, which register must not undo.
        final UUID owner = id(70);
        final List<CovenRosterData.Entry> legacy = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            legacy.add(row(owner, id(80 + index)));
        }
        final CovenRosterData decoded = CovenRosterData.decode(0, legacy);
        assertEquals(CovenRosterData.MAX_PER_OWNER, decoded.count(owner));

        final List<UUID> retained = decoded.members(owner);
        final List<UUID> discarded = legacy.stream()
            .map(entry -> UUID.fromString(entry.mage()))
            .filter(mage -> !retained.contains(mage))
            .toList();
        assertEquals(10 - CovenRosterData.MAX_PER_OWNER, discarded.size());
        discarded.forEach(mage -> assertTrue(decoded.ownerOf(mage).isEmpty(),
            "a discarded Mage holds no roster membership at all"));
        discarded.forEach(mage -> assertFalse(decoded.register(owner, mage),
            "and cannot be re-added while its owner remains full"));
        assertEquals(retained, decoded.members(owner),
            "the retained six are exactly the lexicographically lowest and never change");
    }

    @Test
    void encodedEntriesRoundTripThroughTheVersionedLayout() {
        final CovenRosterData roster = new CovenRosterData();
        roster.register(id(12), id(30));
        roster.register(id(12), id(31));
        roster.register(id(13), id(32));
        final CovenRosterData reloaded =
            CovenRosterData.decode(CovenRosterData.SCHEMA_VERSION, roster.entries());
        assertEquals(roster.members(id(12)), reloaded.members(id(12)));
        assertEquals(roster.members(id(13)), reloaded.members(id(13)));
        assertEquals(roster.entries(), reloaded.entries(), "encoding is stable across a round trip");
    }
}
