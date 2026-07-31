package com.kadamitas.warlockery.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class IngredientResolutionTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void itemTagsRemainValidBeforeDatapackBindingsAreAvailable() {
        assertTrue(ItemIngredient.parse("#c:ingots/silver").orElseThrow().isResolvable());
    }

    @Test
    void fluidTagsRemainValidBeforeDatapackBindingsAreAvailable() {
        assertTrue(FluidIngredient.parse("#minecraft:water").orElseThrow().isResolvable());
    }

    @Test
    void malformedAndUnknownExactIngredientsRemainInvalid() {
        assertTrue(ItemIngredient.parse("missing separator").isEmpty());
        assertFalse(ItemIngredient.parse("warlockery:missing_item").orElseThrow().isResolvable());
        assertFalse(FluidIngredient.parse("warlockery:missing_fluid").orElseThrow().isResolvable());
    }

    @Test
    void sharedFactoryPreservesExactAndTagIdentifiersAcrossRegistryTypes() {
        final ItemIngredient item = ItemIngredient.parse("#c:ingots/silver").orElseThrow();
        final FluidIngredient fluid = FluidIngredient.parse("minecraft:water").orElseThrow();
        final EntityTypeIngredient entity = EntityTypeIngredient.parse("#minecraft:skeletons").orElseThrow();

        assertTrue(item.tag());
        assertEquals("c:ingots/silver", item.id().toString());
        assertFalse(fluid.tag());
        assertEquals("minecraft:water", fluid.value());
        assertTrue(entity.tag());
        assertEquals("minecraft:skeletons", entity.id().toString());
    }
}
