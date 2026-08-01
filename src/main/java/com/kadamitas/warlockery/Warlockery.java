package com.kadamitas.warlockery;

import com.kadamitas.warlockery.compat.fabric.FabricEnergyCompatibility;
import com.kadamitas.warlockery.config.WarlockeryConfig;
import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.fabric.WarlockeryFabricEvents;
import com.kadamitas.warlockery.fabric.WarlockeryWorldGeneration;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModChunkTickets;
import com.kadamitas.warlockery.registry.ModCreativeTabs;
import com.kadamitas.warlockery.registry.ModEffects;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModFluids;
import com.kadamitas.warlockery.registry.ModGameTests;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.ModMenus;
import com.kadamitas.warlockery.registry.ModSounds;
import com.kadamitas.warlockery.registry.ModVillagers;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public final class Warlockery implements ModInitializer {
    public static final String MOD_ID = "warlockery";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        WarlockeryConfig.initialize();
        WarlockeryEntityData.initialize();
        ModNetwork.init();
        FabricEnergyCompatibility.initialize();

        ModFluids.register();
        ModEntities.register();
        ModEffects.register();
        ModItems.registerSpawnEggs(ModEntities.ALL);
        ModBlocks.register();
        ModItems.register();
        ModVillagers.register();
        ModSounds.register();
        ModMenus.register();
        ModBlockEntities.register();
        ModChunkTickets.register();
        ModCreativeTabs.register();
        ModGameTests.register();

        WarlockeryWorldGeneration.initialize();
        WarlockeryFabricEvents.initialize();
        LOGGER.info("Loading Warlockery for Minecraft 26.2 on Fabric");
    }
}
