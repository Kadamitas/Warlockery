package com.kadamitas.warlockery.entity;

import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class MandrakeEntity extends Monster implements ArcaneCreature {
    public static final double BASE_MAX_HEALTH=20,BASE_FOLLOW_RANGE=35,BASE_MOVEMENT_SPEED=.23,BASE_ATTACK_DAMAGE=3,BASE_ARMOR=2;
    public static final int XP_REWARD=5; static final String STATE_KEY="WarlockeryMandrakeState";
    private MandrakeState state=MandrakeState.empty(); private final MandrakeRuntime.TransientState transientState=new MandrakeRuntime.TransientState(); private final MandrakeRuntime.Counters counters=new MandrakeRuntime.Counters(); private boolean extractionBorn;
    public MandrakeEntity(EntityType<? extends Monster> type,Level level){super(type,level);xpReward=XP_REWARD;normalizeLifecycle();}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,BASE_MAX_HEALTH).add(Attributes.FOLLOW_RANGE,BASE_FOLLOW_RANGE).add(Attributes.MOVEMENT_SPEED,BASE_MOVEMENT_SPEED).add(Attributes.ATTACK_DAMAGE,BASE_ATTACK_DAMAGE).add(Attributes.ARMOR,BASE_ARMOR).add(Attributes.SPAWN_REINFORCEMENTS_CHANCE,0);}
    @Override public CreatureKind creatureKind(){return CreatureKind.MANDRAKE;}
    public MandrakeState mandrakeState(){return state;} public void setMandrakeState(MandrakeState value){state=value==null?MandrakeState.empty():value;} public MandrakeRuntime.TransientState mandrakeTransient(){return transientState;} public MandrakeRuntime.Counters mandrakeCounters(){return counters;}
    public void markExtractionBorn(){extractionBorn=true;} boolean consumeExtractionBorn(){boolean result=extractionBorn;extractionBorn=false;return result;}
    @Override protected void registerGoals(){goalSelector.addGoal(10,new LookOnly(this));}
    private static final class LookOnly extends RandomLookAroundGoal{LookOnly(net.minecraft.world.entity.Mob mob){super(mob);setFlags(EnumSet.of(Goal.Flag.LOOK));}}
    @Override protected void customServerAiStep(ServerLevel level){super.customServerAiStep(level);MandrakeRuntime.tick(this,level);}
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){float healthBefore=getHealth()+getAbsorptionAmount();boolean accepted=super.hurtServer(level,source,amount);if(accepted&&getHealth()+getAbsorptionAmount()<healthBefore&&source.getEntity() instanceof LivingEntity attacker)MandrakeRuntime.acceptedDamage(this,attacker);return accepted;}
    @Override public boolean isPreventingPlayerRest(ServerLevel level,Player player){return !LivingRootsRules.rooted(transientState.phase())&&super.isPreventingPlayerRest(level,player);}
    @Override protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource r,DifficultyInstance d){}
    @Override public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance d,EntitySpawnReason reason,@Nullable SpawnGroupData data){var result=super.finalizeSpawn(level,d,reason,data);normalizeLifecycle();return result;}
    public void normalizeLifecycle(){setCanPickUpLoot(false);for(var slot:EquipmentSlot.values())setItemSlot(slot,ItemStack.EMPTY);restore(Attributes.MAX_HEALTH,BASE_MAX_HEALTH);restore(Attributes.FOLLOW_RANGE,BASE_FOLLOW_RANGE);restore(Attributes.MOVEMENT_SPEED,BASE_MOVEMENT_SPEED);restore(Attributes.ATTACK_DAMAGE,BASE_ATTACK_DAMAGE);restore(Attributes.ARMOR,BASE_ARMOR);restore(Attributes.SPAWN_REINFORCEMENTS_CHANCE,0);setHealth(Math.min(getHealth(),getMaxHealth()));}
    private void restore(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> key,double base){var a=getAttribute(key);if(a!=null){for(String id:java.util.List.of("baby","random_spawn_bonus","zombie_random_spawn_bonus","leader_zombie_bonus","reinforcement_caller_charge","reinforcement_callee_charge")){a.removeModifier(net.minecraft.resources.Identifier.withDefaultNamespace(id));}a.setBaseValue(base);}}
    @Override protected void addAdditionalSaveData(ValueOutput output){super.addAdditionalSaveData(output);output.store(STATE_KEY,CompoundTag.CODEC,state.write());}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);state=input.read(STATE_KEY,CompoundTag.CODEC).map(MandrakeState::read).orElse(MandrakeState.empty());transientState.resetForLoad();normalizeLifecycle();}
    @Override public void remove(RemovalReason reason){MandrakeRuntime.cancel(this);super.remove(reason);}
}
