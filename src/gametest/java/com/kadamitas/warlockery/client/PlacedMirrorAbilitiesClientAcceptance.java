package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.InteractiveUtilityBlock;
import com.kadamitas.warlockery.block.MagicMirrorMemory;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.crafting.AltarPowerNetwork;
import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Native placed-mirror controls, with read-only observation of their resulting state. */
public final class PlacedMirrorAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final List<String> MIRRORS = List.of("mirrorblock", "mirrorblock2", "mirrorwall");
    private static final BlockPos MIRROR = new BlockPos(0, 100, 0);
    private static final BlockPos PARTNER = new BlockPos(8, 100, 0);
    private static final BlockPos ALTAR = new BlockPos(-5, 100, 2);
    private static final Vec3 START = new Vec3(.5, 100, -2.5);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private volatile ReflectionObservation observation;
    private volatile TravelObservation travelObservation;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("placed-mirror-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                final var current = observation;
                if (current != null && current.player.level() == level) current.loaded(entity);
            });
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final var current=travelObservation;
                if(current!=null && current.player.level().getServer()==server) current.sample("server_end_tick");
            });
            final String configured = System.getProperty("warlockery.placedMirrorIds", "");
            final Set<String> requested = configured.isBlank() ? Set.copyOf(MIRRORS) : Arrays.stream(configured.split(","))
                .map(String::strip).map(id -> id.replaceFirst("^warlockery:", "")).collect(Collectors.toSet());
            check(!requested.isEmpty() && MIRRORS.containsAll(requested), "Requested mirrors have native scenarios");
            for (String id : MIRRORS) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", requested.contains(id) ? "NOT_RUN" : "NOT_SELECTED");
                row.put("acquisition_status", "NOT_RUN");
                row.put("persistence_status", "NOT_RUN");
                row.put("distinct_visitor_history_status", "NOT_RUN");
                row.put("combat_damage_status", "NOT_RUN");
                results.put(id, row);
            }
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (String id : MIRRORS) {
                if (!requested.contains(id)) continue;
                final Map<String, Object> row = results.get(id);
                row.put("status", "RUNNING"); write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context, row);
                        readGuide(context, id, row);
                        place(context, id, MIRROR, 0);
                        row.put("placement_status", "PASSED");
                        final UUID witness = witness();
                        reading(context, id, witness, row);
                        replication(context, id, row);
                        masquerade(context, id, witness, row);
                        pairing(context, id, row);
                        blockedPartner(context, id, row);
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        fail(id, row, failure);
                        try { screenshot(context, id + "-failure-in-world"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
                        if (travelObservation != null) row.put("paired_travel_observation",serverValue(player -> travelObservation.report()));
                        travelObservation = null;
                        if (observation != null) row.put("reflection_observation", serverValue(player -> observation.report()));
                        observation = null;
                        write(false);
                    }
                } catch (Throwable failure) { fail(id, row, failure); }
                finally { observation = null; travelObservation = null; world = null; }
                write(false);
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_PLACED_MIRROR_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), stackTrace(failure)); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Native placed mirror evidence: " + evidence, failure);
        }
    }

    private void stage(final ClientGameTestContext context, final Map<String, Object> row) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.HARD, true);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent(); player.removeAllEffects();
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            player.setHealth(100); player.setAbsorptionAmount(0); player.getFoodData().setFoodLevel(20);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 99, -8), new BlockPos(12, 108, 8)))
                player.level().setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.teleportTo(START.x, START.y, START.z); player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets();
        row.put("fixture", "Fresh disposable Survival world; platform, input items, a named cow and later six altar blocks/reserve staged. Actor has 100 maximum health with ordinary damage enabled. Mirror placement, readings, memory entries, samples, outputs, names, travel and reflections arise only from native mouse/key actions.");
        row.put("not_run", List.of("crafting acquisition", "save/reload persistence", "multiple distinct visitors", "own-sample masquerade restoration", "goblet sample", "reflection combat damage", "pair range boundary and unloaded partner"));
    }

    private UUID witness() {
        return serverValue(player -> {
            final Cow cow = (Cow) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:cow"))
                .create(player.level(), EntitySpawnReason.COMMAND);
            check(cow != null, "Cow prerequisite exists");
            cow.setPos(3.5, 100, -.5); cow.setNoAi(true); cow.setPersistenceRequired();
            cow.setCustomName(Component.literal("Mirror Witness")); cow.setHealth(cow.getMaxHealth());
            cow.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(40);
            cow.setAbsorptionAmount(40);
            check(cow.getAbsorptionAmount() == 40, "Named witness has actual absorption for the mirror reading prerequisite");
            player.level().addFreshEntity(cow); return cow.getUUID();
        });
    }

    private void reading(final ClientGameTestContext context, final String id, final UUID witness, final Map<String, Object> row) throws Exception {
        emptyHand(context);
        check(serverValue(player -> memory(player).isEmpty()), "No mirror visitor is preloaded");
        final String expected = translated(context, "message.warlockery.mirror.fairest", Component.literal("Mirror Witness"),
            Component.translatable("message.warlockery.spirit_locator.direction.east"));
        for (int visit = 0; visit < 2; visit++) {
            clearChat(context); use(context, Vec3.atCenterOf(MIRROR), MIRROR);
            row.put("reading_observed_overlay", overlay(context));
            row.put("reading_observed_memory", serverValue(PlacedMirrorAbilitiesClientAcceptance::memory));
            await(context, player -> memory(player).equals(List.of(player.getName().getString())), 30,
                "Native empty-hand mirror use records the visitor; overlay=" + overlay(context));
            awaitChat(context, expected);
            check(serverValue(player -> memory(player).equals(List.of(player.getName().getString()))),
                "Actual empty-hand use records one visitor and repeat use deduplicates that visitor");
        }
        row.put("reading_status", "PASSED"); row.put("memory_retention_dedup_status", "PASSED");
        row.put("fairest_message", expected); row.put("fairest_target_uuid", witness.toString());
        row.put("visitor_memory", serverValue(PlacedMirrorAbilitiesClientAcceptance::memory));
        row.put("reading_fixture", "Named full-health cow with 40 absorption has higher fairness than the full-health player and stands east of the mirror. Memory observation only reads the resulting saved-data map.");
        screenshot(context, id + "-reading-and-memory");
    }

    @SuppressWarnings("unchecked")
    private static List<String> memory(final ServerPlayer player) {
        final Map<Long, List<String>> visitors = (Map<Long, List<String>>) field(MagicMirrorMemory.get(player.level()), "visitors");
        return List.copyOf(visitors.getOrDefault(MIRROR.asLong(), List.of()));
    }

    private void replication(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        supply(context, 1, item("ingredient_quartz_sphere"));
        check(serverValue(player -> AltarPowerNetwork.available(player.level(), MIRROR) == 0), "Unpowered fixture has no reachable altar reserve");
        use(context, Vec3.atCenterOf(MIRROR), MIRROR);
        awaitOverlay(context, translated(context, "message.warlockery.mirror.needs_power"));
        check(serverValue(player -> count(player, item("ingredient_quartz_sphere")) == 1 && count(player, item("replication_charge")) == 0),
            "Unpowered native use preserves the sphere and creates no charge");
        row.put("unpowered_refusal_status", "PASSED");
        screenshot(context, id + "-sphere-unpowered-refusal");
        server(player -> {
            for (BlockPos pos : altarPositions()) player.level().setBlockAndUpdate(pos, ModBlocks.ALTAR.get().defaultBlockState());
        });
        context.waitTicks(41);
        check(serverValue(player -> altarPositions().stream().allMatch(pos -> altar(player, pos).isMultiblockValid())), "Six actual altar blocks form a valid multiblock");
        look(context, Vec3.atCenterOf(MIRROR)); assertBlockPointer(context, MIRROR);
        for (int tick = 0; tick < 45 && serverValue(player -> player.level().getGameTime() % 40) > 5; tick++) context.waitTicks(1);
        check(serverValue(player -> player.level().getGameTime() % 40) <= 5, "Power exchange starts before the next recharge boundary");
        server(player -> {
            for (BlockPos pos : altarPositions()) {
                final var altar = altar(player, pos); check(altar.consumePower(altar.availablePower()), "Clear prerequisite reserve");
            }
            check(altar(player, ALTAR).receivePower(500) == 500, "Stage exactly 500 spendable altar power");
        });
        final long beforeTick = serverValue(player -> player.level().getGameTime());
        check(serverValue(player -> totalPower(player) == 500 && AltarPowerNetwork.available(player.level(), MIRROR) == 500), "Exactly 500 total power is reachable before native use");
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(2);
        await(context, player -> count(player, item("replication_charge")) == 1, 12, "Native powered mirror use creates one Replication Charge");
        final long afterTick = serverValue(player -> player.level().getGameTime());
        check(beforeTick / 40 == afterTick / 40, "Exact power observation excludes natural recharge");
        check(serverValue(player -> count(player, item("ingredient_quartz_sphere")) == 0 && count(player, item("replication_charge")) == 1
            && totalPower(player) == 0), "One sphere and exactly 500 actual power become exactly one charge");
        awaitOverlay(context, translated(context, "message.warlockery.mirror.replication_captured"));
        row.put("powered_conversion_status", "PASSED");
        row.put("conversion", Map.of("sphere_before", 1, "sphere_after", 0, "charge_before", 0, "charge_after", 1,
            "total_altar_power_before", 500, "total_altar_power_after", 0, "game_tick_before", beforeTick, "game_tick_after", afterTick));
        row.put("power_fixture", "Six staged altar blocks form naturally; every reserve is cleared, then one receives 500 through the existing power API. Native exchange is observed within one 40-tick recharge interval.");
        screenshot(context, id + "-sphere-powered-conversion");
    }

    private static List<BlockPos> altarPositions() {
        final List<BlockPos> result = new ArrayList<>();
        for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) result.add(ALTAR.offset(x, 0, z));
        return result;
    }
    private static AltarBlockEntity altar(final ServerPlayer player, final BlockPos pos) { return (AltarBlockEntity) player.level().getBlockEntity(pos); }
    private static int totalPower(final ServerPlayer player) { return altarPositions().stream().mapToInt(pos -> altar(player, pos).getPower()).sum(); }

    private void masquerade(final ClientGameTestContext context, final String id, final UUID witness, final Map<String, Object> row) throws Exception {
        supply(context, 2, item("sympathetic_vial"));
        check(serverValue(player -> SympatheticBinding.read(player.getMainHandItem()).isEmpty() && player.getCustomName() == null), "Vial and actor start without an injected sample or disguise");
        position(context, new Vec3(3.5, 100, -2.5));
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> java.util.stream.StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
            .anyMatch(entity -> entity.getUUID().equals(witness)), 40);
        look(context, serverValue(player -> player.level().getEntity(witness).getBoundingBox().getCenter()));
        check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(witness)), "Native pointer hits the named cow for sampling");
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(3);
        await(context, player -> SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targetId().equals(witness)
            && binding.targetName().equals("Mirror Witness")).isPresent(), 20, "Native vial use captures the exact living subject");
        position(context, START); use(context, Vec3.atCenterOf(MIRROR), MIRROR);
        await(context, player -> player.getCustomName() != null && player.getCustomName().getString().equals("Mirror Witness"), 20,
            "Native mirror use applies the sampled subject's display name");
        check(serverValue(player -> count(player, item("sympathetic_vial")) == 0
            && WarlockeryEntityData.get(player).getStringOr("WarlockeryMirrorMasquerade", "").equals("Mirror Witness")),
            "Mirror consumes one native sample and records its resulting masquerade");
        awaitOverlay(context, translated(context, "message.warlockery.mirror.masquerade_applied", "Mirror Witness"));
        row.put("native_vial_masquerade_status", "PASSED"); row.put("sampled_subject_uuid", witness.toString());
        screenshot(context, id + "-native-vial-masquerade");
    }

    private void pairing(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final String partner = MIRRORS.get((MIRRORS.indexOf(id) + 1) % MIRRORS.size());
        position(context, new Vec3(8.5, 100, -2.5)); place(context, partner, PARTNER, 3);
        position(context, START); emptyHand(context);
        check(serverValue(player -> player.level().getBlockState(PARTNER.above()).isAir() && player.level().getBlockState(PARTNER.above(2)).isAir()), "Different mirror design has two clear arrival blocks");
        final double supportedY=serverValue(player -> {
            final var state=player.level().getBlockState(PARTNER);
            final var shape=state.getCollisionShape(player.level(),PARTNER);
            row.put("partner_collision_boxes",shape.toAabbs().stream().map(box -> box.move(PARTNER).toString()).toList());
            row.put("partner_block_state",state.toString());
            row.put("native_requested_arrival",new Vec3(PARTNER.getX()+.5,PARTNER.getY()+1,PARTNER.getZ()+.5).toString());
            return shape.toAabbs().stream().filter(box -> box.minX<.8 && box.maxX>.2 && box.minZ<.8 && box.maxZ>.2)
                .mapToDouble(box -> PARTNER.getY()+box.maxY).max().orElseThrow(() -> new AssertionError("Actual partner has collision support under the arrival footprint"));
        });
        check(supportedY==PARTNER.getY()+1,"Actual mirror collision supports the documented exact arrival at y101");
        server(player -> { travelObservation=new TravelObservation(player); travelObservation.sample("before_native_use"); });
        row.put("paired_travel_client_before",clientPosition(context));
        crouchUse(context);
        server(player -> travelObservation.sample("after_native_use"));
        row.put("paired_travel_client_after_use",clientPosition(context));
        final Vec3 destination = new Vec3(PARTNER.getX()+.5,supportedY,PARTNER.getZ()+.5);
        int stableTicks=0;
        for(int tick=0;tick<30 && stableTicks<4;tick++) {
            final boolean settled=serverValue(player -> stableArrival(player,destination))
                && context.computeOnClient(client -> client.player!=null && client.player.position().distanceTo(destination)<.1);
            stableTicks=settled?stableTicks+1:0;
            context.waitTicks(1);
        }
        row.put("paired_travel_observation",serverValue(player -> travelObservation.report()));
        row.put("paired_travel_client_settled",clientPosition(context));
        row.put("supported_arrival",destination.toString());
        check(stableTicks==4,"Native crouch use reaches the actual partner center, acknowledges the teleport, and remains collision-supported for four ticks; observed="+serverValue(player -> player.position().toString()));
        travelObservation=null;
        awaitOverlay(context, translated(context, "message.warlockery.mirror.paired_travel"));
        row.put("cross_design_pairing_status", "PASSED");
        row.put("paired_travel", Map.of("source", id, "destination", partner, "mirror_distance", 8,
            "actual_position", serverValue(player -> player.position().toString())));
        screenshot(context, id + "-native-cross-design-travel");
    }

    private void blockedPartner(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        position(context, START); emptyHand(context);
        server(player -> {
            player.level().setBlockAndUpdate(PARTNER.above(), Blocks.STONE.defaultBlockState());
            player.level().setBlockAndUpdate(PARTNER.above(2), Blocks.STONE.defaultBlockState());
            observation = new ReflectionObservation(player);
        });
        crouchUse(context);
        await(context, player -> observation.reflections.size() == 1, 20, "A blocked usable-range partner causes one actual native Glass Doppelganger summon");
        check(serverValue(player -> {
            final Map<String, Object> spawned = observation.reflections.values().iterator().next();
            return player.getStringUUID().equals(spawned.get("combat_target")) && player.getStringUUID().equals(spawned.get("reflected_target"))
                && Boolean.TRUE.equals(spawned.get("normal_ai")) && Boolean.TRUE.equals(spawned.get("hostile_category"));
        }), "Actual mirror summon assigns the activating player to the live hostile reflection without fixture targeting");
        check(serverValue(player -> observation.reflections.keySet().stream().allMatch(uuid -> player.level().getEntity(uuid) instanceof Mob mob && mob.isAlive())
            && player.position().distanceTo(START) < 1), "Reflection remains alive and blocked partner does not teleport the player");
        awaitOverlay(context, translated(context, "message.warlockery.mirror.reflection_emerged"));
        row.put("blocked_partner_reflection_status", "PASSED");
        row.put("reflection_fixture", "The previously usable partner remains placed; two staged stone blocks obstruct its arrival space. Origin remains clear. Passive entity-load observation records the real spawn before its ordinary mimic AI can change targeting; no attack damage is claimed.");
        screenshot(context, id + "-blocked-partner-native-reflection");
    }

    private void crouchUse(final ClientGameTestContext context) {
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.waitTicks(3);
        try { use(context, Vec3.atCenterOf(MIRROR), MIRROR); }
        finally { context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); context.waitTicks(2); }
    }
    private static boolean stableArrival(final ServerPlayer player,final Vec3 destination) {
        return Math.abs(player.getX()-destination.x)<.02 && Math.abs(player.getZ()-destination.z)<.02
            && Math.abs(player.getY()-destination.y)<.02 && field(player.connection,"awaitingPositionFromClient")==null
            && player.level().noCollision(player)
            && player.level().getBlockCollisions(player,player.getBoundingBox().move(0,-.02,0)).iterator().hasNext();
    }
    private static Map<String,Object> clientPosition(final ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            if(client.player==null) return Map.of("player_present",false);
            return Map.of("player_present",true,"position",client.player.position().toString(),"velocity",client.player.getDeltaMovement().toString(),
                "dimension",client.player.level().dimension().identifier().toString(),"on_ground",client.player.onGround(),
                "yaw",client.player.getYRot(),"pitch",client.player.getXRot(),"screen",String.valueOf(client.gui.screen()));
        });
    }
    private static final class TravelObservation {
        final ServerPlayer player;
        final List<Map<String,Object>> samples=new ArrayList<>();
        TravelObservation(final ServerPlayer player) { this.player=player; }
        void sample(final String phase) {
            if(samples.size()>=80) return;
            final Map<String,Object> row=new LinkedHashMap<>();
            row.put("phase",phase); row.put("game_tick",player.level().getGameTime());
            row.put("position",player.position().toString()); row.put("velocity",player.getDeltaMovement().toString());
            row.put("dimension",player.level().dimension().identifier().toString()); row.put("on_ground",player.onGround());
            row.put("fall_distance",player.fallDistance); row.put("no_collision",player.level().noCollision(player));
            row.put("bounding_box",player.getBoundingBox().toString()); row.put("feet_block",player.level().getBlockState(player.blockPosition()).toString());
            row.put("support_block",player.level().getBlockState(player.blockPosition().below()).toString());
            row.put("awaiting_position_from_client",String.valueOf(field(player.connection,"awaitingPositionFromClient")));
            row.put("teleport_id",field(player.connection,"awaitingTeleport")); row.put("teleport_tick",field(player.connection,"awaitingTeleportTime"));
            row.put("display_name",player.getDisplayName().getString()); row.put("custom_name",String.valueOf(player.getCustomName()));
            row.put("masquerade",WarlockeryEntityData.get(player).getStringOr("WarlockeryMirrorMasquerade",""));
            samples.add(row);
        }
        Map<String,Object> report() { return Map.of("observer","Passive server end-tick and boundary snapshots; no player, terrain, velocity or teleport state changed","samples",List.copyOf(samples)); }
    }
    private static final class ReflectionObservation {
        final ServerPlayer player;
        final Map<UUID, Map<String, Object>> reflections = new LinkedHashMap<>();
        ReflectionObservation(final ServerPlayer player) { this.player = player; }
        void loaded(final Entity entity) {
            if (entity instanceof Mob mob && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("warlockery:glass_doppelganger"))
                reflections.put(entity.getUUID(), Map.of("type", "warlockery:glass_doppelganger", "normal_ai", !mob.isNoAi(),
                    "hostile_category", entity.getType().getCategory() == MobCategory.MONSTER,
                    "combat_target", mob.getTarget() == null ? "none" : mob.getTarget().getStringUUID(),
                    "reflected_target", WarlockeryEntityData.get(mob).getStringOr("WarlockeryReflectedTarget", ""),
                    "name", mob.getDisplayName().getString(), "position", mob.position().toString()));
        }
        Map<String, Object> report() { return Map.of("actual_spawns", Map.copyOf(reflections), "observer", "Passive server entity-load event, no behavior injection"); }
    }

    private void readGuide(final ClientGameTestContext context, final String section, final Map<String, Object> row) throws Exception {
        final var profile = ManualProfile.profiles().stream().filter(book -> book.sections().contains(section)).findFirst().orElseThrow();
        supply(context, 0, item(profile.id()));
        context.runOnClient(client -> client.player.setXRot(-70)); context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, section);
        final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, section).body().getString());
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class); method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        check(!body.isBlank() && pages > 0, "Indexed placed mirror instructions contain actual pages");
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection")) && (int) field(client.gui.screen(), "bodyPage") == expected), "Native book buttons visit every mirror guide page");
            screenshot(context, section + "-guide-" + page);
        }
        row.put("guide", Map.of("book", profile.id(), "section", section, "text", body, "pages_read", pages)); row.put("guide_status", "PASSED");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE); context.waitTicks(3);
    }

    private void place(final ClientGameTestContext context, final String id, final BlockPos pos, final int slot) {
        supply(context, slot, item(id));
        use(context, new Vec3(pos.getX() + .5, pos.getY() - .001, pos.getZ() + .5), pos.below());
        await(context, player -> BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()).toString().equals("warlockery:" + id), 30,
            "Native placement creates exact mirror design " + id);
        check(serverValue(player -> player.getInventory().getItem(slot).isEmpty()), "Survival placement consumes exactly one mirror item");
    }
    private void supply(final ClientGameTestContext context, final int slot, final Item item) {
        server(player -> { player.getInventory().setItem(slot, new ItemStack(item)); player.inventoryMenu.broadcastChanges(); }); sync(context, slot);
    }
    private void emptyHand(final ClientGameTestContext context) {
        sync(context, 8); check(serverValue(player -> player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()), "Both hands are actually empty");
    }
    private void sync(final ClientGameTestContext context, final int slot) {
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1 + slot); context.waitTicks(2);
    }
    private void position(final ClientGameTestContext context, final Vec3 point) {
        server(player -> { player.teleportTo(point.x, point.y, point.z); player.setDeltaMovement(Vec3.ZERO); });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
    }
    private static void look(final ClientGameTestContext context, final Vec3 point) {
        context.runOnClient(client -> {
            final Vec3 delta = point.subtract(client.player.getEyePosition());
            client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        }); context.waitTicks(2);
    }
    private static void assertBlockPointer(final ClientGameTestContext context, final BlockPos expected) {
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(expected)), "Native pointer targets intended block " + expected);
    }
    private static void use(final ClientGameTestContext context, final Vec3 point, final BlockPos expected) {
        look(context, point); assertBlockPointer(context, expected); context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(2);
    }
    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> predicate, final int ticks, final String message) {
        for (int remaining = ticks; remaining > 0 && !serverValue(predicate::test); remaining -= 2) context.waitTicks(2);
        check(serverValue(predicate::test), message);
    }
    private static String translated(final ClientGameTestContext context, final String key, final Object... arguments) {
        return context.computeOnClient(client -> Component.translatable(key, arguments).getString());
    }
    private static void clearChat(final ClientGameTestContext context) { context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false)); }
    private static List<String> chat(final ClientGameTestContext context) {
        return context.computeOnClient(client -> ((List<?>) field(client.gui.hud.getChat(), "allMessages")).stream()
            .map(message -> ((net.minecraft.client.multiplayer.chat.GuiMessage) message).content().getString()).toList());
    }
    private static void awaitChat(final ClientGameTestContext context, final String expected) {
        for (int tick = 0; tick < 40 && !chat(context).contains(expected); tick++) context.waitTicks(1);
        check(chat(context).contains(expected), "Rendered chat receives expected mirror reading: " + expected + "; actual=" + chat(context));
    }
    private static String overlay(final ClientGameTestContext context) {
        return context.computeOnClient(client -> { final Object value = field(client.gui.hud, "overlayMessageString"); return value instanceof Component text ? text.getString() : String.valueOf(value); });
    }
    private static void awaitOverlay(final ClientGameTestContext context, final String expected) {
        for (int tick = 0; tick < 30 && !overlay(context).equals(expected); tick++) context.waitTicks(1);
        check(overlay(context).equals(expected), "Rendered overlay reports mirror result: " + expected + "; actual=" + overlay(context));
    }
    private static Object field(final Object object, final String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) try {
            final var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
        } catch (NoSuchFieldException ignored) { } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        throw new AssertionError("Missing observed field " + name);
    }
    private static Item item(final String id) { return ModItems.ALL.get(id).get(); }
    private static int count(final ServerPlayer player, final Item item) {
        int result = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) if (player.getInventory().getItem(slot).is(item)) result += player.getInventory().getItem(slot).getCount();
        return result;
    }
    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T serverValue(final Function<ServerPlayer, T> action) { return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer())); }
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots); }
    private static String stackTrace(final Throwable failure) { final var text = new StringWriter(); failure.printStackTrace(new PrintWriter(text)); return text.toString(); }
    private void fail(final String id, final Map<String, Object> row, final Throwable failure) {
        row.put("status", "FAILED"); row.put("failure", stackTrace(failure)); failures.add(id + ": " + failure);
    }
    private static String classHash(final Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing loaded class bytes " + type);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }
    private void write(final boolean finished) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", finished); report.put("selected_native_scenarios_passed", finished && failures.isEmpty());
        report.put("all_three_native_scenarios_passed", finished && results.size() == MIRRORS.size() && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("execution", "Native rendered Fabric development-classpath client; staged prerequisites with real mouse/key manual, placement, block and living-entity interactions. Resulting memory, binding, items, power, travel and summons are observed without outcome injection.");
        report.put("class_sha256", Map.of("test", classHash(PlacedMirrorAbilitiesClientAcceptance.class), "mirror_block", classHash(InteractiveUtilityBlock.class),
            "memory", classHash(MagicMirrorMemory.class), "altar", classHash(AltarBlockEntity.class),
            "vial", classHash(com.kadamitas.warlockery.item.SympatheticVialItem.class),
            "reflection", classHash(com.kadamitas.warlockery.entity.GlassDoppelgangerEntity.class)));
        report.put("mirrors", results); report.put("failures", failures); report.put("screenshots", screenshots);
        final Path pending = evidence.resolve("placed-mirror-abilities.json.tmp");
        Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(pending, evidence.resolve("placed-mirror-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean value, final String message) { if (!value) throw new AssertionError(message); }
}
