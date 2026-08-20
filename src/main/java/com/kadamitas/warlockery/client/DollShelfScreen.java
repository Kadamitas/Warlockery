package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.menu.DollShelfMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class DollShelfScreen extends AbstractContainerScreen<DollShelfMenu> {
    private static final int OUTLINE = 0xFF49382C;
    private static final int OAK_DARK = 0xFF93603B;
    private static final int OAK = 0xFFC68E54;
    private static final int OAK_LIGHT = 0xFFE7BF82;
    private static final int BRASS = 0xFFB57824;
    private static final int CUBBY = 0xFF70584A;
    private static final int PAPER = 0xFFF1EAD8;
    private static final int PAPER_EDGE = 0xFFC3B9A4;

    public DollShelfScreen(final DollShelfMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title, DollShelfMenu.SCREEN_WIDTH, DollShelfMenu.SCREEN_HEIGHT);
        titleLabelX = 12;
        titleLabelY = 8;
        inventoryLabelX = DollShelfMenu.INVENTORY_X;
        inventoryLabelY = DollShelfMenu.INVENTORY_Y - 12;
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

        cabinet(graphics, x, y);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                cubby(
                    graphics,
                    x + DollShelfMenu.SHELF_X + column * 24,
                    y + DollShelfMenu.SHELF_Y + row * 24,
                    row * 3 + column
                );
            }
        }
        inventory(graphics, x, y);
    }

    @Override
    protected void extractLabels(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xFF34271F, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF202426, false);
    }

    private static void cabinet(final GuiGraphicsExtractor graphics, final int x, final int y) {
        graphics.fill(x, y, x + DollShelfMenu.SCREEN_WIDTH, y + DollShelfMenu.SCREEN_HEIGHT, OUTLINE);
        graphics.fill(x + 2, y + 2, x + 194, y + 210, OAK_DARK);
        graphics.fill(x + 3, y + 3, x + 193, y + 209, OAK_LIGHT);
        graphics.fill(x + 5, y + 5, x + 191, y + 115, OAK);
        graphics.fill(x + 6, y + 6, x + 190, y + 21, 0xFFEED09D);
        graphics.fill(x + 6, y + 21, x + 190, y + 24, BRASS);

        graphics.fill(x + 40, y + 27, x + 156, y + 111, OUTLINE);
        graphics.fill(x + 44, y + 29, x + 152, y + 108, OAK_LIGHT);
        graphics.fill(x + 48, y + 31, x + 148, y + 105, OAK_DARK);
        graphics.fill(x + 50, y + 33, x + 146, y + 103, 0xFFB47849);
        for (int row = 0; row < 3; row++) {
            final int shelfY = y + 52 + row * 24;
            graphics.fill(x + 45, shelfY, x + 151, shelfY + 6, OUTLINE);
            graphics.fill(x + 47, shelfY, x + 149, shelfY + 4, OAK_LIGHT);
            graphics.fill(x + 49, shelfY + 1, x + 147, shelfY + 2, 0xFFF1D19A);
            graphics.fill(x + 47, shelfY + 4, x + 149, shelfY + 6, OAK_DARK);
        }

        graphics.fill(x + 29, y + 36, x + 40, y + 103, OAK_DARK);
        graphics.fill(x + 156, y + 36, x + 167, y + 103, OAK_DARK);
        graphics.fill(x + 27, y + 46, x + 31, y + 62, OAK_LIGHT);
        graphics.fill(x + 165, y + 46, x + 169, y + 62, OAK_LIGHT);
        graphics.fill(x + 27, y + 77, x + 31, y + 93, OAK_LIGHT);
        graphics.fill(x + 165, y + 77, x + 169, y + 93, OAK_LIGHT);

        graphics.fill(x + 35, y + 109, x + 161, y + 115, OAK_DARK);
        graphics.fill(x + 47, y + 115, x + 58, y + 119, OUTLINE);
        graphics.fill(x + 138, y + 115, x + 149, y + 119, OUTLINE);
        graphics.fill(x + 91, y + 27, x + 105, y + 31, BRASS);

        stud(graphics, x + 8, y + 8);
        stud(graphics, x + 185, y + 8);
        stud(graphics, x + 8, y + 202);
        stud(graphics, x + 185, y + 202);
    }

    private static void cubby(
        final GuiGraphicsExtractor graphics,
        final int x,
        final int y,
        final int index
    ) {
        graphics.fill(x - 3, y - 3, x + 19, y + 19, OUTLINE);
        graphics.fill(x - 2, y - 2, x + 18, y + 18, OAK_LIGHT);
        graphics.fill(x - 1, y - 1, x + 17, y + 17, OAK_DARK);
        graphics.fill(x, y, x + 16, y + 16, CUBBY);
        graphics.fill(x + 1, y + 1, x + 15, y + 4, 0xFF534036);
        graphics.fill(x + 2, y + 13, x + 14, y + 16, BRASS);
        graphics.fill(x + 6, y + 14, x + 10, y + 15, index % 2 == 0 ? 0xFFE6C774 : 0xFFD8A94D);
    }

    private static void inventory(final GuiGraphicsExtractor graphics, final int x, final int y) {
        final int left = x + DollShelfMenu.INVENTORY_X - 7;
        final int top = y + DollShelfMenu.INVENTORY_Y - 17;
        final int right = x + DollShelfMenu.INVENTORY_X + 169;
        final int bottom = y + DollShelfMenu.SCREEN_HEIGHT - 5;
        graphics.fill(left, top, right, bottom, PAPER_EDGE);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, PAPER);
        graphics.fill(left + 3, y + DollShelfMenu.INVENTORY_Y - 3, right - 3, y + DollShelfMenu.INVENTORY_Y - 1, 0xFFAFA58F);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                inventorySlot(
                    graphics,
                    x + DollShelfMenu.INVENTORY_X + column * 18,
                    y + DollShelfMenu.INVENTORY_Y + row * 18
                );
            }
        }
        final int hotbarY = y + DollShelfMenu.INVENTORY_Y + 58;
        graphics.fill(left + 3, hotbarY - 4, right - 3, hotbarY - 2, 0xFFAFA58F);
        for (int column = 0; column < 9; column++) {
            inventorySlot(graphics, x + DollShelfMenu.INVENTORY_X + column * 18, hotbarY);
        }
    }

    private static void inventorySlot(final GuiGraphicsExtractor graphics, final int x, final int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8577);
        graphics.fill(x, y, x + 16, y + 16, 0xFFFDF8EA);
        graphics.fill(x + 1, y + 1, x + 15, y + 15, 0xFFBDB6A5);
        graphics.fill(x + 2, y + 2, x + 14, y + 14, 0xFFD9D2C1);
        graphics.fill(x + 2, y + 13, x + 14, y + 14, 0xFFF6EEDB);
    }

    private static void stud(final GuiGraphicsExtractor graphics, final int x, final int y) {
        graphics.fill(x, y, x + 2, y + 2, 0xFFF4D48D);
        graphics.fill(x + 1, y + 1, x + 3, y + 3, OAK_DARK);
    }
}
