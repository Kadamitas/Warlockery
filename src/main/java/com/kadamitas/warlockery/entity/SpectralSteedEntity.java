package com.kadamitas.warlockery.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The dedicated body shared by Pale Steed and Nightmare.
 *
 * <p>It is deliberately thin, and deliberately a subclass rather than a replacement. Acquisition,
 * ownership and the passenger seam all stay where they already are: binding and the mount entry
 * remain {@code CreatureBehaviorRuntime.interactMount}, reached through {@code ArcaneMob.mobInteract},
 * and the owner-only passenger and control checks remain {@code ArcaneMob.canAddPassenger} and
 * {@code ArcaneMob.getControllingPassenger}, both asking {@link SpectralMountRules}. This class adds
 * the parts that seam has no place for: durable gait, fatigue, bond and rest state, a runtime that
 * owns the tick, a rider-facing speed and steering scale derived from the band, and a dismount that
 * looks before it drops somebody.</p>
 *
 * <p>The two ridden-input overrides both call {@code super} and scale the result, so the shared
 * {@code ArcaneMob} seam and {@link SpectralMountRules#input} and {@link SpectralMountRules#speed}
 * stay on the live path rather than being superseded into dead code.</p>
 *
 * <p>Goals are untouched on purpose. An unbound Nightmare is the Spirit World and Dream Weaver's
 * hostile referent, and its inherited hostility is part of that frozen contract; a ridden steed is
 * kept out of combat by the runtime clearing its target rather than by removing a goal that an
 * unbound one still needs.</p>
 */
public class SpectralSteedEntity extends ArcaneMob {
    private static final String STATE_KEY = "WarlockerySpectralSteed";

    private SpectralSteedState steedState = SpectralSteedState.empty();

    public SpectralSteedEntity(
        final EntityType<? extends Zombie> type,
        final Level level,
        final CreatureKind kind
    ) {
        super(type, level, kind);
        if (!SpectralSteedRules.isSteed(kind)) {
            throw new IllegalArgumentException("SpectralSteedEntity only bodies the steeds: " + kind);
        }
    }

    public SpectralSteedState steedState() {
        return steedState;
    }

    public void setSteedState(final SpectralSteedState state) {
        steedState = state == null ? SpectralSteedState.empty() : state;
    }

    /**
     * The generic tactical and ambient layers stand aside while the owner is steering, because both
     * of them issue navigation and would fight the rider for it. Unridden, both still run, so hay
     * rest and the shared pack doctrine keep working exactly as before.
     */
    @Override
    protected void tickSpecializedActivity(final ServerLevel level) {
        if (!SpectralSteedRuntime.carryingOwner(this)) {
            super.tickSpecializedActivity(level);
        }
        SpectralSteedRuntime.tick(this, level);
    }

    /** The shared mount speed, scaled by the band this steed has actually reached. */
    @Override
    protected float getRiddenSpeed(final Player controller) {
        return super.getRiddenSpeed(controller)
            * SpectralSteedRules.gaitSpeedFactor(steedState.gait());
    }

    /** A balking steed accepts no steering, and the rider stays on it throughout. */
    @Override
    protected Vec3 getRiddenInput(final Player controller, final Vec3 selfInput) {
        return super.getRiddenInput(controller, selfInput)
            .scale(SpectralSteedRules.riddenInputScale(steedState.balking()));
    }

    /** Mounting starts a ride episode: transient bands and per-ride accumulators reset. */
    @Override
    protected void addPassenger(final Entity passenger) {
        super.addPassenger(passenger);
        setSteedState(steedState.episodeStart());
    }

    /** Dismounting ends it, leaving every durable fact in place. */
    @Override
    protected void removePassenger(final Entity passenger) {
        super.removePassenger(passenger);
        setSteedState(steedState.episodeEnd());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(final LivingEntity passenger) {
        return level() instanceof ServerLevel level
            ? SpectralSteedRuntime.dismountLocation(this, level)
            : super.getDismountLocationForPassenger(passenger);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, steedState.write(creatureKind()));
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        setSteedState(input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> SpectralSteedState.read(tag, creatureKind()))
            .orElseGet(SpectralSteedState::empty));
    }
}


