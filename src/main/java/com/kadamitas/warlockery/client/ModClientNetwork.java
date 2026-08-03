package com.kadamitas.warlockery.client;

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
        ClientPlayNetworking.registerGlobalReceiver(
            ModNetwork.OpenRitualScreenPayload.TYPE,
            (payload, context) -> context.client().execute(() ->
                RitualSelectionScreen.openOrUpdate(payload.center(), payload.options()))
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
        send(new ModNetwork.RitualActionPayload(center, "", false));
    }

    public static void requestActivation(final BlockPos center, final String ritualId) {
        send(new ModNetwork.RitualActionPayload(center, ritualId, true));
    }

    private static void send(final CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(payload.type())) {
            ClientPlayNetworking.send(payload);
        }
    }
}
