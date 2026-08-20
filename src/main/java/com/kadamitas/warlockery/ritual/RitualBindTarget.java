package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.entity.CreatureBehaviorTags;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public enum RitualBindTarget implements StringIdentified {
    FAMILIAR("familiar", CreatureBehaviorTags.EntityTypes.FAMILIARS),
    SPECTRAL("spectral", WarlockeryTags.EntityTypes.SPECTRAL);

    private static final EnumLookup<RitualBindTarget> LOOKUP = EnumLookup.create("bind target", values());

    private final String id;
    private final TagKey<EntityType<?>> candidates;

    RitualBindTarget(final String id, final TagKey<EntityType<?>> candidates) {
        this.id = id;
        this.candidates = candidates;
    }

    public String id() {
        return id;
    }

    public boolean matches(final LivingEntity entity) {
        return entity.typeHolder().is(candidates);
    }

    public static Optional<RitualBindTarget> find(final String id) {
        return LOOKUP.find(id);
    }

    public static List<String> registeredTargets() {
        return Arrays.stream(values()).map(RitualBindTarget::id).toList();
    }
}
