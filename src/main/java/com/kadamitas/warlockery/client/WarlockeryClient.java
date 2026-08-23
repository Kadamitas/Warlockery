package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.block.FetishBlock;
import com.kadamitas.warlockery.compat.neoforge.WarlockeryFluidClient;
import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import com.kadamitas.warlockery.item.ManualScreenBridge;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Warlockery.MOD_ID, dist = Dist.CLIENT)
public final class WarlockeryClient {
    private static WolfFormAvatarRenderer wolfFormAvatarRenderer;
    private static WerewolfFormAvatarRenderer werewolfFormAvatarRenderer;

    public WarlockeryClient(final IEventBus modBus) {
        modBus.addListener(WarlockeryClient::registerRenderers);
        modBus.addListener(WarlockeryClient::addPlayerLayers);
        modBus.addListener(WarlockeryClient::registerKeyMappings);
        modBus.addListener(WarlockeryClient::registerBlockColors);
        modBus.addListener(WarlockeryClient::registerMenuScreens);
        modBus.addListener(WarlockeryClient::addHudLayers);
        modBus.addListener(WarlockeryFluidClient::registerModels);
        modBus.addListener(ModNetwork::registerClientPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(WarlockeryClient::clientTick);
        NeoForge.EVENT_BUS.addListener((MovementInputUpdateEvent event) ->
            PreyDriveControls.suppressMovement(event));
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre<?> event) -> renderWolfAvatar(event));
        NeoForge.EVENT_BUS.addListener((RenderArmEvent<?> event) -> renderTransformedFirstPersonArm(event));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> clientLogout(event));
        ClientSupernaturalState.register();
    }

    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        ManualScreenBridge.setOpenHandler(ManualScreen::open);
        ModNetwork.setClientScreenHandler(payload ->
            RitualSelectionScreen.openOrUpdate(payload.center(), payload.options()));
        ModNetwork.setClientDollHandler(DollStatusOverlay::activate);
        ModNetwork.setClientSupernaturalHandler(payload -> {
            SupernaturalStatusOverlay.update(payload);
            ClientSupernaturalState.update(payload);
        });
        ModNetwork.setClientPlayerWolfVisualHandler(PlayerWolfVisualState::update);
        event.registerBlockEntityRenderer(ModBlockEntities.MAGIC_MACHINE.get(), MachineOverlayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WOLF_TRAP.get(), WolfTrapOverlayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ALTAR.get(), AltarOverlayRenderer::new);
        event.registerEntityRenderer(ModEntities.BROOM.get(), BroomEntityRenderer::new);
        TexturedCreatureRenderers.registerNami(event, ModEntities.NAMI.get());
        final CreatureVisualProfile glassVisual = CreatureVisualProfile.forKind(
            ModEntities.kindFor("glass_doppelganger")
        );
        TexturedCreatureRenderers.registerArcane(
            event,
            ModEntities.ALL.get("glass_doppelganger").get(),
            CreatureModelProfile.forEntity("glass_doppelganger", glassVisual)
        );
        DedicatedCreatureRenderers.registerAll(event);
    }

    public static void addPlayerLayers(final EntityRenderersEvent.AddLayers event) {
        wolfFormAvatarRenderer = new WolfFormAvatarRenderer(event.getContext());
        werewolfFormAvatarRenderer = new WerewolfFormAvatarRenderer(event.getContext());
    }

    private static void renderWolfAvatar(final RenderPlayerEvent.Pre<?> event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (wolfFormAvatarRenderer == null
            || werewolfFormAvatarRenderer == null
            || minecraft.level == null
            || !(minecraft.level.getEntity(event.getRenderState().id) instanceof AbstractClientPlayer player)) {
            return;
        }
        final CameraRenderState cameraState = new CameraRenderState();
        minecraft.gameRenderer.mainCamera().extractRenderState(cameraState, event.getPartialTick());
        switch (PlayerWolfVisualState.shape(player.getUUID())) {
            case WOLF -> wolfFormAvatarRenderer.submitAvatar(
                player,
                event.getRenderState(),
                event.getPoseStack(),
                event.getSubmitNodeCollector(),
                cameraState
            );
            case WOLFMAN -> werewolfFormAvatarRenderer.submitAvatar(
                player,
                event.getRenderState(),
                event.getPoseStack(),
                event.getSubmitNodeCollector(),
                cameraState
            );
            case HUMAN -> {
                return;
            }
        }
        event.setCanceled(true);
    }

    private static void renderTransformedFirstPersonArm(final RenderArmEvent<?> event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || wolfFormAvatarRenderer == null || werewolfFormAvatarRenderer == null) {
            return;
        }
        switch (PlayerWolfVisualState.shape(minecraft.player.getUUID())) {
            case WOLF -> wolfFormAvatarRenderer.submitFirstPersonArm(
                event.getPoseStack(), event.getSubmitNodeCollector(), event.getLightCoords(), event.getArm()
            );
            case WOLFMAN -> werewolfFormAvatarRenderer.submitFirstPersonArm(
                event.getPoseStack(), event.getSubmitNodeCollector(), event.getLightCoords(), event.getArm()
            );
            case HUMAN -> {
                return;
            }
        }
        event.setCanceled(true);
    }

    private static void clientLogout(final ClientPlayerNetworkEvent.LoggingOut event) {
        PlayerWolfVisualState.clear();
        ClientSupernaturalState.clear();
        SupernaturalStatusOverlay.clear();
    }

    public static void registerKeyMappings(final RegisterKeyMappingsEvent event) {
        SupernaturalControls.register(event);
        BroomControls.register(event);
    }

    public static void registerBlockColors(final RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(java.util.List.of((BlockTintSource) state ->
            state.getValue(FetishBlock.ROBE).getTextureDiffuseColor()
        ), ModBlocks.ALL.get("scarecrow").get());
    }

    public static void registerMenuScreens(final RegisterMenuScreensEvent event) {
        ModMenus.MACHINES.values().forEach(type -> event.register(type.get(), MachineScreen::new));
        event.register(ModMenus.DOLL_SHELF.get(), DollShelfScreen::new);
    }

    public static void clientTick(final ClientTickEvent.Post event) {
        SupernaturalControls.tick(event);
        BroomControls.tick(event);
    }

    public static void addHudLayers(final RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.FOOD_LEVEL,
            VampireBloodHud.LAYER,
            VampireBloodHud::extract
        );
        event.registerBelow(
            VanillaGuiLayers.SLEEP_OVERLAY,
            DollStatusOverlay.LAYER,
            DollStatusOverlay::extract
        );
        event.registerBelow(
            VanillaGuiLayers.SLEEP_OVERLAY,
            SupernaturalStatusOverlay.LAYER,
            SupernaturalStatusOverlay::extract
        );
    }
}
