package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

public final class EntGameTests {
    private EntGameTests() {}

    public static void entFellingRousesWarnsThenStrikesWithinItsStand(GameTestHelper helper){
        Fixture f=new Fixture(helper);EntEntity nearest=f.ent(new BlockPos(1,1,1));EntEntity tied=f.ent(new BlockPos(1,1,3));EntEntity capped=f.ent(new BlockPos(3,1,1));ServerPlayer player=f.player(new BlockPos(3,1,3));
        BlockPos log=helper.absolutePos(new BlockPos(1,1,2));f.set(log,Blocks.OAK_LOG.defaultBlockState());
        helper.runAfterDelay(7,()->{nearest.entTransient().phase=EntRules.Phase.TEND;f.breakLog(log,player);helper.assertTrue(nearest.entCounters().tendPreemptions()==1,"a felling alarm fully preempts retained tending ownership");nearest.setPos(log.getX()+.5,log.getY(),log.getZ()-.5);tied.setPos(log.getX()+.5,log.getY(),log.getZ()+2.5);player.teleportTo(log.getX()+2.5,log.getY(),log.getZ()+.5);for(int x=0;x<=2;x++)for(int y=1;y<=4;y++)f.set(log.offset(x,y,0),Blocks.AIR.defaultBlockState());});
        helper.runAfterDelay(32,()->{try{
            helper.assertTrue(nearest.entCounters().noticesReceived()==1&&tied.entCounters().noticesReceived()==1,"distance and UUID ordering notify exactly two registered Ents");
            helper.assertTrue(capped.entCounters().noticesReceived()==0,"the third eligible Ent is excluded by the per-break cap");
            EntEntity warned=List.of(nearest,tied).stream().filter(ent->ent.entTransient().phase()==EntRules.Phase.WARN).findFirst().orElse(null);
            helper.assertTrue(warned!=null,"the actual break event produces a visible warning for a selected Ent");
            f.breakLog(log,player);
            helper.assertTrue(warned.entTransient().phase()==EntRules.Phase.STRIKE,"a second actual felling event during WARN is refusal");
            helper.assertTrue(warned.entCounters().warnings()==1&&warned.entCounters().subjectsBound()==1,"warning and subject counters are pass-local and exact");
            helper.assertTrue(warned.entCounters().episodesStarted()==1,"refusal continues the same episode rather than replaying it");
            helper.succeed();
        }catch(Throwable t){f.close();throw t;}});
    }

    public static void entIgnoresPresenceAndSettlesToItsAnchor(GameTestHelper helper){
        Fixture f=new Fixture(helper);EntEntity ent=f.ent(new BlockPos(1,1,1));ServerPlayer player=f.player(new BlockPos(2,1,1));
        helper.runAfterDelay(211,()->{helper.assertTrue(ent.entTransient().phase()==EntRules.Phase.WARDING,"loaded player presence never starts an episode");helper.assertTrue(ent.entCounters().episodesStarted()==0&&ent.getTarget()==null,"presence creates neither proximity target nor episode");helper.assertTrue(ent.entCounters().pathRequests()==0&&ent.entCounters().meleeAttempts()==0,"presence owns neither movement nor combat");EntRuntime.seedFromFelling(ent,player,ent.blockPosition());});
        helper.runAfterDelay(236,()->{helper.assertTrue(ent.entTransient().phase()==EntRules.Phase.WARN,"visible felling, unlike presence, warns");EntRuntime.seedFromFelling(ent,player,ent.blockPosition());player.setPos(player.getX()+30,player.getY(),player.getZ());});
        helper.runAfterDelay(241,()->{try{helper.assertTrue(ent.entTransient().phase()==EntRules.Phase.SETTLE||ent.entTransient().phase()==EntRules.Phase.WARDING,"a subject beyond the leash releases through settle and may complete immediately at anchor");helper.assertTrue(ent.getTarget()==null,"leash release clears the Mob target");helper.assertTrue(ent.entCounters().subjectsBound()==1&&ent.entCounters().meleeAttempts()==0,"leash release is pass-local and causes no remote strike");helper.succeed();}catch(Throwable t){f.close();throw t;}});
    }

    public static void entStandAlarmAndLogBreakSpawnStayBounded(GameTestHelper helper){
        Fixture f=new Fixture(helper);ServerPlayer player=f.player(new BlockPos(2,1,2));List<EntEntity> population=new ArrayList<>();for(int i=0;i<16;i++)population.add(f.ent(new BlockPos(1+i%4,1,1+i/4)));
        BlockPos log=helper.absolutePos(new BlockPos(2,1,2));f.set(log,Blocks.OAK_LOG.defaultBlockState());f.breakLog(log,player);
        helper.runAfterDelay(2,()->{try{long notified=population.stream().filter(ent->ent.entCounters().noticesReceived()==1).count();helper.assertTrue(notified==2,"a population of sixteen still receives exactly two notices");helper.assertTrue(population.stream().mapToInt(ent->ent.entCounters().noticesReceived()).sum()==2,"no notice is duplicated or broadcast recursively");helper.assertTrue(population.stream().allMatch(ent->ent.entCounters().noticesReceived()<=1),"per-Ent notice reports remain pass-local");helper.assertTrue(EntRules.logBreakSpawnChance(0)==0&&EntRules.logBreakSpawnChance(26)==.26D&&EntRules.logBreakSpawnChance(100)==1,"unchanged spawn probability boundaries remain exact");helper.succeed();}catch(Throwable t){f.close();throw t;}});
    }

    public static void entGroveTendingIsBoundedAndRespectsMobgriefing(GameTestHelper helper){
        Fixture f=new Fixture(helper);f.groveFloor();EntEntity ent=f.ent(new BlockPos(1,1,1));ItemEntity item=f.item(new BlockPos(2,1,1),2);
        boolean old=helper.getLevel().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING);helper.getLevel().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING,false,helper.getLevel().getServer());
        helper.runBeforeTestEnd(() -> helper.getLevel().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING,old,helper.getLevel().getServer()));
        helper.runAfterDelay(2,()->{boolean changed=AmbientActivityRuntime.executeNow(ent,helper.getLevel(),ArcaneCreature.CreatureKind.ENT,AmbientActivityProfile.ActivityType.GROVE_TENDING);helper.assertTrue(!changed&&item.getItem().getCount()==2,"mobGriefing false prevents both edit and shrink");helper.getLevel().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING,true,helper.getLevel().getServer());});
        helper.runAfterDelay(4,()->{boolean changed=AmbientActivityRuntime.executeNow(ent,helper.getLevel(),ArcaneCreature.CreatureKind.ENT,AmbientActivityProfile.ActivityType.GROVE_TENDING);helper.assertTrue(changed&&item.getItem().getCount()==1,"one bounded job plants exactly one sapling and shrinks exactly one item");helper.assertTrue(counter(ent,"tendActualReads")>0&&counter(ent,"tendActualReads")<=896,"grove tending reports its actual cached block reads within the frozen cap");helper.assertTrue(counter(ent,"tendItemVisits")<=8,"grove tending reports no more than eight actual item visits");});
        helper.runAfterDelay(6,()->{for(int i=0;i<12;i++)f.set(helper.absolutePos(new BlockPos(i%4,1,2+i/4)),Blocks.OAK_SAPLING.defaultBlockState());ItemEntity saturated=f.item(new BlockPos(4,1,4),2);boolean changed=AmbientActivityRuntime.executeNow(ent,helper.getLevel(),ArcaneCreature.CreatureKind.ENT,AmbientActivityProfile.ActivityType.GROVE_TENDING);helper.assertTrue(!changed&&saturated.getItem().getCount()==2,"twelve local saplings saturate the grove without mutation");});
        helper.runAfterDelay(8,()->{try{while(EntRuntime.chargeReads(helper.getLevel(),1)){}ItemEntity deferred=f.item(new BlockPos(3,1,1),2);boolean changed=AmbientActivityRuntime.executeNow(ent,helper.getLevel(),ArcaneCreature.CreatureKind.ENT,AmbientActivityProfile.ActivityType.GROVE_TENDING);helper.assertTrue(!changed&&deferred.getItem().getCount()==2,"read exhaustion fails closed without delayed shrink");helper.succeed();}catch(Throwable t){f.close();throw t;}});
    }

    public static void entHazardEscapeAndCancellationAreDeterministic(GameTestHelper helper){
        Fixture f=new Fixture(helper);EntEntity ent=f.ent(new BlockPos(1,1,1));f.player(new BlockPos(2,1,1));ItemEntity item=new ItemEntity(helper.getLevel(),ent.getX()+1,ent.getY(),ent.getZ(),new ItemStack(Items.OAK_SAPLING,2));helper.getLevel().addFreshEntity(item);f.entities.add(item);ent.entTransient().phase=EntRules.Phase.TEND;ent.igniteForTicks(10);EntRuntime.tick(ent,helper.getLevel());
        helper.runAfterDelay(2,()->{helper.assertTrue(ent.entTransient().phase()==EntRules.Phase.ESCAPE,"fire preempts the retained tending job");helper.assertTrue(ent.getTarget()==null,"hazard teardown clears the Mob target");helper.assertTrue(item.getItem().getCount()==2&&ent.entCounters().tendPreemptions()==1,"preemption permits no delayed shrink and is pass-locally counted");ent.clearFire();});
        helper.runAfterDelay(4,()->helper.assertTrue(ent.entTransient().phase()==EntRules.Phase.SETTLE||ent.entTransient().phase()==EntRules.Phase.WARDING,"resolved fire returns through settle without replay"));
        helper.runAfterDelay(11,()->{ent.entTransient().phase=EntRules.Phase.TEND;BlockPos observed=BlockPos.containing(ent.getBoundingBox().minX,ent.getBoundingBox().minY,ent.getBoundingBox().minZ).offset(-1,0,-1);f.set(observed.below(),Blocks.DIRT.defaultBlockState());f.set(observed,Blocks.SWEET_BERRY_BUSH.defaultBlockState());while(!EntRules.staggeredDue(ent.tickCount,ent.getId(),20))ent.tickCount++;EntRuntime.tick(ent,helper.getLevel());helper.assertTrue(ent.entTransient().phase()==EntRules.Phase.ESCAPE,"a charged contact-hazard observation preempts routine work on its due tick");f.set(observed,Blocks.AIR.defaultBlockState());});
        helper.runAfterDelay(13,()->{try{helper.assertTrue(ent.entCounters().tendPreemptions()==2&&item.getItem().getCount()==2,"each hazard cancellation tears down exactly once");helper.assertTrue(counter(ent,"safeCandidates")<=16,"escape examines no more than sixteen candidates");helper.assertTrue(counter(ent,"safeActualReads")>0&&counter(ent,"safeActualReads")<=128,"escape reports every actual cached block read within the frozen cap");helper.assertTrue(counter(ent,"safeEntityVisits")<=32,"escape reports no more than thirty-two actual occupancy visits");int border=counter(ent,"safeBorderAdmissions"),chunks=counter(ent,"safeChunkAdmissions"),occupancy=counter(ent,"safeOccupancyAdmissions");helper.assertTrue(border>=0&&chunks>=0&&occupancy>=0&&counter(ent,"safeAdmissions")==border+chunks+occupancy,"the aggregate admission counter equals each real external query admission exactly once");EntRuntime.cancel(ent,EntRules.Phase.WARDING);helper.assertTrue(ent.entTransient().phase()==EntRules.Phase.WARDING&&ent.getTarget()==null,"explicit lifecycle cancellation normalizes all transient ownership");ServerPlayer illegal=f.player(new BlockPos(3,1,1));illegal.setGameMode(GameType.CREATIVE);int grievance=ent.entState().grievance(),episodes=ent.entCounters().episodesStarted();EntRuntime.afterHurt(ent,illegal);helper.assertTrue(ent.entState().grievance()==grievance&&ent.entCounters().episodesStarted()==episodes,"an illegal attributed attacker cannot mint grievance or an episode");ServerPlayer legal=f.player(new BlockPos(2,1,1));ent.entTransient().phase=EntRules.Phase.TEND;int preemptions=ent.entCounters().tendPreemptions(),count=item.getItem().getCount();EntRuntime.afterHurt(ent,legal);helper.assertTrue(ent.entCounters().tendPreemptions()==preemptions+1&&item.getItem().getCount()==count,"direct harm fully cancels tending before it rouses a legal episode");}catch(Throwable t){f.close();throw t;}});
        helper.runAfterDelay(15,()->{try{helper.assertTrue(ent.entTransient().phase()!=EntRules.Phase.TEND&&ent.entCounters().tendPreemptions()==3,"the preempted tending job cannot regain ownership on a later tick");helper.assertTrue(item.getItem().getCount()==2,"the preempted tending job performs no delayed item shrink");helper.succeed();}catch(Throwable t){f.close();throw t;}});
    }

    public static void entSaveReloadVariantsAndGolemLifecycleAreReplaced(GameTestHelper helper){
        Fixture f=new Fixture(helper);EntEntity ent=f.ent(new BlockPos(1,1,1));ent.setEntState(new EntState(1,1,1,1,1,77,321,4321));EntRuntime.cancel(ent,EntRules.Phase.WARDING);
        TagValueOutput output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,helper.getLevel().registryAccess());ent.saveWithoutId(output);var saved=output.buildResult().copy();java.util.UUID uuid=ent.getUUID();ent.discard();helper.assertTrue(helper.getLevel().getEntity(uuid)==null,"the original Ent is unloaded before its saved UUID is restored");Entity made=ModEntities.ENT.get().create(helper.getLevel(),EntitySpawnReason.LOAD);helper.assertTrue(made instanceof EntEntity,"registered load creates dedicated Ent");EntEntity loaded=(EntEntity)made;f.entities.add(loaded);loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,helper.getLevel().registryAccess(),saved));helper.assertTrue(helper.getLevel().addFreshEntity(loaded),"the reloaded Ent is accepted into the live level");helper.assertTrue(helper.getLevel().getEntity(uuid)==loaded,"the reloaded Ent is the authoritative live entity rather than a rejected duplicate UUID");
        helper.runAfterDelay(2,()->{try{helper.assertTrue(helper.getLevel().getEntity(uuid)==loaded&&loaded.tickCount>0,"the accepted reloaded Ent advances in the live world");helper.assertTrue(loaded.entState().grievance()==77&&321-loaded.entState().warnCooldownRemaining()==loaded.tickCount,"the cooldown advances exactly once for every observed live tick");helper.assertTrue(loaded.entTransient().phase()==EntRules.Phase.WARDING&&loaded.entCounters().episodesStarted()==0&&loaded.entCounters().tendJobs()==0&&loaded.entCounters().tendSuccesses()==0,"phase, pending work, and routine jobs never replay");helper.assertTrue(loaded.getClass().getSuperclass()==net.minecraft.world.entity.PathfinderMob.class,"no golem lifecycle remains");helper.assertTrue(loaded.variant()!=null&&loaded.getCustomName()!=null,"variant and displayed name normalize before AI");helper.succeed();}catch(Throwable t){f.close();throw t;}});
    }

    private static final class Fixture implements AutoCloseable{
        final GameTestHelper helper;final List<Entity> entities=new ArrayList<>();final List<SavedBlock> blocks=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper helper){this.helper=helper;shell();helper.runBeforeTestEnd(this::close);}
        EntEntity ent(BlockPos relative){BlockPos p=helper.absolutePos(relative);EntEntity ent=ModEntities.ENT.get().spawn(helper.getLevel(),p,EntitySpawnReason.EVENT);if(ent==null)throw new IllegalStateException("Ent spawn failed");entities.add(ent);return ent;}
        ServerPlayer player(BlockPos relative){ServerPlayer p=(ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);net.minecraft.network.Connection connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(connection);net.minecraft.server.network.CommonListenerCookie cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false);helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection,p,cookie);p.setGameMode(GameType.SURVIVAL);BlockPos at=helper.absolutePos(relative);p.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);entities.add(p);return p;}
        ItemEntity item(BlockPos relative,int count){BlockPos p=helper.absolutePos(relative);ItemEntity item=new ItemEntity(helper.getLevel(),p.getX()+.5,p.getY(),p.getZ()+.5,new ItemStack(Items.OAK_SAPLING,count));helper.getLevel().addFreshEntity(item);entities.add(item);return item;}
        void breakLog(BlockPos absolute,ServerPlayer player){EntRuntime.handleLogBreak(new BreakBlockEvent(helper.getLevel(),absolute,helper.getLevel().getBlockState(absolute),player));}
        void set(BlockPos absolute,BlockState state){BlockState old=helper.getLevel().getBlockState(absolute);blocks.add(new SavedBlock(absolute,old));helper.getLevel().setBlockAndUpdate(absolute,state);}
        void groveFloor(){for(int x=-3;x<=6;x++)for(int z=-3;z<=6;z++)set(helper.absolutePos(new BlockPos(x,0,z)),Blocks.DIRT.defaultBlockState());}
        void shell(){BlockPos c=helper.absolutePos(new BlockPos(1,1,1));for(int y=0;y<=6;y++)for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)if(Math.abs(x)==6||Math.abs(z)==6||y==6){BlockPos p=c.offset(x,y,z);BlockState old=helper.getLevel().getBlockState(p);if(old.isAir()){blocks.add(new SavedBlock(p,old));helper.getLevel().setBlockAndUpdate(p,Blocks.BARRIER.defaultBlockState());}}}
        public void close(){if(closed)return;closed=true;for(Entity e:entities){if(e instanceof ServerPlayer player){player.getInventory().clearContent();player.removeAllEffects();player.setHealth(player.getMaxHealth());}GameTestMockPlayers.release(e);}for(int i=blocks.size()-1;i>=0;i--){SavedBlock b=blocks.get(i);helper.getLevel().setBlockAndUpdate(b.pos,b.state);}}
    }
    private record SavedBlock(BlockPos pos,BlockState state){}
    private static int counter(EntEntity ent,String name){try{return ent.entCounters().getClass().getField(name).getInt(ent.entCounters());}catch(ReflectiveOperationException ignored){return -1;}}
}
