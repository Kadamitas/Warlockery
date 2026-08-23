package com.kadamitas.warlockery.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import org.jspecify.annotations.Nullable;

public final class EntEntity extends PathfinderMob implements ArcaneCreature {
    static final String STATE_KEY="WarlockeryEntState";
    public static final double BASE_MAX_HEALTH=200, BASE_ATTACK_DAMAGE=15, BASE_MOVEMENT_SPEED=.25,
        BASE_ARMOR=2, BASE_FOLLOW_RANGE=16, BASE_KNOCKBACK_RESISTANCE=1, BASE_STEP_HEIGHT=1;
    public static final int BASE_XP_REWARD=0;
    private static final EntityDataAccessor<Integer> DATA_VARIANT=SynchedEntityData.defineId(EntEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_PRESENTATION_PHASE=SynchedEntityData.defineId(EntEntity.class,EntityDataSerializers.BYTE);
    private EntState entState=new EntState(1,0,0,0,0,0,0,0);
    private final EntRuntime.TransientState transientState=new EntRuntime.TransientState();
    private final EntRuntime.Counters counters=new EntRuntime.Counters();
    public EntEntity(EntityType<? extends PathfinderMob> type,Level level){super(type,level);xpReward=BASE_XP_REWARD;}
    @Override protected void registerGoals(){goalSelector.addGoal(9,new LookAtPlayerGoal(this,Player.class,8));goalSelector.addGoal(10,new LookOnlyRandomLookGoal(this));}
    private static final class LookOnlyRandomLookGoal extends RandomLookAroundGoal{LookOnlyRandomLookGoal(Mob mob){super(mob);setFlags(java.util.EnumSet.of(Flag.LOOK));}}
    @Override public CreatureKind creatureKind(){return CreatureKind.ENT;}
    public EntVariant variant(){return EntVariant.byOrdinal(entityData.get(DATA_VARIANT));}
    public EntRules.Phase presentationPhase(){return EntityPresentationSync.decode(entityData.get(DATA_PRESENTATION_PHASE),EntRules.Phase.WARDING);}
    EntState entState(){return entState;} void setEntState(EntState state){entState=state;}
    EntRuntime.TransientState entTransient(){return transientState;} public EntRuntime.Counters entCounters(){return counters;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(DATA_VARIANT,0);builder.define(DATA_PRESENTATION_PHASE,EntityPresentationSync.encode(EntRules.Phase.WARDING));}
    private void syncPresentationFromRuntime(){byte phase=EntityPresentationSync.encode(transientState.phase());if(entityData.get(DATA_PRESENTATION_PHASE)!=phase)entityData.set(DATA_PRESENTATION_PHASE,phase);}
    @Override protected void customServerAiStep(ServerLevel level){super.customServerAiStep(level);EntRuntime.tick(this,level);syncPresentationFromRuntime();}
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){boolean axe=source.getWeaponItem()!=null&&source.getWeaponItem().is(ItemTags.AXES);boolean mob=source.getEntity() instanceof Mob&&!(source.getEntity() instanceof Player);float before=getHealth()+getAbsorptionAmount();boolean hurt=super.hurtServer(level,source,EntRules.incomingDamage(amount,axe,mob));float after=getHealth()+getAbsorptionAmount();if(hurt&&after<before&&source.getEntity() instanceof LivingEntity living)EntRuntime.afterHurt(this,living);return hurt;}
    @Override public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,EntitySpawnReason reason,@Nullable SpawnGroupData group){SpawnGroupData result=super.finalizeSpawn(level,difficulty,reason,group);String biome=level.registryAccess().lookupOrThrow(Registries.BIOME).getKey(level.getBiome(blockPosition()).value()).toString();initializeVariant(EntVariant.fromBiome(biome));entState=EntState.fresh(getBlockX(),getBlockY(),getBlockZ());return result;}
    @Override protected void addAdditionalSaveData(ValueOutput out){super.addAdditionalSaveData(out);out.putString("WarlockeryEntVariant",variant().serializedName());EntRuntime.writeState(out,entState);}
    @Override protected void readAdditionalSaveData(ValueInput in){super.readAdditionalSaveData(in);in.getString("WarlockeryEntVariant").map(EntVariant::fromSerializedName).ifPresent(this::initializeVariant);entState=EntRuntime.readState(in,blockPosition(),level().getMinY(),level().getMaxY());var legacy=getPersistentData();String key="WarlockeryAmbientCooldownGROVE_TENDING";if(legacy.contains(key)){entState=entState.withCooldowns(entState.warnCooldownRemaining(),EntState.migrateLegacyCooldown(legacy.getLongOr(key,0),level().getGameTime()));legacy.remove(key);}transientState.clear();EntRuntime.cancel(this,EntRules.Phase.WARDING);}
    @Override public void remove(RemovalReason reason){EntRuntime.cancel(this,EntRules.Phase.WARDING);super.remove(reason);}
    private void initializeVariant(EntVariant selected){entityData.set(DATA_VARIANT,selected.ordinal());setCustomName(Component.translatable("entity.warlockery.ent.variant."+selected.serializedName()));applyTraits(selected.traits());}
    private void applyTraits(EntTraits traits){double previous=getMaxHealth();float ratio=previous<=0?1:getHealth()/(float)previous;setBase(Attributes.MAX_HEALTH,traits.maxHealth());setBase(Attributes.ATTACK_DAMAGE,traits.attackDamage());setBase(Attributes.MOVEMENT_SPEED,traits.movementSpeed());setBase(Attributes.ARMOR,traits.armor());setHealth(Math.clamp((float)(traits.maxHealth()*ratio),1,getMaxHealth()));}
    private void setBase(net.minecraft.core.Holder<Attribute> attribute,double value){AttributeInstance instance=getAttribute(attribute);if(instance!=null)instance.setBaseValue(value);}
    public enum EntVariant{
        OAK("oak",0xFFFFFFFF,new EntTraits(200,15,.25,2)),BIRCH("birch",0xFFF1E4B8,new EntTraits(200,13,.29,1)),SPRUCE("spruce",0xFF77906A,new EntTraits(200,16,.23,4)),JUNGLE("jungle",0xFF5FAF61,new EntTraits(200,17,.27,2)),DARK_OAK("dark_oak",0xFF73583F,new EntTraits(200,18,.21,6)),ACACIA("acacia",0xFFE08A52,new EntTraits(200,15,.30,1)),MANGROVE("mangrove",0xFF8A554C,new EntTraits(200,14,.22,5)),CHERRY("cherry",0xFFF1A8B8,new EntTraits(200,13,.28,1)),PALE_OAK("pale_oak",0xFFD8DED1,new EntTraits(200,16,.24,3));
        private final String name;private final int tint;private final EntTraits traits;EntVariant(String name,int tint,EntTraits traits){this.name=name;this.tint=tint;this.traits=traits;}public String serializedName(){return name;}public int tint(){return tint;}public EntTraits traits(){return traits;}static EntVariant fromBiome(String biome){if(biome.contains("cherry"))return CHERRY;if(biome.contains("pale_garden"))return PALE_OAK;if(biome.contains("mangrove"))return MANGROVE;if(biome.contains("dark_forest"))return DARK_OAK;if(biome.contains("jungle"))return JUNGLE;if(biome.contains("savanna"))return ACACIA;if(biome.contains("birch"))return BIRCH;if(biome.contains("taiga")||biome.contains("grove"))return SPRUCE;return OAK;}static EntVariant fromSerializedName(String name){for(var v:values())if(v.name.equals(name))return v;return OAK;}static EntVariant byOrdinal(int ordinal){return ordinal>=0&&ordinal<values().length?values()[ordinal]:OAK;}}
    public record EntTraits(double maxHealth,double attackDamage,double movementSpeed,double armor){public EntTraits{if(maxHealth<1||attackDamage<0||movementSpeed<=0||armor<0)throw new IllegalArgumentException("Ent traits must be safe positive combat values");}}
}
