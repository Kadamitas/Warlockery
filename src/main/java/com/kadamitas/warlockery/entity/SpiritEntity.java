package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.SpiritRules.Phase;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The naturally present, nonhuman-unspecified local spirit. It keeps its registry id, displayed
 * name, passive creature category, natural spawning, dimensions, Vex attribute baseline, three XP,
 * empty loot and spawn egg exactly, and adds one species motive owned by {@link SpiritRuntime}:
 * a bounded wary radius that resolves into finite soul-light attendance while free, and one
 * warned, bounded, single-strike defence of its owner's recent direct attacker while bound.
 *
 * <p>It never petitions at a memorial, never settles into mourning, and never proactively targets
 * a player. That is what keeps it a different being from the Lost Soul that shares its plan.</p>
 */
public final class SpiritEntity extends SpectralEntity {
    static final String STATE_KEY = "WarlockerySpirit";
    private static final EntityDataAccessor<Byte> DATA_PRESENTATION_PHASE =
        SynchedEntityData.defineId(SpiritEntity.class, EntityDataSerializers.BYTE);

    private final SpiritRuntime.Counters counters = new SpiritRuntime.Counters();
    private final SpiritRuntime.TransientState scratch = new SpiritRuntime.TransientState();
    private SpiritState state = SpiritState.empty();

    public SpiritEntity(final EntityType<? extends PathfinderMob> type, final Level level) {
        super(type, level, CreatureKind.SPIRIT);
    }

    public SpiritState spiritState() {
        return state;
    }

    public void setSpiritState(final SpiritState updated) {
        state = updated == null ? SpiritState.empty() : updated;
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

    public SpiritRuntime.Counters spiritCounters() {
        return counters;
    }

    public SpiritRuntime.TransientState spiritTransient() {
        return scratch;
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        SpiritRuntime.tick(this, level);
    }

    /**
     * The complete attack contract: bound, inside an open defence window, and only against the
     * one accepted attacker recorded by the runtime. No free Spirit, and no bound Spirit outside
     * its defence window, may attack anything.
     */
    @Override
    public boolean canAttack(final LivingEntity target) {
        return target != null
            && SpiritRules.canAttack(
                CreatureBehaviorState.owner(this).isPresent(),
                state.phase() == Phase.DEFEND,
                SpiritRuntime.isAcceptedAttacker(this, target)
            );
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && amount > 0.0F) {
            SpiritRuntime.onAcceptedDamage(this, level);
        }
        return hurt;
    }

    @Override
    protected void onBindingCommitted(final ServerLevel level, final UUID owner) {
        SpiritRuntime.onBindingCommitted(this, level, owner);
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
            .map(tag -> SpiritState.read(tag, dimension))
            .orElse(SpiritState.empty());
        syncPresentation(state.phase());
        scratch.resetForLoad();
        normalizeEquipment();
        setNoGravity(true);
    }
}
