package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
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
import net.minecraft.world.DifficultyInstance;
import net.minecraft.resources.Identifier;
import java.util.EnumSet;
import org.jspecify.annotations.Nullable;

public final class NaamahEntity extends ArcaneMob {
    private static final Identifier ZOMBIE_RANDOM_SPAWN_BONUS = Identifier.withDefaultNamespace("zombie_random_spawn_bonus");
    private static final Identifier LEADER_ZOMBIE_BONUS = Identifier.withDefaultNamespace("leader_zombie_bonus");
    private final ServerBossEvent courtBossEvent;
    private final NaamahCourtRuntime.Counters courtCounters = new NaamahCourtRuntime.Counters();
    private NaamahCourtState courtState = NaamahCourtState.empty();
    private long regenerationSuppressedUntil;
    private long nextRegenerationAt;

    public NaamahEntity(final EntityType<? extends Zombie> type, final Level level) {
        super(type, level, CreatureKind.NAAMAH);
        setCustomName(Component.translatable("entity.warlockery.naamah"));
        setCustomNameVisible(true);
        courtBossEvent = new ServerBossEvent(
            getUUID(), Component.translatable("entity.warlockery.naamah"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS
        );
        normalizeLifecycle();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new CourtMeleeGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    private static final class CourtMeleeGoal extends Goal {
        private final NaamahEntity naamah;
        private int attackCooldown;

        private CourtMeleeGoal(final NaamahEntity naamah) {
            this.naamah = naamah;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return NaamahCourtRuntime.meleeExecutorMayRun(naamah);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            naamah.setAggressive(true);
        }

        @Override
        public void stop() {
            naamah.setAggressive(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            final LivingEntity target = naamah.getTarget();
            if (target == null) return;
            naamah.getLookControl().setLookAt(target, 30.0F, 30.0F);
            attackCooldown = Math.max(attackCooldown - 1, 0);
            if (attackCooldown == 0 && naamah.isWithinMeleeAttackRange(target)
                && naamah.getSensing().hasLineOfSight(target)) {
                attackCooldown = adjustedTickDelay(20);
                naamah.swing(InteractionHand.MAIN_HAND);
                naamah.doHurtTarget((ServerLevel)naamah.level(), target);
            }
        }
    }

    @Override
    protected void tickSpecializedActivity(final ServerLevel level) {
        NaamahCourtRuntime.tick(this, level);
    }

    public NaamahCourtState courtState() {
        return courtState;
    }

    public void setCourtState(final NaamahCourtState state) {
        courtState = state == null ? NaamahCourtState.empty() : state;
    }

    public long regenerationSuppressedUntil() {
        return regenerationSuppressedUntil;
    }

    public void suppressRegenerationUntil(final long tick) {
        regenerationSuppressedUntil = Math.max(regenerationSuppressedUntil, tick);
    }

    public long nextRegenerationAt() {
        return nextRegenerationAt;
    }

    public void setNextRegenerationAt(final long tick) {
        nextRegenerationAt = tick;
    }

    public NaamahCourtRuntime.Counters courtCounters() {
        return courtCounters;
    }

    void updateCourtBossBar() {
        courtBossEvent.setProgress(Math.clamp(getHealth() / Math.max(1.0F, getMaxHealth()), 0.0F, 1.0F));
        courtBossEvent.setVisible(isAlive() && !courtState.audienceConcluded());
    }

    @Override
    public void startSeenByPlayer(final ServerPlayer player) {
        super.startSeenByPlayer(player);
        courtBossEvent.addPlayer(player);
        updateCourtBossBar();
    }

    @Override
    public void stopSeenByPlayer(final ServerPlayer player) {
        super.stopSeenByPlayer(player);
        courtBossEvent.removePlayer(player);
    }

    @Override
    public boolean canAttack(final LivingEntity target) {
        return NaamahCourtRuntime.eligibleTarget(this, target) && super.canAttack(target);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
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
        final EntitySpawnReason spawnReason,
        final @Nullable SpawnGroupData groupData
    ) {
        final SpawnGroupData finalized = super.finalizeSpawn(
            level, difficulty, spawnReason, new Zombie.ZombieGroupData(false, false)
        );
        normalizeLifecycle();
        return finalized;
    }

    private void normalizeLifecycle() {
        setBaby(false);
        setCanBreakDoors(false);
        setCanPickUpLoot(false);
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            setItemSlot(slot, ItemStack.EMPTY);
        }
        clearRandomZombieAttributes();
        setPersistenceRequired();
    }

    private void clearRandomZombieAttributes() {
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

    private void removeModifier(final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                final Identifier identifier) {
        final AttributeInstance instance = getAttribute(attribute);
        if (instance != null) instance.removeModifier(identifier);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
        if (amount >= getHealth()
            && source.getEntity() instanceof ServerPlayer player
            && player.getStringUUID().equals(WarlockeryEntityData.get(this).getStringOr(
                SupernaturalProgressionRuntime.NAAMAH_TRIAL_OWNER,
                ""
            ))
            && SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE
            && SupernaturalProgression.level(player, SupernaturalProgression.Path.VAMPIRE) == 6) {
            setHealth(1.0F);
            clearFire();
            setTarget(null);
            getNavigation().stop();
            setCourtState(courtState.conclude(player.getUUID()));
            updateCourtBossBar();
            SupernaturalProgressionRuntime.recordNaamahDefeat(player);
            return true;
        }
        final boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            NaamahCourtRuntime.rememberAttacker(this, source.getEntity(), level.getGameTime());
            updateCourtBossBar();
        }
        return hurt;
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("WarlockeryNaamahCourt", CompoundTag.CODEC, courtState.write());
        output.putLong("WarlockeryNaamahRegenSuppressedUntil", regenerationSuppressedUntil);
        output.putLong("WarlockeryNaamahNextRegenerationAt", nextRegenerationAt);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        courtState = input.read("WarlockeryNaamahCourt", CompoundTag.CODEC)
            .map(tag -> NaamahCourtState.read(tag, level().getGameTime(), getHealth(), getMaxHealth()))
            .orElse(NaamahCourtState.empty().latchPhase(getHealth(), getMaxHealth()));
        regenerationSuppressedUntil = input.getLongOr("WarlockeryNaamahRegenSuppressedUntil", 0L);
        nextRegenerationAt = input.getLongOr("WarlockeryNaamahNextRegenerationAt", 0L);
        normalizeLifecycle();
    }
}
