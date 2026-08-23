package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.PoltergeistRules.Phase;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The summoned household disturbance. It is acquired only by the preserved {@code summon_poltergeist}
 * rite, keeps its registry id, displayed name, category, dimensions, Vex attribute baseline, three
 * XP, empty loot, spawn egg and Death-binding membership exactly, and adds one species motive: a
 * finite room-scale disturbance episode owned by {@link PoltergeistRuntime}.
 *
 * <p>Deliberately not an {@code Enemy}, Vex, Monster, {@link SpiritMob}, {@link SpectralEntity},
 * familiar, or caster. It never binds, never gains an owner, never applies an aura, never applies
 * fear, never imitates and never acquires a combat target: the only damage it can cause is the one
 * separately attributed hit of the prop it throws. That is what keeps it a different being from the
 * Spirit, Spectre, Echo Shade and Banshee neighbours it used to share a class with.</p>
 */
public final class PoltergeistEntity extends PathfinderMob implements ArcaneCreature {
    static final String STATE_KEY = "WarlockeryPoltergeist";
    private static final EntityDataAccessor<Byte> DATA_PRESENTATION_PHASE =
        SynchedEntityData.defineId(PoltergeistEntity.class, EntityDataSerializers.BYTE);

    private final PoltergeistRuntime.Counters counters = new PoltergeistRuntime.Counters();
    private final PoltergeistRuntime.TransientState scratch = new PoltergeistRuntime.TransientState();
    private PoltergeistState state = PoltergeistState.empty();

    public PoltergeistEntity(final EntityType<? extends PathfinderMob> type, final Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.xpReward = 3;
        setNoGravity(true);
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.POLTERGEIST;
    }

    public PoltergeistState poltergeistState() {
        return state;
    }

    public void setPoltergeistState(final PoltergeistState updated) {
        state = updated == null ? PoltergeistState.empty() : updated;
        syncPresentation(state.phase());
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_PRESENTATION_PHASE, (byte) Phase.LURK.ordinal());
    }

    public Phase presentationPhase() {
        final int stored = entityData.get(DATA_PRESENTATION_PHASE);
        final Phase[] phases = Phase.values();
        return stored >= 0 && stored < phases.length ? phases[stored] : Phase.LURK;
    }

    private void syncPresentation(final Phase phase) {
        final byte encoded = (byte) phase.ordinal();
        if (entityData.get(DATA_PRESENTATION_PHASE) != encoded) {
            entityData.set(DATA_PRESENTATION_PHASE, encoded);
        }
    }

    public PoltergeistRuntime.Counters poltergeistCounters() {
        return counters;
    }

    public PoltergeistRuntime.TransientState poltergeistTransient() {
        return scratch;
    }

    /**
     * LOOK only. Movement authority belongs exclusively to {@link PoltergeistRuntime} and no target
     * goal is ever registered, so the generic Vex charge and random flight that used to contest the
     * disturbance can never reappear through the goal selector.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new LookOnlyRandomLookGoal(this));
    }

    /**
     * Vanilla {@link RandomLookAroundGoal} declares MOVE and LOOK; this variant redeclares LOOK only
     * so the goal selector can never contest the runtime's movement authority.
     */
    private static final class LookOnlyRandomLookGoal extends RandomLookAroundGoal {
        private LookOnlyRandomLookGoal(final Mob mob) {
            super(mob);
            setFlags(EnumSet.of(Flag.LOOK));
        }
    }

    public int operationalTargetGoalCount() {
        return targetSelector.getAvailableGoals().size();
    }

    public List<String> operationalGoalNames() {
        return goalSelector.getAvailableGoals().stream()
            .map(goal -> goal.getGoal().getClass().getSimpleName())
            .toList();
    }

    @Override
    protected PathNavigation createNavigation(final Level level) {
        final FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    /** A Poltergeist never acquires a combat target: its only damage is the attributed prop hit. */
    @Override
    public boolean canAttack(final LivingEntity target) {
        return PoltergeistRules.canAttack();
    }

    @Override
    protected void checkFallDamage(
        final double ya,
        final boolean onGround,
        final BlockState onState,
        final BlockPos pos
    ) {
    }

    @Override
    protected void playStepSound(final BlockPos pos, final BlockState blockState) {
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        PoltergeistRuntime.tick(this, level);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && amount > 0.0F) {
            PoltergeistRuntime.onAcceptedDamage(this, level, source, amount);
        }
        return hurt;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    protected void populateDefaultEquipmentSlots(
        final RandomSource random,
        final DifficultyInstance difficulty
    ) {
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason reason,
        final @Nullable SpawnGroupData groupData
    ) {
        final SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
        normalizeEquipment();
        // The registry-owned Vex attribute baseline is exact; the generic Mob random follow-range
        // spawn bonus would make it nondeterministic.
        final AttributeInstance followRange = getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);
        }
        setDeltaMovement(Vec3.ZERO);
        return result;
    }

    private void normalizeEquipment() {
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            if (!getItemBySlot(slot).isEmpty()) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, state.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        state = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(PoltergeistState::read)
            .orElse(PoltergeistState.empty());
        syncPresentation(state.phase());
        scratch.resetForLoad();
        normalizeEquipment();
        setNoGravity(true);
    }
}
