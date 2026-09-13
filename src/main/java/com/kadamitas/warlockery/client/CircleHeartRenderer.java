package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.block.entity.CircleHeartBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class CircleHeartRenderer implements BlockEntityRenderer<CircleHeartBlockEntity, CircleHeartRenderer.State> {
    public CircleHeartRenderer(final BlockEntityRendererProvider.Context context) { }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(final CircleHeartBlockEntity heart, final State state, final float partialTicks,
        final Vec3 cameraPosition, final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(heart, state, partialTicks, cameraPosition, breakProgress);
        state.lines = heart.title().isEmpty() ? List.of() : List.of(
            Component.translatable(heart.title()).withColor(0xFFE4A6),
            heart.total() > 0
                ? Component.translatable("screen.warlockery.manual.ritual_progress", Math.clamp(heart.elapsed() * 100 / heart.total(), 0, 100)).withColor(0xDDAAFF)
                : Component.translatable(heart.ready() ? "overlay.warlockery.all_conditions_met" : "screen.warlockery.ritual.not_ready")
                    .withColor(heart.ready() ? 0x77DD88 : 0xFFBB77),
            Component.translatable("screen.warlockery.ritual.power", heart.power(), heart.requiredPower()).withColor(0xDDDDDD));
    }

    @Override
    public void submit(final State state, final PoseStack pose, final SubmitNodeCollector collector, final CameraRenderState camera) {
        for (int index = 0; index < state.lines.size(); index++) {
            collector.submitNameTag(pose, new Vec3(0.5, 0.9, 0.5), (index - state.lines.size() + 1) * 10,
                state.lines.get(index), true, state.lightCoords, camera);
        }
    }

    @Override
    public int getViewDistance() { return 16; }

    public static final class State extends BlockEntityRenderState { private List<Component> lines = List.of(); }
}
