package com.kadamitas.warlockery.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

final class ManualTypography {
    static final FontDescription.Resource FONT = new FontDescription.Resource(
        Identifier.withDefaultNamespace("uniform")
    );

    private ManualTypography() {
    }

    static MutableComponent readable(final Component text) {
        return text.copy().withStyle(style -> style.withFont(FONT).withBold(false));
    }

    static MutableComponent readable(final Component text, final int color) {
        return readable(text).withColor(color);
    }
}
