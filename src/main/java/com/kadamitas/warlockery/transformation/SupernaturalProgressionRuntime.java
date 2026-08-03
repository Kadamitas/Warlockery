package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.entity.ArcaneCreature;
import com.kadamitas.warlockery.item.BloodGobletItem;
import com.kadamitas.warlockery.item.BloodGobletState;
import com.kadamitas.warlockery.item.ManualItem;
import com.kadamitas.warlockery.item.ManualProgress;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.network.ModNetwork;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;

public final class SupernaturalProgressionRuntime {
    private static final String BLOOD_REMAINING = "WarlockeryBloodRemaining";
    private static final String MESMERIZED_BY = "WarlockeryMesmerizedBy";
    public static final String NAAMAH_TRIAL_OWNER = "WarlockeryNaamahTrialOwner";
    private static final String SUMMON_OWNER = "WarlockerySupernaturalSummonOwner";
    private static final String SUMMON_EXPIRES = "WarlockerySupernaturalSummonExpires";
    private static final String BAT_FORM_VISUAL = "WarlockeryBatFormVisual";
    private static final SupernaturalProgression.Path VAMPIRE = SupernaturalProgression.Path.VAMPIRE;
    private static final SupernaturalProgression.Path WEREWOLF = SupernaturalProgression.Path.WEREWOLF;
    private static boolean registered;

    private SupernaturalProgressionRuntime() {
    }

    public static void registerEvents() {
        if (registered) {
            return;
        }
        registered = true;
        PlayerWolfVisualSync.registerEvents();
        TickEvent.PlayerTickEvent.Post.BUS.addListener(event -> tick(event.player()));
        LivingDamageEvent.BUS.addListener(SupernaturalState::handleDamage);
        LivingDamageEvent.BUS.addListener(SupernaturalProgressionRuntime::handleDamage);
        LivingHurtEvent.BUS.addListener(SupernaturalProgressionRuntime::handleHurt);
        LivingDeathEvent.BUS.addListener(SupernaturalProgressionRuntime::handleDeath);
        PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(SupernaturalProgressionRuntime::handleInteract);
        PlayerEvent.BreakSpeed.BUS.addListener(SupernaturalProgressionRuntime::handleBreakSpeed);
        BlockEvent.BreakEvent.BUS.addListener(SupernaturalProgressionRuntime::handleBlockBreak);
        PlayerEvent.Clone.BUS.addListener(SupernaturalProgressionRuntime::copyAfterClone);
    }

    public static void cyclePower(final ServerPlayer player) {
        final SupernaturalPower next = SupernaturalProgression.cyclePower(player);
        if (next == null) {
            show(player, "message.warlockery.progression.no_active_power", ChatFormatting.RED);
            sync(player);
            return;
        }
        player.sendOverlayMessage(Component.translatable(
            "message.warlockery.progression.power_selected",
            Component.translatable(next.translationKey())
        ).withStyle(ChatFormatting.AQUA));
        sync(player);
    }

    public static void activateSelectedPower(final ServerPlayer player) {
        if (tryTrainingHowl(player)) {
            sync(player);
            return;
        }
        final SupernaturalPower power = SupernaturalProgression.selectedPower(player);
        if (power == null) {
            show(player, "message.warlockery.progression.no_active_power", ChatFormatting.RED);
            sync(player);
            return;
        }
        if (power == SupernaturalPower.BAT_SWARM
            && player.isShiftKeyDown()
            && SupernaturalAbilityRules.batSwarmFormActive(
                SupernaturalProgression.level(player, VAMPIRE),
                SupernaturalProgression.batSwarmUntil(player),
                player.level().getGameTime()
            )) {
            SupernaturalProgression.setBatSwarmUntil(player, 0L);
            tickBatFlight(player);
            sync(player);
            return;
        }
        final int level = SupernaturalProgression.level(player, power.path());
        if (level < power.level()) {
            show(player, "message.warlockery.progression.power_locked", ChatFormatting.RED);
            return;
        }
        if (SupernaturalProgression.onCooldown(player, power)) {
            show(player, "message.warlockery.progression.power_cooling_down", ChatFormatting.RED);
            return;
        }
        if (SupernaturalProgression.resource(player, power.path()) < power.cost()) {
            show(player, "message.warlockery.progression.not_enough_power", ChatFormatting.RED);
            return;
        }
        if (isChargedBloodPower(power)
            && SupernaturalProgression.value(player, VAMPIRE, "charges_" + power.id()) <= 0L) {
            show(player, "message.warlockery.vampire_progression.blood_power_empty", ChatFormatting.RED);
            return;
        }
        if (!activate(player, power)) {
            show(player, "message.warlockery.progression.invalid_target", ChatFormatting.RED);
            return;
        }
        SupernaturalProgression.spend(player, power.path(), power.cost());
        if (isChargedBloodPower(power)) {
            final String key = "charges_" + power.id();
            SupernaturalProgression.setValue(
                player,
                VAMPIRE,
                key,
                Math.max(0L, SupernaturalProgression.value(player, VAMPIRE, key) - 1L)
            );
        }
        SupernaturalProgression.startCooldown(player, power);
        player.sendOverlayMessage(Component.translatable(
            "message.warlockery.progression.power_used",
            Component.translatable(power.translationKey())
        ).withStyle(ChatFormatting.GREEN));
        sync(player);
    }

    public static void recordSunGrenadeBurn(final ServerPlayer player) {
        if (SupernaturalState.getForm(player) != SupernaturalForm.VAMPIRE
            || SupernaturalProgression.level(player, VAMPIRE) != 4
            || !player.level().isDarkOutside()) {
            return;
        }
        SupernaturalAdvancement.recordVampire(
            player,
            VampireProgressionRules.Metric.NIGHTTIME_SUN_GRENADE_BURNS,
            1
        );
        sync(player);
    }

    public static void recordNaamahAudience(final ServerPlayer player) {
        if (SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE
            && SupernaturalProgression.level(player, VAMPIRE) == 6) {
            SupernaturalAdvancement.recordVampire(
                player,
                VampireProgressionRules.Metric.NAAMAH_AUDIENCE_COMPLETED,
                1
            );
        }
    }

    public static void recordNaamahDefeat(final ServerPlayer player) {
        if (SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE
            && SupernaturalProgression.level(player, VAMPIRE) == 6) {
            SupernaturalAdvancement.recordVampire(
                player,
                VampireProgressionRules.Metric.NAAMAH_DEFEATED,
                1
            );
            sync(player);
        }
    }

    public static void recordPoppyOffering(final ServerPlayer player) {
        if (SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE
            && SupernaturalProgression.level(player, VAMPIRE) == 6
            && SupernaturalProgression.counter(
                player,
                VAMPIRE,
                VampireProgressionRules.Metric.NAAMAH_DEFEATED
            ) > 0) {
            SupernaturalAdvancement.recordVampire(
                player,
                VampireProgressionRules.Metric.POPPY_OFFERED_TO_NAAMAH,
                1
            );
            sync(player);
        }
    }

    public static boolean tryCreateVampire(
        final ServerPlayer creator,
        final LivingEntity target,
        final ItemStack goblet
    ) {
        if (SupernaturalState.getForm(creator) != SupernaturalForm.VAMPIRE
            || SupernaturalProgression.level(creator, VAMPIRE) < 9
            || !BloodGobletState.isFull(goblet)
            || SympatheticBinding.read(goblet).filter(binding -> binding.targets(creator)).isEmpty()
            || !coffinNear(creator.level(), target.blockPosition())) {
            return false;
        }
        if (target instanceof Villager villager) {
            if (!villager.getPersistentData().getBooleanOr("WarlockeryCreationTargetDrained", false)
                || !creator.getStringUUID().equals(villager.getPersistentData().getStringOr(MESMERIZED_BY, ""))) {
                return false;
            }
            final Entity converted = ModEntities.ALL.get("vampire").get().create(
                creator.level(),
                EntitySpawnReason.MOB_SUMMONED
            );
            if (!(converted instanceof Mob vampire)) {
                return false;
            }
            vampire.setPos(villager.position());
            vampire.setYRot(villager.getYRot());
            vampire.setPersistenceRequired();
            com.kadamitas.warlockery.entity.CreatureBehaviorState.bind(vampire, creator.getUUID());
            if (!creator.level().addFreshEntity(vampire)) {
                return false;
            }
            villager.discard();
        } else if (target instanceof Player targetPlayer
            && targetPlayer.isShiftKeyDown()
            && SupernaturalState.getForm(targetPlayer) == SupernaturalForm.NONE
            && targetPlayer.getPersistentData().getBooleanOr("WarlockeryCreationTargetDrained", false)
            && creator.getStringUUID().equals(targetPlayer.getPersistentData().getStringOr(MESMERIZED_BY, ""))) {
            SupernaturalAdvancement.beginVampire(targetPlayer);
        } else {
            return false;
        }
        BloodGobletState.setFull(goblet, false);
        SupernaturalAdvancement.recordVampire(creator, VampireProgressionRules.Metric.CREATION_TARGET_FULLY_DRAINED, 1);
        SupernaturalAdvancement.recordVampire(creator, VampireProgressionRules.Metric.CREATION_TARGET_MESMERIZED, 1);
        SupernaturalAdvancement.recordVampire(creator, VampireProgressionRules.Metric.OWN_BLOOD_GOBLET_OFFERED, 1);
        SupernaturalAdvancement.recordVampire(creator, VampireProgressionRules.Metric.COFFIN_WITHIN_FOUR_BLOCKS, 1);
        SupernaturalAdvancement.recordVampire(creator, VampireProgressionRules.Metric.VAMPIRE_CREATED_NEAR_COFFIN, 1);
        sync(creator);
        return true;
    }

    public static boolean chargeBloodPower(final ServerPlayer player, final ItemStack ingredient) {
        if (SupernaturalState.getForm(player) != SupernaturalForm.VAMPIRE
            || SupernaturalProgression.level(player, VAMPIRE) < 10
            || SupernaturalProgression.resource(player, VAMPIRE)
                < SupernaturalProgression.maximumResource(VAMPIRE, 10)) {
            return false;
        }
        final SupernaturalPower power;
        if (ingredient.is(Items.BONE)) {
            power = SupernaturalPower.TELEPORT;
        } else if (ingredient.is(ModItems.ALL.get("ingredient_artichoke").get())) {
            power = SupernaturalPower.CALL_STORM;
        } else if (ingredient.is(ModItems.ALL.get("ingredient_bat_wool").get())) {
            power = SupernaturalPower.SUMMON_BATS;
        } else {
            return false;
        }
        final Map<SupernaturalPower, Integer> currentCharges = SupernaturalAbilityRules.bloodPowers()
            .stream()
            .collect(Collectors.toMap(
                charged -> charged,
                charged -> (int) Math.clamp(
                    SupernaturalProgression.value(player, VAMPIRE, "charges_" + charged.id()),
                    0L,
                    Integer.MAX_VALUE
                )
            ));
        final SupernaturalAbilityRules.BloodPowerCharge charge = SupernaturalAbilityRules.replaceBloodPower(
            currentCharges,
            power
        );
        charge.charges().forEach((charged, charges) -> SupernaturalProgression.setValue(
            player,
            VAMPIRE,
            "charges_" + charged.id(),
            charges
        ));
        SupernaturalProgression.selectPower(player, power);
        SupernaturalProgression.setResource(player, VAMPIRE, 0);
        if (!player.hasInfiniteMaterials()) {
            ingredient.shrink(1);
        }
        player.sendOverlayMessage(Component.translatable(
            charge.replaced()
                ? "message.warlockery.vampire_progression.blood_power_replaced"
                : "message.warlockery.vampire_progression.blood_power_charged",
            Component.translatable(power.translationKey()),
            SupernaturalAbilityRules.BLOOD_POWER_CHARGES
        ).withStyle(ChatFormatting.DARK_RED));
        sync(player);
        return true;
    }

    public static ModNetwork.SupernaturalSnapshot snapshot(final ServerPlayer player) {
        final SupernaturalForm form = SupernaturalState.getForm(player);
        final Optional<SupernaturalProgression.Path> path = SupernaturalProgression.Path.forForm(form);
        final MagicDisplay magic = magicDisplay(player);
        if (path.isEmpty()) {
            return new ModNetwork.SupernaturalSnapshot(
                "", 0, 0, 0, "", "", "", "", -1, 0,
                magic.path(), magic.resource(), magic.maximum()
            );
        }
        final SupernaturalProgression.Path active = path.orElseThrow();
        final int level = SupernaturalProgression.level(player, active);
        final SupernaturalPower power = SupernaturalProgression.selectedPower(player);
        final QuestDisplay quest = active == VAMPIRE ? vampireQuest(player) : werewolfQuest(player);
        return new ModNetwork.SupernaturalSnapshot(
            "path.warlockery." + active.id(),
            level,
            SupernaturalProgression.resource(player, active),
            SupernaturalProgression.maximumResource(active, level),
            power == null ? "" : power.translationKey(),
            active == WEREWOLF
                ? "shape.warlockery." + SupernaturalProgression.werewolfShape(player).name().toLowerCase(Locale.ROOT)
                : "",
            quest.titleKey(),
            quest.progress(),
            powerCharges(player, power),
            powerCooldown(player, power),
            magic.path(),
            magic.resource(),
            magic.maximum()
        );
    }

    private static MagicDisplay magicDisplay(final ServerPlayer player) {
        return MagicPathState.selected(player)
            .map(path -> new MagicDisplay(path.id(), MagicPathState.reserve(player, path), path.maximumReserve()))
            .orElseGet(() -> new MagicDisplay("", 0, 0));
    }

    private static int powerCharges(final ServerPlayer player, final SupernaturalPower power) {
        if (power == null || !isChargedBloodPower(power)) {
            return -1;
        }
        return (int) Math.min(
            Integer.MAX_VALUE,
            SupernaturalProgression.value(player, VAMPIRE, "charges_" + power.id())
        );
    }

    private static int powerCooldown(final ServerPlayer player, final SupernaturalPower power) {
        if (power == null) {
            return 0;
        }
        return (int) Math.min(
            Integer.MAX_VALUE,
            Math.max(0L, SupernaturalProgression.cooldown(player, power) - player.level().getGameTime())
        );
    }

    private static void tick(final Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        SupernaturalState.tick(serverPlayer);
        tickBatFlight(serverPlayer);
        tickSummons(serverPlayer);
        tickMesmerized(serverPlayer);
        tickZombieRespect(serverPlayer);
        if (serverPlayer.tickCount % 20 != 0) {
            if (serverPlayer.tickCount % 5 == 0) {
                sync(serverPlayer);
            }
            return;
        }
        if (SupernaturalState.getForm(serverPlayer) == SupernaturalForm.VAMPIRE) {
            tickVampireProgression(serverPlayer);
        }
        sync(serverPlayer);
    }

    private static void tickVampireProgression(final ServerPlayer player) {
        final int level = SupernaturalProgression.level(player, VAMPIRE);
        final ManualState manual = observations(player);
        SupernaturalProgression.setCounter(
            player,
            VAMPIRE,
            VampireProgressionRules.Metric.OBSERVATIONS_MANUAL_OWNED,
            manual.owned() ? 1 : 0
        );
        SupernaturalProgression.setCounter(
            player,
            VAMPIRE,
            VampireProgressionRules.Metric.TORN_PAGES_INSERTED,
            manual.pages()
        );
        if (level == 1) {
            SupernaturalProgression.setCounter(
                player,
                VAMPIRE,
                VampireProgressionRules.Metric.BLOOD_STORED,
                SupernaturalProgression.resource(player, VAMPIRE)
            );
        }
        if (SupernaturalAdvancement.advanceVampireIfReady(player).advanced()) {
            return;
        }
        if (level == 3) {
            tickVampireNight(player, manual);
        }
        if (level == 7 && SupernaturalProgression.batSwarmUntil(player) > player.level().getGameTime()
            && player.level().isVillage(player.blockPosition())) {
            final String village = player.level().dimension().identifier() + ":"
                + SectionPos.blockToSectionCoord(player.getBlockX()) + ":"
                + SectionPos.blockToSectionCoord(player.getBlockZ());
            SupernaturalAdvancement.recordUniqueVampire(
                player,
                VampireProgressionRules.Metric.DISTINCT_VILLAGES_REACHED_IN_BATSWARM_FORM,
                village
            );
        }
        player.removeEffect(MobEffects.POISON);
        player.setAirSupply(player.getMaxAirSupply());
        if (SupernaturalProgression.resource(player, VAMPIRE) == 0 && player.getFoodData().getFoodLevel() == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 3, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 1, true, false));
        }
    }

    private static void tickVampireNight(final ServerPlayer player, final ManualState manual) {
        final long clock = player.level().getOverworldClockTime();
        final long day = Math.floorDiv(clock, 24_000L);
        final long time = Math.floorMod(clock, 24_000L);
        if (!manual.owned() || manual.pages() < 3) {
            SupernaturalProgression.setValue(player, VAMPIRE, "night_in_progress", -1L);
            SupernaturalProgression.setCounter(
                player,
                VAMPIRE,
                VampireProgressionRules.Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED,
                0
            );
            return;
        }
        if (time >= 13_000L && time < 13_100L
            && SupernaturalProgression.value(player, VAMPIRE, "night_started") != day) {
            final long lastCompleted = SupernaturalProgression.value(player, VAMPIRE, "night_completed");
            if (SupernaturalProgression.counter(
                player,
                VAMPIRE,
                VampireProgressionRules.Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED
            ) > 0 && lastCompleted != day - 1L) {
                SupernaturalProgression.setCounter(
                    player,
                    VAMPIRE,
                    VampireProgressionRules.Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED,
                    0
                );
            }
            SupernaturalProgression.setValue(player, VAMPIRE, "night_started", day);
            SupernaturalProgression.setValue(player, VAMPIRE, "night_in_progress", day);
        }
        if (time >= 23_000L && time < 23_100L
            && SupernaturalProgression.value(player, VAMPIRE, "night_in_progress") == day
            && SupernaturalProgression.value(player, VAMPIRE, "night_completed") != day) {
            SupernaturalProgression.setValue(player, VAMPIRE, "night_completed", day);
            SupernaturalAdvancement.recordVampire(
                player,
                VampireProgressionRules.Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED,
                1
            );
        }
    }

    private static void handleInteract(final PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !(event.getTarget() instanceof LivingEntity target)
            || !event.getItemStack().isEmpty()) {
            return;
        }
        if (SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE && player.isShiftKeyDown()) {
            drinkBlood(player, target);
            return;
        }
        final int level = SupernaturalProgression.level(player, WEREWOLF);
        if (SupernaturalState.getForm(player) == SupernaturalForm.WEREWOLF
            && level == 7
            && SupernaturalProgression.werewolfShape(player) == WerewolfShape.WOLF
            && target instanceof Wolf wolf
            && !wolf.isTame()) {
            if (player.getRandom().nextInt(3) == 0) {
                wolf.tame(player);
                SupernaturalAdvancement.recordUniqueWerewolf(
                    player,
                    WerewolfProgressionRules.Metric.WOLVES_BEFRIENDED_IN_WOLF_FORM,
                    wolf.getUUID().hashCode()
                );
            } else if (player.getRandom().nextInt(10) == 0) {
                wolf.setPersistentAngerTarget(net.minecraft.world.entity.EntityReference.of(player.getUUID()));
            }
            sync(player);
        }
    }

    private static void drinkBlood(final ServerPlayer player, final LivingEntity target) {
        if (target == player || !target.isAlive()) {
            return;
        }
        if (target instanceof Player targetPlayer
            && SupernaturalState.getForm(targetPlayer) == SupernaturalForm.WEREWOLF) {
            player.hurtServer(player.level(), player.damageSources().magic(), 4.0F);
            show(player, "message.warlockery.vampire_progression.werewolf_blood", ChatFormatting.RED);
            return;
        }
        final boolean human = target instanceof Player || target instanceof Villager;
        final int maximum = human ? 500 : 100;
        final int remaining = target.getPersistentData().getIntOr(BLOOD_REMAINING, maximum);
        if (remaining <= 0) {
            show(player, "message.warlockery.vampire_progression.target_drained", ChatFormatting.RED);
            return;
        }
        final boolean batSwarmForm = SupernaturalAbilityRules.batSwarmFormActive(
            SupernaturalProgression.level(player, VAMPIRE),
            SupernaturalProgression.batSwarmUntil(player),
            player.level().getGameTime()
        );
        final int amount = Math.min(
            remaining,
            SupernaturalAbilityRules.bloodSipAmount(human ? 10 : 2, batSwarmForm)
        );
        target.getPersistentData().putInt(BLOOD_REMAINING, remaining - amount);
        SupernaturalProgression.addResource(player, VAMPIRE, amount);
        player.getFoodData().eat(Math.max(1, amount / 5), 0.5F);
        if (target.getHealth() > 1.0F) {
            target.hurtServer(player.level(), target.damageSources().playerAttack(player), 1.0F);
        }
        if (target instanceof Villager villager && remaining - amount <= 250) {
            if (SupernaturalProgression.level(player, VAMPIRE) == 2) {
                SupernaturalAdvancement.recordUniqueVampire(
                    player,
                    VampireProgressionRules.Metric.DISTINCT_VILLAGERS_HALF_DRAINED,
                    villager.getStringUUID()
                );
            }
            if (SupernaturalProgression.level(player, VAMPIRE) == 8 && isCaged(villager)) {
                SupernaturalAdvancement.recordUniqueVampire(
                    player,
                    VampireProgressionRules.Metric.DISTINCT_CAGED_VILLAGERS_HALF_DRAINED,
                    villager.getStringUUID()
                );
            }
        }
        if (SupernaturalProgression.level(player, VAMPIRE) == 9 && remaining - amount == 0) {
            target.getPersistentData().putBoolean("WarlockeryCreationTargetDrained", true);
        }
        if (SupernaturalProgression.level(player, VAMPIRE) == 1) {
            SupernaturalAdvancement.recordVampireValue(
                player,
                VampireProgressionRules.Metric.BLOOD_STORED,
                SupernaturalProgression.resource(player, VAMPIRE)
            );
        }
        player.sendOverlayMessage(Component.translatable(
            "message.warlockery.vampire_progression.drank_blood",
            amount,
            SupernaturalProgression.resource(player, VAMPIRE),
            SupernaturalProgression.maximumResource(VAMPIRE, SupernaturalProgression.level(player, VAMPIRE))
        ).withStyle(ChatFormatting.DARK_RED));
        sync(player);
    }

    private static void handleDamage(final LivingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            final SupernaturalForm form = SupernaturalState.getForm(attacker);
            final int level = SupernaturalProgression.Path.forForm(form)
                .map(path -> SupernaturalProgression.level(attacker, path))
                .orElse(0);
            final boolean directMeleeAttack = event.getSource().getDirectEntity() == attacker;
            if (form == SupernaturalForm.VAMPIRE && directMeleeAttack) {
                event.setAmount(event.getAmount() + (level >= 7 ? 3.0F : level >= 4 ? 2.0F : 1.0F));
                if (SupernaturalAbilityRules.vampireKnockbackActive(
                    level,
                    attacker.isShiftKeyDown(),
                    directMeleeAttack
                )) {
                    event.getEntity().push(attacker.getLookAngle().x * 1.2, 0.3, attacker.getLookAngle().z * 1.2);
                }
            }
            if (form == SupernaturalForm.WEREWOLF
                && SupernaturalProgression.werewolfShape(attacker) != WerewolfShape.HUMAN) {
                final WerewolfShape shape = SupernaturalProgression.werewolfShape(attacker);
                event.setAmount(event.getAmount() + SupernaturalAbilityRules.sprintingDamageBonus(
                    level,
                    shape,
                    attacker.isSprinting(),
                    directMeleeAttack
                ));
                final boolean hunterProtected = protectedByHunterArmor(event.getEntity());
                if (SupernaturalAbilityRules.canSpreadWerewolfCurse(
                    level,
                    shape,
                    directMeleeAttack,
                    event.getEntity().getHealth() - event.getAmount()
                        <= event.getEntity().getMaxHealth() * 0.25F,
                    event.getEntity() instanceof Player || event.getEntity() instanceof Villager,
                    hunterProtected,
                    true
                )
                    && attacker.getRandom().nextInt(4) == 0) {
                    spreadWerewolfCurse(event.getEntity());
                }
            }
        }
    }

    private static void handleHurt(final LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)
            || event.getSource().getDirectEntity() != attacker
            || SupernaturalState.getForm(attacker) != SupernaturalForm.WEREWOLF) {
            return;
        }
        final int level = SupernaturalProgression.level(attacker, WEREWOLF);
        final WerewolfShape shape = SupernaturalProgression.werewolfShape(attacker);
        if (!SupernaturalAbilityRules.armorRendingActive(level, shape)) {
            return;
        }
        final LivingEntity target = event.getEntity();
        final float intendedDamage = event.getAmount();
        final double piercingDamage = SupernaturalAbilityRules.armorPiercingInputDamage(
            intendedDamage,
            input -> CombatRules.getDamageAfterAbsorb(
                target,
                (float) input,
                event.getSource(),
                target.getArmorValue(),
                (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
            )
        );
        event.setAmount((float) piercingDamage);
    }

    private static void handleDeath(final LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        final LivingEntity victim = event.getEntity();
        if (SupernaturalState.getForm(killer) == SupernaturalForm.VAMPIRE) {
            final int level = SupernaturalProgression.level(killer, VAMPIRE);
            if (level == 5 && victim instanceof Blaze) {
                SupernaturalAdvancement.recordVampire(killer, VampireProgressionRules.Metric.BLAZES_DEFEATED, 1);
            }
            if (level == 6 && victim instanceof ArcaneCreature arcane
                && arcane.creatureKind() == ArcaneCreature.CreatureKind.NAAMAH) {
                recordNaamahDefeat(killer);
            }
        }
        if (SupernaturalState.getForm(killer) != SupernaturalForm.WEREWOLF
            || SupernaturalProgression.werewolfShape(killer) == WerewolfShape.HUMAN) {
            return;
        }
        final int level = SupernaturalProgression.level(killer, WEREWOLF);
        if (level >= 4 && !victim.typeHolder().is(EntityTypeTags.UNDEAD)) {
            killer.getFoodData().eat(8, 0.8F);
        }
        if (level == 4 && victim instanceof ArcaneCreature arcane
            && arcane.creatureKind() == ArcaneCreature.CreatureKind.THORNED_PURSUER) {
            SupernaturalAdvancement.recordWerewolf(
                killer,
                WerewolfProgressionRules.Metric.HORNED_HUNTSMEN_DEFEATED,
                1
            );
        } else if (level == 5 && victim instanceof Enemy && !killer.onGround()) {
            SupernaturalAdvancement.recordWerewolf(
                killer,
                WerewolfProgressionRules.Metric.HOSTILES_DEFEATED_WHILE_AIRBORNE,
                1
            );
        } else if (level == 8
            && victim instanceof ZombifiedPiglin
            && killer.level().dimension() == Level.NETHER
            && SupernaturalProgression.werewolfShape(killer) == WerewolfShape.WOLFMAN) {
            SupernaturalAdvancement.recordWerewolf(
                killer,
                WerewolfProgressionRules.Metric.ZOMBIFIED_PIGLINS_DEFEATED_IN_NETHER,
                1
            );
        } else if (level == 9 && (victim instanceof Villager || victim instanceof Player)) {
            SupernaturalAdvancement.recordWerewolf(
                killer,
                WerewolfProgressionRules.Metric.TRUSTED_PREY_DEFEATED_WHILE_TRANSFORMED,
                1
            );
        }
        sync(killer);
    }

    static void handleBreakSpeed(final PlayerEvent.BreakSpeed event) {
        final Player player = event.getEntity();
        if (player.level().isClientSide()
            || SupernaturalState.getForm(player) != SupernaturalForm.WEREWOLF) {
            return;
        }
        final int werewolfLevel = SupernaturalProgression.level(player, WEREWOLF);
        final WerewolfShape shape = SupernaturalProgression.werewolfShape(player);
        final boolean emptyHand = player.getMainHandItem().isEmpty();
        event.setNewSpeed(SupernaturalAbilityRules.wolfDiggingSpeed(
            event.getNewSpeed(),
            werewolfLevel,
            shape,
            emptyHand,
            isLooseEarth(event.getState()),
            player.isShiftKeyDown(),
            isEarth(event.getState())
        ));
    }

    private static void handleBlockBreak(final BlockEvent.BreakEvent event) {
        final Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)
            || SupernaturalState.getForm(player) != SupernaturalForm.WEREWOLF
            || SupernaturalProgression.level(player, WEREWOLF) < 3
            || SupernaturalProgression.werewolfShape(player) == WerewolfShape.HUMAN
            || !isEarth(event.getState())) {
            return;
        }
        final long ready = SupernaturalProgression.value(player, WEREWOLF, "bone_finding_ready");
        if (player.level().getGameTime() >= ready && player.getRandom().nextInt(20) == 0) {
            final ItemStack bones = new ItemStack(Items.BONE, player.getRandom().nextIntBetweenInclusive(1, 2));
            if (!player.getInventory().add(bones)) {
                player.drop(bones, false);
            }
            SupernaturalProgression.setValue(player, WEREWOLF, "bone_finding_ready",
                player.level().getGameTime() + 1_200L);
        }
    }

    private static boolean activate(final ServerPlayer player, final SupernaturalPower power) {
        return switch (power) {
            case TRANSFIX -> transfix(player, false);
            case BLOOD_RUSH -> bloodRush(player);
            case SMASH_STONE -> smashStone(player);
            case BAT_SWARM -> batSwarmForm(player);
            case MESMERIZE -> transfix(player, true);
            case CREATE_VAMPIRE -> createVampire(player);
            case CALL_STORM -> callStorm(player);
            case TELEPORT -> teleport(player);
            case SUMMON_BATS -> summonBats(player);
            case WOLF_FORM -> changeWerewolfShape(player, WerewolfShape.WOLF);
            case FEAST -> feast(player);
            case WOLFMAN_FORM -> changeWerewolfShape(player, WerewolfShape.WOLFMAN);
            case STUN_HOWL -> stunHowl(player);
            case CALL_PACK -> callPack(player);
        };
    }

    private static boolean transfix(final ServerPlayer player, final boolean mesmerize) {
        final LivingEntity target = lookedAt(player, 16.0).orElse(null);
        if (target == null || target == player) {
            return false;
        }
        final int duration = 20 * (5 + SupernaturalProgression.level(player, VAMPIRE) / 2);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, mesmerize ? 6 : 4));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2));
        if (mesmerize) {
            target.getPersistentData().putString(MESMERIZED_BY, player.getStringUUID());
            if (target instanceof Villager villager) {
                villager.getNavigation().moveTo(player, 1.1);
            }
        }
        return true;
    }

    private static boolean bloodRush(final ServerPlayer player) {
        final int level = SupernaturalProgression.level(player, VAMPIRE);
        final int currentAmplifier = Optional.ofNullable(player.getEffect(MobEffects.SPEED))
            .map(MobEffectInstance::getAmplifier)
            .orElse(-1);
        final int amplifier = SupernaturalAbilityRules.nextBloodRushAmplifier(level, currentAmplifier);
        final int duration = SupernaturalAbilityRules.bloodRushDurationTicks(amplifier);
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, duration, amplifier));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, duration, amplifier));
        return true;
    }

    private static boolean smashStone(final ServerPlayer player) {
        final HitResult hit = player.pick(6.0, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            return false;
        }
        final BlockPos pos = blockHit.getBlockPos();
        final BlockState state = player.level().getBlockState(pos);
        if (!state.is(BlockTags.BASE_STONE_OVERWORLD)
            && !state.is(Blocks.COBBLESTONE)
            && !state.is(Blocks.DEEPSLATE)) {
            return false;
        }
        if (!player.level().mayInteract(player, pos)) {
            return false;
        }
        player.causeFoodExhaustion(10.0F);
        return player.level().destroyBlock(pos, true, player, 512);
    }

    private static boolean batSwarmForm(final ServerPlayer player) {
        player.level().getEntities(
            player,
            player.getBoundingBox().inflate(96.0),
            entity -> player.getStringUUID().equals(entity.getPersistentData().getStringOr(SUMMON_OWNER, ""))
                && entity.getPersistentData().getBooleanOr(BAT_FORM_VISUAL, false)
        ).forEach(Entity::discard);
        SupernaturalProgression.setValue(player, VAMPIRE, "bat_previous_mayfly",
            player.getAbilities().mayfly ? 1L : 0L);
        SupernaturalProgression.setValue(player, VAMPIRE, "bat_flight_active", 1L);
        SupernaturalProgression.setBatSwarmUntil(player, player.level().getGameTime() + 600L);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 600, 0, true, false));
        IntStream.range(0, SupernaturalAbilityRules.BATSWARM_VISUAL_COUNT)
            .forEach(index -> spawnBat(player, 600L, true, 1.0 + index * 0.4));
        return true;
    }

    private static boolean createVampire(final ServerPlayer player) {
        final LivingEntity target = lookedAt(player, 8.0).orElse(null);
        if (target == null) {
            return false;
        }
        final ItemStack goblet = fullGoblet(player).orElse(null);
        return goblet != null && tryCreateVampire(player, target, goblet);
    }

    private static boolean callStorm(final ServerPlayer player) {
        final var weather = player.level().getWeatherData();
        final int duration = player.getRandom().nextIntBetweenInclusive(6_000, 18_000);
        weather.setClearWeatherTime(0);
        weather.setRaining(true);
        weather.setRainTime(duration);
        weather.setThundering(true);
        weather.setThunderTime(duration);
        return true;
    }

    private static boolean teleport(final ServerPlayer player) {
        final Vec3 look = player.getLookAngle();
        for (int distance = 24; distance >= 4; distance -= 2) {
            final Vec3 destination = player.position().add(look.scale(distance));
            if (player.randomTeleport(destination.x, destination.y, destination.z, true)) {
                return true;
            }
        }
        return false;
    }

    private static boolean summonBats(final ServerPlayer player) {
        IntStream.range(0, SupernaturalAbilityRules.ATTACKING_BAT_COUNT)
            .forEach(index -> spawnBat(player, 300L, false, 3.0));
        return true;
    }

    private static boolean changeWerewolfShape(final ServerPlayer player, final WerewolfShape requested) {
        final boolean hasCharm = IntStream.range(0, player.getInventory().getContainerSize())
            .mapToObj(player.getInventory()::getItem)
            .anyMatch(stack -> stack.is(ModItems.ALL.get("mooncharm").get()));
        if (!hasCharm || requested == WerewolfShape.WOLFMAN
            && SupernaturalProgression.level(player, WEREWOLF) < 5) {
            return false;
        }
        final WerewolfShape current = SupernaturalProgression.werewolfShape(player);
        SupernaturalProgression.setWerewolfShape(player, current == requested ? WerewolfShape.HUMAN : requested);
        return true;
    }

    private static boolean feast(final ServerPlayer player) {
        if (SupernaturalProgression.werewolfShape(player) == WerewolfShape.HUMAN) {
            return false;
        }
        player.heal(6.0F);
        player.getFoodData().eat(8, 0.8F);
        return true;
    }

    private static boolean stunHowl(final ServerPlayer player) {
        if (SupernaturalProgression.werewolfShape(player) != WerewolfShape.WOLFMAN) {
            return false;
        }
        final int duration = 80 + Math.max(0, SupernaturalProgression.level(player, WEREWOLF) - 7) * 20;
        player.level().getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(16.0),
            target -> target != player && !target.isAlliedTo(player)
        ).forEach(target -> {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 6));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2));
        });
        howlParticles(player);
        return true;
    }

    private static boolean callPack(final ServerPlayer player) {
        if (SupernaturalProgression.werewolfShape(player) != WerewolfShape.WOLF) {
            return false;
        }
        final int level = SupernaturalProgression.level(player, WEREWOLF);
        final int count = 2 + (level > 8 ? player.getRandom().nextInt(Math.min(3, level - 7)) : 0);
        for (int index = 0; index < count; index++) {
            final Wolf wolf = EntityTypes.WOLF.create(player.level(), EntitySpawnReason.MOB_SUMMONED);
            if (wolf == null) {
                continue;
            }
            wolf.setPos(player.getX() + index - 1.0, player.getY(), player.getZ() + 1.0);
            wolf.tame(player);
            wolf.getPersistentData().putString(SUMMON_OWNER, player.getStringUUID());
            wolf.getPersistentData().putLong(SUMMON_EXPIRES, player.level().getGameTime() + 200L);
            player.level().addFreshEntity(wolf);
        }
        howlParticles(player);
        return true;
    }

    private static boolean tryTrainingHowl(final ServerPlayer player) {
        if (SupernaturalState.getForm(player) != SupernaturalForm.WEREWOLF
            || SupernaturalProgression.level(player, WEREWOLF) != 6
            || SupernaturalProgression.werewolfShape(player) == WerewolfShape.HUMAN
            || player.getXRot() > -75.0F
            || !player.level().isDarkOutside()) {
            return false;
        }
        final long marker = net.minecraft.world.level.ChunkPos.pack(
            SectionPos.blockToSectionCoord(player.getBlockX()),
            SectionPos.blockToSectionCoord(player.getBlockZ())
        );
        SupernaturalAdvancement.recordUniqueWerewolf(
            player,
            WerewolfProgressionRules.Metric.DISTINCT_HOWL_REGIONS,
            marker
        );
        howlParticles(player);
        return true;
    }

    private static void tickBatFlight(final ServerPlayer player) {
        final boolean active = SupernaturalState.getForm(player) == SupernaturalForm.VAMPIRE
            && SupernaturalAbilityRules.batSwarmFormActive(
                SupernaturalProgression.level(player, VAMPIRE),
                SupernaturalProgression.batSwarmUntil(player),
                player.level().getGameTime()
            );
        if (active) {
            if (!player.getAbilities().mayfly || !player.getAbilities().flying) {
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
            if (player.getForcedPose() == null) {
                player.setForcedPose(Pose.SWIMMING);
                SupernaturalProgression.setValue(player, VAMPIRE, "bat_pose_active", 1L);
            }
            if (player.tickCount % 40 == 0 && !SupernaturalProgression.spend(player, VAMPIRE, 1)) {
                SupernaturalProgression.setBatSwarmUntil(player, 0L);
            }
            return;
        }
        if (SupernaturalProgression.value(player, VAMPIRE, "bat_flight_active") == 0L) {
            return;
        }
        SupernaturalProgression.setValue(player, VAMPIRE, "bat_flight_active", 0L);
        final boolean privileged = player.isCreative() || player.isSpectator();
        final boolean previous = SupernaturalProgression.value(player, VAMPIRE, "bat_previous_mayfly") == 1L;
        player.getAbilities().mayfly = privileged || previous;
        if (!player.getAbilities().mayfly) {
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();
        if (SupernaturalProgression.value(player, VAMPIRE, "bat_pose_active") == 1L) {
            if (player.getForcedPose() == Pose.SWIMMING) {
                player.setForcedPose(null);
            }
            SupernaturalProgression.setValue(player, VAMPIRE, "bat_pose_active", 0L);
        }
    }

    private static void tickSummons(final ServerPlayer player) {
        final List<Entity> summons = player.level().getEntities(
            player,
            player.getBoundingBox().inflate(96.0),
            entity -> player.getStringUUID().equals(entity.getPersistentData().getStringOr(SUMMON_OWNER, ""))
        );
        if (summons.isEmpty()) {
            return;
        }
        final LivingEntity gazedTarget = lookedAt(player, 24.0)
            .filter(target -> target != player
                && target.isAlive()
                && !player.getStringUUID().equals(
                    target.getPersistentData().getStringOr(SUMMON_OWNER, "")
                ))
            .orElse(null);
        final LivingEntity retaliationTarget = player.getLastHurtMob() != null
            ? player.getLastHurtMob()
            : player.getLastHurtByMob();
        final LivingEntity commandedTarget = switch (SupernaturalAbilityRules.batCommandTarget(
            gazedTarget != null,
            retaliationTarget != null && retaliationTarget.isAlive()
        )) {
            case GAZE -> gazedTarget;
            case RETALIATION -> retaliationTarget;
            case NONE -> null;
        };
        summons.forEach(entity -> {
            if (entity.getPersistentData().getLongOr(SUMMON_EXPIRES, 0L) <= player.level().getGameTime()) {
                entity.discard();
            } else if (entity instanceof Bat bat
                && entity.getPersistentData().getBooleanOr(BAT_FORM_VISUAL, false)) {
                if (SupernaturalAbilityRules.batSwarmFormActive(
                    SupernaturalProgression.level(player, VAMPIRE),
                    SupernaturalProgression.batSwarmUntil(player),
                    player.level().getGameTime()
                )) {
                    orbitPlayer(player, bat);
                } else {
                    bat.discard();
                }
            } else if (entity instanceof Bat bat && commandedTarget != null) {
                pursueWithBat(player, bat, commandedTarget);
            } else if (player.tickCount % 20 == 0 && entity instanceof Mob mob) {
                if (commandedTarget != null && mob.canAttack(commandedTarget)) {
                    mob.setTarget(commandedTarget);
                }
            }
        });
    }

    private static void spawnBat(
        final ServerPlayer player,
        final long lifetime,
        final boolean formVisual,
        final double radius
    ) {
        final Entity entity = EntityTypes.BAT.create(player.level(), EntitySpawnReason.MOB_SUMMONED);
        if (!(entity instanceof Bat bat)) {
            return;
        }
        bat.setPos(
            player.getX() + player.getRandom().nextDouble() * radius * 2.0 - radius,
            player.getY() + 1.0 + player.getRandom().nextDouble() * Math.max(1.0, radius),
            player.getZ() + player.getRandom().nextDouble() * radius * 2.0 - radius
        );
        bat.getPersistentData().putString(SUMMON_OWNER, player.getStringUUID());
        bat.getPersistentData().putLong(SUMMON_EXPIRES, player.level().getGameTime() + lifetime);
        bat.getPersistentData().putBoolean(BAT_FORM_VISUAL, formVisual);
        player.level().addFreshEntity(bat);
    }

    private static void orbitPlayer(final ServerPlayer player, final Bat bat) {
        final double angle = player.level().getGameTime() * 0.16 + bat.getId() * 2.094;
        final Vec3 destination = player.position().add(
            Math.cos(angle) * 1.2,
            1.0 + Math.sin(angle * 0.7) * 0.35,
            Math.sin(angle) * 1.2
        );
        bat.setDeltaMovement(destination.subtract(bat.position()).scale(0.3).add(player.getDeltaMovement().scale(0.5)));
    }

    private static void pursueWithBat(
        final ServerPlayer player,
        final Bat bat,
        final LivingEntity target
    ) {
        final Vec3 pursuit = target.getEyePosition().subtract(bat.position());
        if (pursuit.lengthSqr() > 0.01) {
            bat.setDeltaMovement(pursuit.normalize().scale(0.6));
        }
        if (pursuit.lengthSqr() <= 4.0
            && Math.floorMod(bat.getId(), 20) == Math.floorMod(player.tickCount, 20)) {
            target.hurtServer(
                player.level(),
                player.damageSources().indirectMagic(bat, player),
                1.0F
            );
        }
    }

    private static void tickMesmerized(final ServerPlayer player) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        player.level().getEntitiesOfClass(
            Villager.class,
            player.getBoundingBox().inflate(32.0),
            villager -> player.getStringUUID().equals(villager.getPersistentData().getStringOr(MESMERIZED_BY, ""))
        ).forEach(villager -> {
            if (villager.distanceToSqr(player) > 6.0) {
                villager.getNavigation().moveTo(player, 1.1);
            }
        });
    }

    private static void tickZombieRespect(final ServerPlayer player) {
        if (player.tickCount % 20 != 0
            || SupernaturalState.getForm(player) != SupernaturalForm.VAMPIRE
            || SupernaturalProgression.level(player, VAMPIRE) < 10) {
            return;
        }
        player.level().getEntitiesOfClass(
            Zombie.class,
            player.getBoundingBox().inflate(24.0),
            zombie -> zombie.getTarget() == player
        ).forEach(zombie -> zombie.setTarget(null));
    }

    private static Optional<LivingEntity> lookedAt(final ServerPlayer player, final double range) {
        final Vec3 eye = player.getEyePosition();
        final Vec3 look = player.getLookAngle();
        return player.level().getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(range),
            target -> target != player && target.isAlive() && player.hasLineOfSight(target)
        ).stream()
            .filter(target -> {
                final Vec3 direction = target.getEyePosition().subtract(eye);
                return direction.lengthSqr() <= range * range
                    && direction.normalize().dot(look) >= 0.94;
            })
            .min(Comparator.comparingDouble(player::distanceToSqr));
    }

    private static Optional<ItemStack> fullGoblet(final Player player) {
        return IntStream.range(0, player.getInventory().getContainerSize())
            .mapToObj(player.getInventory()::getItem)
            .filter(stack -> stack.getItem() instanceof BloodGobletItem
                && BloodGobletState.isFull(stack)
                && SympatheticBinding.read(stack).filter(binding -> binding.targets(player)).isPresent())
            .findFirst();
    }

    private static boolean coffinNear(final ServerLevel level, final BlockPos center) {
        return BlockPos.betweenClosedStream(center.offset(-4, -2, -4), center.offset(4, 3, 4))
            .anyMatch(pos -> level.getBlockState(pos).is(ModBlocks.ALL.get("coffinblock").get()));
    }

    private static boolean isCaged(final Villager villager) {
        if (villager.isPassenger()) {
            return true;
        }
        final BlockPos center = villager.blockPosition();
        final boolean bars = BlockPos.betweenClosedStream(center.offset(-2, -1, -2), center.offset(2, 2, 2))
            .filter(pos -> Math.abs(pos.getX() - center.getX()) == 2
                || Math.abs(pos.getZ() - center.getZ()) == 2)
            .anyMatch(pos -> villager.level().getBlockState(pos).is(Blocks.IRON_BARS));
        final boolean roof = BlockPos.betweenClosedStream(center.offset(-2, 2, -2), center.offset(2, 3, 2))
            .anyMatch(pos -> villager.level().getBlockState(pos).is(BlockTags.PLANKS));
        return bars && roof;
    }

    private static boolean isEarth(final BlockState state) {
        return state.is(BlockTags.DIRT)
            || state.is(BlockTags.SAND)
            || state.is(Blocks.GRAVEL)
            || state.is(Blocks.CLAY);
    }

    private static boolean isLooseEarth(final BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND);
    }

    private static void spreadWerewolfCurse(final LivingEntity target) {
        if (protectedByHunterArmor(target)) {
            return;
        }
        if (target instanceof Player player) {
            SupernaturalAdvancement.beginWerewolf(player);
        } else if (target instanceof Villager) {
            target.getPersistentData().putBoolean("WarlockeryLycanthropy", true);
            target.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 12_000, 1));
            target.addEffect(new MobEffectInstance(MobEffects.SPEED, 12_000, 1));
        }
    }

    private static boolean protectedByHunterArmor(final LivingEntity target) {
        return List.of(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        ).stream().map(target::getItemBySlot).anyMatch(stack ->
            stack.is(WarlockeryTags.Items.WARLOCK_HUNTER_ARMOR)
                || stack.is(WarlockeryTags.Items.SILVERED_HUNTER_ARMOR)
                || stack.is(WarlockeryTags.Items.DAWN_HUNTER_ARMOR)
        );
    }

    private static ManualState observations(final Player player) {
        return IntStream.range(0, player.getInventory().getContainerSize())
            .mapToObj(player.getInventory()::getItem)
            .filter(stack -> stack.getItem() instanceof ManualItem manual
                && ManualProgress.isObservations(manual.profile()))
            .map(stack -> {
                final ManualItem manual = (ManualItem) stack.getItem();
                return new ManualState(
                    true,
                    ManualProgress.insertedTornPages(manual.profile(), stack)
                );
            })
            .max(Comparator.comparingInt(ManualState::pages))
            .orElse(new ManualState(false, 0));
    }

    private static QuestDisplay vampireQuest(final Player player) {
        final var evaluation = VampireProgressionRules.evaluate(SupernaturalAdvancement.vampireProgress(player));
        return evaluation.quest().map(quest -> {
            final VampireProgressionRules.RequirementStatus focus = evaluation.requirements().stream()
                .filter(status -> !status.satisfied())
                .findFirst()
                .orElse(evaluation.requirements().getLast());
            return new QuestDisplay(
                "quest.warlockery.vampire." + quest.id(),
                focus.current() + " / " + focus.requirement().required()
            );
        }).orElse(new QuestDisplay("message.warlockery.progression.path_complete", ""));
    }

    private static QuestDisplay werewolfQuest(final Player player) {
        final var evaluation = WerewolfProgressionRules.evaluate(SupernaturalAdvancement.werewolfProgress(player));
        return evaluation.quest().map(quest -> {
            final WerewolfProgressionRules.RequirementStatus focus = evaluation.requirements().stream()
                .filter(status -> !status.satisfied())
                .findFirst()
                .orElse(evaluation.requirements().getLast());
            return new QuestDisplay(
                "quest.warlockery.werewolf." + quest.id(),
                focus.current() + " / " + focus.requirement().required()
            );
        }).orElse(new QuestDisplay("message.warlockery.progression.path_complete", ""));
    }

    private static void howlParticles(final ServerPlayer player) {
        player.level().sendParticles(
            ParticleTypes.SONIC_BOOM,
            player.getX(),
            player.getEyeY(),
            player.getZ(),
            1,
            0.0,
            0.0,
            0.0,
            0.0
        );
    }

    private static void copyAfterClone(final PlayerEvent.Clone event) {
        SupernaturalState.copyAfterClone(event);
        if (event.isWasDeath()
            && SupernaturalState.getForm(event.getEntity()) == SupernaturalForm.VAMPIRE
            && SupernaturalProgression.level(event.getEntity(), VAMPIRE) == 3) {
            SupernaturalProgression.setCounter(
                event.getEntity(),
                VAMPIRE,
                VampireProgressionRules.Metric.CONSECUTIVE_FULL_NIGHTS_SURVIVED,
                0
            );
            SupernaturalProgression.setValue(event.getEntity(), VAMPIRE, "night_in_progress", -1L);
        }
    }

    private static void sync(final ServerPlayer player) {
        ModNetwork.sendSupernaturalSnapshot(player, snapshot(player));
        PlayerWolfVisualSync.refresh(player);
    }

    private static void show(final ServerPlayer player, final String key, final ChatFormatting color) {
        player.sendOverlayMessage(Component.translatable(key).withStyle(color));
    }

    private static boolean isChargedBloodPower(final SupernaturalPower power) {
        return SupernaturalAbilityRules.bloodPowers().contains(power);
    }

    private record ManualState(boolean owned, int pages) {
    }

    private record QuestDisplay(String titleKey, String progress) {
    }

    private record MagicDisplay(String path, int resource, int maximum) {
    }
}
