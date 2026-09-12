package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.DollBehaviorFactory;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class DollAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final List<String> DOLLS = List.of(
        "earth_guard_doll", "water_guard_doll", "hunger_guard_doll", "fire_guard_doll", "death_guard_doll");
    private static final List<Holder<MobEffect>> OBSERVED_EFFECTS = List.of(
        MobEffects.REGENERATION, MobEffects.ABSORPTION, MobEffects.FIRE_RESISTANCE,
        MobEffects.SLOW_FALLING, MobEffects.WATER_BREATHING, MobEffects.SATURATION);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private volatile HazardObservation observation;
    private TestSingleplayerContext world;
    private Path evidence;
    private long started;

    @Override
    public void runTest(final ClientGameTestContext context) {
        started = System.currentTimeMillis();
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("doll-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final HazardObservation current = observation;
                if (current != null && current.player.level().getServer() == server) current.tick();
            });
            final String configured = System.getProperty("warlockery.dollIds", "");
            final Set<String> requested = configured.isBlank() ? Set.copyOf(DOLLS)
                : Arrays.stream(configured.split(",")).map(String::strip)
                    .map(id -> id.replaceFirst("^warlockery:", "")).collect(Collectors.toSet());
            check(!requested.isEmpty() && DOLLS.containsAll(requested), "Requested protection doll IDs must be supported");
            for (String id : DOLLS) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", requested.contains(id) ? "NOT_RUN" : "NOT_SELECTED");
                row.put("acquisition_status", "NOT_RUN");
                row.put("persistence_status", "NOT_RUN");
                results.put(id, row);
            }
            write(false);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (String id : DOLLS) {
                if (!requested.contains(id)) continue;
                final Map<String, Object> row = results.get(id);
                row.put("status", "RUNNING");
                row.put("started_at", System.currentTimeMillis());
                write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        runDoll(context, id, row);
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        recordFailure(id, row, failure);
                        try { screenshot(context, id + "-failure-in-world"); }
                        catch (Throwable capture) { row.put("screenshot_failure", capture.toString()); }
                    } finally {
                        context.getInput().releaseKey(GLFW.GLFW_KEY_W);
                        context.getInput().releaseKey(GLFW.GLFW_KEY_SPACE);
                        final HazardObservation current = observation;
                        if (current != null) row.put("hazard_observation", serverValue(player -> current.report()));
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
            System.out.println("WARLOCKERY_DOLL_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Native doll ability evidence: " + evidence, failure);
        }
    }

    private void runDoll(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
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
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 99, -8), new BlockPos(8, 114, 8))) {
                player.level().setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            player.teleportTo(0.5, 100, 0.5);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        });
        world.getConnection().waitForChunksRender();
        world.getConnection().waitForClientboundPackets();
        row.put("fixture", "Fresh disposable Survival world, HARD difficulty, natural health regeneration disabled; "
            + "safe platform, book and unbound doll staged. Health/food/air vulnerability is staged below. "
            + "Native item-use binds the doll. Hazards damage through ordinary game ticks and movement; "
            + "no damage, protection, effect or charge-consumption function is called by the test.");
        readBook(context, id, row);
        server(player -> {
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(id).get()));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(-70); });
        context.waitTicks(2);
        check(serverValue(player -> SympatheticBinding.read(player.getMainHandItem()).isEmpty()), "Doll starts unbound");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        for (int tick = 0; tick < 30 && !serverValue(player -> DollItem.isBoundTo(player.getMainHandItem(), player)); tick++) {
            context.waitTicks(1);
        }
        check(serverValue(player -> DollItem.isBoundTo(player.getMainHandItem(), player)
            && player.getMainHandItem().getDamageValue() == 0 && player.getMainHandItem().getMaxDamage() == 32
            && player.getMainHandItem().getCount() == 1),
            "Actual self-use binds one undamaged doll to this player without spending a charge");
        row.put("native_binding_uuid", serverValue(player -> player.getUUID().toString()));
        screenshot(context, id + "-self-bound");
        row.put("hazard", hazardDescription(id));
        server(player -> {
            player.removeAllEffects();
            player.setAbsorptionAmount(0);
            player.clearFire();
            player.setHealth(1);
            observation = new HazardObservation(player, id);
            switch (id) {
                case "earth_guard_doll" -> {
                    for (int z = -1; z <= 1; z++) player.level().setBlockAndUpdate(new BlockPos(0, 111, z), Blocks.STONE.defaultBlockState());
                    player.teleportTo(0.5, 112, 0.5);
                    player.setDeltaMovement(Vec3.ZERO);
                    player.resetFallDistance();
                }
                case "water_guard_doll" -> {
                    for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-1, 100, -1), new BlockPos(1, 103, 1))) {
                        final boolean wall = pos.getX() != 0 || pos.getZ() != 0 || pos.getY() == 103;
                        player.level().setBlockAndUpdate(pos, wall ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState());
                    }
                    player.setAirSupply(0);
                }
                case "hunger_guard_doll" -> {
                    player.getFoodData().setFoodLevel(0);
                    player.getFoodData().setSaturation(0);
                }
                case "fire_guard_doll" -> {
                    for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-2, 100, -2), new BlockPos(2, 101, 2))) {
                        final boolean wall = Math.abs(pos.getX()) == 2 || Math.abs(pos.getZ()) == 2;
                        player.level().setBlockAndUpdate(pos, wall ? Blocks.STONE.defaultBlockState()
                            : pos.getY() == 100 ? Blocks.LAVA.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                }
                case "death_guard_doll" -> {
                    player.level().setBlockAndUpdate(new BlockPos(0, 99, 2), Blocks.SAND.defaultBlockState());
                    player.level().setBlockAndUpdate(new BlockPos(0, 100, 2), Blocks.CACTUS.defaultBlockState());
                }
                default -> throw new AssertionError(id);
            }
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(10); });
        final boolean movement = id.equals("earth_guard_doll") || id.equals("death_guard_doll");
        if (movement) context.getInput().holdKey(GLFW.GLFW_KEY_W);
        try {
            for (int tick = 0; tick < 240; tick++) {
                if (serverValue(player -> observation.firstActivation != null || observation.sawDeath)) break;
                context.waitTicks(1);
            }
        } finally { if (movement) context.getInput().releaseKey(GLFW.GLFW_KEY_W); }
        final Map<String, Object> observed = serverValue(player -> observation.report());
        row.put("hazard_observation", observed);
        final Snapshot activated = serverValue(player -> observation.firstActivation);
        check(activated != null, "Real " + id + " hazard must activate its bound doll; observed " + observed);
        check(!serverValue(player -> observation.sawDeath) && activated.alive, "Doll must prevent actual environmental death");
        check(activated.damage == 1 && activated.count == 1 && activated.bound,
            "Protection consumes exactly one of the bound doll's32 charges and preserves the binding");
        check(activated.health >= 10 && activated.health <= 11,
            "Recovery is10 health, allowing one same-tick RegenerationII heal; observed " + activated.health);
        effect(activated, MobEffects.REGENERATION, 1, 899, 900);
        effect(activated, MobEffects.ABSORPTION, 1, 99, 100);
        if (!id.equals("fire_guard_doll")) effect(activated, MobEffects.FIRE_RESISTANCE, 0, 799, 800);
        check(activated.absorption > 0, "Native totem-style absorption must be present after protection");
        switch (id) {
            case "earth_guard_doll" -> {
                check(serverValue(player -> observation.maximumFallDistance) > 3, "Earth guard fixture must actually fall a lethal distance");
                check(activated.fallDistance == 0, "Earth protection resets the actual fall distance");
                effect(activated, MobEffects.SLOW_FALLING, 0, 199, 200);
            }
            case "water_guard_doll" -> {
                check(serverValue(player -> observation.submergedTicks) > 0, "Water guard fixture must actually be underwater");
                check(activated.air >= activated.maximumAir - 1, "Water protection restores a full air supply");
                effect(activated, MobEffects.WATER_BREATHING, 0, 599, 600);
            }
            case "hunger_guard_doll" -> {
                check(serverValue(player -> observation.starvingTicks) > 0, "Hunger guard must encounter real starvation ticks");
                check(activated.food == 20 && activated.saturation >= 10, "Hunger protection restores20 food and at least10 saturation");
                effect(activated, MobEffects.SATURATION, 0, 19, 20);
            }
            case "fire_guard_doll" -> {
                effect(activated, MobEffects.FIRE_RESISTANCE, 0, 1199, 1200);
                check(activated.fireTicks <= 0, "Fire protection clears the actual fire timer");
                for (int tick = 0; tick < 40 && !serverValue(player -> snapshot(player).safeStanding && player.getY() >= 102); tick++)
                    context.waitTicks(1);
                final Snapshot rescued = serverValue(DollAbilitiesClientAcceptance::snapshot);
                row.put("rescue_after_native_teleport", rescued);
                check(rescued.safeStanding && rescued.y >= 102 && rescued.alive,
                    "Lava protection must rescue to an actual safe standing spot above the staged basin walls");
            }
            case "death_guard_doll" -> check(serverValue(player -> observation.maximumZ) > 1.7,
                "Death guard must be reached by native walking into the cactus");
            default -> throw new AssertionError(id);
        }
        row.put("verified", "Native self-binding; actual lethal hazard intercepted; one charge spent; player remained alive; "
            + "source-defined health, common protection effects and doll-specific recovery observed on the first activation tick.");
        row.put("remaining", "Survival acquisition and save/reload; remote/shelf protection; final-charge exhaustion; "
            + "other hazard variants, wrong-hazard refusal, Death-Guard fallback priority, nullifying attacks and bypass-invulnerability sources.");
        server(player -> {
            if (id.equals("death_guard_doll")) player.level().setBlockAndUpdate(new BlockPos(0, 100, 2), Blocks.AIR.defaultBlockState());
        });
        world.getConnection().waitForClientboundPackets();
        screenshot(context, id + "-protection-outcome");
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

    private static String hazardDescription(final String id) {
        return switch (id) {
            case "earth_guard_doll" -> "Health1; native W walks off a12-block-high ledge onto the stone floor. No damage or fall distance injected.";
            case "water_guard_doll" -> "Health1, air0 staged in a sealed three-block-high water column; vanilla underwater ticks cause drowning.";
            case "hunger_guard_doll" -> "Health1, food0 and saturation0 staged on HARD difficulty; vanilla FoodData ticks cause lethal starvation.";
            case "fire_guard_doll" -> "Health1 in a real3x3 lava basin; two-block-high stone walls provide safe rescue spots inside the production search radius.";
            case "death_guard_doll" -> "Health1; native W walks into a naturally placed cactus on sand. Only Death Guard is carried.";
            default -> throw new AssertionError(id);
        };
    }

    private static void effect(final Snapshot snapshot, final Holder<MobEffect> effect,
        final int amplifier, final int minimumDuration, final int maximumDuration) {
        final String id = BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).toString();
        final EffectState state = snapshot.effects.get(id);
        check(state != null && state.amplifier == amplifier && state.duration >= minimumDuration && state.duration <= maximumDuration,
            "Expected native " + id + " amplifier=" + amplifier + " duration=" + minimumDuration + ".." + maximumDuration + ", got " + state);
    }

    private static final class HazardObservation {
        private final ServerPlayer player;
        private final String id;
        private final long firstTick;
        private int observedTicks;
        private int submergedTicks;
        private int starvingTicks;
        private int lavaTicks;
        private boolean sawDeath;
        private double maximumFallDistance;
        private double maximumZ;
        private Snapshot firstActivation;
        private Snapshot latest;

        private HazardObservation(final ServerPlayer player, final String id) {
            this.player = player;
            this.id = id;
            firstTick = player.level().getGameTime();
        }

        private void tick() {
            observedTicks++;
            if (player.isUnderWater()) submergedTicks++;
            if (player.getFoodData().getFoodLevel() == 0) starvingTicks++;
            if (player.isInLava()) lavaTicks++;
            sawDeath |= !player.isAlive();
            maximumFallDistance = Math.max(maximumFallDistance, player.fallDistance);
            maximumZ = Math.max(maximumZ, player.getZ());
            latest = snapshot(player);
            if (firstActivation == null && latest.damage > 0) firstActivation = latest;
        }

        private Map<String, Object> report() {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("item", id);
            result.put("first_server_tick", firstTick);
            result.put("observed_ticks", observedTicks);
            result.put("submerged_ticks", submergedTicks);
            result.put("starving_ticks", starvingTicks);
            result.put("lava_ticks", lavaTicks);
            result.put("maximum_fall_distance", maximumFallDistance);
            result.put("maximum_z", maximumZ);
            result.put("saw_death", sawDeath);
            result.put("first_activation", firstActivation);
            result.put("latest", latest);
            return result;
        }
    }

    private static Snapshot snapshot(final ServerPlayer player) {
        final ItemStack doll = player.getInventory().getItem(0);
        final Map<String, EffectState> effects = new LinkedHashMap<>();
        for (Holder<MobEffect> type : OBSERVED_EFFECTS) {
            final var effect = player.getEffect(type);
            if (effect != null) effects.put(BuiltInRegistries.MOB_EFFECT.getKey(type.value()).toString(),
                new EffectState(effect.getAmplifier(), effect.getDuration()));
        }
        final BlockPos feet = player.blockPosition();
        final boolean safe = player.level().isEmptyBlock(feet) && player.level().isEmptyBlock(feet.above())
            && player.level().getFluidState(feet).isEmpty() && player.level().getFluidState(feet.above()).isEmpty()
            && player.level().getBlockState(feet.below()).isFaceSturdy(player.level(), feet.below(), net.minecraft.core.Direction.UP);
        return new Snapshot(player.level().getGameTime(), player.isAlive(), player.getHealth(), player.getAbsorptionAmount(),
            player.getX(), player.getY(), player.getZ(), player.fallDistance, player.getAirSupply(), player.getMaxAirSupply(),
            player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(), player.getRemainingFireTicks(),
            safe, doll.getCount(), doll.getDamageValue(), DollItem.isBoundTo(doll, player), Map.copyOf(effects));
    }

    private record EffectState(int amplifier, int duration) { }
    private record Snapshot(long serverTick, boolean alive, float health, float absorption,
        double x, double y, double z, double fallDistance, int air, int maximumAir, int food, float saturation,
        int fireTicks, boolean safeStanding, int count, int damage, boolean bound, Map<String, EffectState> effects) { }

    private void recordFailure(final String id, final Map<String, Object> row, final Throwable failure) {
        row.put("status", "FAILED");
        row.put("failure", failure.toString());
        failures.add(id + ": " + failure);
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

    private static String classHash(final Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing loaded class bytes: " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }

    private void write(final boolean finished) throws Exception {
        if (evidence == null) return;
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", finished);
        report.put("selected_scenarios_passed", finished && failures.isEmpty());
        report.put("all_five_scenarios_passed", finished && results.size() == DOLLS.size()
            && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("started_at", started);
        report.put("updated_at", System.currentTimeMillis());
        report.put("execution", "Actual Fabric development-classpath client and fresh disposable worlds. "
            + "Native book/item input and environmental hazards with passive server end-tick observation; no direct damage or protection invocation.");
        report.put("class_sha256", Map.of("test", classHash(DollAbilitiesClientAcceptance.class),
            "doll_runtime", classHash(DollItem.class), "doll_recovery", classHash(DollBehaviorFactory.class)));
        report.put("dolls", results);
        report.put("failures", failures);
        report.put("screenshots", screenshots);
        final Path temporary = evidence.resolve("doll-abilities.json.tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temporary, evidence.resolve("doll-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
