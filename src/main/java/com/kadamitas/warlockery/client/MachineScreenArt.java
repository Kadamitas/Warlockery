package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.crafting.MachineStatus;
import com.kadamitas.warlockery.crafting.PowerMode;
import com.kadamitas.warlockery.menu.MachineUiLayout;
import com.kadamitas.warlockery.menu.MachineUiLayout.SlotPosition;
import com.kadamitas.warlockery.menu.MachineUiLayout.SlotRole;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class MachineScreenArt {
    private static final int OUTLINE = 0xFF343A3E;
    private static final int STEEL_DARK = 0xFF737B7D;
    private static final int STEEL = 0xFFADB4B2;
    private static final int STEEL_LIGHT = 0xFFE8EAE5;
    private static final int PAPER = 0xFFF1EAD8;
    private static final int PAPER_EDGE = 0xFFC3B9A4;
    private static final int WELL = 0xFF686E6D;
    private static final int WELL_DARK = 0xFF494F50;
    private static final int GLASS = 0xFF9BC5C8;
    private static final int COPPER = 0xFFB76D3C;
    private static final int COPPER_LIGHT = 0xFFE1A066;
    private static final int WOOD = 0xFFB17D48;
    private static final int WOOD_LIGHT = 0xFFE0B572;
    private static final int PURPLE = 0xFF76529B;

    private MachineScreenArt() {
    }

    record State(
        int progress,
        int fluid,
        int availablePower,
        int requiredPower,
        int totalPower,
        int millipowerPerTick,
        PowerMode powerMode
    ) {
        boolean usesAltarPower() {
            return powerMode != PowerMode.NONE && totalPower > 0;
        }
    }

    static void chassis(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final MachineUiLayout layout
    ) {
        final int width = layout.width();
        final int height = layout.height();
        graphics.fill(x, y, x + width, y + height, OUTLINE);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, STEEL_DARK);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, STEEL_LIGHT);
        graphics.fill(x + 5, y + 5, x + width - 5, y + layout.inventoryY() - 18, layout.panel());

        graphics.fill(x + 6, y + 6, x + width - 6, y + 21, 0xFFF0F0E9);
        graphics.fill(x + 6, y + 21, x + width - 6, y + 24, layout.accent());
        graphics.fill(x + 6, y + 21, x + width - 6, y + 22, 0x88FFFFFF);

        graphics.fill(x + 7, y + layout.statusY(), x + width - 7, y + layout.statusY() + 13, PAPER_EDGE);
        graphics.fill(x + 8, y + layout.statusY() + 1, x + width - 8, y + layout.statusY() + 12, PAPER);

        rivet(graphics, x + 7, y + 7);
        rivet(graphics, x + width - 10, y + 7);
        rivet(graphics, x + 7, y + height - 10);
        rivet(graphics, x + width - 10, y + height - 10);
    }

    static void machine(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final MachineUiLayout layout,
        final State state
    ) {
        switch (layout.kind()) {
            case "alchemical_oven" -> oven(graphics, x, y, state.progress(), layout.accent());
            case "distillery" -> distillery(graphics, x, y, state, layout.accent());
            case "kettle" -> kettle(graphics, x, y, state, layout.accent());
            case "cauldron" -> cauldron(graphics, x, y, state, layout.accent());
            case "silvervat" -> silverVat(graphics, x, y, state.progress(), layout.accent());
            case "spinningwheel" -> spinningWheel(graphics, x, y, state, layout.accent());
            case "brazier" -> brazier(graphics, x, y, state, layout.accent());
            default -> cauldron(graphics, x, y, state, layout.accent());
        }
    }

    static void slots(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final MachineUiLayout layout
    ) {
        for (final SlotPosition slot : layout.slots()) {
            if (slot.visible()) {
                slot(graphics, x + slot.x(), y + slot.y(), roleColor(slot.role(), layout.accent()));
            }
        }
    }

    static void inventory(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final MachineUiLayout layout
    ) {
        final int left = x + layout.inventoryX() - 7;
        final int top = y + layout.inventoryY() - 17;
        final int right = x + layout.inventoryX() + 169;
        final int bottom = y + layout.height() - 5;
        graphics.fill(left, top, right, bottom, PAPER_EDGE);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, PAPER);
        graphics.fill(left + 3, y + layout.inventoryY() - 3, right - 3, y + layout.inventoryY() - 1, 0xFFAFA58F);
        graphics.fill(left + 3, y + layout.inventoryY() - 1, right - 3, y + layout.inventoryY(), 0x99FFFFFF);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                inventorySlot(
                    graphics,
                    x + layout.inventoryX() + column * 18,
                    y + layout.inventoryY() + row * 18
                );
            }
        }
        final int hotbarY = y + layout.inventoryY() + 58;
        graphics.fill(left + 3, hotbarY - 4, right - 3, hotbarY - 2, 0xFFAFA58F);
        for (int column = 0; column < 9; column++) {
            inventorySlot(graphics, x + layout.inventoryX() + column * 18, hotbarY);
        }
    }

    static void statusLamp(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final int color,
        final MachineStatus status
    ) {
        graphics.fill(x, y, x + 8, y + 8, OUTLINE);
        graphics.fill(x + 1, y + 1, x + 7, y + 7, STEEL);
        if (status == MachineStatus.INVALID || status == MachineStatus.OUTPUT_BLOCKED) {
            graphics.fill(x + 2, y + 2, x + 3, y + 6, color);
            graphics.fill(x + 5, y + 2, x + 6, y + 6, color);
            graphics.fill(x + 3, y + 3, x + 5, y + 5, color);
        } else if (status == MachineStatus.READY) {
            graphics.fill(x + 2, y + 4, x + 4, y + 6, color);
            graphics.fill(x + 4, y + 2, x + 7, y + 4, color);
        } else {
            graphics.fill(x + 2, y + 2, x + 6, y + 6, color);
            graphics.fill(x + 3, y + 1, x + 5, y + 7, color);
        }
    }

    private static void oven(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final int progress,
        final int accent
    ) {
        plate(graphics, x + 9, y + 28, x + 65, y + 83, 0xFFE1D2C1);
        plate(graphics, x + 139, y + 28, x + 190, y + 83, 0xFFE1D2C1);

        graphics.fill(x + 72, y + 29, x + 137, y + 96, OUTLINE);
        graphics.fill(x + 75, y + 31, x + 134, y + 94, 0xFF77736F);
        graphics.fill(x + 80, y + 36, x + 129, y + 80, 0xFF4D4B49);
        graphics.fill(x + 84, y + 41, x + 125, y + 75, 0xFF2F3030);
        graphics.fill(x + 89, y + 59, x + 120, y + 73, 0xFF6D3324);
        graphics.fill(x + 96, y + 50, x + 113, y + 72, accent);
        graphics.fill(x + 101, y + 43, x + 109, y + 65, 0xFFFFB94D);
        graphics.fill(x + 105, y + 39, x + 111, y + 55, 0xFFFFE08A);

        for (int funnel = 0; funnel < 3; funnel++) {
            final int fx = x + 82 + funnel * 20;
            graphics.fill(fx, y + 27, fx + 14, y + 30, STEEL_DARK);
            graphics.fill(fx + 3, y + 30, fx + 11, y + 33, STEEL);
            graphics.fill(fx + 6, y + 33, fx + 8, y + 37, OUTLINE);
        }

        graphics.fill(x + 96, y + 82, x + 132, y + 91, OUTLINE);
        graphics.fill(x + 98, y + 84, x + 130, y + 89, 0xFFE7E7DF);
        graphics.fill(x + 99, y + 85, x + 99 + progress * 30 / 100, y + 88, accent);
        arrow(graphics, x + 132, y + 51, accent);
        graphics.fill(x + 63, y + 48, x + 74, y + 52, accent);
        graphics.fill(x + 64, y + 49, x + 73, y + 50, 0x88FFFFFF);
    }

    private static void distillery(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final State state,
        final int accent
    ) {
        final int progress = state.progress();
        graphics.fill(x + 9, y + 28, x + 44, y + 109, 0xFFC7BBCD);
        graphics.fill(x + 11, y + 30, x + 42, y + 107, 0xFFE8E1E9);
        graphics.fill(x + 155, y + 28, x + 205, y + 87, 0xFFC7BBCD);
        graphics.fill(x + 157, y + 30, x + 203, y + 85, 0xFFE8E1E9);

        processGauge(graphics, x + 58, y + 32, 10, 36, progress, accent);
        graphics.fill(x + 60, y + 75, x + 112, y + 103, OUTLINE);
        graphics.fill(x + 64, y + 68, x + 108, y + 99, COPPER);
        graphics.fill(x + 69, y + 72, x + 103, y + 95, COPPER_LIGHT);
        graphics.fill(x + 77, y + 34, x + 96, y + 70, OUTLINE);
        graphics.fill(x + 80, y + 30, x + 93, y + 70, COPPER_LIGHT);
        graphics.fill(x + 83, y + 34, x + 90, y + 67, 0xFFF0C095);

        graphics.fill(x + 93, y + 36, x + 128, y + 41, COPPER);
        graphics.fill(x + 124, y + 39, x + 130, y + 83, COPPER);
        for (int coil = 0; coil < 4; coil++) {
            final int cy = y + 47 + coil * 9;
            graphics.fill(x + 105, cy, x + 126, cy + 3, OUTLINE);
            graphics.fill(x + 108, cy + 1, x + 125, cy + 2, coil * 25 < progress ? accent : COPPER_LIGHT);
        }
        graphics.fill(x + 128, y + 79, x + 158, y + 84, COPPER);
        graphics.fill(x + 148, y + 82, x + 158, y + 91, GLASS);
        graphics.fill(x + 151, y + 85, x + 158, y + 91, accent);

        altarRail(graphics, x + 49, y + 32, 66, state, PURPLE);
        arrow(graphics, x + 145, y + 53, accent);
    }

    private static void kettle(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final State state,
        final int accent
    ) {
        final int progress = state.progress();
        graphics.fill(x + 10, y + 28, x + 91, y + 105, 0xFFC1D0C4);
        graphics.fill(x + 12, y + 30, x + 89, y + 103, 0xFFE4ECE5);
        for (int arm = 0; arm < 6; arm++) {
            final int ax = x + 38 + (arm % 3) * 16;
            final int ay = y + 48 + (arm / 3) * 28;
            graphics.fill(ax, ay, ax + 20, ay + 3, 0xFF809B87);
        }

        tank(graphics, x + 96, y + 31, 11, 70, state.fluid(), accent);
        altarRail(graphics, x + 106, y + 31, 70, state, PURPLE);
        graphics.fill(x + 112, y + 44, x + 167, y + 93, OUTLINE);
        graphics.fill(x + 117, y + 48, x + 162, y + 88, 0xFF5E6662);
        graphics.fill(x + 121, y + 56, x + 158, y + 85, 0xFF343A38);
        graphics.fill(x + 123, y + 58, x + 156, y + 68, accent);
        graphics.fill(x + 129, y + 52, x + 151, y + 55, STEEL_LIGHT);
        graphics.fill(x + 116, y + 37, x + 161, y + 41, STEEL_DARK);
        graphics.fill(x + 119, y + 34, x + 158, y + 37, STEEL_LIGHT);
        graphics.fill(x + 112, y + 41, x + 118, y + 58, STEEL_DARK);
        graphics.fill(x + 161, y + 41, x + 167, y + 58, STEEL_DARK);
        graphics.fill(x + 130, y + 90, x + 149, y + 101, 0xFFAD4D27);
        graphics.fill(x + 136, y + 86, x + 144, y + 99, 0xFFFFBD4B);
        graphics.fill(x + 139, y + 83, x + 145, y + 92, 0xFFFFE18A);
        segmentedBar(graphics, x + 113, y + 102, 54, progress, accent);
        arrow(graphics, x + 165, y + 59, accent);
    }

    private static void cauldron(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final State state,
        final int accent
    ) {
        final int progress = state.progress();
        plate(graphics, x + 9, y + 27, x + 65, y + 106, 0xFFD5E2E2);
        tank(graphics, x + 72, y + 30, 12, 75, state.fluid(), accent);
        altarRail(graphics, x + 83, y + 30, 75, state, PURPLE);

        graphics.fill(x + 91, y + 49, x + 140, y + 91, OUTLINE);
        graphics.fill(x + 86, y + 44, x + 145, y + 51, STEEL_DARK);
        graphics.fill(x + 93, y + 53, x + 138, y + 87, 0xFF515B5C);
        graphics.fill(x + 98, y + 59, x + 133, y + 75, accent);
        graphics.fill(x + 101, y + 60, x + 130, y + 63, 0x88FFFFFF);
        graphics.fill(x + 96, y + 91, x + 104, y + 99, OUTLINE);
        graphics.fill(x + 127, y + 91, x + 135, y + 99, OUTLINE);
        bubble(graphics, x + 104, y + 54, progress >= 20);
        bubble(graphics, x + 118, y + 49, progress >= 50);
        bubble(graphics, x + 130, y + 55, progress >= 80);

        graphics.fill(x + 148, y + 28, x + 186, y + 106, 0xFF9DB9BB);
        graphics.fill(x + 151, y + 31, x + 183, y + 103, 0xFFE4EEEE);
        for (int ring = 0; ring < 3; ring++) {
            final int inset = ring * 4;
            graphics.fill(x + 157 + inset, y + 37 + inset, x + 177 - inset, y + 39 + inset, ring == 0 ? accent : STEEL_DARK);
            graphics.fill(x + 157 + inset, y + 37 + inset, x + 159 + inset, y + 57 - inset, ring == 0 ? accent : STEEL_DARK);
            graphics.fill(x + 175 - inset, y + 37 + inset, x + 177 - inset, y + 57 - inset, ring == 0 ? accent : STEEL_DARK);
            graphics.fill(x + 157 + inset, y + 55 - inset, x + 177 - inset, y + 57 - inset, ring == 0 ? accent : STEEL_DARK);
        }
        for (int pip = 0; pip < 5; pip++) {
            graphics.fill(x + 155 + pip * 5, y + 69, x + 158 + pip * 5, y + 74, pip * 20 < progress ? accent : STEEL);
        }
        segmentedBar(graphics, x + 153, y + 85, 28, progress, accent);
        arrow(graphics, x + 181, y + 60, accent);
    }

    private static void silverVat(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final int progress,
        final int accent
    ) {
        plate(graphics, x + 9, y + 27, x + 95, y + 89, 0xFFE2E7E5);
        graphics.fill(x + 12, y + 91, x + 94, y + 104, 0xFF98A3A3);
        segmentedBar(graphics, x + 16, y + 95, 74, progress, accent);

        graphics.fill(x + 104, y + 40, x + 165, y + 91, OUTLINE);
        graphics.fill(x + 108, y + 35, x + 161, y + 88, 0xFF89989B);
        graphics.fill(x + 113, y + 42, x + 156, y + 83, 0xFFD7E0E0);
        graphics.fill(x + 118, y + 50, x + 151, y + 79, 0xFF9CA8A9);
        graphics.fill(x + 121, y + 62, x + 148, y + 77, 0xFFD9DEDC);
        graphics.fill(x + 123, y + 63, x + 146, y + 66, 0xCCFFFFFF);

        for (int sensor = 0; sensor < 6; sensor++) {
            final int sx = x + 105 + (sensor % 3) * 24;
            final int sy = y + 27 + (sensor / 3) * 68;
            graphics.fill(sx, sy, sx + 14, sy + 8, OUTLINE);
            graphics.fill(sx + 2, sy + 2, sx + 12, sy + 6, sensor == 1 ? accent : STEEL);
            graphics.fill(sx + 5, sy + 7, sx + 9, sy + 13, STEEL_DARK);
        }
        arrow(graphics, x + 164, y + 58, accent);
    }

    private static void spinningWheel(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final State state,
        final int accent
    ) {
        final int progress = state.progress();
        graphics.fill(x + 9, y + 45, x + 40, y + 87, WOOD);
        graphics.fill(x + 11, y + 47, x + 38, y + 85, 0xFFF0D8AB);
        graphics.fill(x + 43, y + 24, x + 78, y + 108, WOOD);
        graphics.fill(x + 46, y + 27, x + 75, y + 105, 0xFFEBD1A5);

        graphics.fill(x + 80, y + 28, x + 169, y + 103, WOOD);
        graphics.fill(x + 84, y + 32, x + 165, y + 99, 0xFFF0DCB5);
        final int cx = x + 124;
        final int cy = y + 64;
        wheelRing(graphics, cx, cy, progress, accent);
        graphics.fill(cx - 2, cy - 28, cx + 2, cy + 28, progress >= 25 ? accent : WOOD);
        graphics.fill(cx - 28, cy - 2, cx + 28, cy + 2, progress >= 50 ? accent : WOOD);
        graphics.fill(cx - 20, cy - 20, cx - 17, cy - 17, progress >= 75 ? accent : WOOD_LIGHT);
        graphics.fill(cx + 17, cy + 17, cx + 20, cy + 20, progress >= 75 ? accent : WOOD_LIGHT);
        graphics.fill(cx - 3, cy - 3, cx + 4, cy + 4, OUTLINE);
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, STEEL_LIGHT);

        graphics.fill(x + 78, y + 101, x + 174, y + 106, WOOD);
        graphics.fill(x + 93, y + 106, x + 99, y + 111, WOOD);
        graphics.fill(x + 155, y + 106, x + 161, y + 111, WOOD);
        graphics.fill(x + 39, y + 65, x + 96, y + 67, accent);
        graphics.fill(x + 153, y + 64, x + 186, y + 66, accent);
        altarRail(graphics, x + 82, y + 29, 68, state, PURPLE);
    }

    private static void brazier(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final State state,
        final int accent
    ) {
        final int progress = state.progress();
        graphics.fill(x + 9, y + 28, x + 98, y + 102, 0xFFE7D7C9);
        graphics.fill(x + 25, y + 83, x + 84, y + 87, 0xFFC19775);
        graphics.fill(x + 47, y + 43, x + 50, y + 82, 0xFFC19775);
        graphics.fill(x + 31, y + 50, x + 68, y + 53, 0xFFC19775);

        graphics.fill(x + 107, y + 79, x + 162, y + 88, OUTLINE);
        graphics.fill(x + 113, y + 87, x + 156, y + 96, STEEL_DARK);
        graphics.fill(x + 120, y + 95, x + 128, y + 103, OUTLINE);
        graphics.fill(x + 143, y + 95, x + 151, y + 103, OUTLINE);
        graphics.fill(x + 114, y + 73, x + 155, y + 83, 0xFF7C4940);
        graphics.fill(x + 119, y + 69, x + 150, y + 78, accent);
        final int flame = 11 + progress * 29 / 100;
        graphics.fill(x + 125, y + 69 - flame, x + 146, y + 74, accent);
        graphics.fill(x + 132, y + 62 - flame, x + 143, y + 72, 0xFFFFB43C);
        graphics.fill(x + 136, y + 58 - flame, x + 142, y + 66, 0xFFFFE18A);

        altarRail(graphics, x + 101, y + 31, 37, state, PURPLE);
        graphics.fill(x + 160, y + 76, x + 172, y + 80, accent);
        graphics.fill(x + 162, y + 77, x + 171, y + 78, 0x88FFFFFF);
    }

    private static void plate(
        final GuiGraphicsExtractor graphics,
        final int x1,
        final int y1,
        final int x2,
        final int y2,
        final int inside
    ) {
        graphics.fill(x1, y1, x2, y2, OUTLINE);
        graphics.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, STEEL_LIGHT);
        graphics.fill(x1 + 3, y1 + 3, x2 - 2, y2 - 2, inside);
        graphics.fill(x1 + 4, y1 + 4, x2 - 3, y1 + 5, 0x99FFFFFF);
    }

    private static void slot(final GuiGraphicsExtractor graphics, final int x, final int y, final int role) {
        graphics.fill(x - 3, y - 3, x + 19, y + 19, OUTLINE);
        graphics.fill(x - 2, y - 2, x + 18, y + 18, role);
        graphics.fill(x - 1, y - 1, x + 17, y + 17, STEEL_LIGHT);
        graphics.fill(x, y, x + 16, y + 16, WELL);
        graphics.fill(x + 1, y + 1, x + 15, y + 3, WELL_DARK);
        graphics.fill(x + 2, y + 14, x + 14, y + 16, role);
        graphics.fill(x + 3, y + 12, x + 13, y + 14, 0x66FFFFFF);
    }

    private static void inventorySlot(final GuiGraphicsExtractor graphics, final int x, final int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8577);
        graphics.fill(x, y, x + 16, y + 16, 0xFFFDF8EA);
        graphics.fill(x + 1, y + 1, x + 15, y + 15, 0xFFBDB6A5);
        graphics.fill(x + 2, y + 2, x + 14, y + 14, 0xFFD9D2C1);
        graphics.fill(x + 2, y + 13, x + 14, y + 14, 0xFFF6EEDB);
    }

    private static int roleColor(final SlotRole role, final int accent) {
        return switch (role) {
            case MATERIAL, REFINING_INPUT -> 0xFFA7773D;
            case INGREDIENT -> 0xFF5D9368;
            case FIBRE -> 0xFFC18B45;
            case MODIFIER -> 0xFF618FA2;
            case OFFERING -> 0xFFB45635;
            case JAR -> 0xFF78A7B3;
            case FUEL -> 0xFFD76B22;
            case FUME_OUTPUT -> 0xFF8051AC;
            case ASH_OUTPUT -> 0xFF8D8580;
            case PRIMARY_OUTPUT, OUTPUT -> accent;
            case UNUSED -> STEEL_DARK;
        };
    }

    private static void segmentedBar(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final int width,
        final int progress,
        final int color
    ) {
        graphics.fill(x, y, x + width, y + 8, OUTLINE);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 6, 0xFFE9EAE4);
        final int filled = progress * (width - 5) / 100;
        graphics.fill(x + 3, y + 3, x + 3 + filled, y + 5, color);
        for (int marker = 1; marker < 4; marker++) {
            final int mx = x + 2 + marker * (width - 4) / 4;
            graphics.fill(mx, y + 1, mx + 1, y + 7, OUTLINE);
        }
    }

    private static void tank(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final int width,
        final int height,
        final int fluid,
        final int color
    ) {
        graphics.fill(x, y, x + width, y + height, OUTLINE);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFFEAF0EC);
        final int fill = fluid * (height - 5) / 4_000;
        graphics.fill(x + 3, y + height - 3 - fill, x + width - 3, y + height - 3, color);
        graphics.fill(x + 4, y + height - 3 - fill, x + width - 4, y + height - 2 - fill, 0xAAFFFFFF);
        for (int marker = 1; marker < 4; marker++) {
            final int my = y + 2 + marker * (height - 4) / 4;
            graphics.fill(x + width - 2, my, x + width + 2, my + 1, STEEL_DARK);
        }
    }

    private static void processGauge(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final int width,
        final int height,
        final int progress,
        final int color
    ) {
        graphics.fill(x, y, x + width, y + height, OUTLINE);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFFE9EAE4);
        final int fill = Math.clamp(progress, 0, 100) * (height - 5) / 100;
        graphics.fill(x + 3, y + height - 3 - fill, x + width - 3, y + height - 3, color);
        graphics.fill(x + 4, y + height - 3 - fill, x + width - 4, y + height - 2 - fill, 0xAAFFFFFF);
        for (int marker = 1; marker < 4; marker++) {
            final int my = y + 2 + marker * (height - 4) / 4;
            graphics.fill(x + width - 2, my, x + width + 2, my + 1, STEEL_DARK);
        }
    }

    private static void altarRail(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final int height,
        final State state,
        final int color
    ) {
        if (!state.usesAltarPower()) {
            return;
        }
        graphics.fill(x, y, x + 8, y + height, OUTLINE);
        graphics.fill(x + 2, y + 2, x + 6, y + height - 2, 0xFFE6E4E2);
        final int fill = (int) ((long) Math.min(state.availablePower(), state.totalPower()) * (height - 5)
            / Math.max(1, state.totalPower()));
        graphics.fill(x + 3, y + height - 3 - fill, x + 5, y + height - 3, color);
        if (state.requiredPower() > 0) {
            final int requiredY = y + height - 3 - (int) (
                (long) Math.min(state.requiredPower(), state.totalPower()) * (height - 5)
                    / Math.max(1, state.totalPower())
            );
            graphics.fill(x + 1, requiredY, x + 7, requiredY + 1, 0xFFB72E32);
        }
        for (int marker = 1; marker < 4; marker++) {
            final int my = y + marker * height / 4;
            graphics.fill(x + 6, my, x + 10, my + 1, STEEL_DARK);
        }
    }

    private static void arrow(final GuiGraphicsExtractor graphics, final int x, final int y, final int color) {
        graphics.fill(x, y, x + 10, y + 4, color);
        graphics.fill(x + 7, y - 3, x + 10, y + 7, color);
        graphics.fill(x + 10, y - 1, x + 13, y + 5, color);
        graphics.fill(x + 1, y + 1, x + 8, y + 2, 0x88FFFFFF);
    }

    private static void bubble(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final boolean active
    ) {
        final int color = active ? 0xFFEAFBFF : 0xFF8AA3A5;
        graphics.fill(x, y, x + 4, y + 4, color);
        graphics.fill(x + 1, y - 1, x + 3, y + 5, color);
    }

    private static void wheelRing(
        final GuiGraphicsExtractor graphics,
        final int cx,
        final int cy,
        final int progress,
        final int color
    ) {
        final int inactive = WOOD_LIGHT;
        graphics.fill(cx - 22, cy - 28, cx + 22, cy - 24, progress >= 12 ? color : inactive);
        graphics.fill(cx - 22, cy + 24, cx + 22, cy + 28, progress >= 62 ? color : inactive);
        graphics.fill(cx - 28, cy - 22, cx - 24, cy + 22, progress >= 37 ? color : inactive);
        graphics.fill(cx + 24, cy - 22, cx + 28, cy + 22, progress >= 87 ? color : inactive);
        graphics.fill(cx - 26, cy - 25, cx - 19, cy - 20, inactive);
        graphics.fill(cx + 19, cy - 25, cx + 26, cy - 20, inactive);
        graphics.fill(cx - 26, cy + 20, cx - 19, cy + 25, inactive);
        graphics.fill(cx + 19, cy + 20, cx + 26, cy + 25, inactive);
    }

    private static void rivet(final GuiGraphicsExtractor graphics, final int x, final int y) {
        graphics.fill(x, y, x + 2, y + 2, 0xFFF9FAF4);
        graphics.fill(x + 1, y + 1, x + 3, y + 3, 0xFF62696A);
    }
}

