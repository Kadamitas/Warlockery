package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.compat.viewer.RecipeViewerCatalogSync;
import com.kadamitas.warlockery.compat.viewer.RecipeViewerRefreshSignal;

import com.kadamitas.warlockery.network.ModNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ModClientNetwork {
    private static boolean initialized;

    private ModClientNetwork() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
            (handler, sender, client) -> RecipeViewerCatalogSync.beginConnection(handler.getConnection()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> RecipeViewerCatalogSync.disconnect(handler.getConnection()));
        net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (client) RecipeViewerRefreshSignal.publish();
        });
        ClientPlayNetworking.registerGlobalReceiver(com.kadamitas.warlockery.network.RecipeViewerCatalogPayload.TYPE,
            (payload, context) -> {
                final var connection = context.player().connection.getConnection();
                context.client().execute(() -> RecipeViewerCatalogSync.accept(connection, payload));
            });
        ClientPlayNetworking.registerGlobalReceiver(
            ModNetwork.OpenRitualScreenPayload.TYPE,
            (payload, context) -> context.client().execute(() ->
                ManualScreen.openOrUpdateRitual(payload.center(), payload.options(), payload.mayOpen()))
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ModNetwork.DollActivationPayload.TYPE,
            (payload, context) -> context.client().execute(() -> DollStatusOverlay.activate(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ModNetwork.SupernaturalSnapshotPayload.TYPE,
            (payload, context) -> context.client().execute(() -> {
                SupernaturalStatusOverlay.update(payload);
                ClientSupernaturalState.update(payload);
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ModNetwork.PlayerWolfVisualPayload.TYPE,
            (payload, context) -> context.client().execute(() -> PlayerWolfVisualState.update(payload))
        );
        initialized = true;
    }

    public static void requestSupernaturalAction(final ModNetwork.SupernaturalAction action) {
        send(new ModNetwork.SupernaturalActionPayload(action));
    }

    public static void requestBroomControl(
        final int strafe,
        final int forward,
        final boolean ascend,
        final boolean gliding
    ) {
        send(new ModNetwork.BroomControlPayload(
            (byte) Math.clamp(strafe, -1, 1),
            (byte) Math.clamp(forward, -1, 1),
            ascend,
            gliding
        ));
    }

    public static void requestRefresh(final BlockPos center) {
        send(new ModNetwork.RitualActionPayload(center, "", false, false));
    }

    public static void requestSelection(final BlockPos center, final String ritualId) {
        send(new ModNetwork.RitualActionPayload(center, ritualId, false, false));
    }

    public static void requestActivation(final BlockPos center, final String ritualId) {
        send(new ModNetwork.RitualActionPayload(center, ritualId, true, false));
    }

    public static void requestCancellation(final BlockPos center) {
        send(new ModNetwork.RitualActionPayload(center, "", false, true));
    }

    private static void send(final CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(payload.type())) {
            ClientPlayNetworking.send(payload);
        }
    }
}
