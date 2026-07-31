package com.kadamitas.warlockery.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

final class ManualTypography {
    static final FontDescription.Resource FONT = new FontDescription.Resource(
        Identifier.withDefaultNamespace("uniform")
    );
    static final float TITLE_SCALE = 0.86F;
    static final float BODY_SCALE = 0.76F;
    static final int TITLE_LINE_HEIGHT = 9;
    static final int BODY_LINE_HEIGHT = 8;

    private ManualTypography() {
    }

    static MutableComponent readable(final Component text) {
        return text.copy().withStyle(style -> style.withFont(FONT).withBold(false));
    }

    static MutableComponent readable(final Component text, final int color) {
        return readable(text).withColor(color);
    }

    static int wrappingWidth(final int renderedWidth, final float scale) {
        return Math.max(1, (int) Math.floor(renderedWidth / scale));
    }
}
