package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.LycanPackRules.Variant;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class WerewolfEntity extends ArcaneMob {
    private static final String STATE_KEY = "WarlockeryLycanPack";
    private static final Identifier ZOMBIE_RANDOM_SPAWN_BONUS =
        Identifier.withDefaultNamespace("zombie_random_spawn_bonus");
    private static final Identifier LEADER_ZOMBIE_BONUS =
        Identifier.withDefaultNamespace("leader_zombie_bonus");

    private final LycanPackRuntime.Counters packCounters = new LycanPackRuntime.Counters();
    private LycanPackState packState;
    private @Nullable UUID transientAttackerId;
    private long transientAttackerUntil;
    private @Nullable UUID transientCarrionId;

    public WerewolfEntity(final EntityType<? extends Zombie> type, final Level level) {
        super(type, level, CreatureKind.WEREWOLF);
        packState = LycanPackState.empty(variant(), level.getGameTime());
        normalizeLifecycle();
    }

    public Variant variant() {
        return Variant.WEREWOLF;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new LycanMeleeGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    private static final class LycanMeleeGoal extends Goal {
        private final WerewolfEntity lycan;
        private int attackCooldown;

        private LycanMeleeGoal(final WerewolfEntity lycan) {
            this.lycan = lycan;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return LycanPackRuntime.meleeExecutorMayRun(lycan);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            lycan.setAggressive(true);
        }

        @Override
        public void stop() {
            lycan.setAggressive(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            final LivingEntity target = lycan.getTarget();
            if (target == null) return;
            lycan.getLookControl().setLookAt(target, 30.0F, 30.0F);
            attackCooldown = Math.max(0, attackCooldown - 1);
            if (attackCooldown == 0 && lycan.isWithinMeleeAttackRange(target)
                && lycan.getSensing().hasLineOfSight(target)) {
                attackCooldown = adjustedTickDelay(20);
                lycan.swing(InteractionHand.MAIN_HAND);
                lycan.doHurtTarget((ServerLevel) lycan.level(), target);
            }
        }
    }

    /**
     * The pack runtime replaces the generic tactical layer completely: it owns target arbitration,
     * the pounce and harry actions, the fear retreat and every navigation request, all under its own
     * cadence and route-failure accounting, so {@link TacticalCombatRuntime} stays out by
     * construction and the PACK doctrine row for this kind is retired rather than left unreachable.
     *
     * <p>It replaced nothing in the ambient layer. The moon is read only to size a hunt, and an idle
     * sated lycan has no night posture of its own, so the declared MOON_GAZE vigil is reached again
     * here. The ambient pass yields on its own whenever a target, a hazard or a passenger state is
     * live, which is exactly when the pack runtime is steering.</p>
     */
    @Override
    protected void tickSpecializedActivity(final ServerLevel level) {
        LycanPackRuntime.tick(this, level);
        AmbientActivityRuntime.tick(this, level, CreatureKind.WEREWOLF);
    }

    public LycanPackState packState() {
        return packState;
    }

    public void setPackState(final LycanPackState state) {
        packState = state == null || state.variant() != variant()
            ? LycanPackState.empty(variant(), level().getGameTime()) : state;
    }

    public LycanPackRuntime.Counters packCounters() {
        return packCounters;
    }

    public @Nullable UUID transientAttackerId(final long now) {
        return transientAttackerUntil > now ? transientAttackerId : null;
    }

    public void rememberTransientAttacker(final @Nullable UUID attackerId, final long until) {
        transientAttackerId = attackerId;
        transientAttackerUntil = attackerId == null ? 0L : until;
    }

    public @Nullable UUID transientCarrionId() {
        return transientCarrionId;
    }

    public void setTransientCarrionId(final @Nullable UUID itemId) {
        transientCarrionId = itemId;
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return LycanPackRuntime.eligibleTarget(this, target);
    }

    boolean lycanBaseMayAttack(final LivingEntity target) {
        return super.canAttack(target);
    }

    @Override
    public boolean doHurtTarget(final ServerLevel level, final Entity target) {
        final int fireBefore = target.getRemainingFireTicks();
        final boolean hurt = super.doHurtTarget(level, target);
        if (hurt) {
            suppressInheritedBurningMelee(target, fireBefore);
            LycanPackRuntime.afterSuccessfulAttack(this, target, level.getGameTime());
        }
        return hurt;
    }

    private void suppressInheritedBurningMelee(final Entity target, final int fireBefore) {
        if (isOnFire() && getMainHandItem().isEmpty() && target.getRemainingFireTicks() > fireBefore) {
            target.setRemainingFireTicks(fireBefore);
        }
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) LycanPackRuntime.afterHurt(this, level, source, amount);
        return hurt;
    }

    @Override
    public boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean isUnderWaterConverting() {
        return false;
    }

    @Override
    public boolean killedEntity(final ServerLevel level, final LivingEntity entity, final DamageSource source) {
        LycanPackRuntime.afterKill(this, level, entity);
        return entity instanceof Villager || super.killedEntity(level, entity, source);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level,
        final DifficultyInstance difficulty,
        final EntitySpawnReason reason,
        final @Nullable SpawnGroupData groupData
    ) {
        final SpawnGroupData result = super.finalizeSpawn(
            level, difficulty, reason, new Zombie.ZombieGroupData(false, false)
        );
        normalizeLifecycle();
        for (final EquipmentSlot slot : EquipmentSlot.values()) setItemSlot(slot, ItemStack.EMPTY);
        return result;
    }

    private void normalizeLifecycle() {
        setBaby(false);
        setCanBreakDoors(false);
        setCanPickUpLoot(false);
        removeModifier(Attributes.FOLLOW_RANGE, RANDOM_SPAWN_BONUS_ID);
        removeModifier(Attributes.FOLLOW_RANGE, ZOMBIE_RANDOM_SPAWN_BONUS);
        removeModifier(Attributes.KNOCKBACK_RESISTANCE, RANDOM_SPAWN_BONUS_ID);
        removeModifier(Attributes.MAX_HEALTH, LEADER_ZOMBIE_BONUS);
        removeModifier(Attributes.SPAWN_REINFORCEMENTS_CHANCE, LEADER_ZOMBIE_BONUS);
        final AttributeInstance reinforcement = getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
        if (reinforcement != null) {
            reinforcement.removeModifiers();
            reinforcement.setBaseValue(0.0D);
        }
        setHealth(Math.min(getHealth(), getMaxHealth()));
    }

    private void removeModifier(final Holder<Attribute> attribute, final Identifier identifier) {
        final AttributeInstance instance = getAttribute(attribute);
        if (instance != null) instance.removeModifier(identifier);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, CompoundTag.CODEC, packState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        packState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> LycanPackState.read(tag, variant(), level().getGameTime()))
            .orElse(LycanPackState.empty(variant(), level().getGameTime()));
        transientCarrionId = null;
        transientAttackerId = null;
        transientAttackerUntil = 0L;
        normalizeLifecycle();
    }
}
