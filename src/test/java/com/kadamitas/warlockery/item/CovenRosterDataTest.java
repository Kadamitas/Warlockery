package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CovenRosterDataTest {
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
}
