package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.menu.DollShelfMenu;
import com.kadamitas.warlockery.menu.MachineMenu;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Warlockery.MOD_ID);
    private static final Map<String, RegistryObject<MenuType<MachineMenu>>> MUTABLE_MACHINES = new LinkedHashMap<>();
    public static final RegistryObject<MenuType<DollShelfMenu>> DOLL_SHELF = REGISTRY.register(
        "doll_shelf",
        () -> new MenuType<>(DollShelfMenu::client, FeatureFlags.DEFAULT_FLAGS)
    );
    public static final Map<String, RegistryObject<MenuType<MachineMenu>>> MACHINES;

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

    public static RegistryObject<MenuType<MachineMenu>> machine(final String kind) {
        return MACHINES.getOrDefault(kind, MACHINES.get("cauldron"));
    }
}
