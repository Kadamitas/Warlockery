package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.menu.DollShelfMenu;
import com.kadamitas.warlockery.menu.MachineMenu;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    private static final Map<String, RegistrationHandle<MenuType<MachineMenu>>> MUTABLE_MACHINES = new LinkedHashMap<>();
    public static final RegistrationHandle<MenuType<DollShelfMenu>> DOLL_SHELF = RegistrationHandle.create(
        "doll_shelf",
        () -> new MenuType<>(DollShelfMenu::client, FeatureFlags.DEFAULT_FLAGS)
    );
    public static final Map<String, RegistrationHandle<MenuType<MachineMenu>>> MACHINES;

    static {
        MachineProfiles.blockIds().stream()
            .map(id -> MachineProfiles.forBlock(id).recipeType())
            .distinct()
            .sorted()
            .forEach(kind -> MUTABLE_MACHINES.put(kind, RegistrationHandle.create(
                kind,
                () -> new MenuType<>((containerId, inventory) -> MachineMenu.client(kind, containerId, inventory), FeatureFlags.DEFAULT_FLAGS)
            )));
        MACHINES = Collections.unmodifiableMap(MUTABLE_MACHINES);
    }

    private ModMenus() {
    }

    public static RegistrationHandle<MenuType<MachineMenu>> machine(final String kind) {
        return MACHINES.getOrDefault(kind, MACHINES.get("cauldron"));
    }

    public static void register() {
        DOLL_SHELF.register(BuiltInRegistries.MENU);
        MACHINES.values().forEach(handle -> handle.register(BuiltInRegistries.MENU));
    }
}
