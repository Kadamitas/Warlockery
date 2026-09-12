package com.kadamitas.warlockery.ritual.hex;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Set;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.entity.LivingEntity;

public final class SinkingFluidContact {
    private SinkingFluidContact() {
    }

    public static double height(final LivingEntity target) {
        final var contact = new EntityFluidInteraction(Set.of(WarlockeryTags.Fluids.SINKING_FLUIDS));
        contact.update(target, false);
        return contact.getFluidHeight(WarlockeryTags.Fluids.SINKING_FLUIDS);
    }
}
