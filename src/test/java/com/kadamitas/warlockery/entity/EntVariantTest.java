package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.EntEntity.EntTraits;
import com.kadamitas.warlockery.entity.EntEntity.EntVariant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class EntVariantTest {
    @Test
    void biomeFamiliesSelectTheExpectedVisibleVariant() {
        final Map<String, EntVariant> expected = Map.of(
            "minecraft:birch_forest", EntVariant.BIRCH,
            "minecraft:old_growth_spruce_taiga", EntVariant.SPRUCE,
            "minecraft:jungle", EntVariant.JUNGLE,
            "minecraft:dark_forest", EntVariant.DARK_OAK,
            "minecraft:savanna", EntVariant.ACACIA,
            "minecraft:mangrove_swamp", EntVariant.MANGROVE,
            "minecraft:cherry_grove", EntVariant.CHERRY,
            "minecraft:pale_garden", EntVariant.PALE_OAK,
            "minecraft:forest", EntVariant.OAK
        );
        expected.forEach((biome, variant) -> assertSame(variant, EntVariant.fromBiome(biome)));
    }

    @Test
    void everyVariantHasAnOpaqueUniqueTintAndRoundTripsItsName() {
        final Set<Integer> tints = Arrays.stream(EntVariant.values())
            .map(EntVariant::tint)
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(EntVariant.values().length, tints.size());
        Arrays.stream(EntVariant.values()).forEach(variant -> {
            assertEquals(0xFF000000, variant.tint() & 0xFF000000);
            assertSame(variant, EntVariant.fromSerializedName(variant.serializedName()));
            assertSame(variant, EntVariant.byOrdinal(variant.ordinal()));
        });
        assertSame(EntVariant.OAK, EntVariant.fromSerializedName("unknown"));
        assertSame(EntVariant.OAK, EntVariant.byOrdinal(-1));
        assertSame(EntVariant.OAK, EntVariant.byOrdinal(EntVariant.values().length));
    }

    @Test
    void traitPackagesAreDistinctAndRemainWithinBalancedBounds() {
        final Set<EntTraits> traits = Arrays.stream(EntVariant.values())
            .map(EntVariant::traits)
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(EntVariant.values().length, traits.size());
        traits.forEach(values -> {
            assertEquals(EntRules.MAX_HEALTH, values.maxHealth());
            assertTrue(values.attackDamage() >= 12.0 && values.attackDamage() <= 20.0);
            assertTrue(values.movementSpeed() >= 0.2 && values.movementSpeed() <= 0.31);
            assertTrue(values.armor() >= 0.0 && values.armor() <= 8.0);
        });
    }
}
