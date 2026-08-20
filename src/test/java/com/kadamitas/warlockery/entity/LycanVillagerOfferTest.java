package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class LycanVillagerOfferTest {
    @Test
    void signatureCatalogAndOfferCapStayExact() {
        assertEquals(3, LycanVillagerEntity.SIGNATURE_OFFER_COUNT);
        assertEquals(64, LycanVillagerEntity.MAX_OFFERS);
        assertEquals(2, LycanVillagerRules.TRADE_FAMILIARITY_POINTS);
        assertEquals(1_200, LycanVillagerRules.TRADE_FAMILIARITY_COOLDOWN_TICKS);
    }
}
