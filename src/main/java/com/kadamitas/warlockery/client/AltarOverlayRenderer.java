package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.diagnostic.DiagnosticChecklist;
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

public final class AltarOverlayRenderer implements BlockEntityRenderer<AltarBlockEntity, AltarOverlayRenderer.State> {
    public AltarOverlayRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
        final AltarBlockEntity altar,
        final State state,
        final float partialTicks,
        final Vec3 cameraPosition,
        final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(altar, state, partialTicks, cameraPosition, breakProgress);
        final AltarBlockEntity.AltarDisplay display = altar.getDisplay();
        final DiagnosticChecklist checklist = DiagnosticChecklist.from(List.of(display.valid()));
        state.lines = checklist.complete()
            ? List.of(
                Component.translatable("overlay.warlockery.altar.title").withColor(0xDDAAFF),
                Component.translatable("overlay.warlockery.all_conditions_met").withColor(checklist.color()),
                Component.translatable("overlay.warlockery.altar.power", display.power(), display.capacity()).withColor(0x55FF55),
                Component.translatable("overlay.warlockery.altar.environment", display.environmentalPower()).withColor(0xAAAAAA),
                Component.translatable(
                    "overlay.warlockery.altar.upgrades",
                    display.activeUpgradeCount(),
                    display.capacityMultiplier(),
                    display.rechargeMultiplier()
                ).withColor(0xFFD966),
                Component.translatable(
                    display.rangeFocused()
                        ? "overlay.warlockery.altar.range_focused"
                        : "overlay.warlockery.altar.range_normal"
                ).withColor(display.rangeFocused() ? 0x55FF55 : 0xAAAAAA)
            )
            : List.of(
                Component.translatable("overlay.warlockery.altar.title").withColor(0xDDAAFF),
                Component.translatable("overlay.warlockery.altar.structure", display.connectedBlocks(), 6).withColor(0xFF5555)
            );
    }

    @Override
    public void submit(
        final State state,
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final CameraRenderState camera
    ) {
        for (int index = 0; index < state.lines.size(); index++) {
            submitNodeCollector.submitNameTag(
                poseStack, new Vec3(0.5, 1.05, 0.5), (index - state.lines.size() + 1) * 10,
                state.lines.get(index), true, state.lightCoords, camera
            );
        }
    }

    @Override
    public int getViewDistance() {
        return 18;
    }

    public static final class State extends BlockEntityRenderState {
        private List<Component> lines = List.of();
    }
}
