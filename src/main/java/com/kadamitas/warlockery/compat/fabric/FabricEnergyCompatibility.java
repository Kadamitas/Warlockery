package com.kadamitas.warlockery.compat.fabric;

import com.kadamitas.warlockery.Warlockery;
import java.util.Objects;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class FabricEnergyCompatibility {
    public static final ItemApiLookup<EnergyReserve, Void> ITEM = ItemApiLookup.get(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "energy_reserve"),
        EnergyReserve.class,
        Void.class
    );

    private FabricEnergyCompatibility() {
    }

    public static void initialize() {
        Objects.requireNonNull(ITEM);
    }

    public static long extract(final ItemStack stack, final long maximum) {
        final EnergyReserve reserve = ITEM.find(stack, null);
        return reserve == null ? 0 : Math.max(0, reserve.extract(maximum, false));
    }
}
