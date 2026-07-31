package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RedstoneSoupItemTest {
    @Test
    void onlyFullHungerUnlocksTheFourHeartHealthBoost() {
        assertFalse(RedstoneSoupItem.grantsHealthBoost(19));
        assertTrue(RedstoneSoupItem.grantsHealthBoost(20));
    }
}
