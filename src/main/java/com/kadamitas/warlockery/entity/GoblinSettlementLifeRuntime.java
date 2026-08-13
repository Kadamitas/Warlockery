package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.world.GoblinSettlementLifeData;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GoblinSettlementLifeRuntime {
    private static final List<BlockPos> HUT_OFFSETS = List.of(
        new BlockPos(7, 0, 0),
        new BlockPos(-7, 0, 0),
        new BlockPos(0, 0, 7),
        new BlockPos(0, 0, -7),
        new BlockPos(9, 0, 7),
        new BlockPos(-9, 0, -7)
    );

    private GoblinSettlementLifeRuntime() {
    }

    public static void tick(final HobgoblinEntity goblin, final ServerLevel level) {
        if (!eligible(goblin) || goblin.isTrading() || goblin.isNoAi() || goblin.getTarget() != null) {
            return;
        }
        if (goblin.isBaby()) {
            if (Math.floorMod(goblin.tickCount + goblin.getId(), GoblinSettlementLifeRules.CHILD_TICK_INTERVAL) == 0) {
                tickChild(goblin, level);
            }
            return;
        }
        if (Math.floorMod(goblin.tickCount + goblin.getId(), GoblinSettlementLifeRules.ADULT_TICK_INTERVAL) != 0) {
            return;
        }
        tickAdult(goblin, level);
    }

    public static boolean hasAvailableHomeForChild(final HobgoblinEntity goblin, final ServerLevel level) {
        return GoblinSettlementLifeRules.canReproduce(residentCount(goblin, level), reachableBedCount(goblin, level));
    }

    public static boolean tryBuildHutAt(
        final HobgoblinEntity builder,
        final ServerLevel level,
        final BlockPos center
    ) {
        if (!eligible(builder)
            || !level.getGameRules().get(GameRules.MOB_GRIEFING)
            || !clearHutFootprint(level, center)) {
            return false;
        }
        final Optional<HutMaterials> materials = hutMaterials(builder, level);
        if (materials.isEmpty()) {
            return false;
        }
        final long key = settlementKey(builder);
        final GoblinSettlementLifeData data = GoblinSettlementLifeData.get(level);
        if (!data.canReserveHut(key, center)) {
            return false;
        }
        final HutMaterials chosen = materials.orElseThrow();
        final Optional<Map<BlockPos, BlockState>> mutation = buildHut(
            level,
            center,
            chosen.dirt(),
            chosen.planks()
        );
        if (mutation.isEmpty()) {
            return false;
        }
        if (!data.reserveHut(key, center)) {
            restore(level, mutation.orElseThrow());
            return false;
        }
        consume(builder, stack -> stack.is(chosen.dirtItem()), GoblinSettlementLifeRules.HUT_DIRT_COST);
        consume(builder, stack -> stack.is(chosen.logItem()), GoblinSettlementLifeRules.HUT_LOG_COST);
        builder.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    public static boolean tryExcavateTunnelAt(
        final HobgoblinEntity miner,
        final ServerLevel level,
        final BlockPos entrance,
        final Direction direction
    ) {
        if (!eligible(miner)
            || miner.isBaby()
            || !direction.getAxis().isHorizontal()
            || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        }
        final List<BlockPos> excavation = IntStream.range(0, 5)
            .mapToObj(step -> entrance.relative(direction, step).below(step / 2))
            .flatMap(base -> java.util.stream.Stream.of(base, base.above()))
            .toList();
        if (excavation.stream().anyMatch(position -> !canExcavate(level, position))) {
            return false;
        }
        final List<BlockPos> solid = excavation.stream()
            .filter(position -> !level.getBlockState(position).isAir())
            .toList();
        if (solid.size() < 4 || solid.size() > GoblinSettlementLifeRules.TUNNEL_EDIT_CAP) {
            return false;
        }
        final GoblinSettlementLifeData data = GoblinSettlementLifeData.get(level);
        final long key = settlementKey(miner);
        if (!data.canReserveTunnel(key, entrance, solid.size())) {
            return false;
        }
        final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (final BlockPos position : solid) {
            originals.put(position.immutable(), level.getBlockState(position));
            if (!level.destroyBlock(position, false, miner)) {
                restore(level, originals);
                return false;
            }
        }
        if (!data.reserveTunnel(key, entrance, solid.size())) {
            restore(level, originals);
            return false;
        }
        miner.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    public static boolean gatherNearestFlower(final HobgoblinEntity child, final ServerLevel level) {
        if (!child.isBaby()
            || !child.getMainHandItem().isEmpty()
            || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        }
        final Optional<BlockPos> target = nearestGatherableFlower(child, level);
        if (target.isEmpty()) {
            return false;
        }
        final BlockPos position = target.orElseThrow();
        if (child.distanceToSqr(Vec3.atCenterOf(position)) > 4.0) {
            child.getNavigation().moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 1.0);
            return false;
        }
        final BlockState flowerState = level.getBlockState(position);
        final ItemStack flower = new ItemStack(flowerState.getBlock().asItem());
        if (flower.isEmpty()
            || !isGatherableFlower(flowerState)
            || level.getBlockEntity(position) != null) {
            return false;
        }
        level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        if (!level.getBlockState(position).isAir()) {
            return false;
        }
        child.setItemSlot(EquipmentSlot.MAINHAND, flower);
        child.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    public static Optional<BlockPos> nearestGatherableFlower(
        final HobgoblinEntity child,
        final ServerLevel level
    ) {
        return BlockPos.betweenClosedStream(
                child.blockPosition().offset(-6, -2, -6),
                child.blockPosition().offset(6, 2, 6)
            )
            .map(BlockPos::immutable)
            .filter(position -> isGatherableFlower(level.getBlockState(position)))
            .min(Comparator.comparingDouble(position -> child.distanceToSqr(Vec3.atCenterOf(position))));
    }

    public static boolean danceWithNearbyChildren(final HobgoblinEntity child, final ServerLevel level) {
        if (!child.isBaby()) {
            return false;
        }
        final List<HobgoblinEntity> children = level.getEntitiesOfClass(
            HobgoblinEntity.class,
            child.getBoundingBox().inflate(8.0, 3.0, 8.0),
            candidate -> candidate.isAlive()
                && candidate.isBaby()
                && candidate.creatureKind() == child.creatureKind()
        );
        if (children.size() < 3) {
            return false;
        }
        final Vec3 center = children.stream()
            .map(HobgoblinEntity::position)
            .reduce(Vec3.ZERO, Vec3::add)
            .scale(1.0 / children.size());
        final int index = children.stream()
            .sorted(Comparator.comparingInt(HobgoblinEntity::getId))
            .toList()
            .indexOf(child);
        final double angle = level.getGameTime() * 0.08 + Math.PI * 2.0 * index / children.size();
        final Vec3 danceStep = center.add(Math.cos(angle) * 2.25, 0.0, Math.sin(angle) * 2.25);
        child.getNavigation().moveTo(danceStep.x, danceStep.y, danceStep.z, 1.05);
        if (child.onGround() && Math.floorMod(child.tickCount + child.getId(), 20) == 0) {
            child.getJumpControl().jump();
        }
        return true;
    }

    public static boolean offerFlower(
        final HobgoblinEntity child,
        final ServerPlayer player,
        final ServerLevel level
    ) {
        if (!child.isBaby()
            || !player.isAlive()
            || player.isSpectator()
            || child.distanceToSqr(player) > 9.0
            || !GoblinSettlementLifeRules.giftReady(
                level.getGameTime(), child.nextFlowerGiftTime(), child.getMainHandItem().is(net.minecraftforge.common.Tags.Items.FLOWERS)
            )) {
            return false;
        }
        final ItemStack gift = child.getMainHandItem().copyWithCount(1);
        if (!player.addItem(gift)) {
            player.drop(gift, false);
        }
        child.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        child.recordFlowerGift(level.getGameTime() + GoblinSettlementLifeRules.GIFT_COOLDOWN_TICKS);
        child.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    static int residentCount(final HobgoblinEntity goblin, final ServerLevel level) {
        return level.getEntitiesOfClass(
            HobgoblinEntity.class,
            goblin.getBoundingBox().inflate(GoblinSettlementLifeRules.SETTLEMENT_RADIUS, 12.0,
                GoblinSettlementLifeRules.SETTLEMENT_RADIUS),
            resident -> resident.isAlive()
                && resident.creatureKind() == goblin.creatureKind()
                && !resident.isVillageRaider()
        ).size();
    }

    static int reachableBedCount(final HobgoblinEntity goblin, final ServerLevel level) {
        return Math.toIntExact(BlockPos.betweenClosedStream(
                goblin.blockPosition().offset(-GoblinSettlementLifeRules.SETTLEMENT_RADIUS, -8,
                    -GoblinSettlementLifeRules.SETTLEMENT_RADIUS),
                goblin.blockPosition().offset(GoblinSettlementLifeRules.SETTLEMENT_RADIUS, 8,
                    GoblinSettlementLifeRules.SETTLEMENT_RADIUS)
            )
            .filter(position -> level.getBlockState(position).getBlock() instanceof BedBlock)
            .filter(position -> level.getBlockState(position).getValue(BedBlock.PART) == BedPart.HEAD)
            .filter(position -> level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty())
            .count());
    }

    private static void tickAdult(final HobgoblinEntity goblin, final ServerLevel level) {
        final int residents = residentCount(goblin, level);
        if (GoblinSettlementLifeRules.needsHousing(residents, reachableBedCount(goblin, level))) {
            if (hutMaterials(goblin, level).isPresent()) {
                findHutSite(goblin, level).ifPresent(position -> tryBuildHutAt(goblin, level, position));
            } else if (!collectLooseBuildingMaterial(goblin, level)) {
                gatherOneTreeLog(goblin, level);
            }
            return;
        }
        if (GoblinSettlementLifeRules.shouldAttemptTunnel(goblin.getRandom().nextInt())) {
            findTunnelEntrance(goblin, level).ifPresent(entrance ->
                tryExcavateTunnelAt(goblin, level, entrance, goblin.getDirection())
            );
        }
    }

    private static void tickChild(final HobgoblinEntity child, final ServerLevel level) {
        if (child.getMainHandItem().isEmpty()) {
            gatherNearestFlower(child, level);
            return;
        }
        final Optional<ServerPlayer> player = level.players().stream()
            .filter(candidate -> !candidate.isSpectator() && candidate.isAlive())
            .filter(candidate -> candidate.distanceToSqr(child) <= 64.0)
            .min(Comparator.comparingDouble(child::distanceToSqr));
        if (player.isPresent()
            && GoblinSettlementLifeRules.giftReady(
                level.getGameTime(), child.nextFlowerGiftTime(), child.getMainHandItem().is(net.minecraftforge.common.Tags.Items.FLOWERS)
            )
            && child.getRandom().nextInt(80) == 0) {
            final ServerPlayer recipient = player.orElseThrow();
            if (child.distanceToSqr(recipient) <= 9.0) {
                offerFlower(child, recipient, level);
            } else {
                child.getNavigation().moveTo(recipient, 1.0);
            }
            return;
        }
        danceWithNearbyChildren(child, level);
    }

    private static boolean collectLooseBuildingMaterial(
        final HobgoblinEntity goblin,
        final ServerLevel level
    ) {
        final Optional<ItemEntity> loose = level.getEntitiesOfClass(
                ItemEntity.class,
                goblin.getBoundingBox().inflate(8.0),
                item -> item.isAlive() && isBuildingMaterial(item.getItem())
            ).stream()
            .min(Comparator.comparingDouble(goblin::distanceToSqr));
        if (loose.isEmpty()) {
            return false;
        }
        final ItemEntity item = loose.orElseThrow();
        if (goblin.distanceToSqr(item) > 4.0) {
            goblin.getNavigation().moveTo(item, 0.9);
            return true;
        }
        net.minecraft.world.entity.npc.InventoryCarrier.pickUpItem(level, goblin, goblin, item);
        return true;
    }

    private static boolean gatherOneTreeLog(final HobgoblinEntity goblin, final ServerLevel level) {
        final GoblinSettlementLifeData data = GoblinSettlementLifeData.get(level);
        final long key = settlementKey(goblin);
        if (!GoblinSettlementLifeRules.canGatherNaturalBlock(data.state(key).worldEdits())) {
            return false;
        }
        final Optional<BlockPos> target = BlockPos.betweenClosedStream(
                goblin.blockPosition().offset(-6, -2, -6),
                goblin.blockPosition().offset(6, 6, 6)
            )
            .filter(position -> level.getBlockState(position).is(net.minecraftforge.common.Tags.Blocks.NATURAL_LOGS)
                || level.getBlockState(position).is(BlockTags.LOGS))
            .filter(position -> leavesNear(level, position))
            .filter(position -> level.getBlockEntity(position) == null)
            .min(Comparator.comparingDouble(position -> goblin.distanceToSqr(Vec3.atCenterOf(position))));
        if (target.isEmpty()) {
            return false;
        }
        final BlockPos position = target.orElseThrow();
        if (goblin.distanceToSqr(Vec3.atCenterOf(position)) > 9.0) {
            goblin.getNavigation().moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0.85);
            return true;
        }
        final BlockState original = level.getBlockState(position);
        final ItemStack log = new ItemStack(original.getBlock().asItem());
        if (log.isEmpty()) {
            return false;
        }
        if (!level.destroyBlock(position, false, goblin)) {
            return false;
        }
        if (!data.recordNaturalBlockGathered(key)) {
            level.setBlockAndUpdate(position, original);
            return false;
        }
        final ItemStack remainder = goblin.getInventory().addItem(log);
        if (!remainder.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(level, position, remainder);
        }
        goblin.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static Optional<BlockPos> findHutSite(final HobgoblinEntity goblin, final ServerLevel level) {
        return HUT_OFFSETS.stream()
            .map(offset -> surface(level, goblin.blockPosition().offset(offset)))
            .filter(position -> !humanVillagerNearby(level, position))
            .filter(position -> clearHutFootprint(level, position))
            .findFirst();
    }

    private static Optional<BlockPos> findTunnelEntrance(final HobgoblinEntity goblin, final ServerLevel level) {
        return List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST).stream()
            .map(direction -> surface(level, goblin.blockPosition().relative(direction, 8)).below())
            .filter(position -> !humanVillagerNearby(level, position))
            .filter(position -> canExcavate(level, position))
            .findFirst();
    }

    private static boolean clearHutFootprint(final ServerLevel level, final BlockPos center) {
        final boolean clear = BlockPos.betweenClosedStream(
                center.offset(-1, 0, -2),
                center.offset(1, 2, 1)
            )
            .allMatch(position -> level.getBlockEntity(position) == null
                && level.getFluidState(position).isEmpty()
                && level.getBlockState(position).canBeReplaced());
        return clear && BlockPos.betweenClosedStream(center.offset(-1, -1, -2), center.offset(1, -1, 1))
            .allMatch(position -> level.getFluidState(position).isEmpty()
                && level.getBlockState(position).isFaceSturdy(level, position, Direction.UP));
    }

    private static Optional<Map<BlockPos, BlockState>> buildHut(
        final ServerLevel level,
        final BlockPos center,
        final net.minecraft.world.level.block.Block dirt,
        final net.minecraft.world.level.block.Block planks
    ) {
        final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        final List<BlockPlacement> placements = new java.util.ArrayList<>();
        for (int y = 0; y < 2; y++) {
            final int height = y;
            BlockPos.betweenClosedStream(center.offset(-1, height, -2), center.offset(1, height, 1))
                .filter(position -> Math.abs(position.getX() - center.getX()) == 1
                    || position.getZ() == center.getZ() - 2
                    || position.getZ() == center.getZ() + 1)
                .filter(position -> !(position.getX() == center.getX()
                    && position.getZ() == center.getZ() + 1))
                .forEach(position -> placements.add(new BlockPlacement(position.immutable(), dirt.defaultBlockState())));
        }
        BlockPos.betweenClosedStream(center.offset(-1, 2, -2), center.offset(1, 2, 1))
            .forEach(position -> placements.add(new BlockPlacement(position.immutable(), planks.defaultBlockState())));
        final BlockState foot = Blocks.BED.brown().defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT)
            .setValue(BedBlock.FACING, Direction.NORTH);
        final BlockState head = foot.setValue(BedBlock.PART, BedPart.HEAD);
        placements.add(new BlockPlacement(center.immutable(), foot));
        placements.add(new BlockPlacement(center.north().immutable(), head));
        for (final BlockPlacement placement : placements) {
            originals.putIfAbsent(placement.position(), level.getBlockState(placement.position()));
            if (!level.setBlockAndUpdate(placement.position(), placement.state())) {
                restore(level, originals);
                return Optional.empty();
            }
        }
        return Optional.of(Map.copyOf(originals));
    }

    private static void restore(final ServerLevel level, final Map<BlockPos, BlockState> originals) {
        originals.forEach(level::setBlockAndUpdate);
    }

    private static boolean canExcavate(final ServerLevel level, final BlockPos position) {
        final BlockState state = level.getBlockState(position);
        return level.getBlockEntity(position) == null
            && level.getFluidState(position).isEmpty()
            && (state.isAir()
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.DIRT)
                || state.is(net.minecraftforge.common.Tags.Blocks.STONES)
                || state.is(net.minecraftforge.common.Tags.Blocks.COBBLESTONES)
                || state.is(net.minecraftforge.common.Tags.Blocks.GRAVELS))
            && state.getDestroySpeed(level, position) >= 0.0F;
    }

    private static boolean leavesNear(final ServerLevel level, final BlockPos position) {
        return BlockPos.betweenClosedStream(position.offset(-2, -1, -2), position.offset(2, 3, 2))
            .anyMatch(candidate -> level.getBlockState(candidate).is(BlockTags.LEAVES));
    }

    private static boolean isGatherableFlower(final BlockState state) {
        return (state.is(BlockTags.SMALL_FLOWERS)
            || state.is(net.minecraftforge.common.Tags.Blocks.FLOWERS)
            || state.getBlock().asItem().getDefaultInstance().is(net.minecraftforge.common.Tags.Items.FLOWERS))
            && !(state.getBlock() instanceof DoublePlantBlock);
    }

    private static Optional<HutMaterials> hutMaterials(
        final HobgoblinEntity goblin,
        final ServerLevel level
    ) {
        final Optional<ItemStack> dirt = inventoryStacks(goblin)
            .filter(stack -> stack.is(ItemTags.DIRT))
            .filter(stack -> stack.getItem() instanceof BlockItem)
            .filter(stack -> count(goblin, candidate -> candidate.is(stack.getItem()))
                >= GoblinSettlementLifeRules.HUT_DIRT_COST)
            .findFirst();
        final Optional<ItemStack> log = inventoryStacks(goblin)
            .filter(GoblinSettlementLifeRuntime::isHutLog)
            .filter(stack -> count(goblin, candidate -> candidate.is(stack.getItem()))
                >= GoblinSettlementLifeRules.HUT_LOG_COST)
            .filter(stack -> plankRecipe(level, stack).isPresent())
            .findFirst();
        if (dirt.isEmpty() || log.isEmpty()) {
            return Optional.empty();
        }
        final ItemStack planks = plankRecipe(level, log.orElseThrow()).orElseThrow();
        if (!(dirt.orElseThrow().getItem() instanceof BlockItem dirtBlock)
            || !(planks.getItem() instanceof BlockItem plankBlock)
            || planks.getCount() * GoblinSettlementLifeRules.HUT_LOG_COST < 12) {
            return Optional.empty();
        }
        return Optional.of(new HutMaterials(
            dirt.orElseThrow().getItem(),
            log.orElseThrow().getItem(),
            dirtBlock.getBlock(),
            plankBlock.getBlock()
        ));
    }

    private static Optional<ItemStack> plankRecipe(final ServerLevel level, final ItemStack log) {
        final CraftingInput input = CraftingInput.of(1, 1, List.of(log.copyWithCount(1)));
        return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
            .map(recipe -> recipe.value().assemble(input))
            .filter(result -> !result.isEmpty() && result.is(ItemTags.PLANKS));
    }

    private static java.util.stream.Stream<ItemStack> inventoryStacks(final HobgoblinEntity goblin) {
        return IntStream.range(0, goblin.getInventory().getContainerSize())
            .mapToObj(goblin.getInventory()::getItem)
            .filter(stack -> !stack.isEmpty());
    }

    private static int count(
        final HobgoblinEntity goblin,
        final java.util.function.Predicate<ItemStack> predicate
    ) {
        return IntStream.range(0, goblin.getInventory().getContainerSize())
            .mapToObj(goblin.getInventory()::getItem)
            .filter(predicate)
            .mapToInt(ItemStack::getCount)
            .sum();
    }

    private static void consume(
        final HobgoblinEntity goblin,
        final java.util.function.Predicate<ItemStack> predicate,
        final int amount
    ) {
        int remaining = amount;
        for (int slot = 0; slot < goblin.getInventory().getContainerSize() && remaining > 0; slot++) {
            final ItemStack stack = goblin.getInventory().getItem(slot);
            if (!predicate.test(stack)) {
                continue;
            }
            final int consumed = Math.min(stack.getCount(), remaining);
            stack.shrink(consumed);
            remaining -= consumed;
        }
    }

    private static boolean isBuildingMaterial(final ItemStack stack) {
        return stack.is(ItemTags.DIRT) || isHutLog(stack);
    }

    private static boolean isHutLog(final ItemStack stack) {
        return stack.is(net.minecraftforge.common.Tags.Items.NATURAL_LOGS) || stack.is(ItemTags.LOGS);
    }

    private static boolean eligible(final HobgoblinEntity goblin) {
        return GoblinSettlementLifeRules.participates(
            goblin.creatureKind(), goblin.isVillageRaider(), GoblinBossRules.isBoss(goblin.creatureKind())
        );
    }

    private static boolean humanVillagerNearby(final ServerLevel level, final BlockPos position) {
        return !level.getEntitiesOfClass(
            net.minecraft.world.entity.npc.villager.Villager.class,
            new AABB(position).inflate(GoblinSettlementLifeRules.SETTLEMENT_RADIUS, 8.0,
                GoblinSettlementLifeRules.SETTLEMENT_RADIUS),
            villager -> GoblinHostilityRules.isHumanVillager(villager.getType())
        ).isEmpty();
    }

    private static long settlementKey(final HobgoblinEntity goblin) {
        return GoblinSettlementLifeRules.settlementKey(goblin.blockPosition(), goblin.creatureKind());
    }

    private static BlockPos surface(final ServerLevel level, final BlockPos position) {
        return new BlockPos(
            position.getX(),
            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, position.getX(), position.getZ()),
            position.getZ()
        );
    }

    private record HutMaterials(
        net.minecraft.world.item.Item dirtItem,
        net.minecraft.world.item.Item logItem,
        net.minecraft.world.level.block.Block dirt,
        net.minecraft.world.level.block.Block planks
    ) {
    }

    private record BlockPlacement(BlockPos position, BlockState state) {
    }
}
