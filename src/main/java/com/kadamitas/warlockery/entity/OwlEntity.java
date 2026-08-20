package com.kadamitas.warlockery.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * {@code warlockery:owl}. A nocturnal aerial familiar: it holds a supported perch, watches from it,
 * and drops once onto tagged prey below it.
 *
 * <p>Identity only. The flight chassis is the whole of what this class adds over
 * {@link AnimalFamiliarMob}: a flying move control, flying navigation and no gravity, so an owl
 * travels through the air rather than walking. The species requires
 * {@link Attributes#FLYING_SPEED} to be declared on its attribute supplier, which is a coordinator
 * deferred edit in {@code ModEntities}; without it the entity cannot be constructed at all and the
 * failure is "Can't find attribute minecraft:flying_speed" before any fixture dispatches.</p>
 *
 * <p>It is not the Cat: no household claim, no patrol ring, no Luck. It is not the Toad: no
 * shelter, no water requirement, no herb landmark, no hop. It keeps its existing broom-conditioned
 * aura and its existing owner-commanded waystone delivery, both unchanged.</p>
 */
public final class OwlEntity extends AnimalFamiliarMob {

    /** Declared on the attribute supplier and asserted live; a flyer without it cannot spawn. */
    public static final double FLYING_SPEED = 0.6;

    public OwlEntity(final EntityType<? extends Zombie> type, final Level level) {
        super(type, level, AnimalFamiliarSpecies.OWL);
        moveControl = new FlyingMoveControl<>(this, 20, true);
        // The flight navigation is installed AFTER super, not by overriding createNavigation.
        // Zombie's own constructor builds a BreakDoorGoal, and every door goal refuses at
        // construction any body without ground navigation, so an owl that declared flight one
        // frame earlier would throw inside its own constructor before it ever existed.
        final FlyingPathNavigation flight = new FlyingPathNavigation(this, level);
        flight.setCanOpenDoors(false);
        flight.setCanFloat(true);
        navigation = flight;
        setNoGravity(true);
    }

    @Override
    public AnimalFamiliarSpecies species() {
        return AnimalFamiliarSpecies.OWL;
    }

    @Override
    protected void checkFallDamage(
        final double fallDistance,
        final boolean onGround,
        final net.minecraft.world.level.block.state.BlockState state,
        final net.minecraft.core.BlockPos position
    ) {
        // A no-gravity flyer accumulates no fall distance to convert into damage.
    }
}
