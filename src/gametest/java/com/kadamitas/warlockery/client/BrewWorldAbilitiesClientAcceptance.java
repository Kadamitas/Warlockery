package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.brew.BrewBehavior;
import com.kadamitas.warlockery.brew.BrewItem;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.brew.BrewMarkerKind;
import com.kadamitas.warlockery.brew.BrewMarkerState;
import com.kadamitas.warlockery.brew.BrewRuntime;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModFluids;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class BrewWorldAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final Set<BrewBehavior> COVERED = Set.of(BrewBehavior.FREEZE, BrewBehavior.ICE_SHELL,
        BrewBehavior.PLACE_SNOW, BrewBehavior.ERODE, BrewBehavior.PLACE_WEB, BrewBehavior.PLACE_VINES,
        BrewBehavior.IGNITE);
    private static final AABB AREA = new AABB(-10, 97, -10, 11, 115, 11);
    private static final List<BlockPos> WATER = List.of(new BlockPos(-1, 99, 1), new BlockPos(0, 99, 2), new BlockPos(1, 99, 1));
    private static final BlockPos OBSIDIAN = new BlockPos(1, 100, 2);
    private static final BlockPos CALCITE = new BlockPos(-1, 100, 1);
    private static final BlockPos PRESERVED = new BlockPos(1, 100, -1);
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
            .resolve("brew-world-abilities").resolve(UUID.randomUUID().toString());
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
            check(!ids.isEmpty(), "Actual registry must expose world-effect brews");
            final String configured = System.getProperty("warlockery.brewWorldIds", "");
            final Set<String> selected = configured.isBlank() ? ids.stream().map(Identifier::toString).collect(Collectors.toSet())
                : Arrays.stream(configured.split(",")).map(String::strip)
                    .map(id -> id.contains(":") ? id : "warlockery:" + id).collect(Collectors.toSet());
            check(!selected.isEmpty() && ids.stream().map(Identifier::toString).collect(Collectors.toSet()).containsAll(selected),
                "Selected IDs must belong to the actual four-family registry census");
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
                        context.getInput().releaseKey(GLFW.GLFW_KEY_W);
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
            System.out.println("WARLOCKERY_BREW_WORLD_ABILITIES_PASSED " + evidence);
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
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-9, 98, -9), new BlockPos(9, 111, 9))) {
                player.level().setBlockAndUpdate(pos, pos.getY() <= 99 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            resetPosition(player);
        });
        world.getConnection().waitForChunksRender();
        world.getConnection().waitForClientboundPackets();
        row.put("fixture", "Fresh Survival world; bedrock platform and specific prerequisite water, rocks or wall are staged. "
            + "One ordinary unmodified brew and one stationary cow are supplied. Native right-click creates the thrown potion; "
            + "normal projectile collision invokes all production behavior. No test invokes impact, damage, growth, markers, "
            + "freezing or resulting block/effect placement. Registered aliases are tested separately.");
        readBook(context, id, kind, row);
        double baseline = 0;
        if (kind.behaviors().contains(BrewBehavior.PLACE_WEB)) {
            baseline = walk(context, 10);
            check(baseline > 0.5, "Control walk must actually advance over the unmodified floor");
            server(BrewWorldAbilitiesClientAcceptance::resetPosition);
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(4);
            row.put("control_walk_distance_10_client_ticks", baseline);
        }
        final UUID target = serverValue(player -> {
            if (kind.behaviors().contains(BrewBehavior.FREEZE)) {
                WATER.forEach(pos -> player.level().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState()));
            }
            if (kind.behaviors().contains(BrewBehavior.ERODE)) {
                player.level().setBlockAndUpdate(OBSIDIAN, Blocks.OBSIDIAN.defaultBlockState());
                player.level().setBlockAndUpdate(CALCITE, Blocks.CALCITE.defaultBlockState());
            }
            if (kind.behaviors().contains(BrewBehavior.PLACE_VINES)) {
                for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-5, 100, 2), new BlockPos(5, 106, 2))) {
                    player.level().setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
                }
            }
            player.level().setBlockAndUpdate(PRESERVED, Blocks.BEDROCK.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(2, 100, 1), Blocks.BEDROCK.defaultBlockState());
            final Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:cow"))
                .create(player.level(), EntitySpawnReason.COMMAND);
            check(entity instanceof Mob, "Cow fixture must be a real living mob");
            final Mob cow = (Mob) entity;
            cow.setPos(2.5, 101, 1.5);
            cow.setNoAi(true);
            if (kind.behaviors().contains(BrewBehavior.ERODE)) {
                cow.setItemSlot(EquipmentSlot.HEAD, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:iron_helmet"))));
            }
            player.level().addFreshEntity(cow);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
            observation = new ImpactObservation(player, cow, id);
            return cow.getUUID();
        });
        row.put("target_uuid", target.toString());
        row.put("target_fixture", "Stationary full-health cow on elevated bedrock pedestal; Erosion adds one undamaged iron helmet. "
            + "No familiar is supplied; vines use ordinary unaided reach.");
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(90); });
        context.waitTicks(2);
        if (kind.behaviors().contains(BrewBehavior.FREEZE)) check(serverValue(player -> WATER.stream()
            .allMatch(pos -> player.level().getFluidState(pos).isSource())), "Freeze fixture starts with actual source water");
        screenshot(context, id.getPath() + "-before-native-throw");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        for (int tick = 0; tick < 100; tick++) {
            if (serverValue(player -> !observation.projectileIds.isEmpty() && observation.projectilesPresent == 0)) break;
            context.waitTicks(1);
        }
        check(serverValue(player -> observation.projectileIds.size() == 1 && observation.projectilesPresent == 0),
            "One real owned thrown potion must exist and finish its native impact");
        check(serverValue(player -> player.getMainHandItem().isEmpty()), "One native throw consumes the supplied brew");
        context.waitTicks(2);
        final List<String> checks = new ArrayList<>();
        checks.add("Exactly one native owned projectile observed; it impacted/disappeared and consumed one brew.");
        if (kind.behaviors().contains(BrewBehavior.IGNITE)) {
            check(serverValue(player -> observation.maximumFireTicks >= 120 * kind.potency() - 5),
                "Native splash ignites the actual nearby cow for the configured duration");
            check(serverValue(player -> !blocks(player, Blocks.FIRE).isEmpty()),
                "Native flame brew places supported fire");
            context.waitTicks(25);
            check(serverValue(player -> observation.target.getHealth() < observation.initialHealth),
                "The ignited creature takes real fire damage during subsequent ticks");
            checks.add("Creature ignited for configured duration, supported fire formed, subsequent burning damaged it.");
        }
        if (kind.behaviors().contains(BrewBehavior.FREEZE)) {
            check(serverValue(player -> WATER.stream().allMatch(pos -> player.level().getBlockState(pos).is(Blocks.ICE))),
                "Native freeze converts all three nearby source-water samples to ice");
            check(serverValue(player -> observation.maximumFrozen >= observation.target.getTicksRequiredToFreeze() + 90),
                "Actual nearby cow receives source-defined freezing ticks");
            if (!kind.effects().isEmpty()) check(serverValue(player -> observation.maximumSlownessAmplifier >= 2),
                "Freeze/Frost splash also applies the configured SlownessIII to the actual cow");
            checks.add("Three source waters became ice; actual cow froze; configured slowness observed where present.");
        }
        if (kind.behaviors().contains(BrewBehavior.ICE_SHELL)) {
            check(serverValue(player -> blocks(player, Blocks.PACKED_ICE).size()) > 20,
                "Native ice-shell behavior creates an actual enclosing packed-ice shell");
            check(serverValue(player -> blocks(player, Blocks.PACKED_ICE).stream().mapToInt(BlockPos::getY).max().orElse(0)) >= 103,
                "Packed-ice shell extends above ground level");
            checks.add("Actual packed-ice shell contains more than20 blocks and extends overhead.");
        }
        if (kind.behaviors().contains(BrewBehavior.PLACE_SNOW)) {
            check(serverValue(player -> !blocks(player, Blocks.SNOW).isEmpty()), "Native snowy brew places real snow layers on supported floor");
            if (kind.behaviors().contains(BrewBehavior.APPLY_SNOW_TRAIL)) check(serverValue(player -> observation.maximumSnowTrail > 0),
                "SnowBurst also applies its real persistent snow-trail marker");
            checks.add("Snow layers placed on actual supported floor; SnowBurst marker observed when applicable.");
        }
        if (kind.behaviors().contains(BrewBehavior.ERODE)) {
            check(serverValue(player -> !player.level().getBlockState(OBSIDIAN).is(Blocks.OBSIDIAN)
                && !player.level().getBlockState(CALCITE).is(Blocks.CALCITE)), "Erosion removes both tagged obsidian and calcite samples");
            check(serverValue(player -> observation.maximumObsidianDrops >= 1), "Obsidian destruction must produce its real item drop");
            check(serverValue(player -> sourceFluids(player) > 0), "Erosion must place its actual source fluid after impact");
            check(serverValue(player -> observation.firstErosionHealth != null
                && Math.abs(observation.initialHealth - observation.firstErosionHealth - 8.0F * kind.potency()) < 0.01F),
                "Actual nearby cow receives exactly the configured initial magic damage");
            check(serverValue(player -> observation.firstErosionWear >= 2 && observation.firstErosionWear <= 3),
                "Erosion impact spends two helmet durability, allowing same-tick periodic wear");
            check(serverValue(player -> observation.maximumErosion >= 598 && observation.maximumErosion <= 600),
                "Erosion applies its source-defined600-tick ongoing marker");
            final int before = serverValue(player -> observation.target.getItemBySlot(EquipmentSlot.HEAD).getDamageValue());
            context.waitTicks(25);
            check(serverValue(player -> observation.target.isAlive()
                && observation.target.getItemBySlot(EquipmentSlot.HEAD).getDamageValue() > before),
                "Normal subsequent game ticks continue erosion helmet wear without another throw");
            checks.add("Tagged rocks removed; obsidian item dropped; source acid placed;8 magic damage and2 initial helmet wear; ongoing erosion wear followed.");
        }
        if (kind.behaviors().contains(BrewBehavior.PLACE_WEB)) {
            check(serverValue(player -> !blocks(player, Blocks.COBWEB).isEmpty()
                && player.level().getBlockState(player.blockPosition()).is(Blocks.COBWEB)),
                "Native web impact creates real webs including the thrower's feet");
            final double slowed = walk(context, 10);
            row.put("web_walk_distance_10_client_ticks", slowed);
            check(slowed < baseline * 0.55 + 0.1, "Actual cobweb must substantially slow the same native forward input");
            checks.add("Real surface webs formed; native10-tick walking slowed compared with the prethrow control.");
        }
        if (kind.behaviors().contains(BrewBehavior.PLACE_VINES)) {
            final List<BlockPos> vines = serverValue(player -> blocks(player, Blocks.VINE));
            check(vines.size() >= 3 && vines.stream().map(BlockPos::getY).distinct().count() >= 2,
                "Native vine brew must grow several attached vines across more than one wall height");
            check(serverValue(player -> vines.stream().allMatch(pos -> {
                final var state = player.level().getBlockState(pos);
                return state.canSurvive(player.level(), pos);
            }) && vines.stream().anyMatch(pos -> player.level().getBlockState(pos).getValue(VineBlock.SOUTH)
                && player.level().getBlockState(pos.south()).is(Blocks.BEDROCK))),
                "All observed vines have valid support and at least one attaches to the staged south wall");
            checks.add("Attached vines grew vertically along the staged wall with valid support; no familiar-assisted reach claimed.");
        }
        check(serverValue(player -> player.level().getBlockState(PRESERVED).is(Blocks.BEDROCK)),
            "Unrelated nonreplaceable bedrock sample survives each world-effect brew");
        checks.add("Unrelated bedrock preserved.");
        row.put("checks", checks);
        row.put("result_blocks", serverValue(player -> Map.of(
            "ice", blockCoordinates(player, Blocks.ICE), "packed_ice", blockCoordinates(player, Blocks.PACKED_ICE),
            "snow", blockCoordinates(player, Blocks.SNOW), "cobweb", blockCoordinates(player, Blocks.COBWEB),
            "vines", blockCoordinates(player, Blocks.VINE), "erosion_source_count", sourceFluids(player))));
        row.put("remaining", "Survival acquisition and persistence; every environmental variant and boundary; "
            + "custom/lingering delivery; familiar bonuses; failed-vine refund; full timed snow-trail travel. "
            + "This receipt covers only the named native world-effect and entity checks.");
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.player.setXRot(25));
        screenshot(context, id.getPath() + "-native-world-outcome");
    }

    private void readBook(final ClientGameTestContext context, final Identifier id, final BrewKind kind,
        final Map<String, Object> row) throws Exception {
        final String section = "brew_entry_" + kind.id();
        final var candidate = ManualProfile.profiles().stream().filter(profile -> profile.sections().contains(section)).findFirst();
        if (candidate.isEmpty()) { row.put("book_status", "NO_INDEXED_CANONICAL_GUIDE"); return; }
        final ManualProfile profile = candidate.orElseThrow();
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(profile.id()).get()));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.player.setXRot(-75));
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
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
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
    }

    private double walk(final ClientGameTestContext context, final int ticks) {
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); });
        final Vec3 before = serverValue(Entity::position);
        context.getInput().holdKey(GLFW.GLFW_KEY_W);
        try { context.waitTicks(ticks); }
        finally { context.getInput().releaseKey(GLFW.GLFW_KEY_W); }
        return Math.sqrt(serverValue(player -> player.position().subtract(before).horizontalDistanceSqr()));
    }

    private static void resetPosition(final ServerPlayer player) {
        player.teleportTo(0.5, 100, 0.5);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    private static List<BlockPos> positions() {
        return BlockPos.betweenClosedStream(new BlockPos(-8, 99, -8), new BlockPos(8, 108, 8)).map(BlockPos::immutable).toList();
    }

    private static List<BlockPos> blocks(final ServerPlayer player, final Block block) {
        return positions().stream().filter(pos -> player.level().getBlockState(pos).is(block)).toList();
    }

    private static List<String> blockCoordinates(final ServerPlayer player, final Block block) {
        return blocks(player, block).stream().map(pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ()).toList();
    }

    private static long sourceFluids(final ServerPlayer player) {
        return positions().stream().filter(pos -> player.level().getFluidState(pos).isSource()
            && player.level().getFluidState(pos).getType() == ModFluids.EROSION_SOURCE.get()).count();
    }

    private static final class ImpactObservation {
        private final ServerPlayer player;
        private final Mob target;
        private final Identifier item;
        private final float initialHealth;
        private final Set<String> projectileIds = new LinkedHashSet<>();
        private int projectilesPresent;
        private int observedTicks;
        private int maximumFrozen;
        private int maximumFireTicks;
        private int maximumSlownessAmplifier = -1;
        private int maximumErosion;
        private int maximumSnowTrail;
        private int maximumObsidianDrops;
        private Float firstErosionHealth;
        private int firstErosionWear;
        private String lastProjectilePosition;

        private ImpactObservation(final ServerPlayer player, final Mob target, final Identifier item) {
            this.player = player;
            this.target = target;
            this.item = item;
            initialHealth = target.getHealth();
        }

        private void tick() {
            observedTicks++;
            final List<AbstractThrownPotion> projectiles = player.level().getEntitiesOfClass(AbstractThrownPotion.class, AREA,
                potion -> potion.getOwner() == player && potion.getItem().is(BuiltInRegistries.ITEM.getValue(item)));
            projectilesPresent = projectiles.size();
            for (AbstractThrownPotion projectile : projectiles) {
                projectileIds.add(projectile.getUUID().toString());
                lastProjectilePosition = projectile.position().toString();
            }
            maximumFrozen = Math.max(maximumFrozen, target.getTicksFrozen());
            maximumFireTicks = Math.max(maximumFireTicks, target.getRemainingFireTicks());
            final var slow = target.getEffect(MobEffects.SLOWNESS);
            if (slow != null) maximumSlownessAmplifier = Math.max(maximumSlownessAmplifier, slow.getAmplifier());
            final int erosion = BrewMarkerState.remainingTicks(target, BrewMarkerKind.EROSION);
            maximumErosion = Math.max(maximumErosion, erosion);
            maximumSnowTrail = Math.max(maximumSnowTrail, BrewMarkerState.remainingTicks(target, BrewMarkerKind.SNOW_TRAIL));
            if (erosion > 0 && firstErosionHealth == null) {
                firstErosionHealth = target.getHealth();
                firstErosionWear = target.getItemBySlot(EquipmentSlot.HEAD).getDamageValue();
            }
            final int drops = player.level().getEntitiesOfClass(ItemEntity.class, AREA,
                entity -> entity.getItem().is(Blocks.OBSIDIAN.asItem())).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
            maximumObsidianDrops = Math.max(maximumObsidianDrops, drops);
        }

        private Map<String, Object> report() {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("observed_server_ticks", observedTicks);
            result.put("native_projectile_uuids", projectileIds);
            result.put("projectiles_still_present", projectilesPresent);
            result.put("last_observed_projectile_position", lastProjectilePosition);
            result.put("target_initial_health", initialHealth);
            result.put("target_current_health", target.getHealth());
            result.put("target_alive", target.isAlive());
            result.put("maximum_frozen_ticks", maximumFrozen);
            result.put("maximum_fire_ticks", maximumFireTicks);
            result.put("maximum_slowness_amplifier", maximumSlownessAmplifier);
            result.put("maximum_erosion_marker_ticks", maximumErosion);
            result.put("first_erosion_health", firstErosionHealth);
            result.put("first_erosion_helmet_wear", firstErosionWear);
            result.put("current_helmet_wear", target.getItemBySlot(EquipmentSlot.HEAD).getDamageValue());
            result.put("maximum_obsidian_item_drop_count", maximumObsidianDrops);
            result.put("maximum_snow_trail_ticks", maximumSnowTrail);
            return result;
        }
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
        report.put("class_sha256", Map.of("test", classHash(BrewWorldAbilitiesClientAcceptance.class),
            "brew_item", classHash(BrewItem.class), "brew_runtime", classHash(BrewRuntime.class)));
        report.put("family_behavior_census", COVERED.stream().map(Enum::name).sorted().toList());
        report.put("registered_item_count", results.size());
        report.put("items", results);
        report.put("failures", failures);
        report.put("screenshots", screenshots);
        final Path temporary = evidence.resolve("brew-world-abilities.json.tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temporary, evidence.resolve("brew-world-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
