package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.menu.DollShelfMenu;
import com.kadamitas.warlockery.menu.MachineMenu;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(Registries.MENU, Warlockery.MOD_ID);
    private static final Map<String, DeferredHolder<MenuType<?>, MenuType<MachineMenu>>> MUTABLE_MACHINES = new LinkedHashMap<>();
    public static final DeferredHolder<MenuType<?>, MenuType<DollShelfMenu>> DOLL_SHELF = REGISTRY.register(
        "doll_shelf",
        () -> new MenuType<>(DollShelfMenu::client, FeatureFlags.DEFAULT_FLAGS)
    );
    public static final Map<String, DeferredHolder<MenuType<?>, MenuType<MachineMenu>>> MACHINES;

    static {
        MachineProfiles.blockIds().stream()
            .map(id -> MachineProfiles.forBlock(id).recipeType())
            .distinct()
            .sorted()
            .forEach(kind -> MUTABLE_MACHINES.put(kind, REGISTRY.register(
                kind,
                () -> new MenuType<>((containerId, inventory) -> MachineMenu.client(kind, containerId, inventory), FeatureFlags.DEFAULT_FLAGS)
            )));
        MACHINES = Collections.unmodifiableMap(MUTABLE_MACHINES);
    }

    private ModMenus() {
    }

    public static DeferredHolder<MenuType<?>, MenuType<MachineMenu>> machine(final String kind) {
        return MACHINES.getOrDefault(kind, MACHINES.get("cauldron"));
    }
}
