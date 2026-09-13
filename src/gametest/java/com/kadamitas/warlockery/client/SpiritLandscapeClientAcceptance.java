package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/** Samples fresh generated worlds; never copies blocks or modifies terrain to obtain equality. */
public final class SpiritLandscapeClientAcceptance implements FabricClientGameTest {
    private static final long SEED = 867530912345L;
    private static final ResourceKey<Level> SPIRIT = ResourceKey.create(Registries.DIMENSION, Identifier.parse("warlockery:spirit_world"));
    private static final int[][] REGIONS = {{64,128},{512,-256},{-384,768}};
    private final List<Map<String,Object>> chunks = new ArrayList<>();
    private final List<Map<String,Object>> rechecks = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();
    private final Map<String,Object> report = new LinkedHashMap<>();
    private Path evidence;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("spirit-landscape").resolve(UUID.randomUUID().toString());
        report.put("seed", SEED);
        report.put("chunks", chunks);
        report.put("pretravel_rechecks", rechecks);
        report.put("screenshots", screenshots);
        report.put("excluded_blocks", List.of());
        report.put("diagnostic_protocol", "All six original coordinates are compared before any camera travel, then all six are compared again before travel. Both passes retain every block, biome and height difference. Chunk-holder snapshots inspect existing holders without requesting more generation; their asynchronous states are observations, not an execution trace. Feature steps are read from each actual generator's cached supplier after the first comparison pass.");
        report.put("scope", "Six pristine chunks, every full-height block column and biome at four-block vertical intervals, across three regions of one seed. Includes generated ores, water, caves, structures and flora; excludes no block differences. Does not prove every coordinate, other seeds, player-build exclusion, or survival travel/body behavior.");
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280,800);
            try (var world = context.worldBuilder().adjustSettings(settings -> {
                var normal = settings.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL);
                settings.setWorldType(new WorldCreationUiState.WorldTypeEntry(normal));
                settings.setSeed(Long.toString(SEED));
                settings.setGenerateStructures(true);
            }).create()) {
                world.getConnection().waitForChunksRender();
                world.getServer().runOnServer(server -> {
                    ServerLevel overworld = server.overworld(), spirit = server.getLevel(SPIRIT);
                    require(spirit != null, "Spirit dimension exists");
                    require(overworld.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator, "Overworld actually uses natural noise terrain, not the test builder flat default");
                    require(spirit.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator, "Spirit actually uses noise terrain");
                    require(overworld.getSeed() == SEED && spirit.getSeed() == SEED, "Both actual dimensions use the selected world seed");
                    require(overworld.getMinY() == spirit.getMinY() && overworld.getMaxY() == spirit.getMaxY(), "Both realms have the same vertical range");
                    report.put("min_y", overworld.getMinY()); report.put("max_y_inclusive", overworld.getMaxY());
                    report.put("overworld_generator", overworld.getChunkSource().getGenerator().getClass().getName());
                    report.put("spirit_generator", spirit.getChunkSource().getGenerator().getClass().getName());
                    var player = world.getConnection().getServerPlayer();
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true; player.onUpdateAbilities();
                });
                for (int region=0; region<REGIONS.length; region++) {
                    final int rx=REGIONS[region][0], rz=REGIONS[region][1];
                    for (int offset=0; offset<2; offset++) {
                        final int cx=rx+offset;
                        chunks.add(world.getServer().computeOnServer(server -> compare(server.overworld(),server.getLevel(SPIRIT),cx,rz)));
                        write();
                    }
                }
                world.getServer().runOnServer(server -> {
                    var overworldDetails=generatorDetails(server.overworld());
                    var spiritDetails=generatorDetails(server.getLevel(SPIRIT));
                    report.put("overworld_generation_details",overworldDetails);
                    report.put("spirit_generation_details",spiritDetails);
                    report.put("generation_details_equal",overworldDetails.equals(spiritDetails));
                });
                for (int[] region:REGIONS) for (int offset=0;offset<2;offset++) {
                    final int cx=region[0]+offset,cz=region[1];
                    rechecks.add(world.getServer().computeOnServer(server -> compare(server.overworld(),server.getLevel(SPIRIT),cx,cz)));
                    write();
                }
                for (int region=0; region<REGIONS.length; region++) {
                    final int rx=REGIONS[region][0], rz=REGIONS[region][1];
                    final int x=rx*16+16, z=rz*16+8;
                    final int y=world.getServer().computeOnServer(server -> Math.max(server.overworld().getHeight(Heightmap.Types.WORLD_SURFACE,x,z),server.getLevel(SPIRIT).getHeight(Heightmap.Types.WORLD_SURFACE,x,z))+32);
                    capture(context,world,Level.OVERWORLD,new Vec3(x+.5,y,z+.5),"region-"+region+"-overworld");
                    capture(context,world,SPIRIT,new Vec3(x+.5,y,z+.5),"region-"+region+"-spirit");
                    write();
                }
                boolean equal=java.util.stream.Stream.concat(chunks.stream(),rechecks.stream()).allMatch(row -> ((Number)row.get("block_mismatches")).longValue()==0 && ((Number)row.get("biome_mismatches")).longValue()==0 && ((Number)row.get("height_mismatches")).longValue()==0);
                report.put("sampling_completed",true); report.put("all_sampled_columns_equal",equal); write();
                require(equal,"Natural terrain/biome differences found; inspect spirit-landscape.json for coordinates and counts");
            }
        } catch (Throwable failure) {
            report.put("failure",failure.toString());
            try { write(); } catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
            throw new AssertionError("Spirit landscape acceptance: "+evidence,failure);
        }
    }

    private static Map<String,Object> compare(ServerLevel a,ServerLevel b,int cx,int cz) {
        // getChunk requests actual FULL generation; both samples are taken before teleporting here.
        List<Map<String,Object>> pipeline=new ArrayList<>();
        pipeline.add(pipelineSnapshot("before_full_requests",a,b,cx,cz));
        var ac=a.getChunk(cx,cz);
        String aFirstHash=blockHash(ac);
        pipeline.add(pipelineSnapshot("overworld_full_returned",a,b,cx,cz));
        var bc=b.getChunk(cx,cz);
        String bFirstHash=blockHash(bc);
        pipeline.add(pipelineSnapshot("spirit_full_returned",a,b,cx,cz));
        List<Map<String,Object>> columns=new ArrayList<>();
        List<Map<String,Object>> examples=new ArrayList<>();
        Map<String,Long> blockPairs=new TreeMap<>(),categoryPairs=new TreeMap<>(),stateOnlyPairs=new TreeMap<>();
        Map<String,Map<String,Object>> categoryExamples=new TreeMap<>();
        long blockDiff=0,biomeDiff=0,heightDiff=0,compared=0;
        for(int dx=0;dx<16;dx++) for(int dz=0;dz<16;dz++) {
            int x=cx*16+dx,z=cz*16+dz,bd=0,biod=0;
            int ah=a.getHeight(Heightmap.Types.WORLD_SURFACE,x,z),bh=b.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
            int af=a.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z),bf=b.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z);
            if(ah!=bh || af!=bf) heightDiff++;
            for(int y=a.getMinY();y<=a.getMaxY();y++) {
                BlockPos pos=new BlockPos(x,y,z);
                var as=ac.getBlockState(pos); var bs=bc.getBlockState(pos); compared++;
                if(!as.equals(bs)) {
                    bd++;
                    String ak=BuiltInRegistries.BLOCK.getKey(as.getBlock()).toString(),bk=BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString();
                    String category=blockCategory(as)+" -> "+blockCategory(bs);
                    blockPairs.merge(ak+" -> "+bk,1L,Long::sum);
                    categoryPairs.merge(category,1L,Long::sum);
                    if(as.getBlock()==bs.getBlock()) stateOnlyPairs.merge(ak,1L,Long::sum);
                    categoryExamples.putIfAbsent(category,Map.of("x",x,"y",y,"z",z,"overworld",as.toString(),"spirit",bs.toString()));
                    if(examples.size()<48) examples.add(Map.of("x",x,"y",y,"z",z,"overworld",as.toString(),"spirit",bs.toString()));
                }
                if(Math.floorMod(y,4)==0) {
                    String ab=a.getBiome(pos).unwrapKey().orElseThrow().identifier().toString();
                    String bb=b.getBiome(pos).unwrapKey().orElseThrow().identifier().toString();
                    if(!ab.equals(bb)) {
                        biod++;
                        if(examples.size()<48) examples.add(Map.of("x",x,"y",y,"z",z,"overworld_biome",ab,"spirit_biome",bb));
                    }
                }
            }
            blockDiff+=bd; biomeDiff+=biod;
            columns.add(Map.of("x",x,"z",z,"overworld_surface",ah,"spirit_surface",bh,"overworld_floor",af,"spirit_floor",bf,"block_mismatches",bd,"biome_mismatches",biod));
        }
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("chunk_x",cx); row.put("chunk_z",cz); row.put("blocks_compared",compared);
        row.put("block_mismatches",blockDiff); row.put("biome_mismatches",biomeDiff); row.put("height_mismatches",heightDiff);
        row.put("columns",columns); row.put("first_mismatches",examples);
        row.put("block_pair_histogram",blockPairs); row.put("category_pair_histogram",categoryPairs);
        row.put("same_block_different_properties",stateOnlyPairs); row.put("first_example_per_category_pair",categoryExamples);
        row.put("pipeline_snapshots",pipeline);
        row.put("overworld_hash_at_full_return",aFirstHash); row.put("overworld_hash_after_comparison",blockHash(ac));
        row.put("spirit_hash_at_full_return",bFirstHash); row.put("spirit_hash_after_comparison",blockHash(bc));
        return row;
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> generatorDetails(ServerLevel level) {
        try {
            var generator=level.getChunkSource().getGenerator();
            var placed=level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            // Inspect the actual memoized list used by applyBiomeDecoration, not a separately rebuilt approximation.
            var field=ChunkGenerator.class.getDeclaredField("featuresPerStep");
            field.setAccessible(true);
            var steps=((Supplier<List<FeatureSorter.StepFeatureData>>)field.get(generator)).get();
            List<Map<String,Object>> orderedSteps=new ArrayList<>();
            for(int step=0;step<steps.size();step++) {
                var data=steps.get(step);
                List<Map<String,Object>> features=new ArrayList<>();
                for(var feature:data.features()) features.add(Map.of("index",data.indexMapping().applyAsInt(feature),"key",String.valueOf(placed.getKey(feature))));
                orderedSteps.add(Map.of("step",step,"features",features));
            }
            List<Map<String,Object>> biomes=new ArrayList<>();
            for(var biome:generator.getBiomeSource().possibleBiomes()) {
                List<List<String>> features=new ArrayList<>();
                for(var step:generator.getBiomeGenerationSettings(biome).features())
                    features.add(step.stream().map(holder -> String.valueOf(placed.getKey(holder.value()))).toList());
                biomes.add(Map.of("biome",biome.unwrapKey().orElseThrow().identifier().toString(),"features_by_step",features));
            }
            return Map.of("generator_class",generator.getClass().getName(),"biome_source_class",generator.getBiomeSource().getClass().getName(),
                "noise_settings",((NoiseBasedChunkGenerator)generator).generatorSettings().unwrapKey().orElseThrow().identifier().toString(),
                "actual_cached_feature_steps",orderedSteps,"possible_biomes_in_iteration_order",biomes);
        } catch(ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect actual generator feature ordering",failure);
        }
    }

    private static Map<String,Object> pipelineSnapshot(String phase,ServerLevel a,ServerLevel b,int cx,int cz) {
        return Map.of("phase",phase,"overworld",chunkHolders(a,cx,cz),"spirit",chunkHolders(b,cx,cz));
    }

    private static Map<String,Object> chunkHolders(ServerLevel level,int cx,int cz) {
        List<Map<String,Object>> holders=new ArrayList<>();
        var source=level.getChunkSource();
        for(int x=cx-2;x<=cx+2;x++) for(int z=cz-2;z<=cz+2;z++) {
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("chunk_x",x); row.put("chunk_z",z);
            var holder=source.chunkMap.getUpdatingChunkIfPresent(ChunkPos.pack(x,z));
            row.put("holder_present",holder!=null);
            if(holder!=null) {
                row.put("latest_status",String.valueOf(holder.getLatestStatus()));
                row.put("persisted_status",String.valueOf(holder.getPersistedStatus()));
                row.put("full_status",String.valueOf(holder.getFullStatus()));
                row.put("ticket_level",holder.getTicketLevel()); row.put("queue_level",holder.getQueueLevel());
                List<Map<String,Object>> futures=new ArrayList<>();
                for(var future:holder.getAllFutures()) futures.add(Map.of("status",future.getFirst().getName(),"scheduled",future.getSecond()!=null,
                    "done",future.getSecond()!=null && future.getSecond().isDone(),"exceptional",future.getSecond()!=null && future.getSecond().isCompletedExceptionally()));
                row.put("status_futures",futures);
            }
            holders.add(row);
        }
        return Map.of("game_time",level.getGameTime(),"loaded_chunks",source.getLoadedChunksCount(),"pending_main_thread_tasks",source.getPendingTasksCount(),"radius_two_holders",holders);
    }

    private static String blockHash(ChunkAccess chunk) {
        // Diagnostic FNV-1a over registry block-state IDs in x/z/y order; exact comparisons above decide success.
        long hash=0xcbf29ce484222325L;
        var pos=new BlockPos.MutableBlockPos();
        for(int dx=0;dx<16;dx++) for(int dz=0;dz<16;dz++) for(int y=chunk.getMinY();y<=chunk.getMaxY();y++) {
            int state=Block.getId(chunk.getBlockState(pos.set(chunk.getPos().x()*16+dx,y,chunk.getPos().z()*16+dz)));
            for(int shift=0;shift<32;shift+=8) { hash^=(state>>>shift)&255; hash*=0x100000001b3L; }
        }
        return Long.toUnsignedString(hash,16);
    }

    private static String blockCategory(BlockState state) {
        String key=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if(state.isAir()) return "air";
        if(state.is(BlockTags.LOGS)) return "logs";
        if(state.is(BlockTags.LEAVES)) return "leaves";
        if(!state.getFluidState().isEmpty()) return "fluid_or_waterlogged";
        if(key.contains("ore")) return "ore";
        if(key.contains("moss") || key.endsWith(":clay")) return "moss_or_clay";
        if(key.contains("grass") && !key.endsWith(":grass_block") || key.contains("fern") || key.contains("flower") || key.contains("litter") || key.contains("vine") || key.contains("root") || key.contains("sapling") || key.contains("bush") || key.contains("fungus") || key.contains("mushroom")) return "plants";
        if(key.contains("stone") || key.contains("dirt") || key.contains("sand") || key.contains("gravel") || key.endsWith(":grass_block") || key.contains("bedrock")) return "terrain";
        return "other:"+key;
    }

    private void capture(ClientGameTestContext context,TestSingleplayerContext world,ResourceKey<Level> dimension,Vec3 point,String name) throws Exception {
        world.getServer().runOnServer(server -> {
            var player=world.getConnection().getServerPlayer();
            player.setGameMode(GameType.CREATIVE); player.getAbilities().flying=true; player.onUpdateAbilities();
            require(player.teleportTo(server.getLevel(dimension),point.x,point.y,point.z,Set.of(),135,35,true),"Camera teleport succeeds");
            player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.player!=null && client.player.level().dimension().equals(dimension) && client.gui.screen()==null,300);
        // Waking returns to the saved entry point. Position this landscape camera only after that transfer settles.
        world.getServer().runOnServer(server -> {
            var player=world.getConnection().getServerPlayer();
            require(player.level().dimension().equals(dimension),"Camera reached requested realm");
            require(player.teleportTo(server.getLevel(dimension),point.x,point.y,point.z,Set.of(),135,35,true),"Camera placement succeeds within realm");
            player.getAbilities().flying=true; player.onUpdateAbilities(); player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForClientboundPackets(); world.getConnection().waitForChunksRender();
        context.waitFor(client -> client.player!=null && client.player.level().dimension().equals(dimension) && client.player.position().distanceTo(point)<.5);
        context.runOnClient(client -> { client.player.getAbilities().flying=true; client.player.setYRot(135); client.player.setXRot(35); });
        context.waitTicks(110);
        ManualClientAcceptance.saveScreenshot(context,evidence,name,screenshots);
    }
    private void write() throws Exception { Files.writeString(evidence.resolve("spirit-landscape.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
    private static void require(boolean valid,String message) { if(!valid) throw new AssertionError(message); }
}
