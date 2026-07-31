package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.block.ConnectedGlyphBlock;
import com.kadamitas.warlockery.block.StatueBlock;
import com.kadamitas.warlockery.crafting.AltarPowerNetwork;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class VeilWaystoneRuntime {
    private static final String READY_AT = "WarlockeryVeilWaystoneReadyAt";
    private static final String USED = "WarlockeryVeilWaystoneUsed";

    private VeilWaystoneRuntime() {
    }

    public static void tick(final ItemEntity entity, final WaystoneItem.Kind kind) {
        if (!(entity.level() instanceof ServerLevel level) || kind == WaystoneItem.Kind.CREATURE) {
            return;
        }
        final CompoundTag data = entity.getPersistentData();
        if (data.getBooleanOr(USED, false)) {
            return;
        }
        final Optional<BlockPos> center = ringCenter(level, entity.blockPosition(), kind);
        if (center.isEmpty()) {
            data.remove(READY_AT);
            return;
        }
        final long gameTime = level.getGameTime();
        if (!data.contains(READY_AT)) {
            data.putLong(READY_AT, gameTime + VeilWaystoneRules.ATTUNEMENT_TICKS);
            return;
        }
        if (gameTime < data.getLongOr(READY_AT, Long.MAX_VALUE)) {
            return;
        }
        if (kind == WaystoneItem.Kind.BASE) {
            bind(level, center.orElseThrow(), entity);
        } else {
            transpose(level, center.orElseThrow(), entity);
        }
    }

    static boolean attempted(final ItemEntity entity) {
        return entity.getPersistentData().getBooleanOr(USED, false);
    }

    static Optional<BlockPos> ringCenter(
        final ServerLevel level,
        final BlockPos itemPosition,
        final WaystoneItem.Kind kind
    ) {
        return java.util.stream.IntStream.of(0, -1, 1)
            .mapToObj(deltaY -> itemPosition.offset(0, deltaY, 0))
            .filter(center -> kind == WaystoneItem.Kind.BASE
                ? isExactRing(
                    level,
                    center,
                    VeilWaystoneRules.bindingRingSet(),
                    VeilWaystoneRules.bindingSquare()
                )
                : isExactRing(
                    level,
                    center,
                    VeilWaystoneRules.transpositionRingSet(),
                    VeilWaystoneRules.transpositionSquare()
                ))
            .findFirst();
    }

    static boolean isExactRing(
        final ServerLevel level,
        final BlockPos center,
        final Set<BlockPos> expected,
        final Set<BlockPos> occupiedSquare
    ) {
        return occupiedSquare.stream().allMatch(offset -> {
            final BlockState state = level.getBlockState(center.offset(offset));
            if (expected.contains(offset)) {
                return state.is(ModBlocks.ALL.get("circleglyph_veil").get());
            }
            return !(state.getBlock() instanceof ConnectedGlyphBlock);
        });
    }

    private static void bind(final ServerLevel level, final BlockPos center, final ItemEntity trigger) {
        final List<ItemEntity> waystones = centerItems(level, center).stream()
            .filter(item -> item.getItem().is(ModItems.ALL.get("ingredient_waystone").get()))
            .toList();
        if (waystones.isEmpty() || waystones.size() > VeilWaystoneRules.MAX_POSITION_WAYSTONES) {
            markUsed(waystones.isEmpty() ? List.of(trigger) : waystones);
            notifyPlayer(level, trigger, Component.translatable("message.warlockery.veil_waystone.invalid_offering")
                .withStyle(ChatFormatting.RED));
            return;
        }
        final Optional<LivingEntity> target = nearestLiving(level, center);
        final VeilWaystoneRules.BindingMode mode = VeilWaystoneRules.bindingMode(waystones.size(), target.isPresent());
        final int power = VeilWaystoneRules.requiredPower(mode);
        markUsed(waystones);
        if (!AltarPowerNetwork.consume(level, center, power)) {
            notifyPlayer(level, trigger, Component.translatable(
                "message.warlockery.veil_waystone.missing_power",
                power
            ).withStyle(ChatFormatting.RED));
            fizzle(level, center);
            return;
        }
        if (mode == VeilWaystoneRules.BindingMode.CREATURE) {
            bindCreature(waystones.getFirst(), target.orElseThrow());
            notifyPlayer(level, trigger, Component.translatable(
                "message.warlockery.veil_waystone.bound_creature",
                target.orElseThrow().getDisplayName()
            ).withStyle(ChatFormatting.GREEN));
        } else {
            waystones.forEach(item -> bindPosition(item, level, center));
            notifyPlayer(level, trigger, Component.translatable(
                "message.warlockery.veil_waystone.bound_position",
                waystones.size()
            ).withStyle(ChatFormatting.GREEN));
        }
        flourish(level, center);
    }

    private static void bindPosition(final ItemEntity entity, final ServerLevel level, final BlockPos center) {
        final ItemStack bound = entity.getItem().transmuteCopy(
            ModItems.ALL.get("ingredient_waystone_bound").get(),
            entity.getItem().getCount()
        );
        WaystoneState.write(bound, level.dimension().identifier(), center);
        bound.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable(
            "tooltip.warlockery.waystone.position",
            level.dimension().identifier().toString(),
            center.getX(),
            center.getY(),
            center.getZ()
        ))));
        entity.setItem(bound);
    }

    private static void bindCreature(final ItemEntity entity, final LivingEntity target) {
        final ItemStack bound = entity.getItem().transmuteCopy(
            ModItems.ALL.get("ingredient_waystone_creature_bound").get(),
            1
        );
        final SympatheticBinding binding = SympatheticBinding.from(target);
        binding.write(bound);
        bound.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable(
            "tooltip.warlockery.waystone.creature",
            binding.targetName()
        ))));
        entity.setItem(bound);
    }

    private static void transpose(final ServerLevel level, final BlockPos center, final ItemEntity trigger) {
        markUsed(List.of(trigger));
        final Optional<WaystoneState.Location> location = WaystoneState.read(trigger.getItem());
        if (location.isEmpty()) {
            fail(level, center, trigger, "missing_destination");
            return;
        }
        final WaystoneState.Location destination = location.orElseThrow();
        if (!destination.dimension().equals(level.dimension().identifier())) {
            fail(level, center, trigger, "same_realm_only");
            return;
        }
        level.getChunkAt(destination.position());
        if (hasInhibitor(level, center) || hasInhibitor(level, destination.position())) {
            fail(level, center, trigger, "inhibited");
            return;
        }
        final Optional<BlockPos> landing = safeLanding(level, destination.position());
        if (landing.isEmpty()) {
            fail(level, center, trigger, "unsafe_destination");
            return;
        }
        if (!AltarPowerNetwork.consume(level, center, VeilWaystoneRules.TELEPORT_POWER)) {
            notifyPlayer(level, trigger, Component.translatable(
                "message.warlockery.veil_waystone.missing_power",
                VeilWaystoneRules.TELEPORT_POWER
            ).withStyle(ChatFormatting.RED));
            fizzle(level, center);
            return;
        }
        final List<Entity> travellers = nearbyTravellers(level, center);
        final BlockPos arrival = landing.orElseThrow();
        notifyPlayer(level, trigger, Component.translatable(
            "message.warlockery.veil_waystone.travelled",
            travellers.size()
        ).withStyle(ChatFormatting.GREEN));
        flourish(level, center);
        travellers.forEach(entity -> entity.teleportTo(
            arrival.getX() + 0.5D,
            arrival.getY(),
            arrival.getZ() + 0.5D
        ));
        flourish(level, arrival);
    }

    private static void fail(
        final ServerLevel level,
        final BlockPos center,
        final ItemEntity trigger,
        final String reason
    ) {
        notifyPlayer(level, trigger, Component.translatable(
            "message.warlockery.veil_waystone." + reason
        ).withStyle(ChatFormatting.RED));
        fizzle(level, center);
    }

    private static List<ItemEntity> centerItems(final ServerLevel level, final BlockPos center) {
        return level.getEntitiesOfClass(
            ItemEntity.class,
            new AABB(center).inflate(0.48D, 0.9D, 0.48D),
            ItemEntity::isAlive
        );
    }

    private static Optional<LivingEntity> nearestLiving(final ServerLevel level, final BlockPos center) {
        return level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(center).inflate(1.5D, 2.0D, 1.5D),
            LivingEntity::isAlive
        ).stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(
            center.getX() + 0.5D,
            center.getY() + 0.5D,
            center.getZ() + 0.5D
        )));
    }

    private static List<Entity> nearbyTravellers(final ServerLevel level, final BlockPos center) {
        final AABB interior = new AABB(center).inflate(1.48D, 2.0D, 1.48D);
        return level.getEntities(
            (Entity) null,
            interior,
            entity -> entity.isAlive() && (entity instanceof LivingEntity || entity instanceof ItemEntity)
        );
    }

    private static Optional<BlockPos> safeLanding(final ServerLevel level, final BlockPos origin) {
        return java.util.stream.IntStream.rangeClosed(0, 2)
            .boxed()
            .flatMap(radius -> BlockPos.betweenClosedStream(
                origin.offset(-radius, -2, -radius),
                origin.offset(radius, 2, radius)
            ))
            .map(BlockPos::immutable)
            .filter(position -> level.isEmptyBlock(position))
            .filter(position -> level.isEmptyBlock(position.above()))
            .filter(position -> level.getBlockState(position.below()).isFaceSturdy(
                level,
                position.below(),
                Direction.UP
            ))
            .findFirst();
    }

    private static boolean hasInhibitor(final ServerLevel level, final BlockPos center) {
        return BlockPos.betweenClosedStream(
            center.offset(-VeilWaystoneRules.INHIBITOR_RADIUS, -VeilWaystoneRules.INHIBITOR_RADIUS,
                -VeilWaystoneRules.INHIBITOR_RADIUS),
            center.offset(VeilWaystoneRules.INHIBITOR_RADIUS, VeilWaystoneRules.INHIBITOR_RADIUS,
                VeilWaystoneRules.INHIBITOR_RADIUS)
        ).map(level::getBlockState).anyMatch(state -> state.is(WarlockeryTags.Blocks.RITUAL_INHIBITORS)
            && (!(state.getBlock() instanceof StatueBlock statue) || statue.occludes(state)));
    }

    private static void markUsed(final List<ItemEntity> items) {
        items.forEach(item -> {
            item.getPersistentData().putBoolean(USED, true);
            item.getPersistentData().remove(READY_AT);
        });
    }

    private static void notifyPlayer(final ServerLevel level, final ItemEntity entity, final Component message) {
        final Entity owner = entity.getOwner();
        final ServerPlayer recipient = owner instanceof ServerPlayer player
            && player.distanceToSqr(entity) <= VeilWaystoneRules.MESSAGE_RANGE * VeilWaystoneRules.MESSAGE_RANGE
            ? player
            : level.getNearestPlayer(
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                VeilWaystoneRules.MESSAGE_RANGE,
                candidate -> candidate instanceof ServerPlayer
            ) instanceof ServerPlayer nearby ? nearby : null;
        if (recipient != null) {
            recipient.sendSystemMessage(message);
        }
    }

    private static void flourish(final ServerLevel level, final BlockPos center) {
        level.sendParticles(
            ParticleTypes.PORTAL,
            center.getX() + 0.5D,
            center.getY() + 0.4D,
            center.getZ() + 0.5D,
            32,
            1.1D,
            0.35D,
            1.1D,
            0.08D
        );
    }

    private static void fizzle(final ServerLevel level, final BlockPos center) {
        level.sendParticles(
            ParticleTypes.SMOKE,
            center.getX() + 0.5D,
            center.getY() + 0.35D,
            center.getZ() + 0.5D,
            12,
            0.55D,
            0.15D,
            0.55D,
            0.01D
        );
    }
}
