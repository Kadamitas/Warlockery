package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.diagnostic.DiagnosticChecklist;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class AltarOverlayRenderer implements BlockEntityRenderer<AltarBlockEntity, AltarOverlayRenderer.State> {
    private static final List<Vec3> ATTACHMENT_POSITIONS = List.of(
        new Vec3(0.28, 1.04, 0.28),
        new Vec3(0.72, 1.04, 0.28),
        new Vec3(0.28, 1.04, 0.72),
        new Vec3(0.72, 1.04, 0.72)
    );
    private final ItemModelResolver itemModelResolver;

    public AltarOverlayRenderer(final BlockEntityRendererProvider.Context context) {
        itemModelResolver = context.itemModelResolver();
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
        final Set<BlockPos> cluster = connectedAltars(altar);
        final var placementResult = AltarOverlayLayout.placeIfPresent(cluster);
        if (placementResult.isEmpty()) {
            state.visible = false;
            state.lines = List.of();
            state.attachments = List.of();
            return;
        }
        final List<net.minecraft.world.item.ItemStack> attachments = altar.attachmentStacks();
        state.attachments = IntStream.range(0, attachments.size()).mapToObj(index -> {
            final ItemStackRenderState itemState = new ItemStackRenderState();
            itemModelResolver.updateForTopItem(
                itemState,
                attachments.get(index),
                ItemDisplayContext.FIXED,
                altar.getLevel(),
                null,
                altar.getBlockPos().hashCode() + index
            );
            return itemState;
        }).toList();
        final AltarOverlayLayout.Placement placement = placementResult.orElseThrow();
        state.visible = altar.getBlockPos().equals(placement.anchor());
        if (!state.visible) {
            state.lines = List.of();
            return;
        }
        state.position = placement.position();
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
        submitAttachments(state, poseStack, submitNodeCollector);
        if (!state.visible) {
            return;
        }
        for (int index = 0; index < state.lines.size(); index++) {
            submitNodeCollector.submitNameTag(
                poseStack, state.position, (index - state.lines.size() + 1) * 10,
                state.lines.get(index), true, state.lightCoords, camera
            );
        }
    }

    private static void submitAttachments(
        final State state,
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector
    ) {
        IntStream.range(0, Math.min(state.attachments.size(), ATTACHMENT_POSITIONS.size())).forEach(index -> {
            final Vec3 position = ATTACHMENT_POSITIONS.get(index);
            poseStack.pushPose();
            poseStack.translate((float) position.x, (float) position.y, (float) position.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(index * 90.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.scale(0.38F, 0.38F, 0.38F);
            state.attachments.get(index).submit(
                poseStack,
                submitNodeCollector,
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                0
            );
            poseStack.popPose();
        });
    }

    private static Set<BlockPos> connectedAltars(final AltarBlockEntity altar) {
        if (altar.getLevel() == null || altar.isRemoved()) {
            return Set.of();
        }
        final Set<BlockPos> connected = new HashSet<>();
        final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(altar.getBlockPos());
        while (!frontier.isEmpty() && connected.size() < 64) {
            final BlockPos current = frontier.removeFirst();
            if (connected.contains(current)) {
                continue;
            }
            if (!altar.getLevel().getBlockState(current).is(ModBlocks.ALTAR.get())) {
                continue;
            }
            connected.add(current);
            frontier.add(current.north());
            frontier.add(current.south());
            frontier.add(current.east());
            frontier.add(current.west());
        }
        return Set.copyOf(connected);
    }

    @Override
    public int getViewDistance() {
        return 18;
    }

    public static final class State extends BlockEntityRenderState {
        private List<Component> lines = List.of();
        private Vec3 position = new Vec3(0.5, 1.3, 0.5);
        private boolean visible;
        private List<ItemStackRenderState> attachments = List.of();
    }
}
