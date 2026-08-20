package com.kadamitas.warlockery.entity;

import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class ThornedPursuerEntity extends Monster implements ArcaneCreature {
    public static final double BASE_MAX_HEALTH = 100.0D;
    public static final double BASE_ATTACK_DAMAGE = 11.0D;
    public static final double BASE_ARMOR = 8.0D;
    public static final double BASE_FOLLOW_RANGE = 35.0D;
    public static final double BASE_MOVEMENT_SPEED = 0.23D;
    public static final double BASE_REINFORCEMENT_CHANCE = 0.0D;
    public static final int BASE_XP = 5;
    public static final int LIFECYCLE_EQUIPMENT_SLOTS = 0;
    private static final String STATE_KEY = "WarlockeryThornedPursuerState";
    private static final Set<String> LIFECYCLE_MODIFIERS = Set.of(
        "minecraft:baby", "minecraft:random_spawn_bonus", "minecraft:zombie_random_spawn_bonus",
        "minecraft:leader_zombie_bonus", "minecraft:reinforcement_caller_charge",
        "minecraft:reinforcement_callee_charge", "warlockery:thorned_pursuer_course");

    private ThornedPursuerState pursuerState = ThornedPursuerState.defaults();
    private final ThornedPursuerRuntime.Transient runtime = new ThornedPursuerRuntime.Transient();
    private ThornedPursuerRuntime.Counters counters = new ThornedPursuerRuntime.Counters();

    public ThornedPursuerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = BASE_XP;
        normalizeLifecycle();
    }

    @Override public CreatureKind creatureKind() { return CreatureKind.THORNED_PURSUER; }

    @Override protected void registerGoals() {
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    public List<String> operationalGoalNames() {
        return goalSelector.getAvailableGoals().stream().map(goal -> goal.getGoal().getClass().getSimpleName()).toList();
    }
    public int operationalTargetGoalCount() { return targetSelector.getAvailableGoals().size(); }
    public static Set<String> lifecycleModifierIds() { return LIFECYCLE_MODIFIERS; }
    public ThornedPursuerState pursuerState() { return pursuerState; }
    public void setPursuerState(ThornedPursuerState state) { pursuerState = state == null ? ThornedPursuerState.defaults() : state; }
    public ThornedPursuerRuntime.Transient pursuerRuntime() { return runtime; }
    public ThornedPursuerRuntime.Counters pursuerCounters() { return counters; }
    public void resetPursuerCounters() { counters = new ThornedPursuerRuntime.Counters(); }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        pursuerState = pursuerState.tickLoaded();
        ThornedPursuerRuntime.tick(this, level);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float beforeHealth = getHealth();
        float beforeAbsorption = getAbsorptionAmount();
        boolean accepted = super.hurtServer(level, source, amount);
        float acceptedLoss = ThornedPursuerRules.acceptedEffectiveLoss(beforeHealth, beforeAbsorption,
            getHealth(), getAbsorptionAmount());
        if (accepted && acceptedLoss > 0.0F && source.getEntity() instanceof LivingEntity attacker) {
            ThornedPursuerRuntime.afterAcceptedDamage(this, level, attacker, acceptedLoss);
        }
        return accepted;
    }

    @Override public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
        return runtime.phase().ordinal() >= ThornedPursuerRules.Phase.BAY.ordinal()
            && runtime.phase().ordinal() <= ThornedPursuerRules.Phase.BREAK.ordinal();
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(STATE_KEY, net.minecraft.nbt.CompoundTag.CODEC, pursuerState.write());
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        pursuerState = input.read(STATE_KEY, net.minecraft.nbt.CompoundTag.CODEC)
            .map(ThornedPursuerState::read).orElseGet(ThornedPursuerState::defaults);
        normalizeLifecycle();
        runtime.resetForLoad(this);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                   EntitySpawnReason reason,
                                                   @Nullable SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
        normalizeLifecycle();
        return result;
    }

    public void normalizeLifecycle() {
        setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) setItemSlot(slot, ItemStack.EMPTY);
        setAttributeBase(Attributes.MAX_HEALTH, BASE_MAX_HEALTH);
        setAttributeBase(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE);
        setAttributeBase(Attributes.ARMOR, BASE_ARMOR);
        setAttributeBase(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE);
        setAttributeBase(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED);
        setAttributeBase(Attributes.SPAWN_REINFORCEMENTS_CHANCE, BASE_REINFORCEMENT_CHANCE);
        setHealth(Math.min(getHealth(), getMaxHealth()));
    }

    @Override public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel serverLevel) ThornedPursuerRuntime.onRemoved(this, serverLevel);
        else runtime.reset(this);
        super.remove(reason);
    }

    @Override public Entity teleport(TeleportTransition transition) {
        if (level() instanceof ServerLevel serverLevel) ThornedPursuerRuntime.onRemoved(this, serverLevel);
        else runtime.resetForLoad(this);
        Entity arrived = super.teleport(transition);
        if (arrived instanceof ThornedPursuerEntity pursuer) pursuer.pursuerRuntime().resetForLoad(pursuer);
        return arrived;
    }

    private void setAttributeBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                  double value) {
        var instance = getAttribute(attribute);
        if (instance != null) {
            for (String id : LIFECYCLE_MODIFIERS) instance.removeModifier(net.minecraft.resources.Identifier.parse(id));
            instance.setBaseValue(value);
        }
    }
}
