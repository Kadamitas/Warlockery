package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTRY =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Warlockery.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = REGISTRY.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.warlockery.main"))
        .withTabsBefore(CreativeModeTabs.INGREDIENTS)
        .icon(() -> ModItems.ALL.get("ritual_knife").get().getDefaultInstance())
        .displayItems((_, output) -> CreativeInventoryCatalog.sortedIds(ModItems.ALL.keySet()).stream()
            .map(ModItems.ALL::get)
            .forEach(item -> output.accept(item.get())))
        .build());

    private ModCreativeTabs() {
    }
}
