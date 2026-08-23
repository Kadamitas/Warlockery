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
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderAvatarEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Warlockery.MOD_ID, value = Dist.CLIENT)
public final class WarlockeryClient {
    private static WolfFormAvatarRenderer wolfFormAvatarRenderer;
    private static WerewolfFormAvatarRenderer werewolfFormAvatarRenderer;

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

    @SubscribeEvent
    public static void addPlayerLayers(final EntityRenderersEvent.AddLayers event) {
        wolfFormAvatarRenderer = new WolfFormAvatarRenderer(event.getContext());
        werewolfFormAvatarRenderer = new WerewolfFormAvatarRenderer(event.getContext());
    }

    @SubscribeEvent
    public static boolean renderWolfAvatar(final RenderAvatarEvent.Pre event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (wolfFormAvatarRenderer == null
            || werewolfFormAvatarRenderer == null
            || minecraft.level == null
            || !(minecraft.level.getEntity(event.getState().id) instanceof AbstractClientPlayer player)) {
            return false;
        }
        return switch (PlayerWolfVisualState.shape(player.getUUID())) {
            case WOLF -> {
                wolfFormAvatarRenderer.submitAvatar(
                    player,
                    event.getState(),
                    event.getPoseStack(),
                    event.getNodeCollector(),
                    event.getCameraState()
                );
                yield true;
            }
            case WOLFMAN -> {
                werewolfFormAvatarRenderer.submitAvatar(
                    player,
                    event.getState(),
                    event.getPoseStack(),
                    event.getNodeCollector(),
                    event.getCameraState()
                );
                yield true;
            }
            case HUMAN -> false;
        };
    }

    @SubscribeEvent
    public static boolean renderTransformedFirstPersonArm(final RenderArmEvent event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || wolfFormAvatarRenderer == null || werewolfFormAvatarRenderer == null) {
            return false;
        }
        return switch (PlayerWolfVisualState.shape(minecraft.player.getUUID())) {
            case WOLF -> {
                wolfFormAvatarRenderer.submitFirstPersonArm(
                    event.getPoseStack(), event.getNodeCollector(), event.getPackedLight(), event.getArm()
                );
                yield true;
            }
            case WOLFMAN -> {
                werewolfFormAvatarRenderer.submitFirstPersonArm(
                    event.getPoseStack(), event.getNodeCollector(), event.getPackedLight(), event.getArm()
                );
                yield true;
            }
            case HUMAN -> false;
        };
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
    public static void suppressPreyDriveInput(final MovementInputUpdateEvent event) {
        PreyDriveControls.suppressMovement(event);
    }

    @SubscribeEvent
    public static void clientLogout(final ClientPlayerNetworkEvent.LoggingOut event) {
        PlayerWolfVisualState.clear();
        ClientSupernaturalState.clear();
        SupernaturalStatusOverlay.clear();
    }

    @SubscribeEvent
    public static void addHudLayers(final AddGuiOverlayLayersEvent event) {
        if (event.getLayeredDraw().getName().equals(ForgeLayeredDraw.VANILLA_ROOT)) {
            event.getLayeredDraw().addAbove(
                ForgeLayeredDraw.HOTBAR_AND_DECOS,
                VampireBloodHud.LAYER,
                ForgeLayeredDraw.HEALTH_BAR,
                VampireBloodHud::extract
            );
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
