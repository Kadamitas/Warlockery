package com.kadamitas.warlockery.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class BroomItem extends Item implements GlyphClearingTool {
    public BroomItem(final Properties properties) {
        super(properties);
    }

    @Override
    public int glyphRadius() {
        return 0;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        return clearGlyphs(context);
    }
}
