package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import com.kadamitas.warlockery.item.ManualScreenBridge;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Warlockery.MOD_ID, value = Dist.CLIENT)
public final class WarlockeryClient {
    private WarlockeryClient() {
    }

    @SubscribeEvent
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

    @SubscribeEvent
    public static void addHudLayers(final AddGuiOverlayLayersEvent event) {
        if (event.getLayeredDraw().getName().equals(ForgeLayeredDraw.VANILLA_ROOT)) {
            event.getLayeredDraw().add(
                ForgeLayeredDraw.PRE_SLEEP_STACK,
                DollStatusOverlay.LAYER,
                DollStatusOverlay::extract
            );
        }
    }
}
