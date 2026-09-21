package com.kadamitas.warlockery;

import com.kadamitas.warlockery.block.ConnectedGlyphChunkRefresh;
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
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            com.kadamitas.warlockery.command.WarlockeryTestCommands.register(dispatcher));
        WarlockeryEntityData.initialize();
        ModNetwork.init();
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            ModNetwork.queueRecipeViewerCatalog(server, java.util.List.of(handler.getPlayer())));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
            (server, resources, success) -> {
                if (success) ModNetwork.queueRecipeViewerCatalog(server, server.getPlayerList().getPlayers());
            });
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

        WarlockeryWorldGeneration.initialize();
        WarlockeryFabricEvents.initialize();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register(
            (level, chunk, newChunk) -> ConnectedGlyphChunkRefresh.queue(level, chunk.getPos()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(ConnectedGlyphChunkRefresh::tick);
        LOGGER.info("Loading Warlockery for Minecraft 26.3 on Fabric");
    }
}
