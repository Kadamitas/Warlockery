package com.kadamitas.warlockery.mutation;

import com.kadamitas.warlockery.block.CritterSnareBlock;
import com.kadamitas.warlockery.block.CritterSnarePayload;
import com.kadamitas.warlockery.block.GrassperBlock;
import com.kadamitas.warlockery.item.AttunedStoneItem;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public final class AdvancedMutationResolver {
    private AdvancedMutationResolver() {
    }

    public static Optional<BlockPos> findCenter(final Level level, final BlockPos clicked) {
        return List.of(clicked, clicked.above(), clicked.below()).stream()
            .filter(position -> level.getBlockState(position).is(AdvancedMutationTags.Blocks.COBWEBS))
            .findFirst();
    }

    public static Outcome attempt(
        final ServerLevel level,
        final BlockPos center,
        final Player player
    ) {
        final MutationContext context = scan(level, center);
        final AdvancedMutationAssessment assessment = AdvancedMutationRules.select(context.snapshot());
        if (!assessment.complete()) {
            player.sendOverlayMessage(missingMessage(assessment));
            return new Outcome(assessment.kind(), false, 0, assessment.diagnostic());
        }
        final int affected = switch (assessment.kind()) {
            case TOAD -> createToads(level, context);
            case MINEDRAKE -> createMinedrakes(level, context);
        };
        final String diagnostic = "\u2713 " + assessment.kind().displayName() + " mutation created " + affected;
        player.sendOverlayMessage(Component.translatable(
            "message.warlockery.advanced_mutation.created",
            Component.translatable(assessment.kind().translationKey()),
            affected
        ));
        return new Outcome(assessment.kind(), true, affected, diagnostic);
    }

    private static Component missingMessage(final AdvancedMutationAssessment assessment) {
        final MutableComponent details = Component.empty();
        for (int index = 0; index < assessment.missing().size(); index++) {
            if (index > 0) {
                details.append(Component.literal("; "));
            }
            final AdvancedMutationAssessment.MissingCondition condition = assessment.missing().get(index);
            final Component label = Component.translatable(
                "message.warlockery.advanced_mutation.requirement." + condition.id()
            );
            details.append(condition.required() == 1
                ? label
                : Component.translatable(
                    "message.warlockery.advanced_mutation.requirement_count",
                    label,
                    condition.present(),
                    condition.required()
                ));
        }
        return Component.translatable(
            "message.warlockery.advanced_mutation.missing",
            Component.translatable(assessment.kind().translationKey()),
            details
        );
    }

    private static MutationContext scan(final ServerLevel level, final BlockPos center) {
        final List<BlockPos> slimeSnares = BlockPos.betweenClosedStream(
                center.offset(-AdvancedMutationLayout.ENTITY_RADIUS, -1, -AdvancedMutationLayout.ENTITY_RADIUS),
                center.offset(AdvancedMutationLayout.ENTITY_RADIUS, 1, AdvancedMutationLayout.ENTITY_RADIUS)
            )
            .filter(position -> isSlimeSnare(level.getBlockState(position)))
            .map(BlockPos::immutable)
            .toList();
        final List<BlockPos> mandrakeCrops = AdvancedMutationLayout.cardinalRays(center).stream()
            .map(ray -> ray.stream()
                .filter(position -> isMatureMandrake(level.getBlockState(position)))
                .findFirst())
            .flatMap(Optional::stream)
            .toList();
        final List<IngredientSlot> grasspers = AdvancedMutationLayout.diagonalRays(center).stream()
            .map(ray -> ray.stream()
                .filter(position -> level.getBlockState(position).is(AdvancedMutationTags.Blocks.GRASSPERS))
                .findFirst())
            .flatMap(Optional::stream)
            .map(position -> new IngredientSlot(position, storedIngredient(level, position).orElse(ItemStack.EMPTY)))
            .toList();
        final AABB entityArea = new AABB(center).inflate(
            AdvancedMutationLayout.ENTITY_RADIUS,
            2.0,
            AdvancedMutationLayout.ENTITY_RADIUS
        );
        final List<Mob> toadHosts = nearbyHosts(level, center, entityArea, AdvancedMutationTags.EntityTypes.TOAD_HOSTS);
        final List<Mob> creeperHosts = nearbyHosts(level, center, entityArea, AdvancedMutationTags.EntityTypes.CREEPER_HOSTS);
        final List<Mob> livingMandrakes = nearbyHosts(
            level,
            center,
            entityArea,
            AdvancedMutationTags.EntityTypes.LIVING_MANDRAKES
        );
        final List<ItemStack> ingredients = grasspers.stream().map(IngredientSlot::stack).toList();
        final AdvancedMutationSnapshot snapshot = new AdvancedMutationSnapshot(
            level.getBlockState(center).is(AdvancedMutationTags.Blocks.COBWEBS),
            level.getFluidState(center.below()).is(AdvancedMutationTags.Fluids.MUTATION_WATER),
            slimeSnares.size(),
            grasspers.size(),
            count(ingredients, AdvancedMutationResolver::isMutandisExtremis),
            count(ingredients, AdvancedMutationResolver::isChargedAttunedStone),
            count(ingredients, stack -> stack.is(AdvancedMutationTags.Items.FOCUSED_WILL)),
            mandrakeCrops.size(),
            toadHosts.size(),
            creeperHosts.size(),
            livingMandrakes.size()
        );
        return new MutationContext(
            center,
            slimeSnares,
            mandrakeCrops,
            grasspers,
            toadHosts,
            creeperHosts,
            livingMandrakes,
            snapshot
        );
    }

    private static List<Mob> nearbyHosts(
        final ServerLevel level,
        final BlockPos center,
        final AABB area,
        final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> tag
    ) {
        return level.getEntitiesOfClass(
            Mob.class,
            area,
            entity -> entity.isAlive() && entity.typeHolder().is(tag)
        ).stream().sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(
            center.getX() + 0.5,
            center.getY() + 0.5,
            center.getZ() + 0.5
        ))).toList();
    }

    private static boolean isSlimeSnare(final BlockState state) {
        if (!state.is(AdvancedMutationTags.Blocks.SLIME_SNARES)) {
            return false;
        }
        return !(state.getBlock() instanceof CritterSnareBlock)
            || state.getValue(CritterSnareBlock.PAYLOAD) == CritterSnarePayload.SLIME;
    }

    private static boolean isMatureMandrake(final BlockState state) {
        return state.is(AdvancedMutationTags.Blocks.MANDRAKE_CROPS)
            && state.getBlock() instanceof CropBlock crop
            && crop.isMaxAge(state);
    }

    private static boolean isMutandisExtremis(final ItemStack stack) {
        return stack.is(AdvancedMutationTags.Items.MUTANDIS_EXTREMIS);
    }

    private static boolean isChargedAttunedStone(final ItemStack stack) {
        return stack.is(AdvancedMutationTags.Items.CHARGED_ATTUNED_STONES)
            && (!(stack.getItem() instanceof AttunedStoneItem) || AttunedStoneItem.storedPower(stack) > 0);
    }

    private static int count(
        final List<ItemStack> stacks,
        final java.util.function.Predicate<ItemStack> predicate
    ) {
        return Math.toIntExact(stacks.stream().filter(predicate).count());
    }

    private static Optional<ItemStack> storedIngredient(final ServerLevel level, final BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof GrassperBlock) {
            return GrassperBlock.storedItem(level, pos);
        }
        return itemHandlers(level.getBlockEntity(pos)).stream()
            .map(handler -> firstStack(handler, false))
            .flatMap(Optional::stream)
            .findFirst();
    }

    private static Optional<ItemStack> firstStack(final IItemHandler handler, final boolean extract) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                return Optional.of(extract
                    ? handler.extractItem(slot, 1, false)
                    : handler.getStackInSlot(slot).copyWithCount(1));
            }
        }
        return Optional.empty();
    }

    private static List<IItemHandler> itemHandlers(final BlockEntity blockEntity) {
        if (blockEntity == null) {
            return List.of();
        }
        final List<IItemHandler> handlers = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction)
                .resolve()
                .filter(handler -> handlers.stream().noneMatch(existing -> existing == handler))
                .ifPresent(handlers::add);
        }
        return List.copyOf(handlers);
    }

    private static int createToads(final ServerLevel level, final MutationContext context) {
        consumeWeb(level, context.center());
        context.grasspers().forEach(slot -> consumeIngredient(level, slot.position()));
        context.toadHosts().getFirst().discard();
        int spawned = 0;
        for (BlockPos snarePos : context.slimeSnares()) {
            clearSnare(level, snarePos);
            final Entity toad = ModEntities.ALL.get("toad").get().create(level, EntitySpawnReason.EVENT);
            if (toad instanceof Mob mob) {
                mob.snapTo(snarePos.getX() + 0.5, snarePos.getY() + 0.1, snarePos.getZ() + 0.5);
                mob.setPersistenceRequired();
                if (level.addFreshEntity(mob)) {
                    spawned++;
                }
            }
        }
        return spawned;
    }

    private static int createMinedrakes(final ServerLevel level, final MutationContext context) {
        consumeWeb(level, context.center());
        context.grasspers().forEach(slot -> consumeIngredient(level, slot.position()));
        context.creeperHosts().getFirst().discard();
        context.livingMandrakes().getFirst().discard();
        final BlockState minedrake = ModBlocks.ALL.get("dreamroot").get().defaultBlockState();
        return Math.toIntExact(context.mandrakeCrops().stream()
            .filter(position -> level.setBlockAndUpdate(position, minedrake))
            .count());
    }

    private static void consumeWeb(final ServerLevel level, final BlockPos center) {
        level.setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
    }

    private static void clearSnare(final ServerLevel level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CritterSnareBlock) {
            level.setBlockAndUpdate(pos, state.setValue(CritterSnareBlock.PAYLOAD, CritterSnarePayload.EMPTY));
        } else {
            level.destroyBlock(pos, false);
        }
    }

    private static void consumeIngredient(final ServerLevel level, final BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof GrassperBlock) {
            GrassperBlock.takeStoredItem(level, pos);
            return;
        }
        itemHandlers(level.getBlockEntity(pos)).stream()
            .map(handler -> firstStack(handler, true))
            .flatMap(Optional::stream)
            .findFirst();
    }

    public record Outcome(
        AdvancedMutationKind kind,
        boolean success,
        int affected,
        String diagnostic
    ) {
        public Outcome {
            if (affected < 0 || diagnostic.isBlank()) {
                throw new IllegalArgumentException("Mutation outcomes require a diagnostic and nonnegative count");
            }
        }
    }

    private record IngredientSlot(BlockPos position, ItemStack stack) {
    }

    private record MutationContext(
        BlockPos center,
        List<BlockPos> slimeSnares,
        List<BlockPos> mandrakeCrops,
        List<IngredientSlot> grasspers,
        List<Mob> toadHosts,
        List<Mob> creeperHosts,
        List<Mob> livingMandrakes,
        AdvancedMutationSnapshot snapshot
    ) {
    }
}
