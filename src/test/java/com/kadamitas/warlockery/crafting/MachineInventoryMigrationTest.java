package com.kadamitas.warlockery.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class MachineInventoryMigrationTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void spinnerMovesItsLegacyOutputAndPreservesExtraModifiers() {
        final NonNullList<ItemStack> legacy = empty();
        legacy.set(0, stack(Items.STRING, 8));
        legacy.set(1, stack(Items.DIAMOND));
        legacy.set(4, stack(Items.REDSTONE));
        legacy.set(6, stack(Items.COBWEB));
        legacy.set(7, stack(Items.COBWEB));

        final var result = MachineInventoryMigration.migrate(
            "spinningwheel",
            0,
            legacy,
            (slot, stack) -> slot == 0 ? stack.is(Items.STRING) : slot < 4
        );

        assertTrue(result.migrated());
        assertTrue(result.inventory().get(0).is(Items.STRING));
        assertTrue(result.inventory().get(1).is(Items.DIAMOND));
        assertTrue(result.inventory().get(2).is(Items.REDSTONE));
        assertTrue(result.inventory().get(4).is(Items.COBWEB));
        assertEquals(2, result.inventory().get(4).getCount());
        assertTrue(result.overflow().isEmpty());
    }

    @Test
    void distilleryMovesFourOutputsAndRefundsObsoleteFuel() {
        final NonNullList<ItemStack> legacy = empty();
        legacy.set(0, stack(Items.BLAZE_POWDER));
        legacy.set(1, stack(Items.GUNPOWDER));
        legacy.set(2, stack(Items.CLAY_BALL));
        legacy.set(4, stack(Items.COAL, 3));
        legacy.set(5, stack(Items.GLOWSTONE_DUST, 2));
        legacy.set(6, stack(Items.SLIME_BALL));

        final var result = MachineInventoryMigration.migrate(
            "distillery",
            0,
            legacy,
            (slot, stack) -> slot == 2 ? stack.is(Items.CLAY_BALL) : slot < 2 && !stack.is(Items.COAL)
        );

        assertTrue(result.inventory().get(0).is(Items.BLAZE_POWDER));
        assertTrue(result.inventory().get(1).is(Items.GUNPOWDER));
        assertTrue(result.inventory().get(2).is(Items.CLAY_BALL));
        assertTrue(result.inventory().get(3).is(Items.GLOWSTONE_DUST));
        assertTrue(result.inventory().get(4).is(Items.SLIME_BALL));
        assertEquals(1, result.overflow().size());
        assertTrue(result.overflow().getFirst().is(Items.COAL));
        assertEquals(3, result.overflow().getFirst().getCount());
    }

    @Test
    void brazierMovesAshBesideItsThreeReagents() {
        final NonNullList<ItemStack> legacy = empty();
        legacy.set(0, stack(Items.GUNPOWDER));
        legacy.set(1, stack(Items.GLOWSTONE_DUST));
        legacy.set(2, stack(Items.BONE_MEAL));
        legacy.set(6, stack(Items.CHARCOAL));

        final var result = MachineInventoryMigration.migrate(
            "brazier", 0, legacy, (slot, _) -> slot < 3
        );

        assertTrue(result.inventory().get(3).is(Items.CHARCOAL));
        assertTrue(result.overflow().isEmpty());
    }

    @Test
    void currentLayoutsRoundTripWithoutRemapping() {
        final NonNullList<ItemStack> current = empty();
        current.set(4, stack(Items.COBWEB));

        final var result = MachineInventoryMigration.migrate(
            "spinningwheel", MachineInventoryMigration.CURRENT_VERSION, current, (_, _) -> false
        );

        assertFalse(result.migrated());
        assertTrue(result.inventory().get(4).is(Items.COBWEB));
        assertTrue(result.overflow().isEmpty());
    }

    private static NonNullList<ItemStack> empty() {
        return NonNullList.withSize(9, ItemStack.EMPTY);
    }

    private static ItemStack stack(final net.minecraft.world.level.ItemLike item) {
        return stack(item, 1);
    }

    private static ItemStack stack(final net.minecraft.world.level.ItemLike item, final int count) {
        final var value = item.asItem();
        final var holder = value.builtInRegistryHolder();
        if (!holder.areComponentsBound()) {
            holder.bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64)
                .build());
        }
        return new ItemStack(holder, count);
    }
}

