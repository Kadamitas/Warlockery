package com.kadamitas.warlockery.entity;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
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
 * The veil-drawn apparition, summoned at night by the preserved {@code summon_spectre} rite and by
 * the brazier. It keeps its registry id, displayed name, hostile monster category, 0.65 x 1.8
 * dimensions, Vex attribute baseline, three XP, Vex-family sounds, spectral dust drop, spectral and
 * reagent tags, Sentinel and Ghost Walking fetish bindings, its role in {@code bind_death} and its
 * spawn egg exactly, and adds one species motive owned by {@link SpectreRuntime}: a finite
 * telegraphed haunting that delivers Darkness and Weakness once to one appointed witness.
 *
 * <p>It never strikes, never deals damage, never samples or answers a player's motion, never walks,
 * never binds to an owner and never refreshes or spreads a dread. That is what keeps it a different
 * being from the Echo Shade that shares its plan.</p>
 *
 * <p>Deliberately not a Vex, {@link SpiritMob}, {@link SpectralEntity}, {@link ArcaneMob},
 * {@link WingedArcaneMob}, familiar or caster. It stays a {@link Monster} so the shipped hostile
 * identity, peaceful-difficulty despawn and player-rest prevention are unchanged.</p>
 */
public final class SpectreEntity extends Monster implements ArcaneCreature {
    static final String STATE_KEY = "WarlockerySpectre";

    private final ApparitionEpisodeRuntime.Counters apparitionCounters =
        new ApparitionEpisodeRuntime.Counters();
    private final SpectreRuntime.Counters counters = new SpectreRuntime.Counters();
    private final SpectreRuntime.TransientState scratch = new SpectreRuntime.TransientState();
    private SpectreState state = SpectreState.empty();

    public SpectreEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.xpReward = 3;
        setNoGravity(true);
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.SPECTRE;
    }

    public SpectreState spectreState() {
        return state;
    }

    public void setSpectreState(final SpectreState updated) {
        state = updated == null ? SpectreState.empty() : updated;
    }

    public SpectreRuntime.Counters spectreCounters() {
        return counters;
    }

    public ApparitionEpisodeRuntime.Counters apparitionCounters() {
        return apparitionCounters;
    }

    public SpectreRuntime.TransientState spectreTransient() {
        return scratch;
    }

    /**
     * LOOK only. Movement authority belongs exclusively to {@link SpectreRuntime}, and no target
     * goal is ever registered, so no goal can appoint a witness or start an attack.
     */
    @Override
    protected void registerGoals() {
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
    protected PathNavigation createNavigation(final Level level) {
        final FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        SpectreRuntime.tick(this, level);
    }

    /** A Spectre never attacks. No phase, no witness and no provocation may change that. */
    @Override
    public boolean canAttack(final LivingEntity target) {
        return SpectreRules.canAttack();
    }

    // ---------------------------------------------------------------- preserved identity

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
        // The registry-owned Vex attribute baseline is exact; the generic Mob random follow-range
        // spawn bonus would make it nondeterministic.
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
            .map(tag -> SpectreState.read(tag, dimension))
            .orElse(SpectreState.empty());
        scratch.resetForLoad();
        normalizeEquipment();
        setNoGravity(true);
    }
}
