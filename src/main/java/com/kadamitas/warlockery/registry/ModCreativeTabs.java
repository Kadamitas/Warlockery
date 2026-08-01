package com.kadamitas.warlockery.registry;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;

public final class ModCreativeTabs {
    public static final RegistrationHandle<CreativeModeTab> MAIN = RegistrationHandle.create("main", () ->
        FabricCreativeModeTab.builder()
        .title(Component.translatable("itemGroup.warlockery.main"))
        .icon(() -> ModItems.ALL.get("ritual_knife").get().getDefaultInstance())
        .displayItems((_, output) -> CreativeInventoryCatalog.sortedIds(ModItems.ALL.keySet()).stream()
            .map(ModItems.ALL::get)
            .forEach(item -> output.accept(item.get())))
        .build());

    private ModCreativeTabs() {
    }

    public static void register() {
        MAIN.register(BuiltInRegistries.CREATIVE_MODE_TAB);
    }
}
