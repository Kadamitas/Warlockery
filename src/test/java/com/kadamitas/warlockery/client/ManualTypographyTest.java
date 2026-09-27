package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void signsAndPortentsTitleFitsTheCurrentTwoLineNavigationHeading() {
        final ManualLayout compact = ManualLayout.calculate(320, 240);
        final int renderedWidth = compact.navigationWidth() - compact.textInset() * 2;
        final int wrappingWidth = ManualTypography.wrappingWidth(renderedWidth, ManualTypography.TITLE_SCALE);

        assertTrue("Signs &".length() * 9 <= wrappingWidth);
        assertTrue("Portents".length() * 9 <= wrappingWidth);
        assertTrue(2 * ManualTypography.TITLE_LINE_HEIGHT <= compact.chapterControls().getFirst().y() - compact.top() - 16);
    }

}
