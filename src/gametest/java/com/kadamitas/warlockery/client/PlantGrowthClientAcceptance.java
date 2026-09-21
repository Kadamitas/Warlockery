package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.*;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.*;

/** Real planting, ordinary accelerated random ticks, native bonemeal, harvest and extracted Mandrake combat. */
public final class PlantGrowthClientAcceptance implements FabricClientGameTest {
    private static final BlockPos PLANT = new BlockPos(0, 100, 0);
    private static final Vec3 CAMERA = new Vec3(.5, 100, -2.5);
    private static final List<String> TREES = List.of("alder_sapling", "hawthorn_sapling", "rowan_sapling");
    private static final List<String> EXTRA = List.of("somniancotton", "plantmine", "vine", "hex_sapling", "voidbramble", "pitgrass");
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private String active;

    @Override public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("plant-growth").resolve(UUID.randomUUID().toString());
        final List<String> census = new ArrayList<>(ContentCatalog.CROPS.stream().sorted().toList());
        census.addAll(TREES); census.addAll(MagicalPlantBlockFactory.supportedIds().stream().sorted().toList()); census.addAll(EXTRA); census.addAll(List.of("ingredient_verdant_catalyst", "ingredient_verdant_catalyst_prime"));
        final String configured = System.getProperty("warlockery.plantIds", "");
        final Set<String> selected = configured.isBlank() ? Set.copyOf(census)
            : new HashSet<>(Arrays.stream(configured.split(",")).map(String::trim).toList());
        check(census.containsAll(selected), "Unknown requested plant case");
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (String id : census) results.put(id, new LinkedHashMap<>(Map.of("status", selected.contains(id) ? "NOT_RUN" : "NOT_SELECTED")));
            for (String id : census) {
                if (!selected.contains(id)) continue;
                active = id; row().put("status", "RUNNING"); write();
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context);
                        row().put("implementation", id.startsWith("ingredient_verdant") ? item(id).getClass().getSimpleName() : block(id).getClass().getSimpleName());
                        readGuide(context, guide(id));
                        if (id.startsWith("ingredient_verdant")) catalyst(context, id);
                        else if (ContentCatalog.CROPS.contains(id)) crop(context, id);
                        else if (block(id) instanceof SaplingBlock) tree(context, id);
                        else special(context, id);
                        row().putIfAbsent("coverage", "Native placement/growth/harvest branches recorded below; acquisition, mutation recipes, persistence and statistical rates excluded.");
                        row().put("status", "NOT_IMPLEMENTED".equals(row().get("growth_status")) ? "PARTIAL" : "PASSED");
                    } catch (Throwable failure) {
                        row().put("status", "FAILED"); row().put("failure", failure.toString());
                        row().put("failure_stack", Arrays.stream(failure.getStackTrace()).map(Object::toString).toList());
                        failures.add(id + ": " + failure);
                        try { shot(context, "failure"); } catch (Throwable ignored) { }
                    } finally { release(context); write(); }
                } finally { world = null; }
            }
            write(); check(failures.isEmpty(), String.join("; ", failures));
            System.out.println("WARLOCKERY_PLANT_GROWTH_FINISHED " + evidence);
        } catch (Throwable failure) { throw new AssertionError("Plant evidence: " + evidence, failure); }
    }

    private void stage(final ClientGameTestContext context) {
        world.getConnection().waitForChunksRender();
        server(player -> {
            player.setGameMode(GameType.SURVIVAL); player.setPermanentlyInvulnerable(false); player.getInventory().clearContent();
            player.level().getGameRules().set(GameRules.SPAWN_MOBS, false, player.level().getServer());
            player.level().getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, player.level().getServer());
            player.level().clockManager().setTotalTicks(player.level().dimensionType().defaultClock().orElseThrow(), 6000L);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-9, 98, -9), new BlockPos(9, 116, 9)))
                player.level().setBlockAndUpdate(pos, pos.getY() < 100 ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
        });
        position(context, CAMERA);
        row().put("fixture", "Fresh Survival world; supplied planting material/tools, soil, water and daylight, with a vulnerable observer. Native randomTickSpeed gamerule may be accelerated to 512; no age, growth callback, mature block, creature outcome or loot is injected.");
    }

    private void crop(final ClientGameTestContext context, final String id) throws Exception {
        final Item seed = ModItems.seedFor(block(id)).orElseThrow().asItem();
        server(player -> {
            for (BlockPos pos : BlockPos.betweenClosed(PLANT.offset(-3,-1,-3), PLANT.offset(3,-1,3)))
                player.level().setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
            player.level().setBlockAndUpdate(PLANT.offset(3,-1,0), Blocks.WATER.defaultBlockState());
        });
        plant(context, seed, PLANT.below(), id);
        check(age() == 0, "Native seed creates an immature age-zero crop"); shot(context, "seedling");
        supply(context, ItemStack.EMPTY); breakBlock(context, PLANT);
        check(total(seed) >= 1, "Immature native harvest returns planting material");
        row().put("immature_drops", items());
        // Collect the real returned seed and use that stack to replant.
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); });
        context.getInput().holdKeyFor(com.mojang.blaze3d.platform.InputConstants.KEY_W, 12);
        await(context, player -> inventoryCount(player, seed) > 0, 40, "Native walk collects the immature crop seed");
        position(context, CAMERA); selectExisting(context, seed); useBlock(context, PLANT.below(), true);
        await(context, player -> state(player).is(block(id)), 40, "Returned seed replants natively");
        ticks(512); await(context, player -> age(player) > 0, 600, "Ordinary random ticks advance the seedling"); ticks(0);
        row().put("intermediate_age", age()); shot(context, "intermediate");
        ticks(512); await(context, player -> age(player) == CropBlock.MAX_AGE, 1800, "Ordinary random ticks fully mature the crop"); ticks(0);
        shot(context, "mature"); row().put("natural_maturity", state().toString());
        if (id.equals("mandrake")) {
            mandrake(context, seed);
            return;
        }
        supply(context, ItemStack.EMPTY); breakBlock(context, PLANT);
        final Item produce = item(switch (id) {
            case "garlicplant" -> "garlic"; case "snowbell" -> "minecraft:snowball";
            case "dreamroot" -> "seedsdreamroot"; default -> "ingredient_" + id;
        });
        check(total(produce) > 0, "Mature harvest supplies its actual recipe ingredient");
        row().put("mature_drops", items()); shot(context, "harvest-drops");
        clearLoose(); plant(context, seed, PLANT.below(), id); boneMature(context);
        row().put("bonemeal_maturity", state().toString()); shot(context, "bonemeal-mature");
        if(id.equals("snowbell")) {
            final List<Object> harvests=new ArrayList<>();
            boolean needle=false;
            for(int attempt=0;attempt<32 && !needle;attempt++) {
                if(attempt>0){plant(context,seed,PLANT.below(),id);boneMature(context);}
                supply(context,ItemStack.EMPTY);breakBlock(context,PLANT);
                needle=total(item("ingredient_icy_needle"))>0;
                harvests.add(Map.of("attempt",attempt+1,"drops",items(),"needle",needle));
                if(!needle)clearLoose();
            }
            row().put("needle_harvest_trials",harvests);
            check(needle,"At least one actual Icy Needle must drop within 32 mature native harvests (20% base chance; no Fortune staged)");
            row().put("needle_probability_scope","Observed a chance-based mature crop drop; this sample does not establish the configured probability or Fortune scaling.");
            shot(context,"icy-needle-harvest");
        }
        row().put("regrowth", "Harvest removes the crop; a returned seed was collected and replanted, not auto-regrown.");
    }

    private void mandrake(final ClientGameTestContext context, final Item seed) throws Exception {
        row().put("combat_fixture", "Vulnerable survival player; full health staged before each independent harvest trial; native sword attacks against the live extracted creature.");
        final List<Object> attempts = new ArrayList<>(); boolean killed = false; boolean rootObtained = false;
        row().put("day_trials", attempts);
        for (int attempt = 0; attempt < 16 && (!killed || !rootObtained); attempt++) {
            server(player -> player.setHealth(player.getMaxHealth()));
            if (attempt > 0) { clearLoose(); plant(context, seed, PLANT.below(), "mandrake"); boneMature(context); }
            supply(context, ItemStack.EMPTY); breakBlock(context, PLANT);
            final UUID spawned = value(player -> player.level().getEntitiesOfClass(LivingEntity.class, new AABB(PLANT).inflate(5),
                mob -> BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath().equals("mandrake") && mob.isAlive())
                .stream().map(Entity::getUUID).findFirst().orElse(null));
            final Map<String,Object> trial = new LinkedHashMap<>(); trial.put("attempt", attempt + 1); trial.put("spawn", String.valueOf(spawned));
            attempts.add(trial);
            if (spawned != null) {
                shot(context, "awakened-" + attempt); supply(context, new ItemStack(Items.DIAMOND_SWORD));
                for (int hit = 0; hit < 14 && value(player -> alive(player, spawned)); hit++) {
                    final Vec3 target = value(player -> player.level().getEntity(spawned).position());
                    if (value(player -> player.position().distanceTo(target)) > 3) position(context, target.add(0,0,-2));
                    look(context, value(player -> player.level().getEntity(spawned).getBoundingBox().getCenter()));
                    check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult aim && aim.getEntity().getUUID().equals(spawned)),
                        "Native attack pointer must hit the extracted Mandrake");
                    context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT); context.waitTicks(12);
                }
                check(!value(player -> alive(player, spawned)), "Native sword attacks kill the actual extracted Mandrake");
                killed = true; rootObtained |= total(item("ingredient_mandrake_root")) > 0;
                trial.put("killed", true); trial.put("drops", items()); shot(context, "mandrake-kill-drops-" + attempt);
                position(context, CAMERA);
            } else trial.put("quiet_harvest_drops", items());
        }
        row().put("day_trials", attempts);
        check(killed && rootObtained, "Bounded natural day trials must observe an extracted Mandrake kill and an actual root drop; loot is random");
        boolean quietNight = false;
        server(player -> player.level().clockManager().setTotalTicks(player.level().dimensionType().defaultClock().orElseThrow(), 18000L));
        final List<Object> night = new ArrayList<>();
        for (int attempt = 0; attempt < 8 && !quietNight; attempt++) {
            clearLoose(); plant(context, seed, PLANT.below(), "mandrake"); boneMature(context);
            supply(context, ItemStack.EMPTY); breakBlock(context, PLANT);
            final boolean awakened = value(player -> !player.level().getEntitiesOfClass(LivingEntity.class, new AABB(PLANT).inflate(5),
                mob -> BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath().equals("mandrake") && mob.isAlive()).isEmpty());
            night.add(Map.of("awakened", awakened, "drops", items())); quietNight = !awakened && total(item("ingredient_mandrake_root")) > 0;
            if (awakened) server(player -> player.level().getEntitiesOfClass(LivingEntity.class, new AABB(PLANT).inflate(8),
                mob -> BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath().equals("mandrake")).forEach(Entity::discard));
        }
        check(quietNight, "Bounded night trials observe a quiet mature harvest, without claiming guaranteed safety");
        row().put("night_trials", night); row().put("probability_limits", "75% day / 25% night awaken in source; trials do not establish rates. Death loot can be zero. Boline does not suppress awakening.");
        shot(context, "night-quiet-harvest");
    }

    private void tree(final ClientGameTestContext context, final String id) throws Exception {
        plant(context, block(id).asItem(), PLANT.below(), id); shot(context, "seedling");
        ticks(512); await(context, player -> !state(player).is(block(id)), 1800, "Natural sapling ticks generate the tree"); ticks(0);
        final String family = id.replace("_sapling", "");
        check(state().is(block(family + "_log")), "Grown trunk is the registered matching tree wood");
        final BlockPos leaf = value(player -> BlockPos.betweenClosedStream(PLANT.offset(-7,0,-7), PLANT.offset(7,14,7))
            .filter(pos -> player.level().getBlockState(pos).is(block(family + "_leaves"))).map(BlockPos::immutable).findFirst().orElseThrow());
        row().put("natural_tree", Map.of("trunk", state().toString(), "leaf", leaf.toShortString())); shot(context, "natural-tree");
        server(player -> player.setNoGravity(true));
        position(context, Vec3.atBottomCenterOf(leaf).add(0,1,-2)); supply(context, new ItemStack(Items.SHEARS)); breakBlock(context, leaf);
        check(total(block(family + "_leaves").asItem()) > 0, "Native shears recover grown leaves");
        shot(context, "sheared-leaves");
        server(player -> BlockPos.betweenClosedStream(PLANT.offset(-8,0,-8), PLANT.offset(8,15,8))
            .filter(pos -> player.level().getBlockState(pos).is(block(family + "_log")) || player.level().getBlockState(pos).is(block(family + "_leaves")))
            .forEach(pos -> player.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())));
        position(context, CAMERA); server(player -> player.setNoGravity(false)); plant(context, block(id).asItem(), PLANT.below(), id);
        supply(context, new ItemStack(Items.BONE_MEAL, 64));
        for (int use = 0; use < 32 && value(player -> state(player).is(block(id))); use++) useBlock(context, PLANT, false);
        check(state().is(block(family + "_log")), "Native bonemeal generates the matching tree");
        shot(context, "bonemeal-tree"); row().put("remaining", "Natural leaf decay/sapling probability and blocked-space failures are not measured.");
    }

    private void special(final ClientGameTestContext context, final String id) throws Exception {
        final boolean hanging = block(id) instanceof VineBlock;
        final BlockPos target = hanging ? PLANT.above(2) : PLANT;
        if (hanging) server(player -> {
            for (int y=100;y<=105;y++) player.level().setBlockAndUpdate(new BlockPos(0,y,1), Blocks.STONE.defaultBlockState());
        });
        plant(context, block(id).asItem(), hanging ? target.south() : PLANT.below(), id, target);
        shot(context, "placed");
        if (hanging || Set.of("embermoss", "glintweed", "bramble").contains(id)) {
            final long initial = plants(id);
            ticks(512); await(context, player -> plants(player,id) > initial, 2400, "Ordinary random ticks spread the placed plant"); ticks(0);
            row().put("natural_spread_count", plants(id)); shot(context, "spread");
            if (!hanging) {
                server(player -> BlockPos.betweenClosedStream(PLANT.offset(-6,-1,-6),PLANT.offset(6,5,6))
                    .filter(pos -> !pos.equals(PLANT) && player.level().getBlockState(pos).is(block(id)))
                    .forEach(pos -> player.level().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState())));
                supply(context,new ItemStack(Items.BONE_MEAL,64));
                for(int use=0;use<32 && plants(id)==1;use++) useBlock(context,PLANT,false);
                check(plants(id)>1,"Native bonemeal spreads the supported plant"); row().put("bonemeal_spread_count",plants(id));
            }
        }
        if (id.equals("grassper")) {
            supply(context,new ItemStack(Items.APPLE)); useBlock(context,PLANT,false);
            check(state().getValue(GrassperBlock.OCCUPIED),"Native use stores offered item");
            supply(context,ItemStack.EMPTY); useBlock(context,PLANT,false);
            check(!state().getValue(GrassperBlock.OCCUPIED) && total(Items.APPLE)==1,"Native empty-hand use returns the held apple once");
            row().put("storage_return",true);
        }
        if(Set.of("embermoss","leapinglily","bloodrose").contains(id)) {
            supply(context,ItemStack.EMPTY);
            context.runOnClient(client -> {client.player.setYRot(0);client.player.setXRot(0);});
            context.getInput().holdKeyFor(com.mojang.blaze3d.platform.InputConstants.KEY_W,14);
            if(id.equals("embermoss")) await(context,ServerPlayer::isOnFire,40,"Native walking contact with Ember Moss ignites player");
            if(id.equals("leapinglily")) await(context,player -> player.hasEffect(net.minecraft.world.effect.MobEffects.JUMP_BOOST)
                && player.hasEffect(net.minecraft.world.effect.MobEffects.SPEED),40,"Native lily contact grants jump and speed");
            if(id.equals("bloodrose")) {
                await(context,player -> {
                    final var contact=com.kadamitas.warlockery.data.WarlockeryEntityData.get(player);
                    return contact.getLongOr("WarlockeryBloodPoppyPosition",Long.MIN_VALUE)==PLANT.asLong()
                        && BloodPoppyRules.sampleIsFresh(player.level().getGameTime(),contact.getLongOr("WarlockeryBloodPoppyTime",-1L));
                },40,"Ordinary walking actually records fresh contact with this Blood Poppy");
                row().put("observed_contact_position",value(player -> player.position().toString()));
            }
            shot(context,"native-contact");position(context,id.equals("bloodrose")?new Vec3(.5,100,-1.5):CAMERA);
            if(id.equals("bloodrose")) {
                supply(context,new ItemStack(item("sympathetic_vial")));useBlock(context,PLANT,false);
                check(value(player -> com.kadamitas.warlockery.item.SympatheticBinding.read(player.getMainHandItem())
                    .map(binding -> binding.targets(player)).orElse(false)),"Native vial use samples the player who actually touched Blood Poppy");
                row().put("sampled_native_contact",true);
            } else row().put("native_contact_effect",true);
            server(player -> {player.clearFire();player.removeAllEffects();});
        }
        if(id.equals("crittersnare")) {
            final UUID subject=value(player -> {
                final var silverfish=EntityTypes.SILVERFISH.create(player.level(),EntitySpawnReason.COMMAND);
                check(silverfish!=null,"Staged small critter creates");
                // Keep ordinary AI/physics active so gravity carries the critter into the snare.
                silverfish.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
                check(silverfish.typeHolder().is(com.kadamitas.warlockery.item.ResourceCompatibilityTags.EntityTypes.CRITTER_SNARE_TARGETS),"Actual staged creature belongs to the snare target tag");
                silverfish.snapTo(.5,101,.5);check(player.level().addFreshEntity(silverfish),"Staged critter enters the native world");return silverfish.getUUID();
            });
            row().put("critter_fixture","Actual Silverfish one block above snare; ordinary AI and gravity enabled, horizontal movement speed staged to zero. No contact callback or captured payload is injected.");
            await(context,player -> state(player).getValue(CritterSnareBlock.PAYLOAD).occupied(),80,"Actual falling critter enters and fills the native snare");
            check(value(player -> player.level().getEntity(subject)==null),"Captured subject leaves the world");shot(context,"captured-critter");
            supply(context,ItemStack.EMPTY);useBlock(context,PLANT,false);
            check(!state().getValue(CritterSnareBlock.PAYLOAD).occupied(),"Native empty-hand use releases stored critter");
            check(value(player -> !player.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Silverfish.class,new AABB(PLANT).inflate(4)).isEmpty()),"A live released Silverfish is present");
            row().put("native_capture_release",true);shot(context,"released-critter");
        }
        supply(context, new ItemStack(id.equals("bloodrose") ? item("boline") : id.equals("bramble") ? Items.DIAMOND_AXE : Items.SHEARS));
        breakBlock(context,target);
        check(total(block(id).asItem())>0,"Native appropriate-tool harvest returns the placed plant");
        row().put("harvest_drops",items()); shot(context,"harvest");
        if (id.equals("somniancotton")) {
            row().put("day_harvest", "Dormant cotton returns itself; no age growth exists."); clearLoose();
            server(player -> player.level().clockManager().setTotalTicks(player.level().dimensionType().defaultClock().orElseThrow(),18000L));
            plant(context,block(id).asItem(),PLANT.below(),id); supply(context,ItemStack.EMPTY); breakBlock(context,PLANT);
            check(total(item("ingredient_disturbed_cotton"))==1,"Actual night harvest produces one Disturbed Cotton");
            row().put("night_drops",items()); shot(context,"night-disturbed-harvest");
        }
        if (block(id).getClass()==Block.class && (id.equals("hex_sapling") || id.equals("vine"))) {
            row().put("coverage","Placement/harvest only: registered plain Block has no growth behavior. This is a source gap, NOT a passed sapling/vine growth test.");
            row().put("growth_status","NOT_IMPLEMENTED");
        } else if (!Set.of("embermoss","glintweed","bramble","spanishmoss").contains(id))
            row().put("growth_status","NO_AGE_GROWTH_IN_NATIVE_BLOCK");
        row().put("special_remaining","Bramble teleport, Plant Mine trigger and Void Bramble ritual inhibition require their existing device/plant ability receipts. Other observed contact/storage/sample/capture branches are recorded explicitly; no blanket all-plant-power claim.");
    }

    private void catalyst(final ClientGameTestContext context, final String id) throws Exception {
        final boolean prime=id.endsWith("_prime");
        final List<Block> inputs=new ArrayList<>(List.of(Blocks.DANDELION,Blocks.OAK_SAPLING,Blocks.OAK_LEAVES));
        if(prime)inputs.addAll(List.of(Blocks.WHEAT,Blocks.GRASS_BLOCK,Blocks.MYCELIUM,Blocks.DIRT));
        final List<Object> trials=new ArrayList<>();
        for(Block input:inputs){
            server(player -> {
                player.level().setBlockAndUpdate(PLANT,Blocks.AIR.defaultBlockState());
                player.level().setBlockAndUpdate(PLANT.above(),Blocks.AIR.defaultBlockState());
                player.level().setBlockAndUpdate(PLANT.below(),input==Blocks.WHEAT?Blocks.FARMLAND.defaultBlockState():Blocks.DIRT.defaultBlockState());
                player.level().setBlockAndUpdate(PLANT,input.defaultBlockState());
                if(input==Blocks.DIRT)player.level().setBlockAndUpdate(PLANT.above(),Blocks.WATER.defaultBlockState());
            });
            supply(context,new ItemStack(item(id),2));shot(context,"catalyst-before-"+BuiltInRegistries.BLOCK.getKey(input).getPath());
            useBlock(context,PLANT,false);
            final BlockState result=state();
            final Block expected=input==Blocks.GRASS_BLOCK?Blocks.MYCELIUM:input==Blocks.MYCELIUM?Blocks.GRASS_BLOCK:input==Blocks.DIRT?Blocks.CLAY:null;
            check(expected==null ? !result.is(input) && !result.isAir() : result.is(expected),"Native catalyst transforms supported input into a live output");
            check(value(player -> player.getMainHandItem().getCount()==1),"Successful catalyst use consumes exactly one dose");
            trials.add(Map.of("input",BuiltInRegistries.BLOCK.getKey(input).toString(),"output",result.toString(),"remaining",1));
            shot(context,"catalyst-after-"+BuiltInRegistries.BLOCK.getKey(input).getPath());
        }
        server(player -> {BlockPos.betweenClosedStream(PLANT.offset(-7,0,-7),PLANT.offset(7,4,7)).forEach(pos -> player.level().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState()));player.level().setBlockAndUpdate(PLANT,Blocks.DIRT.defaultBlockState());});
        supply(context,new ItemStack(item(id),2));useBlock(context,PLANT,false);
        check(state().is(Blocks.DIRT) && value(player -> player.getMainHandItem().getCount()==2),"Dry dirt refuses transmutation without consumption");
        row().put("native_transmutations",trials);row().put("dry_dirt_refusal",true);
        row().put("remaining","Random output distribution and Spirit-only Nether Wart output are not certified.");
    }

    private void boneMature(final ClientGameTestContext context) {
        supply(context,new ItemStack(Items.BONE_MEAL,64));
        for(int i=0;i<16 && age()<CropBlock.MAX_AGE;i++) useBlock(context,PLANT,false);
        check(age()==CropBlock.MAX_AGE,"Actual bone meal right-clicks mature the replanted crop");
    }
    private int age(){ return value(PlantGrowthClientAcceptance::age); }
    private static int age(ServerPlayer player){ BlockState state=state(player); return state.hasProperty(CropBlock.AGE)?state.getValue(CropBlock.AGE):-1; }
    private BlockState state(){ return value(PlantGrowthClientAcceptance::state); }
    private static BlockState state(ServerPlayer player){ return player.level().getBlockState(PLANT); }
    private static Block block(String id){ return ModBlocks.ALL.get(id).get(); }
    private static Item item(String id){ return id.startsWith("minecraft:") ? BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id)) : ModItems.ALL.get(id).get(); }
    private void ticks(int speed){ server(player -> player.level().getGameRules().set(GameRules.RANDOM_TICK_SPEED,speed,player.level().getServer())); row().put("accelerated_random_tick_speed",512); }
    private long plants(String id){ return value(player -> plants(player,id)); }
    private static long plants(ServerPlayer player,String id){ return BlockPos.betweenClosedStream(PLANT.offset(-7,-1,-7),PLANT.offset(7,7,7)).filter(pos -> player.level().getBlockState(pos).is(block(id))).count(); }
    private void clearLoose(){ server(player -> player.level().getEntitiesOfClass(ItemEntity.class,new AABB(PLANT).inflate(12)).forEach(Entity::discard)); }
    private static boolean alive(ServerPlayer player,UUID id){ Entity e=player.level().getEntity(id); return e!=null && e.isAlive(); }
    private static int inventoryCount(ServerPlayer player,Item item){ return java.util.stream.IntStream.range(0,player.getInventory().getContainerSize()).map(slot -> player.getInventory().getItem(slot).is(item)?player.getInventory().getItem(slot).getCount():0).sum(); }
    private int total(Item item){ return value(player -> inventoryCount(player,item)+player.level().getEntitiesOfClass(ItemEntity.class,new AABB(PLANT).inflate(12),drop -> drop.getItem().is(item)).stream().mapToInt(drop -> drop.getItem().getCount()).sum()); }
    private Map<String,Integer> items(){ return value(player -> { final Map<String,Integer> result=new TreeMap<>(); for(int slot=0;slot<player.getInventory().getContainerSize();slot++){ ItemStack stack=player.getInventory().getItem(slot); if(!stack.isEmpty()) result.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum); } player.level().getEntitiesOfClass(ItemEntity.class,new AABB(PLANT).inflate(12)).forEach(drop -> result.merge(BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()).toString(),drop.getItem().getCount(),Integer::sum)); return result; }); }
    private void plant(ClientGameTestContext context,Item item,BlockPos support,String id){ plant(context,item,support,id,PLANT); }
    private void plant(ClientGameTestContext context,Item item,BlockPos support,String id,BlockPos target){ supply(context,new ItemStack(item)); useBlock(context,support,true); await(context,player -> player.level().getBlockState(target).is(block(id)),60,"Native planting places "+id); }
    private void selectExisting(ClientGameTestContext context,Item item){ server(player -> { for(int slot=0;slot<player.getInventory().getContainerSize();slot++) if(player.getInventory().getItem(slot).is(item)){ ItemStack old=player.getInventory().getItem(0); player.getInventory().setItem(0,player.getInventory().getItem(slot)); player.getInventory().setItem(slot,old); break; } player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges(); }); context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1); context.waitTicks(3); }
    private void supply(ClientGameTestContext context,ItemStack stack){ server(player -> { player.getInventory().setItem(0,stack);player.getInventory().setSelectedSlot(0);player.inventoryMenu.broadcastChanges(); }); world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1);context.waitTicks(3); }
    private Vec3 top(BlockPos pos){return value(player -> {var shape=player.level().getBlockState(pos).getShape(player.level(),pos);return new Vec3(pos.getX()+.5,pos.getY()+(shape.isEmpty()?1:shape.bounds().maxY)-.005,pos.getZ()+.5);});}
    private Vec3 outline(BlockPos pos){return value(player -> {var shape=player.level().getBlockState(pos).getShape(player.level(),pos);return shape.isEmpty()?Vec3.atCenterOf(pos):shape.bounds().getCenter().add(pos.getX(),pos.getY(),pos.getZ());});}
    private void useBlock(ClientGameTestContext context,BlockPos pos,boolean placement){ look(context,placement && pos.getY()<100 ? top(pos) : outline(pos)); check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)),"Native pointer must target "+pos); context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(3); }
    private void breakBlock(ClientGameTestContext context,BlockPos pos){ look(context,outline(pos));check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)),"Native harvest pointer must target plant");context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);try{ await(context,player -> player.level().getBlockState(pos).isAir(),240,"Native attack removes plant"); }finally{context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);}context.waitTicks(2); }
    private void position(ClientGameTestContext context,Vec3 point){
        server(player -> {player.setDeltaMovement(Vec3.ZERO);player.teleportTo(point.x,point.y,point.z);});
        world.getConnection().waitForClientboundPackets();
        await(context,player -> player.connection.hasClientLoaded() && !pending(player),120,"Native teleport acknowledgement before plant input");
        boolean synchronizedPose=false; Vec3 serverPose=point; Vec3 clientPose=null;
        for(int tick=0;tick<100;tick++){
            serverPose=value(ServerPlayer::position);
            clientPose=context.computeOnClient(client -> client.player==null?null:client.player.position());
            if(clientPose!=null && clientPose.distanceTo(serverPose)<.3){synchronizedPose=true;break;}
            context.waitTicks(1);
        }
        row().put("last_camera",Map.of("requested",point.toString(),"server",serverPose.toString(),
            "client",String.valueOf(clientPose),"acknowledged",true));
        check(synchronizedPose,"Camera mismatch after native teleport acknowledgement: requested="+point+", server="+serverPose+", client="+clientPose);
    }
    private static boolean pending(ServerPlayer player){try{var f=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");f.setAccessible(true);return f.get(player.connection)!=null;}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void look(ClientGameTestContext context,Vec3 target){context.runOnClient(client -> {Vec3 d=target.subtract(client.player.getEyePosition());client.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));client.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));});context.waitTicks(3);}
    private void await(ClientGameTestContext context,Predicate<ServerPlayer> test,int ticks,String message){for(int i=0;i<ticks && !value(test::test);i++)context.waitTicks(1);check(value(test::test),message);world.getConnection().waitForClientboundPackets();}
    private void server(Consumer<ServerPlayer> action){world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));}
    private <T>T value(Function<ServerPlayer,T> action){AtomicReference<T> result=new AtomicReference<>();server(player -> result.set(action.apply(player)));return result.get();}
    private Map<String,Object> row(){return results.get(active);}
    private void shot(ClientGameTestContext context,String name)throws Exception{ManualClientAcceptance.saveScreenshot(context,evidence,active+"-"+name,screenshots);}
    private static void release(ClientGameTestContext context){context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);}
    private void write()throws Exception{Files.writeString(evidence.resolve("plant-growth.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("results",results,"screenshots",screenshots,"failures",failures,"all_plant_abilities_certified",false)));}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static String guide(String id){return switch(id){case "garlicplant" -> "plant_garlic";case "embermoss" -> "plant_ember_moss";case "glintweed" -> "plant_glint_weed";case "spanishmoss" -> "plant_spanish_moss";case "somniancotton" -> "plant_somnian_cotton";case "leapinglily" -> "plant_leaping_lily";case "bloodrose" -> "plant_blood_rose";case "grassper" -> "plant_grassper";case "crittersnare" -> "plant_critter_snare";case "voidbramble" -> "plant_void_bramble";case "plantmine" -> "device_plant_mine";default -> ContentCatalog.CROPS.contains(id)||id.equals("bramble")||id.equals("pitgrass")?"plant_"+id:id;};}
    private void readGuide(ClientGameTestContext context,String section)throws Exception{
        Optional<ManualProfile> found=ManualProfile.profiles().stream().filter(book -> book.sections().contains(section)).findFirst();
        if(found.isEmpty()){row().put("guide_status","NO_INDEXED_ENTRY: "+section);return;}
        ManualProfile profile=found.orElseThrow();supply(context,new ItemStack(item(profile.id())));context.runOnClient(client -> client.player.setXRot(-75));context.waitTicks(2);context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);context.waitForScreen(ManualScreen.class);ManualClientAcceptance.selectSection(context,section);
        int pages=context.computeOnClient(client -> {try{var method=ManualScreen.class.getDeclaredMethod("bodyPages",ManualLayout.class,String.class);method.setAccessible(true);return ((List<?>)method.invoke(client.gui.screen(),ManualLayout.calculate(client.gui.screen().width,client.gui.screen().height),section)).size();}catch(ReflectiveOperationException e){throw new AssertionError(e);}});
        String body=context.computeOnClient(client -> ManualArticleCatalog.article(profile,section).body().getString());
        for(int page=0;page<pages;page++){if(page>0)ManualClientAcceptance.clickButton(context,Component.translatable("screen.warlockery.manual.next").getString());shot(context,"guide-"+page);}
        row().put("guide",Map.of("book",profile.id(),"section",section,"body",body,"pages",pages));context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);context.waitFor(client -> client.gui.screen()==null);
    }
}
