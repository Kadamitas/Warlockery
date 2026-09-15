package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.entity.CorpseEntity;
import com.kadamitas.warlockery.entity.CorpseRules;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.magic.InfernalPower;
import com.kadamitas.warlockery.magic.MagicConstructData;
import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathProfile;
import com.kadamitas.warlockery.magic.MagicPathRules.ActionKind;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Arcane Focus follow-ups: paired-player and cross-dimension Otherwhere recall, Grave Corpse directives,
 * timed-path expiry, reserve/recall persistence across a save-reload, lightning recharge and the automatic
 * path effects not covered by InfusionPassiveClientAcceptance. Only prerequisites are staged; native Focus input,
 * native walking and ordinary ticks produce every asserted outcome.
 */
public final class FocusFollowUpClientAcceptance implements FabricClientGameTest {
    private static final Vec3 START = new Vec3(0.5, 100, -2);
    private static final Vec3 TARGET = new Vec3(0.5, 100, 0.5);
    private static final BlockPos WORK = new BlockPos(2, 99, -1);
    private static final AABB AREA = new AABB(-22, 96, -22, 23, 118, 23);
    private static final String GRAVE_OWNER = "WarlockeryGraveOwner";
    private static final List<String> BOOKS = List.of("arcane_focus", "arcane_focus_targets", "infusion_passives", "ingredient_brew_grave", "infernal_sacrifices");
    private static final Map<String, String> CONTRACTS = new LinkedHashMap<>();
    private static final Map<String, List<String>> NOT_RUN = new LinkedHashMap<>();
    static {
        CONTRACTS.put("paired_recall", "Crouch-use on another player returns both players to the natively saved point");
        NOT_RUN.put("paired_recall", List.of("real second client transport", "target player refusal branches"));
        CONTRACTS.put("cross_dimension_recall", "Crouch-air recall from the Nether returns the player to the Overworld point saved natively");
        NOT_RUN.put("cross_dimension_recall", List.of("Spirit World recall entry", "recall into a missing dimension"));
        CONTRACTS.put("corpse_directives", "Block use refuses without an owned body, then natively bound Corpse receives exactly one Grave directive and walks toward the clicked position while the unbound control does not");
        NOT_RUN.put("corpse_directives", List.of("scan budget exhaustion", "sixty-four body snapshots", "expiry of the Grave bond"));
        CONTRACTS.put("grave_thrall_defends", "A natively bound undead answers the caster's recent native attack by targeting that creature");
        NOT_RUN.put("grave_thrall_defends", List.of("attacker retaliation branch", "two-hour bond expiry"));
        CONTRACTS.put("timed_infusion_expiry", "A timed path keeps its automatic effect and native cycling until it expires; afterwards only the permanent path answers native use");
        NOT_RUN.put("timed_infusion_expiry", List.of("Imp proximity renewal (covered elsewhere)", "two-hour brew durations"));
        CONTRACTS.put("reserve_persistence_reload", "Spent reserve, the active path and the natively saved recall point survive closing and reopening the world");
        NOT_RUN.put("reserve_persistence_reload", List.of("death persistence", "Infusion Recharge rite (ritual walkthrough)"));
        CONTRACTS.put("infernal_lightning_recharge", "A lightning strike refills Infernal reserve only while the Explosion power is held");
        NOT_RUN.put("infernal_lightning_recharge", List.of("natural storms"));
        CONTRACTS.put("path_refresh_effects", "Each active path renews its automatic effect during ordinary ticks while no path renews nothing");
        NOT_RUN.put("path_refresh_effects", List.of("effect expiry after losing the path"));
        CONTRACTS.put("earth_magnet", "Earth draws a loose ingot within six blocks toward the player while a farther ingot stays put");
        NOT_RUN.put("earth_magnet", List.of("chalk-heart offering exclusion"));
        CONTRACTS.put("infernal_web_climb", "With the Web power, native walking into a wall climbs it; without a path the wall stops the player");
        NOT_RUN.put("infernal_web_climb", List.of("spider sacrifice acquisition (covered by the Focus suite)"));
        CONTRACTS.put("infernal_leap_fall", "With the Leaping power an eight-block native fall causes no damage; the control is hurt");
        NOT_RUN.put("infernal_leap_fall", List.of("Flight power fall branch"));
    }
    private final Map<String, Map<String, Object>> cases = new LinkedHashMap<>();
    private final Map<String, Object> guides = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private String active;
    private UUID fixture;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("focus-follow-up").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (String id : CONTRACTS.keySet()) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", "NOT_RUN"); row.put("contract", CONTRACTS.get(id)); row.put("not_run", NOT_RUN.get(id));
                cases.put(id, row);
            }
            final String requested = System.getProperty("warlockery.focusFollowUpCases", "").trim();
            final Set<String> selected = requested.isEmpty() ? Set.copyOf(CONTRACTS.keySet())
                : Set.copyOf(Arrays.stream(requested.split(",")).map(String::trim).toList());
            check(CONTRACTS.keySet().containsAll(selected), "Only known follow-up cases may be selected: " + selected);
            write(false);
            world = context.worldBuilder().create();
            try {
                world.getConnection().waitForChunksRender();
                active = CONTRACTS.keySet().iterator().next();
                reset(context, null);
                for (String book : BOOKS) readBook(context, book);
                for (String id : CONTRACTS.keySet()) {
                    if (!selected.contains(id)) continue;
                    active = id;
                    cases.get(id).put("status", "RUNNING");
                    write(false);
                    try {
                        exercise(context, id);
                        screenshot(context, id + "-native-outcome");
                        cases.get(id).put("status", "PASSED");
                    } catch (Throwable failure) {
                        cases.get(id).put("status", "FAILED");
                        cases.get(id).put("failure", failure.toString());
                        failures.add(id + ": " + failure);
                        try { screenshot(context, id + "-failure"); } catch (Throwable capture) { failure.addSuppressed(capture); }
                    } finally {
                        release(context);
                        removeFixture();
                        write(false);
                    }
                }
            } finally {
                if (world != null) { world.close(); world = null; }
            }
            write(true);
            check(failures.isEmpty(), String.join("; ", failures));
            System.out.println("WARLOCKERY_FOCUS_FOLLOW_UP_PASS " + evidence);
        } catch (Throwable failure) {
            failures.add("suite: " + failure);
            try { screenshot(context, "suite-failure"); } catch (Throwable capture) { failure.addSuppressed(capture); }
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); } catch (Exception writing) { failure.addSuppressed(writing); }
            throw new AssertionError("Focus follow-up evidence: " + evidence, failure);
        }
    }

    private void exercise(final ClientGameTestContext context, final String id) throws Exception {
        switch (id) {
            case "paired_recall" -> pairedRecall(context);
            case "cross_dimension_recall" -> crossDimensionRecall(context);
            case "corpse_directives" -> corpseDirectives(context);
            case "grave_thrall_defends" -> graveThrallDefends(context);
            case "timed_infusion_expiry" -> timedExpiry(context);
            case "reserve_persistence_reload" -> persistence(context);
            case "infernal_lightning_recharge" -> lightningRecharge(context);
            case "path_refresh_effects" -> pathRefresh(context);
            case "earth_magnet" -> earthMagnet(context);
            case "infernal_web_climb" -> webClimb(context);
            case "infernal_leap_fall" -> leapFall(context);
            default -> throw new AssertionError("Unimplemented case " + id);
        }
    }

    // ------------------------------------------------------------------ Otherwhere

    private void pairedRecall(final ClientGameTestContext context) throws Exception {
        reset(context, MagicPath.OTHERWHERE);
        final BlockPos origin = value(ServerPlayer::blockPosition);
        paid(context, ActionKind.WORLD, () -> block(context, WORK, true),
            player -> MagicPathState.recall(player).filter(recall -> recall.position().equals(origin)).isPresent(), "Native crouch-block saves the caster position");
        final UUID other = spawnFixturePlayer(context, new Vec3(12.5, 100, -2));
        position(context, new Vec3(10.5, 100, -2));
        final Vec3 otherBefore = value(player -> fixturePlayer(player).position());
        row().put("before", Map.of("caster", value(Entity::position).toString(), "other", otherBefore.toString()));
        screenshot(context, active + "-before-recall");
        paid(context, ActionKind.TARGET, () -> entityUse(context, other, true), player ->
            player.position().distanceToSqr(Vec3.atBottomCenterOf(origin.above())) < 2
                && fixturePlayer(player).position().distanceToSqr(Vec3.atBottomCenterOf(origin.above())) < 2,
            "Native crouch-use on the other player returns both to the saved point");
        row().put("after", Map.of("caster", value(Entity::position).toString(), "other", value(player -> fixturePlayer(player).position().toString())));
        row().put("control", "The other player stood twelve blocks from the saved point before the paired recall and had no recall of its own.");
        row().put("fixture", "Connected synthetic ServerPlayer as the paired target; both players are staged ten to twelve blocks away after the native save.");
    }

    private void crossDimensionRecall(final ClientGameTestContext context) throws Exception {
        reset(context, MagicPath.OTHERWHERE);
        final BlockPos origin = value(ServerPlayer::blockPosition);
        final String overworld = value(player -> player.level().dimension().identifier().toString());
        paid(context, ActionKind.WORLD, () -> block(context, WORK, true),
            player -> MagicPathState.recall(player).filter(recall -> recall.position().equals(origin)).isPresent(), "Native crouch-block saves the Overworld position");
        server(player -> {
            final ServerLevel nether = player.level().getServer().getLevel(Level.NETHER);
            check(nether != null, "The native server provides the Nether dimension");
            nether.getChunkAt(new BlockPos(0, 100, 0));
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-6, 99, -6), new BlockPos(6, 106, 6)))
                nether.setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.NETHERRACK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            check(player.teleportTo(nether, 0.5, 100, 0.5, Set.of(), 0, 0, true), "Staged travel places the caster in the Nether");
            player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        context.waitFor(client -> client.level != null && client.level.dimension().equals(Level.NETHER) && client.gui.screen() == null, 300);
        await(context, player -> player.connection.hasClientLoaded() && !awaitingTeleport(player) && player.level().dimension().equals(Level.NETHER), 100,
            "Server acknowledges the Nether arrival before native input");
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.waitTicks(10);
        row().put("staged_dimension", value(player -> player.level().dimension().identifier().toString()));
        screenshot(context, active + "-in-nether");
        paid(context, ActionKind.SELF, () -> air(context, true, -45), player -> player.level().dimension().identifier().toString().equals(overworld)
            && player.position().distanceToSqr(Vec3.atBottomCenterOf(origin.above())) < 2, "Native crouch-air recall crosses back to the saved Overworld point");
        world.getConnection().waitForChunksRender();
        context.waitFor(client -> client.level != null && !client.level.dimension().equals(Level.NETHER), 300);
        row().put("after", Map.of("dimension", value(player -> player.level().dimension().identifier().toString()), "position", value(Entity::position).toString()));
        row().put("control", "Before the native recall the caster stood on a Nether platform with no Overworld chunk beneath it; only the saved point brought it back.");
    }

    // ------------------------------------------------------------------ Grave

    private void corpseDirectives(final ClientGameTestContext context) throws Exception {
        reset(context, MagicPath.GRAVE);
        // The bound body starts ten blocks from the clicked block so a directive must move it; the control sits at a similar distance.
        final UUID body = spawnCorpse(new Vec3(-5.5, 100, 5.5), false);
        final UUID control = spawnCorpse(new Vec3(6.5, 100, 5.5), true);
        final int before = value(player -> MagicPathState.reserve(player, MagicPath.GRAVE));
        block(context, WORK, false);
        context.waitTicks(10);
        check(value(player -> MagicPathState.reserve(player, MagicPath.GRAVE) == before), "Block use with no owned body refuses without spending reserve");
        check(value(player -> corpse(player, body).corpseCounters().graveDirectivesReceived == 0), "Unowned body receives no directive");
        row().put("refusal_overlay", overlay(context));
        row().put("refusal_reserve", before);
        position(context, new Vec3(-5.5, 100, 3.5));
        paid(context, ActionKind.TARGET, () -> entityUse(context, body, false), player -> player.getStringUUID().equals(
            WarlockeryEntityData.get(corpse(player, body)).getStringOr(GRAVE_OWNER, "")) && corpse(player, body).corpseCounters().graveBindNotifications >= 1,
            "Native Grave creature use binds the Corpse and notifies its runtime");
        position(context, START);
        final Vec3 bodyBefore = value(player -> corpse(player, body).position());
        final Vec3 controlBefore = value(player -> corpse(player, control).position());
        final Vec3 destination = Vec3.atBottomCenterOf(WORK.above());
        paid(context, ActionKind.WORLD, () -> block(context, WORK, false), player -> corpse(player, body).corpseCounters().graveDirectivesReceived == 1
            && corpse(player, body).transientFacts().graveDestination().isPresent(), "Native block use delivers exactly one Grave directive to the owned body");
        await(context, player -> corpse(player, body).transientFacts().activity() == CorpseRules.Activity.GRAVE_COMMAND
            || corpse(player, body).position().distanceTo(destination) < bodyBefore.distanceTo(destination) - 1.5, 100,
            "Directed body enters its Grave command activity or measurably approaches the clicked position");
        int ticks = 0;
        while (ticks++ < 200 && value(player -> corpse(player, body).position().distanceTo(destination) > bodyBefore.distanceTo(destination) - 2.0)) context.waitTicks(1);
        final double approached = bodyBefore.distanceTo(destination) - value(player -> corpse(player, body).position().distanceTo(destination));
        check(approached >= 2.0, "Directed body must walk at least two blocks toward the clicked position: approached=" + approached);
        check(value(player -> corpse(player, control).corpseCounters().graveDirectivesReceived == 0
            && corpse(player, control).position().distanceTo(controlBefore) < 0.5), "Unbound control body receives no directive and stays put");
        row().put("body", Map.of("before", bodyBefore.toString(), "after", value(player -> corpse(player, body).position().toString()), "approached", approached,
            "directives", value(player -> corpse(player, body).corpseCounters().graveDirectivesReceived),
            "activity", value(player -> corpse(player, body).transientFacts().activity().name())));
        row().put("control_body", Map.of("directives", 0, "moved", value(player -> corpse(player, control).position().distanceTo(controlBefore))));
        row().put("fixture", "Two staged Corpse bodies; the bound one keeps its AI, the control has no AI and no owner. Refusal, binding and the directive all come from native Focus input.");
    }

    private void graveThrallDefends(final ClientGameTestContext context) throws Exception {
        reset(context, MagicPath.GRAVE);
        final UUID thrall = spawn(EntityTypes.ZOMBIE, TARGET);
        paid(context, ActionKind.TARGET, () -> entityUse(context, thrall, false), player -> player.getStringUUID().equals(
            WarlockeryEntityData.get(living(player, thrall)).getStringOr(GRAVE_OWNER, "")), "Native Grave creature use binds the zombie");
        final UUID cow = spawn(EntityTypes.COW, new Vec3(2.0, 100, 0.5));
        context.waitTicks(25);
        check(value(player -> ((Mob) living(player, thrall)).getTarget() == null), "Control: bound thrall has no target before the caster attacks");
        hold(context, ItemStack.EMPTY);
        lookAt(context, cow);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        await(context, player -> player.getLastHurtMob() != null && player.getLastHurtMob().getUUID().equals(cow), 30, "Native bare-hand punch records the cow as the caster's recent attack");
        await(context, player -> {
            final LivingEntity target = ((Mob) living(player, thrall)).getTarget();
            return target != null && target.getUUID().equals(cow);
        }, 45, "Bound thrall answers the caster's recent attack by targeting the cow");
        row().put("thrall_target", value(player -> ((Mob) living(player, thrall)).getTarget().getUUID().toString()));
        row().put("fixture", "Staged NoAI zombie bound natively; a staged cow punched natively. Ordinary thrall ticks alone assign the target.");
    }

    // ------------------------------------------------------------------ expiry, persistence, recharge

    private void timedExpiry(final ClientGameTestContext context) throws Exception {
        reset(context, MagicPath.GRAVE);
        server(player -> MagicPathState.grantTimed(player, MagicPath.LIGHT, 100));
        final long granted = value(player -> player.level().getGameTime());
        await(context, player -> player.hasEffect(MobEffects.NIGHT_VISION) && MagicPathState.active(player).size() == 2, 30, "Both paths are active with their automatic effect");
        air(context, true, -45);
        await(context, player -> MagicPathState.selected(player).orElseThrow() == MagicPath.LIGHT, 30, "Native crouch-air cycles onto the timed Light path while it is active");
        row().put("cycled_to_light_at", value(player -> player.level().getGameTime() - granted));
        screenshot(context, active + "-both-active");
        await(context, player -> !MagicPathState.has(player, MagicPath.LIGHT), 140, "Timed Light path expires after its hundred ticks");
        check(value(player -> MagicPathState.has(player, MagicPath.GRAVE) && MagicPathState.active(player).equals(List.of(MagicPath.GRAVE))),
            "Permanent Grave path remains the only active path (control)");
        final int lightReserve = value(player -> MagicPathState.reserve(player, MagicPath.LIGHT));
        paid(context, ActionKind.SELF, () -> air(context, false, -45),
            player -> player.getEffect(MobEffects.NIGHT_VISION) != null && player.getEffect(MobEffects.NIGHT_VISION).getDuration() > 1000 && !player.hasEffect(MobEffects.INVISIBILITY),
            "After expiry native air use answers with Grave Night Vision rather than Light Invisibility");
        check(value(player -> MagicPathState.reserve(player, MagicPath.LIGHT) == lightReserve), "Expired path spends nothing");
        row().put("after_expiry", Map.of("active", value(player -> MagicPathState.active(player).stream().map(MagicPath::id).toList()), "light_reserve", lightReserve));
        row().put("fixture", "Grave granted permanently and Light granted for one hundred ticks as staged prerequisites; native cycling and use observe which path answers.");
    }

    private void persistence(final ClientGameTestContext context) throws Exception {
        // Otherwhere must be the selected path for the crouch-block save; Light is granted afterwards (becoming selected) for the spend.
        reset(context, MagicPath.OTHERWHERE);
        final BlockPos origin = value(ServerPlayer::blockPosition);
        paid(context, ActionKind.WORLD, () -> block(context, WORK, true),
            player -> MagicPathState.recall(player).filter(recall -> recall.position().equals(origin)).isPresent(), "Native crouch-block saves the recall point before saving");
        server(player -> MagicPathState.grantPermanent(player, MagicPath.LIGHT));
        paid(context, ActionKind.SELF, () -> air(context, false, -45), player -> player.hasEffect(MobEffects.INVISIBILITY), "Native Light self use spends reserve before saving");
        final int reserveBefore = value(player -> MagicPathState.reserve(player, MagicPath.LIGHT));
        final String recallBefore = value(player -> MagicPathState.recall(player).orElseThrow().toString());
        row().put("before_reload", Map.of("reserve", reserveBefore, "recall", recallBefore, "active", value(player -> MagicPathState.active(player).stream().map(MagicPath::id).toList())));
        screenshot(context, active + "-before-reload");
        final TestWorldSave save = world.getWorldSave();
        world.close();
        world = null;
        world = save.open();
        world.getConnection().waitForChunksRender();
        world.getConnection().waitForClientboundPackets();
        await(context, player -> player.connection.hasClientLoaded() && !awaitingTeleport(player), 200, "Reopened world is ready");
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.waitTicks(10);
        check(value(player -> MagicPathState.has(player, MagicPath.LIGHT) && MagicPathState.reserve(player, MagicPath.LIGHT) == reserveBefore
            && MagicPathState.has(player, MagicPath.OTHERWHERE) && MagicPathState.selected(player).orElseThrow() == MagicPath.LIGHT),
            "Active paths, selection and spent reserve survive the reload");
        check(value(player -> MagicPathState.recall(player).orElseThrow().toString().equals(recallBefore)), "Recall point survives the reload");
        row().put("after_reload", Map.of("reserve", value(player -> MagicPathState.reserve(player, MagicPath.LIGHT)), "recall", value(player -> MagicPathState.recall(player).orElseThrow().toString())));
        server(player -> player.removeEffect(MobEffects.INVISIBILITY));
        check(value(player -> player.getMainHandItem().is(ModItems.ALL.get("arcane_focus").get())), "Reloaded inventory still holds the Focus in the selected slot");
        paid(context, ActionKind.SELF, () -> air(context, false, -45), player -> player.hasEffect(MobEffects.INVISIBILITY), "Native use after the reload continues from the persisted reserve");
        row().put("fixture", "The world is closed and reopened through the client game test save; no state is re-injected after reopening.");
    }

    private void lightningRecharge(final ClientGameTestContext context) throws Exception {
        reset(context, MagicPath.INFERNAL);
        server(player -> check(MagicPathState.spend(player, MagicPath.INFERNAL, 100), "Stage a partly spent reserve"));
        strikeLightning(context);
        check(value(player -> MagicPathState.reserve(player, MagicPath.INFERNAL) == 60), "Control: lightning without the Explosion power leaves reserve at sixty");
        row().put("control_power", value(player -> MagicPathState.lastPower(player).id()));
        server(player -> { MagicPathState.setLastPower(player, InfernalPower.EXPLOSION); player.setHealth(20); player.clearFire(); });
        strikeLightning(context);
        await(context, player -> MagicPathState.reserve(player, MagicPath.INFERNAL) == MagicPath.INFERNAL.maximumReserve(), 20, "Lightning refills Infernal reserve while the Explosion power is held");
        row().put("refilled_reserve", value(player -> MagicPathState.reserve(player, MagicPath.INFERNAL)));
        row().put("fixture", "Infernal attunement, a partly spent reserve and the Explosion power are staged; a lightning bolt is summoned onto the player as the hazard.");
    }

    private void strikeLightning(final ClientGameTestContext context) {
        final float health = value(ServerPlayer::getHealth);
        server(player -> {
            final var server = player.level().getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "summon minecraft:lightning_bolt " + player.getX() + " " + player.getY() + " " + player.getZ());
        });
        await(context, player -> player.getHealth() < health, 30, "Summoned lightning actually strikes the player");
        server(player -> { player.clearFire(); player.setHealth(20); });
        context.waitTicks(5);
    }

    // ------------------------------------------------------------------ automatic effects

    private void pathRefresh(final ClientGameTestContext context) throws Exception {
        reset(context, null);
        context.waitTicks(30);
        check(value(player -> player.getActiveEffects().isEmpty()), "Control: without a path ordinary ticks renew no effect");
        final Map<String, Object> observed = new LinkedHashMap<>();
        observed.put("none", "no effects after thirty ticks");
        final Map<MagicPath, List<Holder<MobEffect>>> expected = new LinkedHashMap<>();
        expected.put(MagicPath.IMP, List.of(MobEffects.FIRE_RESISTANCE));
        expected.put(MagicPath.GRAVE, List.of(MobEffects.NIGHT_VISION));
        expected.put(MagicPath.LIGHT, List.of(MobEffects.NIGHT_VISION));
        expected.put(MagicPath.OTHERWHERE, List.of(MobEffects.SPEED));
        expected.put(MagicPath.OVERWORLD, List.of(MobEffects.HASTE, MobEffects.RESISTANCE));
        expected.put(MagicPath.SKY, List.of(MobEffects.SLOW_FALLING));
        for (var entry : expected.entrySet()) {
            reset(context, entry.getKey());
            await(context, player -> entry.getValue().stream().allMatch(player::hasEffect), 30, entry.getKey().id() + " renews its automatic effects");
            observed.put(entry.getKey().id(), value(player -> player.getActiveEffects().stream().map(effect -> effect.getEffect().getRegisteredName() + "/" + effect.getAmplifier()).toList()));
        }
        final Map<InfernalPower, List<Holder<MobEffect>>> infernal = new LinkedHashMap<>();
        infernal.put(InfernalPower.PROJECTILE, List.of(MobEffects.WATER_BREATHING));
        infernal.put(InfernalPower.FIRE, List.of(MobEffects.FIRE_RESISTANCE));
        infernal.put(InfernalPower.SPEED, List.of(MobEffects.SPEED));
        infernal.put(InfernalPower.LEAPING, List.of(MobEffects.JUMP_BOOST));
        infernal.put(InfernalPower.FLIGHT, List.of(MobEffects.SLOW_FALLING));
        infernal.put(InfernalPower.AQUATIC, List.of(MobEffects.WATER_BREATHING, MobEffects.DOLPHINS_GRACE));
        for (var entry : infernal.entrySet()) {
            reset(context, MagicPath.INFERNAL);
            server(player -> MagicPathState.setLastPower(player, entry.getKey()));
            await(context, player -> entry.getValue().stream().allMatch(player::hasEffect), 30, "Infernal " + entry.getKey().id() + " renews its automatic effects");
            if (entry.getKey() == InfernalPower.SPEED) check(value(player -> player.getEffect(MobEffects.SPEED).getAmplifier() == 1), "Horse power renews Speed II");
            observed.put("infernal_" + entry.getKey().id(), value(player -> player.getActiveEffects().stream().map(effect -> effect.getEffect().getRegisteredName() + "/" + effect.getAmplifier()).toList()));
        }
        row().put("observed", observed);
        row().put("fixture", "Each path (and each Infernal power) is granted as a staged prerequisite after a full reset; only ordinary ticks renew the effects.");
    }

    private void earthMagnet(final ClientGameTestContext context) throws Exception {
        reset(context, MagicPath.OVERWORLD);
        final UUID near = dropIngot(new Vec3(0.5, 100.2, 3.0));
        final UUID far = dropIngot(new Vec3(0.5, 100.2, 8.5));
        final double farBefore = value(player -> horizontal(player, far));
        await(context, player -> {
            final Entity drop = player.level().getEntity(near);
            return drop == null || horizontal(player, near) < 3.0;
        }, 60, "Earth draws the nearby ingot toward the player or into the inventory");
        context.waitTicks(20);
        check(value(player -> player.level().getEntity(far) != null && Math.abs(horizontal(player, far) - farBefore) < 0.4), "Control ingot beyond six blocks stays put");
        row().put("near", value(player -> player.level().getEntity(near) == null ? "picked up" : "distance " + horizontal(player, near)));
        row().put("far", Map.of("before", farBefore, "after", value(player -> horizontal(player, far))));
        row().put("fixture", "Two iron ingots are dropped at five and ten and a half blocks; the player stands still.");
    }

    private void webClimb(final ClientGameTestContext context) throws Exception {
        reset(context, null);
        buildWall();
        final double controlPeak = walkIntoWall(context);
        row().put("control_walk_y_per_tick", row().get("last_walk_y_per_tick"));
        check(controlPeak < 100.6, "Control: without a path the wall stops the player: peak=" + controlPeak);
        reset(context, MagicPath.INFERNAL);
        server(player -> MagicPathState.setLastPower(player, InfernalPower.WEB));
        buildWall();
        final double peak = walkIntoWall(context);
        row().put("peak_y", Map.of("control", controlPeak, "web_power", peak));
        row().put("suspected_product_defect", "MagicPathRuntime.applyInfernalMotionPassive reads ServerPlayer.horizontalCollision, but the client clips its own movement before reporting it, so the server-side move never collides and the climb never triggers for a real player.");
        check(peak > 101.5, "Web power lets native walking climb the wall: peak=" + peak + " y_per_tick=" + row().get("last_walk_y_per_tick")
            + " server_collision=" + row().get("last_walk_server_horizontal_collision"));
        row().put("fixture", "Four-block stone wall three blocks ahead of the start; eighty ticks of native forward walking with and without the staged Web power.");
    }

    private void buildWall() {
        server(player -> {
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-3, 100, 1), new BlockPos(3, 103, 1))) player.level().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        });
    }

    private double walkIntoWall(final ClientGameTestContext context) {
        position(context, START);
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); });
        context.waitTicks(2);
        double peak = value(ServerPlayer::getY);
        final List<Double> trace = new ArrayList<>();
        final List<Boolean> collisions = new ArrayList<>();
        context.getInput().holdKey(GLFW.GLFW_KEY_W);
        try {
            for (int tick = 0; tick < 80; tick++) {
                context.waitTicks(1);
                final double y = value(ServerPlayer::getY);
                trace.add(Math.round(y * 100.0) / 100.0);
                collisions.add(value(player -> player.horizontalCollision));
                peak = Math.max(peak, y);
            }
        } finally { context.getInput().releaseKey(GLFW.GLFW_KEY_W); }
        row().put("last_walk_y_per_tick", trace);
        row().put("last_walk_server_horizontal_collision", collisions);
        row().put("last_walk_end", Map.of("server", value(Entity::position).toString(), "client", context.computeOnClient(client -> client.player.position().toString()),
            "client_horizontal_collision", context.computeOnClient(client -> client.player.horizontalCollision)));
        return peak;
    }

    private void leapFall(final ClientGameTestContext context) throws Exception {
        reset(context, null);
        final float controlLoss = ledgeFall(context);
        check(controlLoss > 0, "Control: an eight-block native fall hurts the unattuned player");
        reset(context, MagicPath.INFERNAL);
        server(player -> MagicPathState.setLastPower(player, InfernalPower.LEAPING));
        final float infusedLoss = ledgeFall(context);
        check(infusedLoss == 0, "Leaping power cancels the same fall's damage");
        row().put("health_loss", Map.of("control", controlLoss, "leaping_power", infusedLoss));
        row().put("fixture", "Stone ledge eight blocks above the arena; native walking leaves it in both runs.");
    }

    private float ledgeFall(final ClientGameTestContext context) {
        server(player -> {
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-1, 107, -3), new BlockPos(1, 107, -1))) player.level().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        });
        position(context, new Vec3(0.5, 108, -0.5));
        final float before = value(ServerPlayer::getHealth);
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(20); });
        context.getInput().holdKeyFor(GLFW.GLFW_KEY_W, 8);
        await(context, player -> player.onGround() && player.getY() < 100.5, 80, "Native walking leaves the ledge and lands on the arena");
        context.waitTicks(5);
        final float loss = before - value(ServerPlayer::getHealth);
        server(player -> {
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-1, 107, -3), new BlockPos(1, 107, -1))) player.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            player.setHealth(player.getMaxHealth());
        });
        return loss;
    }

    // ------------------------------------------------------------------ fixtures

    private UUID spawnCorpse(final Vec3 pos, final boolean noAi) {
        return value(player -> {
            final Entity entity = ModEntities.ALL.get("corpse").get().create(player.level(), EntitySpawnReason.COMMAND);
            check(entity instanceof CorpseEntity, "Registry creates the Corpse body");
            final CorpseEntity body = (CorpseEntity) entity;
            body.setNoAi(noAi); body.setPersistenceRequired(); body.setPos(pos);
            check(player.level().addFreshEntity(body), "Corpse body spawns");
            return body.getUUID();
        });
    }

    private static CorpseEntity corpse(final ServerPlayer player, final UUID id) {
        final Entity entity = player.level().getEntity(id);
        if (!(entity instanceof CorpseEntity body)) throw new AssertionError("Corpse body missing: " + id);
        return body;
    }

    private UUID dropIngot(final Vec3 pos) {
        return value(player -> {
            final ItemEntity drop = new ItemEntity(player.level(), pos.x, pos.y, pos.z, new ItemStack(Items.IRON_INGOT));
            drop.setDeltaMovement(Vec3.ZERO);
            check(player.level().addFreshEntity(drop), "Ingot drop spawns");
            return drop.getUUID();
        });
    }

    private static double horizontal(final ServerPlayer player, final UUID id) {
        final Entity entity = player.level().getEntity(id);
        if (entity == null) throw new AssertionError("Drop missing: " + id);
        return Math.hypot(entity.getX() - player.getX(), entity.getZ() - player.getZ());
    }

    private UUID spawn(final EntityType<?> type, final Vec3 pos) {
        return value(player -> {
            final Entity entity = type.create(player.level(), EntitySpawnReason.TRIGGERED);
            check(entity instanceof Mob, "Fixture target is a live mob");
            final Mob mob = (Mob) entity;
            mob.setNoAi(true); mob.setPersistenceRequired(); mob.setPos(pos);
            if (type == EntityTypes.ZOMBIE) mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            check(player.level().addFreshEntity(mob), "Fixture target spawns"); return mob.getUUID();
        });
    }

    private UUID spawnFixturePlayer(final ClientGameTestContext context, final Vec3 position) {
        fixture = value(player -> {
            final var server = player.level().getServer();
            final var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FocusFixture");
            final var other = new ServerPlayer(server, player.level(), profile, net.minecraft.server.level.ClientInformation.createDefault());
            final var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, other, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false));
            other.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            other.setGameMode(GameType.SURVIVAL); other.teleportTo(position.x, position.y, position.z); other.setHealth(20);
            return other.getUUID();
        });
        world.getConnection().waitForClientboundPackets();
        final UUID id = fixture;
        context.waitFor(client -> client.level.getPlayerByUUID(id) != null, 60);
        return id;
    }

    private ServerPlayer fixturePlayer(final ServerPlayer player) {
        final ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(fixture);
        check(other != null, "Fixture player must remain connected");
        return other;
    }

    private void removeFixture() {
        if (fixture == null || world == null) return;
        final UUID id = fixture;
        fixture = null;
        server(player -> {
            final ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(id);
            if (other != null) player.level().getServer().getPlayerList().remove(other);
        });
    }

    // ------------------------------------------------------------------ Focus input

    private void paid(final ClientGameTestContext context, final ActionKind kind, final Runnable input, final Predicate<ServerPlayer> outcome, final String assertion) {
        final MagicPath path = value(player -> MagicPathState.selected(player).orElseThrow());
        final int before = value(player -> MagicPathState.reserve(player, path));
        final int cost = MagicPathProfile.forPath(path).cost(kind);
        input.run();
        await(context, player -> MagicPathState.reserve(player, path) == before - cost && outcome.test(player), 80, assertion);
        @SuppressWarnings("unchecked") final List<Object> uses = (List<Object>) row().computeIfAbsent("native_uses", ignored -> new ArrayList<>());
        uses.add(Map.of("path", path.id(), "action", kind.name(), "reserve_before", before, "reserve_after", value(player -> MagicPathState.reserve(player, path)), "assertion", assertion));
    }

    private void entityUse(final ClientGameTestContext context, final UUID target, final boolean secondary) {
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.level.getEntities((Entity) null, AREA, entity -> entity.getUUID().equals(target)).size() == 1, 60);
        secondary(context, secondary, () -> {
            look(context, value(player -> {
                final Entity entity = player.level().getEntity(target);
                check(entity != null, "Target entity exists");
                return entity.getBoundingBox().getCenter();
            }));
            check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(target)), "Native focus pointer hits the intended entity");
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        });
    }

    private void block(final ClientGameTestContext context, final BlockPos pos, final boolean secondary) {
        world.getConnection().waitForClientboundPackets();
        secondary(context, secondary, () -> {
            look(context, new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5));
            check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)), "Native focus pointer hits the intended block");
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        });
    }

    private void air(final ClientGameTestContext context, final boolean secondary, final float pitch) {
        secondary(context, secondary, () -> {
            context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(pitch); }); context.waitTicks(3);
            check(context.computeOnClient(client -> client.hitResult != null && client.hitResult.getType() == HitResult.Type.MISS), "Native personal focus use aims at empty air");
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        });
    }

    private void secondary(final ClientGameTestContext context, final boolean secondary, final Runnable action) {
        await(context, player -> player.connection.hasClientLoaded() && !awaitingTeleport(player), 100, "Native connection and teleport acknowledgement are ready before Focus input");
        context.waitTicks(5);
        if (secondary) context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
        try { context.waitTicks(2); action.run(); context.waitTicks(2); }
        finally { if (secondary) context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); }
    }

    private void lookAt(final ClientGameTestContext context, final UUID target) {
        look(context, value(player -> living(player, target).getBoundingBox().getCenter()));
        check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(target)), "Native pointer targets the creature");
    }

    private static void look(final ClientGameTestContext context, final Vec3 target) {
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(3);
    }

    private void hold(final ClientGameTestContext context, final ItemStack stack) {
        server(player -> { player.getInventory().setItem(0, stack.copy()); player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.waitTicks(3);
    }

    private static String overlay(final ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            final Object text = field(client.gui.hud, "overlayMessageString");
            return text instanceof Component component ? component.getString() : "";
        });
    }

    private static void release(final ClientGameTestContext context) {
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        for (int key : List.of(GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT))
            context.getInput().releaseKey(key);
    }

    // ------------------------------------------------------------------ staging

    private void reset(final ClientGameTestContext context, final MagicPath path) {
        if (context.computeOnClient(client -> client.gui.screen() != null)) {
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
        }
        release(context);
        removeFixture();
        server(player -> {
            final var server = player.level().getServer();
            server.setDifficulty(Difficulty.NORMAL, true);
            player.level().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            player.level().getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            player.level().getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, server);
            player.level().getEntities((Entity) null, AREA, entity -> entity != player).forEach(Entity::discard);
            player.level().getDataStorage().set(MagicConstructData.TYPE, new MagicConstructData());
            WarlockeryEntityData.get(player).remove("WarlockeryMagicPaths");
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(false);
            player.getAbilities().flying = false; player.getAbilities().invulnerable = false; player.onUpdateAbilities();
            player.removeAllEffects(); player.clearFire(); player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(5);
            player.getInventory().clearContent(); player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("arcane_focus").get()));
            for (BlockPos pos : BlockPos.betweenClosed(-20, 98, -20, 20, 114, 20))
                player.level().setBlockAndUpdate(pos, pos.getY() == 98 ? Blocks.BEDROCK.defaultBlockState() : pos.getY() == 99 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance(); player.teleportTo(START.x, START.y, START.z);
            if (path != null) MagicPathState.grantPermanent(player, path);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set 18000");
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets(); world.getConnection().waitForChunksRender();
        context.getInput().pressKey(GLFW.GLFW_KEY_1); context.waitTicks(5);
        row().put("initial_path", path == null ? "none" : path.id());
    }

    private void position(final ClientGameTestContext context, final Vec3 point) {
        server(player -> { player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance(); player.teleportTo(point.x, point.y, point.z); });
        world.getConnection().waitForClientboundPackets();
        await(context, player -> player.connection.hasClientLoaded() && !awaitingTeleport(player) && player.position().distanceTo(point) < 0.15, 100, "Server acknowledges the staged position");
        context.waitFor(client -> client.player != null && client.player.position().distanceTo(point) < 0.2);
        context.waitTicks(3);
    }

    private static boolean awaitingTeleport(final ServerPlayer player) {
        try {
            final var field = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");
            field.setAccessible(true); return field.get(player.connection) != null;
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot observe native teleport acknowledgement", failure); }
    }

    // ------------------------------------------------------------------ book

    private void readBook(final ClientGameTestContext context, final String id) throws Exception {
        final ManualProfile profile = ManualProfile.profiles().stream().filter(book -> book.sections().contains(id)).findFirst().orElseThrow();
        hold(context, new ItemStack(ModItems.ALL.get(profile.id()).get()));
        await(context, player -> player.connection.hasClientLoaded() && !awaitingTeleport(player), 100, "Connection is ready before opening the guide");
        context.waitFor(client -> client.player.getMainHandItem().is(ModItems.ALL.get(profile.id()).get()));
        context.runOnClient(client -> client.player.setXRot(-70));
        context.waitTicks(6);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, id);
        final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, id).body().getString());
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), id)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        check(!body.isBlank() && pages > 0, "Guide contains instructions");
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> id.equals(field(client.gui.screen(), "selectedSection")) && expected == (int) field(client.gui.screen(), "bodyPage")),
                "Native buttons visit each guide page");
            screenshot(context, id + "-book-" + page);
        }
        guides.put(id, Map.of("book", profile.id(), "body", body, "pages_read", pages));
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
        hold(context, new ItemStack(ModItems.ALL.get("arcane_focus").get()));
    }

    // ------------------------------------------------------------------ plumbing

    private static Object field(final Object object, final String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException failure) { throw new AssertionError(name, failure); }
        }
        throw new AssertionError("Missing observation field " + name);
    }

    private static LivingEntity living(final ServerPlayer player, final UUID id) {
        final Entity entity = player.level().getEntity(id);
        if (!(entity instanceof LivingEntity living)) throw new AssertionError("Live fixture target missing: " + id);
        return living;
    }

    private Map<String, Object> row() { return cases.get(active); }

    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> condition, final int ticks, final String message) {
        for (int tick = 0; tick < ticks && !value(condition::test); tick++) context.waitTicks(1);
        check(value(condition::test), message);
        world.getConnection().waitForClientboundPackets();
    }

    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T value(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> result = new AtomicReference<>();
        server(player -> result.set(action.apply(player)));
        return result.get();
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
    }

    private void write(final boolean complete) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("item", "warlockery:arcane_focus");
        report.put("completed", complete);
        report.put("all_selected_scenarios_passed", complete && failures.isEmpty() && cases.values().stream().anyMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("guides_read_in_game", guides);
        report.put("fixture", "Rendered Survival client with native Focus input. Platform, path attunement, Infernal powers, Corpse bodies, a synthetic second ServerPlayer, a Nether platform, dropped ingots, walls, ledges and a summoned lightning bolt are staged prerequisites. Recall points, bindings, directives, teleports, effects and reserve changes come only from native use and ordinary ticks.");
        report.put("cases", cases);
        report.put("screenshots", screenshots);
        report.put("failures", failures);
        report.put("remaining", List.of("Infusion Recharge rite (ritual walkthrough)", "Spirit World recall entry", "Corpse scan budget and sixty-four-body snapshots",
            "Grave bond expiry after two hours", "Flight-power fall branch", "Survival acquisition of paths and the Arcane Focus"));
        Files.writeString(evidence.resolve("focus-follow-up.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
