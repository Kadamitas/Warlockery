package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.config.WarlockeryConfig;
import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.entity.LycanPackRules;
import com.kadamitas.warlockery.entity.WerewolfEntity;
import com.kadamitas.warlockery.entity.WerewolfHunterEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.ModVillagers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class CreatureWorldIntegration {
    public static final int MAX_RAW_ARMING_VISITS = LycanPackRules.MAX_RAW_ARMING_VISITS;
    public static final int MAX_RETAINED_ARMING = LycanPackRules.MAX_RETAINED_ARMING;

    private CreatureWorldIntegration() {
    }

    public static void tick(final ServerLevel level) {
        GoblinRaidRuntime.tick(level);
        VillageGuardRuntime.tick(level);
        if (level.players().isEmpty()) return;
        final long gameTime = level.getGameTime();
        final boolean overworld = level.dimension() == Level.OVERWORLD;
        final boolean scanForPillagers = WarlockeryConfig.armPillagers()
            && scheduled(gameTime, WarlockeryConfig.pillagerScanInterval());
        final boolean attemptEnclave = overworld && WarlockeryConfig.hobgoblinEnclaves()
            && scheduled(gameTime, WarlockeryConfig.hobgoblinEnclaveInterval());
        final boolean attemptHunt = overworld && WarlockeryConfig.silverHunts()
            && scheduled(gameTime, WarlockeryConfig.silverHuntInterval());
        final boolean attemptVillageParity = overworld && scheduled(gameTime, 1_200);
        final boolean attemptLandmark = overworld && scheduled(gameTime, LegacyStructureRules.LANDMARK_INTERVAL);
        final boolean attemptGathering = overworld && scheduled(gameTime, LegacyStructureRules.GATHERING_INTERVAL);
        if (!scanForPillagers && !attemptEnclave && !attemptHunt && !attemptVillageParity
            && !attemptLandmark && !attemptGathering) return;

        final ServerPlayer player = level.players().get(level.getRandom().nextInt(level.players().size()));
        if (scanForPillagers) armNearbyPillagers(level, player);
        if (attemptVillageParity) enrichVillage(level, player);
        if (attemptLandmark) tryGenerateLegacyLandmark(level, player);
        if (attemptGathering) gatherCircleMages(level, player);

        if (attemptEnclave && level.getRandom().nextDouble() < WarlockeryConfig.hobgoblinEnclaveChance()) {
            tryFoundHobgoblinEnclave(level, player);
        }
        if (attemptHunt
            && level.getRandom().nextDouble() < WarlockeryConfig.silverHuntChance()
            && isFullMoonNight(level, player)) {
            spawnSilverHunt(level, player);
        }
    }

    static boolean scheduled(final long gameTime, final int interval) {
        return gameTime % interval == 0L;
    }

    private static void tryFoundHobgoblinEnclave(final ServerLevel level, final ServerPlayer player) {
        final int distance = 24 + level.getRandom().nextInt(17);
        final BlockPos origin = surface(level, player.blockPosition().offset(
            level.getRandom().nextBoolean() ? distance : -distance,
            0,
            level.getRandom().nextInt(distance * 2 + 1) - distance
        ));
        final long region = LegacyStructureRules.regionKey(origin.getX(), origin.getZ());
        final LegacySiteIndex sites = LegacySiteIndex.get(level);
        if (sites.containsCamp(region)) {
            return;
        }
        final boolean residentsNearby = !level.getEntitiesOfClass(
            HobgoblinEntity.class,
            new AABB(origin).inflate(64, 24, 64)
        ).isEmpty();
        final boolean clear = clearHutFootprint(level, origin);
        if (!HobgoblinCampRules.canFound(level.isVillage(origin), residentsNearby, clear, distance)) {
            return;
        }
        buildHut(level, origin);
        if (WarlockeryConfig.settlementFortifications()) {
            SettlementFortificationRuntime.fortifyHobgoblinSettlement(level, origin);
        }
        sites.registerCamp(region);
        final int residents = HobgoblinCampRules.residents(level.getRandom().nextInt());
        for (int index = 0; index < residents; index++) {
            final BlockPos spawn = origin.offset(index % 2 * 2 - 1, 0, 3 + index / 2);
            final HobgoblinEntity hobgoblin = ModEntities.HOBGOBLIN.get().spawn(level, spawn, EntitySpawnReason.EVENT);
            if (hobgoblin != null) hobgoblin.assignProfessionFromVillage();
        }
        Warlockery.LOGGER.info("Travelling hobgoblins raised a wilderness hut at {}", origin);
    }

    private static boolean clearHutFootprint(final ServerLevel level, final BlockPos origin) {
        return BlockPos.betweenClosedStream(origin.offset(-2, 0, -2), origin.offset(2, 3, 2))
            .allMatch(pos -> level.getBlockEntity(pos) == null && level.getBlockState(pos).canBeReplaced())
            && BlockPos.betweenClosedStream(origin.offset(-2, -1, -2), origin.offset(2, -1, 2))
                .allMatch(pos -> !level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty());
    }

    private static void buildHut(final ServerLevel level, final BlockPos origin) {
        BlockPos.betweenClosedStream(origin.offset(-2, -1, -2), origin.offset(2, -1, 2))
            .forEach(pos -> level.setBlockAndUpdate(pos, Blocks.COBBLESTONE.defaultBlockState()));
        for (int y = 0; y <= 2; y++) {
            final int height = y;
            BlockPos.betweenClosedStream(origin.offset(-2, height, -2), origin.offset(2, height, 2))
                .filter(pos -> Math.abs(pos.getX() - origin.getX()) == 2 || Math.abs(pos.getZ() - origin.getZ()) == 2)
                .filter(pos -> !(height < 2 && pos.getX() == origin.getX() && pos.getZ() == origin.getZ() + 2))
                .forEach(pos -> level.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState()));
        }
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-2, 2}) {
                for (int y = 0; y <= 3; y++) {
                    level.setBlockAndUpdate(origin.offset(dx, y, dz), Blocks.OAK_LOG.defaultBlockState());
                }
            }
        }
        BlockPos.betweenClosedStream(origin.offset(-2, 3, -2), origin.offset(2, 3, 2))
            .forEach(pos -> level.setBlockAndUpdate(pos, Blocks.SPRUCE_SLAB.defaultBlockState()));
        level.setBlockAndUpdate(origin.offset(0, 0, -1), Blocks.CAULDRON.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(1, 0, -1), Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(0, 0, 2), Blocks.OAK_FENCE_GATE.defaultBlockState());
    }

    private static void enrichVillage(final ServerLevel level, final ServerPlayer player) {
        if (!level.isVillage(player.blockPosition())) {
            return;
        }
        final LegacySiteIndex sites = LegacySiteIndex.get(level);
        final long region = LegacyStructureRules.regionKey(
            player.blockPosition().getX(),
            player.blockPosition().getZ()
        );
        Optional<BlockPos> bell = sites.nearestBell(player.blockPosition(), 48.0)
            .filter(pos -> level.getBlockState(pos).is(Blocks.BELL));
        if (bell.isEmpty() && !sites.scannedVillageRegion(region)) {
            bell = BlockPos.betweenClosedStream(
                player.blockPosition().offset(-32, -12, -32),
                player.blockPosition().offset(32, 12, 32)
            ).filter(pos -> level.getBlockState(pos).is(Blocks.BELL))
                .map(BlockPos::immutable)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(player.blockPosition())));
            sites.markVillageRegionScanned(region);
            bell.ifPresent(sites::registerBell);
        }
        if (bell.isEmpty()) {
            return;
        }
        final BlockPos villageCenter = bell.orElseThrow();
        if (WarlockeryConfig.settlementFortifications()) {
            SettlementFortificationRuntime.fortifyHumanVillage(level, villageCenter);
        }
        if (VillageLegacyData.get(level).contains(villageCenter)) {
            return;
        }
        final List<BlockPos> candidates = List.of(
            villageCenter.offset(12, 0, 0),
            villageCenter.offset(-12, 0, 0),
            villageCenter.offset(0, 0, 12),
            villageCenter.offset(0, 0, -12),
            villageCenter.offset(16, 0, 8),
            villageCenter.offset(-16, 0, -8)
        ).stream().map(pos -> surface(level, pos)).toList();
        final Optional<BlockPos> apothecary = candidates.stream()
            .filter(pos -> clearStructureFootprint(level, pos, 2, 4))
            .findFirst();
        final Optional<BlockPos> keep = candidates.stream()
            .filter(pos -> apothecary.stream().noneMatch(first -> first.closerThan(pos, 10.0)))
            .filter(pos -> clearStructureFootprint(level, pos, 2, 8))
            .findFirst();
        if (apothecary.isEmpty() || keep.isEmpty()) {
            return;
        }
        buildApothecary(level, apothecary.orElseThrow());
        buildTownKeep(level, keep.orElseThrow());
        VillageLegacyData.get(level).mark(villageCenter);
    }

    private static boolean clearStructureFootprint(
        final ServerLevel level,
        final BlockPos origin,
        final int radius,
        final int height
    ) {
        return BlockPos.betweenClosedStream(origin.offset(-radius, 0, -radius), origin.offset(radius, height, radius))
            .allMatch(pos -> level.getBlockEntity(pos) == null && level.getBlockState(pos).canBeReplaced())
            && BlockPos.betweenClosedStream(origin.offset(-radius, -1, -radius), origin.offset(radius, -1, radius))
                .allMatch(pos -> !level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty());
    }

    private static void buildApothecary(final ServerLevel level, final BlockPos origin) {
        buildShell(level, origin, 2, 3, Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.DARK_OAK_SLAB);
        level.setBlockAndUpdate(origin.offset(-1, 0, -1), Blocks.WATER_CAULDRON.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(1, 0, -1), Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(1, 1, -1), Blocks.POTTED_CACTUS.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(0, 0, 1), ModBlocks.ALL.get("distilleryidle").get().defaultBlockState());
        final Villager warlock = EntityTypes.VILLAGER.spawn(level, origin, EntitySpawnReason.STRUCTURE);
        if (warlock != null) {
            warlock.setVillagerData(warlock.getVillagerData().withProfession(level.registryAccess(), ModVillagers.WARLOCK_KEY));
            warlock.setPersistenceRequired();
        }
        ModEntities.NAMI.get().spawn(level, origin.offset(-1, 0, 1), EntitySpawnReason.STRUCTURE);
    }

    private static void buildTownKeep(final ServerLevel level, final BlockPos origin) {
        buildShell(level, origin, 2, 7, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        for (int y = 0; y <= 6; y++) {
            level.setBlockAndUpdate(
                origin.offset(-1, y, -1),
                Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST)
            );
        }
        final BlockPos treasury = origin.offset(1, 6, 0);
        level.setBlockAndUpdate(treasury, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(treasury) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.GOLDEN_HELMET));
            chest.setItem(1, new ItemStack(Items.GOLDEN_CHESTPLATE));
            chest.setItem(2, new ItemStack(Items.GOLD_INGOT, 5));
            chest.setItem(3, new ItemStack(Items.GOLD_NUGGET, 18));
        }
    }

    private static void tryGenerateLegacyLandmark(final ServerLevel level, final ServerPlayer player) {
        final long region = LegacyStructureRules.regionKey(
            player.blockPosition().getX(),
            player.blockPosition().getZ()
        );
        final LegacyLandmarkData landmarks = LegacyLandmarkData.get(level);
        if (landmarks.contains(region) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        final int distance = 40 + level.getRandom().nextInt(25);
        final BlockPos origin = surface(level, player.blockPosition().offset(
            level.getRandom().nextBoolean() ? distance : -distance,
            0,
            level.getRandom().nextInt(distance * 2 + 1) - distance
        ));
        final LegacyStructureRules.Landmark landmark = LegacyStructureRules.select(level.getRandom().nextInt());
        final boolean clear = clearStructureFootprint(level, origin, landmark.radius(), landmark.height());
        if (!LegacyStructureRules.canGenerate(
            true,
            level.isVillage(origin),
            landmarks.contains(region),
            clear
        )) {
            return;
        }
        switch (landmark) {
            case STONE_CIRCLE -> buildStoneCircle(level, origin);
            case STRAW_IDOL -> buildStrawIdol(level, origin);
            case ABANDONED_SHACK -> buildAbandonedShack(level, origin);
        }
        landmarks.mark(region);
    }

    private static void buildStoneCircle(final ServerLevel level, final BlockPos origin) {
        final List<BlockPos> pillars = List.of(
            origin.north(4),
            origin.south(4),
            origin.east(4),
            origin.west(4),
            origin.offset(3, 0, 3),
            origin.offset(3, 0, -3),
            origin.offset(-3, 0, 3),
            origin.offset(-3, 0, -3)
        );
        pillars.forEach(base -> {
            level.setBlockAndUpdate(base, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            level.setBlockAndUpdate(base.above(), Blocks.STONE_BRICKS.defaultBlockState());
        });
        level.setBlockAndUpdate(origin, ModBlocks.ALL.get("circle").get().defaultBlockState());
        LegacySiteIndex.get(level).registerCircle(origin);
        if (night(level)) {
            spawnCircleMages(level, origin);
        }
    }

    private static void buildStrawIdol(final ServerLevel level, final BlockPos origin) {
        level.setBlockAndUpdate(origin, ModBlocks.ALL.get("wickerbundle").get().defaultBlockState());
        level.setBlockAndUpdate(origin.above(), Blocks.OAK_FENCE.defaultBlockState());
        level.setBlockAndUpdate(origin.above(2), ModBlocks.ALL.get("scarecrow").get().defaultBlockState());
    }

    private static void buildAbandonedShack(final ServerLevel level, final BlockPos origin) {
        buildShell(level, origin, 2, 2, Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.MOSSY_COBBLESTONE_SLAB);
        level.setBlockAndUpdate(origin.offset(-1, 0, -1), Blocks.CRAFTING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(1, 1, -1), Blocks.COBWEB.defaultBlockState());
        final BlockPos supplies = origin.offset(1, 0, 0);
        level.setBlockAndUpdate(supplies, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(supplies) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 3));
            chest.setItem(1, new ItemStack(Items.BONE_MEAL, 4));
            chest.setItem(2, new ItemStack(ModItems.ALL.get("ingredient_book_herbology").get()));
        }
    }

    private static void gatherCircleMages(final ServerLevel level, final ServerPlayer player) {
        if (!night(level)) {
            return;
        }
        final Block circle = ModBlocks.ALL.get("circle").get();
        LegacySiteIndex.get(level).nearbyCircles(player.blockPosition(), 48.0).stream()
            .filter(pos -> level.getBlockState(pos).is(circle))
            .filter(pos -> stoneCirclePresent(level, pos))
            .findFirst()
            .ifPresent(center -> spawnCircleMages(level, center));
    }

    private static boolean stoneCirclePresent(final ServerLevel level, final BlockPos center) {
        return List.of(center.north(4), center.south(4), center.east(4), center.west(4)).stream()
            .allMatch(pos -> level.getBlockState(pos).is(Blocks.MOSSY_COBBLESTONE));
    }

    private static void spawnCircleMages(final ServerLevel level, final BlockPos center) {
        final int present = level.getEntitiesOfClass(
            Mob.class,
            new AABB(center).inflate(12.0),
            mob -> mob.getType() == ModEntities.ALL.get("circle_mage").get()
        ).size();
        if (!LegacyStructureRules.shouldGather(night(level), present)) {
            return;
        }
        final List<BlockPos> positions = List.of(center.north(2), center.south(2), center.east(2));
        for (int index = present; index < 3; index++) {
            final Mob mage = (Mob) ModEntities.ALL.get("circle_mage").get().spawn(
                level,
                positions.get(index),
                EntitySpawnReason.STRUCTURE
            );
            if (mage != null) {
                mage.setPersistenceRequired();
            }
        }
    }

    private static boolean night(final ServerLevel level) {
        final long time = level.getOverworldClockTime() % 24_000L;
        return time >= 13_000L && time <= 23_000L;
    }

    private static void buildShell(
        final ServerLevel level,
        final BlockPos origin,
        final int radius,
        final int wallHeight,
        final net.minecraft.world.level.block.Block foundation,
        final net.minecraft.world.level.block.Block wall,
        final net.minecraft.world.level.block.Block roof
    ) {
        BlockPos.betweenClosedStream(origin.offset(-radius, -1, -radius), origin.offset(radius, -1, radius))
            .forEach(pos -> level.setBlockAndUpdate(pos, foundation.defaultBlockState()));
        for (int y = 0; y <= wallHeight; y++) {
            final int height = y;
            BlockPos.betweenClosedStream(origin.offset(-radius, height, -radius), origin.offset(radius, height, radius))
                .filter(pos -> Math.abs(pos.getX() - origin.getX()) == radius || Math.abs(pos.getZ() - origin.getZ()) == radius)
                .filter(pos -> !(height < 2 && pos.getX() == origin.getX() && pos.getZ() == origin.getZ() + radius))
                .forEach(pos -> level.setBlockAndUpdate(pos, wall.defaultBlockState()));
        }
        BlockPos.betweenClosedStream(
            origin.offset(-radius, wallHeight + 1, -radius),
            origin.offset(radius, wallHeight + 1, radius)
        ).forEach(pos -> level.setBlockAndUpdate(pos, roof.defaultBlockState()));
    }

    private static void spawnSilverHunt(final ServerLevel level, final ServerPlayer player) {
        final BlockPos origin = surface(level, player.blockPosition().offset(20 + level.getRandom().nextInt(12), 0, level.getRandom().nextInt(25) - 12));
        final WerewolfEntity werewolf = ModEntities.WEREWOLF.get().spawn(level, origin, EntitySpawnReason.EVENT);
        final WerewolfHunterEntity hunter = ModEntities.WEREWOLF_HUNTER.get().spawn(level, surface(level, origin.offset(9, 0, 2)), EntitySpawnReason.PATROL);
        final Pillager pillager = EntityTypes.PILLAGER.spawn(level, surface(level, origin.offset(11, 0, -2)), EntitySpawnReason.PATROL);
        if (werewolf == null || hunter == null) return;
        hunter.setTarget(werewolf);
        werewolf.setTarget(hunter);
        if (pillager != null) {
            equipSilver(pillager);
            pillager.setTarget(werewolf);
        }
    }

    public static ArmingReport armNearbyPillagers(final ServerLevel level, final ServerPlayer player) {
        final AABB bounds = new AABB(player.blockPosition()).inflate(48);
        final List<WerewolfEntity> rawLycans = new ArrayList<>();
        level.getEntities().get(EntityTypeTest.forClass(WerewolfEntity.class), bounds, candidate -> {
            rawLycans.add(candidate);
            return rawLycans.size() >= MAX_RAW_ARMING_VISITS
                ? AbortableIterationConsumer.Continuation.ABORT
                : AbortableIterationConsumer.Continuation.CONTINUE;
        });
        final List<WerewolfEntity> retainedLycans = rawLycans.stream()
            .filter(LivingEntity::isAlive)
            .sorted(Comparator.<WerewolfEntity>comparingDouble(player::distanceToSqr)
                .thenComparing(WerewolfEntity::getUUID, LycanPackRules.unsignedUuidOrder()))
            .limit(MAX_RETAINED_ARMING)
            .toList();
        if (retainedLycans.isEmpty()) {
            return new ArmingReport(rawLycans.size(), 0, 0, 0, 0);
        }
        final List<Pillager> rawPillagers = new ArrayList<>();
        level.getEntities().get(EntityTypeTest.forClass(Pillager.class), bounds, candidate -> {
            rawPillagers.add(candidate);
            return rawPillagers.size() >= MAX_RAW_ARMING_VISITS
                ? AbortableIterationConsumer.Continuation.ABORT
                : AbortableIterationConsumer.Continuation.CONTINUE;
        });
        final List<Pillager> retainedPillagers = rawPillagers.stream()
            .filter(LivingEntity::isAlive)
            .sorted(Comparator.<Pillager>comparingDouble(player::distanceToSqr)
                .thenComparing(Pillager::getUUID, LycanPackRules.unsignedUuidOrder()))
            .limit(MAX_RETAINED_ARMING)
            .toList();
        int armed = 0;
        for (final Pillager pillager : retainedPillagers) {
            final WerewolfEntity target = retainedLycans.stream()
                .min(Comparator.<WerewolfEntity>comparingDouble(pillager::distanceToSqr)
                    .thenComparing(WerewolfEntity::getUUID, LycanPackRules.unsignedUuidOrder()))
                .orElseThrow();
            equipSilver(pillager);
            pillager.setTarget(target);
            armed++;
        }
        return new ArmingReport(
            rawLycans.size(), retainedLycans.size(), rawPillagers.size(), retainedPillagers.size(), armed
        );
    }

    public static List<ArmingCandidate> retainNearestToAnchor(final List<ArmingCandidate> visited) {
        return visited.stream()
            .limit(MAX_RAW_ARMING_VISITS)
            .sorted(Comparator.comparingDouble(ArmingCandidate::distanceSqr)
                .thenComparing(ArmingCandidate::id, LycanPackRules.unsignedUuidOrder()))
            .limit(MAX_RETAINED_ARMING)
            .toList();
    }

    public static Optional<UUID> nearestRetainedLycan(final List<ArmingCandidate> retained) {
        return retained.stream()
            .min(Comparator.comparingDouble(ArmingCandidate::distanceSqr)
                .thenComparing(ArmingCandidate::id, LycanPackRules.unsignedUuidOrder()))
            .map(ArmingCandidate::id);
    }

    public record ArmingCandidate(UUID id, double distanceSqr) {
    }

    public record ArmingReport(
        int rawLycanVisits,
        int retainedLycans,
        int rawPillagerVisits,
        int retainedPillagers,
        int armedPillagers
    ) {
    }

    private static void equipSilver(final Pillager pillager) {
        pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        pillager.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.ALL.get("ingredient_bolt_silver").get(), 64));
    }

    private static boolean isFullMoonNight(final ServerLevel level, final ServerPlayer player) {
        final long time = level.getOverworldClockTime() % 24_000L;
        return time >= 13_000L && time <= 23_000L
            && level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, player.position()) == MoonPhase.FULL_MOON;
    }

    private static BlockPos surface(final ServerLevel level, final BlockPos pos) {
        return new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()), pos.getZ());
    }
}
