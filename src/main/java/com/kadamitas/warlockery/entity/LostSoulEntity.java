package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.LostSoulRules.Phase;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The called human-dead-coded shade. It is acquired only by the preserved {@code summon_lost_soul}
 * rite, keeps its registry id, displayed name, category, dimensions, Vex attribute baseline, three
 * XP, empty loot and spawn egg exactly, and adds one species motive: a finite memorial episode
 * owned by {@link LostSoulRuntime}.
 *
 * <p>It never attacks, never targets, never copies an owner target, never warns, never defends,
 * never collects souls, never consumes corpses, and never completes as a reward. That is what
 * keeps it a different being from the Spirit that shares its plan.</p>
 */
public final class LostSoulEntity extends SpectralEntity {
    static final String STATE_KEY = "WarlockeryLostSoul";
    private static final EntityDataAccessor<Byte> DATA_PRESENTATION_PHASE =
        SynchedEntityData.defineId(LostSoulEntity.class, EntityDataSerializers.BYTE);

    private final LostSoulRuntime.Counters counters = new LostSoulRuntime.Counters();
    private final LostSoulRuntime.TransientState scratch = new LostSoulRuntime.TransientState();
    private LostSoulState state = LostSoulState.empty();

    public LostSoulEntity(final EntityType<? extends PathfinderMob> type, final Level level) {
        super(type, level, CreatureKind.LOST_SOUL);
    }

    public LostSoulState lostSoulState() {
        return state;
    }

    public void setLostSoulState(final LostSoulState updated) {
        state = updated == null ? LostSoulState.empty() : updated;
        syncPresentation(state.phase());
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_PRESENTATION_PHASE, (byte) Phase.WANDER.ordinal());
    }

    public Phase presentationPhase() {
        final int stored = entityData.get(DATA_PRESENTATION_PHASE);
        final Phase[] phases = Phase.values();
        return stored >= 0 && stored < phases.length ? phases[stored] : Phase.WANDER;
    }

    private void syncPresentation(final Phase phase) {
        final byte encoded = (byte) phase.ordinal();
        if (entityData.get(DATA_PRESENTATION_PHASE) != encoded) {
            entityData.set(DATA_PRESENTATION_PHASE, encoded);
        }
    }

    public LostSoulRuntime.Counters lostSoulCounters() {
        return counters;
    }

    public LostSoulRuntime.TransientState lostSoulTransient() {
        return scratch;
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        LostSoulRuntime.tick(this, level);
    }

    /** A bound Lost Soul attends its owner. It never fights for one, so it can never attack. */
    @Override
    public boolean canAttack(final LivingEntity target) {
        return LostSoulRules.canAttack(
            CreatureBehaviorState.owner(this).isPresent(),
            target != null
        );
    }

    @Override
    protected void onBindingCommitted(final ServerLevel level, final UUID owner) {
        LostSoulRuntime.onBindingCommitted(this, level, owner);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, state.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final String dimension = level().dimension().identifier().toString();
        state = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> LostSoulState.read(tag, dimension))
            .orElse(LostSoulState.empty());
        syncPresentation(state.phase());
        scratch.resetForLoad();
        normalizeEquipment();
        setNoGravity(true);
    }
}
