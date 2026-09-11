package com.kadamitas.warlockery.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class ManualNavigationButton extends Button {
    private static final int PADDING = 4;
    private final Font font;
    private final List<FormattedCharSequence> lines;

    ManualNavigationButton(final Font font, final int x, final int y, final int width,
        final Component label, final OnPress onPress) {
        super(x, y, width, heightFor(font, label, width), label, onPress, DEFAULT_NARRATION);
        this.font = font;
        lines = font.split(label, Math.max(1, width - PADDING * 2));
    }

    static int heightFor(final Font font, final Component label, final int width) {
        return Math.max(18, font.split(label, Math.max(1, width - PADDING * 2)).size()
            * ManualTypography.TITLE_LINE_HEIGHT + PADDING * 2);
    }

    @Override
    protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX,
        final int mouseY, final float partialTick) {
        extractDefaultSprite(graphics);
        for (int line = 0; line < lines.size(); line++) {
            graphics.text(font, lines.get(line), getX() + PADDING,
                getY() + PADDING + line * ManualTypography.TITLE_LINE_HEIGHT,
                active ? 0xFFFFFFFF : 0xFFA0A0A0, true);
        }
    }
}
