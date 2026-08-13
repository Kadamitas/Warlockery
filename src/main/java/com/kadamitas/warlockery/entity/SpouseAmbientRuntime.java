package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.ritual.marriage.MarriageData;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpouseAmbientRuntime {
    static final String ACTION = "WarlockerySpouseAmbientAction";
    static final String ACTION_EXPIRES = "WarlockerySpouseAmbientExpires";
    static final String KISS_READY = "WarlockerySpouseKissReady";
    static final String COOK_READY = "WarlockerySpouseCookReady";
    static final String SOURCE = "WarlockerySpouseCookSource";
    static final String FURNACE = "WarlockerySpouseCookFurnace";
    static final String RESULT = "WarlockerySpouseCookResult";
    private static final String KISS = "kiss";
    private static final String GATHER = "gather";
    private static final String FURNACE_APPROACH = "furnace";
    private static final String COOKING = "cooking";
    private static final String DELIVERY = "delivery";
    private static final double ROUTINE_RADIUS = 16.0;

    private SpouseAmbientRuntime() {
    }

    public static boolean tick(final PathfinderMob spouse, final ServerLevel level) {
        return spousePlayer(spouse, level)
            .map(player -> tick(spouse, level, player))
            .orElse(false);
    }

    static Optional<ServerPlayer> spousePlayer(final PathfinderMob spouse, final ServerLevel level) {
        return MarriageData.get(level).ownerForNami(spouse.getUUID())
            .map(level.getServer().getPlayerList()::getPlayer)
            .filter(java.util.Objects::nonNull);
    }

    public static boolean tick(
        final PathfinderMob spouse,
        final ServerLevel level,
        final ServerPlayer player
    ) {
        if (!isMarriedTo(spouse, level, player)) {
            abort(spouse, level, player);
            return false;
        }
        final String action = spouse.getPersistentData().getStringOr(ACTION, "");
        if (!action.isBlank()) {
            return continueRoutine(spouse, level, player, action);
        }
        if (spouse.tickCount % SpouseAmbientRules.DECISION_INTERVAL_TICKS != 0) {
            return false;
        }
        final Optional<CookingTask> cookingTask = findCookingTask(spouse, level, player);
        final SpouseAmbientRules.Context context = context(spouse, level, player, cookingTask.isPresent());
        return switch (SpouseAmbientRules.choose(
            context,
            level.getRandom().nextInt(SpouseAmbientRules.KISS_ROLL_BOUND),
            level.getRandom().nextInt(SpouseAmbientRules.COOK_ROLL_BOUND)
        )) {
            case KISS -> beginKiss(spouse, level);
            case COOK -> beginCooking(spouse, level, cookingTask.orElseThrow());
            case NONE -> false;
        };
    }

    public static void abort(
        final PathfinderMob spouse,
        final ServerLevel level,
        final ServerPlayer player
    ) {
        final String action = spouse.getPersistentData().getStringOr(ACTION, "");
        if ((GATHER.equals(action) || FURNACE_APPROACH.equals(action)) && !spouse.getMainHandItem().isEmpty()) {
            dropFor(spouse, level, player, spouse.getMainHandItem().copy());
            spouse.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        clearAction(spouse);
    }

    static SpouseAmbientRules.Context context(
        final PathfinderMob spouse,
        final ServerLevel level,
        final ServerPlayer player,
        final boolean hasCookWork
    ) {
        return new SpouseAmbientRules.Context(
            isMarriedTo(spouse, level, player),
            player.level() == level,
            peaceful(spouse, player),
            HazardEscapeRuntime.currentHazard(spouse, level).isEmpty(),
            !spouse.isBaby(),
            spouse.getMainHandItem().isEmpty(),
            hasCookWork,
            level.getGameTime(),
            spouse.getPersistentData().getLongOr(KISS_READY, 0L),
            spouse.getPersistentData().getLongOr(COOK_READY, 0L)
        );
    }

    static boolean beginKiss(final PathfinderMob spouse, final ServerLevel level) {
        start(spouse, level, KISS);
        return true;
    }

    static boolean beginCooking(
        final PathfinderMob spouse,
        final ServerLevel level,
        final CookingTask task
    ) {
        if (!spouse.getMainHandItem().isEmpty()) {
            return false;
        }
        start(spouse, level, GATHER);
        spouse.getPersistentData().putString(SOURCE, task.source().getUUID().toString());
        spouse.getPersistentData().putLong(FURNACE, task.furnace().asLong());
        spouse.getPersistentData().putString(RESULT, task.resultId().toString());
        return true;
    }

    static Optional<CookingTask> findCookingTask(
        final PathfinderMob spouse,
        final ServerLevel level,
        final ServerPlayer player
    ) {
        if (!contextPrerequisites(spouse, level, player)) {
            return Optional.empty();
        }
        final AABB search = spouse.getBoundingBox().inflate(ROUTINE_RADIUS);
        return level.getEntitiesOfClass(ItemEntity.class, search, source -> eligibleSource(source, player)).stream()
            .flatMap(source -> nearbyFurnaces(spouse, level)
                .map(furnace -> cookingTask(level, source, furnace)))
            .flatMap(Optional::stream)
            .min(Comparator.comparingDouble(task -> spouse.distanceToSqr(task.source())
                + Vec3.atCenterOf(task.furnace()).distanceToSqr(spouse.position())));
    }

    private static boolean continueRoutine(
        final PathfinderMob spouse,
        final ServerLevel level,
        final ServerPlayer player,
        final String action
    ) {
        if (level.getGameTime() > spouse.getPersistentData().getLongOr(ACTION_EXPIRES, 0L)) {
            abort(spouse, level, player);
            return false;
        }
        if (!contextPrerequisites(spouse, level, player)) {
            spouse.getNavigation().stop();
            return true;
        }
        return switch (action) {
            case KISS -> continueKiss(spouse, level, player);
            case GATHER -> continueGather(spouse, level, player);
            case FURNACE_APPROACH -> continueFurnaceApproach(spouse, level, player);
            case COOKING -> continueCooking(spouse, level, player);
            case DELIVERY -> continueDelivery(spouse, level, player);
            default -> {
                abort(spouse, level, player);
                yield false;
            }
        };
    }

    private static boolean continueKiss(
        final PathfinderMob spouse,
        final ServerLevel level,
        final ServerPlayer player
    ) {
        if (spouse.distanceToSqr(player) > 2.25) {
            spouse.getNavigation().moveTo(player, 1.05);
            return true;
        }
        final Vec3 toward = player.position().subtract(spouse.position());
        if (toward.lengthSqr() > 1.0E-4) {
            spouse.setDeltaMovement(spouse.getDeltaMovement().add(toward.normalize().scale(0.06)));
        }
        spouse.lookAt(player, 45.0F, 45.0F);
        level.sendParticles(ParticleTypes.HEART, spouse.getX(), spouse.getEyeY() + 0.25, spouse.getZ(), 5, 0.3, 0.2, 0.3, 0.02);
        level.playSound(null, spouse.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.45F, 1.45F);
        spouse.getPersistentData().putLong(
            KISS_READY,
            SpouseAmbientRules.nextReadyAt(SpouseAmbientRules.Routine.KISS, level.getGameTime())
        );
        clearAction(spouse);
        return false;
    }

    private static boolean continueGather(
        final PathfinderMob spouse,
        final ServerLevel level,
        final ServerPlayer player
    ) {
        final Optional<ItemEntity> source = source(level, spouse);
        if (source.isEmpty() || !eligibleSource(source.orElseThrow(), player)) {
            abort(spouse, level, player);
            return false;
        }
        final ItemEntity item = source.orElseThrow();
        if (spouse.distanceToSqr(item) > 2.25) {
            spouse.getNavigation().moveTo(item, 1.0);
            return true;
        }
        final ItemStack held = item.getItem().copyWithCount(1);
        final ItemStack remaining = item.getItem().copy();
        remaining.shrink(1);
        if (remaining.isEmpty()) {
            item.discard();
        } else {
            item.setItem(remaining);
        }
        spouse.setItemSlot(EquipmentSlot.MAINHAND, held);
        spouse.getPersistentData().putString(ACTION, FURNACE_APPROACH);
        return true;
    }

    private static boolean continueFurnaceApproach(
        final PathfinderMob spouse,
        final ServerLevel level,
        final ServerPlayer player
    ) {
        final BlockPos position = BlockPos.of(spouse.getPersistentData().getLongOr(FURNACE, 0L));
        final Optional<SpouseCookingMachine> machine = SpouseCookingMachine.at(level, position);
        if (machine.isEmpty()) {
            abort(spouse, level, player);
            return false;
        }
        if (Vec3.atCenterOf(position).distanceToSqr(spouse.position()) > 4.0) {
            spouse.getNavigation().moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 1.0);
            return true;
        }
        final ItemStack raw = spouse.getMainHandItem();
        final Optional<ItemStack> result = cookingResult(level, machine.orElseThrow(), raw);
        if (!machine.orElseThrow().availableFor(level, raw)
            || result.isEmpty()
            || !resultMatches(spouse, result.orElseThrow())) {
            abort(spouse, level, player);
            return false;
        }
        if (!machine.orElseThrow().insertOne(level, raw)) {
            abort(spouse, level, player);
            return false;
        }
        spouse.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        spouse.getPersistentData().putString(ACTION, COOKING);
        level.playSound(null, position, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.35F, 0.85F);
        return true;
    }

    private static boolean continueCooking(
        final PathfinderMob spouse,
        final ServerLevel level,
        final ServerPlayer player
    ) {
        final BlockPos position = BlockPos.of(spouse.getPersistentData().getLongOr(FURNACE, 0L));
        final Optional<SpouseCookingMachine> machine = SpouseCookingMachine.at(level, position);
        if (machine.isEmpty()) {
            clearAction(spouse);
            return false;
        }
        final Optional<Identifier> expected = resultId(spouse);
        final Optional<ItemStack> expectedStack = expected.flatMap(BuiltInRegistries.ITEM::get)
            .map(holder -> new ItemStack(holder.value()));
        final Optional<ItemStack> delivery = expectedStack.flatMap(
            expectedResult -> machine.orElseThrow().extractOne(level, expectedResult)
        );
        if (delivery.isPresent()) {
            spouse.setItemSlot(EquipmentSlot.MAINHAND, delivery.orElseThrow());
            spouse.getPersistentData().putString(ACTION, DELIVERY);
            return true;
        }
        if (!machine.orElseThrow().pending(level)) {
            clearAction(spouse);
            return false;
        }
        if (Vec3.atCenterOf(position).distanceToSqr(spouse.position()) > 9.0) {
            spouse.getNavigation().moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0.9);
        } else {
            spouse.getNavigation().stop();
        }
        return true;
    }

    private static boolean continueDelivery(
        final PathfinderMob spouse,
        final ServerLevel level,
        final ServerPlayer player
    ) {
        if (spouse.distanceToSqr(player) > 3.0) {
            spouse.getNavigation().moveTo(player, 1.05);
            return true;
        }
        final ItemStack delivery = spouse.getMainHandItem().copy();
        spouse.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!delivery.isEmpty() && !player.addItem(delivery) && !delivery.isEmpty()) {
            dropFor(spouse, level, player, delivery);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getEyeY(), player.getZ(), 7, 0.35, 0.35, 0.35, 0.04);
        level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.55F, 1.25F);
        spouse.getPersistentData().putLong(
            COOK_READY,
            SpouseAmbientRules.nextReadyAt(SpouseAmbientRules.Routine.COOK, level.getGameTime())
        );
        clearAction(spouse);
        return false;
    }

    private static boolean isMarriedTo(
        final PathfinderMob spouse,
        final ServerLevel level,
        final ServerPlayer player
    ) {
        return MarriageData.get(level).bond(player.getUUID())
            .filter(MarriageData.Bond::isNami)
            .filter(bond -> bond.partnerUuid().equals(spouse.getUUID()))
            .isPresent();
    }

    private static boolean contextPrerequisites(
        final PathfinderMob spouse,
        final ServerLevel level,
        final ServerPlayer player
    ) {
        return isMarriedTo(spouse, level, player)
            && player.level() == level
            && !spouse.isBaby()
            && peaceful(spouse, player)
            && HazardEscapeRuntime.currentHazard(spouse, level).isEmpty();
    }

    private static boolean peaceful(final PathfinderMob spouse, final ServerPlayer player) {
        return spouse.isAlive()
            && player.isAlive()
            && !player.isSpectator()
            && spouse.getTarget() == null
            && spouse.getLastHurtByMob() == null
            && player.getLastHurtByMob() == null;
    }

    private static boolean eligibleSource(final ItemEntity source, final ServerPlayer player) {
        return source.isAlive()
            && !source.hasPickUpDelay()
            && source.getItem().is(SpouseAmbientTags.COOKABLE_RAW_MEATS)
            && (source.getOwner() == null || source.getOwner() == player);
    }

    private static Stream<BlockPos> nearbyFurnaces(final PathfinderMob spouse, final ServerLevel level) {
        final BlockPos origin = spouse.blockPosition();
        final int radius = (int) ROUTINE_RADIUS;
        return BlockPos.betweenClosedStream(origin.offset(-radius, -4, -radius), origin.offset(radius, 4, radius))
            .filter(level::isLoaded)
            .filter(position -> SpouseCookingMachine.at(level, position).isPresent());
    }

    private static Optional<CookingTask> cookingTask(
        final ServerLevel level,
        final ItemEntity source,
        final BlockPos position
    ) {
        final Optional<SpouseCookingMachine> machine = SpouseCookingMachine.at(level, position);
        if (machine.isEmpty() || !machine.orElseThrow().availableFor(level, source.getItem())) {
            return Optional.empty();
        }
        return cookingResult(level, machine.orElseThrow(), source.getItem())
            .map(result -> new CookingTask(source, position.immutable(), BuiltInRegistries.ITEM.getKey(result.getItem())));
    }

    private static Optional<ItemStack> cookingResult(
        final ServerLevel level,
        final SpouseCookingMachine machine,
        final ItemStack stack
    ) {
        final SingleRecipeInput input = new SingleRecipeInput(stack.copyWithCount(1));
        return cookingResult(level, machine.recipeType(), input);
    }

    private static <T extends AbstractCookingRecipe> Optional<ItemStack> cookingResult(
        final ServerLevel level,
        final RecipeType<T> type,
        final SingleRecipeInput input
    ) {
        return level.recipeAccess().getRecipeFor(type, input, level)
            .map(recipe -> recipe.value().assemble(input))
            .filter(result -> !result.isEmpty());
    }

    private static Optional<ItemEntity> source(final ServerLevel level, final PathfinderMob spouse) {
        return uuid(spouse.getPersistentData().getStringOr(SOURCE, ""))
            .map(level::getEntity)
            .filter(ItemEntity.class::isInstance)
            .map(ItemEntity.class::cast);
    }

    private static Optional<Identifier> resultId(final PathfinderMob spouse) {
        final String value = spouse.getPersistentData().getStringOr(RESULT, "");
        return value.isBlank() ? Optional.empty() : Optional.ofNullable(Identifier.tryParse(value));
    }

    private static boolean resultMatches(final PathfinderMob spouse, final ItemStack result) {
        return resultId(spouse).filter(BuiltInRegistries.ITEM.getKey(result.getItem())::equals).isPresent();
    }

    private static Optional<UUID> uuid(final String value) {
        try {
            return value.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(value));
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static void start(final PathfinderMob spouse, final ServerLevel level, final String action) {
        spouse.getPersistentData().putString(ACTION, action);
        spouse.getPersistentData().putLong(
            ACTION_EXPIRES,
            Math.addExact(level.getGameTime(), SpouseAmbientRules.ROUTINE_TIMEOUT_TICKS)
        );
    }

    private static void clearAction(final PathfinderMob spouse) {
        spouse.getPersistentData().remove(ACTION);
        spouse.getPersistentData().remove(ACTION_EXPIRES);
        spouse.getPersistentData().remove(SOURCE);
        spouse.getPersistentData().remove(FURNACE);
        spouse.getPersistentData().remove(RESULT);
        spouse.getNavigation().stop();
    }

    private static void dropFor(
        final PathfinderMob spouse,
        final ServerLevel level,
        final ServerPlayer player,
        final ItemStack stack
    ) {
        if (stack.isEmpty()) {
            return;
        }
        final ItemEntity drop = new ItemEntity(level, spouse.getX(), spouse.getEyeY(), spouse.getZ(), stack);
        if (player != null) {
            drop.setTarget(player.getUUID());
        }
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }

    record CookingTask(ItemEntity source, BlockPos furnace, Identifier resultId) {
    }
}
