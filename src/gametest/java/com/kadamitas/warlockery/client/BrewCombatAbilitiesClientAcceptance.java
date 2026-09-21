package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.brew.BrewBehavior;
import com.kadamitas.warlockery.brew.BrewItem;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.brew.BrewRuntime;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BrewCombatAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final Set<BrewBehavior> COVERED = Set.of(
        BrewBehavior.REMOVE_BENEFICIAL, BrewBehavior.REMOVE_HARMFUL, BrewBehavior.REMOVE_NAUSEA,
        BrewBehavior.HARM_WEREWOLVES, BrewBehavior.WEAKEN_VAMPIRES, BrewBehavior.HARM_DEMONS,
        BrewBehavior.HARM_INSECTS, BrewBehavior.BUFF_UNDEAD, BrewBehavior.SPREAD_HARMFUL,
        BrewBehavior.STEAL_BENEFICIAL, BrewBehavior.HARM_UNDEAD, BrewBehavior.CURSE_UNDEAD,
        BrewBehavior.DRAIN_RESERVES, BrewBehavior.EXTEND_EFFECTS);
    private static final AABB AREA = new AABB(-10, 97, -10, 11, 115, 11);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private volatile ImpactObservation observation;
    private TestSingleplayerContext world;
    private Path evidence;
    private long started;

    @Override
    public void runTest(final ClientGameTestContext context) {
        started = System.currentTimeMillis();
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("brew-combat-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final ImpactObservation current = observation;
                if (current != null && current.player.level().getServer() == server) current.tick();
            });
            final List<Identifier> ids = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals("warlockery"))
                .filter(id -> BuiltInRegistries.ITEM.getValue(id) instanceof BrewItem item
                    && item.kind().behaviors().stream().anyMatch(COVERED::contains)
                    && !item.kind().behaviors().contains(BrewBehavior.EXPLODE)).sorted().toList();
            check(!ids.isEmpty(), "Actual registry must expose combat-effect brews");
            check(ids.stream().flatMap(id -> ((BrewItem) BuiltInRegistries.ITEM.getValue(id)).kind().behaviors().stream())
                .collect(Collectors.toSet()).containsAll(COVERED), "Runtime registry must expose every one of the fourteen scheduled behavior families");
            final String configured = System.getProperty("warlockery.brewCombatIds", "");
            final Set<String> selected = configured.isBlank() ? ids.stream().map(Identifier::toString).collect(Collectors.toSet())
                : Arrays.stream(configured.split(",")).map(String::strip)
                    .map(id -> id.contains(":") ? id : "warlockery:" + id).collect(Collectors.toSet());
            check(!selected.isEmpty() && ids.stream().map(Identifier::toString).collect(Collectors.toSet()).containsAll(selected),
                "Selected IDs must belong to the actual combat-family registry census");
            for (Identifier id : ids) {
                final BrewKind kind = ((BrewItem) BuiltInRegistries.ITEM.getValue(id)).kind();
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", selected.contains(id.toString()) ? "NOT_RUN" : "NOT_SELECTED");
                row.put("kind", kind.id());
                row.put("behaviors", kind.behaviors().stream().map(Enum::name).toList());
                row.put("radius", kind.radius());
                row.put("potency", kind.potency());
                row.put("canonical_guide", "brew_entry_" + kind.id());
                row.put("acquisition_status", "NOT_RUN");
                row.put("persistence_status", "NOT_RUN");
                results.put(id.toString(), row);
            }
            write(false);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (Identifier id : ids) {
                if (!selected.contains(id.toString())) continue;
                final Map<String, Object> row = results.get(id.toString());
                row.put("status", "RUNNING");
                row.put("started_at", System.currentTimeMillis());
                write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        runBrew(context, id, row);
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        recordFailure(id, row, failure);
                        try { screenshot(context, id.getPath() + "-failure-in-world"); }
                        catch (Throwable capture) { row.put("screenshot_failure", capture.toString()); }
                    } finally {
                        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
                        final ImpactObservation current = observation;
                        if (current != null) row.put("impact_observation", serverValue(player -> current.report()));
                        observation = null;
                        row.put("finished_at", System.currentTimeMillis());
                        write(false);
                    }
                } catch (Throwable failure) {
                    recordFailure(id, row, failure);
                    write(false);
                } finally {
                    observation = null;
                    world = null;
                }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_BREW_COMBAT_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Native thrown brew evidence: " + evidence, failure);
        }
    }

    private void runBrew(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) throws Exception {
        final BrewKind kind = ((BrewItem) BuiltInRegistries.ITEM.getValue(id)).kind();
        server(player -> {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getAbilities().invulnerable = false;
            player.onUpdateAbilities();
            final var server = player.level().getServer();
            server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            player.level().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            player.level().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, false, server);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 18000");
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-12, 98, -12), new BlockPos(12, 112, 12)))
                player.level().setBlockAndUpdate(pos, pos.getY() <= 99 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.teleportTo(0.5, 100, 0.5);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        });
        world.getConnection().waitForChunksRender();
        world.getConnection().waitForClientboundPackets();
        readBook(context, id, kind, row);
        server(player -> {
            final String type = kind.behaviors().contains(BrewBehavior.HARM_WEREWOLVES) ? "warlockery:werewolf"
                : kind.behaviors().contains(BrewBehavior.WEAKEN_VAMPIRES) ? "warlockery:vampire"
                : kind.behaviors().contains(BrewBehavior.HARM_DEMONS) ? "warlockery:demon"
                : kind.behaviors().contains(BrewBehavior.HARM_INSECTS) ? "minecraft:spider"
                : kind.behaviors().stream().anyMatch(b -> b == BrewBehavior.BUFF_UNDEAD || b == BrewBehavior.HARM_UNDEAD
                    || b == BrewBehavior.CURSE_UNDEAD) ? "minecraft:zombie" : "minecraft:cow";
            final Mob target = target(player, type, new Vec3(2.0, 100, 0.5));
            final Mob control = target(player, kind.behaviors().contains(BrewBehavior.BUFF_UNDEAD) ? "minecraft:zombie" : "minecraft:cow",
                new Vec3(-1.5, 100, 0.5));
            final Mob outside = target(player, type, new Vec3(kind.radius() + 4.0, 100, 0.5));
            if (kind.behaviors().contains(BrewBehavior.BUFF_UNDEAD)) {
                com.kadamitas.warlockery.entity.CreatureBehaviorState.bind(target, player.getUUID());
                for (Mob mob : List.of(target, control, outside)) mob.setHealth(50.0F);
            }
            if (kind.behaviors().stream().anyMatch(b -> b == BrewBehavior.REMOVE_BENEFICIAL || b == BrewBehavior.REMOVE_HARMFUL
                || b == BrewBehavior.REMOVE_NAUSEA || b == BrewBehavior.DRAIN_RESERVES || b == BrewBehavior.STEAL_BENEFICIAL)) {
                for (Mob mob : List.of(target, outside)) {
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SPEED, 1200, 1));
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MINING_FATIGUE, 1200, 1));
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.NAUSEA, 1200, 0));
                }
            }
            if (kind.behaviors().contains(BrewBehavior.SPREAD_HARMFUL)) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MINING_FATIGUE, 1200, 2));
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SPEED, 1200, 1));
            }
            if (kind.behaviors().contains(BrewBehavior.EXTEND_EFFECTS)) {
                for (Mob mob : List.of(target, outside)) {
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SPEED, 600, 1, true, false, false));
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MINING_FATIGUE, 24000, 2));
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0));
                }
            }
            if (kind.behaviors().contains(BrewBehavior.DRAIN_RESERVES)) {
                com.kadamitas.warlockery.magic.MagicPathState.grantPermanent(player, com.kadamitas.warlockery.magic.MagicPath.OVERWORLD);
                player.getInventory().setItem(9, new ItemStack(Items.STICK));
            }
            player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
            observation = new ImpactObservation(player, target, control, outside, id);
        });
        row.put("staged_prerequisites", "Disposable Survival world, nighttime bedrock arena, stationary living targets with normal server ticks, 100 health, zero armor and toughness, supplied brew and named potion-effect prerequisites. Owned-undead binding and an Overworld infusion are staged only for the cases that require them. Effects, damage and outcome under test come from the native projectile; no BrewRuntime, impact or damage method is invoked by the test.");
        row.put("covered_behaviors", kind.behaviors().stream().filter(COVERED::contains).map(Enum::name).toList());
        row.put("other_behaviors_not_claimed", kind.behaviors().stream().filter(b -> !COVERED.contains(b)).map(Enum::name).toList());
        if (kind.behaviors().contains(BrewBehavior.DRAIN_RESERVES)) row.put("remaining_energy_contract",
            "NOT_RUN: no third-party item exposing the energy_reserve API is staged. This case verifies beneficial-effect removal, unchanged ordinary item and unchanged infusion reserve, not compatible-energy extraction.");
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(90); });
        context.waitTicks(2);
        screenshot(context, id.getPath() + "-before-native-throw");
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        for (int tick = 0; tick < 100; tick++) {
            if (serverValue(player -> observation.after != null)) break;
            context.waitTicks(1);
        }
        check(serverValue(player -> observation.projectileIds.size() == 1 && observation.after != null),
            "Exactly one owned potion must fly and complete its real native collision");
        check(serverValue(player -> player.getMainHandItem().isEmpty()), "Native use consumes exactly one brew");
        final ImpactObservation result = observation;
        row.put("impact_observation", serverValue(player -> result.report()));
        server(player -> verify(kind, result, row));
        if (kind.behaviors().contains(BrewBehavior.BUFF_UNDEAD)) {
            context.waitTicks(101);
            final Map<String, State> healed = serverValue(player -> result.snapshot());
            check(healed.get("target").health >= 52.0F && healed.get("target").health <= 53.0F,
                "Owned undead must really regain health at one point per fifty normal ticks");
            check(healed.get("control").health == 50.0F && healed.get("outside").health == 50.0F,
                "Unowned and out-of-range wounded undead must remain unhealed");
            row.put("healing_after_101_ticks", healed);
        }
        screenshot(context, id.getPath() + "-after-native-impact");
        row.put("ability_complete", false);
        row.put("remaining", "Acquisition, all delivery variants, multiplayer and any extra behaviors listed above.");
    }

    private static Mob target(final ServerPlayer player, final String type, final Vec3 position) {
        final Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(type)).create(player.level(), EntitySpawnReason.COMMAND);
        check(entity instanceof Mob, "Actual typed target must be a living mob: " + type);
        final Mob mob = (Mob) entity;
        mob.setPos(position.x, position.y, position.z);
        mob.setNoAi(true);
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        mob.setHealth(100);
        mob.setPersistenceRequired();
        for (EquipmentSlot slot : EquipmentSlot.VALUES) mob.setItemSlot(slot, ItemStack.EMPTY);
        player.level().addFreshEntity(mob);
        return mob;
    }

    private static void verify(final BrewKind kind, final ImpactObservation result, final Map<String, Object> row) {
        final State positive = result.after.get("target");
        final State negative = result.after.get("control");
        final State outside = result.after.get("outside");
        final State owner = result.after.get("owner");
        final State before = result.before.get("target");
        final List<String> checks = new ArrayList<>();
        check(outside.health == result.before.get("outside").health, "Out-of-range same-species control must retain health");
        check(outside.effects.keySet().equals(result.before.get("outside").effects.keySet()),
            "Out-of-range control must retain its original effects without new effects");
        for (BrewBehavior behavior : kind.behaviors()) {
            switch (behavior) {
                case REMOVE_BENEFICIAL, DRAIN_RESERVES -> {
                    absent(positive, "speed");
                    effect(positive, "mining_fatigue", 1);
                    effect(positive, "nausea", 0);
                    effect(outside, "speed", 1);
                    if (behavior == BrewBehavior.DRAIN_RESERVES) {
                        check(result.reserveAfter == result.reserveBefore, "Drain Magic must not consume infusion reserve");
                        check(result.player.getInventory().getItem(9).is(Items.STICK), "Ordinary non-energy item must remain intact");
                    }
                    checks.add(behavior + ": positive speed removed, harmful effects and out-of-range speed preserved");
                }
                case REMOVE_HARMFUL -> {
                    effect(positive, "speed", 1);
                    absent(positive, "mining_fatigue");
                    absent(positive, "nausea");
                    effect(outside, "mining_fatigue", 1);
                    checks.add("Harmful effects removed while beneficial speed survives");
                }
                case REMOVE_NAUSEA -> {
                    absent(positive, "nausea");
                    effect(positive, "speed", 1);
                    effect(positive, "mining_fatigue", 1);
                    effect(outside, "nausea", 0);
                    checks.add("Only Nausea removed");
                }
                case HARM_WEREWOLVES, HARM_DEMONS, HARM_INSECTS, HARM_UNDEAD -> {
                    final float expected = behavior == BrewBehavior.HARM_WEREWOLVES ? 10.0F : 12.0F;
                    check(Math.abs(before.health - positive.health - expected * kind.potency()) < 0.02F,
                        behavior + " must inflict its actual typed damage; the DEMON kind has no supernatural reduction");
                    check(negative.health == result.before.get("control").health, "In-range ordinary cow must not take typed damage");
                    check(positive.sourceOwner.equals(result.player.getUUID().toString()),
                        behavior + " must attribute damage to the native thrower; actual owner=" + positive.sourceOwner);
                    check(result.projectileIds.contains(positive.directSource), "Damaging source must be the actual observed thrown potion");
                    checks.add(behavior + ": exact health loss, eligible species only, real thrower and projectile attribution");
                }
                case WEAKEN_VAMPIRES -> {
                    effect(positive, "weakness", 2);
                    check(positive.effects.get("weakness").duration >= 1190, "Vampire receives about 60 seconds of Weakness III");
                    absent(negative, "weakness");
                    absent(outside, "weakness");
                    checks.add("Only nearby vampire receives Weakness III");
                }
                case BUFF_UNDEAD -> {
                    effect(positive, "strength", 1);
                    effect(positive, "resistance", 0);
                    effect(positive, "undead_mending", 0);
                    for (String effect : List.of("strength", "resistance", "undead_mending")) {
                        absent(negative, effect);
                        absent(outside, effect);
                    }
                    checks.add("Owned undead receives Strength II, Resistance I and Undead Mending; unowned undead remains unbuffed");
                }
                case SPREAD_HARMFUL -> {
                    effect(positive, "mining_fatigue", 2);
                    effect(negative, "mining_fatigue", 2);
                    effect(owner, "mining_fatigue", 2);
                    absent(positive, "speed");
                    absent(negative, "speed");
                    absent(outside, "mining_fatigue");
                    check(Math.abs(positive.effects.get("mining_fatigue").duration - owner.effects.get("mining_fatigue").duration) <= 2
                        && Math.abs(negative.effects.get("mining_fatigue").duration - owner.effects.get("mining_fatigue").duration) <= 2,
                        "Both recipients preserve the source's remaining harmful duration without inflation");
                    checks.add("Closest afflicted thrower's Mining Fatigue III spreads; original retained; Speed not copied");
                }
                case STEAL_BENEFICIAL -> {
                    absent(positive, "speed");
                    effect(owner, "speed", 1);
                    effect(positive, "mining_fatigue", 1);
                    absent(owner, "mining_fatigue");
                    effect(outside, "speed", 1);
                    checks.add("Speed moves from donor to thrower while harmful and out-of-range effects stay put");
                }
                case CURSE_UNDEAD -> {
                    effect(positive, "weakness", 1);
                    check(positive.fireTicks >= 590, "Undead must actually ignite for about 30 seconds");
                    check(negative.fireTicks <= 0 && outside.fireTicks <= 0, "Ordinary cow and distant undead must remain unlit");
                    absent(negative, "weakness");
                    checks.add("Nearby undead ignited and weakened; ordinary living and distant undead preserved");
                }
                case EXTEND_EFFECTS -> {
                    effect(positive, "speed", 1);
                    effect(positive, "mining_fatigue", 2);
                    final Effect oldSpeed = result.lastBefore.get("target").effects.get("speed");
                    final Effect speed = positive.effects.get("speed");
                    check(Math.abs(speed.duration - 2 * oldSpeed.duration) <= 4, "Finite duration doubles at impact, within normal tick ordering");
                    check(positive.effects.get("mining_fatigue").duration >= 35998
                        && positive.effects.get("mining_fatigue").duration <= 36000, "Long harmful duration caps at 30 minutes");
                    check(positive.effects.get("night_vision").duration == -1, "Infinite duration stays infinite");
                    check(speed.ambient == oldSpeed.ambient && speed.visible == oldSpeed.visible && speed.icon == oldSpeed.icon,
                        "Extension preserves amplifier and presentation flags");
                    check(outside.effects.get("speed").duration <= result.before.get("outside").effects.get("speed").duration,
                        "Out-of-range effect is not extended");
                    checks.add("Beneficial and harmful effects extended; cap, infinite effect and flags preserved");
                }
                default -> { }
            }
        }
        row.put("native_checks", checks);
    }

    private static void absent(final State state, final String id) {
        check(!state.effects.containsKey(id), id + " must be absent");
    }

    private static void effect(final State state, final String id, final int amplifier) {
        check(state.effects.containsKey(id) && state.effects.get(id).amplifier == amplifier,
            id + " must have amplifier " + amplifier + "; observed=" + state.effects.get(id));
    }

    private record Effect(int duration, int amplifier, boolean ambient, boolean visible, boolean icon) { }
    private record State(float health, Map<String, Effect> effects, int fireTicks, String sourceOwner, String directSource) { }

    private static State state(final LivingEntity entity) {
        final Map<String, Effect> effects = new LinkedHashMap<>();
        entity.getActiveEffects().forEach(effect -> effects.put(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).getPath(),
            new Effect(effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon())));
        final var source = entity.getLastDamageSource();
        return new State(entity.getHealth(), effects, entity.getRemainingFireTicks(),
            source == null || source.getEntity() == null ? "" : source.getEntity().getUUID().toString(),
            source == null || source.getDirectEntity() == null ? "" : source.getDirectEntity().getUUID().toString());
    }

    private static final class ImpactObservation {
        private final ServerPlayer player;
        private final Mob target;
        private final Mob control;
        private final Mob outside;
        private final Identifier item;
        private final Set<String> projectileIds = new LinkedHashSet<>();
        private final Map<String, State> before;
        private Map<String, State> lastBefore;
        private Map<String, State> after;
        private int observedTicks;
        private int projectilesPresent;
        private final int reserveBefore;
        private int reserveAfter;
        private ImpactObservation(final ServerPlayer player, final Mob target, final Mob control, final Mob outside, final Identifier item) {
            this.player = player; this.target = target; this.control = control; this.outside = outside; this.item = item;
            before = snapshot();
            lastBefore = before;
            reserveBefore = com.kadamitas.warlockery.magic.MagicPathState.reserve(player, com.kadamitas.warlockery.magic.MagicPath.OVERWORLD);
        }
        private Map<String, State> snapshot() {
            return Map.of("target", state(target), "control", state(control), "outside", state(outside), "owner", state(player));
        }
        private void tick() {
            observedTicks++;
            final List<AbstractThrownPotion> projectiles = player.level().getEntitiesOfClass(AbstractThrownPotion.class, AREA,
                potion -> potion.getOwner() == player && potion.getItem().is(BuiltInRegistries.ITEM.getValue(item)));
            projectilesPresent = projectiles.size();
            projectiles.forEach(potion -> projectileIds.add(potion.getUUID().toString()));
            if (after == null && !projectileIds.isEmpty() && projectilesPresent == 0) {
                after = snapshot();
                reserveAfter = com.kadamitas.warlockery.magic.MagicPathState.reserve(player, com.kadamitas.warlockery.magic.MagicPath.OVERWORLD);
            } else if (after == null) lastBefore = snapshot();
        }
        private Map<String, Object> report() {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("observed_server_ticks", observedTicks);
            result.put("native_projectile_uuids", projectileIds);
            result.put("projectiles_present", projectilesPresent);
            result.put("target_type", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
            result.put("before", before);
            result.put("last_before_impact", lastBefore);
            result.put("first_after_impact", after);
            result.put("infusion_reserve_before", reserveBefore);
            result.put("infusion_reserve_after", reserveAfter);
            return result;
        }
    }

    private void readBook(final ClientGameTestContext context, final Identifier id, final BrewKind kind,
        final Map<String, Object> row) throws Exception {
        final String section = "brew_entry_" + kind.id();
        final var candidate = ManualProfile.profiles().stream().filter(profile -> profile.sections().contains(section)).findFirst();
        check(candidate.isPresent(), "Player books must index canonical combat-brew guide " + section);
        final ManualProfile profile = candidate.orElseThrow();
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(profile.id()).get()));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.player.setXRot(-75));
        context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, section);
        final String text = context.computeOnClient(client -> ManualArticleCatalog.article(profile, section).body().getString());
        check(!text.isBlank(), "Canonical brew guide must contain readable text");
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(),
                    ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection"))
                && (int) field(client.gui.screen(), "bodyPage") == expected), "Native page input reaches the canonical brew instructions");
            screenshot(context, id.getPath() + "-canonical-book-" + page);
        }
        row.put("book", profile.id());
        row.put("book_status", "ALL_PAGES_REACHED");
        row.put("book_pages_read", pages);
        row.put("book_text", text);
        row.put("guide_mapping", "This exact registry item uses BrewKind " + kind.id() + "; canonical section " + section
            + " is read for that shared behavior. This does not assert identical acquisition recipes for aliases.");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
    }

    private static Object field(final Object object, final String name) {
        try {
            final var field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer()));
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
    }

    private void recordFailure(final Identifier id, final Map<String, Object> row, final Throwable failure) {
        row.put("status", "FAILED");
        row.put("failure", failure.toString());
        failures.add(id + ": " + failure);
    }

    private static String classHash(final Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing loaded class bytes: " + type);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }

    private void write(final boolean finished) throws Exception {
        if (evidence == null) return;
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", finished);
        report.put("selected_scenarios_passed", finished && failures.isEmpty());
        report.put("all_censused_aliases_passed", finished && !results.isEmpty()
            && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("started_at", started);
        report.put("updated_at", System.currentTimeMillis());
        report.put("execution", "Rendered Fabric development-classpath client. Actual native potion throws and normal collision/world/entity behavior; "
            + "passive end-of-server-tick observations. Fresh staged worlds. No direct impact/damage/world-effect invocation.");
        report.put("class_sha256", Map.of("test", classHash(BrewCombatAbilitiesClientAcceptance.class),
            "brew_item", classHash(BrewItem.class), "brew_runtime", classHash(BrewRuntime.class)));
        report.put("family_behavior_census", COVERED.stream().map(Enum::name).sorted().toList());
        report.put("registered_item_count", results.size());
        report.put("items", results);
        report.put("failures", failures);
        report.put("screenshots", screenshots);
        final Path temporary = evidence.resolve("brew-combat-abilities.json.tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temporary, evidence.resolve("brew-combat-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
