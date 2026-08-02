package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import java.util.stream.LongStream;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BeastSpeechTradeSeedTest {
    private static final UUID TRADER = UUID.fromString("addc1d57-b9fd-4e8c-b8ad-2a1773998016");
    private static final UUID PARTNER = UUID.fromString("f78e236b-3ee5-4d0f-98bb-cfa33cf2ad52");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nonceAdvancesForEveryTradeWithoutWaitingForAnotherTick() {
        final CompoundTag tradeState = new CompoundTag();

        assertEquals(1L, BeastSpeechTradeSeed.nextNonce(tradeState));
        assertEquals(2L, BeastSpeechTradeSeed.nextNonce(tradeState));
        assertEquals(3L, BeastSpeechTradeSeed.nextNonce(tradeState));
    }

    @Test
    void repeatedTradesDuringTheSameTickReceiveUniqueSeeds() {
        final long gameTime = 24_000L;
        final long fixedEntropy = 0x6a09e667f3bcc909L;
        final Set<Long> seeds = LongStream.rangeClosed(1L, 256L)
            .map(nonce -> BeastSpeechTradeSeed.compose(TRADER, PARTNER, gameTime, nonce, fixedEntropy))
            .boxed()
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertEquals(256, seeds.size());
        assertTrue(seeds.stream()
            .map(seed -> BeastSpeechTradeCatalog.selectReward(
                BeastSpeechTradeCatalog.Partner.DEMON,
                true,
                seed
            ).orElseThrow().item().id())
            .distinct()
            .count() > 1L);
    }

    @Test
    void tradesWithinOneSecondNoLongerShareATimeBucketSeed() {
        final long entropy = 0x510e527fade682d1L;
        final long first = BeastSpeechTradeSeed.compose(TRADER, PARTNER, 1_200L, 41L, entropy);
        final long laterSameSecond = BeastSpeechTradeSeed.compose(TRADER, PARTNER, 1_219L, 42L, entropy);

        assertNotEquals(first, laterSameSecond);
    }
}
