package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.crafting.MachineStatus;
import com.kadamitas.warlockery.menu.MachineMenu;
import com.kadamitas.warlockery.menu.MachineUiLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int TEXT = 0xFF252A2C;

    private final MachineUiLayout layout;

    public MachineScreen(final MachineMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title, menu.layout().width(), menu.layout().height());
        layout = menu.layout();
        titleLabelX = 12;
        titleLabelY = 8;
        inventoryLabelX = layout.inventoryX();
        inventoryLabelY = layout.inventoryY() - 12;
    }

    @Override
    public void extractBackground(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final float partialTick
    ) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        final int x = leftPos;
        final int y = topPos;

        MachineScreenArt.chassis(graphics, x, y, layout);
        MachineScreenArt.machine(graphics, x, y, layout, new MachineScreenArt.State(
            menu.progressPercent(),
            menu.fluidAmount(),
            menu.availableAltarPower(),
            menu.requiredAltarPower(),
            menu.totalAltarPower(),
            menu.altarMillipowerPerTick(),
            menu.powerMode()
        ));
        MachineScreenArt.slots(graphics, x, y, layout);
        MachineScreenArt.inventory(graphics, x, y, layout);
        status(graphics, x, y, menu.status(), menu.progressPercent());
    }

    @Override
    protected void extractLabels(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF202426, false);
    }

    private void status(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final MachineStatus status,
        final int progress
    ) {
        final int color = statusColor(status, layout.accent());
        final Component message = statusMessage(status, progress);
        MachineScreenArt.statusLamp(graphics, x + 11, y + layout.statusY() + 3, color, status);
        if (!message.getString().isBlank()) {
            final int maxWidth = layout.width() - 42;
            final String compact = font.plainSubstrByWidth(message.getString(), maxWidth);
            graphics.text(font, Component.literal(compact), x + 25, y + layout.statusY() + 3, color, false);
        }
    }

    private static Component statusMessage(final MachineStatus status, final int progress) {
        return switch (status) {
            case EMPTY -> Component.empty();
            case INVALID -> Component.translatable("overlay.warlockery.machine.invalid");
            case INCOMPLETE -> Component.translatable("overlay.warlockery.machine.incomplete");
            case NO_HEAT -> Component.translatable("overlay.warlockery.cauldron.no_heat");
            case NO_FUEL -> Component.translatable("overlay.warlockery.cauldron.no_fuel");
            case NO_ALTAR_POWER -> Component.translatable("overlay.warlockery.machine.no_altar_power");
            case NO_FAMILIAR -> Component.translatable("overlay.warlockery.machine.no_familiar");
            case NO_IGNITION -> Component.translatable("overlay.warlockery.machine.no_ignition");
            case OUTPUT_BLOCKED -> Component.translatable("overlay.warlockery.cauldron.output_blocked");
            case READY -> Component.translatable("overlay.warlockery.cauldron.ready");
            case PROCESSING -> Component.translatable("overlay.warlockery.machine.processing", progress);
            default -> Component.empty();
        };
    }

    private static int statusColor(final MachineStatus status, final int accent) {
        return switch (status) {
            case EMPTY -> 0xFF666E70;
            case INVALID, OUTPUT_BLOCKED -> 0xFFB72E32;
            case INCOMPLETE, NO_HEAT, NO_FUEL, NO_ALTAR_POWER, NO_FAMILIAR, NO_IGNITION -> 0xFF9B6716;
            case READY -> 0xFF2E7E43;
            case PROCESSING -> accent;
        };
    }
}
