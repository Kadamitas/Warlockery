package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.block.FetishBlock;
import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import com.kadamitas.warlockery.item.ManualScreenBridge;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.registry.ModBlockEntities;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModFluids;
import com.kadamitas.warlockery.registry.ModMenus;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;

public final class WarlockeryClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModNetwork.init();
        ModClientNetwork.init();
        ManualScreenBridge.setOpenHandler(ManualScreen::open);
        ModMenus.MACHINES.values().forEach(type -> MenuScreens.register(type.get(), MachineScreen::new));
        MenuScreens.register(ModMenus.DOLL_SHELF.get(), DollShelfScreen::new);
        BlockEntityRenderers.register(ModBlockEntities.MAGIC_MACHINE.get(), MachineOverlayRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.WOLF_TRAP.get(), WolfTrapOverlayRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.ALTAR.get(), AltarOverlayRenderer::new);
        EntityRenderers.register(ModEntities.BROOM.get(), BroomEntityRenderer::new);
        ModEntities.ALL.forEach((id, type) -> {
            if ("nami".equals(id)) {
                TexturedCreatureRenderers.registerNami(type.get());
                return;
            }
            if ("naamah".equals(id)) {
                TexturedCreatureRenderers.registerNaamah(type.get());
                return;
            }
            final CreatureVisualProfile visual = CreatureVisualProfile.forKind(ModEntities.kindFor(id));
            TexturedCreatureRenderers.registerArcane(type.get(), CreatureModelProfile.forEntity(id, visual));
        });
        SupernaturalControls.register();
        BroomControls.register();
        BlockColorRegistry.register(
            List.of((BlockTintSource) state -> state.getValue(FetishBlock.ROBE).getTextureDiffuseColor()),
            ModBlocks.ALL.get("scarecrow").get()
        );
        ModFluids.families().forEach(WarlockeryClient::registerFluidRenderer);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            SupernaturalControls.tick(minecraft);
            BroomControls.tick(minecraft);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
            ClientSupernaturalState.clear();
            PlayerWolfVisualState.clear();
        });
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.SLEEP,
            SupernaturalStatusOverlay.LAYER,
            SupernaturalStatusOverlay::extract
        );
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.SLEEP,
            DollStatusOverlay.LAYER,
            DollStatusOverlay::extract
        );
    }

    private static void registerFluidRenderer(final ModFluids.RenderFamily family) {
        FluidRenderingRegistry.register(
            family.source().get(),
            family.flowing().get(),
            new FluidModel.Unbaked(
                new Material(family.stillTexture()),
                new Material(family.flowingTexture()),
                new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                BlockTintSources.constant(family.tint())
            )
        );
    }
}
