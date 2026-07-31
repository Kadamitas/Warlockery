package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.menu.MachineMenu;
import com.kadamitas.warlockery.menu.MachineUiLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    public MachineScreen(final MachineMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title, 176, 185);
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelY = 91;
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        final int x = leftPos;
        final int y = topPos;
        final MachineUiLayout layout = menu.layout();
        panel(graphics, x, y, layout, menu.progressPercent());
        machineMotif(graphics, x, y, layout);
        layout.slots().forEach(position -> slot(graphics, x + position.x(), y + position.y(), layout.accent()));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(graphics, x + 8 + column * 18, y + 103 + row * 18, 0xFF8E765B);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(graphics, x + 8 + column * 18, y + 161, 0xFF8E765B);
        }
    }

    @Override
    protected void extractLabels(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xFFF4E5C5, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF3A261B, false);
    }

    private static void panel(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final MachineUiLayout layout,
        final int progressPercent
    ) {
        graphics.fill(x, y, x + 176, y + 185, 0xFF231A22);
        graphics.fill(x + 3, y + 3, x + 173, y + 91, layout.panel());
        graphics.fill(x + 3, y + 94, x + 173, y + 182, 0xFFE4D2AE);
        graphics.fill(x + 5, y + 83, x + 171, y + 87, layout.accent());
        graphics.fill(x + 96, y + 13, x + 99, y + 76, 0xFF0C0A10);
        graphics.fill(x + 99, y + 42, x + 111, y + 46, layout.accent());
        graphics.fill(x + 106, y + 38, x + 112, y + 50, layout.accent());
        graphics.fill(x + 104, y + 68, x + 164, y + 75, 0xFF0B0910);
        graphics.fill(x + 106, y + 70, x + 106 + progressPercent * 56 / 100, y + 73, layout.accent());
    }

    private static void machineMotif(final GuiGraphicsExtractor graphics, final int x, final int y, final MachineUiLayout layout) {
        switch (layout.kind()) {
            case "spinningwheel" -> wheel(graphics, x + 49, y + 34, layout.accent());
            case "alchemical_oven", "brazier" -> flame(graphics, x + 49, y + 33, layout.accent());
            case "distillery" -> flask(graphics, x + 49, y + 31, layout.accent());
            default -> cauldron(graphics, x + 49, y + 33, layout.accent());
        }
    }

    private static void slot(final GuiGraphicsExtractor graphics, final int x, final int y, final int accent) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF0B0910);
        graphics.fill(x, y, x + 16, y + 16, 0xFF6A5B66);
        graphics.fill(x + 1, y + 1, x + 15, y + 15, 0xFF2A242D);
        graphics.fill(x + 1, y + 14, x + 15, y + 15, accent);
    }

    private static void wheel(final GuiGraphicsExtractor graphics, final int x, final int y, final int color) {
        graphics.fill(x - 10, y - 12, x + 10, y + 12, 0xFF120E15);
        graphics.fill(x - 8, y - 10, x + 8, y + 10, color);
        graphics.fill(x - 4, y - 6, x + 4, y + 6, 0xFF201725);
        graphics.fill(x - 12, y - 1, x + 12, y + 2, 0xFFE7D4A1);
        graphics.fill(x - 1, y - 14, x + 2, y + 14, 0xFFE7D4A1);
    }

    private static void flame(final GuiGraphicsExtractor graphics, final int x, final int y, final int color) {
        graphics.fill(x - 10, y + 8, x + 11, y + 13, 0xFF151018);
        graphics.fill(x - 6, y - 5, x + 7, y + 9, color);
        graphics.fill(x - 2, y - 11, x + 4, y + 3, 0xFFFFC65C);
        graphics.fill(x, y - 4, x + 5, y + 7, 0xFFFFE7A0);
    }

    private static void flask(final GuiGraphicsExtractor graphics, final int x, final int y, final int color) {
        graphics.fill(x - 3, y - 13, x + 4, y - 5, 0xFFC7E7E6);
        graphics.fill(x - 10, y - 5, x + 11, y + 12, 0xFFC7E7E6);
        graphics.fill(x - 8, y - 3, x + 9, y + 10, 0xFF25212E);
        graphics.fill(x - 7, y + 3, x + 8, y + 9, color);
    }

    private static void cauldron(final GuiGraphicsExtractor graphics, final int x, final int y, final int color) {
        graphics.fill(x - 13, y - 5, x + 14, y, 0xFF111016);
        graphics.fill(x - 10, y, x + 11, y + 12, 0xFF302B35);
        graphics.fill(x - 8, y, x + 9, y + 4, color);
        graphics.fill(x - 8, y + 12, x - 4, y + 16, 0xFF111016);
        graphics.fill(x + 5, y + 12, x + 9, y + 16, 0xFF111016);
    }
}
