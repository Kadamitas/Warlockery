package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModItems;
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
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class BrazierAndDeathProtectionAcceptance implements FabricClientGameTest {
    private static final List<String> BRAZIER_RECIPES = List.of(
        "brazier_anguish_of_the_dead", "brazier_deathly_veil", "brazier_drain_growth",
        "brazier_fortification_of_the_corpse", "brazier_graveyard_mist",
        "brazier_summon_banshee", "brazier_summon_poltergeist", "brazier_summon_spectre");
    private static final BlockPos CACTUS = new BlockPos(0, 100, 2);
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private volatile DeathObservation observation;
    private volatile boolean observingBrazier;
    private volatile float brazierMinimumMaximumHealth = Float.POSITIVE_INFINITY;
    private volatile float brazierMaximumMaximumHealth;
    private volatile float brazierMaximumAbsorption;
    private volatile long brazierHealthSamples;
    private TestSingleplayerContext world;
    private Path evidence;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("brazier-and-death-protection").resolve(UUID.randomUUID().toString());
        final Map<String, Object> brazier = new LinkedHashMap<>();
        final Map<String, Object> death = new LinkedHashMap<>();
        report.put("brazier", brazier);
        report.put("death_guard_doll", death);
        report.put("screenshots", screenshots);
        report.put("failures", failures);
        report.put("execution", "Actual rendered Fabric client. Existing native machine recipe walkthrough covers all eight "
            + "brazier recipes with fresh worlds, native book pages, inventory clicks, ignition and runtime observers. "
            + "Death Guard uses its own bounded cactus arena and passive damage-source/health observations.");
        report.put("acquisition_status", "NOT_RUN");
        report.put("persistence_status", "NOT_RUN");
        report.put("all_item_contracts_complete", false);
        brazier.put("status", "NOT_RUN");
        brazier.put("required_recipes", BRAZIER_RECIPES);
        death.put("status", "NOT_RUN");
        try {
            Files.createDirectories(evidence);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                if (!observingBrazier) return;
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    brazierMinimumMaximumHealth = Math.min(brazierMinimumMaximumHealth, player.getMaxHealth());
                    brazierMaximumMaximumHealth = Math.max(brazierMaximumMaximumHealth, player.getMaxHealth());
                    brazierMaximumAbsorption = Math.max(brazierMaximumAbsorption, player.getAbsorptionAmount());
                    brazierHealthSamples++;
                }
            });
            write(false);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try {
                death.put("status", "RUNNING");
                write(false);
                runDeathGuard(context, death);
                death.put("status", "PASSED");
            } catch (Throwable failure) {
                death.put("status", "FAILED");
                death.put("failure", failure.toString());
                failures.add("Death Guard: " + failure);
            } finally {
                observation = null;
                world = null;
                write(false);
            }
            final Map<String, String> previous = new LinkedHashMap<>();
            final Map<String, String> configured = Map.of(
                "warlockery.clientEvidence", evidence.toString(),
                "warlockery.machineTypes", "brazier",
                "warlockery.machineRepresentativeTypes", "none",
                "warlockery.machineRecipeIds", String.join(",", BRAZIER_RECIPES));
            try {
                configured.forEach((key, value) -> {
                    previous.put(key, System.getProperty(key));
                    System.setProperty(key, value);
                });
                brazier.put("status", "RUNNING");
                brazier.put("recipe_receipt_directory", evidence.resolve("machines").toString());
                brazier.put("remaining", "Boundary radii, buff expiry, interrupted burns, every ingredient-tag alternative, "
                    + "rare bonus summons and save/reload are not exhaustively covered by this eight-recipe sweep.");
                write(false);
                final var machineSuite = new MachineWalkthroughAcceptance();
                try {
                    observingBrazier = true;
                    machineSuite.runTest(context);
                } finally {
                    observingBrazier = false;
                    brazier.put("health_observation", Map.of("samples", brazierHealthSamples,
                        "minimum_maximum_health", brazierHealthSamples == 0 ? 0 : brazierMinimumMaximumHealth,
                        "maximum_maximum_health", brazierMaximumMaximumHealth,
                        "maximum_absorption", brazierMaximumAbsorption));
                    final Path machineEvidence = (Path) field(machineSuite, "evidence");
                    if (machineEvidence != null) brazier.put("recipe_receipt", machineEvidence.resolve("machine-walkthrough.json").toString());
                }
                final var receipt = com.google.gson.JsonParser.parseString(Files.readString(
                    Path.of((String) brazier.get("recipe_receipt")))).getAsJsonObject();
                check(receipt.get("required_recipe_count").getAsInt() == BRAZIER_RECIPES.size()
                    && receipt.get("recipe_sweep_complete").getAsBoolean(), "All eight native brazier recipe receipts must be complete");
                final var actual = receipt.getAsJsonArray("recipe_results").asList().stream()
                    .map(value -> value.getAsJsonObject().get("recipe").getAsString()).collect(java.util.stream.Collectors.toSet());
                check(actual.equals(BRAZIER_RECIPES.stream().map(id -> "warlockery:" + id)
                    .collect(java.util.stream.Collectors.toSet())), "Brazier coverage must equal the eight requested runtime recipes");
                check(brazierHealthSamples > 0 && brazierMinimumMaximumHealth == 20.0F
                    && brazierMaximumMaximumHealth == 20.0F && brazierMaximumAbsorption == 0.0F,
                    "Throughout all eight brazier burns, the player gains neither maximum-health hearts nor absorption hearts");
                brazier.put("status", "PASSED");
            } catch (Throwable failure) {
                brazier.put("status", "FAILED");
                brazier.put("failure", failure.toString());
                failures.add("Brazier: " + failure);
            } finally {
                previous.forEach((key, value) -> {
                    if (value == null) System.clearProperty(key); else System.setProperty(key, value);
                });
                write(false);
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_BRAZIER_AND_DEATH_PROTECTION_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); } catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Brazier and Death Guard evidence: " + evidence, failure);
        }
    }

    private void runDeathGuard(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            final DeathObservation current = observation;
            if (current != null && current.player.level().getServer() == server) current.tick();
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            final DeathObservation current = observation;
            if (current != null && entity == current.player) {
                current.damageSources.add(source.is(DamageTypes.CACTUS) ? "minecraft:cactus" : source.getMsgId());
                current.lastDamageTick = current.player.level().getGameTime();
                current.lastDamageWasCactus = source.is(DamageTypes.CACTUS);
                current.lastObservedAmount = amount;
            }
            return true;
        });
        try (var created = context.worldBuilder().create()) {
            world = created;
            try {
                world.getServer().runOnServer(server -> {
                    server.setDifficulty(Difficulty.HARD, true);
                    server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
                });
                server(player -> {
                    player.setGameMode(GameType.SURVIVAL);
                    player.getInventory().clearContent();
                    player.removeAllEffects();
                    player.setAbsorptionAmount(0);
                    player.setHealth(player.getMaxHealth());
                    player.getFoodData().setFoodLevel(20);
                    player.getFoodData().setSaturation(5);
                    for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 99, -8), new BlockPos(8, 108, 8))) {
                        final boolean wall = Math.abs(pos.getX()) == 3 || Math.abs(pos.getZ()) == 4;
                        player.level().setBlockAndUpdate(pos, pos.getY() == 99 || wall && pos.getY() <= 103
                            ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                    player.teleportTo(0.5, 100, 0.5);
                    player.setDeltaMovement(Vec3.ZERO);
                    player.resetFallDistance();
                });
                world.getConnection().waitForChunksRender();
                world.getConnection().waitForClientboundPackets();
                row.put("fixture", "Fresh Survival world; HARD difficulty; natural regeneration disabled; staged book "
                    + "and unbound doll; stone floor and enclosing walls prevent a fall from satisfying the test. "
                    + "Health is staged to one before native walking into an ordinary cactus. No damage, protection, "
                    + "effect application, max-health attribute, durability consumption or successful outcome is injected.");
                final float originalMaximum = serverValue(ServerPlayer::getMaxHealth);
                row.put("before_binding", serverValue(BrazierAndDeathProtectionAcceptance::health));
                check(originalMaximum == 20.0F, "Fresh player has exactly ten ordinary hearts");
                readBook(context, "death_guard_doll", row);
                server(player -> {
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("death_guard_doll").get()));
                    player.getInventory().setSelectedSlot(0);
                    player.inventoryMenu.broadcastChanges();
                });
                world.getConnection().waitForClientboundPackets();
                context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(-70); });
                context.waitTicks(2);
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                for (int tick = 0; tick < 30 && !serverValue(player -> DollItem.isBoundTo(player.getMainHandItem(), player)); tick++)
                    context.waitTicks(1);
                check(serverValue(player -> DollItem.isBoundTo(player.getMainHandItem(), player)
                    && player.getMainHandItem().getDamageValue() == 0), "Native use binds the doll without spending a charge");
                check(serverValue(ServerPlayer::getMaxHealth) == originalMaximum, "Binding does not add five maximum-health hearts");
                row.put("after_binding", serverValue(BrazierAndDeathProtectionAcceptance::health));
                screenshot(context, "death-guard-bound-ten-hearts");
                server(player -> {
                    // Sand is a falling block: without support under the one-block floor it drops, the cactus
                    // breaks, and the walk ends in a fall through the hole instead of a cactus hit.
                    player.level().setBlockAndUpdate(CACTUS.below(2), Blocks.STONE.defaultBlockState());
                    player.level().setBlockAndUpdate(CACTUS.below(), Blocks.SAND.defaultBlockState());
                    player.level().setBlockAndUpdate(CACTUS, Blocks.CACTUS.defaultBlockState());
                    check(player.level().getBlockState(CACTUS).is(Blocks.CACTUS), "Real cactus exists in the bounded arena");
                    player.setHealth(1.0F);
                    observation = new DeathObservation(player, originalMaximum);
                });
                world.getConnection().waitForClientboundPackets();
                context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(10); });
                context.waitTicks(4);
                check(serverValue(player -> player.level().getBlockState(CACTUS).is(Blocks.CACTUS)
                    && player.level().getBlockState(CACTUS.below()).is(Blocks.SAND)
                    && player.level().getBlockState(player.blockPosition().below()).is(Blocks.STONE)
                    && player.getY() >= 99.9 && player.getY() < 101), "Cactus stays supported and the player stands on the arena floor before walking");
                context.getInput().holdKey(GLFW.GLFW_KEY_W);
                try {
                    for (int tick = 0; tick < 160 && !serverValue(player -> observation.activated || !player.isAlive()); tick++)
                        context.waitTicks(1);
                } finally {
                    context.getInput().releaseKey(GLFW.GLFW_KEY_W);
                }
                check(serverValue(player -> observation.activated), "Actual cactus collision activates the doll");
                check(serverValue(player -> observation.activationWasCactus), "Passive native damage event proves cactus, not fall damage");
                check(serverValue(player -> observation.activationY >= 99.9 && observation.activationY < 101),
                    "Activation stays at arena floor height");
                check(serverValue(player -> !observation.maximumChanged && player.getMaxHealth() == originalMaximum),
                    "Maximum health stays at ten hearts on every observed tick");
                check(serverValue(player -> player.isAlive() && player.getInventory().getItem(0).getDamageValue() == 1),
                    "Native lethal damage is prevented and exactly one charge is consumed");
                check(serverValue(player -> observation.activationAbsorption == 0.0F),
                    "Lethal protection adds no temporary absorption hearts");
                check(serverValue(player -> observation.activationHealth == originalMaximum),
                    "Lethal protection restores exactly the full existing maximum health");
                check(serverValue(player -> !player.hasEffect(MobEffects.REGENERATION) && !player.hasEffect(MobEffects.ABSORPTION)
                    && !player.hasEffect(MobEffects.FIRE_RESISTANCE)), "Lethal protection applies no Totem recovery effects");
                row.put("activation", serverValue(player -> observation.activation));
                world.getConnection().waitForClientboundPackets();
                row.put("client_after_activation", context.computeOnClient(client -> Map.of(
                    "maximum_health", client.player.getMaxHealth(), "current_health", client.player.getHealth(),
                    "absorption", client.player.getAbsorptionAmount())));
                check(context.computeOnClient(client -> client.player.getMaxHealth()) == originalMaximum,
                    "The rendered client's maximum health also remains unchanged");
                screenshot(context, "death-guard-lethal-recovery");
                server(player -> player.level().setBlockAndUpdate(CACTUS, Blocks.AIR.defaultBlockState()));
                context.waitTicks(60);
                check(serverValue(player -> !player.hasEffect(MobEffects.REGENERATION) && !player.hasEffect(MobEffects.ABSORPTION)
                    && !player.hasEffect(MobEffects.FIRE_RESISTANCE)), "No recovery effect appears through ordinary later ticks");
                row.put("after_settling", serverValue(player -> {
                    final Map<String, Object> value = health(player);
                    value.put("maximum_changed_on_any_tick", observation.maximumChanged);
                    value.put("damage_sources_after_activation", List.copyOf(observation.damageSources));
                    return value;
                }));
                // Ordinary non-lethal cactus contact can land once or twice before the fixture removes the cactus.
                check(serverValue(player -> !observation.maximumChanged && player.getMaxHealth() == originalMaximum),
                    "After settling, maximum health is unchanged on every observed tick");
                check(serverValue(player -> player.getAbsorptionAmount() == 0), "After settling, no temporary hearts exist");
                check(serverValue(player -> player.getHealth() >= originalMaximum - 2 && player.getHealth() <= originalMaximum),
                    "After settling, health stays at full apart from ordinary cactus contact; observed=" + serverValue(ServerPlayer::getHealth));
                check(serverValue(player -> player.getInventory().getItem(0).getDamageValue() == 1), "After settling, only one charge was spent");
                row.put("remaining", "Remote shelves, final charge, alternative damage sources, guard precedence, nullifying attacks, "
                    + "bypass-invulnerability damage, survival acquisition and save/reload are NOT_RUN here.");
                screenshot(context, "death-guard-settled-ten-full-hearts");
            } catch (Throwable failure) {
                try { screenshot(context, "death-guard-failure"); } catch (Throwable capture) { failure.addSuppressed(capture); }
                throw failure;
            } finally {
                context.getInput().releaseKey(GLFW.GLFW_KEY_W);
                if (observation != null) row.put("native_damage_observation", serverValue(player -> observation.details()));
                observation = null;
            }
        }
    }

    private void readBook(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final ManualProfile profile = ManualProfile.profiles().stream().filter(book -> book.sections().contains(id))
            .findFirst().orElseThrow(() -> new AssertionError("No indexed book instructions for " + id));
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(profile.id()).get()));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.player.setXRot(-70));
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, id);
        final String text = context.computeOnClient(client -> ManualArticleCatalog.article(profile, id).body().getString());
        check(!text.isBlank(), "Doll article must contain readable instructions");
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(),
                    ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), id)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> id.equals(field(client.gui.screen(), "selectedSection"))
                && expected == (int) field(client.gui.screen(), "bodyPage")), "Native Next reaches each doll instruction page");
            screenshot(context, id + "-book-" + page);
        }
        row.put("book", profile.id());
        row.put("section", id);
        row.put("book_text", text);
        row.put("book_pages_read", pages);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
    }


    private static Map<String, Object> health(final ServerPlayer player) {
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("tick", player.level().getGameTime());
        value.put("maximum_health", player.getMaxHealth());
        value.put("current_health", player.getHealth());
        value.put("absorption", player.getAbsorptionAmount());
        value.put("doll_damage", player.getInventory().getItem(0).getDamageValue());
        value.put("alive", player.isAlive());
        return value;
    }

    private static final class DeathObservation {
        private final ServerPlayer player;
        private final float originalMaximum;
        private final List<String> damageSources = new ArrayList<>();
        private boolean maximumChanged;
        private boolean activated;
        private boolean activationWasCactus;
        private boolean lastDamageWasCactus;
        private long lastDamageTick = -100;
        private float lastObservedAmount;
        private double activationY;
        private float activationAbsorption;
        private float activationHealth;
        private Map<String, Object> activation = Map.of();

        private DeathObservation(final ServerPlayer player, final float maximum) {
            this.player = player;
            originalMaximum = maximum;
        }

        private void tick() {
            maximumChanged |= player.getMaxHealth() != originalMaximum;
            if (!activated && player.getInventory().getItem(0).getDamageValue() > 0) {
                activated = true;
                activationY = player.getY();
                activationAbsorption = player.getAbsorptionAmount();
                activationHealth = player.getHealth();
                final boolean eventCactus = lastDamageWasCactus && player.level().getGameTime() - lastDamageTick <= 1;
                final DamageSource last = player.getLastDamageSource();
                final boolean vanillaCactus = last != null && last.is(DamageTypes.CACTUS);
                final boolean touchingCactus = BlockPos.betweenClosedStream(player.getBoundingBox().inflate(0.05))
                    .anyMatch(pos -> player.level().getBlockState(pos).is(Blocks.CACTUS));
                final boolean noOtherSource = damageSources.stream().allMatch("minecraft:cactus"::equals);
                activationWasCactus = eventCactus || vanillaCactus
                    || touchingCactus && player.fallDistance == 0 && noOtherSource;
                activation = health(player);
                activation.put("y", activationY);
                activation.put("native_source_is_cactus", activationWasCactus);
                activation.put("fabric_event_saw_cactus", eventCactus);
                activation.put("vanilla_last_damage_source", last == null ? "none" : last.is(DamageTypes.CACTUS) ? "minecraft:cactus" : last.getMsgId());
                activation.put("collision_box_touches_cactus", touchingCactus);
                activation.put("fall_distance", player.fallDistance);
            }
        }

        private Map<String, Object> details() {
            final Map<String, Object> value = new LinkedHashMap<>();
            value.put("sources", List.copyOf(damageSources));
            value.put("last_damage_tick", lastDamageTick);
            value.put("last_observed_damage_amount", lastObservedAmount);
            value.put("damage_observation_note", "Fabric ALLOW_DAMAGE observer always returns true. "
                + "Amount may already be zero after Warlockery protection; source and activation are observed separately.");
            value.put("maximum_health_changed", maximumChanged);
            value.put("activation", activation);
            return value;
        }
    }

    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer()));
    }

    private static Object field(final Object object, final String name) {
        try {
            final var field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots);
    }

    private void write(final boolean finished) throws Exception {
        report.put("completed", finished);
        report.put("selected_scenarios_passed", finished && failures.isEmpty());
        report.put("updated_at", System.currentTimeMillis());
        final Path temporary = evidence.resolve("brazier-and-death-protection.json.tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temporary, evidence.resolve("brazier-and-death-protection.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
