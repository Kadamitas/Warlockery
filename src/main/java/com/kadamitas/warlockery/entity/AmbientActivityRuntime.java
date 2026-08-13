package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.AmbientActivityProfile.ActivityType;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Comparator;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

public final class AmbientActivityRuntime {
    private static final String HEARTH_POSITION = "WarlockeryAmbientHearthPosition";
    private static final String HEARTH_EXPIRES = "WarlockeryAmbientHearthExpires";
    private static final String COOLDOWN_PREFIX = "WarlockeryAmbientCooldown";
    private static final Map<Mob, WeakReference<net.minecraft.world.level.block.entity.BlockEntity>> ACTIVE_HEARTHS =
        new WeakHashMap<>();

    private AmbientActivityRuntime() {
    }

    public static void tick(final Mob creature, final ServerLevel level, final CreatureKind kind) {
        clearExpiredHearth(creature, level);
        final long gameTime = level.getGameTime();
        final boolean combat = creature.getTarget() != null && creature.getTarget().isAlive()
            || TacticalCombatRuntime.hasRecentDirectedThreat(creature, gameTime);
        final boolean hazard = HazardEscapeRuntime.currentHazard(creature, level)
            .filter(found -> !FamiliarBondRules.isClassicFamiliar(kind)
                && HazardEscapeRules.shouldEscape(kind, found))
            .isPresent();
        if (!AmbientActivityRules.canStart(
            creature.isAlive(),
            creature.isNoAi(),
            combat,
            hazard,
            creature.isPassenger()
        )) {
            return;
        }
        for (final AmbientActivityProfile profile : AmbientActivityProfile.forKind(kind)) {
            if (tryActivity(creature, level, kind, profile, gameTime)) {
                return;
            }
        }
    }

    static boolean executeNow(
        final Mob creature,
        final ServerLevel level,
        final CreatureKind kind,
        final ActivityType type
    ) {
        final AmbientActivityProfile profile = AmbientActivityProfile.forType(type);
        if (!profile.kinds().contains(kind)) {
            return false;
        }
        return AmbientActivityFactory.create(type).perform(
            new AmbientActivityContext(creature, level, kind, profile)
        );
    }

    static boolean makeWinterHearth(final AmbientActivityContext context) {
        final Mob creature = context.creature();
        final ServerLevel level = context.level();
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)
            || activeHearth(creature, level).isPresent()
            || !isColdAround(creature, level)
            || countBlocks(level, creature.blockPosition(), AmbientActivityRules.SEARCH_RADIUS,
                state -> state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) > 0) {
            return false;
        }
        return hearthPosition(creature, level).map(position -> {
            if (!level.setBlockAndUpdate(position, Blocks.CAMPFIRE.defaultBlockState())) {
                return false;
            }
            final var hearth = level.getBlockEntity(position);
            if (hearth == null) {
                level.removeBlock(position, false);
                return false;
            }
            AmbientActivityHearthData.get(level).claim(position, creature.getUUID(), level.getBlockState(position));
            ACTIVE_HEARTHS.put(creature, new WeakReference<>(hearth));
            creature.getPersistentData().putLong(HEARTH_POSITION, position.asLong());
            creature.getPersistentData().putLong(
                HEARTH_EXPIRES,
                context.gameTime() + AmbientActivityRules.TEMPORARY_HEARTH_TICKS
            );
            level.sendParticles(
                ParticleTypes.FLAME,
                position.getX() + 0.5,
                position.getY() + 0.55,
                position.getZ() + 0.5,
                8,
                0.25,
                0.15,
                0.25,
                0.01
            );
            return true;
        }).orElse(false);
    }

    static boolean tendGrove(final AmbientActivityContext context) {
        final ServerLevel level = context.level();
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)
            || countBlocks(level, context.creature().blockPosition(), AmbientActivityRules.SEARCH_RADIUS,
                state -> state.getBlock().asItem().getDefaultInstance().is(ItemTags.SAPLINGS)) >= 12) {
            return false;
        }
        final Optional<ItemEntity> sapling = level.getEntitiesOfClass(
                ItemEntity.class,
                context.creature().getBoundingBox().inflate(8.0),
                item -> item.isAlive() && item.getItem().is(ItemTags.SAPLINGS)
                    && item.getItem().getItem() instanceof BlockItem
            ).stream()
            .min(Comparator.comparingDouble(context.creature()::distanceToSqr));
        if (sapling.isEmpty()) {
            return false;
        }
        final ItemEntity item = sapling.orElseThrow();
        final BlockState planted = ((BlockItem) item.getItem().getItem()).getBlock().defaultBlockState();
        final Optional<BlockPos> destination = BlockPos.betweenClosedStream(
                item.blockPosition().offset(-4, -2, -4),
                item.blockPosition().offset(4, 2, 4)
            )
            .filter(position -> level.getBlockState(position).isAir())
            .filter(position -> planted.canSurvive(level, position))
            .min(Comparator.comparingDouble(item.blockPosition()::distSqr));
        if (destination.isEmpty()) {
            return false;
        }
        final BlockPos position = destination.orElseThrow();
        if (!level.setBlockAndUpdate(position, planted)) {
            return false;
        }
        item.getItem().shrink(1);
        if (item.getItem().isEmpty()) {
            item.discard();
        }
        level.sendParticles(
            ParticleTypes.HAPPY_VILLAGER,
            position.getX() + 0.5,
            position.getY() + 0.6,
            position.getZ() + 0.5,
            6,
            0.25,
            0.25,
            0.25,
            0.0
        );
        return true;
    }

    static boolean inspectShinyBlock(final AmbientActivityContext context) {
        return seekAndSignal(context,
            state -> AmbientActivityTags.matches(ActivityType.SHINY_CURIOSITY, state),
            ParticleTypes.ENCHANT,
            true);
    }

    static boolean seekNightPerch(final AmbientActivityContext context) {
        if (!AmbientActivityRules.isNight(context.level().getDefaultClockTime())) {
            return false;
        }
        return seekAndSignal(context, state -> state.is(BlockTags.LOGS)
            || AmbientActivityTags.matches(ActivityType.NIGHT_PERCH, state), ParticleTypes.ENCHANT, false);
    }

    static boolean seekPondRest(final AmbientActivityContext context) {
        return seekAndSignal(context,
            state -> AmbientActivityTags.matches(ActivityType.POND_REST, state),
            ParticleTypes.HAPPY_VILLAGER,
            false);
    }

    static boolean hauntBell(final AmbientActivityContext context) {
        final Optional<BlockPos> bell = nearestBlock(context, state -> state.getBlock() instanceof BellBlock);
        if (bell.isEmpty()) {
            return false;
        }
        final BlockPos position = bell.orElseThrow();
        moveTo(context.creature(), position, 1.0);
        if (context.creature().distanceToSqr(Vec3.atCenterOf(position)) <= 16.0) {
            ((BellBlock) context.level().getBlockState(position).getBlock())
                .attemptToRing(context.creature(), context.level(), position, Direction.UP);
            context.level().sendParticles(
                ParticleTypes.SOUL,
                position.getX() + 0.5,
                position.getY() + 1.0,
                position.getZ() + 0.5,
                5,
                0.35,
                0.35,
                0.35,
                0.01
            );
        }
        return true;
    }

    static boolean chargeStormRod(final AmbientActivityContext context) {
        if (!context.level().isRaining()) {
            return false;
        }
        return seekAndSignal(context, state -> AmbientActivityTags.matches(ActivityType.STORM_ROD, state),
            ParticleTypes.ENCHANTED_HIT, true);
    }

    static boolean studyArcana(final AmbientActivityContext context) {
        return seekAndSignal(context,
            state -> AmbientActivityTags.matches(ActivityType.ARCANE_STUDY, state),
            ParticleTypes.ENCHANT,
            false);
    }

    static boolean scavengeRottenFlesh(final AmbientActivityContext context) {
        final Optional<ItemEntity> food = context.level().getEntitiesOfClass(
                ItemEntity.class,
                context.creature().getBoundingBox().inflate(6.0),
                item -> item.isAlive() && item.getItem().is(net.minecraft.world.item.Items.ROTTEN_FLESH)
            ).stream()
            .min(Comparator.comparingDouble(context.creature()::distanceToSqr));
        if (food.isEmpty()) {
            return false;
        }
        final ItemEntity item = food.orElseThrow();
        if (context.creature().distanceToSqr(item) > 4.0) {
            context.creature().getNavigation().moveTo(item, 1.0);
            return false;
        }
        item.getItem().shrink(1);
        if (item.getItem().isEmpty()) {
            item.discard();
        }
        context.creature().heal(2.0F);
        context.level().playSound(
            null,
            context.creature().blockPosition(),
            SoundEvents.GENERIC_EAT.value(),
            SoundSource.HOSTILE,
            0.6F,
            0.8F
        );
        return true;
    }

    static boolean seekDaylightShelter(final AmbientActivityContext context) {
        final Mob creature = context.creature();
        final ServerLevel level = context.level();
        if (!AmbientActivityRules.isDay(level.getDefaultClockTime()) || !level.canSeeSky(creature.blockPosition())) {
            return false;
        }
        return BlockPos.betweenClosedStream(
                creature.blockPosition().offset(-AmbientActivityRules.SEARCH_RADIUS, -3, -AmbientActivityRules.SEARCH_RADIUS),
                creature.blockPosition().offset(AmbientActivityRules.SEARCH_RADIUS, 3, AmbientActivityRules.SEARCH_RADIUS)
            )
            .filter(position -> !level.canSeeSky(position))
            .map(position -> TacticalCombatRuntime.standableNear(level, position))
            .flatMap(Optional::stream)
            .distinct()
            .filter(position -> TacticalCombatRuntime.routeReaches(creature, position))
            .min(Comparator.comparingDouble(position -> position.distSqr(creature.blockPosition())))
            .map(position -> moveTo(creature, position, 1.2))
            .orElse(false);
    }

    static boolean keepSoulLanternVigil(final AmbientActivityContext context) {
        return seekAndSignal(context,
            state -> AmbientActivityTags.matches(ActivityType.SOUL_LANTERN_VIGIL, state),
            ParticleTypes.SOUL,
            false);
    }

    static boolean restAtHay(final AmbientActivityContext context) {
        final boolean found = seekAndSignal(context, state -> AmbientActivityTags.matches(ActivityType.HAY_REST, state),
            ParticleTypes.HAPPY_VILLAGER, false);
        if (found && nearestBlock(context, state -> AmbientActivityTags.matches(ActivityType.HAY_REST, state))
            .filter(position -> context.creature().distanceToSqr(Vec3.atCenterOf(position)) <= 9.0)
            .isPresent()) {
            context.creature().heal(1.0F);
        }
        return found;
    }

    static boolean patrolVillageBell(final AmbientActivityContext context) {
        if (!context.level().isVillage(context.creature().blockPosition())) {
            return false;
        }
        return seekAndSignal(context, state -> state.getBlock() instanceof BellBlock,
            ParticleTypes.HAPPY_VILLAGER, false);
    }

    static boolean restNearHome(final AmbientActivityContext context) {
        return seekAndSignal(context, state -> state.is(BlockTags.BEDS)
            || AmbientActivityTags.matches(ActivityType.FAMILIAR_HOME, state), ParticleTypes.HEART, false);
    }

    static boolean visitThornGarden(final AmbientActivityContext context) {
        return seekAndSignal(context,
            state -> AmbientActivityTags.matches(ActivityType.THORN_GARDEN, state),
            ParticleTypes.HAPPY_VILLAGER,
            false);
    }

    static boolean gazeAtReflection(final AmbientActivityContext context) {
        return seekAndSignal(context,
            state -> AmbientActivityTags.matches(ActivityType.MIRROR_GAZE, state),
            ParticleTypes.WITCH,
            false);
    }

    static boolean gazeAtMoon(final AmbientActivityContext context) {
        if (!AmbientActivityRules.isNight(context.level().getDefaultClockTime())
            || !context.level().canSeeSky(context.creature().blockPosition())) {
            return false;
        }
        context.creature().getLookControl().setLookAt(
            context.creature().getX(),
            context.creature().getY() + 12.0,
            context.creature().getZ() - 12.0
        );
        context.level().sendParticles(
            ParticleTypes.ENCHANT,
            context.creature().getX(),
            context.creature().getY() + context.creature().getBbHeight(),
            context.creature().getZ(),
            5,
            0.3,
            0.3,
            0.3,
            0.01
        );
        return true;
    }

    static Optional<BlockPos> activeHearth(final Mob creature, final ServerLevel level) {
        final long encoded = creature.getPersistentData().getLongOr(HEARTH_POSITION, Long.MIN_VALUE);
        if (encoded == Long.MIN_VALUE) {
            return Optional.empty();
        }
        final BlockPos position = BlockPos.of(encoded);
        final var hearth = level.getBlockEntity(position);
        final WeakReference<net.minecraft.world.level.block.entity.BlockEntity> expected = ACTIVE_HEARTHS.get(creature);
        return hearth != null
            && level.getBlockState(position).is(Blocks.CAMPFIRE)
            && AmbientActivityHearthData.get(level).owns(position, creature.getUUID(), level.getBlockState(position))
            && (expected == null || expected.get() == hearth)
                ? Optional.of(position)
                : Optional.empty();
    }

    static void clearExpiredHearth(final Mob creature, final ServerLevel level) {
        final long expires = creature.getPersistentData().getLongOr(HEARTH_EXPIRES, Long.MAX_VALUE);
        if (level.getGameTime() < expires) {
            return;
        }
        final long encoded = creature.getPersistentData().getLongOr(HEARTH_POSITION, Long.MIN_VALUE);
        if (encoded != Long.MIN_VALUE) {
            final BlockPos position = BlockPos.of(encoded);
            activeHearth(creature, level).ifPresent(owned -> level.removeBlock(owned, false));
            AmbientActivityHearthData.get(level).release(position, creature.getUUID());
        }
        ACTIVE_HEARTHS.remove(creature);
        creature.getPersistentData().remove(HEARTH_POSITION);
        creature.getPersistentData().remove(HEARTH_EXPIRES);
    }

    static String cooldownKey(final ActivityType type) {
        return COOLDOWN_PREFIX + type.name();
    }

    private static boolean tryActivity(
        final Mob creature,
        final ServerLevel level,
        final CreatureKind kind,
        final AmbientActivityProfile profile,
        final long gameTime
    ) {
        if (!AmbientActivityRules.shouldCheck(
            creature.tickCount,
            creature.getId(),
            profile.type().ordinal(),
            profile.checkIntervalTicks()
        ) || !AmbientActivityRules.passesRareRoll(
            gameTime,
            creature.getId(),
            profile.type(),
            profile.chanceDenominator()
        ) || !AmbientActivityRules.cooldownElapsed(
            gameTime,
            creature.getPersistentData().getLongOr(cooldownKey(profile.type()), 0L)
        )) {
            return false;
        }
        final boolean performed = AmbientActivityFactory.create(profile.type()).perform(
            new AmbientActivityContext(creature, level, kind, profile)
        );
        if (performed) {
            creature.getPersistentData().putLong(cooldownKey(profile.type()), gameTime + profile.cooldownTicks());
        }
        return performed;
    }

    private static Optional<BlockPos> hearthPosition(final Mob creature, final ServerLevel level) {
        final BlockPos origin = creature.blockPosition();
        return BlockPos.betweenClosedStream(origin.offset(-6, -2, -6), origin.offset(6, 2, 6))
            .filter(position -> position.distSqr(origin) >= 4.0)
            .filter(position -> level.getBlockState(position).isAir())
            .filter(position -> level.getBlockState(position.below()).isFaceSturdy(
                level,
                position.below(),
                Direction.UP
            ))
            .filter(position -> Blocks.CAMPFIRE.defaultBlockState().canSurvive(level, position))
            .filter(position -> BlockPos.betweenClosedStream(position.offset(-2, -1, -2), position.offset(2, 2, 2))
                .noneMatch(candidate -> level.getBlockState(candidate).is(BlockTags.LOGS)
                    || level.getBlockState(candidate).is(BlockTags.LEAVES)))
            .min(Comparator.comparingDouble(origin::distSqr));
    }

    private static boolean isColdAround(final Mob creature, final ServerLevel level) {
        if (level.getBlockState(creature.blockPosition().below()).is(Blocks.SNOW_BLOCK)
            || level.getBlockState(creature.blockPosition()).is(Blocks.SNOW)) {
            return true;
        }
        final String biome = level.registryAccess().lookupOrThrow(Registries.BIOME)
            .getKey(level.getBiome(creature.blockPosition()).value()).toString();
        return AmbientActivityRules.isColdBiomeId(biome);
    }

    private static boolean seekAndSignal(
        final AmbientActivityContext context,
        final Predicate<BlockState> predicate,
        final net.minecraft.core.particles.SimpleParticleType particle,
        final boolean chime
    ) {
        return nearestBlock(context, predicate).map(position -> {
            moveTo(context.creature(), position, 1.0);
            if (context.creature().distanceToSqr(Vec3.atCenterOf(position)) <= 16.0) {
                context.level().sendParticles(
                    particle,
                    position.getX() + 0.5,
                    position.getY() + 0.8,
                    position.getZ() + 0.5,
                    5,
                    0.25,
                    0.25,
                    0.25,
                    0.01
                );
                if (chime) {
                    context.level().playSound(
                        null,
                        position,
                        SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.NEUTRAL,
                        0.4F,
                        1.25F
                    );
                }
            }
            return true;
        }).orElse(false);
    }

    private static Optional<BlockPos> nearestBlock(
        final AmbientActivityContext context,
        final Predicate<BlockState> predicate
    ) {
        final BlockPos origin = context.creature().blockPosition();
        final int radius = AmbientActivityRules.SEARCH_RADIUS;
        return BlockPos.betweenClosedStream(origin.offset(-radius, -4, -radius), origin.offset(radius, 4, radius))
            .filter(position -> predicate.test(context.level().getBlockState(position)))
            .min(Comparator.comparingDouble(origin::distSqr));
    }

    private static int countBlocks(
        final ServerLevel level,
        final BlockPos origin,
        final int radius,
        final Predicate<BlockState> predicate
    ) {
        return (int) BlockPos.betweenClosedStream(
                origin.offset(-radius, -4, -radius),
                origin.offset(radius, 4, radius)
            )
            .filter(position -> predicate.test(level.getBlockState(position)))
            .limit(13)
            .count();
    }

    private static boolean moveTo(final Mob creature, final BlockPos position, final double speed) {
        creature.getNavigation().moveTo(
            position.getX() + 0.5,
            position.getY() + 0.5,
            position.getZ() + 0.5,
            speed
        );
        return true;
    }
}
