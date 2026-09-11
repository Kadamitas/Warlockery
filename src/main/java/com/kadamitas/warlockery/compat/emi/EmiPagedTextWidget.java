package com.kadamitas.warlockery.compat.emi;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class EmiPagedTextWidget extends Widget {
    private static final int LINE_HEIGHT = 11;
    private static final int FOOTER_HEIGHT = 18;
    private final Bounds bounds;
    private final List<FormattedCharSequence> lines;
    private final EmiTextPages pages;

    EmiPagedTextWidget(List<Component> paragraphs, int x, int y, int width, int height) {
        bounds = new Bounds(x, y, width, height);
        var font = Minecraft.getInstance().font;
        lines = paragraphs.stream().flatMap(paragraph -> java.util.stream.Stream.concat(
            font.split(paragraph, Math.max(1, width - 4)).stream(), java.util.stream.Stream.of(FormattedCharSequence.EMPTY))).toList();
        pages = new EmiTextPages(lines.size(), Math.max(1, (height - FOOTER_HEIGHT) / LINE_HEIGHT));
    }

    @Override public Bounds getBounds() { return bounds; }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var font = Minecraft.getInstance().font;
        for (int line = pages.start(); line < pages.end(); line++) {
            graphics.text(font, lines.get(line), bounds.x() + 2, bounds.y() + (line - pages.start()) * LINE_HEIGHT, 0xFF302A32, false);
        }
        if (pages.pageCount() > 1) {
            int y = bounds.y() + bounds.height() - FOOTER_HEIGHT + 2;
            graphics.fill(bounds.x(), y - 2, bounds.x() + 18, y + 12, 0xFFD9CFDC);
            graphics.fill(bounds.x() + bounds.width() - 18, y - 2, bounds.x() + bounds.width(), y + 12, 0xFFD9CFDC);
            graphics.text(font, Component.literal("<"), bounds.x() + 6, y, 0xFF302A32, false);
            graphics.text(font, Component.literal(">"), bounds.x() + bounds.width() - 12, y, 0xFF302A32, false);
            var counter = Component.literal((pages.page() + 1) + " / " + pages.pageCount());
            graphics.text(font, counter, bounds.x() + (bounds.width() - font.width(counter)) / 2, y, 0xFF302A32, false);
        }
    }

    @Override public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || pages.pageCount() < 2 || !bounds.contains(mouseX, mouseY)
            || mouseY < bounds.y() + bounds.height() - FOOTER_HEIGHT) return false;
        if (mouseX < bounds.x() + 20) { pages.move(-1); return true; }
        if (mouseX >= bounds.x() + bounds.width() - 20) { pages.move(1); return true; }
        return false;
    }
}
