package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

}
