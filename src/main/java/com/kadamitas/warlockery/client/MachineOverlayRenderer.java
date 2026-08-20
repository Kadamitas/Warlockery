package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.kadamitas.warlockery.brew.CauldronChalkCircles;
import com.kadamitas.warlockery.crafting.MachineDisplay;
import com.kadamitas.warlockery.crafting.MachineProfile;
import com.kadamitas.warlockery.crafting.MachineRecipeManager;
import com.kadamitas.warlockery.crafting.MachineStatus;
import com.kadamitas.warlockery.crafting.MachineUiState;
import com.kadamitas.warlockery.brew.custom.CustomBrewCauldronState;
import com.kadamitas.warlockery.util.ItemDisplayNames;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MachineOverlayRenderer
    implements BlockEntityRenderer<MagicMachineBlockEntity, MachineOverlayRenderer.State> {

    public MachineOverlayRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
        final MagicMachineBlockEntity machine,
        final State state,
        final float partialTicks,
        final Vec3 cameraPosition,
        final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(machine, state, partialTicks, cameraPosition, breakProgress);
        state.lines = createLines(
            machine.machineProfile(),
            machine.getMachineDisplay(),
            machine.getCustomBrewState(),
            machine.getCauldronChalkCircles()
        );
    }

    @Override
    public void submit(
        final State state,
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final CameraRenderState camera
    ) {
        if (state.lines.isEmpty()) {
            return;
        }
        final Vec3 attachment = new Vec3(0.5, 0.95, 0.5);
        for (int index = 0; index < state.lines.size(); index++) {
            final int offset = (index - state.lines.size() + 1) * 10;
            submitNodeCollector.submitNameTag(
                poseStack,
                attachment,
                offset,
                state.lines.get(index),
                true,
                state.lightCoords,
                camera
            );
        }
    }

    @Override
    public int getViewDistance() {
        return 24;
    }

    private static List<Component> createLines(
        final MachineProfile profile,
        final MachineDisplay display,
        final CustomBrewCauldronState customBrew,
        final CauldronChalkCircles.State chalkCircles
    ) {
        if ("cauldron".equals(profile.recipeType()) && customBrew.engaged()) {
            return createCustomBrewLines(customBrew, chalkCircles);
        }
        final MachineRecipeManager.Diagnostic diagnostic = display.diagnostic();
        if (display.status() == MachineStatus.EMPTY) {
            final ArrayList<Component> lines = new ArrayList<>();
            if ("cauldron".equals(profile.recipeType())) {
                appendChalkCircleLines(lines, chalkCircles);
            }
            return List.copyOf(lines);
        }
        final ArrayList<Component> lines = new ArrayList<>();
        if (!"cauldron".equals(profile.recipeType())) {
            lines.add(Component.translatable(
                "overlay.warlockery.machine.title",
                Component.translatable("block.warlockery." + profile.displayBlock())
            ).withColor(0xDDAAFF));
        }
        if (diagnostic.recipe().isEmpty()) {
            lines.add(Component.translatable("overlay.warlockery.cauldron.unknown").withColor(0xFF7777));
        } else {
            lines.add(Component.translatable(
                "overlay.warlockery.cauldron.recipe",
                ItemDisplayNames.text(diagnostic.output())
            ).withColor(0xDDAAFF));
        }
        if (!diagnostic.missing().isEmpty()) {
            lines.add(Component.translatable(
                "overlay.warlockery.cauldron.missing",
                joinMissing(diagnostic.missing())
            ).withColor(0xFFFF55));
        }
        if (!diagnostic.wrong().isEmpty()) {
            lines.add(Component.translatable(
                "overlay.warlockery.cauldron.wrong",
                joinWrong(diagnostic.wrong())
            ).withColor(0xFF5555));
        }
        final MachineUiState uiState = MachineUiState.from(profile, diagnostic, display.status());
        if (uiState.showGreenCheck()) {
            lines.add(Component.translatable("overlay.warlockery.all_conditions_met").withColor(uiState.checklist().color()));
        }
        final Component status = switch (display.status()) {
            case PROCESSING -> Component.translatable(
                "overlay.warlockery.machine.processing", display.progressPercent()
            ).withColor(0x55FFFF);
            case NO_HEAT -> Component.translatable("overlay.warlockery.cauldron.no_heat").withColor(0xFFAA00);
            case NO_FUEL -> Component.translatable("overlay.warlockery.cauldron.no_fuel").withColor(0xFFAA00);
            case NO_ALTAR_POWER -> Component.translatable("overlay.warlockery.machine.no_altar_power").withColor(0xFFAA00);
            case NO_FAMILIAR -> Component.translatable("overlay.warlockery.machine.no_familiar").withColor(0xFFAA00);
            case NO_IGNITION -> Component.translatable("overlay.warlockery.machine.no_ignition").withColor(0xFFAA00);
            case OUTPUT_BLOCKED -> Component.translatable("overlay.warlockery.cauldron.output_blocked").withColor(0xFF5555);
            default -> null;
        };
        if (status != null) {
            lines.add(status);
        }
        if ("cauldron".equals(profile.recipeType())) {
            appendChalkCircleLines(lines, chalkCircles);
        }
        return List.copyOf(lines);
    }

    private static List<Component> createCustomBrewLines(
        final CustomBrewCauldronState state,
        final CauldronChalkCircles.State chalkCircles
    ) {
        final ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("overlay.warlockery.custom_brew.title").withColor(0xDDAAFF));
        if (!state.selectedEffects().isEmpty()) {
            lines.add(Component.translatable(
                "overlay.warlockery.custom_brew.formula",
                compact(state.selectedEffects())
            ).withColor(0xDDAAFF));
        }
        if (!state.acceptedInputs().isEmpty()) {
            lines.add(Component.translatable(
                "overlay.warlockery.custom_brew.accepted",
                compact(state.acceptedInputs())
            ).withColor(0x55FFFF));
        }
        lines.add(Component.translatable(
            "overlay.warlockery.custom_brew.capacity",
            state.capacityCost(),
            state.capacity()
        ).withColor(state.capacity() >= state.capacityCost() ? 0x55FF55 : 0xFF5555));
        if (state.requiredPower() > 0) {
            lines.add(Component.translatable(
                "overlay.warlockery.custom_brew.power",
                state.availablePower(),
                state.requiredPower()
            ).withColor(state.availablePower() >= state.requiredPower() ? 0x55FF55 : 0xFFAA00));
        }
        if (state.failure() != com.kadamitas.warlockery.brew.custom.CustomBrewFailure.NONE) {
            lines.add(Component.translatable(
                "overlay.warlockery.custom_brew.failure." + state.failure().id(),
                state.detail()
            ).withColor(0xFF5555));
        }
        if (state.showGreenCheck()) {
            lines.add(Component.translatable("overlay.warlockery.all_conditions_met").withColor(state.checklist().color()));
        }
        if (state.progressPercent() > 0) {
            lines.add(Component.translatable(
                "overlay.warlockery.machine.processing",
                state.progressPercent()
            ).withColor(0x55FFFF));
        }
        appendChalkCircleLines(lines, chalkCircles);
        return List.copyOf(lines);
    }

    private static void appendChalkCircleLines(
        final List<Component> lines,
        final CauldronChalkCircles.State circles
    ) {
        if (!circles.hasMarks()) {
            return;
        }
        final boolean malformed = java.util.stream.Stream.of(circles.small(), circles.medium())
            .map(CauldronChalkCircles.Ring::kind)
            .anyMatch(kind -> kind == CauldronChalkCircles.RingKind.INCOMPLETE
                || kind == CauldronChalkCircles.RingKind.MIXED);
        lines.add(Component.translatable(
            "overlay.warlockery.cauldron.chalk.rings",
            ringState(circles.small()),
            ringState(circles.medium())
        ).withColor(malformed ? 0xFFAA00 : 0xCC88FF));
        if (circles.ritualWeight() > 0 || circles.infernalWeight() > 0) {
            lines.add(Component.translatable(
                "overlay.warlockery.cauldron.chalk.influence",
                formatMultiplier(circles.potencyMultiplier()),
                Math.round(circles.stability() * 100.0F),
                Math.round(circles.mishapRisk() * 100.0F)
            ).withColor(circles.riskDelta() > 0.0F ? 0xFF8855 : 0x55FFAA));
        }
    }

    private static Component ringState(final CauldronChalkCircles.Ring ring) {
        return switch (ring.kind()) {
            case EMPTY -> Component.translatable("overlay.warlockery.cauldron.chalk.empty");
            case RITUAL -> Component.translatable(
                "overlay.warlockery.cauldron.chalk.ritual", ring.required()
            );
            case INFERNAL -> Component.translatable(
                "overlay.warlockery.cauldron.chalk.infernal", ring.required()
            );
            case INCOMPLETE -> Component.translatable(
                "overlay.warlockery.cauldron.chalk.incomplete", ring.present(), ring.required()
            );
            case MIXED -> Component.translatable(
                "overlay.warlockery.cauldron.chalk.mixed", ring.present(), ring.required()
            );
        };
    }

    private static String formatMultiplier(final float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String joinMissing(final List<MachineRecipeManager.MissingInput> entries) {
        return compact(entries.stream()
            .map(entry -> entry.count() + "× " + ItemDisplayNames.text(entry.ingredient()))
            .toList());
    }

    private static String joinWrong(final List<MachineRecipeManager.WrongInput> entries) {
        return compact(entries.stream()
            .map(entry -> entry.count() + "× " + ItemDisplayNames.text(entry.item()))
            .toList());
    }

    private static String compact(final List<String> values) {
        final String first = values.stream().limit(3).collect(Collectors.joining(", "));
        return values.size() > 3 ? first + " +" + (values.size() - 3) : first;
    }

    public static final class State extends BlockEntityRenderState {
        private List<Component> lines = List.of();
    }
}
