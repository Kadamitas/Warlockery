package com.kadamitas.warlockery.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * {@code warlockery:familiar_cat}. A household familiar: it claims one place, walks a small ring
 * around it in daylight, stalks vermin inside that ring, and curls up there at night.
 *
 * <p>Everything shared with the Owl and the Toad is in {@link AnimalFamiliarMob} and
 * {@link AnimalFamiliarRuntime}. What is here is only identity: the species, a ground chassis. It has no flight, no perch, no broom aura, no courier, no pond, no shelter, no
 * herb landmark and no hop.</p>
 */
public final class FamiliarCatEntity extends AnimalFamiliarMob {

    public FamiliarCatEntity(final EntityType<? extends Zombie> type, final Level level) {
        super(type, level, AnimalFamiliarSpecies.CAT);
    }

    @Override
    public AnimalFamiliarSpecies species() {
        return AnimalFamiliarSpecies.CAT;
    }
}
