package com.kadamitas.warlockery.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class ManualReferenceButton extends Button {
    ManualReferenceButton(final int x, final int y, final int width, final Component label, final OnPress action) {
        super(x, y, Math.max(1, width), ManualTypography.BODY_LINE_HEIGHT, label, action, DEFAULT_NARRATION);
    }

    @Override
    protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        if (isHoveredOrFocused()) graphics.fill(getX(), getY() + 9, getX() + getWidth(), getY() + 11, 0xFF174B9A);
    }
}
