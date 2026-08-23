package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.EchoShadeRules.Phase;
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
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
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
 * The mirror's hostile answer, released at night by the preserved {@code summon_reflection} rite
 * from one mirror and one refined evil. It keeps its registry id, displayed name, hostile monster
 * category, 0.6 x 1.95 dimensions, ground attribute baseline, five XP, Zombie-family sounds, empty
 * loot and spawn egg exactly, and adds one species motive owned by {@link EchoShadeRuntime}: a
 * finite echo that samples one player's motion, answers it from a mirrored offset, and spends a
 * single ordinary attributed melee attempt.
 *
 * <p>It never flies, never binds to an owner, never applies a status effect, never petitions at a
 * memorial and never copies anything a player carries, wears, knows or is. That is what keeps it a
 * different being from the Spectre that shares its plan.</p>
 *
 * <p>Deliberately not a Zombie, {@link ArcaneMob}, {@link SpiritMob}, {@link SpectralEntity},
 * {@link WingedArcaneMob}, familiar or caster. It stays a {@link Monster} so the shipped hostile
 * identity, peaceful-difficulty despawn and player-rest prevention are unchanged.</p>
 */
public final class EchoShadeEntity extends Monster implements ArcaneCreature {
    static final String STATE_KEY = "WarlockeryEchoShade";
    private static final EntityDataAccessor<Byte> DATA_PRESENTATION_PHASE =
        SynchedEntityData.defineId(EchoShadeEntity.class, EntityDataSerializers.BYTE);

    private final ApparitionEpisodeRuntime.Counters apparitionCounters =
        new ApparitionEpisodeRuntime.Counters();
    private final EchoShadeRuntime.Counters counters = new EchoShadeRuntime.Counters();
    private final EchoShadeRuntime.TransientState scratch = new EchoShadeRuntime.TransientState();
    private EchoShadeState state = EchoShadeState.empty();
    private boolean striking;

    public EchoShadeEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level);
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.ECHO_SHADE;
    }

    public EchoShadeState echoShadeState() {
        return state;
    }

    public void setEchoShadeState(final EchoShadeState updated) {
        state = updated == null ? EchoShadeState.empty() : updated;
        syncPresentation(state.phase());
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_PRESENTATION_PHASE, (byte) Phase.WATCH.ordinal());
    }

    public Phase presentationPhase() {
        final int stored = entityData.get(DATA_PRESENTATION_PHASE);
        final Phase[] phases = Phase.values();
        return stored >= 0 && stored < phases.length ? phases[stored] : Phase.WATCH;
    }

    private void syncPresentation(final Phase phase) {
        final byte encoded = (byte) phase.ordinal();
        if (entityData.get(DATA_PRESENTATION_PHASE) != encoded) {
            entityData.set(DATA_PRESENTATION_PHASE, encoded);
        }
    }

    public EchoShadeRuntime.Counters echoShadeCounters() {
        return counters;
    }

    public ApparitionEpisodeRuntime.Counters apparitionCounters() {
        return apparitionCounters;
    }

    public EchoShadeRuntime.TransientState echoShadeTransient() {
        return scratch;
    }

    /** Set only around the single runtime-owned attempt, so no other path can open the gate. */
    void setStriking(final boolean value) {
        striking = value;
    }

    /**
     * LOOK and JUMP only. Movement authority belongs exclusively to {@link EchoShadeRuntime}, and
     * no target goal is ever registered, so no goal can appoint a mark or start an attack.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new LookOnlyRandomLookGoal(this));
    }

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
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        EchoShadeRuntime.tick(this, level);
    }

    /**
     * The complete attack contract: only inside the one open strike window opened by the runtime,
     * and only against the exact player currently marked. Nothing else may ever be attacked.
     */
    @Override
    public boolean canAttack(final LivingEntity target) {
        return target != null
            && EchoShadeRules.canAttack(striking, EchoShadeRuntime.isMark(this, target.getUUID()));
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && amount > 0.0F) {
            EchoShadeRuntime.onAcceptedDamage(this);
        }
        return hurt;
    }

    // ---------------------------------------------------------------- preserved identity

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    @Override
    protected void playStepSound(final BlockPos pos, final BlockState blockState) {
        playSound(SoundEvents.ZOMBIE_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void populateDefaultEquipmentSlots(
        final net.minecraft.util.RandomSource random,
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
        // The registry-owned ground attribute baseline is exact; the generic Mob random
        // follow-range spawn bonus would make it nondeterministic.
        final AttributeInstance followRange = getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);
        }
        setDeltaMovement(Vec3.ZERO);
        return result;
    }

    void normalizeEquipment() {
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            if (!getItemBySlot(slot).isEmpty()) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    // ---------------------------------------------------------------- persistence

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
            .map(tag -> EchoShadeState.read(tag, dimension))
            .orElse(EchoShadeState.empty());
        syncPresentation(state.phase());
        scratch.resetForLoad();
        striking = false;
        normalizeEquipment();
    }
}
