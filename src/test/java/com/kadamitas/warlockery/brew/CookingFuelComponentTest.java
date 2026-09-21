package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.block.entity.CookingFuels;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class CookingFuelComponentTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void fuelAcceptanceTracksTheStackComponent() {
        // Unit bootstrap does not bind data-pack-driven item components.
        // Bind only missing fixture defaults, then exercise the actual stack component.
        final var holder = Items.COAL.builtInRegistryHolder();
        if (!holder.areComponentsBound()) {
            holder.bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
        }
        final ItemStack coal = new ItemStack(holder);
        coal.remove(DataComponents.COOKING_FUEL);
        assertFalse(CookingFuels.isFuel(coal));
        final var properties = new RecordingProperties();
        BrewItem.configure(properties, BrewKind.COMBUSTION);
        coal.set(DataComponents.COOKING_FUEL, properties.fuel);
        assertTrue(CookingFuels.isFuel(coal));
        coal.remove(DataComponents.COOKING_FUEL);
        assertFalse(CookingFuels.isFuel(coal));
        assertFalse(CookingFuels.isFuel(ItemStack.EMPTY));
    }

    @Test
    void combustionBrewPreservesItsBurnTimeUsingTheNewComponent() {
        // Observe the exact properties initializer used by the production constructor.
        // Do not attempt to register a new Item after vanilla registry freeze.
        final var properties = new RecordingProperties();
        BrewItem.configure(properties, BrewKind.COMBUSTION);
        final var fuel = properties.fuel;
        assertNotNull(fuel);
        // These are constant providers: no level or mutable loot context is needed.
        assertEquals(2_400, fuel.burnTime().get(null, 0));
        assertEquals(1.0F, fuel.speedMultiplier().get(null, 0.0F));
    }

    private static final class RecordingProperties extends Item.Properties {
        private CookingFuel fuel;

        @Override
        public <T> Item.Properties component(final DataComponentType<T> type, final T value) {
            if (type == DataComponents.COOKING_FUEL) {
                fuel = (CookingFuel) value;
            }
            return super.component(type, value);
        }
    }
}
