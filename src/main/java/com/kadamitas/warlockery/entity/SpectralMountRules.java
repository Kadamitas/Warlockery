package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class SpectralMountRules {
    private SpectralMountRules() {
    }

    public static boolean isMount(final CreatureKind kind) {
        return kind == CreatureKind.PALE_STEED || kind == CreatureKind.NIGHTMARE;
    }

    public static boolean canControl(
        final CreatureKind kind,
        final Optional<UUID> owner,
        final UUID rider
    ) {
        return isMount(kind) && owner.filter(rider::equals).isPresent();
    }

    public static Vec3 input(final float sideways, final float forward) {
        return new Vec3(sideways * 0.5F, 0.0, forward <= 0.0F ? forward * 0.25F : forward);
    }

    public static float speed(final CreatureKind kind, final double baseSpeed) {
        final double multiplier = kind == CreatureKind.NIGHTMARE ? 1.35 : 1.15;
        return (float) (Math.max(0.0, baseSpeed) * multiplier);
    }
}
