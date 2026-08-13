package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

public record AmbientActivityContext(
    Mob creature,
    ServerLevel level,
    CreatureKind kind,
    AmbientActivityProfile profile
) {
    public AmbientActivityContext {
        if (creature == null || level == null || kind == null || profile == null) {
            throw new IllegalArgumentException("Ambient activity contexts require a creature, level, kind, and profile");
        }
    }

    public long gameTime() {
        return level.getGameTime();
    }
}
