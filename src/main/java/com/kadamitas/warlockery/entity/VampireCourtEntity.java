package com.kadamitas.warlockery.entity;

import java.util.EnumSet;
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

public final class VampireCourtEntity extends ArcaneMob {
    private static final String STATE_KEY = "WarlockeryVampireCourt";
    private static final Identifier ZOMBIE_RANDOM_SPAWN_BONUS =
        Identifier.withDefaultNamespace("zombie_random_spawn_bonus");
    private static final Identifier LEADER_ZOMBIE_BONUS =
        Identifier.withDefaultNamespace("leader_zombie_bonus");

    private final VampireCourtRuntime.Counters courtCounters = new VampireCourtRuntime.Counters();
    private VampireCourtState courtState;

    public VampireCourtEntity(
        final EntityType<? extends Zombie> type,
        final Level level,
        final CreatureKind kind
    ) {
        super(type, level, requireCourtKind(kind));
        courtState = VampireCourtState.empty(kind, level.getGameTime());
        normalizeLifecycle();
    }

    private static CreatureKind requireCourtKind(final CreatureKind kind) {
        if (kind != CreatureKind.VAMPIRE && kind != CreatureKind.BLOOD_THRALL) {
            throw new IllegalArgumentException("VampireCourtEntity requires a court kind");
        }
        return kind;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new CourtMeleeGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    private static final class CourtMeleeGoal extends Goal {
        private final VampireCourtEntity member;
        private int attackCooldown;

        private CourtMeleeGoal(final VampireCourtEntity member) {
            this.member = member;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return VampireCourtRuntime.meleeExecutorMayRun(member);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            member.setAggressive(true);
        }

        @Override
        public void stop() {
            member.setAggressive(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            final LivingEntity target = member.getTarget();
            if (target == null) return;
            member.getLookControl().setLookAt(target, 30.0F, 30.0F);
            attackCooldown = Math.max(0, attackCooldown - 1);
            if (attackCooldown == 0 && member.isWithinMeleeAttackRange(target)
                && member.getSensing().hasLineOfSight(target)) {
                attackCooldown = adjustedTickDelay(20);
                member.swing(InteractionHand.MAIN_HAND);
                member.doHurtTarget((ServerLevel) member.level(), target);
            }
        }
    }

    @Override
    protected void tickSpecializedActivity(final ServerLevel level) {
        VampireCourtRuntime.tick(this, level);
    }

    public VampireCourtState courtState() {
        return courtState;
    }

    public void setCourtState(final VampireCourtState state) {
        courtState = state == null || state.kind() != creatureKind()
            ? VampireCourtState.empty(creatureKind(), level().getGameTime()) : state;
    }

    public VampireCourtRuntime.Counters courtCounters() {
        return courtCounters;
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return VampireCourtRuntime.eligibleTarget(this, target);
    }

    boolean courtBaseMayAttack(final LivingEntity target) {
        return super.canAttack(target);
    }

    @Override
    public boolean doHurtTarget(final ServerLevel level, final Entity target) {
        final boolean hurt = super.doHurtTarget(level, target);
        if (hurt) VampireCourtRuntime.afterSuccessfulAttack(this, target, level.getGameTime());
        return hurt;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) VampireCourtRuntime.rememberAttacker(this, source.getEntity(), level.getGameTime());
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
        return result;
    }

    private void normalizeLifecycle() {
        setBaby(false);
        setCanBreakDoors(false);
        setCanPickUpLoot(false);
        for (final EquipmentSlot slot : EquipmentSlot.values()) setItemSlot(slot, ItemStack.EMPTY);
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
        output.store(STATE_KEY, CompoundTag.CODEC, courtState.write());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        courtState = input.read(STATE_KEY, CompoundTag.CODEC)
            .map(tag -> VampireCourtState.read(tag, creatureKind(), level().getGameTime()))
            .orElse(VampireCourtState.empty(creatureKind(), level().getGameTime()));
        normalizeLifecycle();
    }
}
