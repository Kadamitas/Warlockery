package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Optional;
import java.util.ArrayList;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

public final class EntRuntime {
    private static boolean registered;
    private static final WeakHashMap<ServerLevel, EntRules.Quota> QUOTAS=new WeakHashMap<>();

    private EntRuntime() {
    }

    public static void registerEvents() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> handleLogBreak(event));
    }

    public static void handleLogBreak(final BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
            || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getState().is(BlockTags.LOGS)) notifyFelling(level, player, event.getPos());
        if (!event.getState().is(WarlockeryTags.Blocks.ENT_SPAWNING_LOGS)) return;
        final int neighboringLogs = neighboringLogCount(level, event.getPos());
        if (!EntRules.shouldSpawn(neighboringLogs, level.getRandom().nextDouble())) {
            return;
        }
        findSpawnPosition(level, event.getPos()).ifPresent(position -> spawn(level, player, event.getPos(), position));
    }

    static int neighboringLogCount(final ServerLevel level, final BlockPos brokenLog) {
        return (int) BlockPos.betweenClosedStream(brokenLog.offset(-1, -1, -1), brokenLog.offset(1, 1, 1))
            .filter(position -> !position.equals(brokenLog))
            .filter(position -> level.getBlockState(position).is(WarlockeryTags.Blocks.ENT_SPAWNING_LOGS))
            .count();
    }

    private static Optional<BlockPos> findSpawnPosition(final ServerLevel level, final BlockPos origin) {
        for (int attempt = 0; attempt < 12; attempt++) {
            final int x = EntRules.horizontalOffset(level.getRandom().nextInt(9), level.getRandom().nextBoolean());
            final int z = EntRules.horizontalOffset(level.getRandom().nextInt(9), level.getRandom().nextBoolean());
            for (int y = 0; y <= EntRules.MAX_VERTICAL_SPAWN_OFFSET; y++) {
                final BlockPos candidate = origin.offset(x, EntRules.verticalOffset(y), z);
                if (canSpawnAt(level, candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean canSpawnAt(final ServerLevel level, final BlockPos position) {
        if (!level.getWorldBorder().isWithinBounds(position)
            || !level.getBlockState(position.below()).isFaceSturdy(level, position.below(), Direction.UP)) {
            return false;
        }
        for (int height = 0; height < 4; height++) {
            if (!level.getBlockState(position.above(height)).getCollisionShape(level, position.above(height)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void spawn(
        final ServerLevel level,
        final ServerPlayer player,
        final BlockPos origin,
        final BlockPos position
    ) {
        final EntEntity ent = ModEntities.ENT.get().spawn(level, position, EntitySpawnReason.EVENT);
        if (ent == null) {
            return;
        }
        ent.setPersistenceRequired();
        seedFromFelling(ent, player, origin);
        level.sendParticles(
            ParticleTypes.WITCH,
            position.getX() + 0.5D,
            position.getY() + 1.5D,
            position.getZ() + 0.5D,
            36,
            1.2D,
            1.5D,
            1.2D,
            0.04D
        );
        level.sendParticles(
            ParticleTypes.SMOKE,
            origin.getX() + 0.5D,
            origin.getY() + 0.5D,
            origin.getZ() + 0.5D,
            18,
            0.5D,
            0.8D,
            0.5D,
            0.02D
        );
        level.playSound(null, origin, SoundEvents.SKELETON_HORSE_DEATH, SoundSource.HOSTILE, 0.8F, 0.7F);
        level.playSound(null, position, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.HOSTILE, 1.0F, 0.55F);
    }

    public static void tick(final EntEntity ent, final ServerLevel level) {
        final TransientState scratch = ent.entTransient();
        EntState state = ent.entState().reconcileAnchor(ent.getBlockX(), ent.getBlockY(), ent.getBlockZ());
        state = state.withCooldowns(EntRules.decrementLoaded(state.warnCooldownRemaining()),
            EntRules.decrementLoaded(state.tendCooldownRemaining()));
        scratch.age++;
        if(scratch.evidenceRemaining>0)scratch.evidenceRemaining--;
        if(scratch.routeBackoff>0)scratch.routeBackoff--;
        ent.entCounters().aiTicks++;
        boolean teleported=scratch.initialized && ent.position().distanceToSqr(scratch.lastPosition)>64;
        String dimension=level.dimension().identifier().toString();
        boolean dimensionChanged=scratch.dimension!=null&&!scratch.dimension.equals(dimension);
        scratch.initialized=true;scratch.lastPosition=ent.position();scratch.dimension=dimension;
        if(teleported)cancel(ent,EntRules.Phase.SETTLE);
        if(dimensionChanged){state=new EntState(1,1,ent.getBlockX(),ent.getBlockY(),ent.getBlockZ(),state.grievance(),state.warnCooldownRemaining(),state.tendCooldownRemaining());cancel(ent,EntRules.Phase.WARDING);}
        if (ent.isOnFire() || ent.isInLava() || footprintHazard(ent,level)) {
            if(scratch.phase!=EntRules.Phase.ESCAPE)cancel(ent, EntRules.Phase.ESCAPE);
            escapeHazard(ent,level,scratch);
            ent.setEntState(state);
            return;
        } else if(scratch.phase==EntRules.Phase.ESCAPE){cancel(ent,EntRules.Phase.SETTLE);}
        if (scratch.phase == EntRules.Phase.ROUSED && scratch.age >= EntRules.ORIENTATION_TICKS) {
            LivingEntity offender = resolve(ent, scratch.pending);
            boolean visible = offender != null && scratch.evidenceRemaining>0 && subjectLegal(ent,offender,state) && sight(ent,offender,level);
            EntRules.Phase oriented = EntRules.afterOrientation(state.grievance(), state.warnCooldownRemaining(), visible);
            if (oriented == EntRules.Phase.WARN) {
                var warning = EntRules.warningTransition(quota(level).tryWarning(level.getServer().isSameThread()), state.warnCooldownRemaining());
                if (warning.emitted()) {
                    scratch.phase = warning.phase();
                    scratch.age = 0;
                    state = state.withCooldowns(warning.cooldown(), state.tendCooldownRemaining());
                    ent.entCounters().warnings++;
                    feedback(ent,level);
                } else ent.entCounters().tokensDeferred++;
            } else {
                scratch.phase = oriented;
                scratch.age = 0;
            }
            if (scratch.phase == EntRules.Phase.STRIKE) {
                scratch.subject = scratch.pending;
                ent.entCounters().subjectsBound++;
            }
        } else if (scratch.phase == EntRules.Phase.WARN && EntRules.warningExpired(scratch.age)) {
            cancel(ent, EntRules.Phase.SETTLE);
        } else if (scratch.phase == EntRules.Phase.STRIKE) {
            LivingEntity subject = resolve(ent, scratch.subject);
            boolean legal=subjectLegal(ent,subject,state);
            if(subject!=null&&EntRules.staggeredDue(ent.tickCount,ent.getId(),10)){if(sight(ent,subject,level))scratch.sightLoss=0;else scratch.sightLoss+=10;}
            if (!legal || scratch.sightLoss>=40 || EntRules.strikeExpired(scratch.age)) cancel(ent, EntRules.Phase.SETTLE);
            else if(ent.distanceToSqr(subject)<=ent.getBbWidth()*ent.getBbWidth()+subject.getBbWidth()&&sight(ent,subject,level)
                && EntRules.pathDue(ent.tickCount,ent.getId())&&quota(level).tryMelee(level.getServer().isSameThread())){ent.doHurtTarget(level,subject);ent.entCounters().meleeAttempts++;}
            else if (EntRules.pathDue(ent.tickCount, ent.getId())&&scratch.routeBackoff==0&&quota(level).tryPath(level.getServer().isSameThread())) {int route=requestPath(ent,subject.getX(),subject.getY(),subject.getZ(),scratch,false);if(route==2){cancel(ent,EntRules.Phase.SETTLE);scratch.routeBackoff=EntRules.ROUTE_BACKOFF_TICKS;}}
        } else if (scratch.phase == EntRules.Phase.SETTLE) {
            if (ent.distanceToSqr(state.anchorX()+.5,state.anchorY(),state.anchorZ()+.5)<=4) cancel(ent, EntRules.Phase.WARDING);
            else if (EntRules.settleExpired(scratch.age)) { state=state.reanchored(ent.getBlockX(),ent.getBlockY(),ent.getBlockZ());ent.entCounters().reanchors++;cancel(ent, EntRules.Phase.WARDING); }
            else if (EntRules.pathDue(ent.tickCount,ent.getId())&&scratch.routeBackoff==0&&quota(level).tryPath(level.getServer().isSameThread())) {int route=requestPath(ent,state.anchorX()+.5,state.anchorY(),state.anchorZ()+.5,scratch,true);if(route==1)state=state.reanchored(ent.getBlockX(),ent.getBlockY(),ent.getBlockZ());}
        } else if (scratch.phase == EntRules.Phase.TEND) {
            boolean succeeded=AmbientActivityRuntime.executeNow(ent,level,ArcaneCreature.CreatureKind.ENT,AmbientActivityProfile.ActivityType.GROVE_TENDING);
            if(succeeded){var profile=AmbientActivityProfile.forType(AmbientActivityProfile.ActivityType.GROVE_TENDING);state=state.withCooldowns(state.warnCooldownRemaining(),profile.cooldownTicks());ent.entCounters().tendSuccesses++;}
            scratch.phase=EntRules.Phase.WARDING;
            cancel(ent,EntRules.Phase.WARDING);
        } else if (scratch.phase == EntRules.Phase.WARDING && EntRules.staggeredDue(ent.tickCount,ent.getId(),100)) state=state.withGrievance(EntRules.decayGrievance(state.grievance(),100));
        if (scratch.phase==EntRules.Phase.WARDING && state.tendCooldownRemaining()==0) {
            var profile=AmbientActivityProfile.forType(AmbientActivityProfile.ActivityType.GROVE_TENDING);
            if (EntRules.staggeredDue(ent.tickCount,ent.getId(),profile.checkIntervalTicks())
                && ent.getRandom().nextInt(profile.chanceDenominator())==0 && quota(level).tryTendJob(level.getServer().isSameThread())) {
                scratch.phase=EntRules.Phase.TEND;
                scratch.age=0;
                ent.entCounters().tendJobs++;
            }
        }
        ent.setEntState(state);
    }

    public static void afterHurt(final EntEntity ent, final LivingEntity attacker) {
        if(!(ent.level() instanceof ServerLevel level))return;
        preemptTending(ent);
        if(!EntRules.reactionAllowed(true,0,subjectCandidateLegal(ent,attacker,ent.entState()),sight(ent,attacker,level)))return;
        ent.setEntState(ent.entState().withGrievance(EntRules.addDamageGrievance(ent.entState().grievance())));
        if(ent.entTransient().phase==EntRules.Phase.WARN&&attacker.getUUID().equals(ent.entTransient().pending)){ent.entTransient().phase=EntRules.Phase.STRIKE;ent.entTransient().subject=attacker.getUUID();ent.entTransient().age=0;ent.entCounters().subjectsBound++;}
        else rouse(ent, attacker); ent.entCounters().attributions++;
    }

    public static void seedFromFelling(final EntEntity ent, final ServerPlayer player, final BlockPos origin) {
        EntState old=ent.entState(); ent.setEntState(new EntState(1,1,origin.getX(),origin.getY(),origin.getZ(),20,old.warnCooldownRemaining(),old.tendCooldownRemaining())); rouse(ent,player);
    }

    private static void notifyFelling(ServerLevel level, ServerPlayer player, BlockPos broken) {
        EntRules.Quota quota=quota(level);boolean thread=level.getServer().isSameThread();if(!quota.tryNoticeScan(thread))return;
        java.util.ArrayList<EntEntity> raw=new java.util.ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(EntEntity.class),new net.minecraft.world.phys.AABB(broken).inflate(16),_ -> true,raw,4);
        for(int index=0;index<raw.size();index++)if(!quota.tryRawEntityVisit(thread))return;
        raw.stream().filter(ent -> EntRules.insideClaim(ent.entState().anchorX(),ent.entState().anchorY(),ent.entState().anchorZ(),broken.getX(),broken.getY(),broken.getZ()))
            .sorted(java.util.Comparator.comparingDouble((EntEntity ent)->ent.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(broken))).thenComparing(EntEntity::getUUID)).limit(2)
            .forEach(ent->{if(!quota.tryNotice(thread))return;ent.setEntState(ent.entState().withGrievance(EntRules.addFellingGrievance(ent.entState().grievance())));rouse(ent,player);ent.entCounters().noticesReceived++;});
    }

    private static void rouse(EntEntity ent, LivingEntity offender){preemptTending(ent);TransientState s=ent.entTransient();if(s.phase==EntRules.Phase.WARN&&offender.getUUID().equals(s.pending)){if(ent.level() instanceof ServerLevel level&&EntRules.reactionAllowed(true,0,subjectLegal(ent,offender,ent.entState()),sight(ent,offender,level))){s.phase=EntRules.Phase.STRIKE;s.subject=s.pending;s.age=0;ent.entCounters().subjectsBound++;}else cancel(ent,EntRules.Phase.SETTLE);return;}s.phase=EntRules.Phase.ROUSED;s.pending=offender.getUUID();s.evidenceRemaining=40;s.age=0;ent.entCounters().episodesStarted++;}
    private static void preemptTending(EntEntity ent){if(ent.entTransient().phase==EntRules.Phase.TEND)cancel(ent,EntRules.Phase.WARDING);}
    private static LivingEntity resolve(EntEntity ent,UUID id){if(id==null)return null;var entity=((ServerLevel)ent.level()).getEntity(id);return entity instanceof LivingEntity living&&living.isAlive()&&!living.isInvulnerable()?living:null;}
    public static void cancel(EntEntity ent,EntRules.Phase next){TransientState s=ent.entTransient();if(s.phase==EntRules.Phase.TEND&&next!=EntRules.Phase.TEND)ent.entCounters().tendPreemptions++;s.clear();s.phase=next;ent.setTarget(null);ent.getNavigation().stop();ent.getMoveControl().setWait();var d=ent.getDeltaMovement();ent.setDeltaMovement(0,d.y,0);}

    private static boolean sight(EntEntity ent,LivingEntity target,ServerLevel level){return quota(level).trySightRay(level.getServer().isSameThread())&&ent.hasLineOfSight(target);}
    private static boolean subjectCandidateLegal(EntEntity ent,LivingEntity subject,EntState state){if(subject==null)return false;var brain=subject.getBrain();boolean special=subject.isSleeping()||subject instanceof Villager villager&&villager.isTrading();boolean playerLegal=!(subject instanceof Player p)||!p.isCreative()&&!p.isSpectator();boolean raid=!brain.isActive(Activity.RAID)&&!brain.isActive(Activity.PRE_RAID);boolean panic=!brain.isActive(Activity.PANIC)&&!brain.hasMemoryValue(MemoryModuleType.IS_PANICKING);boolean breeding=!(subject instanceof Animal animal&&animal.isInLove())&&!brain.hasMemoryValue(MemoryModuleType.BREED_TARGET);return EntRules.subjectLegal(subject.isAlive()&&!subject.isRemoved(),subject.level()==ent.level(),subject!=ent&&!(subject instanceof EntEntity),!special,playerLegal,!subject.isInvulnerable(),raid,panic,breeding)&&EntRules.insideLeash(state.anchorX(),state.anchorY(),state.anchorZ(),subject.getX(),subject.getY(),subject.getZ())&&ent.distanceToSqr(subject)<=576;}
    private static boolean subjectLegal(EntEntity ent,LivingEntity subject,EntState state){return subjectCandidateLegal(ent,subject,state)&&state.grievance()>=20;}
    private static int requestPath(EntEntity ent,double x,double y,double z,TransientState s,boolean allowReanchor){ent.entCounters().pathRequests++;var path=ent.getNavigation().createPath(x,y,z,0);if(path!=null&&path.canReach()&&ent.getNavigation().moveTo(path,1)){s.routeFailures=0;ent.entCounters().pathsAccepted++;return 0;}ent.entCounters().pathsRejected++;EntRules.RouteResolution failure=allowReanchor?EntRules.settleRouteFailure(s.routeFailures):EntRules.strikeRouteFailure(s.routeFailures);s.routeFailures=failure.failures();s.routeBackoff=failure.backoff();if(failure.reanchor()){ent.getNavigation().stop();ent.entCounters().reanchors++;return 1;}return failure.phase()==EntRules.Phase.SETTLE?2:0;}
    private static boolean footprintHazard(EntEntity ent,ServerLevel level){
        if(!EntRules.staggeredDue(ent.tickCount,ent.getId(),20))return false;
        var halo=ent.getBoundingBox().inflate(1);
        HaloReadCache cache=new HaloReadCache(level,halo,128);
        if(!cache.admit()||!cache.haloLoaded())return false;
        for(BlockPos p:BlockPos.betweenClosed(cache.min,cache.max)){
            var state=cache.getBlockState(p);
            if(!cache.withinContract())return false;
            if(contactHazard(state))return true;
        }
        return false;
    }
    private static void escapeHazard(EntEntity ent,ServerLevel level,TransientState s){
        boolean thread=level.getServer().isSameThread();EntRules.Quota quota=quota(level);
        if(s.routeBackoff>0||!EntRules.pathDue(ent.tickCount,ent.getId())||!quota.tryExpensive(thread))return;
        int[][] offsets={{6,0,0},{-6,0,0},{0,0,6},{0,0,-6},{4,0,4},{-4,0,4},{4,0,-4},{-4,0,-4},{3,1,0},{-3,1,0},{0,1,3},{0,1,-3},{2,2,2},{-2,2,2},{2,2,-2},{-2,2,-2}};
        int occupancyVisits=0;ent.entCounters().safeCandidates=0;ent.entCounters().safeActualReads=0;ent.entCounters().safeEntityVisits=0;ent.entCounters().safeAdmissions=0;ent.entCounters().safeBorderAdmissions=0;ent.entCounters().safeChunkAdmissions=0;ent.entCounters().safeOccupancyAdmissions=0;
        for(int[] o:offsets){
            if(ent.entCounters().safeCandidates>=16||!quota.trySafeDestinationVisit(thread))break;
            ent.entCounters().safeCandidates++;
            BlockPos p=ent.blockPosition().offset(o[0],o[1],o[2]);
            var box=ent.getBoundingBox().move(p.getX()+.5-ent.getX(),p.getY()-ent.getY(),p.getZ()+.5-ent.getZ());
            var cache=new HaloReadCache(level,box.inflate(1),128-ent.entCounters().safeActualReads);
            ent.entCounters().safeAdmissions++;ent.entCounters().safeBorderAdmissions++;
            if(!level.getWorldBorder().isWithinBounds(box))continue;
            ent.entCounters().safeAdmissions++;ent.entCounters().safeChunkAdmissions++;
            if(!cache.haloLoaded())continue;
            if(!safeBody(ent,box,cache)){ent.entCounters().safeActualReads+=cache.actualReads();continue;}
            ent.entCounters().safeActualReads+=cache.actualReads();
            ArrayList<Entity> occupants=new ArrayList<>();int remaining=Math.min(8,32-occupancyVisits);if(remaining<=0)break;
            ent.entCounters().safeAdmissions++;ent.entCounters().safeOccupancyAdmissions++;
            level.getEntities(EntityTypeTest.forClass(Entity.class),box,e->e!=ent,occupants,remaining);
            occupancyVisits+=occupants.size();ent.entCounters().safeEntityVisits=occupancyVisits;for(int index=0;index<occupants.size();index++)if(!chargeRawVisit(level))return;
            if(occupants.stream().anyMatch(entity->entity.canBeCollidedWith(ent)))continue;
            if(quota.tryPath(thread)){requestPath(ent,p.getX()+.5,p.getY(),p.getZ()+.5,s,false);return;}
        }
        s.routeFailures=EntRules.routeFailuresAfter(s.routeFailures);if(EntRules.routeExhausted(s.routeFailures)){s.routeBackoff=100;s.routeFailures=0;}
    }
    private static boolean safeBody(EntEntity ent,net.minecraft.world.phys.AABB box,HaloReadCache cache){
        boolean supported=false;
        int floorY=net.minecraft.util.Mth.floor(box.minY-1.0E-6D);
        for(int x=net.minecraft.util.Mth.floor(box.minX);x<=net.minecraft.util.Mth.floor(box.maxX-1.0E-6D);x++)for(int z=net.minecraft.util.Mth.floor(box.minZ);z<=net.minecraft.util.Mth.floor(box.maxZ-1.0E-6D);z++){
            BlockPos floor=new BlockPos(x,floorY,z);var state=cache.getBlockState(floor);if(!cache.withinContract())return false;
            if(!state.getFluidState().isEmpty()||contactHazard(state))return false;
            supported|=state.isFaceSturdy(cache,floor,Direction.UP);
        }
        if(!supported)return false;
        var bodyShape=net.minecraft.world.phys.shapes.Shapes.create(box);
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX-1.0E-6D,box.maxY-1.0E-6D,box.maxZ-1.0E-6D))){
            var state=cache.getBlockState(pos);if(!cache.withinContract()||!state.getFluidState().isEmpty()||contactHazard(state))return false;
            var collision=state.getCollisionShape(cache,pos);if(!collision.isEmpty()&&net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(collision.move(pos.getX(),pos.getY(),pos.getZ()),bodyShape,net.minecraft.world.phys.shapes.BooleanOp.AND))return false;
        }
        return cache.withinContract();
    }
    private static boolean contactHazard(net.minecraft.world.level.block.state.BlockState state){return state.is(net.minecraft.tags.BlockTags.FIRE)||state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)||state.is(net.minecraft.world.level.block.Blocks.CACTUS)||state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH);}
    private static void feedback(EntEntity ent,ServerLevel level){if(!quota(level).tryFeedback(level.getServer().isSameThread()))return;level.playSound(null,ent.blockPosition(),SoundEvents.SKELETON_HORSE_DEATH,SoundSource.HOSTILE,.5f,.7f);level.sendParticles(ParticleTypes.SMOKE,ent.getX(),ent.getY()+1,ent.getZ(),8,.4,.5,.4,0);}

    static void writeState(ValueOutput output,EntState state){CompoundTag tag=new CompoundTag();tag.putInt("SchemaVersion",state.schema());tag.putByte("Anchored",(byte)state.anchored());tag.putInt("AnchorX",state.anchorX());tag.putInt("AnchorY",state.anchorY());tag.putInt("AnchorZ",state.anchorZ());tag.putInt("Grievance",state.grievance());tag.putInt("WarnCooldownRemaining",state.warnCooldownRemaining());tag.putInt("TendCooldownRemaining",state.tendCooldownRemaining());output.store("WarlockeryEntState",CompoundTag.CODEC,tag);}
    static EntState readState(ValueInput input,BlockPos position,int minY,int maxY){return input.read("WarlockeryEntState",CompoundTag.CODEC).map(tag->EntState.normalize(tag.getIntOr("SchemaVersion",0),tag.getByteOr("Anchored",(byte)0),tag.getIntOr("AnchorX",0),tag.getIntOr("AnchorY",0),tag.getIntOr("AnchorZ",0),tag.getIntOr("Grievance",0),tag.getIntOr("WarnCooldownRemaining",0),tag.getIntOr("TendCooldownRemaining",0),minY,maxY).reconcileAnchor(position.getX(),position.getY(),position.getZ())).orElseGet(()->EntState.fresh(position.getX(),position.getY(),position.getZ()));}

    public static final class TransientState { EntRules.Phase phase=EntRules.Phase.WARDING;UUID pending;UUID subject;int age,evidenceRemaining,sightLoss,routeFailures,routeBackoff;boolean initialized;net.minecraft.world.phys.Vec3 lastPosition=net.minecraft.world.phys.Vec3.ZERO;String dimension;void clear(){phase=EntRules.Phase.WARDING;pending=null;subject=null;age=evidenceRemaining=sightLoss=routeFailures=routeBackoff=0;}public EntRules.Phase phase(){return phase;} }
    static EntRules.Quota quota(ServerLevel level){int tick=level.getServer().getTickCount();EntRules.Quota q=QUOTAS.get(level);if(q==null||q.serverTick()!=tick){q=EntRules.Quota.fresh(tick);QUOTAS.put(level,q);}return q;}
    static boolean chargeReads(ServerLevel level,int amount){return quota(level).tryChargedReads(amount,level.getServer().isSameThread());}
    static boolean chargeRawVisit(ServerLevel level){return quota(level).tryRawEntityVisit(level.getServer().isSameThread());}
    static boolean chargeBlockEdit(ServerLevel level){return quota(level).tryBlockEdit(level.getServer().isSameThread());}
    static final class HaloReadCache implements net.minecraft.world.level.BlockGetter {
        private final ServerLevel level;final BlockPos min,max;private final int budget;private final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> cache=new HashMap<>();private int reads,admissions;private boolean rejected;
        HaloReadCache(ServerLevel level,net.minecraft.world.phys.AABB halo,int budget){this(level,BlockPos.containing(halo.minX,halo.minY,halo.minZ),BlockPos.containing(halo.maxX,halo.maxY,halo.maxZ),budget);}
        HaloReadCache(ServerLevel level,BlockPos min,BlockPos max,int budget){this.level=level;this.min=min.immutable();this.max=max.immutable();this.budget=Math.max(0,budget);}
        boolean admit(){if(admissions>=64){rejected=true;return false;}admissions++;return true;}int admissions(){return admissions;}
        boolean haloLoaded(){for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)if(!level.getChunkSource().hasChunk(x,z))return false;return true;}
        boolean withinContract(){return !rejected;}int actualReads(){return reads;}
        @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos position){if(position.getX()<min.getX()||position.getX()>max.getX()||position.getY()<min.getY()||position.getY()>max.getY()||position.getZ()<min.getZ()||position.getZ()>max.getZ()){rejected=true;return net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();}BlockPos key=position.immutable();var found=cache.get(key);if(found!=null)return found;if(reads>=budget||!chargeReads(level,1)){rejected=true;return net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();}reads++;var state=level.getBlockState(key);cache.put(key,state);return state;}
        @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos position){return getBlockState(position).getFluidState();}
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos position){rejected=true;return null;}
        @Override public int getHeight(){return level.getHeight();}@Override public int getMinY(){return level.getMinY();}
        net.minecraft.world.level.LevelReader levelReader(){return (net.minecraft.world.level.LevelReader)java.lang.reflect.Proxy.newProxyInstance(net.minecraft.world.level.LevelReader.class.getClassLoader(),new Class<?>[]{net.minecraft.world.level.LevelReader.class},(proxy,method,args)->switch(method.getName()){case "getBlockState"->getBlockState((BlockPos)args[0]);case "getFluidState"->getFluidState((BlockPos)args[0]);case "getBlockEntity"->getBlockEntity((BlockPos)args[0]);case "getHeight"->args==null||args.length==0?getHeight():reject(method.getReturnType());case "getMinY"->getMinY();case "toString"->"HaloReadCache.LevelReader";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->reject(method.getReturnType());});}
        private Object reject(Class<?> type){rejected=true;if(!type.isPrimitive())return null;if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;if(type==double.class)return 0D;if(type==float.class)return 0F;if(type==short.class)return (short)0;if(type==byte.class)return (byte)0;if(type==char.class)return (char)0;return null;}
    }
    public static final class Counters {
        public int aiTicks,episodesStarted,subjectsBound,noticesReceived,warnings,attributions,tendJobs,tendSuccesses,
            tendPreemptions,pathRequests,pathsAccepted,pathsRejected,meleeAttempts,reanchors,tokensDeferred,
            safeCandidates,safeActualReads,safeEntityVisits,safeAdmissions,safeBorderAdmissions,safeChunkAdmissions,
            safeOccupancyAdmissions,tendActualReads,tendItemVisits,tendAdmissions;
        public int aiTicks(){return aiTicks;} public int episodesStarted(){return episodesStarted;}
        public int subjectsBound(){return subjectsBound;} public int noticesReceived(){return noticesReceived;}
        public int warnings(){return warnings;} public int attributions(){return attributions;}
        public int tendJobs(){return tendJobs;} public int tendSuccesses(){return tendSuccesses;}
        public int tendPreemptions(){return tendPreemptions;} public int pathRequests(){return pathRequests;}
        public int pathsAccepted(){return pathsAccepted;} public int pathsRejected(){return pathsRejected;}
        public int meleeAttempts(){return meleeAttempts;} public int reanchors(){return reanchors;}
        public int tokensDeferred(){return tokensDeferred;}
    }
}
