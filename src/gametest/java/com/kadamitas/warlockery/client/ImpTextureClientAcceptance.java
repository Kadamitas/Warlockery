package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.client.model.ImpModel;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import com.kadamitas.warlockery.entity.ImpEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Captures the real Imp renderer; staged pose and camera are visual prerequisites, not behavior evidence. */
public final class ImpTextureClientAcceptance implements FabricClientGameTest {
    private static final Identifier TEXTURE=Identifier.parse("warlockery:textures/entity/imp.png");
    private static final Vec3 IMP_POSITION=new Vec3(.5,100,.5);
    private final Map<String,Object> report=new LinkedHashMap<>();
    private final List<Map<String,Object>> views=new ArrayList<>();
    private final List<String> screenshots=new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private int impId;
    private net.minecraft.resources.ResourceKey<Level> stagedDimension=Level.OVERWORLD;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence=Path.of(System.getProperty("warlockery.clientEvidence",".codex-local/client-acceptance"))
            .resolve("imp-texture").resolve(UUID.randomUUID().toString());
        report.put("capture_completed",false);
        report.put("visual_review_status","PENDING_IMAGE_REVIEW");
        report.put("scope","Actual rendered Imp texture in a disposable world, viewed from six daylight angles/distances plus two real Nether dark-terrain views. No behavior, combat, acquisition, UV correctness or aesthetic approval is inferred from successful capture.");
        report.put("fixture","Actual registered ImpEntity with no AI, no target, no equipment, persistent and stationary at yaw 0; native model animation remains active. Neutral smooth-stone pad surrounded by grass in clear daylight, then a netherrack pad in the actual Nether dimension lit only by four embedded glowstone corners. Spectator camera placement and F1 HUD control only.");
        report.put("views",views); report.put("screenshots",screenshots);
        final int originalFov=context.computeOnClient(client -> client.options.fov().get());
        final boolean originalHudHidden=context.computeOnClient(client -> client.gui.hud.isHidden());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280,800);
            context.runOnClient(client -> client.options.fov().set(70));
            try(var created=context.worldBuilder().create()) {
                world=created;
                try {
                    stage(context);
                    for(int cycle=0;cycle<3 && !context.computeOnClient(client -> client.options.getCameraType().isFirstPerson());cycle++)
                        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F5);
                    check(context.computeOnClient(client -> client.options.getCameraType().isFirstPerson()),"Native first-person camera is active");
                    if(!context.computeOnClient(client -> client.gui.hud.isHidden())) context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
                    context.waitFor(client -> client.gui.hud.isHidden() && client.gui.screen()==null,30);
                    capture(context,"01-face-close",new Vec3(.5,100.78,1.80),new Vec3(.5,100.73,.5));
                    capture(context,"02-front-full",new Vec3(.5,100.66,2.85),new Vec3(.5,100.50,.5));
                    capture(context,"03-front-oblique",new Vec3(2.40,100.85,2.55),new Vec3(.5,100.50,.5));
                    capture(context,"04-side-wings",new Vec3(3.10,100.70,.5),new Vec3(.5,100.50,.5));
                    capture(context,"05-back-wings-tail",new Vec3(.5,100.75,-2.15),new Vec3(.5,100.50,.5));
                    capture(context,"06-normal-play-distance",new Vec3(1.25,101.62,6.50),new Vec3(.5,100.50,.5));
                    stageNether(context);
                    capture(context,"07-nether-oblique",new Vec3(2.40,100.85,2.55),new Vec3(.5,100.50,.5));
                    capture(context,"08-nether-play-distance",new Vec3(1.25,101.62,6.50),new Vec3(.5,100.50,.5));
                    check(views.size()==8 && screenshots.size()==8,"All eight native views captured");
                    report.put("capture_completed",true); write();
                } catch(Throwable failure) {
                    try { ManualClientAcceptance.saveScreenshot(context,evidence,"failure-in-world",screenshots); }
                    catch(Throwable captureFailure) { failure.addSuppressed(captureFailure); }
                    throw failure;
                } finally {
                    if(context.computeOnClient(client -> client.gui.hud.isHidden())!=originalHudHidden)
                        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
                }
            }
            System.out.println("WARLOCKERY_IMP_TEXTURE_CAPTURED "+evidence);
        } catch(Throwable failure) {
            report.put("failure",failure.toString());
            try { write(); } catch(Exception writeFailure) { failure.addSuppressed(writeFailure); }
            throw new AssertionError("Native Imp texture evidence: "+evidence,failure);
        } finally {
            world=null;
            context.runOnClient(client -> client.options.fov().set(originalFov));
        }
    }

    private void stage(final ClientGameTestContext context) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL,true);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 6000");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"weather clear");
            final var level=server.overworld();
            for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(-12,99,-12),new BlockPos(12,106,12))) {
                final var state=pos.getY()!=99?Blocks.AIR.defaultBlockState()
                    :Math.abs(pos.getX())<=4 && Math.abs(pos.getZ())<=4?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.GRASS_BLOCK.defaultBlockState();
                level.setBlockAndUpdate(pos,state);
            }
            final var player=world.getConnection().getServerPlayer();
            player.setGameMode(GameType.SPECTATOR); player.getInventory().clearContent(); player.removeAllEffects();
            player.teleportTo(level,.5,101,4.5,Set.of(),180,15,true); player.setDeltaMovement(Vec3.ZERO);
            final var entity=ModEntities.ALL.get("imp").get().create(level,EntitySpawnReason.COMMAND);
            check(entity instanceof ImpEntity,"Registry creates the actual ImpEntity");
            final ImpEntity imp=(ImpEntity)entity;
            imp.setNoAi(true); imp.setPersistenceRequired(); imp.setTarget(null);
            imp.snapTo(IMP_POSITION.x,IMP_POSITION.y,IMP_POSITION.z,0,0);
            imp.setYBodyRot(0); imp.setYHeadRot(0); imp.setDeltaMovement(Vec3.ZERO);
            check(level.addFreshEntity(imp),"Actual Imp is added to the native world");
            impId=imp.getId();
            final var profile=CreatureVisualProfile.forKind(CreatureKind.IMP);
            report.put("entity",Map.of("id","warlockery:imp","uuid",imp.getStringUUID(),"class",imp.getClass().getName(),
                "width",imp.getBbWidth(),"height",imp.getBbHeight(),"profile_width",profile.width(),"profile_height",profile.height(),
                "profile_archetype",profile.archetype().name(),"no_ai",imp.isNoAi(),"native_no_gravity",imp.isNoGravity()));
            report.put("sources",Map.of("entity","com.kadamitas.warlockery.entity.ImpEntity",
                "profile","com.kadamitas.warlockery.entity.CreatureVisualProfile.forKind(IMP)",
                "renderer_registration","com.kadamitas.warlockery.client.DedicatedCreatureRenderers",
                "model","com.kadamitas.warlockery.client.model.ImpModel"));
        });
        world.getConnection().waitForClientboundPackets(); world.getConnection().waitForChunksRender();
        context.waitFor(client -> client.level!=null && client.level.getEntity(impId) instanceof ImpEntity,100);
        report.put("loaded_texture",context.computeOnClient(client -> {
            try {
                final var resource=client.getResourceManager().getResource(TEXTURE).orElseThrow();
                final byte[] bytes;
                try(var input=resource.open()) { bytes=input.readAllBytes(); }
                final var decoded=ImageIO.read(new ByteArrayInputStream(bytes));
                check(decoded!=null && decoded.getWidth()==ImpModel.TEXTURE_WIDTH && decoded.getHeight()==ImpModel.TEXTURE_HEIGHT,
                    "Runtime resource dimensions match the native model UV atlas");
                final var entity=client.level.getEntity(impId);
                return Map.of("identifier",TEXTURE.toString(),"sha256",sha256(bytes),"width",decoded.getWidth(),"height",decoded.getHeight(),
                    "resource_pack",resource.sourcePackId(),"renderer_class",client.getEntityRenderDispatcher().getRenderer(entity).getClass().getName(),
                    "model_class_sha256",classHash(ImpModel.class),"renderer_registration_class_sha256",classHash(DedicatedCreatureRenderers.class));
            } catch(Exception failure) { throw new AssertionError("Cannot inspect the active Imp texture resource",failure); }
        }));
    }

    private void stageNether(final ClientGameTestContext context) {
        world.getServer().runOnServer(server -> {
            final var level=server.getLevel(Level.NETHER);
            check(level!=null,"The native server provides the actual Nether dimension");
            for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(-12,99,-12),new BlockPos(12,106,12))) {
                final var state=pos.getY()!=99?Blocks.AIR.defaultBlockState()
                    :Math.abs(pos.getX())==4 && Math.abs(pos.getZ())==4?Blocks.GLOWSTONE.defaultBlockState():Blocks.NETHERRACK.defaultBlockState();
                level.setBlockAndUpdate(pos,state);
            }
            final var player=world.getConnection().getServerPlayer();
            check(player.teleportTo(level,.5,101,4.5,Set.of(),180,15,true),"Native camera enters the actual Nether");
            player.setDeltaMovement(Vec3.ZERO);
            final var entity=ModEntities.ALL.get("imp").get().create(level,EntitySpawnReason.COMMAND);
            check(entity instanceof ImpEntity,"Registry creates the actual Nether ImpEntity");
            final ImpEntity imp=(ImpEntity)entity;
            imp.setNoAi(true); imp.setPersistenceRequired(); imp.setTarget(null);
            imp.snapTo(IMP_POSITION.x,IMP_POSITION.y,IMP_POSITION.z,0,0);
            imp.setYBodyRot(0); imp.setYHeadRot(0); imp.setDeltaMovement(Vec3.ZERO);
            check(level.addFreshEntity(imp),"Actual Imp is added to the native Nether");
            impId=imp.getId();
            report.put("nether_fixture",Map.of("dimension",level.dimension().identifier().toString(),
                "uuid",imp.getStringUUID(),"terrain","netherrack pad, four embedded glowstone corners, no sky light"));
        });
        stagedDimension=Level.NETHER;
        world.getConnection().waitForClientboundPackets(); world.getConnection().waitForChunksRender();
        context.waitFor(client -> client.level!=null && client.level.dimension().equals(Level.NETHER)
            && client.level.getEntity(impId) instanceof ImpEntity,200);
    }

    private void capture(final ClientGameTestContext context,final String name,final Vec3 eye,final Vec3 target) throws Exception {
        world.getServer().runOnServer(server -> {
            final var player=world.getConnection().getServerPlayer();
            final var level=server.getLevel(stagedDimension);
            check(level!=null,"Staged dimension exists on the native server");
            check(player.teleportTo(level,eye.x,eye.y-player.getEyeHeight(),eye.z,Set.of(),player.getYRot(),player.getXRot(),true),
                "Native camera teleport succeeds");
            check(player.level().dimension().equals(stagedDimension),"Camera is in the staged dimension");
            player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForClientboundPackets(); world.getConnection().waitForChunksRender();
        for(int tick=0;tick<100 && !world.getServer().computeOnServer(server -> {
            final var player=world.getConnection().getServerPlayer();
            return player.connection.hasClientLoaded() && !awaitingTeleport(player);
        });tick++) context.waitTicks(1);
        check(world.getServer().computeOnServer(server -> !awaitingTeleport(world.getConnection().getServerPlayer())),"Native camera teleport is acknowledged");
        context.waitFor(client -> client.player!=null && client.player.getEyePosition().distanceTo(eye)<.05,60);
        context.runOnClient(client -> {
            final Vec3 delta=target.subtract(client.player.getEyePosition());
            client.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
            client.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        });
        context.waitTicks(6);
        final Map<String,Object> view=context.computeOnClient(client -> {
            final var imp=client.level.getEntity(impId);
            check(imp instanceof ImpEntity && imp.isAlive() && !imp.isInvisible(),"The actual Imp is present and visible");
            check(imp.position().distanceTo(IMP_POSITION)<.02,"Static visual subject remains at its staged position");
            check(client.gui.screen()==null && client.gui.hud.isHidden(),"Screenshot uses the unobstructed native view");
            final double alignment=client.player.getViewVector(1).dot(target.subtract(client.player.getEyePosition()).normalize());
            check(alignment>.999,"Native camera points at the Imp's body/face target");
            check(client.player.getEyePosition().distanceTo(imp.position())<7,"Imp is within the intended close or play-distance view");
            final Map<String,Object> data=new LinkedHashMap<>();
            data.put("name",name); data.put("screenshot",evidence.resolve(name+".png").toString());
            data.put("camera_eye",client.player.getEyePosition().toString()); data.put("look_target",target.toString());
            data.put("camera_alignment",alignment); data.put("camera_distance",client.player.getEyePosition().distanceTo(imp.position()));
            data.put("entity_position",imp.position().toString()); data.put("entity_collision_bounds",imp.getBoundingBox().toString());
            data.put("entity_yaw",imp.getYRot()); data.put("entity_head_yaw",imp.getYHeadRot());
            data.put("sky_light",client.level.getBrightness(LightLayer.SKY,imp.blockPosition()));
            data.put("block_light",client.level.getBrightness(LightLayer.BLOCK,imp.blockPosition()));
            data.put("game_time",client.level.getGameTime()); data.put("fov",client.options.fov().get());
            data.put("window",List.of(1280,800)); data.put("hud_hidden",client.gui.hud.isHidden());
            return data;
        });
        ManualClientAcceptance.saveScreenshot(context,evidence,name,screenshots);
        views.add(view); write();
    }

    private static boolean awaitingTeleport(final ServerPlayer player) {
        try {
            final var field=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");
            field.setAccessible(true); return field.get(player.connection)!=null;
        } catch(ReflectiveOperationException failure) { throw new AssertionError("Cannot observe native camera acknowledgement",failure); }
    }
    private static String sha256(final byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String classHash(final Class<?> type) throws Exception {
        try(var stream=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")) {
            if(stream==null) throw new IllegalStateException("Missing runtime class "+type.getName());
            return sha256(stream.readAllBytes());
        }
    }
    private void write() throws Exception { Files.writeString(evidence.resolve("imp-texture.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
    private static void check(final boolean valid,final String message) { if(!valid) throw new AssertionError(message); }
}
