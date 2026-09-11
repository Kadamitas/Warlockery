package com.kadamitas.warlockery.ritual.marriage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MarriageDataTest {
    @Test
    void demonSpousePoolHasFifteenDistinctNamesWithoutLilith() {
        assertEquals(15, DemonSpouseNames.ALL.size());
        assertEquals(15, new HashSet<>(DemonSpouseNames.ALL).size());
        assertFalse(DemonSpouseNames.ALL.stream().anyMatch("Lilith"::equalsIgnoreCase));
    }

    @Test
    void onlyNamiBondsAreRecognizedAsDemonMarriages() {
        final UUID player = UUID.randomUUID();
        final UUID partner = UUID.randomUUID();

        assertTrue(new MarriageData.Bond(player.toString(), partner.toString(), "nami", "Agrat").isNami());
        assertFalse(new MarriageData.Bond(player.toString(), partner.toString(), "naamah", "Naamah").isNami());
    }

    @Test
    void simultaneousNamiMarriagesClaimDifferentNames() {
        final MarriageData marriages = new MarriageData();
        final UUID firstPlayer = UUID.randomUUID();
        final UUID secondPlayer = UUID.randomUUID();

        assertEquals(MarriageData.MarriageResult.SUCCESS, marriages.marryNami(firstPlayer, UUID.randomUUID()));
        assertEquals(MarriageData.MarriageResult.SUCCESS, marriages.marryNami(secondPlayer, UUID.randomUUID()));
        assertNotEquals(
            marriages.bond(firstPlayer).orElseThrow().spouseName(),
            marriages.bond(secondPlayer).orElseThrow().spouseName()
        );
    }

    @Test
    void oneSpouseLimitAndDivorceAreEnforced() {
        final MarriageData marriages = new MarriageData();
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        final UUID third = UUID.randomUUID();

        assertEquals(MarriageData.MarriageResult.SUCCESS, marriages.marryPlayers(first, second));
        assertEquals(MarriageData.MarriageResult.ALREADY_MARRIED, marriages.marryPlayers(first, third));
        assertTrue(marriages.divorce(first).isPresent());
        assertFalse(marriages.isMarried(first));
        assertFalse(marriages.isMarried(second));
        assertEquals(MarriageData.MarriageResult.SUCCESS, marriages.marryPlayers(first, third));
    }

    @Test
    void allFifteenNamesCanBeClaimedAndReleased() {
        final MarriageData marriages = new MarriageData();
        final Set<String> claimed = new HashSet<>();
        UUID firstPlayer = null;
        for (int index = 0; index < 15; index++) {
            final UUID player = UUID.randomUUID();
            if (firstPlayer == null) {
                firstPlayer = player;
            }
            assertEquals(MarriageData.MarriageResult.SUCCESS, marriages.marryNami(player, UUID.randomUUID()));
            claimed.add(marriages.bond(player).orElseThrow().spouseName());
        }
        assertEquals(15, claimed.size());
        assertFalse(marriages.hasAvailableDemonName());
        assertEquals(
            MarriageData.MarriageResult.NO_DEMON_NAMES,
            marriages.marryNami(UUID.randomUUID(), UUID.randomUUID())
        );
        marriages.divorce(firstPlayer);
        assertTrue(marriages.hasAvailableDemonName());
    }
}
