package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import com.kadamitas.warlockery.item.ManualScreenBridge;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModMenus;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.block.FetishBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderAvatarEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Warlockery.MOD_ID, value = Dist.CLIENT)
public final class WarlockeryClient {
    private static WolfFormAvatarRenderer wolfFormAvatarRenderer;

    private WarlockeryClient() {
    }

    @SubscribeEvent
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
        ModMenus.MACHINES.values().forEach(type -> MenuScreens.register(type.get(), MachineScreen::new));
        MenuScreens.register(ModMenus.DOLL_SHELF.get(), DollShelfScreen::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MAGIC_MACHINE.get(), MachineOverlayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WOLF_TRAP.get(), WolfTrapOverlayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ALTAR.get(), AltarOverlayRenderer::new);
        event.registerEntityRenderer(ModEntities.BROOM.get(), BroomEntityRenderer::new);
        ModEntities.ALL.forEach((id, type) -> {
            if ("nami".equals(id)) {
                TexturedCreatureRenderers.registerNami(event, type.get());
                return;
            }
            if ("naamah".equals(id)) {
                TexturedCreatureRenderers.registerNaamah(event, type.get());
                return;
            }
            final CreatureVisualProfile visual = CreatureVisualProfile.forKind(ModEntities.kindFor(id));
            TexturedCreatureRenderers.registerArcane(
                event,
                type.get(),
                CreatureModelProfile.forEntity(id, visual)
            );
        });
    }

    @SubscribeEvent
    public static void addPlayerLayers(final EntityRenderersEvent.AddLayers event) {
        wolfFormAvatarRenderer = new WolfFormAvatarRenderer(event.getContext());
    }

    @SubscribeEvent
    public static boolean renderWolfAvatar(final RenderAvatarEvent.Pre event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (wolfFormAvatarRenderer == null
            || minecraft.level == null
            || !(minecraft.level.getEntity(event.getState().id) instanceof AbstractClientPlayer player)
            || !PlayerWolfVisualState.isWolf(player.getUUID())) {
            return false;
        }
        wolfFormAvatarRenderer.submitAvatar(
            player,
            event.getState(),
            event.getPoseStack(),
            event.getNodeCollector(),
            event.getCameraState()
        );
        return true;
    }

    @SubscribeEvent
    public static void registerKeyMappings(final RegisterKeyMappingsEvent event) {
        SupernaturalControls.register(event);
        BroomControls.register(event);
    }

    @SubscribeEvent
    public static void registerBlockColors(final RegisterColorHandlersEvent.Block event) {
        event.register(java.util.List.of((BlockTintSource) state ->
            state.getValue(FetishBlock.ROBE).getTextureDiffuseColor()
        ), ModBlocks.ALL.get("scarecrow").get());
    }

    @SubscribeEvent
    public static void clientTick(final TickEvent.ClientTickEvent.Post event) {
        SupernaturalControls.tick(event);
        BroomControls.tick(event);
    }

    @SubscribeEvent
    public static void clientLogout(final ClientPlayerNetworkEvent.LoggingOut event) {
        PlayerWolfVisualState.clear();
    }

    @SubscribeEvent
    public static void addHudLayers(final AddGuiOverlayLayersEvent event) {
        if (event.getLayeredDraw().getName().equals(ForgeLayeredDraw.VANILLA_ROOT)) {
            event.getLayeredDraw().add(
                ForgeLayeredDraw.PRE_SLEEP_STACK,
                DollStatusOverlay.LAYER,
                DollStatusOverlay::extract
            );
            event.getLayeredDraw().add(
                ForgeLayeredDraw.PRE_SLEEP_STACK,
                SupernaturalStatusOverlay.LAYER,
                SupernaturalStatusOverlay::extract
            );
        }
    }
}
