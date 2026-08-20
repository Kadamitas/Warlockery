package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class RedstoneSoupItemTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void onlyFullHungerUnlocksTheFourHeartHealthBoost() {
        assertFalse(RedstoneSoupItem.grantsHealthBoost(19));
        assertTrue(RedstoneSoupItem.grantsHealthBoost(20));
    }
}
