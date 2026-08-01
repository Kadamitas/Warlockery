package com.kadamitas.warlockery.dream;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.fabric.event.LivingDamageContext;
import com.kadamitas.warlockery.util.DataParsing;
import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.block.DreamWeaverBlock;
import com.kadamitas.warlockery.block.DreamWeaverMode;
import com.kadamitas.warlockery.brew.BrewMarkerKind;
import com.kadamitas.warlockery.brew.BrewMarkerState;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.ritual.ManifestationRuntime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpiritWorldRuntime {
    public static final ResourceKey<Level> SPIRIT_WORLD = ResourceKey.create(
        Registries.DIMENSION,
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "spirit_world")
    );
    public static final String BODY_DREAMER = "WarlockerySpiritWorldDreamer";
    private static final ResourceKey<WorldClock> SPIRIT_CLOCK = ResourceKey.create(
        Registries.WORLD_CLOCK,
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "spirit_world")
    );
    private static final String SLEEPING_BREW_ATTEMPT = "WarlockerySpiritWorldSleepingBrewAttempt";
    private static final int INFLUENCE_RADIUS = 8;
    private static final int BODY_CHECK_INTERVAL = 20;
    private static final int NIGHTMARE_CHECK_INTERVAL = 100;
    private static final int COTTON_INTERVAL = 200;
    private static final int COTTON_CAP = 12;
    private static final int SPIRIT_INTERVAL = 300;
    private static final int SPIRIT_CAP = 4;
    private static final Set<MinecraftServer> CONFIGURED_CLOCKS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ServerPlayer, ServerLevel> GAME_TEST_DESTINATIONS = new WeakHashMap<>();

    private SpiritWorldRuntime() {
    }

    public static EntryResult enterFromSleepingApple(final ServerPlayer player) {
        return enterInternal(player, true, false);
    }

    public static EntryResult enterFromSleepingBrew(final ServerPlayer player) {
        final long attempt = sleepingBrewMarker(player);
        if (attempt <= 0L || WarlockeryEntityData.get(player).getLongOr(SLEEPING_BREW_ATTEMPT, Long.MIN_VALUE) == attempt) {
            return new EntryResult(SpiritWorldRules.EntryDiagnostic.ALREADY_DREAMING, false);
        }
        WarlockeryEntityData.get(player).putLong(SLEEPING_BREW_ATTEMPT, attempt);
        return enterInternal(player, false, true);
    }

    public static EntryResult enter(final ServerPlayer player, final boolean forcedNightmare) {
        return enterInternal(player, forcedNightmare, false);
    }

    public static boolean wake(final ServerPlayer player, final SpiritWorldRules.WakeCause cause) {
        if (ManifestationRuntime.isActive(player)
            && !ManifestationRuntime.returnToSpiritWorld(player, ManifestationRuntime.ReturnCause.PORTAL)) {
            return false;
        }
        final Optional<SpiritWorldState.Session> stored = SpiritWorldState.read(player);
        if (stored.isEmpty()) {
            return false;
        }
        final SpiritWorldState.Session session = stored.orElseThrow();
        final ServerLevel source = player.level().getServer().getLevel(ResourceKey.create(
            Registries.DIMENSION,
            session.sourceDimension()
        ));
        if (source == null) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.spirit_world.wake.source_unavailable"));
            return false;
        }
        final ServerLevel dreamLevel = destination(player);
        final List<ItemStackWithSlot> exports = SpiritWorldRules.exports(
            SpiritWorldState.snapshot(player.getInventory()),
            stack -> stack.is(WarlockeryTags.Items.SPIRIT_WORLD_EXPORTS)
        );
        final boolean teleported = player.teleportTo(
            source,
            session.sourceX(),
            session.sourceY(),
            session.sourceZ(),
            Set.of(),
            session.sourceYaw(),
            session.sourcePitch(),
            true
        );
        if (!teleported) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.spirit_world.wake.blocked"));
            return false;
        }
        removeBody(source, session);
        removePortal(dreamLevel, session.portal());
        SpiritWorldState.restore(player.getInventory(), session.originalInventory(), session.selectedSlot());
        exports.stream().map(ItemStackWithSlot::stack).map(ItemStack::copy).forEach(stack -> addOrDrop(player, stack));
        SpiritWorldState.clear(player);
        SpiritManifestationState.clear(player);
        GAME_TEST_DESTINATIONS.remove(player);
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.NAUSEA);
        player.removeEffect(MobEffects.HUNGER);
        player.removeEffect(MobEffects.SLOWNESS);
        player.setHealth(Math.max(1.0F, player.getHealth()));
        player.setDeltaMovement(Vec3.ZERO);
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(Component.translatable("message.warlockery.spirit_world.wake." + cause.id()));
        return true;
    }

    public static boolean isDreaming(final net.minecraft.world.entity.player.Player player) {
        return SpiritWorldState.active(player);
    }

    public static boolean isNightmare(final ServerPlayer player) {
        return SpiritWorldState.read(player).map(SpiritWorldState.Session::nightmare).orElse(false);
    }

    public static boolean isDemonicNightmare(final ServerPlayer player) {
        return SpiritWorldState.read(player).map(SpiritWorldState.Session::demonicNightmare).orElse(false);
    }

    public static boolean isSpiritWorld(final Level level) {
        return level.dimension().equals(SPIRIT_WORLD);
    }

    public static boolean isSpiritWorld(final Level level, final Player player) {
        return isSpiritWorld(level)
            || player instanceof ServerPlayer serverPlayer
                && SpiritWorldState.active(serverPlayer)
                && GAME_TEST_DESTINATIONS.get(serverPlayer) == level;
    }

    static void useGameTestDestination(final ServerPlayer player) {
        if (!(player.level().getServer() instanceof GameTestServer)) {
            throw new IllegalStateException("GameTest Spirit World destinations require a GameTest server");
        }
        GAME_TEST_DESTINATIONS.put(player, player.level());
    }

    private static ServerLevel destination(final ServerPlayer player) {
        final ServerLevel spiritWorld = player.level().getServer().getLevel(SPIRIT_WORLD);
        return spiritWorld != null ? spiritWorld : GAME_TEST_DESTINATIONS.get(player);
    }

    public static boolean wakeIfBodyMissing(final ServerPlayer player) {
        return SpiritWorldState.active(player)
            && !bodyPresent(player)
            && wake(player, SpiritWorldRules.WakeCause.BODY_DESTROYED);
    }

    public static boolean isSleepingBody(final Entity entity) {
        return WarlockeryEntityData.get(entity).contains(BODY_DREAMER);
    }

    public static Optional<UUID> bodyDreamer(final Entity entity) {
        return DataParsing.uuid(WarlockeryEntityData.get(entity).getStringOr(BODY_DREAMER, ""));
    }

    private static EntryResult enterInternal(
        final ServerPlayer player,
        final boolean forcedNightmare,
        final boolean sleepingBrew
    ) {
        final ServerLevel source = player.level();
        final ServerLevel destination = destination(player);
        final SpiritWorldRules.EntryDiagnostic diagnostic = SpiritWorldRules.diagnoseEntry(
            true,
            SpiritWorldState.active(player),
            isSpiritWorld(source),
            destination != null
        );
        if (!diagnostic.ready()) {
            player.sendOverlayMessage(Component.translatable(
                "message.warlockery.spirit_world.entry." + diagnostic.id()
            ));
            return new EntryResult(diagnostic, false);
        }
        final DreamInfluence influence = dreamInfluence(source, player.blockPosition());
        final double chance = SpiritWorldRules.nightmareChance(forcedNightmare, influence.environment());
        final boolean demonicNightmare = SpiritWorldRules.entersNightmare(
            SpiritWorldRules.demonicNightmareChance(SpiritWorldRules.demonicNightmareEligible(
                sleepingBrew,
                influence.environment().nightmareWeaver(),
                influence.environment().flowingSpirit() > 0,
                influence.environment().demonHeart() > 0
            )),
            player.getRandom().nextDouble()
        );
        final boolean nightmare = demonicNightmare
            || SpiritWorldRules.entersNightmare(chance, player.getRandom().nextDouble());
        final BlockPos arrival = safeSurface(destination, player.blockPosition());
        final BlockPos portal = portalPosition(destination, arrival);
        final Optional<ArmorStand> body = spawnBody(source, player);
        if (body.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.warlockery.spirit_world.entry.body_failed"));
            return new EntryResult(SpiritWorldRules.EntryDiagnostic.DESTINATION_UNAVAILABLE, nightmare);
        }
        final List<ItemStackWithSlot> completeInventory = SpiritWorldState.snapshot(player.getInventory());
        final List<ItemStackWithSlot> carriedIntoDream = SpiritWorldRules.exports(
            completeInventory,
            stack -> stack.is(WarlockeryTags.Items.SPIRIT_WORLD_CARRY_IN)
        );
        final List<ItemStackWithSlot> originalInventory = completeInventory.stream()
            .filter(entry -> !entry.stack().is(WarlockeryTags.Items.SPIRIT_WORLD_CARRY_IN))
            .toList();
        final int selectedSlot = player.getInventory().getSelectedSlot();
        final BlockState replacedPortalState = destination.getBlockState(portal);
        destination.setBlockAndUpdate(portal, ModBlocks.ALL.get("spiritportal").get().defaultBlockState());
        final SpiritWorldState.Session session = new SpiritWorldState.Session(
            nightmare,
            demonicNightmare,
            source.dimension().identifier(),
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYRot(),
            player.getXRot(),
            body.orElseThrow().getUUID(),
            portal,
            originalInventory,
            selectedSlot
        );
        player.closeContainer();
        SpiritWorldState.begin(player, session);
        player.getInventory().clearContent();
        carriedIntoDream.stream()
            .filter(entry -> entry.isValidInContainer(player.getInventory().getContainerSize()))
            .forEach(entry -> player.getInventory().setItem(entry.slot(), entry.stack().copy()));
        player.getInventory().setChanged();
        final boolean teleported = player.teleportTo(
            destination,
            arrival.getX() + 0.5,
            arrival.getY(),
            arrival.getZ() + 0.5,
            Set.of(),
            player.getYRot(),
            player.getXRot(),
            true
        );
        if (!teleported) {
            SpiritWorldState.restore(player.getInventory(), completeInventory, selectedSlot);
            SpiritWorldState.clear(player);
            body.orElseThrow().discard();
            destination.setBlockAndUpdate(portal, replacedPortalState);
            player.sendOverlayMessage(Component.translatable("message.warlockery.spirit_world.entry.teleport_failed"));
            return new EntryResult(SpiritWorldRules.EntryDiagnostic.DESTINATION_UNAVAILABLE, nightmare);
        }
        player.setDeltaMovement(Vec3.ZERO);
        BrewMarkerState.remove(player, BrewMarkerKind.SLEEPING);
        if (nightmare) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, true, false, true));
            spawnNightmare(destination, player);
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
        }
        if (demonicNightmare) {
            player.sendSystemMessage(Component.translatable("message.warlockery.spirit_world.entry.demonic_nightmare"));
        }
        player.sendSystemMessage(Component.translatable(
            nightmare
                ? "message.warlockery.spirit_world.entry.nightmare"
                : "message.warlockery.spirit_world.entry.dream"
        ));
        return new EntryResult(SpiritWorldRules.EntryDiagnostic.READY, nightmare);
    }

    public static void tickLevel(final ServerLevel level) {
        if (isSpiritWorld(level)) {
            configureClock(level);
        }
    }

    public static void tickPlayer(final ServerPlayer player) {
        if (!SpiritWorldState.active(player)) {
            return;
        }
        if (ManifestationRuntime.isActive(player)) {
            if (player.tickCount % BODY_CHECK_INTERVAL == 0) {
                wakeIfBodyMissing(player);
            }
            return;
        }
        if (!isSpiritWorld(player.level(), player)) {
            wake(player, SpiritWorldRules.WakeCause.SESSION_RECOVERY);
            return;
        }
        if (player.tickCount % BODY_CHECK_INTERVAL == 0 && wakeIfBodyMissing(player)) {
            return;
        }
        if (isNightmare(player)) {
            if (player.tickCount % 40 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, true, false, true));
            }
            if (player.tickCount % NIGHTMARE_CHECK_INTERVAL == 0 && !nearbyNightmare(player)) {
                spawnNightmare(player.level(), player);
            }
        }
        if (isDemonicNightmare(player)) {
            if (player.tickCount % 40 == 0) {
                fieryRain(player.level(), player);
            }
            if (player.tickCount % 160 == 0) {
                spawnDemonicPursuer(player.level(), player);
            }
        }
        if (player.tickCount % NIGHTMARE_CHECK_INTERVAL == 0) {
            clearExcludedMobs(player.level(), player.blockPosition());
        }
        tickNaturalSources(player);
    }

    public static void handleDamage(final LivingDamageContext event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !SpiritWorldState.active(player)
            || !SpiritWorldRules.fatalDreamDamage(player.getHealth(), event.getAmount())) {
            return;
        }
        event.setAmount(0.0F);
        player.setHealth(Math.max(1.0F, player.getHealth()));
        player.level().getServer().execute(() -> {
            if (ManifestationRuntime.isActive(player)) {
                ManifestationRuntime.returnToSpiritWorld(player, ManifestationRuntime.ReturnCause.FATAL_DAMAGE);
            } else {
                wake(player, SpiritWorldRules.WakeCause.FATAL_DAMAGE);
            }
        });
    }

    private static long sleepingBrewMarker(final ServerPlayer player) {
        return BrewMarkerState.data(player, BrewMarkerKind.SLEEPING)
            .map(marker -> marker.getLongOr("expiration", 0L))
            .orElse(0L);
    }

    private static Optional<ArmorStand> spawnBody(final ServerLevel level, final ServerPlayer player) {
        final ArmorStand body = new ArmorStand(level, player.getX(), player.getY(), player.getZ());
        body.setNoBasePlate(true);
        body.setShowArms(true);
        body.setNoGravity(true);
        body.setCustomName(Component.translatable("entity.warlockery.sleeping_body", player.getDisplayName()));
        body.setCustomNameVisible(true);
        WarlockeryEntityData.get(body).putString(BODY_DREAMER, player.getStringUUID());
        return level.addFreshEntity(body) ? Optional.of(body) : Optional.empty();
    }

    private static BlockPos safeSurface(final ServerLevel level, final BlockPos origin) {
        final int x = Math.clamp(origin.getX(), -29_999_900, 29_999_900);
        final int z = Math.clamp(origin.getZ(), -29_999_900, 29_999_900);
        final int y = Math.clamp(
            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
            level.getMinY() + 2,
            level.getMaxY() - 3
        );
        return new BlockPos(x, y, z);
    }

    private static BlockPos portalPosition(final ServerLevel level, final BlockPos arrival) {
        return java.util.stream.IntStream.rangeClosed(2, 6)
            .mapToObj(offset -> safeSurface(level, arrival.offset(offset, 0, 0)))
            .filter(pos -> level.getBlockState(pos).canBeReplaced())
            .findFirst()
            .orElseGet(() -> arrival.offset(3, 0, 0));
    }

    private static DreamInfluence dreamInfluence(
        final ServerLevel level,
        final BlockPos center
    ) {
        boolean nightmareWeaver = false;
        int flowingSpirit = 0;
        int wispyCotton = 0;
        int demonHeart = 0;
        int fire = 0;
        for (final BlockPos pos : BlockPos.betweenClosed(
            center.offset(-INFLUENCE_RADIUS, -INFLUENCE_RADIUS, -INFLUENCE_RADIUS),
            center.offset(INFLUENCE_RADIUS, INFLUENCE_RADIUS, INFLUENCE_RADIUS)
        )) {
            if (!level.isLoaded(pos) || pos.distSqr(center) > INFLUENCE_RADIUS * INFLUENCE_RADIUS) {
                continue;
            }
            final BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DreamWeaverBlock) {
                if (state.getValue(DreamWeaverBlock.MODE) == DreamWeaverMode.NIGHTMARES) {
                    nightmareWeaver = true;
                }
                continue;
            }
            if (state.is(ModBlocks.ALL.get("spiritflowing").get())) {
                flowingSpirit++;
            }
            if (state.is(ModBlocks.ALL.get("somniancotton").get())) {
                wispyCotton++;
            }
            if (state.is(ModBlocks.ALL.get("demonheart").get())) {
                demonHeart++;
            }
            if (state.is(BlockTags.FIRE)) {
                fire++;
            }
        }
        return new DreamInfluence(new SpiritWorldRules.NightmareEnvironment(
            nightmareWeaver,
            flowingSpirit,
            wispyCotton,
            demonHeart,
            fire
        ));
    }

    private static boolean bodyPresent(final ServerPlayer player) {
        return SpiritWorldState.read(player).map(session -> {
            final ServerLevel source = player.level().getServer().getLevel(ResourceKey.create(
                Registries.DIMENSION,
                session.sourceDimension()
            ));
            if (source == null) {
                return true;
            }
            source.getChunkAt(BlockPos.containing(session.sourceX(), session.sourceY(), session.sourceZ()));
            final Entity body = source.getEntity(session.body());
            return body != null && body.isAlive() && isSleepingBody(body);
        }).orElse(false);
    }

    private static void removeBody(final ServerLevel source, final SpiritWorldState.Session session) {
        source.getChunkAt(BlockPos.containing(session.sourceX(), session.sourceY(), session.sourceZ()));
        final Entity body = source.getEntity(session.body());
        if (body != null && isSleepingBody(body)) {
            body.discard();
        }
    }

    private static void removePortal(final ServerLevel level, final BlockPos portal) {
        if (level == null) {
            return;
        }
        final Block portalBlock = ModBlocks.ALL.get("spiritportal").get();
        if (level.getBlockState(portal).is(portalBlock)) {
            level.destroyBlock(portal, false);
        }
    }

    private static boolean nearbyNightmare(final ServerPlayer player) {
        return !player.level().getEntitiesOfClass(
            Mob.class,
            new AABB(player.blockPosition()).inflate(48.0),
            mob -> mob.isAlive() && mob.typeHolder().is(WarlockeryTags.EntityTypes.NIGHTMARES)
        ).isEmpty();
    }

    private static void spawnNightmare(final ServerLevel level, final ServerPlayer player) {
        final Entity created = ModEntities.ALL.get("nightmare").get().create(level, EntitySpawnReason.EVENT);
        if (!(created instanceof Mob nightmare)) {
            return;
        }
        final BlockPos spawn = safeSurface(level, player.blockPosition().offset(5, 0, 2));
        nightmare.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        if (!level.noCollision(nightmare)) {
            nightmare.discard();
            return;
        }
        nightmare.setTarget(player);
        nightmare.setPersistenceRequired();
        level.addFreshEntity(nightmare);
    }

    private static void addOrDrop(final ServerPlayer player, final ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void configureClock(final ServerLevel level) {
        final MinecraftServer server = level.getServer();
        final Holder<WorldClock> clock = level.registryAccess()
            .lookupOrThrow(Registries.WORLD_CLOCK)
            .getOrThrow(SPIRIT_CLOCK);
        synchronized (CONFIGURED_CLOCKS) {
            if (CONFIGURED_CLOCKS.add(server)) {
                level.clockManager().setPaused(clock, true);
            }
        }
        final boolean anyNightmare = server.getPlayerList().getPlayers().stream()
            .filter(SpiritWorldRuntime::isDreaming)
            .anyMatch(SpiritWorldRuntime::isNightmare);
        final long desired = SpiritWorldRules.dreamClockTime(anyNightmare);
        if (level.clockManager().getTotalTicks(clock) != desired) {
            level.clockManager().setTotalTicks(clock, desired);
        }
    }

    private static void tickNaturalSources(final ServerPlayer player) {
        final int offset = player.getUUID().hashCode();
        final long gameTime = player.level().getGameTime();
        if (SpiritWorldRules.naturalSourceScheduled(gameTime, offset, COTTON_INTERVAL)) {
            growCotton(player.level(), player.blockPosition());
        }
        if (SpiritWorldRules.naturalSourceScheduled(gameTime, offset, SPIRIT_INTERVAL)) {
            spawnNaturalSpirit(player.level(), player);
        }
    }

    private static void growCotton(final ServerLevel level, final BlockPos center) {
        final Block cotton = ModBlocks.ALL.get("somniancotton").get();
        final int present = Math.toIntExact(BlockPos.betweenClosedStream(
            center.offset(-16, -8, -16),
            center.offset(16, 8, 16)
        ).filter(pos -> level.getBlockState(pos).is(cotton)).limit(COTTON_CAP).count());
        if (!SpiritWorldRules.belowNaturalSourceCap(present, COTTON_CAP)) {
            return;
        }
        java.util.stream.IntStream.range(0, Math.min(3, COTTON_CAP - present)).forEach(ignored -> {
            final int x = center.getX() + level.getRandom().nextInt(25) - 12;
            final int z = center.getZ() + level.getRandom().nextInt(25) - 12;
            final BlockPos pos = safeSurface(level, new BlockPos(x, center.getY(), z));
            final BlockPos ground = pos.below();
            if (level.getBlockState(pos).canBeReplaced()
                && level.getFluidState(pos).isEmpty()
                && level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
                level.setBlockAndUpdate(pos, cotton.defaultBlockState());
            }
        });
    }

    private static void spawnNaturalSpirit(final ServerLevel level, final ServerPlayer player) {
        final int present = level.getEntitiesOfClass(
            Mob.class,
            new AABB(player.blockPosition()).inflate(48.0),
            mob -> mob.getType() == ModEntities.ALL.get("spirit").get()
        ).size();
        if (!SpiritWorldRules.belowNaturalSourceCap(present, SPIRIT_CAP)
            || level.getRandom().nextDouble() >= 0.4) {
            return;
        }
        final Entity created = ModEntities.ALL.get("spirit").get().create(level, EntitySpawnReason.NATURAL);
        if (!(created instanceof Mob spirit)) {
            return;
        }
        final int x = player.blockPosition().getX() + level.getRandom().nextInt(33) - 16;
        final int z = player.blockPosition().getZ() + level.getRandom().nextInt(33) - 16;
        final BlockPos spawn = safeSurface(level, new BlockPos(x, player.blockPosition().getY(), z));
        spirit.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        if (level.noCollision(spirit)) {
            level.addFreshEntity(spirit);
        } else {
            spirit.discard();
        }
    }

    private static void clearExcludedMobs(final ServerLevel level, final BlockPos center) {
        level.getEntitiesOfClass(EnderMan.class, new AABB(center).inflate(64.0)).stream()
            .filter(enderman -> SpiritWorldRules.excludesFromSpiritWorld(
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(enderman.getType())
            ))
            .forEach(EnderMan::discard);
    }

    private static void fieryRain(final ServerLevel level, final ServerPlayer player) {
        level.sendParticles(
            ParticleTypes.FALLING_LAVA,
            player.getX(),
            player.getY() + 6.0,
            player.getZ(),
            36,
            5.0,
            2.0,
            5.0,
            0.08
        );
        level.sendParticles(
            ParticleTypes.FLAME,
            player.getX(),
            player.getY() + 2.0,
            player.getZ(),
            18,
            3.0,
            2.0,
            3.0,
            0.02
        );
        player.hurtServer(level, player.damageSources().onFire(), 1.0F);
    }

    private static void spawnDemonicPursuer(final ServerLevel level, final ServerPlayer player) {
        final int present = level.getEntitiesOfClass(
            Mob.class,
            new AABB(player.blockPosition()).inflate(48.0),
            mob -> mob.getType() == ModEntities.ALL.get("demon").get()
        ).size();
        if (!SpiritWorldRules.belowNaturalSourceCap(present, 3)) {
            return;
        }
        final Entity created = ModEntities.ALL.get("demon").get().create(level, EntitySpawnReason.EVENT);
        if (!(created instanceof Mob demon)) {
            return;
        }
        final BlockPos spawn = safeSurface(level, player.blockPosition().offset(6, 0, -4));
        demon.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        if (level.noCollision(demon)) {
            demon.setTarget(player);
            demon.setPersistenceRequired();
            level.addFreshEntity(demon);
        } else {
            demon.discard();
        }
    }

    public record EntryResult(SpiritWorldRules.EntryDiagnostic diagnostic, boolean nightmare) {
        public boolean entered() {
            return diagnostic.ready();
        }
    }

    private record DreamInfluence(SpiritWorldRules.NightmareEnvironment environment) {
    }
}
