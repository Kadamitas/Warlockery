package com.kadamitas.warlockery.entity;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;

public final class BrambleColossusRuntime {
    private static final Map<ServerLevel, Quota> QUOTAS = new WeakHashMap<>();
    private static final TagKey<Block> CONTACT_HAZARDS = TagKey.create(Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/contact_hazards"));
    private BrambleColossusRuntime() {}

    public static final class TransientState {
        private BrambleColossusRules.Phase phase = BrambleColossusRules.Phase.KEEPING;
        private UUID pending;
        private UUID subject;
        private int phaseTicks, unseenTicks, sightCooldown, pathCooldown, routeFailures, routeBackoff, hazardObservationCooldown, loadedTicks, returnTicks;
        private boolean observedContact, observedFire, returningFromHazard, retaliating;
        public BrambleColossusRules.Phase phase() { return phase; }
        public UUID pending() { return pending; }
        public UUID subject() { return subject; }
        public void reset() { phase = BrambleColossusRules.Phase.KEEPING; pending = null; subject = null; phaseTicks = unseenTicks = sightCooldown = pathCooldown = routeFailures = routeBackoff = hazardObservationCooldown = returnTicks = 0; observedContact = observedFire = returningFromHazard = false; }
        public void resetAfterLoad() { reset(); loadedTicks = 0; retaliating = false; }
        public void resetAfterDimensionChange() { resetAfterLoad(); }
    }
    public static final class Counters {
        public int sweeps, rawVisits, uuidResolves, sightRays, displays, sounds, particles, meleeAttempts, thornEvents, feedbackEvents, paths, blockReads, occupancyVisits, safeCandidates, hazardSearches, cancellations, deferred, genericDispatches, effects, blockEdits, entityCreations;
    }
    private static final class Quota {
        int tick = Integer.MIN_VALUE, expensive, paths, sweeps, visits, resolves, rays, reads, occupancy, displays, melee, thorns, feedback;
        void reset(int now) { if (tick != now) { tick=now; expensive=paths=sweeps=visits=resolves=rays=reads=occupancy=displays=melee=thorns=feedback=0; } }
    }
    private static boolean token(ServerLevel level, java.util.function.Predicate<Quota> claim) {
        if (!level.getServer().isSameThread()) return false;
        var quota = QUOTAS.computeIfAbsent(level, ignored -> new Quota()); quota.reset(level.getServer().getTickCount()); return claim.test(quota);
    }
    static boolean claimDisplayQuota(ServerLevel level) {
        return token(level, q -> q.displays < BrambleColossusRules.LEVEL_DISPLAY_LIMIT
            && q.feedback < BrambleColossusRules.LEVEL_FEEDBACK_LIMIT
            && ++q.displays > 0 && ++q.feedback > 0);
    }

    public static void tick(BrambleColossusEntity mob, ServerLevel level) {
        var state = mob.colossusState();
        if (!state.posted() || state.post().orElseThrow().distSqr(mob.blockPosition()) > BrambleColossusRules.CORRUPT_POST_DISTANCE * BrambleColossusRules.CORRUPT_POST_DISTANCE) { cancel(mob); state = state.postedAt(mob.blockPosition()); }
        state = state.tickCooldowns(); mob.setColossusState(state);
        var scratch = mob.colossusTransient();
        scratch.loadedTicks++;
        if (scratch.pathCooldown > 0) scratch.pathCooldown--; if (scratch.routeBackoff > 0) scratch.routeBackoff--; if (scratch.hazardObservationCooldown > 0) scratch.hazardObservationCooldown--;
        if (hazard(mob, level)) { withdraw(mob, level); return; }
        if (scratch.phase == BrambleColossusRules.Phase.WITHDRAW) { scratch.returningFromHazard = true; scratch.returnTicks = 0; scratch.phase = BrambleColossusRules.Phase.KEEPING; }
        if (scratch.returningFromHazard) { returnToPost(mob, level); return; }
        if (BrambleColossusRules.falterAt(state.nerve())) { falter(mob); recover(mob); returnToPost(mob, level); return; }
        if (BrambleColossusRules.staysFaltered(scratch.phase, state.nerve())) { recover(mob); returnToPost(mob, level); return; }
        if (scratch.phase == BrambleColossusRules.Phase.FALTER) { scratch.reset(); }
        switch (scratch.phase) {
            case MARK -> tickMark(mob, level);
            case DISPLAY -> tickDisplay(mob, level);
            case THRESH -> tickThresh(mob, level);
            case CIRCUIT -> tickCircuit(mob, level);
            default -> tickRoutine(mob, level);
        }
    }
    private static void tickRoutine(BrambleColossusEntity mob, ServerLevel level) {
        recover(mob); sustain(mob, level);
        if (BrambleColossusRules.shouldSweep(mob.tickCount, mob.getId())) sweep(mob, level);
        if (mob.colossusTransient().phase == BrambleColossusRules.Phase.KEEPING && BrambleColossusRules.shouldAlarm(mob.tickCount, mob.getId())) alarm(mob, level);
        if (mob.colossusTransient().phase != BrambleColossusRules.Phase.KEEPING) return;
        if (TreefydState.wandering(mob) && mob.colossusState().circuitCooldownRemaining() == 0 && BrambleColossusRules.shouldPulse(mob.tickCount, mob.getId(), 200, 0)) startCircuit(mob, level);
    }
    private static void sweep(BrambleColossusEntity mob, ServerLevel level) {
        if (!token(level, q -> q.expensive < BrambleColossusRules.LEVEL_EXPENSIVE_LIMIT && q.sweeps < BrambleColossusRules.LEVEL_SWEEP_LIMIT && q.visits + 6 <= BrambleColossusRules.LEVEL_RAW_VISIT_LIMIT && ++q.expensive > 0 && ++q.sweeps > 0 && (q.visits += 6) > 0)) { mob.colossusCounters().deferred++; return; }
        BlockPos post = mob.colossusState().post().orElse(mob.blockPosition());
        AABB box = new AABB(post).inflate(10, 5, 10);
        var candidates = new ArrayList<LivingEntity>(6);
        com.kadamitas.warlockery.entity.BoundedEntityQuery.visit(level, EntityTypeTest.forClass(LivingEntity.class), box, e -> {
            mob.colossusCounters().rawVisits++; candidates.add(e);
            return candidates.size() >= 6 ? AbortableIterationConsumer.Continuation.ABORT : AbortableIterationConsumer.Continuation.CONTINUE;
        });
        candidates.sort(Comparator.<LivingEntity>comparingDouble(mob::distanceToSqr).thenComparing(LivingEntity::getUUID));
        int rays = 0;
        for (LivingEntity candidate : candidates) {
            if (!insideHeld(mob, candidate) || !legalSubjectWithoutSight(mob, candidate) || rays++ >= 2 || !sight(mob, candidate, level)) continue;
            mob.colossusTransient().pending = candidate.getUUID(); mob.colossusTransient().phase = BrambleColossusRules.Phase.MARK; mob.colossusTransient().phaseTicks = 20; cancelMovement(mob); break;
        }
        mob.colossusCounters().sweeps++;
    }
    private static void alarm(BrambleColossusEntity mob, ServerLevel level) {
        if (!BrambleColossusRules.mayBindAt(mob.colossusState().nerve())) return;
        var ids = new UUID[TreefydState.MAX_ALLOWLIST + 1]; ids[0] = CreatureBehaviorState.owner(mob).orElse(null);
        for (int i = 0; i < TreefydState.MAX_ALLOWLIST; i++) ids[i + 1] = TreefydState.allowedAt(mob, i);
        for (UUID id : ids) {
            if (id == null) continue;
            if (!token(level, q -> q.resolves < BrambleColossusRules.LEVEL_RESOLVE_LIMIT && ++q.resolves > 0)) { mob.colossusCounters().deferred++; return; }
            mob.colossusCounters().uuidResolves++;
            if (!(level.getEntity(id) instanceof LivingEntity party) || !party.isAlive() || !insideHeld(mob, party)) continue;
            LivingEntity attacker = party.getLastHurtByMob();
            int age = party.tickCount - party.getLastHurtByMobTimestamp();
            if (attacker != null && BrambleColossusRules.fresh(age) && legalSubjectWithoutSight(mob, attacker) && insideHeld(mob, attacker) && sight(mob, attacker, level)) {
                mob.colossusTransient().pending = attacker.getUUID(); mob.colossusTransient().phase = BrambleColossusRules.Phase.MARK; mob.colossusTransient().phaseTicks = 20; cancelMovement(mob); return;
            }
        }
    }
    private static void tickMark(BrambleColossusEntity mob, ServerLevel level) {
        if (--mob.colossusTransient().phaseTicks > 0) return;
        LivingEntity target = resolve(level, mob.colossusTransient().pending);
        boolean eligible=target != null && insideHeld(mob,target) && legalSubject(mob,target);
        var next = BrambleColossusRules.afterMark(eligible, eligible && sight(mob,target,level), mob.colossusState().displayCooldownRemaining());
        mob.colossusTransient().phase = next; mob.colossusTransient().phaseTicks = next == BrambleColossusRules.Phase.DISPLAY ? 40 : next == BrambleColossusRules.Phase.THRESH ? 200 : 0;
        if (next == BrambleColossusRules.Phase.THRESH) { mob.colossusTransient().subject = mob.colossusTransient().pending; mob.colossusTransient().pending = null; }
    }
    private static void tickDisplay(BrambleColossusEntity mob, ServerLevel level) {
        LivingEntity target = resolve(level, mob.colossusTransient().pending);
        if (target == null || !legalSubject(mob,target) || !sight(mob,target,level) || !insideHeld(mob,target)) { cancel(mob); return; }
        boolean quotaGranted=mob.colossusTransient().phaseTicks!=40||claimDisplayQuota(level);
        var gate=BrambleColossusRules.displayGate(mob.colossusTransient().phaseTicks,quotaGranted);
        if(gate==BrambleColossusRules.DisplayGate.WAIT_FOR_QUOTA){mob.colossusCounters().deferred++;return;}
        if (gate==BrambleColossusRules.DisplayGate.EMIT) {
            level.playSound(null, mob.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK, mob.getSoundSource(), 1, .7F);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY(), mob.getZ(), 8, .6,.8,.6,0);
            mob.colossusCounters().displays++; mob.colossusCounters().sounds++; mob.colossusCounters().particles+=8;
            mob.colossusCounters().feedbackEvents++; mob.setColossusState(mob.colossusState().withDisplayCooldown(600));
        }
        if (--mob.colossusTransient().phaseTicks <= 0) { mob.colossusTransient().subject=target.getUUID(); mob.colossusTransient().pending=null; mob.colossusTransient().phase=BrambleColossusRules.Phase.THRESH; mob.colossusTransient().phaseTicks=200; }
    }
    private static void tickThresh(BrambleColossusEntity mob, ServerLevel level) {
        LivingEntity target=resolve(level,mob.colossusTransient().subject); if(target==null||!legalSubject(mob,target)||!insideLeash(mob,target)||--mob.colossusTransient().phaseTicks<=0){cancel(mob);return;}
        if (++mob.colossusTransient().sightCooldown >= 10) { mob.colossusTransient().sightCooldown=0; if (!sight(mob,target,level)) mob.colossusTransient().unseenTicks += 10; else mob.colossusTransient().unseenTicks=0; }
        if (!BrambleColossusRules.retainSubject(target.isAlive(), legalSubjectWithoutSight(mob,target), target.level()==level, distanceFromPost(mob,target), mob.distanceTo(target), mob.colossusTransient().unseenTicks, mob.colossusState().nerve())) { cancel(mob); return; }
        if (mob.distanceToSqr(target) <= 9) { if(BrambleColossusRules.shouldPulse(mob.tickCount,mob.getId(),20,0)&&token(level,q->q.melee<8&&++q.melee>0)){mob.colossusCounters().meleeAttempts++;mob.doHurtTarget(level,target);} }
        else if(TreefydState.wandering(mob)) path(mob,target.position(),level);
    }
    private static void startCircuit(BrambleColossusEntity mob,ServerLevel level){double[] o=BrambleColossusRules.waypointApproachOffset(mob.colossusState().leg());BlockPos p=mob.colossusState().post().orElseThrow();mob.colossusTransient().phase=BrambleColossusRules.Phase.CIRCUIT;mob.colossusTransient().phaseTicks=300;path(mob,new Vec3(p.getX()+.5+o[0],p.getY(),p.getZ()+.5+o[1]),level);}
    private static void tickCircuit(BrambleColossusEntity mob,ServerLevel level){BlockPos p=mob.colossusState().post().orElseThrow();double[]o=BrambleColossusRules.waypointOffset(mob.colossusState().leg());Vec3 d=new Vec3(p.getX()+.5+o[0],p.getY(),p.getZ()+.5+o[1]);if(mob.position().distanceToSqr(d)<=4||--mob.colossusTransient().phaseTicks<=0){int leg=BrambleColossusRules.nextLeg(mob.colossusState().leg());mob.setColossusState(mob.colossusState().withLeg(leg).withCircuitCooldown(leg==0?2400:0));cancel(mob);}else path(mob,d,level);}
    private static boolean path(BrambleColossusEntity mob,Vec3 target,ServerLevel level){if(mob.colossusTransient().pathCooldown>0||mob.colossusTransient().routeBackoff>0||!token(level,q->q.paths<BrambleColossusRules.LEVEL_PATH_LIMIT&&q.expensive<BrambleColossusRules.LEVEL_EXPENSIVE_LIMIT&&++q.paths>0&&++q.expensive>0)){mob.colossusCounters().deferred++;return false;}mob.colossusTransient().pathCooldown=20;var path=mob.getNavigation().createPath(target.x,target.y,target.z,0);if(path==null||!path.canReach()){if(BrambleColossusRules.thirdFailure(++mob.colossusTransient().routeFailures)){cancel(mob);mob.colossusTransient().routeBackoff=BrambleColossusRules.routeBackoffSentinel();return true;}return false;}mob.colossusTransient().routeFailures=0;mob.getNavigation().moveTo(path,1);mob.colossusCounters().paths++;return false;}
    private static void sustain(BrambleColossusEntity mob,ServerLevel level){if(BrambleColossusRules.shouldPulse(mob.tickCount,mob.getId(),40,0)&&token(level,q->q.reads<1024&&++q.reads>0)){mob.colossusCounters().blockReads++;if(level.getBlockState(mob.blockPosition().below()).is(CreatureBehaviorTags.Blocks.LIVING_GROUND))mob.heal(1+CreatureBehaviorState.empowerment(mob)*.5F);}}
    private static void recover(BrambleColossusEntity mob){if(Math.floorMod(mob.colossusTransient().loadedTicks,BrambleColossusRules.NERVE_RECOVERY_CADENCE)==0&&mob.colossusTransient().subject==null&&mob.colossusTransient().pending==null)mob.setColossusState(mob.colossusState().withNerve(BrambleColossusRules.recoverNerve(mob.colossusState().nerve())));}
    private static boolean hazard(BrambleColossusEntity mob, ServerLevel level){
        var s=mob.colossusTransient(); if(s.hazardObservationCooldown==0 && token(level,q->q.reads+18<=BrambleColossusRules.LEVEL_READ_LIMIT&&(q.reads+=18)>0)){s.hazardObservationCooldown=20;s.observedContact=false;s.observedFire=false;int reads=0;AABB b=mob.getBoundingBox();for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(b.minX,b.minY-1,b.minZ),BlockPos.containing(b.maxX,b.maxY,b.maxZ))){if(reads++>=18)break;var st=level.getBlockState(p);mob.colossusCounters().blockReads++;s.observedContact|=st.is(CONTACT_HAZARDS);s.observedFire|=st.is(BlockTags.FIRE)||st.getFluidState().is(FluidTags.LAVA);}}return mob.isOnFire()||mob.isInLava()||mob.getAirSupply()<=0||s.observedContact||s.observedFire;}
    private static void withdraw(BrambleColossusEntity mob,ServerLevel level){
        var s=mob.colossusTransient();
        double currentScore=(mob.isOnFire()?4:0)+(mob.isInLava()?4:0)+(mob.getAirSupply()<=0?4:0)
            +(s.observedContact?2:0)+(s.observedFire?2:0);
        if(s.phase!=BrambleColossusRules.Phase.WITHDRAW){cancel(mob);s.phase=BrambleColossusRules.Phase.WITHDRAW;}
        if(s.routeBackoff>0)return;
        if(!token(level,q->q.expensive<BrambleColossusRules.LEVEL_EXPENSIVE_LIMIT
            &&q.reads+BrambleColossusRules.SAFE_SEARCH_READS<=BrambleColossusRules.LEVEL_READ_LIMIT
            &&q.occupancy+BrambleColossusRules.SAFE_SEARCH_VISITS<=BrambleColossusRules.LEVEL_OCCUPANCY_LIMIT
            &&++q.expensive>0&&(q.reads+=BrambleColossusRules.SAFE_SEARCH_READS)>0
            &&(q.occupancy+=BrambleColossusRules.SAFE_SEARCH_VISITS)>0)){mob.colossusCounters().deferred++;return;}
        mob.colossusCounters().hazardSearches++;
        BlockPos origin=mob.blockPosition();
        BlockPos post=mob.colossusState().post().orElse(origin);
        int reads=0,visits=0;
        for(int[]o:BrambleColossusRules.SAFE_CANDIDATES){
            if(reads>=BrambleColossusRules.SAFE_SEARCH_READS||visits>=BrambleColossusRules.SAFE_SEARCH_VISITS)break;
            mob.colossusCounters().safeCandidates++;
            BlockPos p=origin.offset(o[0],o[1],o[2]);
            boolean inside=BrambleColossusRules.insideLeash(p.getX()+.5-(post.getX()+.5),p.getY()-post.getY(),p.getZ()+.5-(post.getZ()+.5));
            if(!BrambleColossusRules.safeDestination(inside,currentScore,0))continue;
            SafeResult result=safe(mob,level,p,BrambleColossusRules.SAFE_SEARCH_READS-reads,
                Math.min(BrambleColossusRules.SAFE_VISITS_PER_CANDIDATE,BrambleColossusRules.SAFE_SEARCH_VISITS-visits));
            reads+=result.reads();visits+=result.visits();
            if(!result.safe())continue;
            path(mob,new Vec3(p.getX()+.5,p.getY(),p.getZ()+.5),level);return;
        }
        if(++s.routeFailures>=3){s.routeFailures=0;s.routeBackoff=BrambleColossusRules.routeBackoffSentinel();}
    }
    private record SafeResult(boolean safe,int reads,int visits){}
    private static SafeResult safe(BrambleColossusEntity mob,ServerLevel level,BlockPos p,int readBudget,int visitBudget){
        AABB moved=mob.getBoundingBox().move(p.getX()+.5-mob.getX(),p.getY()-mob.getY(),p.getZ()+.5-mob.getZ());
        AABB halo=moved.inflate(1.0D);
        if(!level.getWorldBorder().isWithinBounds(halo))return new SafeResult(false,0,0);
        HaloReadCache cache=new HaloReadCache(level,halo,readBudget);
        if(!cache.haloLoaded())return new SafeResult(false,0,0);
        boolean clear=true;
        BlockPos min=BlockPos.containing(moved.minX,moved.minY,moved.minZ);
        BlockPos max=BlockPos.containing(moved.maxX-1.0E-7D,moved.maxY-1.0E-7D,moved.maxZ-1.0E-7D);
        for(BlockPos body:BlockPos.betweenClosed(min,max)){
            var state=cache.getBlockState(body);
            if(!state.getFluidState().isEmpty()||state.is(CONTACT_HAZARDS)||state.is(BlockTags.FIRE)
                ||state.getCollisionShape(cache,body).toAabbs().stream().anyMatch(shape->shape.move(body).intersects(moved))){clear=false;break;}
        }
        for(int x=min.getX();clear&&x<=max.getX();x++)for(int z=min.getZ();clear&&z<=max.getZ();z++){
            BlockPos support=new BlockPos(x,min.getY()-1,z);
            if(!cache.getBlockState(support).isFaceSturdy(cache,support,net.minecraft.core.Direction.UP))clear=false;
        }
        if(!clear||!cache.withinContract()){mob.colossusCounters().blockReads+=cache.actualReads();return new SafeResult(false,cache.actualReads(),0);}
        int[]visited={0};boolean[]occupied={false};
        com.kadamitas.warlockery.entity.BoundedEntityQuery.visit(level, EntityTypeTest.forClass(Entity.class),moved,e->{
            visited[0]++;mob.colossusCounters().occupancyVisits++;
            if(e!=mob&&e.canBeCollidedWith(mob)){occupied[0]=true;return AbortableIterationConsumer.Continuation.ABORT;}
            return visited[0]>=visitBudget?AbortableIterationConsumer.Continuation.ABORT:AbortableIterationConsumer.Continuation.CONTINUE;
        });
        mob.colossusCounters().blockReads+=cache.actualReads();
        return new SafeResult(!occupied[0],cache.actualReads(),visited[0]);
    }
    private static final class HaloReadCache implements net.minecraft.world.level.BlockGetter{
        private final ServerLevel level;private final BlockPos min,max;private final int budget;
        private final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> cache=new java.util.HashMap<>();
        private int reads;private boolean rejected;
        HaloReadCache(ServerLevel level,AABB halo,int budget){this.level=level;this.min=BlockPos.containing(halo.minX,halo.minY,halo.minZ);this.max=BlockPos.containing(halo.maxX,halo.maxY,halo.maxZ);this.budget=budget;}
        boolean haloLoaded(){return level.hasChunkAt(min)&&level.hasChunkAt(max)&&level.hasChunkAt(new BlockPos(min.getX(),min.getY(),max.getZ()))&&level.hasChunkAt(new BlockPos(max.getX(),max.getY(),min.getZ()));}
        boolean withinContract(){return !rejected;}int actualReads(){return reads;}
        @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos position){
            if(position.getX()<min.getX()||position.getX()>max.getX()||position.getY()<min.getY()||position.getY()>max.getY()||position.getZ()<min.getZ()||position.getZ()>max.getZ()){rejected=true;return net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();}
            BlockPos key=position.immutable();var existing=cache.get(key);if(existing!=null)return existing;
            if(reads>=budget){rejected=true;return net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();}
            reads++;var state=level.getBlockState(key);cache.put(key,state);return state;
        }
        @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos p){rejected=true;return null;}
        @Override public int getHeight(){return level.getHeight();}@Override public int getMinY(){return level.getMinY();}
    }
    private static void returnToPost(BrambleColossusEntity mob,ServerLevel level){var s=mob.colossusTransient();BlockPos p=mob.colossusState().post().orElse(mob.blockPosition());if(mob.distanceToSqr(Vec3.atCenterOf(p))<=4){s.returningFromHazard=false;s.returnTicks=0;cancelMovement(mob);return;}if(BrambleColossusRules.returnTimedOut(++s.returnTicks)){mob.recordPost(mob.blockPosition());cancel(mob);s.routeBackoff=BrambleColossusRules.routeBackoffSentinel();return;}if(path(mob,Vec3.atCenterOf(p),level)){mob.recordPost(mob.blockPosition());mob.colossusTransient().routeBackoff=BrambleColossusRules.routeBackoffSentinel();}}
    private static void falter(BrambleColossusEntity mob){var s=mob.colossusTransient();if(s.phase!=BrambleColossusRules.Phase.FALTER){int backoff=s.routeBackoff;cancel(mob);s.routeBackoff=backoff;s.phase=BrambleColossusRules.Phase.FALTER;s.returnTicks=0;}s.pending=null;s.subject=null;}
    public static void onAcceptedDamage(BrambleColossusEntity mob,ServerLevel level,LivingEntity attacker,float amount){
        onAcceptedDamage(mob,level,attacker,amount,0);
    }
    static void onAcceptedDamage(BrambleColossusEntity mob,ServerLevel level,LivingEntity attacker,float amount,int evidenceAge){
        if(amount<=0)return;
        mob.setColossusState(mob.colossusState().withNerve(BrambleColossusRules.loseNerve(mob.colossusState().nerve())));
        boolean visible=sight(mob,attacker,level);
        if(BrambleColossusRules.acceptedAttribution(amount,evidenceAge,visible)&&legalSubject(mob,attacker)
            &&!BrambleColossusRules.falterAt(mob.colossusState().nerve())){
            var next=BrambleColossusRules.afterAcceptedDamage(mob.colossusTransient().phase);
            if(next==BrambleColossusRules.Phase.THRESH){mob.colossusTransient().subject=attacker.getUUID();mob.colossusTransient().pending=null;mob.colossusTransient().phaseTicks=200;}
            else{mob.colossusTransient().pending=attacker.getUUID();mob.colossusTransient().phaseTicks=20;}
            mob.colossusTransient().phase=next;cancelMovement(mob);
        }
        boolean owner=CreatureBehaviorState.owner(mob).filter(attacker.getUUID()::equals).isPresent();
        boolean listed=TreefydState.isAllowed(mob,attacker.getUUID());
        boolean same=attacker instanceof BrambleColossusEntity;
        boolean relation=attacker==mob||attacker.isAlliedTo(mob)||mob.isAlliedTo(attacker);
        var scratch=mob.colossusTransient();
        if(!scratch.retaliating&&!relation&&BrambleColossusRules.thornContact(owner,listed,same,mob.distanceToSqr(attacker),amount)
            &&token(level,q->q.thorns<BrambleColossusRules.LEVEL_THORN_LIMIT&&++q.thorns>0)){
            scratch.retaliating=true;
            try{mob.colossusCounters().thornEvents++;attacker.hurtServer(level,mob.damageSources().thorns(mob),BrambleColossusRules.thornDamage(amount));}
            finally{scratch.retaliating=false;}
        }
    }
    public static boolean legalSubject(BrambleColossusEntity mob,LivingEntity target){return legalSubjectWithoutSight(mob,target);}
    private static boolean legalSubjectWithoutSight(BrambleColossusEntity mob,LivingEntity target){
        boolean owner=CreatureBehaviorState.owner(mob).filter(target.getUUID()::equals).isPresent();
        boolean listed=TreefydState.isAllowed(mob,target.getUUID());
        boolean same=target instanceof BrambleColossusEntity;
        var brain=target.getBrain();
        boolean playerExcluded=target instanceof Player p&&(p.isCreative()||p.isSpectator());
        boolean trading=target instanceof AbstractVillager villager&&villager.getTradingPlayer()!=null;
        boolean breeding=target instanceof Animal animal&&animal.isInLove()
            ||brain.hasMemoryValue(MemoryModuleType.BREED_TARGET);
        boolean panic=brain.isActive(Activity.PANIC)||brain.hasMemoryValue(MemoryModuleType.IS_PANICKING);
        boolean raid=target instanceof Raider raider&&raider.getCurrentRaid()!=null
            ||brain.isActive(Activity.RAID)||brain.isActive(Activity.PRE_RAID);
        boolean relation=target==mob||target.isAlliedTo(mob)||mob.isAlliedTo(target);
        boolean ordinary=BrambleColossusRules.ordinarySubject(!target.isAlive(),target.isRemoved(),
            target.isInvulnerable(),target.level()!=mob.level(),playerExcluded,target.isSleeping(),
            trading||breeding,panic,raid)&&!relation;
        return BrambleColossusRules.legal(owner,listed,same,ordinary,true,mob.colossusState().nerve());
    }
    private static boolean sight(BrambleColossusEntity mob,LivingEntity target,ServerLevel level){if(!token(level,q->q.rays<32&&++q.rays>0))return false;mob.colossusCounters().sightRays++;return level.clip(new ClipContext(mob.getEyePosition(),target.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob)).getType()!=HitResult.Type.BLOCK;}
    private static boolean insideHeld(BrambleColossusEntity mob,LivingEntity target){BlockPos p=mob.colossusState().post().orElse(mob.blockPosition());return BrambleColossusRules.insideHeldVolume(target.getX()-(p.getX()+.5),target.getY()-p.getY(),target.getZ()-(p.getZ()+.5));}
    private static boolean insideLeash(BrambleColossusEntity mob,LivingEntity target){BlockPos p=mob.colossusState().post().orElse(mob.blockPosition());return BrambleColossusRules.insideLeash(target.getX()-(p.getX()+.5),target.getY()-p.getY(),target.getZ()-(p.getZ()+.5));}
    private static LivingEntity resolve(ServerLevel level,UUID id){return id!=null&&level.getEntity(id)instanceof LivingEntity living?living:null;}
    private static double distanceFromPost(BrambleColossusEntity mob,LivingEntity target){BlockPos p=mob.colossusState().post().orElse(mob.blockPosition());return Math.sqrt(target.distanceToSqr(Vec3.atCenterOf(p)));}
    public static void cancel(BrambleColossusEntity mob){mob.colossusTransient().reset();mob.colossusCounters().cancellations++;mob.setTarget(null);cancelMovement(mob);}
    public static void cancelMovement(BrambleColossusEntity mob){mob.getNavigation().stop();mob.getMoveControl().setWait();mob.setDeltaMovement(0,mob.getDeltaMovement().y,0);}
}
