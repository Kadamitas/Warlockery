package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.menu.DollShelfMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class DollShelfScreen extends AbstractContainerScreen<DollShelfMenu> {
    public DollShelfScreen(final DollShelfMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title, 176, 185);
        inventoryLabelY = 91;
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        final int x = leftPos;
        final int y = topPos;
        graphics.fill(x, y, x + 176, y + 185, 0xFF2C1B12);
        graphics.fill(x + 3, y + 3, x + 173, y + 91, 0xFF704322);
        graphics.fill(x + 3, y + 94, x + 173, y + 182, 0xFFE0C99D);
        graphics.fill(x + 25, y + 10, x + 117, y + 87, 0xFF392318);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                final int sx = x + 34 + column * 24;
                final int sy = y + 16 + row * 24;
                graphics.fill(sx - 3, sy - 3, sx + 19, sy + 19, 0xFF9B6332);
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF171016);
                graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF3A2730);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(graphics, x + 8 + column * 18, y + 103 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(graphics, x + 8 + column * 18, y + 161);
        }
    }

    @Override
    protected void extractLabels(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        graphics.text(font, title, 8, 6, 0xFFFFE8BA, false);
        graphics.text(font, playerInventoryTitle, 8, inventoryLabelY, 0xFF3A261B, false);
    }

    private static void slot(final GuiGraphicsExtractor graphics, final int x, final int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF5B432D);
        graphics.fill(x, y, x + 16, y + 16, 0xFFB59A70);
    }
}
