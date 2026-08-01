package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.brew.BrewRuntime;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class CustomBrewTriggerData extends SavedData {
    private static final Codec<CustomBrewTriggerData> CODEC = ArmedTrigger.CODEC.listOf()
        .xmap(CustomBrewTriggerData::new, data -> List.copyOf(data.triggers));
    public static final SavedDataType<CustomBrewTriggerData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "custom_brew_triggers"),
        CustomBrewTriggerData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final List<ArmedTrigger> triggers;
    private final Map<Long, PendingActivator> pendingActivators;

    public CustomBrewTriggerData() {
        triggers = new ArrayList<>();
        pendingActivators = new HashMap<>();
    }

    private CustomBrewTriggerData(final List<ArmedTrigger> triggers) {
        this.triggers = new ArrayList<>(triggers);
        pendingActivators = new HashMap<>();
    }

    public static CustomBrewTriggerData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean arm(final ServerLevel level, final BlockPos position, final CustomBrewFormula formula) {
        return arm(level, position, formula, null);
    }

    public boolean arm(
        final ServerLevel level,
        final BlockPos position,
        final CustomBrewFormula formula,
        final @Nullable Entity armer
    ) {
        final BlockState state = level.getBlockState(position);
        if (!supports(state)) {
            return false;
        }
        final long packed = position.asLong();
        final Optional<UUID> owner = Optional.ofNullable(armer).map(Entity::getUUID);
        for (int index = 0; index < triggers.size(); index++) {
            final ArmedTrigger trigger = triggers.get(index);
            if (trigger.position() == packed && trigger.formula().equals(formula)) {
                triggers.set(index, trigger.addCharge(owner));
                setDirty();
                return true;
            }
        }
        triggers.add(new ArmedTrigger(packed, formula, 1, active(state), owner));
        setDirty();
        return true;
    }

    public static void handleBlockUse(final ServerLevel level, final BlockPos position, final ServerPlayer player) {
        get(level).noteActivator(level, position, player);
    }

    private void noteActivator(
        final ServerLevel level,
        final BlockPos position,
        final LivingEntity activator
    ) {
        final long expiration = level.getGameTime() + 5L;
        List.of(position, position.above(), position.below()).stream()
            .mapToLong(BlockPos::asLong)
            .filter(packed -> triggers.stream().anyMatch(trigger -> trigger.position() == packed))
            .forEach(packed -> pendingActivators.put(
                packed,
                new PendingActivator(activator.getUUID(), expiration)
            ));
    }

    public void tick(final ServerLevel level) {
        boolean changed = false;
        pendingActivators.entrySet().removeIf(entry -> entry.getValue().expiration() < level.getGameTime());
        final Set<Long> activatedPositions = new HashSet<>();
        final ListIterator<ArmedTrigger> iterator = triggers.listIterator();
        while (iterator.hasNext()) {
            final ArmedTrigger trigger = iterator.next();
            final BlockPos position = BlockPos.of(trigger.position());
            if (!level.isLoaded(position)) {
                continue;
            }
            final BlockState state = level.getBlockState(position);
            if (!supports(state)) {
                iterator.remove();
                changed = true;
                continue;
            }
            final boolean active = active(state);
            if (active && !trigger.active()) {
                final var target = activationTarget(level, position);
                if (target.isPresent()) {
                    activate(
                        level,
                        position,
                        trigger.formula(),
                        target.orElseThrow(),
                        trigger.owner().map(level::getEntity).orElse(null)
                    );
                    activatedPositions.add(trigger.position());
                    changed = true;
                    if (trigger.charges() <= 1) {
                        iterator.remove();
                    } else {
                        iterator.set(trigger.consume().withActive(true));
                    }
                    continue;
                }
            }
            if (active != trigger.active()) {
                iterator.set(trigger.withActive(active));
                changed = true;
            }
        }
        activatedPositions.forEach(pendingActivators::remove);
        if (changed) {
            setDirty();
        }
    }

    public int size() {
        return triggers.size();
    }

    public static boolean supports(final BlockState state) {
        return state.hasProperty(BlockStateProperties.POWERED)
            || state.hasProperty(BlockStateProperties.OPEN)
            || state.hasProperty(BlockStateProperties.POWER);
    }

    public static boolean active(final BlockState state) {
        if (state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED)) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) {
            return true;
        }
        return state.hasProperty(BlockStateProperties.POWER) && state.getValue(BlockStateProperties.POWER) > 0;
    }

    private static java.util.Optional<LivingEntity> closestTarget(
        final ServerLevel level,
        final BlockPos position
    ) {
        final Vec3 center = Vec3.atCenterOf(position);
        return level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(position).inflate(2.5),
            LivingEntity::isAlive
        ).stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));
    }

    private Optional<LivingEntity> activationTarget(final ServerLevel level, final BlockPos position) {
        final PendingActivator pending = pendingActivators.get(position.asLong());
        if (pending != null && pending.expiration() >= level.getGameTime()) {
            final Entity entity = level.getEntity(pending.entity());
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return Optional.of(living);
            }
        }
        return closestTarget(level, position);
    }

    private static void activate(
        final ServerLevel level,
        final BlockPos position,
        final CustomBrewFormula formula,
        final LivingEntity target,
        final @Nullable Entity owner
    ) {
        final int effects = CustomBrewRuntime.applyEffectsTo(level, formula, target, owner, owner);
        final BrewRuntime.ImpactResult result = CustomBrewRuntime.handleImpactTo(level, formula, target, owner, owner);
        level.sendParticles(
            ParticleTypes.WITCH,
            target.getX(),
            target.getY() + target.getBbHeight() * 0.5,
            target.getZ(),
            Math.max(12, 12 + effects + result.affectedEntities()),
            0.35,
            0.45,
            0.35,
            0.02
        );
        level.playSound(
            null,
            position,
            SoundEvents.BREWING_STAND_BREW,
            SoundSource.BLOCKS,
            0.8F,
            1.2F
        );
    }

    public record ArmedTrigger(
        long position,
        CustomBrewFormula formula,
        int charges,
        boolean active,
        Optional<UUID> owner
    ) {
        static final Codec<ArmedTrigger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("position").forGetter(ArmedTrigger::position),
            CustomBrewFormula.CODEC.fieldOf("formula").forGetter(ArmedTrigger::formula),
            Codec.intRange(1, 64).fieldOf("charges").forGetter(ArmedTrigger::charges),
            Codec.BOOL.optionalFieldOf("active", false).forGetter(ArmedTrigger::active),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(ArmedTrigger::owner)
        ).apply(instance, ArmedTrigger::new));

        public ArmedTrigger(
            final long position,
            final CustomBrewFormula formula,
            final int charges,
            final boolean active
        ) {
            this(position, formula, charges, active, Optional.empty());
        }

        public ArmedTrigger addCharge() {
            return addCharge(Optional.empty());
        }

        public ArmedTrigger addCharge(final Optional<UUID> armer) {
            return new ArmedTrigger(
                position,
                formula,
                Math.min(64, charges + 1),
                active,
                armer.isPresent() ? armer : owner
            );
        }

        public ArmedTrigger consume() {
            return new ArmedTrigger(position, formula, Math.max(1, charges - 1), active, owner);
        }

        public ArmedTrigger withActive(final boolean active) {
            return new ArmedTrigger(position, formula, charges, active, owner);
        }
    }

    private record PendingActivator(UUID entity, long expiration) {
    }
}
