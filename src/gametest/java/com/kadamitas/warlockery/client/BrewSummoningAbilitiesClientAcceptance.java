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
import com.kadamitas.warlockery.registry.ModEffects;
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
import net.minecraft.world.entity.animal.Animal;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.item.Items;
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

public final class BrewSummoningAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final Set<BrewBehavior> COVERED = Set.of(BrewBehavior.SUMMON_BATS,
        BrewBehavior.SUMMON_MURDEROUS_FLOCK, BrewBehavior.SUMMON_POISON_TOADS, BrewBehavior.RAISE_DEAD,
        BrewBehavior.SUMMON_OWLS, BrewBehavior.SUMMON_ABYSSAL_REGENT, BrewBehavior.ATTRACT_ANIMALS,
        BrewBehavior.BREED_ANIMALS);
    private static final AABB AREA = new AABB(-16, 97, -16, 17, 120, 17);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private volatile SummonObservation observation;
    private TestSingleplayerContext world;
    private Path evidence;
    private long started;

    @Override
    public void runTest(final ClientGameTestContext context) {
        started = System.currentTimeMillis();
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("brew-summoning-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final SummonObservation current = observation;
                if (current != null && current.player.level().getServer() == server) current.tick();
            });
            final List<Identifier> ids = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals("warlockery"))
                .filter(id -> BuiltInRegistries.ITEM.getValue(id) instanceof BrewItem item
                    && item.kind().behaviors().stream().anyMatch(COVERED::contains)
                    && !item.kind().behaviors().contains(BrewBehavior.EXPLODE)).sorted().toList();
            check(!ids.isEmpty(), "Actual registry must expose summoning or animal-effect brews");
            final String configured = System.getProperty("warlockery.brewSummoningIds", "");
            final Set<String> selected = configured.isBlank() ? ids.stream().map(Identifier::toString).collect(Collectors.toSet())
                : Arrays.stream(configured.split(",")).map(String::strip)
                    .map(id -> id.contains(":") ? id : "warlockery:" + id).collect(Collectors.toSet());
            check(!selected.isEmpty() && ids.stream().map(Identifier::toString).collect(Collectors.toSet()).containsAll(selected),
                "Selected IDs must belong to the actual summoning/animal registry census");
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
                        final SummonObservation current = observation;
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
            System.out.println("WARLOCKERY_BREW_SUMMONING_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Native thrown brew evidence: " + evidence, failure);
        }
    }

    private void runBrew(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) throws Exception {
        final BrewKind kind = ((BrewItem) BuiltInRegistries.ITEM.getValue(id)).kind();
        world.getServer().runOnServer(server -> server.getGameRules().set(GameRules.SPAWN_MOBS, false, server));
        server(player -> {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent(); player.removeAllEffects();
            player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-12, 99, -12), new BlockPos(12, 114, 12)))
                player.level().setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.teleportTo(0.5, 100, 0.5); player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets();
        row.put("fixture", "Fresh disposable Survival world, normal mob spawning disabled, bedrock floor and clear air. "
            + "Only named prerequisite cows or one protected-from-sun stationary zombie are staged. Native right-click throws "
            + "one ordinary registered brew; normal projectile collision alone invokes effects. No summoned creature, love state, "
            + "ownership, targeting, damage, breeding result or navigation path is injected.");
        readBook(context, id, kind, row);
        final String expectedType = expectedType(kind);
        row.put("expected_summon_type", expectedType);
        if (kind.behaviors().contains(BrewBehavior.SUMMON_ABYSSAL_REGENT))
            row.put("known_source_discrepancy_under_test", "Item/kind names Abyssal Regent. Current handler and English guide name Emberhorn Archfiend; only actual warlockery:abyssal_regent satisfies this test.");
        server(player -> {
            final boolean animal = kind.behaviors().contains(BrewBehavior.ATTRACT_ANIMALS);
            final boolean hostileTarget = kind.behaviors().contains(BrewBehavior.SUMMON_MURDEROUS_FLOCK)
                || kind.behaviors().contains(BrewBehavior.SUMMON_OWLS);
            final List<Mob> prerequisites = new ArrayList<>();
            final Mob target = createMob(player, hostileTarget ? "minecraft:zombie" : "minecraft:cow", 3.5, 100, 0.5);
            target.setNoAi(true);
            if (hostileTarget) target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            if (animal) {
                final Animal cow = (Animal) target;
                cow.setAge(0); cow.setHealth(6.0F);
            }
            prerequisites.add(target);
            if (kind.behaviors().contains(BrewBehavior.BREED_ANIMALS)) {
                final Animal partner = (Animal) createMob(player, "minecraft:cow", 2.5, 100, 1.5);
                partner.setNoAi(true); partner.setAge(0); partner.setHealth(6.0F); prerequisites.add(partner);
            }
            player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
            player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges();
            observation = new SummonObservation(player, id, kind, prerequisites);
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(90); });
        context.waitTicks(3);
        if (kind.behaviors().contains(BrewBehavior.ATTRACT_ANIMALS)
                || kind.behaviors().contains(BrewBehavior.BREED_ANIMALS)) server(player ->
            observation.prerequisites.forEach(mob -> mob.setNoAi(false)));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        for (int tick = 0; tick < 100 && !serverValue(player -> observation.projectileSeenAndGone()); tick++) context.waitTicks(1);
        check(serverValue(player -> observation.projectileIds.size() == 1 && observation.projectileSeenAndGone()),
            "Exactly one actual owned thrown brew was observed and naturally collided");
        check(serverValue(player -> player.getInventory().getItem(0).isEmpty()), "Native throw consumes exactly the one staged brew");
        context.waitTicks(4);
        row.put("impact_observation", serverValue(player -> observation.report()));
        screenshot(context, id.getPath() + "-native-impact-result");
        if (!expectedType.isEmpty()) {
            final int count = serverValue(player -> observation.count(expectedType));
            final int minimum = expectedMinimum(kind);
            final int maximum = expectedMaximum(kind);
            check(count >= minimum && count <= maximum, "Named intended creature count " + expectedType + " must be "
                + minimum + ".." + maximum + "; actual observed IDs: " + serverValue(player -> observation.spawnCounts()));
            check(serverValue(player -> observation.spawned.values().stream().allMatch(entity -> entity.get("type").equals(expectedType))),
                "No wrong-type creature may substitute for the named summon");
            if (kind.behaviors().contains(BrewBehavior.RAISE_DEAD) || kind.behaviors().contains(BrewBehavior.SUMMON_OWLS)
                || kind.behaviors().contains(BrewBehavior.SUMMON_MURDEROUS_FLOCK)) {
                check(serverValue(player -> observation.spawned.values().stream().allMatch(entity -> Boolean.TRUE.equals(entity.get("owned_by_thrower")))),
                    "Every summoned companion is actually bound to the throwing player");
                check(serverValue(player -> observation.spawned.values().stream().allMatch(entity ->
                    observation.prerequisites.getFirst().getUUID().toString().equals(entity.get("initial_target")))),
                    "Each companion selects the intended legal staged victim, not its owner");
            }
            if (kind.behaviors().contains(BrewBehavior.RAISE_DEAD)) {
                check(serverValue(player -> observation.spawned.values().stream().allMatch(entity ->
                    ((Number) entity.get("strength_amplifier")).intValue() == 1 && ((Number) entity.get("resistance_amplifier")).intValue() == 0)),
                    "Raised Corpses receive actual Strength II and Resistance I");
                if (kind.behaviors().contains(BrewBehavior.BUFF_UNDEAD))
                    check(serverValue(player -> observation.spawned.values().stream().allMatch(entity -> Boolean.TRUE.equals(entity.get("undead_mending")))),
                        "Raising also grants actual Undead Mending to the newly owned undead");
            }
        }
        if (kind.behaviors().contains(BrewBehavior.SUMMON_POISON_TOADS))
            check(serverValue(player -> observation.maximumPoisonAmplifier == 1 && observation.maximumPoisonDuration >= 595),
                "Poison Toad brew affects the nearby non-toad victim with actual Poison II for30seconds");
        if (kind == BrewKind.BATS) check(serverValue(player -> observation.maximumSlownessAmplifier == 1 && observation.maximumWeaknessAmplifier == 0),
            "Bats hybrid also applies its actual Slowness II and Weakness I payload");
        if (kind.behaviors().contains(BrewBehavior.ATTRACT_ANIMALS)) {
            for (int tick = 0; tick < 80 && !serverValue(player -> observation.approached); tick++) context.waitTicks(1);
            check(serverValue(player -> observation.pathTowardImpact && observation.approached),
                "Actual living cow acquires a navigation path near the impact and physically approaches it by at least0.5blocks");
        }
        if (kind.behaviors().contains(BrewBehavior.BREED_ANIMALS)) {
            check(serverValue(player -> observation.loveHealth.size() == 2 && observation.loveHealth.values().stream().allMatch(health -> health == 8.0F)),
                "Both actual adult cows enter love mode and recover exactly2health");
            check(serverValue(player -> observation.loveOwnerIds.stream().allMatch(owner -> owner.equals(player.getUUID().toString()))
                && observation.loveOwnerIds.size() == 2), "Both love states attribute the throwing player");
            for (int tick = 0; tick < 240 && !serverValue(player -> observation.babyBorn); tick++) context.waitTicks(1);
            check(serverValue(player -> observation.babyBorn), "Normal animal AI mating produces an actual new baby cow after the brew");
            row.put("additional_unverified_branches", List.of("Villager bread provisioning", "Owned zombie pairing"));
        }
        screenshot(context, id.getPath() + "-verified-named-outcome");
        check(serverValue(player -> observation.observerError == null), "Passive outcome observer must complete without errors: "
            + serverValue(player -> observation.observerError));
        row.put("impact_observation", serverValue(player -> observation.report()));
    }

    private static Mob createMob(final ServerPlayer player, final String type, final double x, final double y, final double z) {
        final var entity = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(type)).create(player.level(), EntitySpawnReason.COMMAND);
        check(entity instanceof Mob, "Actual prerequisite entity registry type is available: " + type);
        final Mob mob = (Mob) entity; mob.snapTo(x, y, z);
        check(player.level().addFreshEntity(mob), "Prerequisite entity added without invoking brew behavior");
        return mob;
    }

    private static String expectedType(final BrewKind kind) {
        if (kind.behaviors().contains(BrewBehavior.SUMMON_BATS)) return "minecraft:bat";
        if (kind.behaviors().contains(BrewBehavior.SUMMON_MURDEROUS_FLOCK)) return "warlockery:hex_bat";
        if (kind.behaviors().contains(BrewBehavior.SUMMON_POISON_TOADS)) return "warlockery:toad";
        if (kind.behaviors().contains(BrewBehavior.RAISE_DEAD)) return "warlockery:corpse";
        if (kind.behaviors().contains(BrewBehavior.SUMMON_OWLS)) return "warlockery:owl";
        if (kind.behaviors().contains(BrewBehavior.SUMMON_ABYSSAL_REGENT)) return "warlockery:abyssal_regent";
        return "";
    }
    private static int expectedMinimum(final BrewKind kind) {
        if (kind.behaviors().contains(BrewBehavior.SUMMON_BATS)) return Math.clamp((int) Math.ceil(kind.potency() * 4), 2, 12);
        if (kind.behaviors().contains(BrewBehavior.SUMMON_MURDEROUS_FLOCK)) return Math.clamp((int) Math.ceil(kind.potency() * 4), 3, 8);
        if (kind.behaviors().contains(BrewBehavior.SUMMON_POISON_TOADS)) return 4;
        if (kind.behaviors().contains(BrewBehavior.SUMMON_OWLS)) return 3;
        return 1;
    }
    private static int expectedMaximum(final BrewKind kind) { return kind.behaviors().contains(BrewBehavior.RAISE_DEAD) ? 3 : expectedMinimum(kind); }

    private static final class SummonObservation {
        final ServerPlayer player;
        final Identifier item;
        final BrewKind kind;
        final List<Mob> prerequisites;
        final Set<String> prerequisiteIds;
        final Set<String> projectileIds = new LinkedHashSet<>();
        final Map<String, Map<String, Object>> spawned = new LinkedHashMap<>();
        final Map<String, Float> loveHealth = new LinkedHashMap<>();
        final Map<String, String> loveOwners = new LinkedHashMap<>();
        final List<String> loveOwnerIds = new ArrayList<>();
        final Vec3 intendedImpact = new Vec3(0.5, 100, 0.5);
        final double initialDistance;
        int projectilesPresent;
        int ticks;
        int maximumPoisonAmplifier = -1;
        int maximumPoisonDuration;
        int maximumSlownessAmplifier = -1;
        int maximumWeaknessAmplifier = -1;
        boolean pathTowardImpact;
        boolean approached;
        boolean babyBorn;
        String lastProjectilePosition;
        String observerError;
        SummonObservation(final ServerPlayer player, final Identifier item, final BrewKind kind, final List<Mob> prerequisites) {
            this.player = player; this.item = item; this.kind = kind; this.prerequisites = List.copyOf(prerequisites);
            prerequisiteIds = prerequisites.stream().map(entity -> entity.getUUID().toString()).collect(Collectors.toSet());
            initialDistance = prerequisites.getFirst().position().distanceTo(intendedImpact);
        }
        void tick() {
            try {
                ticks++;
                final var projectiles = player.level().getEntitiesOfClass(AbstractThrownPotion.class, AREA,
                    potion -> potion.getOwner() == player && potion.getItem().is(BuiltInRegistries.ITEM.getValue(item)));
                projectilesPresent = projectiles.size();
                projectiles.forEach(projectile -> { projectileIds.add(projectile.getUUID().toString()); lastProjectilePosition = projectile.position().toString(); });
                if (projectileIds.isEmpty()) return;
                for (Mob mob : player.level().getEntitiesOfClass(Mob.class, AREA)) {
                    if (prerequisiteIds.contains(mob.getUUID().toString()) || spawned.containsKey(mob.getUUID().toString())) continue;
                    final Map<String, Object> record = new LinkedHashMap<>();
                    record.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
                    record.put("java_class", mob.getClass().getName()); record.put("first_position", mob.position().toString());
                    record.put("owned_by_thrower", CreatureBehaviorState.isOwnedBy(mob, player.getUUID()));
                    record.put("initial_target", mob.getTarget() == null ? "none" : mob.getTarget().getUUID().toString());
                    record.put("strength_amplifier", amplifier(mob, MobEffects.STRENGTH));
                    record.put("resistance_amplifier", amplifier(mob, MobEffects.RESISTANCE));
                    record.put("undead_mending", mob.hasEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(
                        ModEffects.UNDEAD_MENDING.get())));
                    record.put("baby", mob.isBaby());
                    spawned.put(mob.getUUID().toString(), record);
                    if (mob instanceof Animal && mob.isBaby() && record.get("type").equals("minecraft:cow")) babyBorn = true;
                }
                final Mob target = prerequisites.getFirst();
                final var poison = target.getEffect(MobEffects.POISON);
                if (poison != null) { maximumPoisonAmplifier = Math.max(maximumPoisonAmplifier, poison.getAmplifier()); maximumPoisonDuration = Math.max(maximumPoisonDuration, poison.getDuration()); }
                maximumSlownessAmplifier = Math.max(maximumSlownessAmplifier, amplifier(target, MobEffects.SLOWNESS));
                maximumWeaknessAmplifier = Math.max(maximumWeaknessAmplifier, amplifier(target, MobEffects.WEAKNESS));
                if (projectileSeenAndGone() && kind.behaviors().contains(BrewBehavior.ATTRACT_ANIMALS)) {
                    final var navigationTarget = target.getNavigation().getTargetPos();
                    if (navigationTarget != null && Vec3.atCenterOf(navigationTarget).distanceTo(intendedImpact) <= 2.0) pathTowardImpact = true;
                    if (target.position().distanceTo(intendedImpact) < initialDistance - 0.5) approached = true;
                }
                for (Mob prerequisite : prerequisites) if (prerequisite instanceof Animal animal && animal.isInLove()
                    && !loveHealth.containsKey(animal.getUUID().toString())) {
                    loveHealth.put(animal.getUUID().toString(), animal.getHealth());
                    final String owner = animal.getLoveCause() == null ? "none" : animal.getLoveCause().getUUID().toString();
                    loveOwners.put(animal.getUUID().toString(), owner); loveOwnerIds.add(owner);
                }
            } catch (Throwable failure) { if (observerError == null) observerError = failure.toString(); }
        }
        boolean projectileSeenAndGone() { return !projectileIds.isEmpty() && projectilesPresent == 0; }
        int count(final String type) { return (int) spawned.values().stream().filter(entity -> entity.get("type").equals(type)).count(); }
        Map<String, Long> spawnCounts() { return spawned.values().stream().collect(Collectors.groupingBy(entity -> (String) entity.get("type"), LinkedHashMap::new, Collectors.counting())); }
        Map<String, Object> report() {
            final Map<String, Object> report = new LinkedHashMap<>();
            report.put("observed_server_ticks", ticks); report.put("native_projectile_uuids", projectileIds);
            report.put("last_projectile_position", lastProjectilePosition); report.put("projectiles_present", projectilesPresent);
            report.put("spawned_by_uuid", spawned); report.put("actual_type_counts", spawnCounts());
            report.put("prerequisite_entity_uuids", prerequisiteIds); report.put("initial_cow_distance", initialDistance);
            report.put("current_target_distance", prerequisites.getFirst().position().distanceTo(intendedImpact));
            report.put("actual_navigation_toward_impact", pathTowardImpact); report.put("actual_approach_at_least_half_block", approached);
            report.put("first_love_health", loveHealth); report.put("love_owner_by_animal", loveOwners); report.put("native_baby_born", babyBorn);
            report.put("maximum_poison_amplifier", maximumPoisonAmplifier); report.put("maximum_poison_duration", maximumPoisonDuration);
            report.put("maximum_slowness_amplifier", maximumSlownessAmplifier); report.put("maximum_weakness_amplifier", maximumWeaknessAmplifier);
            if (observerError != null) report.put("observer_error", observerError);
            return report;
        }
        private static int amplifier(final LivingEntity mob, final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
            final var instance = mob.getEffect(effect); return instance == null ? -1 : instance.getAmplifier();
        }
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
        final var trace = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(trace));
        row.put("failure_stack", trace.toString());
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
        report.put("execution", "Rendered Fabric development-classpath client. Actual native potion throws and normal collision, spawning, ownership, targeting, animal navigation and breeding; "
            + "passive end-of-server-tick observations. Fresh staged worlds. No direct impact/damage/world-effect invocation.");
        report.put("class_sha256", Map.of("test", classHash(BrewSummoningAbilitiesClientAcceptance.class),
            "brew_item", classHash(BrewItem.class), "brew_runtime", classHash(BrewRuntime.class)));
        report.put("summoning_behavior_census", COVERED.stream().map(Enum::name).sorted().toList());
        report.put("registered_item_count", results.size());
        report.put("items", results);
        report.put("failures", failures);
        report.put("screenshots", screenshots);
        final Path temporary = evidence.resolve("brew-summoning-abilities.json.tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temporary, evidence.resolve("brew-summoning-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
