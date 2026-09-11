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
    void manualTextUsesNativeSizeWithComfortableLineSpacing() {
        assertEquals(1.0F, ManualTypography.TITLE_SCALE);
        assertEquals(1.0F, ManualTypography.BODY_SCALE);
        assertTrue(ManualTypography.BODY_LINE_HEIGHT >= 11);
        assertTrue(ManualTypography.TITLE_LINE_HEIGHT >= 11);
        assertEquals(300, ManualTypography.wrappingWidth(300, ManualTypography.BODY_SCALE));
    }
    @Test
    void manualRenderingDisablesDarkDropShadows() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/kadamitas/warlockery/client/ManualScreen.java"
        ));

        assertTrue(source.contains("graphics.text(font, text, x, y, -1, false)"));
        assertTrue(source.contains("graphics.pose().scale(scale, scale)"));
        assertFalse(source.contains("graphics.textRenderer()"));
    }
}
