package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MandrakeRuntime {
    public enum CancellationReason { REMOVAL, TELEPORT, DIMENSION_CHANGE, DEATH, DISCARD, TRADE, SLEEP, RAID, PANIC, BREEDING, HAZARD, SUBJECT_INVALID, TOKEN_DENIED, UNLOAD }
    private static final List<BlockPos> SAFE_OFFSETS=List.of(
        new BlockPos(2,0,0),new BlockPos(-2,0,0),new BlockPos(0,0,2),new BlockPos(0,0,-2),
        new BlockPos(2,0,2),new BlockPos(2,0,-2),new BlockPos(-2,0,2),new BlockPos(-2,0,-2),
        new BlockPos(3,0,0),new BlockPos(-3,0,0),new BlockPos(0,0,3),new BlockPos(0,0,-3),
        new BlockPos(3,0,3),new BlockPos(3,0,-3),new BlockPos(-3,0,3),new BlockPos(-3,0,-3));
    private MandrakeRuntime() {}

    public static final class TransientState {
        MandrakeRules.Phase phase=MandrakeRules.Phase.SEEDED;
        int phaseTicks,meleeDelay,unseenTicks,routeDelay,routeFailures,routeBackoff;
        UUID subject; ResourceKey<Level> subjectDimension,lastDimension; BlockPos anchor,escapeDestination; boolean subjectVisible,subjectRequired,escapeAbandon,escapeRouteActive,resettleRouteActive; Vec3 lastPosition;
        public MandrakeRules.Phase phase(){return phase;}
        public void resetForLoad(){phase=MandrakeRules.Phase.SEEDED;phaseTicks=meleeDelay=unseenTicks=routeDelay=routeFailures=routeBackoff=0;subject=null;subjectDimension=lastDimension=null;anchor=escapeDestination=null;lastPosition=null;subjectVisible=subjectRequired=escapeAbandon=escapeRouteActive=resettleRouteActive=false;}
    }
    public static final class Counters {
        public int aiTicks,cheapDecisions,rootedTicks,episodeStarts,episodeCancelsByReason;
        public int rawVisits,sightRays,wails,recipients,wailCooldownBlocks,nauseaApplications;
        public int attackerAttributions,attackerRejectionsByReason,attackerExpiries,meleeAttempts,meleeAccepted;
        public int pathRequests,pathsAccepted,pathFailures,pathBackoffs,navigationOverwrites,resettleEntries,resettleArrivals,reanchors;
        public int hazardObservationReads,safeCandidates,safeReads,safeEntityVisits,hazardRoutes,hazardEscapeSuccesses,unrootEvents,rerootEvents;
        public int tokensGranted,tokensDeferred,feedbackEmitted,feedbackSuppressed,sounds,particles;
        public int genericBehaviorDispatches,genericTacticalDispatches,genericAmbientDispatches,genericHazardDispatches;
        public int slownessApplications,poisonApplications,weaknessApplications,thornsDamageEvents,explosions,reinforcements;
        public int villagerConversions,drownedConversions,turtleEggBreaks,doorBreaks,babyStates,equipmentStates;
        public int blockEdits,chunkLoadRequests,entityCreationsOutsideBulbPath,stateKeys,stateBytes,stateMismatches,transientReplays;
        public int cancellations; public long serverTickNs;
    }

    public static void tick(MandrakeEntity mob,ServerLevel level){
        MandrakeState state=mob.mandrakeState();TransientState s=mob.mandrakeTransient();Counters c=mob.mandrakeCounters();c.aiTicks++;
        if(s.lastDimension!=null&&s.lastDimension!=level.dimension()){cancelEpisode(mob,CancellationReason.DIMENSION_CHANGE);s.anchor=mob.blockPosition();}
        else if(s.lastPosition!=null&&(!LivingRootsRules.rooted(s.phase)||s.subject!=null)&&s.lastPosition.distanceToSqr(mob.position())>64.0D){cancelEpisode(mob,CancellationReason.TELEPORT);s.anchor=mob.blockPosition();}
        s.lastDimension=level.dimension();s.lastPosition=mob.position();
        if(s.anchor==null)s.anchor=mob.blockPosition();
        final LivingEntity boundActor=subject(level,s.subject,s.subjectDimension);
        final CancellationReason actorCancellation=actorCancellationReason(boundActor);
        if(actorCancellation!=null){cancelEpisode(mob,actorCancellation);s.phase=MandrakeRules.Phase.SEEDED;s.anchor=mob.blockPosition();}
        state=new MandrakeState(1,LivingRootsRules.decrementLoaded(state.wailCooldownRemaining()),LivingRootsRules.decrementLoaded(state.episodeCooldownRemaining()));
        if(s.routeDelay>0)s.routeDelay--;if(s.routeBackoff>0)s.routeBackoff--;
        final boolean hazard=hazardActive(mob,level);if(hazard&&s.phase!=MandrakeRules.Phase.ESCAPE){cancelEpisode(mob,CancellationReason.HAZARD);s.phase=MandrakeRules.Phase.ESCAPE;c.unrootEvents++;}if(s.phase==MandrakeRules.Phase.ESCAPE){escape(mob,level,hazard);mob.setMandrakeState(state);return;}
        if(mob.consumeExtractionBorn()&&s.phase==MandrakeRules.Phase.SEEDED)disturb(mob,null);
        s.phaseTicks++;
        if(s.phase==MandrakeRules.Phase.DISTURBED&&s.phaseTicks>=MandrakeRules.TELEGRAPH_TICKS){
            if(state.wailCooldownRemaining()==0){s.phase=MandrakeRules.Phase.WAIL;boolean emitted=emitWail(mob,level);s.phase=MandrakeRules.afterWailToken(emitted);if(emitted)state=new MandrakeState(1,MandrakeRules.WAIL_COOLDOWN_TICKS,state.episodeCooldownRemaining());}
            else{s.phase=MandrakeRules.Phase.FLAIL;c.wailCooldownBlocks++;}if(s.phase==MandrakeRules.Phase.FLAIL)s.phaseTicks=0;
        }else if(s.phase==MandrakeRules.Phase.WAIL){boolean emitted=emitWail(mob,level);s.phase=MandrakeRules.afterWailToken(emitted);if(emitted){state=new MandrakeState(1,MandrakeRules.WAIL_COOLDOWN_TICKS,state.episodeCooldownRemaining());s.phaseTicks=0;}}
        else if(s.phase==MandrakeRules.Phase.FLAIL){flail(mob,level);if(MandrakeRules.flailComplete(s.phaseTicks,s.subjectRequired,s.subject!=null)){s.phase=MandrakeRules.Phase.RESETTLE;s.phaseTicks=0;s.resettleRouteActive=false;c.resettleEntries++;stop(mob);}}
        else if(s.phase==MandrakeRules.Phase.RESETTLE)resettle(mob,level);
        else if(s.phase==MandrakeRules.Phase.SEEDED){c.rootedTicks++;stop(mob);}
        mob.setMandrakeState(state);
    }
    public static void disturb(MandrakeEntity mob,LivingEntity attacker){acceptedDamageAt(mob,attacker,attacker==null?0:attacker.tickCount);}
    public static void acceptedDamage(MandrakeEntity mob,LivingEntity attacker){acceptedDamageAt(mob,attacker,attacker.tickCount);}
    static void acceptedDamageAt(MandrakeEntity mob,LivingEntity attacker,int attackerTick){if(attacker!=null&&!MandrakeRules.freshAttribution((long)attacker.tickCount-attackerTick)){mob.mandrakeCounters().attackerExpiries++;return;}if(attacker!=null&&!eligible(mob,attacker)){mob.mandrakeCounters().attackerRejectionsByReason++;return;}TransientState s=mob.mandrakeTransient();MandrakeRules.Phase prior=s.phase;if(!MandrakeRules.mayBindDamageSubject(prior)){mob.mandrakeCounters().attackerRejectionsByReason++;return;}if(MandrakeRules.startsDamageEpisode(prior)){if(prior==MandrakeRules.Phase.RESETTLE){s.resettleRouteActive=false;s.routeDelay=s.routeFailures=s.routeBackoff=0;stop(mob);}s.phase=MandrakeRules.afterAcceptedDamage(prior);s.phaseTicks=0;mob.mandrakeCounters().episodeStarts++;mob.setMandrakeState(new MandrakeState(1,mob.mandrakeState().wailCooldownRemaining(),MandrakeRules.EPISODE_TICKS));}s.subject=attacker==null?null:attacker.getUUID();s.subjectDimension=attacker==null?null:attacker.level().dimension();s.subjectRequired=attacker!=null;s.subjectVisible=false;if(attacker!=null)mob.mandrakeCounters().attackerAttributions++;}
    private static boolean emitWail(MandrakeEntity mob,ServerLevel level){var quota=LivingRootsRules.quota(level);if(!quota.wail()){mob.mandrakeCounters().tokensDeferred++;return false;}mob.mandrakeCounters().tokensGranted++;var list=new ArrayList<LivingEntity>();level.getEntities(EntityTypeTest.forClass(LivingEntity.class),mob.getBoundingBox().inflate(MandrakeRules.WAIL_RADIUS),candidate->true,list,MandrakeRules.RAW_CANDIDATE_CAP);if(!quota.entities(list.size())){mob.mandrakeCounters().tokensDeferred++;return false;}mob.mandrakeCounters().rawVisits+=list.size();for(LivingEntity candidate:list.stream().filter(e->eligible(mob,e)).sorted(Comparator.<LivingEntity>comparingDouble(mob::distanceToSqr).thenComparing(LivingEntity::getUUID)).limit(MandrakeRules.WAIL_SIGHT_CAP).toList()){if(!quota.sight())break;mob.mandrakeCounters().sightRays++;if(mob.hasLineOfSight(candidate)){candidate.addEffect(new MobEffectInstance(MobEffects.NAUSEA,MandrakeRules.WAIL_DURATION_TICKS,MandrakeRules.WAIL_AMPLIFIER));mob.mandrakeCounters().recipients++;mob.mandrakeCounters().nauseaApplications++;}}feedback(mob,level);mob.mandrakeCounters().wails++;return true;}
    private static boolean eligible(MandrakeEntity mob,LivingEntity e){return e!=mob&&e.isAlive()&&!e.isRemoved()&&e.level()==mob.level()&&!(e instanceof MandrakeEntity)&&!(e instanceof DreamrootEntity)&&mob.canAttack(e)&&(!(e instanceof Player p)||!p.isCreative()&&!p.isSpectator()&&!p.isSleeping())&&(!(e instanceof net.minecraft.world.entity.npc.villager.AbstractVillager villager)||villager.getTradingPlayer()==null)&&!(e instanceof net.minecraft.world.entity.npc.villager.Villager villager&&(villager.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.BREED_TARGET)||villager.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.IS_PANICKING)||villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC)))&&!(e instanceof net.minecraft.world.entity.raid.Raider raider&&raider.getCurrentRaid()!=null);}
    private static void flail(MandrakeEntity mob,ServerLevel level){TransientState s=mob.mandrakeTransient();LivingEntity target=subject(level,s.subject,s.subjectDimension);if(target==null){if(s.subjectRequired)s.subject=null;return;}if(!eligible(mob,target)||mob.distanceToSqr(target)>256){s.subject=null;return;}if(s.meleeDelay>0)s.meleeDelay--;if((mob.tickCount%10)==0){var q=LivingRootsRules.quota(level);if(q.sight()){mob.mandrakeCounters().sightRays++;s.subjectVisible=mob.hasLineOfSight(target);if(s.subjectVisible)s.unseenTicks=0;else s.unseenTicks+=10;}if(s.unseenTicks>=40){s.subject=null;return;}}if(mob.distanceToSqr(target)<=4&&s.subjectVisible){if(s.meleeDelay==0&&LivingRootsRules.quota(level).melee()){mob.mandrakeCounters().meleeAttempts++;if(mob.doHurtTarget(level,target))mob.mandrakeCounters().meleeAccepted++;s.meleeDelay=MandrakeRules.MELEE_CADENCE_TICKS;}}else requestPath(mob,level,target.blockPosition(),1.0);}
    private static void resettle(MandrakeEntity mob,ServerLevel level){TransientState s=mob.mandrakeTransient();if(mob.distanceToSqr(s.anchor.getX()+.5,s.anchor.getY(),s.anchor.getZ()+.5)<=MandrakeRules.ARRIVAL_DISTANCE_SQUARED){mob.mandrakeCounters().resettleArrivals++;rootHere(mob);return;}if(s.phaseTicks>=MandrakeRules.EPISODE_TICKS){rootHere(mob);return;}if(s.resettleRouteActive){if(!mob.getNavigation().isDone())return;routeFailed(mob);if(s.routeFailures>=MandrakeRules.MAX_ROUTE_FAILURES){rootHere(mob);return;}}if(s.routeDelay>0||s.routeBackoff>0)return;if(requestPath(mob,level,s.anchor,1.0)){s.resettleRouteActive=true;return;}if(s.routeFailures>=MandrakeRules.MAX_ROUTE_FAILURES)rootHere(mob);}
    private static void rootHere(MandrakeEntity mob){TransientState s=mob.mandrakeTransient();s.phase=MandrakeRules.Phase.SEEDED;s.phaseTicks=s.routeFailures=s.routeDelay=0;s.subject=null;s.subjectDimension=null;s.subjectVisible=s.subjectRequired=s.escapeAbandon=s.escapeRouteActive=s.resettleRouteActive=false;s.escapeDestination=null;s.anchor=mob.blockPosition();mob.mandrakeCounters().reanchors++;stop(mob);}
    private static boolean requestPath(MandrakeEntity mob,ServerLevel level,BlockPos destination,double speed){TransientState s=mob.mandrakeTransient();if(s.routeDelay>0||s.routeBackoff>0)return false;var quota=LivingRootsRules.quota(level);if(!quota.path()){mob.mandrakeCounters().tokensDeferred++;return false;}s.routeDelay=MandrakeRules.ROUTE_CADENCE_TICKS;mob.mandrakeCounters().pathRequests++;var path=mob.getNavigation().createPath(destination,0);boolean accepted=path!=null&&path.canReach()&&mob.getNavigation().moveTo(path,speed);if(accepted){mob.mandrakeCounters().pathsAccepted++;return true;}routeFailed(mob);return false;}
    private static boolean hazardActive(MandrakeEntity mob,ServerLevel level){if(mob.isOnFire()||mob.isInLava())return true;if(mob.hurtTime<=0||!LivingRootsRules.staggeredDue(mob.tickCount,mob.getId(),MandrakeRules.HAZARD_CADENCE_TICKS))return false;var q=LivingRootsRules.quota(level);var observation=LivingRootsRules.observeHazard(level,mob,MandrakeRules.HAZARD_FOOTPRINT_READ_CAP,MandrakeRuntime::unsafe);if(!q.reads(observation.actualReads()))return false;mob.mandrakeCounters().hazardObservationReads+=observation.actualReads();return observation.safe();}
    private static void escape(MandrakeEntity mob,ServerLevel level,boolean hazard){TransientState s=mob.mandrakeTransient();if(s.escapeDestination!=null){if(mob.distanceToSqr(s.escapeDestination.getX()+.5,s.escapeDestination.getY(),s.escapeDestination.getZ()+.5)<=MandrakeRules.ARRIVAL_DISTANCE_SQUARED){int[] visits={0},reads={0};if(safe(mob,level,s.escapeDestination,visits,reads)){s.escapeDestination=null;s.escapeRouteActive=false;s.routeFailures=s.routeBackoff=0;s.escapeAbandon=false;s.phase=MandrakeRules.Phase.SEEDED;s.anchor=mob.blockPosition();mob.mandrakeCounters().hazardEscapeSuccesses++;mob.mandrakeCounters().rerootEvents++;mob.mandrakeCounters().reanchors++;stop(mob);}return;}if(s.escapeRouteActive){if(!mob.getNavigation().isDone())return;routeFailed(mob);}if(s.escapeDestination!=null){if(s.routeDelay>0)return;if(requestPath(mob,level,s.escapeDestination,1.35)){s.escapeRouteActive=true;return;}return;}}if(s.routeBackoff>0)return;if(s.escapeAbandon){s.escapeAbandon=false;s.phase=MandrakeRules.Phase.SEEDED;s.anchor=mob.blockPosition();mob.mandrakeCounters().rerootEvents++;mob.mandrakeCounters().reanchors++;stop(mob);return;}if(s.routeDelay>0)return;int[] searchVisits={0},searchReads={0};for(BlockPos offset:SAFE_OFFSETS){mob.mandrakeCounters().safeCandidates++;BlockPos candidate=mob.blockPosition().offset(offset);if(safe(mob,level,candidate,searchVisits,searchReads)){s.escapeDestination=candidate;if(requestPath(mob,level,candidate,1.35)){s.escapeRouteActive=true;mob.mandrakeCounters().hazardRoutes++;return;}return;}}if(s.routeFailures==0){s.escapeAbandon=true;s.routeBackoff=MandrakeRules.ROUTE_BACKOFF_TICKS;mob.mandrakeCounters().pathBackoffs++;}}
    private static void routeFailed(MandrakeEntity mob){TransientState s=mob.mandrakeTransient();s.escapeRouteActive=s.resettleRouteActive=false;s.routeFailures++;mob.mandrakeCounters().pathFailures++;if(MandrakeRules.clearEscapeDestination(s.routeFailures)){s.escapeDestination=null;s.escapeAbandon=true;s.routeBackoff=MandrakeRules.ROUTE_BACKOFF_TICKS;mob.mandrakeCounters().pathBackoffs++;}}
    private static boolean safe(MandrakeEntity mob,ServerLevel level,BlockPos pos,int[] searchVisits,int[] searchReads){AABB box=mob.getBoundingBox().move(pos.getX()+.5-mob.getX(),pos.getY()-mob.getY(),pos.getZ()+.5-mob.getZ());var quota=LivingRootsRules.quota(level);int remaining=MandrakeRules.SAFE_READ_CAP-searchReads[0];if(remaining<=0)return false;var observation=LivingRootsRules.observeSafeDestination(level,mob,pos,remaining,MandrakeRuntime::unsafe);if(!quota.reads(observation.actualReads()))return false;searchReads[0]+=observation.actualReads();mob.mandrakeCounters().safeReads+=observation.actualReads();if(!observation.safe())return false;var occupants=new ArrayList<net.minecraft.world.entity.Entity>();int cap=Math.min(MandrakeRules.OCCUPANCY_VISITS_PER_CANDIDATE,MandrakeRules.OCCUPANCY_VISITS_PER_SEARCH-searchVisits[0]);if(cap<=0)return false;level.getEntities(EntityTypeTest.forClass(net.minecraft.world.entity.Entity.class),box,e->true,occupants,cap);if(!quota.occupancy(occupants.size()))return false;searchVisits[0]+=occupants.size();mob.mandrakeCounters().safeEntityVisits+=occupants.size();return occupants.stream().noneMatch(e->e!=mob&&e.isAlive()&&e.canBeCollidedWith(mob));}
    private static boolean unsafe(net.minecraft.world.level.block.state.BlockState state){return state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)||state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE)||state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.CACTUS)||state.is(Blocks.SWEET_BERRY_BUSH);}
    private static LivingEntity subject(ServerLevel level,UUID id,ResourceKey<Level> dimension){if(dimension!=null&&dimension!=level.dimension())return null;var e=id==null?null:level.getEntity(id);return e instanceof LivingEntity living&&living.isAlive()?living:null;}
    private static CancellationReason actorCancellationReason(LivingEntity actor){
        if(actor instanceof Player player&&player.isSleeping())return CancellationReason.SLEEP;
        if(actor instanceof net.minecraft.world.entity.npc.villager.AbstractVillager villager&&villager.getTradingPlayer()!=null)return CancellationReason.TRADE;
        if(actor instanceof net.minecraft.world.entity.animal.Animal animal&&animal.isInLove())return CancellationReason.BREEDING;
        if(actor instanceof net.minecraft.world.entity.npc.villager.Villager villager){
            if(villager.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.BREED_TARGET))return CancellationReason.BREEDING;
            if(villager.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.IS_PANICKING)
                ||villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC))return CancellationReason.PANIC;
        }
        if(actor instanceof net.minecraft.world.entity.raid.Raider raider&&raider.getCurrentRaid()!=null)return CancellationReason.RAID;
        return null;
    }
    private static void feedback(MandrakeEntity mob,ServerLevel level){Counters c=mob.mandrakeCounters();if(!LivingRootsRules.quota(level).feedback()){c.feedbackSuppressed++;return;}level.playSound(null,mob.blockPosition(),SoundEvents.GRASS_STEP,SoundSource.HOSTILE,1.0F,0.7F);level.sendParticles(ParticleTypes.ANGRY_VILLAGER,mob.getX(),mob.getEyeY(),mob.getZ(),8,.4,.4,.4,0);c.feedbackEmitted++;c.sounds++;c.particles+=8;}
    private static void cancelEpisode(MandrakeEntity mob,CancellationReason reason){TransientState s=mob.mandrakeTransient();s.subject=null;s.subjectDimension=null;s.subjectVisible=s.subjectRequired=s.escapeAbandon=s.escapeRouteActive=s.resettleRouteActive=false;s.escapeDestination=null;s.phaseTicks=s.meleeDelay=s.unseenTicks=s.routeDelay=s.routeFailures=s.routeBackoff=0;s.anchor=null;mob.mandrakeCounters().episodeCancelsByReason++;mob.mandrakeCounters().cancellations++;stop(mob);}
    public static void cancel(MandrakeEntity mob){cancelEpisode(mob,CancellationReason.REMOVAL);mob.mandrakeTransient().resetForLoad();}
    private static void stop(MandrakeEntity mob){mob.getNavigation().stop();mob.getMoveControl().setWait();var v=mob.getDeltaMovement();mob.setDeltaMovement(0,v.y,0);mob.setTarget(null);}
}
