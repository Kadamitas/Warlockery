package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class KettleBrewerContextTest {
    private static final UUID FIRST = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void thePlayerWhoStartsTheCycleIsFrozenAsItsBrewer() {
        final KettleBrewerContext started = KettleBrewerContext.EMPTY.claim(FIRST, 40L).begin(60L);

        assertEquals(Optional.of(FIRST), started.activeBrewer());
        assertEquals(started, started.claim(SECOND, 61L));
    }

    @Test
    void anExpiredMenuClaimCannotBlessLaterAutomation() {
        final KettleBrewerContext claimed = KettleBrewerContext.EMPTY.claim(FIRST, 40L);
        final long expiredAt = 40L + KettleBrewerContext.CLAIM_WINDOW_TICKS + 1L;
        final KettleBrewerContext expired = claimed.begin(expiredAt);

        assertTrue(claimed.brewer(expiredAt).isEmpty());
        assertTrue(expired.activeBrewer().isEmpty());
    }

    @Test
    void onlyAnActiveCycleIsRestoredAcrossChunkReload() {
        assertEquals(
            Optional.of(FIRST),
            KettleBrewerContext.restored(Optional.of(FIRST)).activeBrewer()
        );
        assertEquals(KettleBrewerContext.EMPTY, KettleBrewerContext.EMPTY.claim(FIRST, 0L).clear());
    }
}

