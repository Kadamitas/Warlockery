package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CreatureBossCompletionTest {
    @Test
    void hornedPursuerUsesRangedAttackOnlyInBoundedVisibleRange() {
        assertFalse(CreatureBehaviorRules.shouldUseRangedAttack(16.0, true));
        assertFalse(CreatureBehaviorRules.shouldUseRangedAttack(64.0, false));
        assertTrue(CreatureBehaviorRules.shouldUseRangedAttack(64.0, true));
        assertFalse(CreatureBehaviorRules.shouldUseRangedAttack(225.0, true));
    }

    @Test
    void shadeExtendsAltarSearchWithoutUnboundedChunkScans() {
        assertEquals(12, CreatureBehaviorRules.altarSearchRange(12, 0));
        assertEquals(20, CreatureBehaviorRules.altarSearchRange(12, 1));
        assertEquals(28, CreatureBehaviorRules.altarSearchRange(12, 2));
        assertEquals(28, CreatureBehaviorRules.altarSearchRange(12, 20));
    }

    @Test
    void rangeExtendersAreDataPackExtensible() throws IOException {
        final Path tag = Path.of(
            "src", "main", "resources", "data", "warlockery", "tags", "entity_type",
            "creature_families", "cauldron_range_extenders.json"
        );
        assertTrue(Files.readString(tag).contains("warlockery:emberhorn_archfiend"));
    }
}
