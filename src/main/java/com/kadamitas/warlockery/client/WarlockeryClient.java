package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.compat.neoforge.WarlockeryFluidClient;
import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import com.kadamitas.warlockery.item.ManualScreenBridge;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@Mod(value = Warlockery.MOD_ID, dist = Dist.CLIENT)
public final class WarlockeryClient {
    public WarlockeryClient(final IEventBus modBus) {
        modBus.addListener(WarlockeryClient::registerRenderers);
        modBus.addListener(WarlockeryClient::addHudLayers);
        modBus.addListener(WarlockeryFluidClient::registerModels);
        modBus.addListener(ModNetwork::registerClientPayloadHandlers);
    }

    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        ManualScreenBridge.setOpenHandler(ManualScreen::open);
        ModNetwork.setClientScreenHandler(payload ->
            RitualSelectionScreen.openOrUpdate(payload.center(), payload.options()));
        ModNetwork.setClientDollHandler(DollStatusOverlay::activate);
        event.registerBlockEntityRenderer(ModBlockEntities.MAGIC_MACHINE.get(), MachineOverlayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WOLF_TRAP.get(), WolfTrapOverlayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ALTAR.get(), AltarOverlayRenderer::new);
        ModEntities.ALL.forEach((id, type) -> {
            final CreatureVisualProfile visual = CreatureVisualProfile.forKind(ModEntities.kindFor(id));
            TexturedCreatureRenderers.registerArcane(
                event,
                type.get(),
                CreatureModelProfile.forEntity(id, visual)
            );
        });
    }

    public static void addHudLayers(final RegisterGuiLayersEvent event) {
        event.registerBelow(
            VanillaGuiLayers.SLEEP_OVERLAY,
            DollStatusOverlay.LAYER,
            DollStatusOverlay::extract
        );
    }
}
