package com.kadamitas.warlockery.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

public interface GlyphClearingTool {
    int glyphRadius();

    default InteractionResult clearGlyphs(final UseOnContext context) {
        return BroomGlyphService.clear(context, glyphRadius());
    }
}
