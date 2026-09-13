package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class VisualUpdateClientAcceptance implements FabricClientGameTest {
    private static final List<String> CREATURES=List.of("abyssal_regent","dreamroot","circle_mage","echo_shade","eldritch_watcher","emberhorn_archfiend","feral_lycan","hellhound","nightmare","poltergeist","spectral_familiar","spectre","werewolf","death","naamah","spirit","werewolf_hunter","illusion_creeper","pale_steed","stonebroker","storm_simian","ent");
    private static final List<String> ICONS=List.of("glintweed","bloodrose","grassper","seedsartichoke","seedsbelladonna","seedsmandrake","seedsdreamroot","seedssnowbell","seedswolfsbane","seedswormwood");
    private static final Set<String> MOTION=Set.of("death","naamah","spirit","werewolf_hunter","illusion_creeper","pale_steed","stonebroker","storm_simian","ent");
    private final Map<String,Object> report=new LinkedHashMap<>();
    private final Map<String,Map<String,Object>> results=new LinkedHashMap<>();
    private final List<String> screenshots=new ArrayList<>();
    private final List<String> failures=new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private String active;
    private int subjectId;
    private double height;

    @Override public void runTest(ClientGameTestContext context) {
        evidence=Path.of(System.getProperty("warlockery.clientEvidence",".codex-local/client-acceptance")).resolve("visual-update").resolve(UUID.randomUUID().toString());
        final String configured=System.getProperty("warlockery.visualIds","");
        final List<String> selected=configured.isBlank()?CREATURES:Arrays.stream(configured.split(",")).map(String::trim).toList();
        check(selected.stream().allMatch(id->CREATURES.contains(id)||id.equals("icons")),"Unknown requested visual case");
        final int originalFov=context.computeOnClient(client->client.options.fov().get());
        final boolean originalHud=context.computeOnClient(client->client.gui.hud.isHidden());
        report.put("capture_completed",false);report.put("visual_review","PENDING_IMAGE_REVIEW");report.put("results",results);report.put("screenshots",screenshots);report.put("failures",failures);
        report.put("scope","Diagnostic native rendering only. Static anatomy uses staged no-AI subjects. Motion uses normal AI and records actual positions/actions; completed capture is not proof of a working attack or an approved appearance. Item views do not test item abilities.");
        try {
            Files.createDirectories(evidence);context.getInput().resizeWindow(1280,800);
            context.runOnClient(client->{client.options.fov().set(60);client.options.guiScale().set(2);client.resizeGui();});
            for(String id:selected)results.put(id,new LinkedHashMap<>(Map.of("status","NOT_RUN")));
            for(String id:selected){
                active=id;row().put("status","RUNNING");write();
                try(var created=context.worldBuilder().create()){
                    world=created;
                    try{stage(context);if(id.equals("icons"))icons(context);else creature(context,id);row().put("status","CAPTURED_REVIEW_PENDING");}
                    catch(Throwable failure){row().put("status","FAILED");row().put("failure",failure.toString());failures.add(id+": "+failure);try{shot(context,"failure");}catch(Throwable ignored){}}
                    finally{context.getInput().releaseKey(GLFW.GLFW_KEY_W);write();}
                }finally{world=null;}
            }
            report.put("capture_completed",failures.isEmpty());write();check(failures.isEmpty(),String.join("; ",failures));
            System.out.println("WARLOCKERY_VISUAL_UPDATE_CAPTURED "+evidence);
        }catch(Throwable failure){throw new AssertionError("Visual update evidence: "+evidence,failure);}
        finally{context.runOnClient(client->client.options.fov().set(originalFov));if(context.computeOnClient(client->client.gui.hud.isHidden())!=originalHud)context.getInput().pressKey(GLFW.GLFW_KEY_F1);}
    }

    private void stage(ClientGameTestContext context){
        world.getServer().runOnServer(server->{
            server.setDifficulty(Difficulty.NORMAL,true);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 6000");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"weather clear");
            var level=server.overworld();
            for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(-18,99,-18),new BlockPos(18,112,18)))level.setBlockAndUpdate(pos,pos.getY()!=99?Blocks.AIR.defaultBlockState():Math.abs(pos.getX())<=10&&Math.abs(pos.getZ())<=10?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.GRASS_BLOCK.defaultBlockState());
            var player=world.getConnection().getServerPlayer();player.setGameMode(GameType.SPECTATOR);player.getInventory().clearContent();player.removeAllEffects();player.teleportTo(level,.5,102,8.5,Set.of(),180,15,true);
        });
        world.getConnection().waitForClientboundPackets();world.getConnection().waitForChunksRender();
        for(int i=0;i<3&&!context.computeOnClient(c->c.options.getCameraType().isFirstPerson());i++)context.getInput().pressKey(GLFW.GLFW_KEY_F5);
        hud(context,true);
    }

    private void creature(ClientGameTestContext context,String id)throws Exception{
        world.getServer().runOnServer(server->{
            var level=server.overworld();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"summon warlockery:"+id+" 0.5 100 0.5 {PersistenceRequired:1b}");
            var subjects=level.getEntitiesOfClass(Mob.class,new AABB(-2,99,-2,3,110,3),mob->mob.getType()==ModEntities.ALL.get(id).get());
            check(subjects.size()==1,"Native summon creates one actual "+id);var mob=subjects.getFirst();
            mob.setNoAi(true);mob.setTarget(null);mob.setYRot(0);mob.setYBodyRot(0);mob.setYHeadRot(0);mob.setDeltaMovement(Vec3.ZERO);
            subjectId=mob.getId();height=Math.max(1.0,mob.getBbHeight()*1.4);
            row().put("entity",Map.of("id",id,"uuid",mob.getStringUUID(),"class",mob.getClass().getName(),"width",mob.getBbWidth(),"height",mob.getBbHeight(),"position",mob.position().toString(),"native_no_gravity",mob.isNoGravity()));
        });
        world.getConnection().waitForClientboundPackets();context.waitFor(client->client.level!=null&&client.level.getEntity(subjectId)!=null,100);
        row().put("runtime_texture",resource(context,"textures/entity/"+id+".png"));
        final Vec3 target=new Vec3(.5,100+height*.42,.5);final double distance=height*1.5;
        capture(context,"front",target.add(0,height*.07,distance),target);
        capture(context,"oblique",target.add(distance*.75,height*.14,distance*.75),target);
        capture(context,"side",target.add(distance,height*.05,0),target);
        capture(context,"rear",target.add(0,height*.07,-distance),target);
        capture(context,"play-distance",target.add(1,height*.1,Math.max(6,distance*1.5)),target);
        if(MOTION.contains(id))motion(context,id);
    }

    private void motion(ClientGameTestContext context,String id)throws Exception{
        final List<Map<String,Object>> frames=new ArrayList<>();row().put("motion_frames",frames);
        world.getServer().runOnServer(server->{
            var mob=(Mob)server.overworld().getEntity(subjectId);check(mob!=null,"Motion subject remains present");
            mob.setNoAi(false);mob.getNavigation().moveTo(4.5,100,.5,1.0);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"summon minecraft:cow 5.5 100 0.5 {PersistenceRequired:1b,Invulnerable:1b}");
            if(!id.equals("pale_steed")){var targets=server.overworld().getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class,new AABB(4,99,-1,7,104,2));if(!targets.isEmpty())mob.setTarget(targets.getFirst());}
        });
        for(int frame=0;frame<8;frame++){
            context.waitTicks(15);
            final Map<String,Object> data=world.getServer().computeOnServer(server->{
                var mob=(Mob)server.overworld().getEntity(subjectId);check(mob!=null&&mob.isAlive(),"Motion subject stays alive");
                Map<String,Object> item=new LinkedHashMap<>();Map<String,Object> presentation=new LinkedHashMap<>();for(var method:mob.getClass().getMethods()){if(method.getName().startsWith("presentation")&&method.getParameterCount()==0){try{presentation.put(method.getName(),String.valueOf(method.invoke(mob)));}catch(ReflectiveOperationException e){throw new AssertionError(e);}}}item.put("presentation",presentation);item.put("position",mob.position().toString());item.put("velocity",mob.getDeltaMovement().toString());item.put("yaw",mob.getYRot());item.put("grounded",mob.onGround());item.put("using_item",mob.isUsingItem());item.put("main_hand",mob.getMainHandItem().toString());item.put("target",mob.getTarget()==null?"none":mob.getTarget().getStringUUID());item.put("projectiles",server.overworld().getEntitiesOfClass(Projectile.class,mob.getBoundingBox().inflate(20)).stream().map(e->e.getType()+" "+e.position()).toList());return item;
            });frames.add(data);
            final Vec3 position=context.computeOnClient(client->client.level.getEntity(subjectId).position());
            final Vec3 aim=position.add(0,height*.42,0);
            capture(context,"motion-"+frame,aim.add(height*1.9,height*.08,height*1.9),aim);
        }
        row().put("motion_review","REQUIRED: judge actual displacement, limb/wing/scythe motion and weapon-use/projectile frames. Lack of requested attack is not a pass.");
    }

    private void icons(ClientGameTestContext context)throws Exception{
        hud(context,false);
        world.getServer().runOnServer(server->{var player=world.getConnection().getServerPlayer();player.setGameMode(GameType.SURVIVAL);player.setInvulnerable(true);player.teleportTo(server.overworld(),.5,100,-2.5,Set.of(),0,15,true);for(int i=0;i<ICONS.size();i++)player.getInventory().setItem(i,new ItemStack(ModItems.ALL.get(ICONS.get(i)).get()));player.inventoryMenu.broadcastChanges();});
        world.getConnection().waitForClientboundPackets();context.getInput().pressKey(GLFW.GLFW_KEY_E);context.waitTicks(5);shot(context,"inventory");context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        final Map<String,Object> resources=new LinkedHashMap<>();row().put("runtime_icons",resources);
        for(String id:ICONS){
            resources.put(id,resource(context,"textures/item/"+id+".png"));
            world.getServer().runOnServer(server->{server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(-18,99,-18,18,112,18)).forEach(Entity::discard);var player=world.getConnection().getServerPlayer();player.getInventory().setItem(0,new ItemStack(ModItems.ALL.get(id).get()));player.getInventory().setSelectedSlot(0);player.inventoryMenu.broadcastChanges();});
            world.getConnection().waitForClientboundPackets();context.runOnClient(client->{client.player.setYRot(0);client.player.setXRot(20);});context.waitTicks(4);shot(context,id+"-held");context.getInput().pressKey(GLFW.GLFW_KEY_Q);context.waitTicks(8);context.runOnClient(client->client.player.setXRot(45));context.waitTicks(2);shot(context,id+"-dropped");
        }
    }

    private Map<String,Object> resource(ClientGameTestContext context,String path){return context.computeOnClient(client->{
        try{var id=Identifier.parse("warlockery:"+path);var found=client.getResourceManager().getResource(id);if(found.isEmpty())return Map.of("requested",id.toString(),"status","DIRECT_RESOURCE_MISSING_CHECK_RENDERER_VARIANT");byte[] bytes;try(var stream=found.get().open()){bytes=stream.readAllBytes();}var decoded=ImageIO.read(new ByteArrayInputStream(bytes));return Map.of("id",id.toString(),"sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),"width",decoded.getWidth(),"height",decoded.getHeight(),"pack",found.get().sourcePackId());}catch(Exception e){throw new AssertionError(e);}
    });}
    private void capture(ClientGameTestContext context,String name,Vec3 eye,Vec3 target)throws Exception{
        world.getServer().runOnServer(server->{var player=world.getConnection().getServerPlayer();player.teleportTo(server.overworld(),eye.x,eye.y-player.getEyeHeight(),eye.z,Set.of(),player.getYRot(),player.getXRot(),true);player.setDeltaMovement(Vec3.ZERO);});
        world.getConnection().waitForClientboundPackets();world.getConnection().waitForChunksRender();
        for(int i=0;i<100&&world.getServer().computeOnServer(server->awaitingTeleport(world.getConnection().getServerPlayer()));i++)context.waitTicks(1);
        context.waitFor(client->client.player!=null&&client.player.getEyePosition().distanceTo(eye)<.05,60);
        context.runOnClient(client->{var delta=target.subtract(client.player.getEyePosition());client.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));client.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.waitTicks(4);shot(context,name);
    }
    private void hud(ClientGameTestContext context,boolean hidden){if(context.computeOnClient(client->client.gui.hud.isHidden())!=hidden)context.getInput().pressKey(GLFW.GLFW_KEY_F1);}
    private void shot(ClientGameTestContext context,String name)throws Exception{ManualClientAcceptance.saveScreenshot(context,evidence,active+"-"+name,screenshots);write();}
    private Map<String,Object> row(){return results.get(active);}
    private void write()throws Exception{Files.writeString(evidence.resolve("visual-update.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
    private static boolean awaitingTeleport(ServerPlayer player){try{var field=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");field.setAccessible(true);return field.get(player.connection)!=null;}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}
