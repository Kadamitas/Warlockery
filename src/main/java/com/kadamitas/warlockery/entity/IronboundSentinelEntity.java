package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.IronboundSentinelRules.Charge;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.Phase;
import com.kadamitas.warlockery.entity.IronboundSentinelRules.SocketAct;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
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
import org.jspecify.annotations.Nullable;

/**
 * Dedicated F36 Ironbound Sentinel: a charge-bound ward keeper on a plain ground {@code Monster}
 * shell with no {@code Zombie} lifecycle at all.
 *
 * <p>It records the station it was made on, seats a durable four-state charge, keeps a small ward
 * around that station by inspecting one persisted quadrant bearing at a time, bars and repels only
 * what it can actually see inside that ward, never pursues past its tether, accumulates strain while
 * its charge cannot be discharged, and stands itself down at the cap. One deliberate open-handed
 * socket act by any party seats or draws the charge.</p>
 *
 * <p>Everything the species does is owned by {@link IronboundSentinelRuntime}, which is the sole
 * navigation writer. The goal selector holds only {@code JUMP} and {@code LOOK} work and the target
 * selector is empty, so no vanilla goal can take navigation or pick a target behind the runtime's
 * back. Deliberately absent: the Drowned conversion and its underwater timer, reinforcement
 * summoning, baby and jockey variants, door breaking, turtle-egg destruction, village travel, random
 * strolling, {@code HurtByTargetGoal}, loot pickup and sunlight combustion.</p>
 */
public final class IronboundSentinelEntity extends Monster implements ArcaneCreature {
    /**
     * The exact values in effect today. The kind matches no rule in {@code ATTRIBUTE_FACTORY_RULES}
     * and no arm of {@code groundAttributes}, so the registry supplies the bare
     * {@code Zombie.createAttributes()} result: follow range 35.0, movement speed 0.23, attack
     * damage 3.0, armor 2.0 and the {@code MAX_HEALTH} attribute default of 20.0 inherited through
     * {@code Monster.createMonsterAttributes()}. F36 declares no new number; it restores the one
     * already in effect so a route-dependent finalization cannot change it.
     */
    public static final double BASE_MAX_HEALTH = 20.0D;
    public static final double BASE_ATTACK_DAMAGE = 3.0D;
    public static final double BASE_ARMOR = 2.0D;
    public static final double BASE_FOLLOW_RANGE = 35.0D;
    public static final double BASE_MOVEMENT_SPEED = 0.23D;
    /** The inherited experience reward, {@code Monster}'s own value, restated so it cannot drift. */
    public static final int XP_REWARD = 5;

    static final String STATE_KEY = "WarlockerySentinelState";

    private static final Identifier BABY_MODIFIER = Identifier.withDefaultNamespace("baby");
    private static final Identifier ZOMBIE_RANDOM_SPAWN_BONUS =
        Identifier.withDefaultNamespace("zombie_random_spawn_bonus");
    private static final Identifier LEADER_ZOMBIE_BONUS =
        Identifier.withDefaultNamespace("leader_zombie_bonus");

    private final IronboundSentinelRuntime.Counters sentinelCounters =
        new IronboundSentinelRuntime.Counters();
    private final IronboundSentinelRuntime.TransientState sentinelTransient =
        new IronboundSentinelRuntime.TransientState();
    private IronboundSentinelState sentinelState = IronboundSentinelState.empty();

    public IronboundSentinelEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level);
        this.xpReward = XP_REWARD;
        normalizeLifecycle();
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.IRONBOUND_SENTINEL;
    }

    public IronboundSentinelState sentinelState() {
        return sentinelState;
    }

    public void setSentinelState(final IronboundSentinelState state) {
        sentinelState = state == null ? IronboundSentinelState.empty() : state;
    }

    public IronboundSentinelRuntime.Counters sentinelCounters() {
        return sentinelCounters;
    }

    public IronboundSentinelRuntime.TransientState sentinelTransient() {
        return sentinelTransient;
    }

    public Optional<UUID> warlockeryOwner() {
        return CreatureBehaviorState.owner(this);
    }

    /**
     * {@code JUMP} and {@code LOOK} only. Nothing here owns {@code MOVE} or {@code TARGET}: the
     * runtime is the sole navigation writer and the target selector stays permanently empty, so the
     * three-writer contention the audit records cannot recur.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new LookOnlyRandomLookGoal(this));
    }

    /** Redeclares LOOK only so the goal selector can never claim MOVE. */
    private static final class LookOnlyRandomLookGoal extends RandomLookAroundGoal {
        private LookOnlyRandomLookGoal(final net.minecraft.world.entity.Mob mob) {
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
        IronboundSentinelRuntime.tick(this, level);
    }

    /**
     * The ordered charge legality function is the final absolute gate on offence. The Sentinel can
     * only ever strike what its own runtime has already bound, which is what stops an external
     * {@code setTarget} from turning a ward keeper into a hunter.
     */
    @Override
    public boolean canAttack(final LivingEntity target) {
        return super.canAttack(target) && IronboundSentinelRuntime.legalSubject(this, target);
    }

    /**
     * The socket act. It is a pure predicate over the interaction, never a tick, and its default arm
     * is an ordinary {@link InteractionResult#PASS} so a failed act stays an ordinary click rather
     * than a swallowed one. There is deliberately no owner gate: the unmaking is available to
     * somebody who is not the maker.
     */
    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !(level() instanceof ServerLevel server)) {
            return super.mobInteract(player, hand);
        }
        final SocketAct act = IronboundSentinelRuntime.socketDecision(this, player);
        return switch (act) {
            case SEAT, DRAW -> IronboundSentinelRuntime.applySocketAct(this, server, act);
            case PASS -> super.mobInteract(player, hand);
        };
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final float before = getHealth() + getAbsorptionAmount();
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && before - (getHealth() + getAbsorptionAmount()) > 0.0F) {
            IronboundSentinelRuntime.onAcceptedDamage(this, level, source);
        }
        return hurt;
    }

    /**
     * A {@code MONSTER} standing in somebody's base must not block the night. The species has no
     * sleep relationship in any phase and in any charge state.
     */
    @Override
    public boolean isPreventingPlayerRest(final ServerLevel level, final Player player) {
        return false;
    }

    /** Removal for any reason drops every held scratch, so nothing outlives the entity. */
    @Override
    public void remove(final RemovalReason reason) {
        IronboundSentinelRuntime.onRemoved(this);
        super.remove(reason);
    }

    /** The phase currently in force, transient and always normalized from the durable charge. */
    public Phase phase() {
        return sentinelTransient.phase();
    }

    public Optional<BlockPos> station() {
        return sentinelState.station();
    }

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
    protected void playStepSound(final BlockPos position, final BlockState state) {
        playSound(SoundEvents.ZOMBIE_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void populateDefaultEquipmentSlots(
        final RandomSource random,
        final DifficultyInstance difficulty
    ) {
        normalizeLifecycle();
    }

    /**
     * The one place a route-dependent difference could still enter. {@code Mob.finalizeSpawn} adds a
     * random follow-range bonus modifier; normalizing after {@code super} strips it, so a spawn-egg
     * Sentinel, a command-summoned one and a datapack one carry byte-identical statistics.
     */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason reason,
        final @Nullable SpawnGroupData groupData
    ) {
        final SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
        normalizeLifecycle();
        return result;
    }

    /**
     * Removes the legacy Zombie lifecycle and restores the declared bases. The Sentinel carries no
     * equipment at any point and never picks any up, which is a deliberate, player-visible removal
     * of the one path by which the current mob can drop anything.
     */
    public void normalizeLifecycle() {
        setCanPickUpLoot(false);
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            if (!getItemBySlot(slot).isEmpty()) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        restoreBase(Attributes.MAX_HEALTH, BASE_MAX_HEALTH);
        restoreBase(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE);
        restoreBase(Attributes.ARMOR, BASE_ARMOR);
        restoreBase(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE);
        restoreBase(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED);
        restoreBase(Attributes.KNOCKBACK_RESISTANCE, null);
        // The attribute survives on the registry-owned supplier even though no Zombie code can read
        // it any more. Pinning the base at zero means an old save that was carrying a randomized
        // reinforcement chance cannot keep it.
        restoreBase(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D);
        setHealth(Math.min(getHealth(), getMaxHealth()));
    }

    private void restoreBase(final Holder<Attribute> attribute, final @Nullable Double base) {
        final AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(BABY_MODIFIER);
        instance.removeModifier(RANDOM_SPAWN_BONUS_ID);
        instance.removeModifier(ZOMBIE_RANDOM_SPAWN_BONUS);
        instance.removeModifier(LEADER_ZOMBIE_BONUS);
        if (base != null) {
            instance.setBaseValue(base);
        }
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, sentinelState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        sentinelState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(IronboundSentinelState::read)
            .orElseGet(IronboundSentinelState::empty);
        sentinelTransient.resetForLoad();
        normalizeLifecycle();
        setTarget(null);
        getNavigation().stop();
    }
}
