package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class ManualTypographyTest {
    @Test
    void manualTextUsesTheReadableVanillaUniformFont() {
        final var styled = ManualTypography.readable(Component.literal("Readable"), 0x3A271F);

        assertEquals(Identifier.withDefaultNamespace("uniform"), ManualTypography.FONT.id());
        assertEquals(ManualTypography.FONT, styled.getStyle().getFont());
        assertEquals(0x3A271F, styled.getStyle().getColor().getValue());
        assertFalse(styled.getStyle().isBold());
    }

    @Test
    void manualRenderingDisablesDarkDropShadows() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/client/ManualScreen.java"
        ));

        assertTrue(source.contains("graphics.text(font, text, x, y, -1, false)"));
        assertFalse(source.contains("graphics.textRenderer()"));
    }
}
