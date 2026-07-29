package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.block.entity.WolfTrapBlockEntity;
import com.kadamitas.warlockery.diagnostic.DiagnosticChecklist;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
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

public final class WolfTrapOverlayRenderer
    implements BlockEntityRenderer<WolfTrapBlockEntity, WolfTrapOverlayRenderer.State> {

    public WolfTrapOverlayRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
        final WolfTrapBlockEntity trap,
        final State state,
        final float partialTicks,
        final Vec3 cameraPosition,
        final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(trap, state, partialTicks, cameraPosition, breakProgress);
        state.lines = createLines(trap.getDisplay());
    }

    @Override
    public void submit(
        final State state,
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final CameraRenderState camera
    ) {
        final Vec3 attachment = new Vec3(0.5, 0.65, 0.5);
        for (int index = 0; index < state.lines.size(); index++) {
            submitNodeCollector.submitNameTag(
                poseStack,
                attachment,
                (index - state.lines.size() + 1) * 10,
                state.lines.get(index),
                true,
                state.lightCoords,
                camera
            );
        }
    }

    @Override
    public int getViewDistance() {
        return 20;
    }

    private static List<Component> createLines(final WolfTrapBlockEntity.TrapDisplay display) {
        final ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("overlay.warlockery.wolftrap.title").withColor(0xDDAAFF));
        if (!display.capturedName().isBlank()) {
            lines.add(Component.translatable(
                "overlay.warlockery.wolftrap.captured",
                capturedName(display.capturedName())
            ).withColor(0x55FF55));
            lines.add(Component.translatable("overlay.warlockery.wolftrap.release").withColor(0xAAAAAA));
            return List.copyOf(lines);
        }
        lines.add(check("overlay.warlockery.wolftrap.armed", display.armed()));
        lines.add(check("overlay.warlockery.wolftrap.full_moon", display.fullMoon()));
        lines.add(check("overlay.warlockery.wolftrap.altar", display.wolfAltar()));
        lines.add(check("overlay.warlockery.wolftrap.bait", display.bait()));
        final DiagnosticChecklist checklist = DiagnosticChecklist.from(List.of(
            display.armed(), display.fullMoon(), display.wolfAltar(), display.bait()
        ));
        if (checklist.complete()) {
            lines.add(Component.translatable("overlay.warlockery.all_conditions_met").withColor(checklist.color()));
        }
        if (display.lured()) {
            lines.add(Component.translatable("overlay.warlockery.wolftrap.lured").withColor(0x55FFFF));
        } else if (display.armed() && display.fullMoon() && display.wolfAltar() && display.bait()) {
            lines.add(Component.translatable("overlay.warlockery.wolftrap.luring", display.progress()).withColor(0x55FFFF));
        }
        return List.copyOf(lines);
    }

    private static Component check(final String key, final boolean passed) {
        return Component.translatable(
            passed ? "overlay.warlockery.requirement.met" : "overlay.warlockery.requirement.missing",
            Component.translatable(key)
        ).withColor(passed ? 0x55FF55 : 0xFF5555);
    }

    private static Component capturedName(final String encodedName) {
        return encodedName.startsWith("@")
            ? Component.translatable(encodedName.substring(1))
            : Component.literal(encodedName);
    }

    public static final class State extends BlockEntityRenderState {
        private List<Component> lines = List.of();
    }
}
