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
 * The living shadow-mark. It keeps its registry id, displayed name, hostile monster category,
 * 0.7 x 0.7 dimensions, Vex attribute baseline, three XP, Vex-family sounds, empty loot table,
 * spectral tag membership, spawn egg, client model and complete absence of any ritual, brazier,
 * fetish, binding, reagent, recipe, natural-spawn or progression route exactly, and adds one
 * species motive owned by {@link UmbralSigilRuntime}: it inscribes a transient three-vertex seal in
 * the air around one appointed player and closes it with a single ordinary attributed attempt.
 *
 * <p>It never imitates anything a player does, never applies a status effect, never lifts or throws
 * a prop, never binds to an owner, never petitions at a memorial, never appoints a death, never
 * edits a block and never places or removes a Sanctity ward. The similarly named {@code DREAD_SIGIL}
 * symbol spell is a name collision and nothing more. That is what keeps it a different being from
 * every one of its spectral neighbours.</p>
 *
 * <p>Deliberately not a Vex, {@link SpiritMob}, {@link SpectralEntity}, {@link ArcaneMob},
 * {@link WingedArcaneMob}, familiar or caster. It stays a {@link Monster} so the shipped hostile
 * identity, peaceful-difficulty despawn and player-rest prevention are unchanged, and it is no
 * longer noclipping, because the inherited Vex chassis passed through walls and the seal it now
 * draws is meant to be flown, not phased through.</p>
 */
public final class UmbralSigilEntity extends Monster implements ArcaneCreature {
    static final String STATE_KEY = "WarlockeryUmbralSigil";

    private final UmbralSigilRuntime.Counters counters = new UmbralSigilRuntime.Counters();
    private final UmbralSigilRuntime.TransientState scratch =
        new UmbralSigilRuntime.TransientState();
    private UmbralSigilState state = UmbralSigilState.empty();
    private boolean striking;

    public UmbralSigilEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.xpReward = 3;
        setNoGravity(true);
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.UMBRAL_SIGIL;
    }

    public UmbralSigilState sigilState() {
        return state;
    }

    public void setSigilState(final UmbralSigilState updated) {
        state = updated == null ? UmbralSigilState.empty() : updated;
    }

    public UmbralSigilRuntime.Counters sigilCounters() {
        return counters;
    }

    public UmbralSigilRuntime.TransientState sigilTransient() {
        return scratch;
    }

    /** Set only around the single runtime-owned attempt, so no other path can open the gate. */
    void setStriking(final boolean value) {
        striking = value;
    }

    /**
     * LOOK only. Movement authority belongs exclusively to {@link UmbralSigilRuntime}, and no
     * target goal is ever registered, so no goal can appoint a subject, charge, or start an attack.
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
        UmbralSigilRuntime.tick(this, level);
    }

    /**
     * The complete attack contract: only inside the one open strike window the runtime opens, and
     * only against the exact player currently being sealed. Nothing else may ever be attacked.
     */
    @Override
    public boolean canAttack(final LivingEntity target) {
        return striking
            && target != null
            && UmbralSigilRuntime.isSubject(this, target.getUUID());
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && amount > 0.0F) {
            UmbralSigilRuntime.onAcceptedDamage(this);
        }
        return hurt;
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
        state = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(UmbralSigilState::read)
            .orElse(UmbralSigilState.empty());
        scratch.resetForLoad();
        striking = false;
        normalizeEquipment();
        setNoGravity(true);
    }
}
