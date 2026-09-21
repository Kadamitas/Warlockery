package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.magic.ImpContractRules;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Ordinary input binds, feeds and offers all six contracts; observers never cast or inject their effects. */
public final class DemonicSpellAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final Vec3 START = new Vec3(.5, 100, -1.5);
    private static final BlockPos ORE = new BlockPos(0, 100, 1);
    private static final BlockPos WATER = new BlockPos(3, 99, 0);
    private static final BlockPos CONTROL_WATER = new BlockPos(10, 99, 0);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private volatile Observation observation;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("demonic-spell-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                final var current = observation;
                if (current != null && current.player.level() == level) current.loaded(entity);
            });
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (var spell : ImpContractRules.Spell.values()) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("item", spell.itemId());
                row.put("status", "NOT_RUN");
                row.put("not_run", List.of("crafting and imp-summoning acquisition", "another owner's refusal",
                    "sympathetic-binding priority", "last-attacker fallback", "cross-dimension refusal",
                    "full thirty-minute expiry", "save/reload persistence", "summoned mob combat and loot"));
                results.put(spell.itemId(), row);
            }
            for (var spell : ImpContractRules.Spell.values()) {
                final Map<String, Object> row = results.get(spell.itemId());
                row.put("status", "RUNNING"); write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context);
                        readBook(context, "ingredient_contract", row);
                        readBook(context, spell.itemId(), row);
                        prepareSubjects(context);
                        if (spell == ImpContractRules.Spell.MELTING_TOUCH) {
                            mine(context, "minecraft:raw_iron", row, "ordinary_ore");
                        } else {
                            selectCreature(context);
                        }
                        if (spell == ImpContractRules.Spell.EVAPORATION) {
                            server(player -> {
                                player.level().setBlockAndUpdate(WATER, Blocks.WATER.defaultBlockState());
                                player.level().setBlockAndUpdate(CONTROL_WATER, Blocks.WATER.defaultBlockState());
                            });
                            context.waitTicks(10);
                        }
                        bindAndImpress(context, spell, row);
                        castAndObserve(context, spell, row);
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        row.put("status", "FAILED"); row.put("failure", stack(failure));
                        failures.add(spell.itemId() + ": " + failure);
                        try { screenshot(context, spell.itemId() + "-failure"); } catch (Throwable ignored) { }
                    } finally {
                        if (observation != null) row.put("observation", serverValue(player -> observation.report()));
                        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
                        observation = null; write(false);
                    }
                } finally { observation = null; world = null; }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_DEMONIC_SPELL_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), stack(failure)); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Demonic contract evidence: " + evidence, failure);
        }
    }

    private void stage(final ClientGameTestContext context) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.removeAllEffects();
            player.setHealth(20); player.setAbsorptionAmount(0); player.getFoodData().setFoodLevel(20);
            player.experienceLevel = 25;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 97, -8), new BlockPos(14, 108, 10)))
                player.level().setBlockAndUpdate(pos, pos.getY() <= 99 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.teleportTo(START.x, START.y, START.z); player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets();
    }

    private void prepareSubjects(final ClientGameTestContext context) {
        server(player -> {
            observation = new Observation(player);
            observation.imp = mob(player, "warlockery:imp", new Vec3(-1.5, 100, .5));
            observation.target = mob(player, "minecraft:cow", new Vec3(2.5, 100, .5));
            observation.control = mob(player, "minecraft:cow", new Vec3(10.5, 100, .5));
            observation.imp.setTarget(player);
            check(CreatureBehaviorState.owner(observation.imp).isEmpty() && CreatureBehaviorState.impFavor(observation.imp) == 0,
                "Supplied imp starts unowned with no favor");
        });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
    }

    private void selectCreature(final ClientGameTestContext context) {
        supply(context, ItemStack.EMPTY);
        aimEntity(context, serverValue(player -> observation.target.getUUID()));
        final float before = serverValue(player -> observation.target.getHealth());
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        await(context, player -> player.getLastHurtMob() == observation.target && observation.target.getHealth() < before, 30,
            "An actual bare-hand hit selects the living target as the book instructs");
        // Wait for ordinary knockback to settle, then observe the selected creature's real position.
        context.waitTicks(20);
    }

    private void bindAndImpress(final ClientGameTestContext context, final ImpContractRules.Spell spell,
        final Map<String, Object> row) throws Exception {
        supply(context, modItem("ingredient_contract"));
        server(player -> player.experienceLevel = 24);
        useImp(context);
        check(serverValue(player -> player.experienceLevel == 24 && player.getMainHandItem().getCount() == 1
            && CreatureBehaviorState.owner(observation.imp).isEmpty()), "Insufficient experience preserves contract and does not bind the imp");
        awaitMessage(context, "message.warlockery.imp_contract.experience");
        server(player -> player.experienceLevel = 25);
        useImp(context);
        await(context, player -> CreatureBehaviorState.isOwnedBy(observation.imp, player.getUUID()), 30, "Ordinary contract binds the imp natively");
        check(serverValue(player -> player.experienceLevel == 0 && player.getMainHandItem().isEmpty() && observation.imp.getTarget() == null),
            "Survival binding consumes one contract and 25 levels and clears aggression");
        row.put("binding_status", "PASSED");
        for (int favor = 1; favor < spell.favor(); favor++) gift(context, favor);
        supply(context, modItem(spell.itemId()));
        final Map<String, Object> before = serverValue(player -> outcomeSnapshot());
        useImp(context); context.waitTicks(5);
        check(serverValue(player -> player.getMainHandItem().is(ModItems.ALL.get(spell.itemId()).get())
            && player.getMainHandItem().getCount() == 1 && CreatureBehaviorState.impFavor(observation.imp) == spell.favor() - 1),
            "Favor one below the requirement refuses without consuming the contract");
        check(before.equals(serverValue(player -> outcomeSnapshot())), "Refused spell produces no primary outcome");
        awaitMessage(context, "message.warlockery.imp_contract.imp_unimpressed");
        row.put("insufficient_favor_status", "PASSED");
        screenshot(context, spell.itemId() + "-insufficient-favor");
        gift(context, spell.favor());
        row.put("favor_reached", spell.favor());
    }

    private Map<String, Object> outcomeSnapshot() {
        final var player = observation.player;
        return Map.of("target_burning", observation.target.isOnFire(), "target_fire_resistance", observation.target.hasEffect(MobEffects.FIRE_RESISTANCE),
            "melting_expiration", WarlockeryEntityData.get(player).getLongOr("WarlockeryImpMeltingExpiration", 0L),
            "water_present", !player.level().getFluidState(WATER).isEmpty(), "summons", observation.summons.size());
    }

    private void gift(final ClientGameTestContext context, final int expected) {
        supply(context, new ItemStack(Items.DIAMOND)); useImp(context);
        await(context, player -> CreatureBehaviorState.impFavor(observation.imp) == expected && player.getMainHandItem().isEmpty(), 30,
            "One native gift is consumed for exactly one favor");
    }

    private void castAndObserve(final ClientGameTestContext context, final ImpContractRules.Spell spell,
        final Map<String, Object> row) throws Exception {
        check(serverValue(player -> spell == ImpContractRules.Spell.MELTING_TOUCH
            ? player.getLastHurtMob() == null && player.getLastHurtByMob() == null : player.getLastHurtMob() == observation.target),
            "Contract's actual combat history selects the intended creature, or self with no history");
        supply(context, modItem(spell.itemId()));
        final long time = serverValue(player -> player.level().getGameTime());
        final Vec3 caster = serverValue(ServerPlayer::position);
        final Vec3 target = serverValue(player -> observation.target.position());
        row.put("caster_before", caster.toString()); row.put("selected_creature_before", target.toString());
        screenshot(context, spell.itemId() + "-before-native-offer");
        useImp(context);
        await(context, player -> player.getMainHandItem().isEmpty(), 30, "Native successful spell consumes exactly one contract");
        check(serverValue(player -> CreatureBehaviorState.impFavor(observation.imp) == spell.favor()), "Casting retains the imp's favor");
        switch (spell) {
            case FIERY_TOUCH -> {
                await(context, player -> observation.target.isOnFire(), 30, "Selected creature ignites");
                final float health = serverValue(player -> observation.target.getHealth());
                await(context, player -> observation.target.getHealth() < health, 80, "Normal fire ticks actually harm the selected target");
                check(serverValue(player -> !player.isOnFire() && !observation.control.isOnFire()), "Caster and untreated control remain unburned");
                look(context, serverValue(player -> observation.target.getBoundingBox().getCenter()));
            }
            case EVAPORATION -> {
                await(context, player -> player.level().getBlockState(WATER).isAir(), 30, "Water near selected creature is removed");
                check(serverValue(player -> !player.level().getFluidState(CONTROL_WATER).isEmpty()), "Distant control water remains");
                look(context, Vec3.atCenterOf(WATER));
            }
            case FIRE_TOLERANCE -> {
                await(context, player -> observation.target.hasEffect(MobEffects.FIRE_RESISTANCE), 30, "Selected creature receives Fire Resistance");
                row.put("fire_resistance_before_hazard", serverValue(player -> Map.of(
                    "target_ticks", observation.target.getEffect(MobEffects.FIRE_RESISTANCE).getDuration(),
                    "caster_passive_ticks", player.hasEffect(MobEffects.FIRE_RESISTANCE) ? player.getEffect(MobEffects.FIRE_RESISTANCE).getDuration() : 0,
                    "control_has_effect", observation.control.hasEffect(MobEffects.FIRE_RESISTANCE))));
                check(serverValue(player -> observation.target.getEffect(MobEffects.FIRE_RESISTANCE).getDuration() >= 35_900
                    && (!player.hasEffect(MobEffects.FIRE_RESISTANCE) || player.getEffect(MobEffects.FIRE_RESISTANCE).getDuration() <= 60)
                    && !observation.control.hasEffect(MobEffects.FIRE_RESISTANCE)),
                    "Thirty-minute protection reaches only the selected creature; the nearby bound imp may refresh the caster's three-second passive");
                final float protectedBefore = serverValue(player -> observation.target.getHealth());
                final float controlBefore = serverValue(player -> observation.control.getHealth());
                server(player -> {
                    player.level().setBlockAndUpdate(observation.target.blockPosition(), Blocks.CAMPFIRE.defaultBlockState());
                    player.level().setBlockAndUpdate(observation.control.blockPosition(), Blocks.CAMPFIRE.defaultBlockState());
                });
                await(context, player -> observation.control.getHealth() < controlBefore, 100, "Real lit campfire harms untreated control");
                context.waitTicks(20);
                check(serverValue(player -> observation.target.getHealth() == protectedBefore), "Same lit campfire hazard cannot harm protected target");
                row.put("fire_trial", Map.of("protected_before", protectedBefore, "protected_after", serverValue(player -> observation.target.getHealth()),
                    "control_before", controlBefore, "control_after", serverValue(player -> observation.control.getHealth())));
                look(context, serverValue(player -> observation.target.getBoundingBox().getCenter()));
            }
            case MELTING_TOUCH -> {
                final long expires = serverValue(player -> WarlockeryEntityData.get(player).getLongOr("WarlockeryImpMeltingExpiration", 0L));
                check(expires >= time + 36_000 && expires <= time + 36_100, "Self receives thirty minutes of melting touch");
                mine(context, "minecraft:iron_ingot", row, "smelted_ore");
            }
            case LIVING_FLAME, TORMENT -> {
                final String entityId = spell == ImpContractRules.Spell.LIVING_FLAME ? "minecraft:blaze" : "warlockery:abyssal_regent";
                await(context, player -> observation.summons.size() == 1, 40, "Native contract creates exactly one named summon");
                final Map<String, Object> summon = serverValue(player -> Map.copyOf(observation.summons.getFirst()));
                check(entityId.equals(summon.get("type")), "Correct summoned creature identity");
                final Vec3 expected = spell == ImpContractRules.Spell.LIVING_FLAME ? target.add(2, 0, 2) : caster.add(3, 0, 3);
                check(expected.distanceTo(new Vec3((double) summon.get("x"), (double) summon.get("y"), (double) summon.get("z"))) < .05,
                    "Summon appears at its documented creature-relative or caster-relative position");
                check("".equals(summon.get("owner")), "Summoned creature remains unbound");
                if (spell == ImpContractRules.Spell.LIVING_FLAME) check(serverValue(player -> observation.target.getUUID().toString()).equals(summon.get("target")),
                    "Blaze is actually ordered to attack the selected creature at spawn");
                final UUID summoned = UUID.fromString((String) summon.get("uuid"));
                context.waitFor(client -> {
                    for (Entity entity : client.level.entitiesForRendering()) if (entity.getUUID().equals(summoned)) return true;
                    return false;
                });
                look(context, serverValue(player -> player.level().getEntity(summoned).getBoundingBox().getCenter()));
                row.put("summon", summon);
            }
        }
        row.put("consumption_status", "PASSED"); row.put("favor_retained_status", "PASSED"); row.put("primary_behavior_status", "PASSED");
        row.put("fixture", "Fresh Survival world, supplied 25 levels, ordinary/ named contracts and diamonds; staged unowned NoAI imp and cows. Ownership, favor, combat target, spell effects, water removal, mining drops and summons arise only from native input and normal ticks. Campfires/ores/water are explicit test hazards and materials. No cast or outcome setter is called.");
        screenshot(context, spell.itemId() + "-after-native-behavior");
    }

    private void mine(final ClientGameTestContext context, final String expected, final Map<String, Object> row, final String phase) throws Exception {
        server(player -> { observation.drops.clear(); player.level().setBlockAndUpdate(ORE, Blocks.IRON_ORE.defaultBlockState()); });
        supply(context, new ItemStack(Items.IRON_PICKAXE)); look(context, Vec3.atCenterOf(ORE));
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(ORE)), "Native pointer targets staged iron ore");
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        try { await(context, player -> player.level().getBlockState(ORE).isAir(), 160, "Native pickaxe mining actually breaks the ore"); }
        finally { context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT); }
        await(context, player -> !observation.drops.isEmpty(), 30, "Mining produces a real dropped item entity");
        check(serverValue(player -> observation.drops.equals(List.of(expected + "*1"))), "Exact ordinary or smelted drop, with no duplicate raw output");
        row.put(phase, serverValue(player -> List.copyOf(observation.drops)));
        screenshot(context, "melting-touch-" + phase);
    }

    private static Mob mob(final ServerPlayer player, final String id, final Vec3 point) {
        final Mob mob = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)).create(player.level(), EntitySpawnReason.COMMAND);
        check(mob != null, "Fixture entity exists: " + id);
        mob.snapTo(point.x, point.y, point.z); mob.setNoAi(true); mob.setPersistenceRequired();
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40); mob.setHealth(40);
        check(player.level().addFreshEntity(mob), "Fixture entity joins the world"); return mob;
    }
    private static final class Observation {
        final ServerPlayer player;
        Mob imp; Mob target; Mob control;
        final List<Map<String, Object>> summons = new ArrayList<>();
        final List<String> drops = new ArrayList<>();
        Observation(final ServerPlayer player) { this.player = player; }
        void loaded(final Entity entity) {
            final String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (id.equals("minecraft:blaze") || id.equals("warlockery:abyssal_regent")) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", id); row.put("uuid", entity.getUUID().toString());
                row.put("x", entity.getX()); row.put("y", entity.getY()); row.put("z", entity.getZ());
                row.put("owner", CreatureBehaviorState.owner(entity).map(UUID::toString).orElse(""));
                row.put("target", entity instanceof Mob mob && mob.getTarget() != null ? mob.getTarget().getUUID().toString() : "");
                summons.add(row);
            }
            if (entity instanceof ItemEntity item) drops.add(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()) + "*" + item.getItem().getCount());
        }
        Map<String, Object> report() {
            final Map<String, Object> row = new LinkedHashMap<>(); row.put("summons", List.copyOf(summons)); row.put("drops", List.copyOf(drops));
            if (imp != null) { row.put("favor", CreatureBehaviorState.impFavor(imp)); row.put("owner", CreatureBehaviorState.owner(imp).map(UUID::toString).orElse("")); }
            if (target != null) row.put("target_health", target.getHealth());
            return row;
        }
    }

    private void useImp(final ClientGameTestContext context) { aimEntity(context, serverValue(player -> observation.imp.getUUID())); context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(3); }
    private void aimEntity(final ClientGameTestContext context, final UUID id) {
        world.getConnection().waitForClientboundPackets();
        look(context, serverValue(player -> player.level().getEntity(id).getBoundingBox().getCenter()));
        check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(id)), "Native pointer targets exact intended creature");
    }
    private static ItemStack modItem(final String id) { return new ItemStack(ModItems.ALL.get(id).get()); }
    private void supply(final ClientGameTestContext context, final ItemStack item) {
        server(player -> { player.getInventory().clearContent(); player.getInventory().setItem(0, item); player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1); context.waitTicks(2);
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
    private void readBook(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final ManualProfile profile = ManualProfile.profiles().stream().filter(book -> book.sections().contains(id)).findFirst().orElseThrow();
        supply(context, modItem(profile.id()));
        context.runOnClient(client -> client.player.setXRot(-70)); context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, id);
        final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, id).body().getString());
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class); method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), id)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        check(!body.isBlank() && pages > 0, "Named contract guide contains actual instructions");
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> id.equals(field(client.gui.screen(), "selectedSection")) && expected == (int) field(client.gui.screen(), "bodyPage")), "Native book buttons visit each instruction page");
            screenshot(context, row.get("item") + "-" + id + "-book-" + page);
        }
        row.put(id + "_guide", Map.of("book", profile.id(), "body", body, "pages_read", pages));
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
    }
    private static Object field(final Object object, final String name) {
        try { final var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T serverValue(final Function<ServerPlayer, T> action) { return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer())); }
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots); }
    private static String stack(final Throwable failure) { final var text = new StringWriter(); failure.printStackTrace(new PrintWriter(text)); return text.toString(); }
    private static void awaitMessage(final ClientGameTestContext context, final String key) {
        final String expected = context.computeOnClient(client -> Component.translatable(key).getString());
        check(!expected.equals(key), "The imp diagnostic has readable translated text");
        context.waitFor(client -> ((List<?>) field(client.gui.hud.getChat(), "allMessages")).stream()
            .anyMatch(message -> ((net.minecraft.client.multiplayer.chat.GuiMessage) message).content().getString().equals(expected)), 40);
    }

    private void write(final boolean complete) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>(); report.put("completed", complete);
        report.put("all_selected_primary_behaviors_passed", complete && failures.isEmpty() && results.size() == ImpContractRules.Spell.values().length);
        report.put("all_item_contracts_complete", false); report.put("items", results); report.put("screenshots", screenshots); report.put("failures", failures);
        final Path temp = evidence.resolve("demonic-spell-abilities.json.tmp");
        Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temp, evidence.resolve("demonic-spell-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
