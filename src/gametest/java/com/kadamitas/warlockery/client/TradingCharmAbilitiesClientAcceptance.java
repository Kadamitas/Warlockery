package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.entity.InfernalHierarchyEntity;
import com.kadamitas.warlockery.item.BeastSpeechTradeCatalog;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModItems;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Native Beast-Speech and Silver-Tongue trades with real offerings, plus a passive comparison of demon aggression with and without the carried charm. */
public final class TradingCharmAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final Vec3 START = new Vec3(.5, 100, -1.5);
    private static final Vec3 COW = new Vec3(.5, 100, 1.5);
    /** Inside the 3-block survival entity reach (the cow sits straight ahead, the imp ahead-left on the floor). */
    private static final Vec3 IMP = new Vec3(-1.5, 100, .5);
    private static final Vec3 PEN_PLAYER = new Vec3(9.5, 100, .5);
    private static final Vec3 PEN_DEMON = new Vec3(12.5, 100, .5);
    private static final int WINDOW = 160;
    private static final String BEAST = "beast_speech_charm";
    private static final String SILVER = "silver_tongue_charm";
    private static final List<String> CASES = List.of("guide", "beast_speech_wrong_offering", "beast_speech_trade", "beast_speech_demon_refusal",
        "silver_tongue_wrong_offering", "silver_tongue_trade", "demon_pacification");
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private UUID cow;
    private UUID imp;
    private volatile AggressionObservation observation;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("trading-charm-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final var current = observation;
                if (current != null && current.player.level().getServer() == server) current.tick();
            });
            for (String id : CASES) { final Map<String, Object> row = new LinkedHashMap<>(); row.put("status", "NOT_RUN"); results.put(id, row); }
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            write(false);
            try (var created = context.worldBuilder().create()) {
                world = created;
                try {
                    stage(context);
                    run(context, "guide", row -> readGuide(context, "beast_speech", row));
                    run(context, "beast_speech_wrong_offering", row -> wrongOffering(context, row, BEAST, cow, "beast-speech"));
                    run(context, "beast_speech_trade", row -> trade(context, row, BEAST, cow, new ItemStack(Items.WHEAT, 3), BeastSpeechTradeCatalog.Partner.COW, "beast-speech"));
                    run(context, "beast_speech_demon_refusal", row -> noVoice(context, row));
                    run(context, "silver_tongue_wrong_offering", row -> wrongOffering(context, row, SILVER, imp, "silver-tongue"));
                    run(context, "silver_tongue_trade", row -> trade(context, row, SILVER, imp, new ItemStack(Items.GOLD_INGOT, 2), BeastSpeechTradeCatalog.Partner.DEMON, "silver-tongue"));
                    run(context, "demon_pacification", row -> pacification(context, row));
                } finally {
                    context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                    observation = null; world = null;
                }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_TRADING_CHARM_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), stack(failure)); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Trading charm evidence: " + evidence, failure);
        }
    }

    private interface Step { void run(Map<String, Object> row) throws Exception; }

    private void run(final ClientGameTestContext context, final String id, final Step step) throws Exception {
        final Map<String, Object> row = results.get(id);
        row.put("status", "RUNNING"); write(false);
        try {
            step.run(row); row.put("status", "PASSED");
        } catch (Throwable failure) {
            row.put("status", "FAILED"); row.put("failure", stack(failure)); failures.add(id + ": " + failure);
            try { screenshot(context, id + "-failure"); } catch (Throwable ignored) { }
        } finally { write(false); }
    }

    private void stage(final ClientGameTestContext context) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.removeAllEffects();
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); player.setHealth(200); player.setAbsorptionAmount(0);
            player.getFoodData().setFoodLevel(20);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 97, -8), new BlockPos(18, 108, 8))) {
                final boolean penWall = pos.getY() <= 103 && pos.getX() >= 8 && pos.getX() <= 14 && pos.getZ() >= -3 && pos.getZ() <= 3
                    && (pos.getX() == 8 || pos.getX() == 14 || pos.getZ() == -3 || pos.getZ() == 3);
                player.level().setBlockAndUpdate(pos, pos.getY() <= 99 ? Blocks.BEDROCK.defaultBlockState() : penWall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            player.teleportTo(START.x, START.y, START.z); player.setDeltaMovement(Vec3.ZERO);
            cow = mob(player, "minecraft:cow", COW, true).getUUID();
            final Mob staged = mob(player, "warlockery:imp", IMP, true);
            staged.setTarget(player); imp = staged.getUUID();
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
        final Map<String, Object> fixture = new LinkedHashMap<>();
        fixture.put("world", "Fresh disposable Survival world, NORMAL difficulty, bedrock platform three blocks thick, a walled 5x5 demon pen at x9-13, no natural spawns or regeneration; the actor has 200 maximum health so the aggression windows cannot kill it.");
        fixture.put("staged", "Charms supplied undamaged (crafting acquisition NOT_RUN); offerings supplied in the offhand; a NoAI cow and a NoAI imp that has been told to target the player stand within reach of the actor; live-AI warlockery:demon subjects are spawned inside the pen for the aggression windows.");
        fixture.put("inputs", "Every trade is a real right-click on the creature with the charm in the main hand; consumption, rewards, wear, message and target clearing are only observed. warlockery:demon is an InfernalHierarchyEntity with no target goals and only attacks a recorded aggressor, so each aggression window begins with one real bare-hand punch (the same provocation with and without the charm); afterwards the actor stands still.");
        fixture.put("not_run", List.of("crafting acquisition", "every other partner species and reward table", "charm breaking at its final use", "creative-mode trades",
            "Silver-Tongue trades with animals", "inventory-full reward drops", "multiplayer", "save/reload"));
        results.get("guide").put("fixture", fixture);
    }

    private void wrongOffering(final ClientGameTestContext context, final Map<String, Object> row, final String charm, final UUID subject, final String prefix) throws Exception {
        supplyHands(context, charm, new ItemStack(Items.COBBLESTONE, 4));
        final Map<String, Integer> before = serverValue(TradingCharmAbilitiesClientAcceptance::inventory);
        clearOverlay(context); useOn(context, subject);
        awaitOverlay(context, translated(context, "message.warlockery.beast_speech.wrong_offering"));
        check(serverValue(player -> player.getOffhandItem().is(Items.COBBLESTONE) && player.getOffhandItem().getCount() == 4), "A refused offering is not consumed");
        check(serverValue(player -> player.getMainHandItem().getDamageValue() == 0), "A refused trade does not wear the charm");
        check(before.equals(serverValue(TradingCharmAbilitiesClientAcceptance::inventory)), "A refused trade gives no reward");
        if (subject.equals(imp)) check(serverValue(player -> mob(player, imp).getTarget() == player), "A refused demon trade leaves the demon's hostility in place");
        row.put("overlay", overlay(context)); row.put("offhand_after", "minecraft:cobblestone*4"); row.put("charm_damage", 0);
        row.put("actual", "Real use on the creature with cobblestone offered was refused; nothing consumed, no wear, no reward.");
        screenshot(context, prefix + "-wrong-offering-refusal");
    }

    private void trade(final ClientGameTestContext context, final Map<String, Object> row, final String charm, final UUID subject,
        final ItemStack offering, final BeastSpeechTradeCatalog.Partner partner, final String prefix) throws Exception {
        supplyHands(context, charm, offering);
        final Map<String, Integer> before = serverValue(TradingCharmAbilitiesClientAcceptance::inventory);
        final int maxDamage = serverValue(player -> player.getMainHandItem().getMaxDamage());
        clearOverlay(context); useOn(context, subject);
        await(context, player -> player.getMainHandItem().getDamageValue() == 1, 20, "A real accepted trade wears the charm by exactly one");
        awaitOverlay(context, translated(context, "message.warlockery.beast_speech.trade_complete"));
        check(serverValue(player -> player.getOffhandItem().is(offering.getItem()) && player.getOffhandItem().getCount() == offering.getCount() - 1), "Exactly one offering is consumed");
        final Map<String, Integer> after = serverValue(TradingCharmAbilitiesClientAcceptance::inventory);
        final Map<String, Integer> gained = new LinkedHashMap<>();
        after.forEach((id, count) -> { final int delta = count - before.getOrDefault(id, 0); if (delta > 0) gained.put(id, delta); });
        check(gained.size() == 1, "Exactly one reward stack arrives; gained=" + gained);
        final String rewardId = gained.keySet().iterator().next();
        final int rewardCount = gained.get(rewardId);
        final var spec = BeastSpeechTradeCatalog.rewards(partner).stream().filter(entry -> entry.item().id().equals(rewardId)).findFirst();
        check(spec.isPresent() && rewardCount >= spec.get().minimum() && rewardCount <= spec.get().maximum(),
            "Reward comes from the " + partner + " table within its documented count: " + rewardId + "*" + rewardCount);
        if (subject.equals(imp)) check(serverValue(player -> mob(player, imp).getTarget() == null), "An accepted demon trade clears the demon's hostility toward the trader");
        check(serverValue(player -> mob(player, subject).isAlive()), "The trading partner is unharmed");
        row.put("overlay", overlay(context)); row.put("offering", BuiltInRegistries.ITEM.getKey(offering.getItem()) + "*" + offering.getCount() + " -> " + (offering.getCount() - 1));
        row.put("reward", rewardId + "*" + rewardCount); row.put("reward_table", partner.name());
        row.put("charm_wear", Map.of("damage_before", 0, "damage_after", 1, "max_damage", maxDamage));
        row.put("actual", "Real use with a valid offering consumed one, wore the charm by one and delivered a table reward" + (subject.equals(imp) ? "; the imp stopped targeting the trader." : "."));
        screenshot(context, prefix + "-trade-complete");
    }

    private void noVoice(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        supplyHands(context, BEAST, new ItemStack(Items.COBBLESTONE, 4));
        check(serverValue(player -> player.getMainHandItem().getDamageValue() == 0), "Fresh Beast-Speech charm supplied for the demon refusal");
        clearOverlay(context); useOn(context, imp);
        awaitOverlay(context, translated(context, "message.warlockery.beast_speech.no_voice"));
        check(serverValue(player -> player.getMainHandItem().getDamageValue() == 0 && player.getOffhandItem().getCount() == 4), "Beast-Speech cannot address a demon; nothing is worn or consumed");
        check(serverValue(player -> mob(player, imp).getTarget() == player), "The demon stays hostile after the refused address");
        row.put("overlay", overlay(context)); row.put("actual", "Beast-Speech on the imp reported no translatable voice; charm and offering untouched.");
        screenshot(context, "beast-speech-demon-no-voice");
    }

    private void pacification(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        server(player -> { mob(player, imp).discard(); mob(player, cow).discard(); });
        final Map<String, Object> carried = window(context, row, true, "carried_silver_tongue");
        final Map<String, Object> untreated = window(context, row, false, "untreated_control");
        final int carriedTargetTicks = (int) carried.get("target_ticks");
        final int controlTargetTicks = (int) untreated.get("target_ticks");
        final float controlDamage = (float) untreated.get("damage");
        check(controlDamage > 0 && controlTargetTicks > WINDOW / 2, "The provoked untreated control demon actually hunts and harms the actor; target_ticks=" + controlTargetTicks + " damage=" + controlDamage);
        check(carriedTargetTicks * 2 < controlTargetTicks, "Carrying the Silver-Tongue charm keeps the demon from holding the actor as a target most of the time; carried=" + carriedTargetTicks + " control=" + controlTargetTicks);
        row.put("comparison", Map.of("carried_target_ticks", carriedTargetTicks, "control_target_ticks", controlTargetTicks,
            "carried_damage", carried.get("damage"), "control_damage", controlDamage,
            "carried_damage_below_control", (float) carried.get("damage") < controlDamage,
            "note", "Only the target-holding comparison is asserted. Damage in both windows is recorded: the code gate is intermittent (three ticks in four), so a carried charm reduces rather than eliminates hits."));
        row.put("actual", "Same pen, same demon type, same single provoking punch, then an idle actor: with the charm in the inventory the demon held the actor as target for " + carriedTargetTicks
            + " of " + WINDOW + " ticks; without the charm for " + controlTargetTicks + " ticks and dealt " + controlDamage + " damage.");
    }

    private Map<String, Object> window(final ClientGameTestContext context, final Map<String, Object> row, final boolean carried, final String name) throws Exception {
        server(player -> {
            player.getInventory().clearContent();
            if (carried) player.getInventory().setItem(1, new ItemStack(item(SILVER)));
            player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges();
            player.removeAllEffects(); player.clearFire(); player.setHealth(200);
            player.teleportTo(PEN_PLAYER.x, PEN_PLAYER.y, PEN_PLAYER.z); player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(GLFW.GLFW_KEY_1); context.waitTicks(3);
        check(serverValue(player -> player.getMainHandItem().isEmpty() && player.getInventory().contains(new ItemStack(item(SILVER))) == carried), "Charm carried state is exactly " + carried);
        final UUID demon = serverValue(player -> mob(player, "warlockery:demon", PEN_DEMON, false).getUUID());
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
        final Map<String, Object> provocation = new LinkedHashMap<>();
        provocation.put("demon_goals", serverValue(player -> ((InfernalHierarchyEntity) mob(player, demon)).operationalGoalNames()));
        provocation.put("demon_target_goals", serverValue(player -> ((InfernalHierarchyEntity) mob(player, demon)).operationalTargetGoalCount()));
        provocation.put("aggressor_before_punch", serverValue(player -> hierarchyAggressor(mob(player, demon))));
        check(serverValue(player -> hierarchyAggressor(mob(player, demon)).isEmpty() && mob(player, demon).getTarget() == null), "Fresh demon records no aggressor and no target");
        // Honest prerequisite: one real bare-hand punch makes the actor the demon's recorded aggressor.
        final float demonHealth = serverValue(player -> mob(player, demon).getHealth());
        aim(context, demon);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        await(context, player -> mob(player, demon).getHealth() < demonHealth && player.getUUID().toString().equals(hierarchyAggressor(mob(player, demon))), 20,
            "A real punch lands and the demon records the actor as its aggressor");
        provocation.put("aggressor_after_punch", serverValue(player -> hierarchyAggressor(mob(player, demon))));
        provocation.put("demon_health_after_punch", serverValue(player -> mob(player, demon).getHealth()));
        server(player -> observation = new AggressionObservation(player, mob(player, demon)));
        context.waitTicks(WINDOW);
        final Map<String, Object> report = serverValue(player -> observation.report());
        observation = null;
        screenshot(context, "demon-window-" + name);
        server(player -> { mob(player, demon).discard(); player.clearFire(); player.setHealth(200); });
        context.waitTicks(3);
        report.put("carried_charm", carried); report.put("provocation", provocation); row.put(name, report);
        return report;
    }

    private static String hierarchyAggressor(final Mob demon) {
        return ((InfernalHierarchyEntity) demon).hierarchyState().aggressorId().map(UUID::toString).orElse("");
    }

    private static final class AggressionObservation {
        final ServerPlayer player; final Mob demon; final float startHealth;
        int ticks; int targetTicks; int hits; float lastHealth; final List<String> sources = new ArrayList<>();
        AggressionObservation(final ServerPlayer player, final Mob demon) { this.player = player; this.demon = demon; startHealth = player.getHealth(); lastHealth = startHealth; }
        void tick() {
            if (ticks >= WINDOW) return;
            ticks++;
            if (demon.getTarget() == player) targetTicks++;
            if (player.getHealth() < lastHealth) {
                final var source = player.getLastDamageSource();
                final String id = source == null ? "unknown" : source.getEntity() == demon ? "demon_melee" : source.getMsgId();
                if (source != null && source.getEntity() == demon) hits++;
                if (sources.size() < 40) sources.add(id + "@" + ticks);
            }
            lastHealth = player.getHealth();
        }
        Map<String, Object> report() {
            final Map<String, Object> value = new LinkedHashMap<>();
            value.put("ticks", ticks); value.put("target_ticks", targetTicks); value.put("demon_hits", hits);
            value.put("damage", startHealth - player.getHealth()); value.put("damage_sources", List.copyOf(sources));
            value.put("demon_alive", demon.isAlive()); value.put("demon_has_ai", !demon.isNoAi());
            value.put("demon_distance", demon.distanceTo(player)); value.put("player_y", player.getY());
            value.put("observer", "Passive end-of-tick sampling of the demon's target and the actor's health; no targeting, damage or protection injected.");
            return value;
        }
    }

    private static Mob mob(final ServerPlayer player, final String id, final Vec3 point, final boolean noAi) {
        final Mob mob = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)).create(player.level(), EntitySpawnReason.COMMAND);
        check(mob != null, "Fixture entity exists: " + id);
        mob.snapTo(point.x, point.y, point.z); mob.setNoAi(noAi); mob.setPersistenceRequired();
        check(player.level().addFreshEntity(mob), "Fixture entity joins the world"); return mob;
    }
    private static Mob mob(final ServerPlayer player, final UUID id) {
        final var entity = player.level().getEntity(id); check(entity instanceof Mob, "Observed creature is loaded: " + id); return (Mob) entity;
    }
    private static Map<String, Integer> inventory(final ServerPlayer player) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        }
        return counts;
    }
    private static Item item(final String id) { return ModItems.ALL.get(id).get(); }
    private void supplyHands(final ClientGameTestContext context, final String charm, final ItemStack offhand) {
        server(player -> {
            player.getInventory().clearContent(); player.getInventory().setItem(0, new ItemStack(item(charm)));
            player.setItemSlot(EquipmentSlot.OFFHAND, offhand.copy());
            player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(GLFW.GLFW_KEY_1); context.waitTicks(2);
        check(serverValue(player -> player.getMainHandItem().is(item(charm)) && ItemStack.isSameItem(player.getOffhandItem(), offhand)), "Charm and offering are actually in opposite hands");
    }
    private void useOn(final ClientGameTestContext context, final UUID subject) {
        aim(context, subject);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(3);
    }
    private void aim(final ClientGameTestContext context, final UUID subject) {
        world.getConnection().waitForClientboundPackets();
        look(context, serverValue(player -> mob(player, subject).getBoundingBox().getCenter()));
        final String pointer = context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit
            ? "entity " + hit.getEntity().getUUID() + " (" + BuiltInRegistries.ENTITY_TYPE.getKey(hit.getEntity().getType()) + ")" : String.valueOf(client.hitResult));
        check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(subject)),
            "Native pointer targets the exact creature " + subject + "; client.hitResult=" + pointer + "; " + serverValue(player -> "player=" + player.position()
                + " subject=" + mob(player, subject).position() + " box=" + mob(player, subject).getBoundingBox() + " distance=" + mob(player, subject).distanceTo(player)));
    }
    private static void look(final ClientGameTestContext context, final Vec3 point) {
        context.runOnClient(client -> { final Vec3 delta = point.subtract(client.player.getEyePosition());
            client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)))); });
        context.waitTicks(2);
    }
    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> predicate, final int ticks, final String message) {
        for (int remaining = ticks; remaining > 0 && !serverValue(predicate::test); remaining--) context.waitTicks(1);
        check(serverValue(predicate::test), message);
    }
    private static String translated(final ClientGameTestContext context, final String key, final Object... arguments) {
        return context.computeOnClient(client -> Component.translatable(key, arguments).getString());
    }
    private static String overlay(final ClientGameTestContext context) {
        return context.computeOnClient(client -> { final Object value = field(client.gui.hud, "overlayMessageString"); return value instanceof Component text ? text.getString() : value == null ? "" : String.valueOf(value); });
    }
    private static void clearOverlay(final ClientGameTestContext context) { context.runOnClient(client -> client.gui.hud.setOverlayMessage(Component.empty(), false)); context.waitTicks(1); }
    private static void awaitOverlay(final ClientGameTestContext context, final String expected) {
        for (int tick = 0; tick < 30 && !overlay(context).equals(expected); tick++) context.waitTicks(1);
        check(overlay(context).equals(expected), "Rendered overlay reports charm result: " + expected + "; actual=" + overlay(context));
    }
    private void readGuide(final ClientGameTestContext context, final String section, final Map<String, Object> row) throws Exception {
        final ManualProfile profile = ManualProfile.profiles().stream().filter(book -> book.sections().contains(section)).findFirst().orElseThrow();
        server(player -> { player.getInventory().clearContent(); player.getInventory().setItem(0, new ItemStack(item(profile.id()))); player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(GLFW.GLFW_KEY_1); context.waitTicks(2);
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(-80); }); context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, section);
        final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, section).body().getString());
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class); method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        check(!body.isBlank() && pages > 0, "Beast speech guide contains actual instructions");
        check(body.contains("offer an animal a food it likes") && body.contains("Silver Tongue Charm speaks to demons"), "Guide promises food offerings for animals and demon speech for the Silver Tongue");
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection")) && (int) field(client.gui.screen(), "bodyPage") == expected), "Native book buttons visit every guide page");
            screenshot(context, section + "-guide-" + page);
        }
        row.put("guide", Map.of("book", profile.id(), "section", section, "text", body, "pages_read", pages,
            "guide_gap", "The guide does not describe carried-charm demon pacification or charm wear; those come from the item code and are recorded as observed."));
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
    }
    private static Object field(final Object object, final String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) try {
            final var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(object);
        } catch (NoSuchFieldException ignored) { } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        throw new AssertionError("Missing observed field " + name);
    }
    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T serverValue(final Function<ServerPlayer, T> action) { return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer())); }
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots); }
    private static String stack(final Throwable failure) { final var text = new StringWriter(); failure.printStackTrace(new PrintWriter(text)); return text.toString(); }
    private void write(final boolean complete) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", complete);
        report.put("all_selected_scenarios_passed", complete && failures.isEmpty() && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("items", List.of("warlockery:" + BEAST, "warlockery:" + SILVER));
        report.put("execution", "Native rendered Fabric client; charms and offerings are supplied, every trade is a real right-click on the creature, and consumption, wear, rewards, messages, target clearing and demon aggression are only observed.");
        report.put("scenarios", results); report.put("screenshots", screenshots); report.put("failures", failures);
        report.put("remaining", "Crafting acquisition, other partner species, final-use breakage, creative trades, Silver-Tongue animal trades, reward drops on full inventory, multiplayer and persistence are NOT_RUN.");
        final Path temp = evidence.resolve("trading-charm-abilities.json.tmp");
        Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temp, evidence.resolve("trading-charm-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
