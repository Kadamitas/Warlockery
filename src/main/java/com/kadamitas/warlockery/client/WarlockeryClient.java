package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.compat.neoforge.WarlockeryFluidClient;
import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import com.kadamitas.warlockery.item.ManualScreenBridge;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.Set;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@Mod(value = Warlockery.MOD_ID, dist = Dist.CLIENT)
public final class WarlockeryClient {
    private static final Set<String> SPECIAL_RENDERERS = Set.of(
        "ent", "werewolf_hunter", "hobgoblin", "goblin", "stonebroker", "forgewarden"
    );

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
        event.registerEntityRenderer(ModEntities.ENT.get(), TexturedCreatureRenderers.Ent::new);
        event.registerEntityRenderer(ModEntities.WEREWOLF_HUNTER.get(), TexturedCreatureRenderers.WerewolfHunter::new);
        event.registerEntityRenderer(ModEntities.HOBGOBLIN.get(), context -> new TexturedCreatureRenderers.Hobgoblin(context, "hobgoblin"));
        event.registerEntityRenderer(ModEntities.GOBLIN.get(), context -> new TexturedCreatureRenderers.Hobgoblin(context, "goblin"));
        event.registerEntityRenderer(ModEntities.STONEBROKER.get(), context -> new TexturedCreatureRenderers.Hobgoblin(context, "stonebroker"));
        event.registerEntityRenderer(ModEntities.FORGEWARDEN.get(), context -> new TexturedCreatureRenderers.Hobgoblin(context, "forgewarden"));
        ModEntities.ALL.forEach((id, type) -> {
            if (SPECIAL_RENDERERS.contains(id)) return;
            if (ModEntities.SPIRIT_IDS.contains(id)) TexturedCreatureRenderers.registerSpirit(event, type.get(), id);
            else TexturedCreatureRenderers.registerArcane(
                event,
                type.get(),
                id,
                CreatureVisualProfile.forKind(ModEntities.kindFor(id))
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
