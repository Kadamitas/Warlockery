package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.kadamitas.warlockery.entity.HellhoundLifeRules.PackRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class HellhoundLifeRuntimeTest {
    @Test
    void candidateRetentionPreseedsAuthorityBeforeGenericCandidates() {
        final UUID ownerThreat = new UUID(0L, 1L);
        final UUID attacker = new UUID(0L, 2L);
        final UUID warned = new UUID(0L, 3L);
        final UUID challenger = new UUID(0L, 4L);
        final List<UUID> generic = IntStream.range(100, 140)
            .mapToObj(index -> new UUID(1L, index))
            .toList();
        final List<UUID> retained = HellhoundLifeRuntime.retainCandidates(
            Optional.of(ownerThreat), Optional.of(attacker), Optional.of(warned),
            Optional.of(challenger), generic
        );
        assertEquals(HellhoundLifeRules.MAX_RETAINED_CANDIDATES, retained.size());
        assertEquals(ownerThreat, retained.get(0), "the owner threat is retained first");
        assertEquals(attacker, retained.get(1), "the direct attacker is retained second");
        assertEquals(warned, retained.get(2), "the current warning target is retained third");
        assertEquals(challenger, retained.get(3), "the stable challenger is retained fourth");
        assertTrue(retained.containsAll(List.of(ownerThreat, attacker, warned, challenger)),
            "required facts cannot be evicted because generic entities iterated first");
    }

    @Test
    void candidateRetentionDeduplicatesAndHandlesEmptySeeds() {
        final UUID shared = new UUID(0L, 5L);
        final List<UUID> retained = HellhoundLifeRuntime.retainCandidates(
            Optional.of(shared), Optional.of(shared), Optional.empty(), Optional.empty(),
            List.of(shared, new UUID(0L, 6L))
        );
        assertEquals(List.of(shared, new UUID(0L, 6L)), retained);
        assertTrue(HellhoundLifeRuntime.retainCandidates(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of()
        ).isEmpty());
    }

    @Test
    void sectorOffsetsStayInsideTheThreeToFiveBlockRing() {
        final BlockPos origin = new BlockPos(0, 64, 0);
        for (int x = -20; x <= 20; x += 7) {
            for (int z = -20; z <= 20; z += 7) {
                final BlockPos target = new BlockPos(x, 64, z);
                for (final PackRole role : PackRole.values()) {
                    final BlockPos sector = HellhoundLifeRuntime.sectorOffset(role, target, origin);
                    final double distance = Math.sqrt(sector.distSqr(target));
                    if (role == PackRole.PRESSURE) {
                        assertEquals(target, sector, "pressure closes directly");
                    } else if (x != 0 || z != 0) {
                        assertTrue(distance >= HellhoundLifeRules.SECTOR_MIN_RADIUS - 0.001D
                                && distance <= HellhoundLifeRules.SECTOR_MAX_RADIUS * Math.sqrt(2.0D) + 0.001D,
                            role + " sector distance " + distance + " for target " + target);
                    }
                    assertEquals(sector, HellhoundLifeRuntime.sectorOffset(role, target, origin),
                        "sector derivation is deterministic");
                }
            }
        }
    }

    @Test
    void sectorRolesNeverCollideForOneTarget() {
        final BlockPos origin = new BlockPos(0, 64, 0);
        final BlockPos target = new BlockPos(9, 64, 3);
        final List<BlockPos> sectors = List.of(
            HellhoundLifeRuntime.sectorOffset(PackRole.PRESSURE, target, origin),
            HellhoundLifeRuntime.sectorOffset(PackRole.LEFT, target, origin),
            HellhoundLifeRuntime.sectorOffset(PackRole.RIGHT, target, origin),
            HellhoundLifeRuntime.sectorOffset(PackRole.CUTOFF, target, origin)
        );
        assertEquals(4, sectors.stream().distinct().count(),
            "each temporary role prefers a distinct approach");
    }
}
