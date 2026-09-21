package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.brew.BrewBehavior;
import com.kadamitas.warlockery.brew.BrewItem;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.brew.BrewMarkerKind;
import com.kadamitas.warlockery.brew.BrewMarkerState;
import com.kadamitas.warlockery.brew.BrewRuntime;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.ritual.hex.HexKind;
import com.kadamitas.warlockery.ritual.hex.HexState;
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
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Rendered native acceptance for the advanced marker, hex, contagion and death-contract brews. */
public final class BrewAdvancedAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final Set<BrewBehavior> COVERED = Set.of(
        BrewBehavior.APPLY_ABSORB_MAGIC, BrewBehavior.APPLY_ATTRACT_ARROWS,
        BrewBehavior.APPLY_CURSED_LEAPING, BrewBehavior.APPLY_DISEASE,
        BrewBehavior.APPLY_ENDER_INHIBITION, BrewBehavior.APPLY_GAS_IMMUNITY,
        BrewBehavior.APPLY_INFECTION, BrewBehavior.APPLY_INSANITY,
        BrewBehavior.APPLY_KEEP_EFFECTS, BrewBehavior.APPLY_KEEP_INVENTORY,
        BrewBehavior.APPLY_NIGHTMARE, BrewBehavior.APPLY_POISON_WEAPON,
        BrewBehavior.APPLY_REFLECT_ARROWS, BrewBehavior.APPLY_REFLECT_DAMAGE,
        BrewBehavior.APPLY_REINCARNATE, BrewBehavior.APPLY_REPEL_ATTACKER,
        BrewBehavior.APPLY_SLEEPING, BrewBehavior.APPLY_SUNLIGHT_CURSE,
        BrewBehavior.APPLY_TINT_SKIN, BrewBehavior.APPLY_VOLATILITY,
        BrewBehavior.APPLY_WEREWOLF_LOCK);
    private static final Set<BrewBehavior> DEATH_BRANCH_BLOCKED = Set.of();
    private static final Set<BrewBehavior> FULL_OUTCOME_OBSERVED = Set.of(
        BrewBehavior.APPLY_ABSORB_MAGIC, BrewBehavior.APPLY_ATTRACT_ARROWS,
        BrewBehavior.APPLY_CURSED_LEAPING, BrewBehavior.APPLY_DISEASE,
        BrewBehavior.APPLY_ENDER_INHIBITION, BrewBehavior.APPLY_GAS_IMMUNITY,
        BrewBehavior.APPLY_INFECTION, BrewBehavior.APPLY_INSANITY,
        BrewBehavior.APPLY_KEEP_EFFECTS, BrewBehavior.APPLY_KEEP_INVENTORY,
        BrewBehavior.APPLY_NIGHTMARE, BrewBehavior.APPLY_POISON_WEAPON,
        BrewBehavior.APPLY_REFLECT_ARROWS, BrewBehavior.APPLY_REFLECT_DAMAGE,
        BrewBehavior.APPLY_REINCARNATE, BrewBehavior.APPLY_REPEL_ATTACKER,
        BrewBehavior.APPLY_SLEEPING, BrewBehavior.APPLY_SUNLIGHT_CURSE, BrewBehavior.APPLY_TINT_SKIN,
        BrewBehavior.APPLY_VOLATILITY, BrewBehavior.APPLY_WEREWOLF_LOCK);
    private static final AABB AREA = new AABB(-12, 97, -12, 13, 116, 13);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();
    private volatile ThrowObservation observation;
    private TestSingleplayerContext world;
    private Path evidence;
    private long started;

    @Override
    public void runTest(final ClientGameTestContext context) {
        started = System.currentTimeMillis();
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("brew-advanced-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final ThrowObservation current = observation;
                if (current != null && current.player.level().getServer() == server) current.tick();
            });
            final List<Identifier> ids = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals("warlockery"))
                .filter(id -> BuiltInRegistries.ITEM.getValue(id) instanceof BrewItem item
                    && item.kind().behaviors().stream().anyMatch(COVERED::contains))
                .sorted().toList();
            check(!ids.isEmpty(), "Registry must expose advanced brew aliases");
            final Set<BrewBehavior> found = ids.stream()
                .flatMap(id -> ((BrewItem) BuiltInRegistries.ITEM.getValue(id)).kind().behaviors().stream())
                .filter(COVERED::contains).collect(Collectors.toSet());
            check(found.containsAll(COVERED), "Registry census must contain all 21 assigned advanced families; missing="
                + COVERED.stream().filter(behavior -> !found.contains(behavior)).toList());
            final String configured = System.getProperty("warlockery.brewAdvancedIds", "");
            final Set<String> selected = configured.isBlank()
                ? ids.stream().map(Identifier::toString).collect(Collectors.toSet())
                : Arrays.stream(configured.split(",")).map(String::strip)
                    .map(id -> id.contains(":") ? id : "warlockery:" + id).collect(Collectors.toSet());
            check(!selected.isEmpty() && ids.stream().map(Identifier::toString).collect(Collectors.toSet()).containsAll(selected),
                "Selected IDs must belong to the advanced-family registry census");
            for (Identifier id : ids) {
                final BrewKind kind = kind(id);
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", selected.contains(id.toString()) ? "NOT_RUN" : "NOT_SELECTED");
                row.put("kind", kind.id());
                row.put("behaviors", kind.behaviors().stream().map(Enum::name).toList());
                row.put("assigned_behaviors", kind.behaviors().stream().filter(COVERED::contains).map(Enum::name).toList());
                row.put("canonical_guide", "brew_entry_" + kind.id());
                results.put(id.toString(), row);
            }
            write(false);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (Identifier id : ids) {
                if (!selected.contains(id.toString())) continue;
                final Map<String, Object> row = results.get(id.toString());
                row.put("status", "RUNNING");
                write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        runBrew(context, id, row);
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        row.put("status", "FAILED");
                        row.put("failure", failure.toString());
                        final java.io.StringWriter stack = new java.io.StringWriter();
                        failure.printStackTrace(new java.io.PrintWriter(stack));
                        row.put("failure_stack", stack.toString());
                        failures.add(id + ": " + failure);
                        try { screenshot(context, id.getPath() + "-failure"); }
                        catch (Throwable capture) { row.put("screenshot_failure", capture.toString()); }
                    } finally {
                        observation = null;
                        row.put("finished_at", System.currentTimeMillis());
                        write(false);
                    }
                } finally {
                    observation = null;
                    world = null;
                }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_BREW_ADVANCED_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Native advanced brew evidence: " + evidence, failure);
        }
    }

    private void runBrew(final ClientGameTestContext context, final Identifier id,
        final Map<String, Object> row) throws Exception {
        final BrewKind kind = kind(id);
        server(player -> reset(player, kind));
        world.getConnection().waitForChunksRender();
        world.getConnection().waitForClientboundPackets();
        readBook(context, id, kind, row);
        server(player -> {
            final Entity target = createTarget(player, kind);
            player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
            observation = new ThrowObservation(player, id, target == null ? null : target.getUUID());
        });
        row.put("staged_prerequisites", "Fresh Survival world; supplied brew; open bedrock arena; one eligible real entity where required. Absorb Magic and Volatility give the actor 100 starting health so their damaging triggers can be observed without death; damage remains enabled. "
            + "Disease stages a clean recipient only after collision, gas immunity stages gas and Poison, infection stages a villager and infectable stone, "
            + "and sunlight curse stages daytime open sky. Outcomes come from native use, projectile collision and normal ticks.");
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(90); });
        context.waitTicks(2);
        screenshot(context, id.getPath() + "-before-native-throw");
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        for (int tick = 0; tick < 100 && !serverValue(player -> observation.impacted); tick++) context.waitTicks(1);
        check(serverValue(player -> observation.projectileIds.size() == 1 && observation.impacted),
            "Exactly one owned potion must fly and complete its native collision");
        check(serverValue(player -> player.getMainHandItem().isEmpty()), "Native use must consume exactly one brew");
        afterImpactSetup(context, kind, row);
        context.waitTicks(45);
        final Map<String, Object> checks = serverValue(player -> verify(player, kind, observation.targetId, observation.maxTargetY));
        row.put("native_checks", checks);
        row.put("throw_observation", serverValue(player -> observation.report()));
        final List<String> blocked = kind.behaviors().stream().filter(DEATH_BRANCH_BLOCKED::contains)
            .map(behavior -> behavior + ": death/respawn or replacement-body continuation is BLOCKED_NOT_RUN; this case proves native application and persistence marker only")
            .toList();
        row.put("blocked_branches", blocked);
        final List<String> markerOnly = kind.behaviors().stream().filter(COVERED::contains)
            .filter(behavior -> !FULL_OUTCOME_OBSERVED.contains(behavior) && !DEATH_BRANCH_BLOCKED.contains(behavior))
            .map(behavior -> behavior + ": native application/persistence proved; its triggered downstream branch remains NOT_RUN")
            .toList();
        row.put("not_run_triggered_branches", markerOnly);
        row.put("ability_complete", blocked.isEmpty() && markerOnly.isEmpty()
            && kind.behaviors().stream().filter(COVERED::contains).allMatch(FULL_OUTCOME_OBSERVED::contains));
        row.put("remaining", blocked.isEmpty() && markerOnly.isEmpty()
            ? "Acquisition, delivery variants, multiplayer and behavior families outside this assigned census."
            : "Explicitly listed triggered/death branches remain unclaimed; no blanket ability pass is recorded.");
        screenshot(context, id.getPath() + "-after-native-outcome");
    }

    private static void reset(final ServerPlayer player, final BrewKind kind) {
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.removeAllEffects();
        if (kind.behaviors().contains(BrewBehavior.APPLY_ABSORB_MAGIC)
            || kind.behaviors().contains(BrewBehavior.APPLY_VOLATILITY)
            || kind.behaviors().contains(BrewBehavior.APPLY_SLEEPING)) {
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        }
        player.setHealth(player.getMaxHealth());
        player.getAbilities().invulnerable = false;
        player.onUpdateAbilities();
        final var server = player.level().getServer();
        server.setDifficulty(Difficulty.NORMAL, true);
        player.level().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
        player.level().getGameRules().set(GameRules.SPAWN_MOBS, false, server);
        player.level().getGameRules().set(GameRules.KEEP_INVENTORY, false, server);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
            kind.behaviors().contains(BrewBehavior.APPLY_SUNLIGHT_CURSE) ? "time set 6000" : "time set 18000");
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-12, 98, -12), new BlockPos(12, 112, 12)))
            player.level().setBlockAndUpdate(pos, pos.getY() <= 99 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
        player.teleportTo(0.5, 100, 0.5);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static Entity createTarget(final ServerPlayer player, final BrewKind kind) {
        final BrewBehavior assigned = kind.behaviors().stream().filter(COVERED::contains).findFirst().orElseThrow();
        if (assigned == BrewBehavior.APPLY_GAS_IMMUNITY) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 1200, 0));
            player.level().setBlockAndUpdate(player.blockPosition(), BuiltInRegistries.BLOCK.getValue(Identifier.parse("warlockery:brewgas")).defaultBlockState());
            return player;
        }
        if (assigned == BrewBehavior.APPLY_SLEEPING || assigned == BrewBehavior.APPLY_WEREWOLF_LOCK
            || assigned == BrewBehavior.APPLY_ENDER_INHIBITION || assigned == BrewBehavior.APPLY_KEEP_INVENTORY
            || assigned == BrewBehavior.APPLY_KEEP_EFFECTS || assigned == BrewBehavior.APPLY_POISON_WEAPON
            || assigned == BrewBehavior.APPLY_REFLECT_DAMAGE || assigned == BrewBehavior.APPLY_REPEL_ATTACKER
            || assigned == BrewBehavior.APPLY_ABSORB_MAGIC || assigned == BrewBehavior.APPLY_ATTRACT_ARROWS
            || assigned == BrewBehavior.APPLY_REFLECT_ARROWS) return player;
        final String type = assigned == BrewBehavior.APPLY_INFECTION ? "minecraft:villager"
            : assigned == BrewBehavior.APPLY_SUNLIGHT_CURSE ? "minecraft:zombie" : "minecraft:cow";
        final Entity target = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(type)).create(player.level(), EntitySpawnReason.COMMAND);
        check(target instanceof Mob, "Advanced target must be a real mob: " + type);
        final Mob mob = (Mob) target;
        mob.setPos(1.7, 100, 0.5);
        mob.setNoAi(assigned != BrewBehavior.APPLY_CURSED_LEAPING);
        mob.setPersistenceRequired();
        if (assigned == BrewBehavior.APPLY_VOLATILITY) {
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
            mob.setHealth(100.0F);
        }
        if (assigned == BrewBehavior.APPLY_REINCARNATE) mob.setHealth(1.0F);
        player.level().addFreshEntity(mob);
        if (assigned == BrewBehavior.APPLY_INFECTION) {
            player.level().setBlockAndUpdate(new BlockPos(1, 100, 2), Blocks.STONE.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(2, 100, 2), Blocks.COBBLESTONE.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(-1, 100, 2), Blocks.STONE_BRICKS.defaultBlockState());
        }
        return mob;
    }

    private void afterImpactSetup(final ClientGameTestContext context, final BrewKind kind,
        final Map<String, Object> row) {
        if (kind.behaviors().contains(BrewBehavior.APPLY_SLEEPING)) {
            for (int tick = 0; tick < 80
                && !serverValue(player -> com.kadamitas.warlockery.dream.SpiritWorldRuntime.isDreaming(player)); tick++) context.waitTicks(1);
            check(serverValue(player -> com.kadamitas.warlockery.dream.SpiritWorldRuntime.isDreaming(player)),
                "Native Sleeping Brew must enter the actual Spirit World");
            context.waitTicks(100);
            check(serverValue(player -> com.kadamitas.warlockery.dream.SpiritWorldRuntime.isDreaming(player)
                && com.kadamitas.warlockery.dream.SpiritWorldRuntime.isSpiritWorld(player.level())),
                "Dream must remain active beyond a body chunk ticket lifetime after leaving the source dimension");
            final var dream = serverValue(player -> com.kadamitas.warlockery.dream.SpiritWorldState.read(player).orElseThrow());
            server(player -> {
                final var source = player.level().getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dream.sourceDimension()));
                final Entity body = source.getEntity(dream.body());
                check(body != null && body.isAlive()
                    && com.kadamitas.warlockery.dream.SpiritWorldRuntime.isSleepingBody(body),
                    "The exact source sleeping body must stay alive and linked through normal cross-dimension ticks");
                final BlockPos dispenser = body.blockPosition().south(4);
                source.setBlockAndUpdate(dispenser, Blocks.DISPENSER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.DispenserBlock.FACING, net.minecraft.core.Direction.NORTH));
                final var inventory = (net.minecraft.world.level.block.entity.DispenserBlockEntity) source.getBlockEntity(dispenser);
                inventory.setItem(0, new ItemStack(Items.ARROW));
                source.setBlockAndUpdate(dispenser.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            });
            for (int tick = 0; tick < 80
                && serverValue(player -> com.kadamitas.warlockery.dream.SpiritWorldRuntime.isDreaming(player)); tick++) context.waitTicks(1);
            row.put("body_attack_observation", serverValue(player -> {
                final var source = player.level().getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dream.sourceDimension()));
                final Entity body = source.getEntity(dream.body());
                return Map.of("dreaming", com.kadamitas.warlockery.dream.SpiritWorldRuntime.isDreaming(player),
                    "player_dimension", player.level().dimension().identifier().toString(),
                    "source_dimension", dream.sourceDimension().toString(), "body_present", body != null,
                    "body_alive", body != null && body.isAlive(), "arrows", source.getEntitiesOfClass(Arrow.class, AREA).stream()
                        .map(arrow -> arrow.position() + " velocity=" + arrow.getDeltaMovement() + " removed=" + arrow.isRemoved()).toList());
            }));
            check(serverValue(player -> !com.kadamitas.warlockery.dream.SpiritWorldRuntime.isDreaming(player)
                && player.level().dimension().identifier().equals(dream.sourceDimension())
                && player.level().getEntity(dream.body()) == null),
                "Actual source-world arrow collision destroys the body and wakes the dreamer back in the original dimension");
            row.put("triggered_outcome", "Native brew entered Spirit World, retained its live source body for 100 normal ticks, then a stocked and powered dispenser fired a real arrow that destroyed the body and triggered return travel");
        }
        if (kind.behaviors().contains(BrewBehavior.APPLY_DISEASE)) server(player -> {
            final Cow recipient = (Cow) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:cow"))
                .create(player.level(), EntitySpawnReason.COMMAND);
            recipient.setPos(3.0, 100, 0.5);
            recipient.setNoAi(true);
            recipient.setPersistenceRequired();
            recipient.setCustomName(Component.literal("clean-recipient"));
            player.level().addFreshEntity(recipient);
        });
        if (kind.behaviors().contains(BrewBehavior.APPLY_WEREWOLF_LOCK)) server(player ->
            com.kadamitas.warlockery.transformation.SupernaturalState.setForm(player,
                com.kadamitas.warlockery.transformation.SupernaturalForm.VAMPIRE));
        if (kind.behaviors().contains(BrewBehavior.APPLY_ENDER_INHIBITION)) {
            final Vec3 before = serverValue(ServerPlayer::position);
            server(player -> {
                player.getInventory().setItem(0, new ItemStack(Items.CHORUS_FRUIT));
                player.getInventory().setSelectedSlot(0);
                player.inventoryMenu.broadcastChanges();
            });
            try {
                world.getConnection().waitForClientboundPackets();
                context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
                context.waitTicks(40);
            } finally { context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); }
            final Vec3 after = serverValue(ServerPlayer::position);
            check(before.distanceToSqr(after) < 0.05, "Ender Inhibition must cancel a normally consumed chorus-fruit teleport");
            check(serverValue(player -> player.getMainHandItem().isEmpty()), "Chorus fruit must be consumed by native use");
            row.put("triggered_outcome", Map.of("chorus_position_before", before, "chorus_position_after", after));
        }
        if (kind.behaviors().contains(BrewBehavior.APPLY_POISON_WEAPON)) {
            final UUID victim = spawnCow(2.6, 100, 0.5, 20.0F);
            nativeHit(context, victim);
            check(serverValue(player -> ((Cow) player.level().getEntity(victim)).hasEffect(MobEffects.POISON)),
                "A native unarmed attack by the marked player must poison its victim");
            row.put("triggered_outcome", "native player attack applied Poison to a clean cow");
        }
        if (kind.behaviors().contains(BrewBehavior.APPLY_ABSORB_MAGIC)) {
            server(player -> {
                final Evoker evoker = (Evoker) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:evoker"))
                    .create(player.level(), EntitySpawnReason.COMMAND);
                evoker.setPos(6.0, 100, 0.5); evoker.setPersistenceRequired(); evoker.setTarget(player);
                player.level().addFreshEntity(evoker);
            });
            for (int tick = 0; tick < 240
                && serverValue(player -> BrewMarkerState.absorbedMagic(player) == 0); tick++) context.waitTicks(1);
            check(serverValue(player -> BrewMarkerState.absorbedMagic(player) > 0),
                "A normal evoker magical attack must create stored absorbed magic");
            row.put("triggered_outcome", Map.of("normal_evoker_magic_absorbed",
                serverValue(player -> BrewMarkerState.absorbedMagic(player))));
        }
        if (kind.behaviors().contains(BrewBehavior.APPLY_KEEP_EFFECTS)
            || kind.behaviors().contains(BrewBehavior.APPLY_KEEP_INVENTORY)) {
            final boolean effects = kind.behaviors().contains(BrewBehavior.APPLY_KEEP_EFFECTS);
            server(player -> {
                player.setHealth(1.0F);
                if (effects) player.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200, 1));
                else player.getInventory().setItem(9, new ItemStack(Items.DIAMOND));
                final Zombie killer = (Zombie) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:zombie"))
                    .create(player.level(), EntitySpawnReason.COMMAND);
                killer.setPos(1.8, 100, 0.5); killer.setPersistenceRequired(); killer.setTarget(player);
                player.level().addFreshEntity(killer);
            });
            context.waitForScreen(net.minecraft.client.gui.screens.DeathScreen.class);
            context.waitFor(client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen screen
                && screen.children().stream().anyMatch(child -> child instanceof net.minecraft.client.gui.components.Button button
                    && button.active && button.getMessage().getString().equals(Component.translatable("deathScreen.respawn").getString())), 80);
            ManualClientAcceptance.clickButton(context, Component.translatable("deathScreen.respawn").getString());
            context.waitFor(client -> client.gui.screen() == null && client.player != null && client.player.isAlive(), 80);
            world.getConnection().waitForClientboundPackets();
            if (effects) {
                check(serverValue(player -> player.hasEffect(MobEffects.SPEED)
                    && player.getEffect(MobEffects.SPEED).getAmplifier() == 1),
                    "Keep Effects must restore staged Speed II through a real death and client respawn");
                row.put("triggered_outcome", "normal hostile death and client respawn restored Speed II");
            } else {
                check(serverValue(player -> player.getInventory().contains(new ItemStack(Items.DIAMOND))),
                    "Keep Inventory must restore the staged diamond through real drops and client respawn");
                row.put("triggered_outcome", "normal hostile death and client respawn restored dropped diamond");
            }
        }
        if (kind.behaviors().contains(BrewBehavior.APPLY_REFLECT_DAMAGE)
            || kind.behaviors().contains(BrewBehavior.APPLY_REPEL_ATTACKER)) {
            final UUID attacker = serverValue(player -> {
                final Zombie zombie = (Zombie) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:zombie"))
                    .create(player.level(), EntitySpawnReason.COMMAND);
                zombie.setPos(2.4, 100, 0.5); zombie.setPersistenceRequired(); zombie.setTarget(player);
                player.level().addFreshEntity(zombie);
                return zombie.getUUID();
            });
            final float attackerHealth = serverValue(player -> ((Zombie) player.level().getEntity(attacker)).getHealth());
            final float playerHealth = serverValue(ServerPlayer::getHealth);
            final double initialDistance = serverValue(player -> player.distanceToSqr(player.level().getEntity(attacker)));
            for (int tick = 0; tick < 100 && serverValue(player -> player.getHealth() == playerHealth); tick++) context.waitTicks(1);
            check(serverValue(player -> player.getHealth() < playerHealth), "A normal hostile AI attack must reach the brewed player");
            if (kind.behaviors().contains(BrewBehavior.APPLY_REFLECT_DAMAGE)) {
                check(serverValue(player -> ((Zombie) player.level().getEntity(attacker)).getHealth() < attackerHealth),
                    "Reflect Damage must return actual health loss to the normal attacker");
                row.put("triggered_outcome", "normal zombie melee damaged player and reflected damage to zombie");
            } else {
                context.waitTicks(4);
                check(serverValue(player -> player.distanceToSqr(player.level().getEntity(attacker)) > initialDistance),
                    "Repel Attacker must push the normal melee attacker farther from the player");
                row.put("triggered_outcome", "normal zombie melee triggered observable outward knockback");
            }
        }
        if (kind.behaviors().contains(BrewBehavior.APPLY_VOLATILITY)
            || kind.behaviors().contains(BrewBehavior.APPLY_REINCARNATE)) {
            final UUID original = observation.targetId;
            final Vec3 origin = serverValue(player -> player.level().getEntity(original).position());
            final UUID neighbor = kind.behaviors().contains(BrewBehavior.APPLY_VOLATILITY)
                ? spawnCow(origin.x + 1.2, origin.y, origin.z, 20.0F) : null;
            nativeHit(context, original);
            if (kind.behaviors().contains(BrewBehavior.APPLY_VOLATILITY)) {
                check(serverValue(player -> !BrewMarkerState.isActive((Cow) player.level().getEntity(original), BrewMarkerKind.VOLATILITY)),
                    "First real damage must consume Volatility");
                check(serverValue(player -> ((Cow) player.level().getEntity(neighbor)).getHealth() < 20.0F),
                    "Volatility must produce a real non-terrain explosion that damages a neighbor");
                row.put("triggered_outcome", "native player hit consumed marker and explosion damaged neighbor");
            } else {
                for (int tick = 0; tick < 30 && serverValue(player -> player.level().getEntity(original) != null); tick++) context.waitTicks(1);
                check(serverValue(player -> player.level().getEntity(original) == null), "Native lethal hit must remove original animal");
                check(serverValue(player -> !player.level().getEntitiesOfClass(Animal.class,
                    new AABB(origin, origin).inflate(2.0), animal -> !animal.getUUID().equals(original)).isEmpty()),
                    "Reincarnation death hook must create a real replacement animal at the death site");
                row.put("triggered_outcome", "native lethal player attack removed original and created replacement animal");
            }
        }
        if (kind.behaviors().contains(BrewBehavior.APPLY_ATTRACT_ARROWS)
            || kind.behaviors().contains(BrewBehavior.APPLY_REFLECT_ARROWS)) {
            final boolean reflect = kind.behaviors().contains(BrewBehavior.APPLY_REFLECT_ARROWS);
            final UUID arrow = spawnArrowAtPlayer(reflect);
            final float beforeHealth = serverValue(ServerPlayer::getHealth);
            final Vec3 before = serverValue(player -> player.level().getEntity(arrow).getDeltaMovement());
            context.waitTicks(reflect ? 7 : 4);
            if (reflect) {
                check(serverValue(player -> player.level().getEntity(arrow) instanceof Arrow shot && shot.getOwner() == player),
                    "Real arrow collision must reverse ownership to the reflecting player");
                check(serverValue(player -> player.getHealth() == beforeHealth),
                    "The reflected arrow must not continue damaging the player after cancellation");
                row.put("triggered_outcome", "normal arrow collision reflected ownership to brewed player");
            } else {
                final Vec3 after = serverValue(player -> player.level().getEntity(arrow).getDeltaMovement());
                check(after.subtract(before).lengthSqr() > 0.0001, "Normal ticks must bend the staged live arrow toward the brewed player");
                row.put("triggered_outcome", Map.of("arrow_velocity_before", before, "arrow_velocity_after", after));
            }
        }
    }

    private UUID spawnCow(final double x, final double y, final double z, final float health) {
        return serverValue(player -> {
            final Cow cow = (Cow) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:cow"))
                .create(player.level(), EntitySpawnReason.COMMAND);
            cow.setPos(x, y, z); cow.setNoAi(true); cow.setHealth(health); cow.setPersistenceRequired();
            player.level().addFreshEntity(cow);
            return cow.getUUID();
        });
    }

    private void nativeHit(final ClientGameTestContext context, final UUID target) {
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(12);
        final Vec3 point = serverValue(player -> player.level().getEntity(target).getBoundingBox().getCenter());
        look(context, point);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(4);
    }

    private UUID spawnArrowAtPlayer(final boolean directHit) {
        return serverValue(player -> {
            final Skeleton shooter = (Skeleton) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:skeleton"))
                .create(player.level(), EntitySpawnReason.COMMAND);
            shooter.setPos(directHit ? 0.5 : -5.0, 100, directHit ? -5.0 : -3.5);
            shooter.setNoAi(true); player.level().addFreshEntity(shooter);
            final Arrow arrow = new Arrow(player.level(), shooter, Items.ARROW.getDefaultInstance(), null);
            arrow.setPos(directHit ? 0.5 : -4.5, 101.2, directHit ? -4.5 : -3.5);
            arrow.shoot(directHit ? 0.0 : 1.0, 0.0, directHit ? 1.0 : 0.0, 1.2F, 0.0F);
            player.level().addFreshEntity(arrow);
            return arrow.getUUID();
        });
    }

    private static void look(final ClientGameTestContext context, final Vec3 target) {
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(2);
    }

    private static Map<String, Object> verify(final ServerPlayer player, final BrewKind kind, final UUID targetId,
        final double maxTargetY) {
        final Map<String, Object> checks = new LinkedHashMap<>();
        final Entity target = targetId == null ? null : player.level().getEntity(targetId);
        for (BrewBehavior behavior : kind.behaviors().stream().filter(COVERED::contains).toList()) {
            switch (behavior) {
                case APPLY_INSANITY -> {
                    final var living = (net.minecraft.world.entity.LivingEntity) target;
                    check(HexState.isActive(living, HexKind.INSANITY), "Insanity hex must be active after native impact");
                    check(living.hasEffect(MobEffects.NAUSEA) && living.hasEffect(MobEffects.DARKNESS),
                        "Insanity must produce its visible Nausea and Darkness outcome");
                    checks.put(behavior.name(), "active insanity hex with Nausea and Darkness");
                }
                case APPLY_NIGHTMARE -> {
                    final var living = (net.minecraft.world.entity.LivingEntity) target;
                    check(HexState.isActive(living, HexKind.WAKING_NIGHTMARE), "Waking Nightmare hex must be active");
                    check(living.hasEffect(MobEffects.DARKNESS) && living.hasEffect(MobEffects.HUNGER),
                        "Nightmare must produce Darkness and Hunger");
                    checks.put(behavior.name(), "active waking-nightmare hex with Darkness and Hunger");
                }
                case APPLY_INFECTION -> {
                    check(target == null || target.isRemoved(), "Infected villager must be replaced by native conversion");
                    check(!player.level().getEntitiesOfClass(ZombieVillager.class, AREA).isEmpty(),
                        "Native infection must create a zombie villager");
                    check(player.level().getBlockState(new BlockPos(1, 100, 2)).is(Blocks.INFESTED_STONE)
                        && player.level().getBlockState(new BlockPos(2, 100, 2)).is(Blocks.INFESTED_COBBLESTONE)
                        && player.level().getBlockState(new BlockPos(-1, 100, 2)).is(Blocks.INFESTED_STONE_BRICKS),
                        "Infection must convert each staged vulnerable stone family");
                    checks.put(behavior.name(), "villager converted and three vulnerable stone families infested");
                }
                case APPLY_DISEASE -> {
                    final Cow recipient = player.level().getEntitiesOfClass(Cow.class, AREA,
                        cow -> cow.hasCustomName() && "clean-recipient".equals(cow.getCustomName().getString())).stream().findFirst().orElseThrow();
                    check(BrewMarkerState.isActive(recipient, BrewMarkerKind.DISEASE)
                        && recipient.hasEffect(MobEffects.POISON) && recipient.hasEffect(MobEffects.WEAKNESS),
                        "Disease must spread under normal ticks to a clean nearby recipient");
                    checks.put(behavior.name(), "normal contagion reached a post-impact clean recipient");
                }
                case APPLY_GAS_IMMUNITY -> {
                    check(BrewMarkerState.isActive(player, BrewMarkerKind.BREW_GAS_IMMUNITY), "Gas immunity marker must be active");
                    check(!player.hasEffect(MobEffects.POISON), "Tagged brew gas contact must clear the staged harmful effect");
                    checks.put(behavior.name(), "tagged gas contact removed Poison while immunity remained active");
                }
                case APPLY_SUNLIGHT_CURSE -> {
                    final Zombie zombie = (Zombie) target;
                    check(BrewMarkerState.isActive(zombie, BrewMarkerKind.SUNLIGHT_CURSE), "Sunlight curse marker must remain active");
                    check(zombie.getRemainingFireTicks() > 0 && zombie.getHealth() < zombie.getMaxHealth(),
                        "Open daytime sky must ignite and damage the cursed undead");
                    checks.put(behavior.name(), "open daytime sky ignited and damaged cursed undead");
                }
                case APPLY_TINT_SKIN -> {
                    final var living = (net.minecraft.world.entity.LivingEntity) target;
                    check(BrewMarkerState.isActive(living, BrewMarkerKind.TINT_SKIN) && living.hasEffect(MobEffects.GLOWING),
                        "Tint Skin must persist and visibly outline the target");
                    checks.put(behavior.name(), "tint marker and visible Glowing effect active");
                }
                case APPLY_CURSED_LEAPING -> {
                    final var living = (net.minecraft.world.entity.LivingEntity) target;
                    check(BrewMarkerState.isActive(living, BrewMarkerKind.CURSED_LEAPING), "Cursed Leaping marker must remain active");
                    check(maxTargetY > 100.4, "Normal cadence must force at least one actual leap");
                    checks.put(behavior.name(), "marker remained active and passive observation recorded a forced leap");
                }
                case APPLY_SLEEPING -> {
                    check(!com.kadamitas.warlockery.dream.SpiritWorldRuntime.isDreaming(player),
                        "The separately observed source-body destruction must leave the dream session closed");
                    checks.put(behavior.name(), "native entry, sustained cross-dimension body lifetime, real projectile body destruction and return travel");
                }
                case APPLY_WEREWOLF_LOCK -> {
                    check(com.kadamitas.warlockery.transformation.SupernaturalState.getForm(player)
                        == com.kadamitas.warlockery.transformation.SupernaturalForm.NONE,
                        "Werewolf Lock must restore the form captured by the native impact after a staged form change");
                    checks.put(behavior.name(), "staged Vampire form was restored to locked pre-impact NONE form by normal tick");
                }
                case APPLY_VOLATILITY -> {
                    check(target instanceof net.minecraft.world.entity.LivingEntity living
                        && !BrewMarkerState.isActive(living, BrewMarkerKind.VOLATILITY),
                        "Volatility must remain consumed after the independently observed damaging explosion");
                    checks.put(behavior.name(), "native player attack consumed the marker and the real explosion damaged a neighbor");
                }
                case APPLY_ABSORB_MAGIC, APPLY_ENDER_INHIBITION, APPLY_POISON_WEAPON, APPLY_ATTRACT_ARROWS,
                    APPLY_REFLECT_ARROWS, APPLY_REFLECT_DAMAGE, APPLY_REPEL_ATTACKER -> {
                    final var living = (net.minecraft.world.entity.LivingEntity) target;
                    final BrewMarkerKind marker = marker(behavior);
                    check(BrewMarkerState.isActive(living, marker), behavior + " marker must remain active after its asserted trigger");
                    checks.put(behavior.name(), "native marker plus separately recorded real triggered outcome");
                }
                case APPLY_REINCARNATE -> checks.put(behavior.name(),
                    "native lethal hit removed original and normal death hook created replacement animal");
                case APPLY_KEEP_EFFECTS -> {
                    check(player.hasEffect(MobEffects.SPEED), "Speed must remain restored after client respawn");
                    checks.put(behavior.name(), "real death/clone flow restored a staged effect");
                }
                case APPLY_KEEP_INVENTORY -> {
                    check(player.getInventory().contains(new ItemStack(Items.DIAMOND)), "Diamond must remain restored after client respawn");
                    checks.put(behavior.name(), "real drops/clone flow restored a staged inventory item");
                }
                default -> {
                    final var living = (net.minecraft.world.entity.LivingEntity) target;
                    final BrewMarkerKind marker = marker(behavior);
                    check(marker != null && BrewMarkerState.isActive(living, marker), behavior + " must apply its persistent marker");
                    checks.put(behavior.name(), DEATH_BRANCH_BLOCKED.contains(behavior)
                        ? "native marker applied; downstream death/clone branch BLOCKED_NOT_RUN"
                        : "native persistent marker applied and remained active through 45 normal ticks");
                }
            }
        }
        return checks;
    }

    private static BrewMarkerKind marker(final BrewBehavior behavior) {
        return switch (behavior) {
            case APPLY_GAS_IMMUNITY -> BrewMarkerKind.BREW_GAS_IMMUNITY;
            case APPLY_TINT_SKIN -> BrewMarkerKind.TINT_SKIN;
            case APPLY_SUNLIGHT_CURSE -> BrewMarkerKind.SUNLIGHT_CURSE;
            case APPLY_ABSORB_MAGIC -> BrewMarkerKind.ABSORB_MAGIC;
            case APPLY_ATTRACT_ARROWS -> BrewMarkerKind.ATTRACT_ARROWS;
            case APPLY_CURSED_LEAPING -> BrewMarkerKind.CURSED_LEAPING;
            case APPLY_DISEASE -> BrewMarkerKind.DISEASE;
            case APPLY_ENDER_INHIBITION -> BrewMarkerKind.ENDER_INHIBITION;
            case APPLY_KEEP_EFFECTS -> BrewMarkerKind.KEEP_EFFECTS;
            case APPLY_KEEP_INVENTORY -> BrewMarkerKind.KEEP_INVENTORY;
            case APPLY_POISON_WEAPON -> BrewMarkerKind.POISON_WEAPON;
            case APPLY_REFLECT_ARROWS -> BrewMarkerKind.REFLECT_ARROWS;
            case APPLY_REFLECT_DAMAGE -> BrewMarkerKind.REFLECT_DAMAGE;
            case APPLY_REINCARNATE -> BrewMarkerKind.REINCARNATE;
            case APPLY_REPEL_ATTACKER -> BrewMarkerKind.REPEL_ATTACKER;
            case APPLY_SLEEPING -> BrewMarkerKind.SLEEPING;
            case APPLY_VOLATILITY -> BrewMarkerKind.VOLATILITY;
            case APPLY_WEREWOLF_LOCK -> BrewMarkerKind.WEREWOLF_LOCK;
            default -> null;
        };
    }

    private void readBook(final ClientGameTestContext context, final Identifier id, final BrewKind kind,
        final Map<String, Object> row) throws Exception {
        final String section = "brew_entry_" + kind.id();
        final ManualProfile profile = ManualProfile.profiles().stream().filter(value -> value.sections().contains(section))
            .findFirst().orElseThrow(() -> new AssertionError("Player books must index " + section));
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
        check(!text.isBlank(), "Canonical advanced-brew guide must be readable");
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
            screenshot(context, id.getPath() + "-book-" + page);
        }
        row.put("book", profile.id());
        row.put("book_pages_read", pages);
        row.put("book_text", text);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
    }

    private static BrewKind kind(final Identifier id) {
        return ((BrewItem) BuiltInRegistries.ITEM.getValue(id)).kind();
    }

    private static final class ThrowObservation {
        private final ServerPlayer player;
        private final Identifier item;
        private final UUID targetId;
        private final Set<String> projectileIds = new LinkedHashSet<>();
        private boolean impacted;
        private int ticks;
        private double maxTargetY;
        private ThrowObservation(final ServerPlayer player, final Identifier item, final UUID targetId) {
            this.player = player; this.item = item; this.targetId = targetId;
            final Entity target = targetId == null ? null : player.level().getEntity(targetId);
            this.maxTargetY = target == null ? Double.NEGATIVE_INFINITY : target.getY();
        }
        private void tick() {
            ticks++;
            final List<AbstractThrownPotion> projectiles = player.level().getEntitiesOfClass(AbstractThrownPotion.class, AREA,
                potion -> potion.getOwner() == player && potion.getItem().is(BuiltInRegistries.ITEM.getValue(item)));
            projectiles.forEach(potion -> projectileIds.add(potion.getUUID().toString()));
            if (!projectileIds.isEmpty() && projectiles.isEmpty()) impacted = true;
            final Entity target = targetId == null ? null : player.level().getEntity(targetId);
            if (target != null) maxTargetY = Math.max(maxTargetY, target.getY());
        }
        private Map<String, Object> report() {
            return Map.of("observed_server_ticks", ticks, "native_projectile_uuids", projectileIds,
                "impact_observed", impacted, "target_uuid", targetId == null ? "" : targetId.toString(),
                "maximum_target_y", maxTargetY);
        }
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
        report.put("all_death_clone_branches_passed", finished && failures.isEmpty());
        report.put("blocked_behavior_branches", DEATH_BRANCH_BLOCKED.stream().map(Enum::name).sorted().toList());
        report.put("started_at", started);
        report.put("updated_at", System.currentTimeMillis());
        report.put("execution", "Rendered Fabric client; named book pages, real native brew use and projectile collision, normal world ticks, passive observations. No BrewRuntime or outcome method invocation.");
        report.put("class_sha256", Map.of("test", classHash(BrewAdvancedAbilitiesClientAcceptance.class),
            "brew_item", classHash(BrewItem.class), "brew_runtime", classHash(BrewRuntime.class)));
        report.put("family_behavior_census", COVERED.stream().map(Enum::name).sorted().toList());
        report.put("registered_item_count", results.size());
        report.put("items", results);
        report.put("failures", failures);
        report.put("screenshots", screenshots);
        final Path temporary = evidence.resolve("brew-advanced-abilities.json.tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temporary, evidence.resolve("brew-advanced-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
