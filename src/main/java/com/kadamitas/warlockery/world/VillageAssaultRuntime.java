package com.kadamitas.warlockery.world;

import com.kadamitas.warlockery.config.WarlockeryConfig;
import com.kadamitas.warlockery.entity.ArcaneCreature;
import com.kadamitas.warlockery.entity.ArcaneMob;
import com.kadamitas.warlockery.entity.GoblinEntity;
import com.kadamitas.warlockery.entity.LycanPackRuntime;
import com.kadamitas.warlockery.entity.VampireCourtEntity;
import com.kadamitas.warlockery.entity.VampireCourtRules;
import com.kadamitas.warlockery.entity.VampireCourtRuntime;
import com.kadamitas.warlockery.entity.WerewolfEntity;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.transformation.VampireProgressionRules;
import com.kadamitas.warlockery.transformation.WerewolfProgressionRules;
import com.kadamitas.warlockery.util.DataParsing;
import com.kadamitas.warlockery.world.VillageAssaultData.AssaultState;
import com.kadamitas.warlockery.world.VillageAssaultRules.AssaultKind;
import com.kadamitas.warlockery.world.VillageAssaultRules.DefenseReward;
import com.kadamitas.warlockery.world.VillageAssaultRules.RewardTheme;
import com.kadamitas.warlockery.world.VillageAssaultRules.SettlementKind;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public final class VillageAssaultRuntime {
    public static final String RAIDER_MARKER = "WarlockeryVillageAssaultRaider";
    public static final String ASSAULT_KIND = "WarlockeryVillageAssaultKind";
    public static final String SETTLEMENT_KIND = "WarlockeryVillageAssaultSettlement";
    public static final String ASSAULT_CENTER = "WarlockeryVillageAssaultCenter";
    public static final String ASSAULT_WAVE = "WarlockeryVillageAssaultWave";
    public static final String ASSAULT_LEADER = "WarlockeryVillageAssaultLeader";
    public static final String HOBGOBLIN_VARIANT = "WarlockeryHobgoblinAssaultVariant";
    public static final String SETTLEMENT_GUARD = "WarlockerySettlementGuard";
    public static final String BLOOD_DRAINED_UNTIL = "WarlockeryBloodDrainedUntil";
    public static final long BLOOD_DRAINED_TICKS = 72_000L;
    private static final String ESCAPED_FORM = "WarlockeryAssaultEscapeForm";
    private static final String ESCAPE_EXPIRES = "WarlockeryAssaultEscapeExpires";
    private static final String APPROACH_FORM = "WarlockeryAssaultApproachForm";
    static final String APPROACH_REVEAL_POSITION = "WarlockeryAssaultApproachRevealPosition";
    private static final double ASSAULT_SEARCH_RADIUS = 192.0;
    private static final double TARGET_SEARCH_RADIUS = 64.0;

    private VillageAssaultRuntime() {
    }

    public static void registerEvents() {
        LivingDamageEvent.BUS.addListener(VillageAssaultRuntime::handleDamage);
        LivingDeathEvent.BUS.addListener(VillageAssaultRuntime::handleDeath);
        PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(VillageAssaultRuntime::handleVillagerInteraction);
        EntityJoinLevelEvent.BUS.addListener(VillageAssaultRuntime::handleEntityJoin);
    }

    public static boolean isAssignedVampireObjective(
        final VampireCourtEntity member,
        final LivingEntity target
    ) {
        if (!(target instanceof AbstractVillager villager) || !(member.level() instanceof ServerLevel level)
            || target.level() != level || member.courtState().targetExpiresAt() <= level.getGameTime()
            || !VampireCourtRules.mayAttackAssaultObjective(
                member.creatureKind(), member.courtState().assaultRole(),
                member.getPersistentData().getBooleanOr(ASSAULT_LEADER, false),
                member.courtState().targetId().filter(target.getUUID()::equals).isPresent()
            )) {
            return false;
        }
        return matchingAssault(VillageAssaultData.get(level), member, AssaultKind.VAMPIRE)
            .filter(state -> !state.raidersRetreating())
            .filter(state -> eligibleObjectiveResident(state, villager))
            .filter(state -> VillageAssaultRules.isFreshObjectiveTarget(
                state.kind(), villager.getStringUUID(), Set.copyOf(state.objectiveVictims()),
                isBloodDrained(villager, level.getGameTime()),
                villager instanceof Villager human && WerewolfVillagerInfectionRuntime.isInfected(human)
            )).isPresent();
    }

    public static boolean protectsFromPreyDrive(final LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return false;
        }
        return VillageAssaultData.get(level).active().map(state -> {
            final boolean objectiveResident = target instanceof AbstractVillager resident
                && eligibleObjectiveResident(state, resident)
                && VillageAssaultRules.isFreshObjectiveTarget(
                    state.kind(),
                    target.getStringUUID(),
                    Set.copyOf(state.objectiveVictims()),
                    isBloodDrained(resident, level.getGameTime()),
                    target instanceof Villager villager && WerewolfVillagerInfectionRuntime.isInfected(villager)
                );
            final boolean withinObjectiveArea = new AABB(state.center())
                .inflate(TARGET_SEARCH_RADIUS, 20.0, TARGET_SEARCH_RADIUS)
                .contains(target.position());
            return protectsPreyTarget(
                state, target.getStringUUID(), objectiveResident, withinObjectiveArea
            );
        }).orElse(false);
    }

    static boolean protectsPreyTarget(
        final AssaultState state,
        final String entityId,
        final boolean objectiveResident,
        final boolean withinObjectiveArea
    ) {
        return state.participants().contains(entityId)
            || state.objectiveVictims().contains(entityId)
            || state.raiderIds().contains(entityId)
            || objectiveResident && withinObjectiveArea;
    }

    public static void tick(final ServerLevel level) {
        WerewolfVillagerInfectionRuntime.tick(level);
        final long gameTime = level.getGameTime();
        if (gameTime % VillageAssaultRules.CHECK_INTERVAL_TICKS != 0L) {
            return;
        }
        final VillageAssaultData data = VillageAssaultData.get(level);
        if (!WarlockeryConfig.villageAssaults()) {
            data.active().ifPresent(state -> {
                clearRaidMarkers(level, state.center());
                data.finish(gameTime, level.getRandom().nextLong(), WarlockeryConfig.villageAssaultFrequency());
            });
            return;
        }
        if (data.active().isPresent()) {
            tickActive(level, data, data.active().orElseThrow(), gameTime);
            return;
        }
        if (data.nextAttempt() == 0L) {
            data.scheduleNext(gameTime, level.getRandom().nextLong(), WarlockeryConfig.villageAssaultFrequency());
            return;
        }
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL || gameTime < data.nextAttempt()) {
            return;
        }
        final Optional<SettlementTarget> target = settlementTarget(level);
        data.scheduleNext(gameTime, level.getRandom().nextLong(), WarlockeryConfig.villageAssaultFrequency());
        if (target.isEmpty()) {
            return;
        }
        final boolean night = isNight(level);
        final boolean fullMoon = isFullMoon(level, target.orElseThrow().center());
        if (!VillageAssaultRules.canStart(
            level.getDifficulty(),
            target.orElseThrow().kind(),
            false,
            gameTime,
            gameTime,
            night,
            fullMoon
        )) {
            return;
        }
        final List<AssaultKind> eligible = VillageAssaultRules.eligibleKinds(
            target.orElseThrow().kind(), night, fullMoon
        );
        final AssaultKind kind = eligible.get(level.getRandom().nextInt(eligible.size()));
        data.begin(target.orElseThrow().center(), kind, target.orElseThrow().kind(), gameTime);
    }

    public static void markRaider(
        final Mob raider,
        final BlockPos center,
        final int wave,
        final AssaultKind kind,
        final SettlementKind settlement,
        final boolean hobgoblinVariant,
        final boolean leader
    ) {
        final var marker = raider.getPersistentData();
        marker.putBoolean(RAIDER_MARKER, true);
        marker.putString(ASSAULT_KIND, kind.serializedName());
        marker.putString(SETTLEMENT_KIND, settlement.serializedName());
        marker.putLong(ASSAULT_CENTER, center.asLong());
        marker.putInt(ASSAULT_WAVE, wave);
        marker.putBoolean(ASSAULT_LEADER, leader);
        marker.putBoolean(HOBGOBLIN_VARIANT, hobgoblinVariant);
        raider.setPersistenceRequired();
        if (raider instanceof ArcaneMob arcane) {
            arcane.setHobgoblinAssaultVariant(hobgoblinVariant);
        }
        // Body-level execution only: every strategic contract above this line is unchanged.
        if (raider instanceof GoblinEntity exactGoblin && kind == AssaultKind.GOBLIN) {
            exactGoblin.joinVillageAssault(center, wave, leader);
        }
        if (kind == AssaultKind.VAMPIRE && raider instanceof VampireCourtEntity courtMember) {
            if (leader) VampireCourtRuntime.markAssaultLeader(courtMember);
        }
        if (hobgoblinVariant) {
            raider.setCustomName(Component.translatable(kind == AssaultKind.VAMPIRE
                ? "entity.warlockery.vampire.hobgoblin_variant"
                : "entity.warlockery.werewolf.hobgoblin_variant"));
            raider.setCustomNameVisible(true);
        }
    }

    public static boolean isAssaultRaider(final Entity entity) {
        return entity.getPersistentData().getBooleanOr(RAIDER_MARKER, false);
    }

    public static boolean isHobgoblinVariant(final Entity entity) {
        return entity instanceof ArcaneMob arcane
            ? arcane.isHobgoblinAssaultVariant()
            : entity.getPersistentData().getBooleanOr(HOBGOBLIN_VARIANT, false);
    }

    public static void markSettlementGuard(final Mob guard, final SettlementKind settlement) {
        guard.addTag(SettlementFortificationRuntime.GUARD_TAG);
        guard.addTag(settlement == SettlementKind.HUMAN
            ? SettlementFortificationRuntime.HUMAN_GUARD_TAG
            : SettlementFortificationRuntime.HOBGOBLIN_GUARD_TAG);
        guard.setPersistenceRequired();
    }

    public static boolean isSilverGuard(final Entity entity) {
        if (entity == null) {
            return false;
        }
        if (VillageGuardRuntime.isSettlementGuard(entity)) {
            return true;
        }
        return entity instanceof ArcaneCreature creature && guardKindQualifies(creature.creatureKind());
    }

    public static boolean guardKindQualifies(final ArcaneCreature.CreatureKind kind) {
        return kind == ArcaneCreature.CreatureKind.FORGEWARDEN
            || kind == ArcaneCreature.CreatureKind.STONEBROKER;
    }

    public static boolean isBloodDrained(final AbstractVillager villager, final long gameTime) {
        return VillageAssaultRules.tradeLocked(gameTime, bloodDrainedUntil(villager));
    }

    public static long bloodDrainedUntil(final AbstractVillager villager) {
        return villager.getPersistentData().getLongOr(BLOOD_DRAINED_UNTIL, 0L);
    }

    public static boolean clearExpiredTradeLock(final AbstractVillager villager, final long gameTime) {
        final long expires = bloodDrainedUntil(villager);
        if (expires == 0L || VillageAssaultRules.tradeLocked(gameTime, expires)) {
            return false;
        }
        villager.getPersistentData().remove(BLOOD_DRAINED_UNTIL);
        return true;
    }

    static int spawnWave(
        final ServerLevel level,
        final BlockPos center,
        final int wave,
        final AssaultKind kind,
        final SettlementKind settlement,
        final int radius
    ) {
        final int size = VillageAssaultRules.waveSize(kind, wave);
        final int direction = level.getRandom().nextInt(8);
        final BlockPos entry = entryPoint(level, center, radius, direction);
        final int stepX = Integer.signum(entry.getZ() - center.getZ());
        final int stepZ = -Integer.signum(entry.getX() - center.getX());
        final java.util.LinkedHashSet<String> memberIds = new java.util.LinkedHashSet<>();
        int spawned = 0;
        for (int index = 0; index < size; index++) {
            final int offset = index - size / 2;
            final BlockPos position = surface(level, entry.offset(stepX * offset * 2, 0, stepZ * offset * 2));
            final Mob raider = kind == AssaultKind.GOBLIN
                ? spawnRaidMember(level, position, center, wave, kind, settlement, spawned == 0)
                : spawnApproachMember(level, position, center, wave, kind, settlement, spawned == 0);
            if (raider == null) {
                continue;
            }
            memberIds.add(raider.getStringUUID());
            spawned++;
        }
        registerRaidMembers(level, center, memberIds);
        return spawned;
    }

    static int spawnCompactWave(
        final ServerLevel level,
        final BlockPos center,
        final int wave,
        final AssaultKind kind,
        final SettlementKind settlement
    ) {
        final int size = VillageAssaultRules.waveSize(kind, wave);
        final java.util.LinkedHashSet<String> memberIds = new java.util.LinkedHashSet<>();
        int spawned = 0;
        for (int index = 0; index < size; index++) {
            final BlockPos position = center.offset(index % 3 - 1, 0, index / 3 % 3 - 1);
            final Mob raider = spawnRaidMember(
                level,
                position,
                center,
                wave,
                kind,
                settlement,
                spawned == 0
            );
            if (raider != null) {
                memberIds.add(raider.getStringUUID());
                spawned++;
            }
        }
        registerRaidMembers(level, center, memberIds);
        return spawned;
    }

    static Mob spawnApproachMember(
        final ServerLevel level,
        final BlockPos position,
        final BlockPos center,
        final int wave,
        final AssaultKind kind,
        final SettlementKind settlement,
        final boolean leader
    ) {
        final Mob approach = switch (kind) {
            case VAMPIRE -> EntityTypes.BAT.spawn(level, position, EntitySpawnReason.PATROL);
            case WEREWOLF -> EntityTypes.WOLF.spawn(level, position, EntitySpawnReason.PATROL);
            case GOBLIN -> null;
        };
        if (approach == null) {
            return null;
        }
        markRaider(
            approach,
            center,
            wave,
            kind,
            settlement,
            settlement == SettlementKind.HOBGOBLIN,
            leader
        );
        approach.getPersistentData().putBoolean(APPROACH_FORM, true);
        if (approach instanceof Bat bat) {
            bat.setResting(false);
        }
        registerRaidMembers(level, center, Set.of(approach.getStringUUID()));
        return approach;
    }

    static Mob spawnRaidMember(
        final ServerLevel level,
        final BlockPos position,
        final BlockPos center,
        final int wave,
        final AssaultKind kind,
        final SettlementKind settlement,
        final boolean leader
    ) {
        final Mob raider = spawnRaider(level, position, kind, leader);
        if (raider == null) {
            return null;
        }
        markRaider(
            raider,
            center,
            wave,
            kind,
            settlement,
            settlement == SettlementKind.HOBGOBLIN && kind != AssaultKind.GOBLIN,
            leader
        );
        applyNpcPowers(raider, kind, wave);
        registerRaidMembers(level, center, Set.of(raider.getStringUUID()));
        return raider;
    }

    static Optional<? extends Mob> transformForEscape(
        final ServerLevel level,
        final Mob raider,
        final boolean forced
    ) {
        final AssaultKind kind = assaultKind(raider).orElse(AssaultKind.GOBLIN);
        final boolean alreadyEscaped = raider.getPersistentData().getBooleanOr(ESCAPED_FORM, false);
        if (kind == AssaultKind.GOBLIN || alreadyEscaped || !forced && !VillageAssaultRules.shouldEscape(
            kind, raider.getHealth(), raider.getMaxHealth(), false
        )) {
            return Optional.empty();
        }
        final Mob escaped = switch (kind) {
            case VAMPIRE -> EntityTypes.BAT.spawn(level, raider.blockPosition(), EntitySpawnReason.CONVERSION);
            case WEREWOLF -> EntityTypes.WOLF.spawn(level, raider.blockPosition(), EntitySpawnReason.CONVERSION);
            case GOBLIN -> null;
        };
        if (escaped == null) {
            return Optional.empty();
        }
        escaped.snapTo(raider.getX(), raider.getY(), raider.getZ(), raider.getYRot(), raider.getXRot());
        copyRaidMarker(raider, escaped);
        escaped.getPersistentData().putBoolean(ESCAPED_FORM, true);
        escaped.getPersistentData().putLong(
            ESCAPE_EXPIRES,
            level.getGameTime() + VillageAssaultRules.ESCAPE_LIFETIME_TICKS
        );
        escaped.setPersistenceRequired();
        final Vec3 away = escapeDirection(raider);
        escaped.setDeltaMovement(away.x, kind == AssaultKind.VAMPIRE ? 0.45 : 0.15, away.z);
        if (isHobgoblinVariant(raider)) {
            escaped.setCustomName(raider.getCustomName());
            escaped.setCustomNameVisible(true);
            escaped.setGlowingTag(true);
        }
        replaceRaidMember(level, raider, escaped);
        raider.discard();
        return Optional.of(escaped);
    }

    public static void beginMemberRetreat(final ServerLevel level, final Mob raider) {
        if (isAssaultRaider(raider) && transformForEscape(level, raider, true).isEmpty()) {
            navigateAway(raider, BlockPos.of(raider.getPersistentData().getLongOr(ASSAULT_CENTER, 0L)));
        }
    }

    static Optional<Mob> transformApproachForm(
        final ServerLevel level,
        final Mob approach,
        final AssaultState state,
        final boolean forced
    ) {
        if (!approach.getPersistentData().getBooleanOr(APPROACH_FORM, false)
            || !isAssaultRaider(approach)) {
            return Optional.empty();
        }
        final int revealRadius = 1;
        final BlockPos revealPosition = approach.blockPosition();
        final long revealX = revealPosition.getX() - state.center().getX();
        final long revealZ = revealPosition.getZ() - state.center().getZ();
        final long revealDistance = revealX * revealX + revealZ * revealZ;
        if (!forced && revealDistance > (double) revealRadius * revealRadius) {
            return Optional.empty();
        }
        final AssaultKind kind = assaultKind(approach).orElse(AssaultKind.GOBLIN);
        if (kind == AssaultKind.GOBLIN) {
            return Optional.empty();
        }
        final boolean leader = approach.getPersistentData().getBooleanOr(ASSAULT_LEADER, false);
        final Mob revealed = spawnRaider(level, approach.blockPosition(), kind, leader);
        if (revealed == null) {
            return Optional.empty();
        }
        revealed.snapTo(
            approach.getX(), approach.getY(), approach.getZ(), approach.getYRot(), approach.getXRot()
        );
        final int wave = Math.clamp(
            approach.getPersistentData().getIntOr(ASSAULT_WAVE, 1),
            1,
            VillageAssaultRules.WAVE_COUNT
        );
        markRaider(
            revealed,
            state.center(),
            wave,
            kind,
            state.settlement(),
            isHobgoblinVariant(approach),
            leader
        );
        revealed.getPersistentData().putLong(APPROACH_REVEAL_POSITION, approach.blockPosition().asLong());
        applyNpcPowers(revealed, kind, wave);
        replaceRaidMember(level, approach, revealed);
        approach.discard();
        if (kind == AssaultKind.VAMPIRE) {
            reconcileVampireCourt(level, state.center(), wave);
        }
        if (approach instanceof Wolf) {
            closeApproachGatesIfUnused(level, state, approach);
        }
        return Optional.of(revealed);
    }

    static FeedResult feedOnVillager(
        final ServerLevel level,
        final Mob vampire,
        final AbstractVillager victim,
        final float proposedDamage
    ) {
        if (!(vampire instanceof VampireCourtEntity court)
            || !isAssignedVampireObjective(court, victim)
                && !assignVampireObjective(level, court, victim)) {
            return new FeedResult(0.0F, false, bloodDrainedUntil(victim), false);
        }
        return feedOnAssignedVillager(level, court, victim, proposedDamage);
    }

    private static FeedResult feedOnAssignedVillager(
        final ServerLevel level,
        final VampireCourtEntity vampire,
        final AbstractVillager victim,
        final float proposedDamage
    ) {
        if (!isAssignedVampireObjective(vampire, victim)) {
            return new FeedResult(0.0F, false, bloodDrainedUntil(victim), false);
        }
        final float damage = VillageAssaultRules.nonlethalFeedingDamage(victim.getHealth(), proposedDamage);
        if (damage <= 0.0F) {
            return new FeedResult(0.0F, false, bloodDrainedUntil(victim), false);
        }
        final VillageAssaultData data = VillageAssaultData.get(level);
        final Optional<AssaultState> active = matchingAssault(data, vampire, AssaultKind.VAMPIRE)
            .filter(state -> !state.raidersRetreating());
        if (active.isEmpty() || !eligibleObjectiveResident(active.orElseThrow(), victim)) {
            return new FeedResult(0.0F, false, bloodDrainedUntil(victim), false);
        }
        final AssaultState before = active.orElseThrow();
        final AssaultState recorded = before.recordObjectiveVictim(victim.getStringUUID());
        if (recorded == before) {
            return new FeedResult(damage, false, bloodDrainedUntil(victim), before.raidersRetreating());
        }
        final long expires = level.getGameTime() + BLOOD_DRAINED_TICKS;
        victim.getPersistentData().putLong(BLOOD_DRAINED_UNTIL, expires);
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 1));
        victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600, 0));
        vampire.heal(6.0F);
        if (vampire instanceof VampireCourtEntity leader) {
            VampireCourtRuntime.afterAssaultFeed(leader, level.getGameTime());
        }
        final AssaultState updated = recorded.objectiveSatisfied()
            ? recorded.beginRaiderRetreat(level.getGameTime())
            : recorded;
        data.update(updated);
        return new FeedResult(damage, true, expires, updated.raidersRetreating());
    }

    static boolean infectVillagerFromRaider(
        final ServerLevel level,
        final Mob werewolf,
        final AbstractVillager victim
    ) {
        if (!VillageAssaultRules.canInfectVillager(
            AssaultKind.WEREWOLF,
            victim.getType() == EntityTypes.VILLAGER,
            false
        ) || matchingAssault(VillageAssaultData.get(level), werewolf, AssaultKind.WEREWOLF).isEmpty()) {
            return false;
        }
        if (!(victim instanceof Villager human) || !WerewolfVillagerInfectionRuntime.markInfected(human)) {
            return false;
        }
        return recordWerewolfObjective(level, werewolf, victim);
    }

    static boolean recordWerewolfObjective(
        final ServerLevel level,
        final Mob werewolf,
        final AbstractVillager victim
    ) {
        final VillageAssaultData data = VillageAssaultData.get(level);
        final Optional<AssaultState> active = matchingAssault(data, werewolf, AssaultKind.WEREWOLF);
        if (active.isEmpty() || !eligibleObjectiveResident(active.orElseThrow(), victim)) {
            return false;
        }
        final AssaultState before = active.orElseThrow();
        final AssaultState recorded = before.recordObjectiveVictim(victim.getStringUUID());
        if (recorded == before) {
            return false;
        }
        data.update(recorded.objectiveSatisfied()
            ? recorded.beginRaiderRetreat(level.getGameTime())
            : recorded);
        return true;
    }

    static void completeDefense(
        final ServerLevel level,
        final VillageAssaultData data,
        final AssaultState state,
        final long gameTime
    ) {
        applyDefenseReward(level, state);
        clearRaidMarkers(level, state.center());
        data.finish(gameTime, level.getRandom().nextLong(), WarlockeryConfig.villageAssaultFrequency());
    }

    static void applyDefenseReward(final ServerLevel level, final AssaultState state) {
        final DefenseReward reward = VillageAssaultRules.reward(state.kind(), state.settlement(), state.wave());
        if (!reward.complete() || state.raidersRetreating()) {
            return;
        }
        state.participants().stream()
            .map(DataParsing::uuid)
            .flatMap(Optional::stream)
            .map(level.getServer().getPlayerList()::getPlayer)
            .filter(java.util.Objects::nonNull)
            .forEach(player -> applyReward(player, level, reward, state));
        level.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(state.center()).inflate(72.0, 24.0, 72.0),
            VillageAssaultRuntime::isSilverGuard
        ).forEach(guard -> guard.heal(guard.getMaxHealth()));
    }

    private static void tickActive(
        final ServerLevel level,
        final VillageAssaultData data,
        final AssaultState initialState,
        final long gameTime
    ) {
        final AssaultState state = initialState;
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL
            || gameTime >= state.expiresAt()
            || !settlementPresent(level, state)) {
            clearRaidMarkers(level, state.center());
            data.finish(gameTime, level.getRandom().nextLong(), WarlockeryConfig.villageAssaultFrequency());
            return;
        }
        final List<Entity> raiders = activeRaiders(level, state.center());
        final AssaultState reconciled = data.active().orElse(state).addRaiders(
            raiders.stream().map(Entity::getStringUUID).collect(java.util.stream.Collectors.toSet())
        );
        if (reconciled != data.active().orElse(state)) {
            data.update(reconciled);
        }
        for (final Entity entity : List.copyOf(raiders)) {
            tickRaider(level, reconciled, entity, gameTime);
        }
        reconcileApproachGates(level, reconciled);
        final AssaultState current = data.active().orElse(state);
        if (current.raidersRetreating()) {
            if (current.raiderIds().isEmpty() || gameTime >= current.nextWaveTime()) {
                clearRaidMarkers(level, current.center());
                data.finish(gameTime, level.getRandom().nextLong(), WarlockeryConfig.villageAssaultFrequency());
            }
            return;
        }
        if (current.awaitingClear()) {
            if (current.raiderIds().isEmpty()) {
                if (current.wave() >= VillageAssaultRules.WAVE_COUNT) {
                    completeDefense(level, data, current, gameTime);
                } else {
                    data.update(current.waveCleared(gameTime));
                }
            }
            return;
        }
        if (gameTime < current.nextWaveTime()) {
            return;
        }
        if (current.wave() >= VillageAssaultRules.WAVE_COUNT) {
            completeDefense(level, data, current, gameTime);
            return;
        }
        final int wave = current.wave() + 1;
        final int spawnRadius = Math.max(
            VillageAssaultRules.SPAWN_RADIUS,
            SettlementFortificationRuntime.approachRadius(
                level,
                current.center(),
                fortificationKind(current.settlement())
            ) + 8
        );
        final int spawned = spawnWave(
            level,
            current.center(),
            wave,
            current.kind(),
            current.settlement(),
            spawnRadius
        );
        final AssaultState postSpawn = data.active().orElse(current);
        data.update(spawned == 0 ? postSpawn.retryAt(gameTime) : postSpawn.waveSpawned(wave));
    }

    private static void tickRaider(
        final ServerLevel level,
        final AssaultState state,
        final Entity entity,
        final long gameTime
    ) {
        if (entity.getPersistentData().getBooleanOr(APPROACH_FORM, false)) {
            if (entity instanceof Mob approach) {
                if (state.raidersRetreating()) {
                    approach.getPersistentData().remove(APPROACH_FORM);
                    approach.getPersistentData().putBoolean(ESCAPED_FORM, true);
                    approach.getPersistentData().putLong(
                        ESCAPE_EXPIRES,
                        gameTime + VillageAssaultRules.ESCAPE_LIFETIME_TICKS
                    );
                    approach.setPersistenceRequired();
                    tickEscapeForm(level, state, approach);
                } else {
                    tickApproachForm(level, state, approach);
                }
            }
            return;
        }
        if (entity.getPersistentData().getBooleanOr(ESCAPED_FORM, false)) {
            if (gameTime >= entity.getPersistentData().getLongOr(ESCAPE_EXPIRES, 0L)) {
                removeRaidMember(level, entity);
                entity.discard();
            } else if (entity instanceof Mob escaped) {
                tickEscapeForm(level, state, escaped);
            }
            return;
        }
        if (!(entity instanceof Mob raider)) {
            return;
        }
        if (state.raidersRetreating()) {
            if (transformForEscape(level, raider, true).isEmpty()) {
                navigateAway(raider, state.center());
            }
            return;
        }
        if (transformForEscape(level, raider, false).isPresent()) {
            return;
        }
        coordinate(raider, level, state);
        applyNpcPowers(raider, state.kind(), Math.max(1, state.wave()));
    }

    static void tickApproachForm(
        final ServerLevel level,
        final AssaultState state,
        final Mob approach
    ) {
        if (transformApproachForm(level, approach, state, false).isPresent()) {
            return;
        }
        final double flightY = SettlementFortificationRuntime.registeredLayout(level, state.center())
            .map(layout -> layout.deckY() + 3.0)
            .orElse(state.center().getY() + 5.0);
        final Vec3 flightTarget = new Vec3(
            state.center().getX() + 0.5,
            flightY,
            state.center().getZ() + 0.5
        );
        if (approach instanceof Bat bat) {
            bat.setResting(false);
            final Vec3 stagedTarget = bat.getY() < flightY - 0.25
                ? new Vec3(bat.getX(), flightY, bat.getZ())
                : flightTarget;
            final Vec3 direction = stagedTarget.subtract(bat.position());
            if (direction.lengthSqr() > 1.0E-4) {
                bat.setDeltaMovement(direction.scale(Math.min(0.42 / direction.length(), 1.0)));
            }
            return;
        }
        SettlementFortificationRuntime.setGatesOpen(level, state.center(), true);
        final BlockPos groundTarget = SettlementFortificationRuntime.nearestGate(
            level,
            state.center(),
            approach.blockPosition()
        ).filter(gate -> horizontalDistanceSqr(approach, gate) > 6.25)
            .orElse(state.center());
        approach.getNavigation().moveTo(
            groundTarget.getX() + 0.5,
            groundTarget.getY(),
            groundTarget.getZ() + 0.5,
            1.25
        );
        final Vec3 groundDirection = Vec3.atBottomCenterOf(groundTarget)
            .subtract(approach.position())
            .multiply(1.0, 0.0, 1.0);
        if (groundDirection.lengthSqr() > 1.0E-4) {
            final Vec3 steering = groundDirection.normalize().scale(0.16);
            approach.setDeltaMovement(steering.x, approach.getDeltaMovement().y, steering.z);
        }
    }

    private static void tickEscapeForm(
        final ServerLevel level,
        final AssaultState state,
        final Mob escaped
    ) {
        if (escaped instanceof Bat bat) {
            bat.setResting(false);
            final Vec3 away = escapeDirection(bat);
            bat.setDeltaMovement(away.x, 0.35, away.z);
            return;
        }
        SettlementFortificationRuntime.setGatesOpen(level, state.center(), true);
        final Optional<BlockPos> gate = SettlementFortificationRuntime.nearestGate(
            level,
            state.center(),
            escaped.blockPosition()
        );
        final BlockPos destination = gate
            .filter(position -> horizontalDistanceSqr(escaped, position) > 6.25)
            .orElseGet(() -> outsideEscapePoint(level, state, escaped));
        escaped.getNavigation().moveTo(
            destination.getX() + 0.5,
            destination.getY(),
            destination.getZ() + 0.5,
            1.35
        );
    }

    private static BlockPos outsideEscapePoint(
        final ServerLevel level,
        final AssaultState state,
        final Entity escaped
    ) {
        final int radius = SettlementFortificationRuntime.approachRadius(
            level,
            state.center(),
            fortificationKind(state.settlement())
        ) + 10;
        final Vec3 away = escapeDirection(escaped).normalize();
        return surface(level, state.center().offset(
            (int) Math.round(away.x * radius),
            0,
            (int) Math.round(away.z * radius)
        ));
    }

    private static void coordinate(
        final Mob raider,
        final ServerLevel level,
        final AssaultState state
    ) {
        if (state.kind() == AssaultKind.VAMPIRE && raider instanceof VampireCourtEntity courtMember) {
            reconcileVampireCourt(level, state.center(), state.wave());
            if (courtMember.courtState().intent() == VampireCourtRules.Intent.SEEK_SHELTER) {
                return;
            }
            if (!mayUseVampireObjective(
                raider.getPersistentData().getBooleanOr(ASSAULT_LEADER, false),
                courtMember.creatureKind() == ArcaneCreature.CreatureKind.VAMPIRE
            )) {
                if (raider.getTarget() instanceof AbstractVillager) raider.setTarget(null);
                return;
            }
        }
        final Optional<AbstractVillager> victim = selectObjectiveResident(level, raider, state);
        if (state.kind() == AssaultKind.WEREWOLF
            && raider instanceof WerewolfEntity werewolf
            && LycanPackRuntime.exactWerewolf(werewolf)) {
            // F04 owns the pack-pressure contract and it is typed on the human Villager; a
            // non-Villager objective simply supplies no pressure target rather than widening F04.
            LycanPackRuntime.coordinateAssaultPressure(
                level, werewolf,
                victim.filter(Villager.class::isInstance).map(Villager.class::cast).orElse(null),
                state.center()
            );
            return;
        }
        if (victim.isPresent()) {
            final AbstractVillager assigned = victim.orElseThrow();
            assignVampireObjective(level, raider, assigned);
            return;
        }
        if (raider.getTarget() instanceof AbstractVillager) {
            raider.setTarget(null);
        }
        if (raider.distanceToSqr(Vec3.atCenterOf(state.center())) > 16.0) {
            raider.getNavigation().moveTo(
                state.center().getX() + 0.5,
                state.center().getY(),
                state.center().getZ() + 0.5,
                1.05
            );
        }
    }

    public static boolean assignVampireObjective(
        final ServerLevel level,
        final Mob raider,
        final AbstractVillager objective
    ) {
        final Optional<AssaultState> active = matchingAssault(
            VillageAssaultData.get(level), raider, AssaultKind.VAMPIRE
        );
        if (!(raider instanceof VampireCourtEntity courtMember)
            || active.isEmpty() || !eligibleObjectiveResident(active.orElseThrow(), objective)
            || !mayUseVampireObjective(
                raider.getPersistentData().getBooleanOr(ASSAULT_LEADER, false),
                courtMember.creatureKind() == ArcaneCreature.CreatureKind.VAMPIRE
            )) {
            return false;
        }
        VampireCourtRuntime.acceptAssaultObjective(courtMember, objective, level.getGameTime());
        return true;
    }

    static Optional<AbstractVillager> selectObjectiveResident(
        final ServerLevel level,
        final Mob raider,
        final AssaultState state
    ) {
        if (state.kind() == AssaultKind.VAMPIRE && isAssaultRaider(raider) && !mayUseVampireObjective(
            raider.getPersistentData().getBooleanOr(ASSAULT_LEADER, false),
            raider instanceof VampireCourtEntity court
                && court.creatureKind() == ArcaneCreature.CreatureKind.VAMPIRE
        )) {
            return Optional.empty();
        }
        return switch (state.settlement()) {
            case HUMAN -> level.getEntitiesOfClass(
                Villager.class,
                new AABB(state.center()).inflate(TARGET_SEARCH_RADIUS, 20.0, TARGET_SEARCH_RADIUS),
                candidate -> candidate.isAlive()
                    && candidate.getType() == EntityTypes.VILLAGER
                    && VillageAssaultRules.isFreshObjectiveTarget(
                        state.kind(),
                        candidate.getStringUUID(),
                        Set.copyOf(state.objectiveVictims()),
                        isBloodDrained(candidate, level.getGameTime()),
                        WerewolfVillagerInfectionRuntime.isInfected(candidate)
                    )
            ).stream()
                .map(AbstractVillager.class::cast)
                .min(Comparator.comparingDouble(raider::distanceToSqr));
            case HOBGOBLIN -> level.getEntitiesOfClass(
                AbstractVillager.class,
                new AABB(state.center()).inflate(TARGET_SEARCH_RADIUS, 20.0, TARGET_SEARCH_RADIUS),
                candidate -> candidate.isAlive()
                    && candidate instanceof ArcaneCreature resident
                    && resident.creatureKind() == ArcaneCreature.CreatureKind.HOBGOBLIN
                    && !isAssaultRaider(candidate)
                    && VillageAssaultRules.isFreshObjectiveTarget(
                        state.kind(),
                        candidate.getStringUUID(),
                        Set.copyOf(state.objectiveVictims()),
                        isBloodDrained(candidate, level.getGameTime()),
                        false
                    )
            ).stream()
                .map(AbstractVillager.class::cast)
                .min(Comparator.comparingDouble(raider::distanceToSqr));
        };
    }

    private static void applyNpcPowers(
        final Mob raider,
        final AssaultKind kind,
        final int wave
    ) {
        final var profile = VillageAssaultRules.npcPowers(kind, wave);
        if (kind == AssaultKind.VAMPIRE) {
            final Set<VampireProgressionRules.Ability> powers = profile.vampireAbilities();
            final boolean leader = raider.getPersistentData().getBooleanOr(ASSAULT_LEADER, false)
                && raider instanceof VampireCourtEntity court
                && court.creatureKind() == ArcaneCreature.CreatureKind.VAMPIRE;
            if (!leader) {
                if (powers.contains(VampireProgressionRules.Ability.SPEED)) {
                    raider.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, false, false));
                }
                if (powers.contains(VampireProgressionRules.Ability.SUPERNATURAL_RESILIENCE)) {
                    raider.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, false, false));
                }
                return;
            }
            if (powers.contains(VampireProgressionRules.Ability.POISON_AND_DISEASE_IMMUNITY)) {
                raider.removeEffect(MobEffects.POISON);
                raider.removeEffect(MobEffects.NAUSEA);
                raider.removeEffect(MobEffects.HUNGER);
            }
            if (powers.contains(VampireProgressionRules.Ability.SPEED)) {
                raider.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, false, false));
            }
            if (powers.contains(VampireProgressionRules.Ability.SUPERNATURAL_RESILIENCE)) {
                raider.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, false, false));
            }
            if (powers.contains(VampireProgressionRules.Ability.RESIST_SUN)) {
                raider.clearFire();
            }
            if (powers.contains(VampireProgressionRules.Ability.NIGHT_VISION)) {
                raider.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, false, false));
            }
            if (powers.contains(VampireProgressionRules.Ability.TRANSFIX)
                && raider.getTarget() != null && raider.distanceToSqr(raider.getTarget()) < 36.0) {
                raider.getTarget().addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 45, 1));
            }
            if (powers.contains(VampireProgressionRules.Ability.MESMERIZE)
                && raider.getTarget() != null && raider.distanceToSqr(raider.getTarget()) < 64.0) {
                raider.getTarget().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
            }
            if (powers.contains(VampireProgressionRules.Ability.TELEPORT)
                && raider.getTarget() != null
                && raider.tickCount % 100 == Math.floorMod(raider.getId(), 100)
                && raider.distanceToSqr(raider.getTarget()) > 36.0) {
                final Vec3 target = raider.getTarget().position();
                raider.randomTeleport(
                    target.x + raider.getRandom().nextIntBetweenInclusive(-3, 3),
                    target.y,
                    target.z + raider.getRandom().nextIntBetweenInclusive(-3, 3),
                    true
                );
            }
            if (powers.contains(VampireProgressionRules.Ability.BAT_SWARM)
                && raider.getTarget() != null && raider.distanceToSqr(raider.getTarget()) < 100.0) {
                raider.getTarget().addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
            }
            return;
        }
        if (kind != AssaultKind.WEREWOLF) {
            return;
        }
        final Set<WerewolfProgressionRules.Ability> powers = profile.werewolfAbilities();
        if (powers.contains(WerewolfProgressionRules.Ability.POISON_AND_DISEASE_IMMUNITY)) {
            raider.removeEffect(MobEffects.POISON);
            raider.removeEffect(MobEffects.NAUSEA);
            raider.removeEffect(MobEffects.HUNGER);
        }
        if (powers.contains(WerewolfProgressionRules.Ability.SUPERNATURAL_RESILIENCE)) {
            raider.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, false, false));
        }
        if (powers.contains(WerewolfProgressionRules.Ability.FEAST)) {
            raider.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false));
        }
        if (powers.contains(WerewolfProgressionRules.Ability.CHARGE_ATTACK)) {
            raider.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, false, false));
        }
        if (powers.contains(WerewolfProgressionRules.Ability.PACK_HOWL)) {
            raider.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, false, false));
        }
        if (powers.contains(WerewolfProgressionRules.Ability.ARMOR_RENDING)
            && raider.getTarget() != null && raider.distanceToSqr(raider.getTarget()) < 16.0) {
            raider.getTarget().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
        }
        if (powers.contains(WerewolfProgressionRules.Ability.STUN_HOWL)
            && raider.getTarget() != null
            && raider.tickCount % 100 == Math.floorMod(raider.getId(), 100)) {
            raider.getTarget().addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 3));
        }
    }

    public static void handleDamage(final LivingDamageEvent event) {
        if (event.getAmount() <= 0.0F) {
            return;
        }
        recordParticipantContribution(event);
        if (!(event.getSource().getEntity() instanceof Mob raider)
            || !isAssaultRaider(raider)) {
            return;
        }
        if (raider.level() instanceof ServerLevel assaultLevel
            && VillageAssaultData.get(assaultLevel).active()
                .filter(state -> state.center().asLong() == raider.getPersistentData().getLongOr(
                    ASSAULT_CENTER,
                    Long.MIN_VALUE
                ))
                .filter(AssaultState::raidersRetreating)
                .isPresent()) {
            event.setAmount(0.0F);
            return;
        }
        applySupernaturalAttackPower(event, raider);
        if (!(event.getEntity() instanceof AbstractVillager victim)
            || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        final AssaultKind kind = assaultKind(raider).orElse(AssaultKind.GOBLIN);
        if (kind == AssaultKind.VAMPIRE) {
            event.setAmount(raider instanceof VampireCourtEntity court
                ? feedOnAssignedVillager(level, court, victim, event.getAmount()).damage()
                : 0.0F);
        } else if (kind == AssaultKind.WEREWOLF
            && victim.getType() == EntityTypes.VILLAGER
            && level.getRandom().nextInt(8) == 0) {
            infectVillagerFromRaider(level, raider, victim);
        }
    }

    private static void applySupernaturalAttackPower(
        final LivingDamageEvent event,
        final Mob raider
    ) {
        final AssaultKind kind = assaultKind(raider).orElse(AssaultKind.GOBLIN);
        final int wave = Math.clamp(raider.getPersistentData().getIntOr(ASSAULT_WAVE, 1), 1,
            VillageAssaultRules.WAVE_COUNT);
        final var profile = VillageAssaultRules.npcPowers(kind, wave);
        if (kind == AssaultKind.VAMPIRE
            && raider.getPersistentData().getBooleanOr(ASSAULT_LEADER, false)
            && raider instanceof VampireCourtEntity court
            && court.creatureKind() == ArcaneCreature.CreatureKind.VAMPIRE
            && profile.vampireAbilities().contains(VampireProgressionRules.Ability.KNOCKBACK)) {
            final Vec3 push = event.getEntity().position().subtract(raider.position()).multiply(1.0, 0.0, 1.0);
            if (push.lengthSqr() > 1.0E-4) {
                final Vec3 normalized = push.normalize().scale(0.65);
                event.getEntity().push(normalized.x, 0.2, normalized.z);
            }
        }
        if (kind == AssaultKind.WEREWOLF
            && profile.werewolfAbilities().contains(WerewolfProgressionRules.Ability.ARMOR_RENDING)) {
            event.setAmount(event.getAmount() * 1.25F);
        }
        if (kind == AssaultKind.WEREWOLF
            && profile.werewolfAbilities().contains(WerewolfProgressionRules.Ability.CHARGE_ATTACK)
            && raider.getDeltaMovement().horizontalDistanceSqr() > 0.12) {
            event.setAmount(event.getAmount() + 2.0F);
        }
    }

    public static void handleDeath(final LivingDeathEvent event) {
        if (isAssaultRaider(event.getEntity())
            && event.getEntity().level() instanceof ServerLevel assaultLevel) {
            removeRaidMember(assaultLevel, event.getEntity());
            if (event.getEntity() instanceof Wolf
                && event.getEntity().getPersistentData().getBooleanOr(APPROACH_FORM, false)) {
                VillageAssaultData.get(assaultLevel).active()
                    .filter(state -> state.center().asLong() == event.getEntity().getPersistentData().getLongOr(
                        ASSAULT_CENTER,
                        Long.MIN_VALUE
                    ))
                    .ifPresent(state -> closeApproachGatesIfUnused(assaultLevel, state, event.getEntity()));
            }
        }
        if (!(event.getEntity() instanceof AbstractVillager victim)
            || !(event.getSource().getEntity() instanceof Mob raider)
            || assaultKind(raider).orElse(AssaultKind.GOBLIN) != AssaultKind.WEREWOLF
            || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        recordWerewolfObjective(level, raider, victim);
    }

    static boolean handleVillagerInteraction(final PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof AbstractVillager villager)
            || !(event.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        final long gameTime = player.level().getGameTime();
        if (!shouldDenyVillagerInteraction(villager, gameTime)) {
            clearExpiredTradeLock(villager, gameTime);
            return false;
        }
        player.sendOverlayMessage(Component.translatable("message.warlockery.villager.blood_drained")
            .withStyle(ChatFormatting.DARK_RED));
        event.setCancellationResult(InteractionResult.FAIL);
        return true;
    }

    static boolean shouldDenyVillagerInteraction(final AbstractVillager villager, final long gameTime) {
        return isBloodDrained(villager, gameTime);
    }

    private static boolean handleEntityJoin(final EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isAssaultRaider(event.getEntity())) {
            return false;
        }
        final Entity entity = event.getEntity();
        final long center = entity.getPersistentData().getLongOr(ASSAULT_CENTER, Long.MIN_VALUE);
        final VillageAssaultData data = VillageAssaultData.get(level);
        final Optional<AssaultState> active = data.active()
            .filter(state -> state.center().asLong() == center);
        if (active.isPresent()) {
            final AssaultState reconciled = active.orElseThrow().addRaiders(Set.of(entity.getStringUUID()));
            if (reconciled != active.orElseThrow()) {
                data.update(reconciled);
            }
            return false;
        }
        if (entity.getPersistentData().getBooleanOr(APPROACH_FORM, false)
            || entity.getPersistentData().getBooleanOr(ESCAPED_FORM, false)) {
            return true;
        }
        clearRaidMarker(entity);
        return false;
    }

    private static Optional<AssaultState> matchingAssault(
        final VillageAssaultData data,
        final Mob raider,
        final AssaultKind expectedKind
    ) {
        return data.active()
            .filter(state -> state.kind() == expectedKind)
            .filter(state -> isAssaultRaider(raider))
            .filter(state -> assaultKind(raider).filter(expectedKind::equals).isPresent())
            .filter(state -> raider.getPersistentData().getLongOr(ASSAULT_CENTER, Long.MIN_VALUE)
                == state.center().asLong());
    }

    private static boolean eligibleObjectiveResident(
        final AssaultState state,
        final AbstractVillager resident
    ) {
        // The one species gate for every objective path. Widening the chain to AbstractVillager
        // admits no new species by itself: a Wandering Trader is an AbstractVillager too and is
        // rejected by both arms. The HOBGOBLIN arm deliberately no longer names a concrete class,
        // because a concrete-class test here is exactly what silently matches nothing once the
        // exact species moves to its own dedicated body.
        return switch (state.settlement()) {
            case HUMAN -> resident.getType() == EntityTypes.VILLAGER;
            case HOBGOBLIN -> resident instanceof ArcaneCreature hobgoblin
                && hobgoblin.creatureKind() == ArcaneCreature.CreatureKind.HOBGOBLIN
                && !isAssaultRaider(resident);
        };
    }

    private static void recordParticipantContribution(final LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
            || !isAssaultRaider(event.getEntity())
            || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        final long center = event.getEntity().getPersistentData().getLongOr(
            ASSAULT_CENTER,
            Long.MIN_VALUE
        );
        final VillageAssaultData data = VillageAssaultData.get(level);
        data.active()
            .filter(state -> state.center().asLong() == center)
            .map(state -> state.addParticipants(Set.of(player.getStringUUID())))
            .filter(updated -> data.active().orElseThrow() != updated)
            .ifPresent(data::update);
    }

    private static void applyReward(
        final ServerPlayer player,
        final ServerLevel level,
        final DefenseReward reward,
        final AssaultState state
    ) {
        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, reward.villageFavorTicks(), 0));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, reward.absorptionTicks(), 1));
        player.addEffect(new MobEffectInstance(
            reward.theme() == RewardTheme.INDUSTRY ? MobEffects.HASTE
                : reward.theme() == RewardTheme.DAWNWARD ? MobEffects.REGENERATION
                    : MobEffects.SPEED,
            reward.signatureBoonTicks(),
            reward.signatureAmplifier()
        ));
        player.getPersistentData().putLong("WarlockerySettlementFavorUntil",
            level.getGameTime() + reward.villageFavorTicks());
        player.getPersistentData().putString("WarlockerySettlementFavor",
            state.settlement().serializedName());
        player.sendSystemMessage(Component.translatable("message.warlockery.village.assault_defended")
            .withStyle(ChatFormatting.GOLD));
    }

    private static Mob spawnRaider(
        final ServerLevel level,
        final BlockPos position,
        final AssaultKind kind,
        final boolean leader
    ) {
        final Entity entity = switch (kind) {
            case GOBLIN -> ModEntities.GOBLIN.get().spawn(level, position, EntitySpawnReason.PATROL);
            case VAMPIRE -> ModEntities.ALL.get(vampireRaiderId(leader)).get().spawn(
                level, position, EntitySpawnReason.PATROL
            );
            case WEREWOLF -> ModEntities.WEREWOLF.get().spawn(level, position, EntitySpawnReason.PATROL);
        };
        return entity instanceof Mob mob ? mob : null;
    }

    static String vampireRaiderId(final boolean leader) {
        return leader ? "vampire" : "blood_thrall";
    }

    static boolean mayUseVampireObjective(final boolean leaderMarker, final boolean fullVampire) {
        return leaderMarker && fullVampire;
    }

    private static void reconcileVampireCourt(
        final ServerLevel level,
        final BlockPos center,
        final int wave
    ) {
        final List<VampireCourtEntity> members = activeRaiders(level, center).stream()
            .filter(VampireCourtEntity.class::isInstance)
            .map(VampireCourtEntity.class::cast)
            .filter(member -> member.getPersistentData().getIntOr(ASSAULT_WAVE, -1) == wave)
            .limit(com.kadamitas.warlockery.entity.VampireCourtRules.MAX_COURT_MEMBERS)
            .toList();
        final Optional<VampireCourtEntity> leader = members.stream()
            .filter(member -> member.creatureKind() == ArcaneCreature.CreatureKind.VAMPIRE)
            .filter(member -> member.getPersistentData().getBooleanOr(ASSAULT_LEADER, false))
            .findFirst();
        if (leader.isEmpty()) return;
        VampireCourtRuntime.markAssaultLeader(leader.orElseThrow());
        members.stream()
            .filter(member -> member.creatureKind() == ArcaneCreature.CreatureKind.BLOOD_THRALL)
            .forEach(member -> VampireCourtRuntime.bindAssaultMember(member, leader.orElseThrow()));
    }

    private static Optional<AssaultKind> assaultKind(final Entity entity) {
        return entity.getPersistentData().getString(ASSAULT_KIND)
            .map(AssaultKind::fromSerializedName);
    }

    private static List<Entity> activeRaiders(final ServerLevel level, final BlockPos center) {
        return level.getEntities(
            (Entity) null,
            new AABB(center).inflate(ASSAULT_SEARCH_RADIUS, 80.0, ASSAULT_SEARCH_RADIUS),
            entity -> entity.isAlive()
                && isAssaultRaider(entity)
                && entity.getPersistentData().getLongOr(ASSAULT_CENTER, Long.MIN_VALUE) == center.asLong()
        );
    }

    private static void registerRaidMembers(
        final ServerLevel level,
        final BlockPos center,
        final Set<String> memberIds
    ) {
        if (memberIds.isEmpty()) {
            return;
        }
        final VillageAssaultData data = VillageAssaultData.get(level);
        data.active()
            .filter(state -> state.center().equals(center))
            .map(state -> state.addRaiders(memberIds))
            .filter(updated -> data.active().orElseThrow() != updated)
            .ifPresent(data::update);
    }

    private static void replaceRaidMember(
        final ServerLevel level,
        final Entity previous,
        final Entity replacement
    ) {
        final VillageAssaultData data = VillageAssaultData.get(level);
        data.active()
            .filter(state -> state.center().asLong() == previous.getPersistentData().getLongOr(
                ASSAULT_CENTER,
                Long.MIN_VALUE
            ))
            .map(state -> state.replaceRaider(previous.getStringUUID(), replacement.getStringUUID()))
            .filter(updated -> data.active().orElseThrow() != updated)
            .ifPresent(data::update);
    }

    private static void removeRaidMember(final ServerLevel level, final Entity member) {
        final VillageAssaultData data = VillageAssaultData.get(level);
        data.active()
            .filter(state -> state.center().asLong() == member.getPersistentData().getLongOr(
                ASSAULT_CENTER,
                Long.MIN_VALUE
            ))
            .map(state -> state.removeRaider(member.getStringUUID()))
            .filter(updated -> data.active().orElseThrow() != updated)
            .ifPresent(data::update);
    }

    private static void clearRaidMarkers(final ServerLevel level, final BlockPos center) {
        SettlementFortificationRuntime.setGatesOpen(level, center, false);
        activeRaiders(level, center).forEach(entity -> {
            if (entity.getPersistentData().getBooleanOr(ESCAPED_FORM, false)
                || entity.getPersistentData().getBooleanOr(APPROACH_FORM, false)) {
                entity.discard();
                return;
            }
            clearRaidMarker(entity);
        });
    }

    private static void clearRaidMarker(final Entity entity) {
        entity.getPersistentData().remove(RAIDER_MARKER);
        entity.getPersistentData().remove(ASSAULT_KIND);
        entity.getPersistentData().remove(SETTLEMENT_KIND);
        entity.getPersistentData().remove(ASSAULT_CENTER);
        entity.getPersistentData().remove(ASSAULT_WAVE);
        entity.getPersistentData().remove(ASSAULT_LEADER);
        entity.getPersistentData().remove(HOBGOBLIN_VARIANT);
        if (entity instanceof ArcaneMob arcane) {
            arcane.setHobgoblinAssaultVariant(false);
        }
        // Releases target, combat role, enclave claims, and the derived persistence reason so a
        // timed-out or unloaded survivor can never stay permanently persistent.
        if (entity instanceof GoblinEntity exactGoblin) {
            exactGoblin.leaveVillageAssault();
        }
    }

    private static void closeApproachGatesIfUnused(
        final ServerLevel level,
        final AssaultState state,
        final Entity excluded
    ) {
        final boolean anotherWolfPassage = activeRaiders(level, state.center()).stream()
            .filter(entity -> entity != excluded && entity.isAlive())
            .anyMatch(entity -> entity instanceof Wolf
                && (entity.getPersistentData().getBooleanOr(APPROACH_FORM, false)
                    || entity.getPersistentData().getBooleanOr(ESCAPED_FORM, false)));
        if (!anotherWolfPassage) {
            SettlementFortificationRuntime.setGatesOpen(level, state.center(), false);
        }
    }

    private static void reconcileApproachGates(
        final ServerLevel level,
        final AssaultState state
    ) {
        final boolean hasWolfPassage = activeRaiders(level, state.center()).stream()
            .anyMatch(entity -> entity.isAlive()
                && entity instanceof Wolf
                && (entity.getPersistentData().getBooleanOr(APPROACH_FORM, false)
                    || entity.getPersistentData().getBooleanOr(ESCAPED_FORM, false)));
        SettlementFortificationRuntime.setGatesOpen(level, state.center(), hasWolfPassage);
    }

    private static SettlementFortificationRules.SettlementKind fortificationKind(
        final SettlementKind kind
    ) {
        return kind == SettlementKind.HUMAN
            ? SettlementFortificationRules.SettlementKind.HUMAN
            : SettlementFortificationRules.SettlementKind.HOBGOBLIN;
    }

    private static double horizontalDistanceSqr(final Entity entity, final BlockPos position) {
        final double x = entity.getX() - (position.getX() + 0.5);
        final double z = entity.getZ() - (position.getZ() + 0.5);
        return x * x + z * z;
    }

    private static void copyRaidMarker(final Entity source, final Entity destination) {
        destination.getPersistentData().putBoolean(RAIDER_MARKER, true);
        source.getPersistentData().getString(ASSAULT_KIND)
            .ifPresent(value -> destination.getPersistentData().putString(ASSAULT_KIND, value));
        source.getPersistentData().getString(SETTLEMENT_KIND)
            .ifPresent(value -> destination.getPersistentData().putString(SETTLEMENT_KIND, value));
        destination.getPersistentData().putLong(
            ASSAULT_CENTER,
            source.getPersistentData().getLongOr(ASSAULT_CENTER, Long.MIN_VALUE)
        );
        destination.getPersistentData().putInt(
            ASSAULT_WAVE,
            source.getPersistentData().getIntOr(ASSAULT_WAVE, 0)
        );
        destination.getPersistentData().putBoolean(
            ASSAULT_LEADER,
            source.getPersistentData().getBooleanOr(ASSAULT_LEADER, false)
        );
        destination.getPersistentData().putBoolean(HOBGOBLIN_VARIANT, isHobgoblinVariant(source));
    }

    private static Vec3 escapeDirection(final Entity entity) {
        final BlockPos center = BlockPos.of(entity.getPersistentData().getLongOr(
            ASSAULT_CENTER,
            entity.blockPosition().asLong()
        ));
        final Vec3 away = entity.position().subtract(Vec3.atCenterOf(center)).multiply(1.0, 0.0, 1.0);
        return away.lengthSqr() < 1.0E-4 ? new Vec3(0.4, 0.0, 0.0) : away.normalize().scale(0.55);
    }

    private static void navigateAway(final Mob raider, final BlockPos center) {
        final Vec3 direction = escapeDirection(raider);
        raider.getNavigation().moveTo(
            raider.getX() + direction.x * 32.0,
            raider.getY(),
            raider.getZ() + direction.z * 32.0,
            1.3
        );
    }

    private static boolean settlementPresent(final ServerLevel level, final AssaultState state) {
        final AABB bounds = new AABB(state.center()).inflate(64.0, 24.0, 64.0);
        return switch (state.settlement()) {
            case HUMAN -> !level.getEntitiesOfClass(
                Villager.class,
                bounds,
                villager -> villager.isAlive() && villager.getType() == EntityTypes.VILLAGER
            ).isEmpty();
            case HOBGOBLIN -> !level.getEntitiesOfClass(
                AbstractVillager.class,
                bounds,
                hobgoblin -> hobgoblin.isAlive()
                    && hobgoblin instanceof ArcaneCreature resident
                    && resident.creatureKind() == ArcaneCreature.CreatureKind.HOBGOBLIN
            ).isEmpty();
        };
    }

    private static Optional<SettlementTarget> settlementTarget(final ServerLevel level) {
        final List<ServerPlayer> players = level.players().stream()
            .filter(player -> !player.isSpectator())
            .toList();
        if (players.isEmpty()) {
            return Optional.empty();
        }
        final ServerPlayer start = players.get(level.getRandom().nextInt(players.size()));
        final Optional<BlockPos> human = humanVillageCenter(level, start.blockPosition());
        if (human.isPresent()) {
            return human.map(center -> new SettlementTarget(center, SettlementKind.HUMAN));
        }
        return hobgoblinSettlementCenter(level, start.blockPosition())
            .map(center -> new SettlementTarget(center, SettlementKind.HOBGOBLIN));
    }

    private static Optional<BlockPos> humanVillageCenter(final ServerLevel level, final BlockPos origin) {
        final Optional<BlockPos> registered = SettlementFortificationRuntime.findRegisteredCenter(
            level,
            origin,
            SettlementFortificationRules.SettlementKind.HUMAN,
            96.0
        ).filter(center -> !level.getEntitiesOfClass(
            Villager.class,
            new AABB(center).inflate(64.0, 24.0, 64.0),
            villager -> villager.isAlive() && villager.getType() == EntityTypes.VILLAGER
        ).isEmpty());
        if (registered.isPresent()) {
            return registered;
        }
        if (!level.isVillage(origin)) {
            return Optional.empty();
        }
        final AABB bounds = new AABB(origin).inflate(48.0, 16.0, 48.0);
        if (level.getEntitiesOfClass(
            Villager.class,
            bounds,
            villager -> villager.isAlive() && villager.getType() == EntityTypes.VILLAGER
        ).isEmpty()) {
            return Optional.empty();
        }
        return SectionPos.cube(SectionPos.of(origin), 2)
            .filter(level::isVillage)
            .map(SectionPos::center)
            .min(Comparator.comparingDouble(origin::distSqr));
    }

    private static Optional<BlockPos> hobgoblinSettlementCenter(
        final ServerLevel level,
        final BlockPos origin
    ) {
        final Optional<BlockPos> registered = SettlementFortificationRuntime.findRegisteredCenter(
            level,
            origin,
            SettlementFortificationRules.SettlementKind.HOBGOBLIN,
            96.0
        );
        if (registered.isEmpty()) {
            return Optional.empty();
        }
        final List<AbstractVillager> residents = level.getEntitiesOfClass(
            AbstractVillager.class,
            new AABB(registered.orElseThrow()).inflate(56.0, 20.0, 56.0),
            hobgoblin -> hobgoblin.isAlive()
                && hobgoblin instanceof ArcaneCreature resident
                && resident.creatureKind() == ArcaneCreature.CreatureKind.HOBGOBLIN
                && !isAssaultRaider(hobgoblin)
        );
        if (residents.size() < 2) {
            return Optional.empty();
        }
        return registered;
    }

    private static BlockPos entryPoint(
        final ServerLevel level,
        final BlockPos center,
        final int radius,
        final int initialDirection
    ) {
        BlockPos fallback = center;
        for (int attempt = 0; attempt < 8; attempt++) {
            final double angle = (initialDirection + attempt) * Math.PI / 4.0;
            final BlockPos candidate = surface(level, center.offset(
                (int) Math.round(Math.cos(angle) * radius),
                0,
                (int) Math.round(Math.sin(angle) * radius)
            ));
            fallback = candidate;
            if (level.isPositionEntityTicking(candidate) && !level.isVillage(candidate)) {
                return candidate;
            }
        }
        return fallback;
    }

    private static BlockPos surface(final ServerLevel level, final BlockPos position) {
        return new BlockPos(
            position.getX(),
            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, position.getX(), position.getZ()),
            position.getZ()
        );
    }

    private static boolean isNight(final ServerLevel level) {
        final long time = level.getOverworldClockTime() % 24_000L;
        return time >= 13_000L && time <= 23_000L;
    }

    private static boolean isFullMoon(final ServerLevel level, final BlockPos center) {
        return level.environmentAttributes().getValue(
            EnvironmentAttributes.MOON_PHASE,
            Vec3.atCenterOf(center)
        ) == MoonPhase.FULL_MOON;
    }

    public record FeedResult(
        float damage,
        boolean newlyCounted,
        long tradeLockExpiresAt,
        boolean retreatStarted
    ) {
    }

    private record SettlementTarget(BlockPos center, SettlementKind kind) {
        private SettlementTarget {
            center = center.immutable();
        }
    }
}
