package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.MimicryRules.Species;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Entity.RemovalReason;
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
 * The one body shared by all four mimics.
 *
 * <p>This class exists for the same reason {@link MimicryRules} and {@link MimicryRuntime} do. Four
 * bodies would each need the same LOOK-only goal set, the same empty target selector, the same
 * single runtime dispatch, the same effective-positive-damage attribution, the same adult empty
 * lifecycle normalisation, the same spawn-bonus strip, the same daylight neutrality, the same
 * phase-dependent rest prevention and the same versioned save and load. That is roughly fifteen
 * members, and copying them four times is precisely how one defect ships four times inside one
 * package. A subclass supplies its species, its kind and its sound set, and nothing else.</p>
 *
 * <p>Deliberately not a {@code Zombie}, not an {@link ArcaneMob}, not a {@link SpiritMob}, not a
 * {@link WingedArcaneMob} and not ageable. It stays a {@link Monster} so the shipped hostile
 * identity, Peaceful-difficulty despawn and monster category are unchanged.</p>
 */
public abstract class AbstractMimicEntity extends Monster
    implements ArcaneCreature, MimicryRuntime.MimicBody {

    private static final String REFLECTED_TARGET_KEY = "WarlockeryReflectedTarget";

    /** Every mimic stores its two bounded counters beneath this one key. */
    public static final String STATE_KEY = "WarlockeryMimicry";

    private final MimicryRuntime.Core core;

    protected AbstractMimicEntity(final EntityType<? extends Monster> type, final Level level, final Species species) {
        super(type, level);
        this.core = new MimicryRuntime.Core(species);
    }

    @Override
    public final Mob body() {
        return this;
    }

    @Override
    public final MimicryRuntime.Core mimicCore() {
        return core;
    }

    @Override
    public final java.util.Optional<java.util.UUID> reflectedTargetIdentity() {
        final String encoded = getPersistentData().getStringOr(REFLECTED_TARGET_KEY, "");
        if (encoded.isEmpty()) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(java.util.UUID.fromString(encoded));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }

    /**
     * Derived from the kind rather than declared a second time, so a body cannot report one species
     * to the registry and another to the runtime.
     */
    @Override
    public final MimicryRules.Species mimicSpecies() {
        return MimicryRules.speciesOf(creatureKind()).orElseThrow();
    }

    // ---------------------------------------------------------------- goals

    /**
     * LOOK and float only. Movement authority belongs exclusively to {@link MimicryRuntime}, and no
     * target goal is ever registered, so no goal can appoint a victim or begin an attack. The whole
     * inherited vanilla zombie goal and target set, including door breaking, villager, golem and
     * turtle targeting, village travel and reinforcement, is replaced rather than suppressed.
     */
    @Override
    protected final void registerGoals() {
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

    /** Fixture seam. A mimic that grew a target goal would be a different creature. */
    public final int operationalTargetGoalCount() {
        return targetSelector.getAvailableGoals().size();
    }

    /** Fixture seam. Proves the movement goals really are LOOK only. */
    public final List<String> operationalGoalNames() {
        return goalSelector.getAvailableGoals().stream()
            .map(goal -> goal.getGoal().getClass().getSimpleName())
            .toList();
    }

    // ---------------------------------------------------------------- the one tick seam

    @Override
    protected final void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        MimicryRuntime.tick(this, level);
    }

    @Override
    public final void remove(final RemovalReason reason) {
        if (!isRemoved()) {
            MimicryRuntime.cancel(this);
        }
        super.remove(reason);
    }

    /**
     * Only an effective positive loss mints an attribution. A truthy call that removed no health
     * and no absorption is not an accepted-damage event, and only a causing {@link LivingEntity}
     * may be attributed at all.
     */
    @Override
    public final boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final float before = getHealth() + getAbsorptionAmount();
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && getHealth() + getAbsorptionAmount() < before
            && source.getEntity() instanceof LivingEntity attacker) {
            MimicryRuntime.onAcceptedDamage(this, attacker);
        }
        return hurt;
    }

    /** A quiet mimic in its routine or spent phase does not block the night. */
    @Override
    public final boolean isPreventingPlayerRest(final ServerLevel level, final Player player) {
        final Species species = mimicSpecies();
        final MimicryRules.Phase phase = core.scratch().phase();
        return phase != species.routine() && phase != species.spent()
            && super.isPreventingPlayerRest(level, player);
    }

    /**
     * Daylight neutrality is structural rather than an override: a mimic is a {@link Monster} and
     * not a {@code Zombie}, so no sunlight combustion path exists to suppress. The previous bodies
     * needed {@code ArcaneMob.isSunSensitive()} returning false only because they inherited one.
     */

    // ---------------------------------------------------------------- preserved identity

    /** The species sound set. The only per-kind member other than the kind and the species. */
    protected abstract SoundEvent ambientSound();

    @Override
    protected final SoundEvent getAmbientSound() {
        return ambientSound();
    }

    @Override
    protected final SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected final SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    @Override
    protected final void playStepSound(final BlockPos pos, final BlockState blockState) {
        playSound(SoundEvents.ZOMBIE_STEP, 0.15F, 1.0F);
    }

    @Override
    protected final void populateDefaultEquipmentSlots(
        final RandomSource random,
        final DifficultyInstance difficulty
    ) {
    }

    @Override
    public final @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason reason,
        final @Nullable SpawnGroupData groupData
    ) {
        final SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
        normalizeLifecycle();
        // The registry-owned attribute baseline is exact. The generic Mob random follow-range spawn
        // bonus would make every exact-attribute assertion nondeterministic.
        final AttributeInstance followRange = getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);
        }
        setDeltaMovement(Vec3.ZERO);
        return result;
    }

    /**
     * The adult empty lifecycle. Every equipment slot is cleared without spawning a replacement
     * drop, so legacy copied player gear on an existing saved mimic disappears with no loot change
     * and no item entity created. Every drop chance on those slots was already zero.
     */
    public final void normalizeLifecycle() {
        setCanPickUpLoot(false);
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            if (!getItemBySlot(slot).isEmpty()) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        setTarget(null);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected final void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        MimicryRuntime.writeSaveData(this, output, STATE_KEY);
    }

    @Override
    protected final void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        MimicryRuntime.readSaveData(this, input, STATE_KEY);
        normalizeLifecycle();
    }
}

