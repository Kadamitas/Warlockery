package com.kadamitas.warlockery.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * {@code warlockery:toad}. A pond-edge garden familiar: it shelters by day, and on a wet day or at
 * night it hops out to one tagged insect beside a retained herb landmark.
 *
 * <p>Identity only. It shares the ground chassis with the Cat but not its schedule, its home
 * predicate, its prey tag or its envelope: a toad's home needs still water within four blocks and
 * something overhead, and its forage is anchored to a herb landmark rather than to a territory.
 * It is the only one of the three whose waking window responds to weather.</p>
 *
 * <p>It has no owl flight, perch, pounce or courier; no cat household, patrol or Luck; and
 * emphatically no poison, no Rain-of-Toads role and no terrain effect. Those belong to the brews
 * and hexes that are outside this family entirely.</p>
 */
public final class ToadEntity extends AnimalFamiliarMob {

    public ToadEntity(final EntityType<? extends Zombie> type, final Level level) {
        super(type, level, AnimalFamiliarSpecies.TOAD);
    }

    @Override
    public AnimalFamiliarSpecies species() {
        return AnimalFamiliarSpecies.TOAD;
    }
}
