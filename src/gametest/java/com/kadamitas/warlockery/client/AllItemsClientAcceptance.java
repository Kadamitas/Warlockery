package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.brew.BrewItem;
import com.kadamitas.warlockery.item.BiomeNoteState;
import com.kadamitas.warlockery.item.BatBallItem;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.registry.ModFluids;
import com.kadamitas.warlockery.item.ManualItem;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.item.WaystoneState;
import com.kadamitas.warlockery.registry.ModBlocks;
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
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class AllItemsClientAcceptance implements FabricClientGameTest {
    private static final BlockPos TARGET = new BlockPos(0, 100, 0);
    private static final AABB AREA = new AABB(-8, 97, -8, 9, 110, 9);
    private static final Set<String> SIMPLE_FOODS = Set.of(
        "stew", "stewraw", "ingredient_odd_porkchop_raw", "ingredient_odd_porkchop_cooked");
    private static final Set<String> BASIC_TOOLS = Set.of(
        "ritual_knife", "canesword", "deathshand", "delvealloysword", "silversword",
        "delvealloyaxe", "silveraxe", "delvealloypickaxe", "silverpickaxe",
        "delvealloyshovel", "silvershovel", "delvealloyhoe", "silverhoe");
    private static final Set<String> BIOME_RECORDERS = Set.of("biomenote", "bookbiomes2", "ingredient_book_biomes");
    private static final Map<String, String> CHALK_GLYPHS = Map.of(
        "chalkheart", "circle", "chalkritual", "circleglyphritual",
        "chalkinfernal", "circleglyphinfernal", "chalk_veil", "circleglyph_veil");
    private static final Set<String> REMEDIES = Set.of(
        "ingredient_purified_milk", "universal_antidote", "ingredient_icy_needle");
    private static final Set<String> POSITION_WAYSTONES = Set.of("ingredient_waystone", "ingredient_waystone_bound");
    private static final Set<String> BROOMS = Set.of("ingredient_broom", "ingredient_broom_enchanted");
    private static final Set<String> SPECIAL_FOODS = Set.of("ingredient_apple_wormy", "ingredient_berries_rowan",
        "ingredient_artichoke", "ingredient_warm_blood", "ingredient_redstone_soup", "ingredient_creeper_heart");
    private static final Set<String> BUCKETS = Set.of("bucketbrew", "bucketerosionbrew", "bucketspirit", "buckethollowtears");
    private static final Map<String, String> SUMMONING_ITEMS = Map.of("hornofthehunt", "thorned_pursuer",
        "ingredient_fool_skull", "pale_steed", "ingredient_bramble_colossus_seed", "bramble_colossus");
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private long started;

    @Override
    public void runTest(final ClientGameTestContext context) {
        started = System.currentTimeMillis();
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("all-items").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            try (var created = context.worldBuilder().create()) {
                world = created;
                world.getConnection().waitForChunksRender();
                final List<Identifier> ids = serverValue(player -> BuiltInRegistries.ITEM.keySet().stream()
                    .filter(id -> id.getNamespace().equals("warlockery")).sorted().toList());
                for (Identifier id : ids) {
                    final Item item = BuiltInRegistries.ITEM.getValue(id);
                    final Map<String, Object> row = new LinkedHashMap<>();
                    row.put("status", "NOT_RUN");
                    row.put("item_class", item.getClass().getName());
                    row.put("scenario", scenario(id.getPath(), item));
                    row.put("reason", pendingReason(id.getPath(), item));
                    row.put("acquisition_status", "NOT_RUN");
                    row.put("persistence_status", "NOT_RUN");
                    results.put(id.toString(), row);
                }
                writeReport();
                check(ids.size() == 584, "Expected 584 runtime item IDs; observed " + ids.size());
                check(ids.stream().map(Identifier::getPath).collect(Collectors.toSet()).equals(ModItems.ALL.keySet()),
                    "Runtime item registry must agree with the complete ModItems catalog");
                final String configured = System.getProperty("warlockery.itemIds", "");
                final Set<String> selected = configured.isBlank() ? Set.of() : Arrays.stream(configured.split(","))
                    .map(String::strip).map(id -> id.contains(":") ? id : "warlockery:" + id).collect(Collectors.toSet());
                check(selected.isEmpty() || results.keySet().containsAll(selected), "Requested item IDs must exist in the registry");
                for (Identifier id : ids) {
                    final Map<String, Object> row = results.get(id.toString());
                    if (!selected.isEmpty() && !selected.contains(id.toString())) {
                        row.put("reason", "Outside this run's explicit warlockery.itemIds selection");
                        continue;
                    }
                    if (row.get("scenario").equals("UNIMPLEMENTED")) continue;
                    row.put("status", "RUNNING");
                    row.put("started_at", System.currentTimeMillis());
                    writeReport();
                    try {
                        runItem(context, id, row);
                        row.put("status", "PARTIAL");
                        row.put("scenario_status", "PASSED");
                        row.put("reason", "The named native-use scenario passed. Acquisition, persistence and remaining behavior contracts are not claimed.");
                        screenshot(context, id.getPath() + "-outcome");
                    } catch (PendingFixture pending) {
                        row.put("status", "NOT_RUN");
                        row.put("reason", pending.getMessage());
                    } catch (Throwable failure) {
                        row.put("status", "FAILED");
                        row.put("scenario_status", "FAILED");
                        row.put("failure", failure.toString());
                        failures.add(id + ": " + failure);
                        try { screenshot(context, id.getPath() + "-failure"); }
                        catch (Throwable capture) { row.put("screenshot_failure", capture.toString()); }
                    } finally {
                        row.put("finished_at", System.currentTimeMillis());
                        writeReport();
                    }
                }
                writeReport();
            }
            if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
            System.out.println("WARLOCKERY_ITEM_SCENARIOS_COMPLETED " + evidence + "; full-item coverage remains incomplete");
        } catch (Throwable failure) {
            try {
                writeReport();
                Files.writeString(evidence.resolve("failure.txt"), failure.toString());
            } catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("All-item client acceptance evidence: " + evidence, failure);
        }
    }

    private static String scenario(final String id, final Item item) {
        if (id.equals("blood_link_doll")) return "BLOOD_LINK_TRANSFER";
        if (id.equals("ingredient_brew_soaring")) return "SOARING_BROOM_FLIGHT";
        if (item instanceof DollItem) return "DOLL_BINDING_AND_ABILITY";
        if (SPECIAL_FOODS.contains(id)) return "SPECIAL_FOOD";
        if (BUCKETS.contains(id)) return "FLUID_BUCKET";
        if (SUMMONING_ITEMS.containsKey(id)) return "ITEM_SUMMONING";
        if (id.equals("ingredient_bat_ball")) return "BAT_CAPTURE_RELEASE";
        if (id.equals("boline")) return "SHEAR_SHEEP";
        if (id.equals("ingredient_wolfsbane")) return "WOLFSBANE_TARGET";
        if (CHALK_GLYPHS.containsKey(id)) return "CHALK_DRAWING";
        if (REMEDIES.contains(id)) return "REMEDY_EFFECTS";
        if (POSITION_WAYSTONES.contains(id)) return "POSITION_WAYSTONE";
        if (BROOMS.contains(id)) return "BROOM_CLEARING";
        if (id.equals("sympathetic_vial")) return "ENTITY_BINDING";
        if (id.equals("ingredient_annointing_paste")) return "ANOINT_CAULDRON";
        if (BIOME_RECORDERS.contains(id)) return "BIOME_RECORDING";
        if (item instanceof ManualItem) return "UNIMPLEMENTED";
        if (item instanceof BlockItem) return "BLOCK_PLACEMENT";
        if (item instanceof SpawnEggItem) return "SPAWN_EGG";
        if (item instanceof BrewItem brew && brew.kind().behaviors().isEmpty() && !brew.kind().effects().isEmpty()) {
            return "POTION_EFFECT_BREW";
        }
        if (SIMPLE_FOODS.contains(id)) return "FOOD";
        final ItemStack stack = new ItemStack(item);
        if (id.startsWith("werewolf_hunter_leggings")
            || item.getClass() == Item.class && stack.has(DataComponents.EQUIPPABLE)) return "EQUIPMENT";
        if (BASIC_TOOLS.contains(id)) return "TOOL";
        return "UNIMPLEMENTED";
    }

    private static String pendingReason(final String id, final Item item) {
        if (item instanceof ManualItem) return BIOME_RECORDERS.contains(id)
            ? "Biome recording scenario pending; full navigation is owned by AllBooksClientAcceptance"
            : "Book navigation and torn-page unlocking are owned by AllBooksClientAcceptance; no result is inferred here";
        if (item instanceof BrewItem brew && !brew.kind().behaviors().isEmpty()) {
            return "Needs a native impact fixture for the exact world/entity behaviors: " + brew.kind().behaviors();
        }
        if (scenario(id, item).equals("UNIMPLEMENTED")) {
            return "No complete positive native-use assertion exists for this special item or material producer/consumer yet";
        }
        return "Native-use scenario has not executed";
    }

    private void runItem(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) throws Exception {
        stage(context, id);
        row.put("setup", "Disposable Survival world; safe dirt platform, cleared nearby entities, one item in hotbar, "
            + "health/hunger and camera position staged. Scenario-specific support, target or damage is declared below. "
            + "No item use method, recipe output, effect outcome, equipment action or spawn-egg result is directly invoked.");
        final Item item = BuiltInRegistries.ITEM.getValue(id);
        readItemGuide(context, id.getPath(), row);
        switch (row.get("scenario").toString()) {
            case "SOARING_BROOM_FLIGHT" -> soaringBroom(context, row);
            case "BLOOD_LINK_TRANSFER" -> bloodLink(context, row);
            case "BLOCK_PLACEMENT" -> place(context, (BlockItem) item, row);
            case "SPAWN_EGG" -> spawn(context, id, row);
            case "FOOD" -> eat(context, id, row);
            case "POTION_EFFECT_BREW" -> throwBrew(context, (BrewItem) item, row);
            case "EQUIPMENT" -> equip(context, id, row);
            case "TOOL" -> tool(context, id, row);
            case "BIOME_RECORDING" -> recordBiome(context, id, row);
            case "CHALK_DRAWING" -> drawChalk(context, id, row);
            case "REMEDY_EFFECTS" -> remedy(context, id, row);
            case "POSITION_WAYSTONE" -> waystone(context, id, row);
            case "BROOM_CLEARING" -> {
                if (id.getPath().equals("ingredient_broom_enchanted")) {
                    flyBroom(context, id, row);
                    stage(context, id);
                }
                clearGlyphs(context, id, row);
            }
            case "ENTITY_BINDING" -> bindEntity(context, row);
            case "ANOINT_CAULDRON" -> anoint(context, row);
            case "DOLL_BINDING_AND_ABILITY" -> doll(context, id, row);
            case "SPECIAL_FOOD" -> specialFood(context, id, row);
            case "FLUID_BUCKET" -> bucket(context, id, row);
            case "ITEM_SUMMONING" -> summonItem(context, id, row);
            case "BAT_CAPTURE_RELEASE" -> captureBat(context, row);
            case "SHEAR_SHEEP" -> shear(context, row);
            case "WOLFSBANE_TARGET" -> wolfsbane(context, row);
            default -> throw new AssertionError("Unimplemented scenario was scheduled");
        }
    }

    private void readItemGuide(final ClientGameTestContext context, final String section, final Map<String, Object> row) throws Exception {
        final var found = com.kadamitas.warlockery.item.ManualProfile.profiles().stream()
            .filter(profile -> profile.sections().contains(section)).findFirst();
        if (found.isEmpty()) return;
        final var book = found.orElseThrow();
        server(player -> {
            player.getInventory().setItem(1, new ItemStack(ModItems.ALL.get(book.id()).get()));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, section);
        final int pages = context.computeOnClient(client -> {
            try {
                var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width,
                    client.gui.screen().height), section)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        row.put("guide_book", book.id());
        row.put("guide_section", section);
        row.put("guide_text", context.computeOnClient(client -> ManualArticleCatalog.article(book, section).body().getString()));
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context,
                net.minecraft.network.chat.Component.translatable("screen.warlockery.manual.next").getString());
            screenshot(context, section + "-guide-" + page);
        }
        closeScreen(context);
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
    }

    private void bloodLink(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final UUID otherId = serverValue(player -> {
            var server = player.level().getServer();
            var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "LinkFixture");
            var other = new ServerPlayer(server, player.level(), profile, net.minecraft.server.level.ClientInformation.createDefault());
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, other,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false));
            other.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            other.setGameMode(GameType.SURVIVAL);
            other.teleportTo(0.5, 100, 0.5);
            other.setHealth(20);
            return other.getUUID();
        });
        row.put("fixture_participant", "A connected synthetic ServerPlayer target; only the activating player has a rendered native client. This does not certify real multiplayer networking.");
        try {
            world.getConnection().waitForClientboundPackets();
            context.waitFor(client -> client.level.getPlayerByUUID(otherId) != null, 40);
            look(context, new Vec3(0.5, 101, 0.5));
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            waitServer(context, player -> SympatheticBinding.read(player.getMainHandItem())
                .filter(binding -> binding.targetId().equals(otherId)).isPresent(), 30,
                "Native use on the other player must bind the Blood Link doll to that player");
            server(player -> {
                player.getFoodData().setFoodLevel(10);
                player.getAbilities().invulnerable = false;
                player.onUpdateAbilities();
                player.level().setBlockAndUpdate(player.blockPosition().below(), Blocks.MAGMA_BLOCK.defaultBlockState());
            });
            waitServer(context, player -> {
                var other = player.level().getServer().getPlayerList().getPlayer(otherId);
                return other != null && other.getHealth() < 20 && player.getMainHandItem().getDamageValue() > 0;
            }, 80, "Natural magma damage must transfer to the natively bound player and consume Blood Link durability");
            row.put("ability", "Real environmental damage transfers to the other natively bound player and spends a charge");
            row.put("holder_health", serverValue(ServerPlayer::getHealth));
            row.put("linked_health", serverValue(player -> player.level().getServer().getPlayerList().getPlayer(otherId).getHealth()));
            screenshot(context, "blood-link-transferred-damage");
        } finally {
            server(player -> {
                var other = player.level().getServer().getPlayerList().getPlayer(otherId);
                if (other != null) player.level().getServer().getPlayerList().remove(other);
                player.getAbilities().invulnerable = true;
                player.onUpdateAbilities();
            });
        }
    }

    private void stage(final ClientGameTestContext context, final Identifier id) {
        closeScreen(context);
        server(player -> {
            final var level = player.level();
            level.getEntities((Entity) null, AREA, entity -> !(entity instanceof Player)).forEach(Entity::discard);
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.DIRT.defaultBlockState());
                for (int y = 100; y <= 104; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            player.setGameMode(GameType.SURVIVAL);
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getAbilities().invulnerable = true;
            player.onUpdateAbilities();
            player.getInventory().clearContent();
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.OFFHAND)) player.setItemSlot(slot, ItemStack.EMPTY);
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
            player.teleportTo(0.5, 100, -2.5);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.waitTicks(3);
        look(context, Vec3.atCenterOf(TARGET.below()).add(0, 0.49, 0));
    }

    private void place(final ClientGameTestContext context, final BlockItem item, final Map<String, Object> row) {
        final var expected = item.getBlock() instanceof com.kadamitas.warlockery.block.ShadedGlassBlock
            ? ModBlocks.ALL.get("shadedglass").get() : item.getBlock();
        final String block = BuiltInRegistries.BLOCK.getKey(expected).toString();
        row.put("expected_block", block);
        final String support = serverValue(player -> {
            if (item.getBlock().defaultBlockState().canSurvive(player.level(), TARGET)) return "dirt floor";
            player.level().setBlockAndUpdate(TARGET.below(), Blocks.FARMLAND.defaultBlockState());
            if (item.getBlock().defaultBlockState().canSurvive(player.level(), TARGET)) return "farmland";
            player.level().setBlockAndUpdate(TARGET.below(), Blocks.DIRT.defaultBlockState());
            player.level().setBlockAndUpdate(TARGET.south(), Blocks.STONE.defaultBlockState());
            if (item.getBlock().defaultBlockState().canSurvive(player.level(), TARGET)) return "south supporting wall";
            return "unsupported fixture";
        });
        if (support.equals("unsupported fixture")) throw new PendingFixture(
            "This block requires a separate water/support/orientation fixture; no placement outcome is claimed");
        row.put("staged_support", support);
        world.getConnection().waitForClientboundPackets();
        look(context, support.equals("south supporting wall") ? new Vec3(0.5, 100.5, 1.0)
            : Vec3.atCenterOf(TARGET.below()).add(0, 0.49, 0));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getBlockState(TARGET).is(expected), 30,
            "Native placement must produce " + block + " at the clicked target");
        check(serverValue(player -> player.getMainHandItem().isEmpty()), "Survival placement must consume the one staged item");
        row.put("observed", serverValue(player -> player.level().getBlockState(TARGET).toString()));
        row.put("remaining", "Break/collect, state variants, special block behavior, menus/processing, save/reload and survival acquisition");
    }

    private void spawn(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) {
        final Identifier entityId = Identifier.fromNamespaceAndPath(id.getNamespace(),
            id.getPath().substring(0, id.getPath().length() - "_spawn_egg".length()));
        final EntityType<?> expected = BuiltInRegistries.ENTITY_TYPE.getValue(entityId);
        row.put("expected_entity", entityId.toString());
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getEntities((Entity) null, AREA,
            entity -> entity.getType() == expected).size() == 1, 40, "Egg must spawn exactly one intended entity");
        check(serverValue(player -> player.getMainHandItem().isEmpty()), "Survival egg use consumes exactly one egg");
        row.put("spawned_uuid", serverValue(player -> player.level().getEntities((Entity) null, AREA,
            entity -> entity.getType() == expected).getFirst().getUUID().toString()));
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> !client.level.getEntities((Entity) null, AREA, entity -> entity.getType() == expected).isEmpty(), 40);
        if (Set.of("spirit", "lost_soul", "poltergeist" ).contains(entityId.getPath())) {
            observeSpectralMovement(context, entityId.getPath(), UUID.fromString(row.get("spawned_uuid").toString()), row);
        }
        row.put("remaining", "Spawner interaction, creature AI/abilities, acquisition and save/reload");
    }

    private void observeSpectralMovement(final ClientGameTestContext context, final String kind,
        final UUID spawned, final Map<String, Object> row) {
        final Vec3 initial = serverValue(player -> player.level().getEntity(spawned).position());
        server(player -> {
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                player.level().setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.DIRT.defaultBlockState());
            }
            player.getAbilities().invulnerable = false;
            player.onUpdateAbilities();
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            player.teleportTo(0.5, 100, -6.5);
            if (kind.equals("lost_soul")) {
                player.level().setBlockAndUpdate(new BlockPos(5, 100, 0), Blocks.SOUL_LANTERN.defaultBlockState());
            }
            if (kind.equals("poltergeist")) {
                final var prop = new net.minecraft.world.entity.item.ItemEntity(
                    player.level(), 1.5, 100.5, 0.5, new ItemStack(Items.STICK));
                prop.setPickUpDelay(600);
                player.level().addFreshEntity(prop);
            }
        });
        world.getConnection().waitForClientboundPackets();
        look(context, initial.add(0, 0.5, 0));
        final long firstTick = serverValue(player -> player.level().getGameTime());
        final double playerStartY = serverValue(Entity::getY);
        final List<String> phases = new ArrayList<>();
        double maximumDisplacementSquared = 0;
        double maximumPlayerRise = 0;
        boolean sawLevitation = false;
        boolean sawWary = false;
        boolean sawPetition = false;
        for (int observed = 0; observed < 320; observed++) {
            final Map<String, Object> sample = serverValue(player -> {
                final Entity entity = player.level().getEntity(spawned);
                check(entity instanceof net.minecraft.world.entity.Mob && entity.isAlive(),
                    "The spawned " + kind + " must survive every normal AI tick");
                final var mob = (net.minecraft.world.entity.Mob) entity;
                check(!mob.isNoAi(), "Flight observation must keep ordinary AI enabled");
                check(mob.isNoGravity(), "The observed spectral body must retain its flying frame");
                check(mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED) != null,
                    "The native flying control requires a registered flying-speed attribute");
                final String phase = entity instanceof com.kadamitas.warlockery.entity.SpiritEntity spirit
                    ? spirit.presentationPhase().name()
                    : entity instanceof com.kadamitas.warlockery.entity.LostSoulEntity soul
                        ? soul.presentationPhase().name()
                        : ((com.kadamitas.warlockery.entity.PoltergeistEntity) entity).presentationPhase().name();
                return Map.of("position", entity.position(), "phase", phase,
                    "levitation", player.hasEffect(MobEffects.LEVITATION), "player_y", player.getY());
            });
            maximumDisplacementSquared = Math.max(maximumDisplacementSquared,
                ((Vec3) sample.get("position")).distanceToSqr(initial));
            maximumPlayerRise = Math.max(maximumPlayerRise, ((Double) sample.get("player_y")) - playerStartY);
            final String phase = (String) sample.get("phase");
            if (!phases.contains(phase)) phases.add(phase);
            sawLevitation |= (Boolean) sample.get("levitation");
            sawWary |= phase.equals("WARY");
            sawPetition |= phase.equals("PETITION") || phase.equals("SETTLE");
            context.waitTicks(1);
        }
        final long elapsed = serverValue(player -> player.level().getGameTime()) - firstTick;
        row.put("spectral_observed_server_ticks", elapsed);
        row.put("spectral_maximum_displacement", Math.sqrt(maximumDisplacementSquared));
        row.put("spectral_seen_phases", phases);
        row.put("spectral_player_rise", maximumPlayerRise);
        row.put("spectral_staged_prerequisite", "Safe 17x17 platform; a vulnerable Survival observer seven blocks from the spawn; "
            + (kind.equals("lost_soul") ? "one soul lantern five blocks east" : kind.equals("poltergeist")
                ? "one loose stick prop" : "no bound owner or forced species phase"));
        check(elapsed >= 100, "The spawned creature must remain alive through at least 100 ordinary AI ticks");
        check(maximumDisplacementSquared >= 1.0,
            "The spawned " + kind + " must actually fly at least one block under its normal AI");
        if (kind.equals("spirit")) check(sawWary, "The free Spirit must visibly enter wary withdrawal from the approaching player");
        if (kind.equals("lost_soul")) check(sawPetition, "The Lost Soul must approach the staged memorial and visibly petition or settle");
        if (kind.equals("poltergeist")) check(sawLevitation && maximumPlayerRise > 0.25,
            "The Poltergeist must apply its real lift and visibly displace the Survival player");
        row.put("spectral_ability_status", "PARTIAL");
    }

    private void eat(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) {
        server(player -> player.getFoodData().setFoodLevel(4));
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(3);
        look(context, new Vec3(0.5, 103, 8));
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try {
            waitServer(context, player -> player.getFoodData().getFoodLevel() > 4, 80,
                "Native eating must increase hunger rather than merely consume or animate");
        } finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
        final Item remainder = id.getPath().startsWith("stew") ? Items.BOWL : Items.AIR;
        check(serverValue(player -> remainder == Items.AIR ? player.getMainHandItem().isEmpty()
            : player.getMainHandItem().is(remainder)), "Food must leave the exact expected bowl/empty-hand remainder");
        row.put("food_level", serverValue(player -> player.getFoodData().getFoodLevel()));
        row.put("remaining", "Actual survival acquisition/crafting and persistence");
    }

    private void throwBrew(final ClientGameTestContext context, final BrewItem brew, final Map<String, Object> row) {
        server(player -> {
            player.getAbilities().invulnerable = false;
            player.onUpdateAbilities();
            player.setHealth(10);
            player.getFoodData().setFoodLevel(5);
        });
        world.getConnection().waitForClientboundPackets();
        look(context, new Vec3(0.5, 99.9, -2.4));
        row.put("expected_effects", brew.kind().effects().stream().map(Object::toString).toList());
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> brew.kind().effects().stream().allMatch(effect -> switch (effect.effect()) {
            case "minecraft:instant_health" -> player.getHealth() > 10;
            case "minecraft:instant_damage" -> player.getHealth() < 10;
            case "minecraft:saturation" -> player.getFoodData().getFoodLevel() > 5;
            default -> BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effect.effect()))
                .map(holder -> player.hasEffect(holder) && player.getEffect(holder).getAmplifier() >= effect.amplifier())
                .orElse(false);
        }), 100, "Native splash must apply every declared potion effect to the staged player");
        check(serverValue(player -> player.getMainHandItem().isEmpty()), "Throwing consumes the one staged splash brew");
        row.put("observed_effects", serverValue(player -> player.getActiveEffects().stream().map(Object::toString).toList()));
        row.put("remaining", "Brewing acquisition, miss/return rules, other targets, full duration and persistence");
    }

    private void equip(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) {
        final EquipmentSlot expected = serverValue(player -> {
            final var equippable = player.getMainHandItem().get(DataComponents.EQUIPPABLE);
            return equippable == null ? EquipmentSlot.LEGS : equippable.slot();
        });
        look(context, new Vec3(0.5, 103, 8));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.getItemBySlot(expected).is(BuiltInRegistries.ITEM.getValue(id)), 20,
            "Native use must equip the item in " + expected);
        check(serverValue(player -> player.getMainHandItem().isEmpty()), "Equipping moves the held stack without duplication");
        row.put("equipped_slot", expected.toString());
        row.put("armor_value", serverValue(Player::getArmorValue));
        row.put("remaining", "Defense, passive/special effects, durability, removal, ownership and persistence");
    }

    private void tool(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) {
        final String name = id.getPath();
        if (name.endsWith("pickaxe")) {
            server(player -> player.level().setBlockAndUpdate(TARGET, Blocks.STONE.defaultBlockState()));
            world.getConnection().waitForClientboundPackets();
            look(context, Vec3.atCenterOf(TARGET));
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            try { waitServer(context, player -> player.level().getBlockState(TARGET).isAir(), 140,
                "Native mining must remove the staged stone"); }
            finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); }
        } else if (name.endsWith("axe") || name.endsWith("shovel") || name.endsWith("hoe")) {
            final var start = name.endsWith("axe") ? Blocks.OAK_LOG : Blocks.DIRT;
            final var expected = name.endsWith("axe") ? Blocks.STRIPPED_OAK_LOG
                : name.endsWith("shovel") ? Blocks.DIRT_PATH : Blocks.FARMLAND;
            server(player -> player.level().setBlockAndUpdate(TARGET.below(), start.defaultBlockState()));
            world.getConnection().waitForClientboundPackets();
            look(context, Vec3.atCenterOf(TARGET.below()).add(0, 0.49, 0));
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            waitServer(context, player -> player.level().getBlockState(TARGET.below()).is(expected), 25,
                "Native tool use must produce " + BuiltInRegistries.BLOCK.getKey(expected));
        } else {
            final UUID target = serverValue(player -> {
                final var cow = net.minecraft.world.entity.EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND);
                check(cow != null, "Combat fixture cow creation must succeed");
                cow.setPos(0.5, 100, 0.2);
                cow.setNoAi(true);
                player.level().addFreshEntity(cow);
                return cow.getUUID();
            });
            world.getConnection().waitForClientboundPackets();
            look(context, new Vec3(0.5, 100.6, 0.2));
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            waitServer(context, player -> {
                final Entity entity = player.level().getEntity(target);
                return entity == null || entity instanceof LivingEntity living && living.getHealth() < living.getMaxHealth();
            }, 30, "Native weapon attack must damage the staged live target");
        }
        check(serverValue(player -> player.getMainHandItem().getDamageValue() > 0), "Tool use must spend durability");
        row.put("durability_spent", serverValue(player -> player.getMainHandItem().getDamageValue()));
        row.put("remaining", "Special abilities, material-specific targets, full drops/collection, acquisition and persistence");
    }

    private void recordBiome(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) {
        final Identifier expected = serverValue(player -> player.level().getBiome(TARGET.below()).unwrapKey().orElseThrow().identifier());
        if (!id.getPath().equals("biomenote")) context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
        try {
            context.waitTicks(2);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            waitServer(context, player -> BiomeNoteState.read(player.getMainHandItem()).filter(expected::equals).isPresent(),
                30, "Native use must record the actual clicked biome in the held item");
        } finally { context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); }
        check(serverValue(player -> player.getMainHandItem().getCount() == 1), "Biome recording preserves the item");
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> BiomeNoteState.read(client.player.getMainHandItem()).filter(expected::equals).isPresent(), 30);
        row.put("recorded_biome", expected.toString());
        row.put("remaining", "Record another biome, use downstream biome consumer, book navigation, survival acquisition and save/reload");
    }

    private void drawChalk(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) {
        final var expected = ModBlocks.ALL.get(CHALK_GLYPHS.get(id.getPath())).get();
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getBlockState(TARGET).is(expected), 30,
            "Chalk must draw its intended glyph on the clicked support");
        check(serverValue(player -> player.getMainHandItem().getDamageValue() == 1
            && player.getMainHandItem().getCount() == 1), "Drawing spends exactly one durability and preserves the chalk");
        world.getConnection().waitForClientboundPackets();
        look(context, new Vec3(0.5, 100.01, 0.5));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitTicks(8);
        check(serverValue(player -> player.level().getBlockState(TARGET).is(expected)
            && player.getMainHandItem().getDamageValue() == 1), "Repeated use on the same glyph must preserve it and spend no durability");
        row.put("drawn_glyph", BuiltInRegistries.BLOCK.getKey(expected).toString());
        row.put("duplicate_drawing", "Rejected without further durability cost");
        if (id.getPath().equals("chalkheart")) {
            context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
            try {
                context.waitTicks(2);
                look(context, Vec3.atCenterOf(TARGET.east().below()).add(0, 0.49, 0));
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                waitServer(context, player -> player.level().getBlockState(TARGET.east()).is(ModBlocks.ALL.get("circleglyphgolden").get()),
                    30, "Sneaking Golden Chalk must draw a golden ring glyph beside its center");
            } finally { context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); }
            check(serverValue(player -> player.getMainHandItem().getDamageValue() == 2), "Golden secondary drawing spends one additional durability");
            row.put("secondary_glyph", "warlockery:circleglyphgolden");
        }
        row.put("remaining", "Recipe acquisition, replacing other glyph colors, unsupported placement, connected shapes, complete ritual layouts and persistence");
    }

    private void anoint(final ClientGameTestContext context, final Map<String, Object> row) {
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitTicks(8);
        check(serverValue(player -> player.level().getBlockState(TARGET.below()).is(Blocks.DIRT)
            && player.getMainHandItem().getCount() == 1), "Paste must refuse dirt without consuming itself");
        server(player -> player.level().setBlockAndUpdate(TARGET, Blocks.CAULDRON.defaultBlockState()));
        world.getConnection().waitForClientboundPackets();
        look(context, new Vec3(0.5, 100.5, 0.01));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getBlockState(TARGET).is(ModBlocks.ALL.get("cauldron").get()),
            30, "Native paste use must transform the staged vanilla cauldron into a Warlockery cauldron");
        check(serverValue(player -> player.getMainHandItem().isEmpty()), "Anointing consumes the one paste");
        row.put("staged_prerequisite", "Empty vanilla cauldron; initial negative target was dirt");
        row.put("observed", "Vanilla cauldron transformed into warlockery:cauldron; paste consumed");
        row.put("remaining", "Acquisition, filled cauldron variants, protection integrations, machine processing and persistence");
    }

    private void remedy(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) {
        final boolean needle = id.getPath().equals("ingredient_icy_needle");
        final boolean milk = id.getPath().equals("ingredient_purified_milk");
        look(context, new Vec3(0.5, 103, 8));
        if (needle) {
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(8);
            check(serverValue(player -> player.getMainHandItem().getCount() == 1), "Icy Needle outside a dream condition must not be consumed");
        }
        server(player -> {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200));
            if (needle) {
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 1200));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 1200));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 1200));
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 1200));
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, 1200));
                if (!milk) player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 1200));
            }
        });
        row.put("staged_prerequisite", needle ? "Nausea, blindness, hunger and beneficial speed; no dream dimension or hex"
            : milk ? "Poison, wither and beneficial speed" : "Poison, wither, slowness and beneficial speed");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(2);
        if (needle) context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        else context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try {
            waitServer(context, player -> needle ? player.getMainHandItem().isEmpty()
                : player.getMainHandItem().is(Items.GLASS_BOTTLE), 80, "Native remedy use must complete and leave the correct remainder");
        } finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
        check(serverValue(player -> player.hasEffect(MobEffects.SPEED)), "The beneficial control effect must survive remedy use");
        if (needle) {
            check(serverValue(player -> !player.hasEffect(MobEffects.NAUSEA) && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.hasEffect(MobEffects.HUNGER)), "Icy Needle must remove the staged dream symptoms and hunger");
        } else if (milk) {
            check(serverValue(player -> player.hasEffect(MobEffects.POISON) != player.hasEffect(MobEffects.WITHER)),
                "Purified Milk must remove exactly one harmful effect, preserving the other");
        } else {
            check(serverValue(player -> !player.hasEffect(MobEffects.POISON) && !player.hasEffect(MobEffects.WITHER)
                && player.hasEffect(MobEffects.SLOWNESS)), "Antidote must remove poison and wither while preserving unrelated harmful slowness");
        }
        row.put("observed_effects", serverValue(player -> player.getActiveEffects().stream().map(Object::toString).toList()));
        row.put("remaining", needle ? "Sleeping, actual dream dimension return, waking-nightmare hex/entity cleanup, acquisition and persistence"
            : "Stacked inventory remainders, no-effect edge cases, acquisition and persistence");
    }

    private void bindEntity(final ClientGameTestContext context, final Map<String, Object> row) {
        final UUID target = serverValue(player -> {
            final var cow = net.minecraft.world.entity.EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND);
            check(cow != null, "Binding fixture cow creation must succeed");
            cow.setPos(0.5, 100, 0.2);
            cow.setNoAi(true);
            player.level().addFreshEntity(cow);
            return cow.getUUID();
        });
        row.put("staged_prerequisite", "One live cow with AI disabled; no binding component prewritten");
        world.getConnection().waitForClientboundPackets();
        look(context, new Vec3(0.5, 100.6, 0.2));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> SympatheticBinding.read(player.getMainHandItem())
            .filter(binding -> binding.targetId().equals(target) && binding.targetType().equals("minecraft:cow")).isPresent(),
            30, "Native vial interaction must bind the exact live target UUID and type");
        check(serverValue(player -> player.getMainHandItem().getCount() == 1), "Binding preserves one vial");
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> SympatheticBinding.read(client.player.getMainHandItem())
            .filter(binding -> binding.targetId().equals(target)).isPresent(), 30);
        row.put("bound_uuid", target.toString());
        row.put("remaining", "Other target types, bed owner binding, rebinding, downstream rituals/dolls, acquisition and persistence");
    }

    private void waystone(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) {
        final boolean alreadyPositionKind = id.getPath().equals("ingredient_waystone_bound");
        if (alreadyPositionKind) {
            final Vec3 initial = serverValue(Entity::position);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(8);
            check(serverValue(player -> player.position().distanceToSqr(initial) < 0.04
                && WaystoneState.read(player.getMainHandItem()).isEmpty() && player.getMainHandItem().getCount() == 1),
                "Uninitialized position waystone must not teleport, acquire a destination or be consumed");
            context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
            context.waitTicks(2);
        }
        try {
            if (alreadyPositionKind) context.waitFor(client -> client.player.isCrouching(), 30);
            look(context, Vec3.atCenterOf(TARGET.below()).add(0, 0.49, 0));
            context.waitFor(client -> client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && hit.getBlockPos().equals(TARGET.below()), 30);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            waitServer(context, player -> player.getMainHandItem().is(ModItems.ALL.get("ingredient_waystone_bound").get())
                && WaystoneState.read(player.getMainHandItem()).filter(location -> location.position().equals(TARGET)
                    && location.dimension().equals(player.level().dimension().identifier())).isPresent(),
                30, "Native waystone binding must record the clicked floor's stand position and actual dimension");
        } finally { context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); }
        world.getConnection().waitForClientboundPackets();
        server(player -> player.teleportTo(3.5, 100, -2.5));
        world.getConnection().waitForClientboundPackets();
        look(context, new Vec3(3.5, 103, 8));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.position().distanceToSqr(new Vec3(0.5, 100, 0.5)) < 0.04,
            30, "Native bound-waystone use must teleport the player back to the recorded position");
        check(serverValue(player -> player.getMainHandItem().getCount() == 1
            && WaystoneState.read(player.getMainHandItem()).filter(location -> location.position().equals(TARGET)).isPresent()),
            "Travel must preserve the bound item and destination");
        row.put("staged_prerequisite", "Clear safe destination; player moved three blocks away after binding; no destination component or teleport result injected");
        row.put("observed_destination", serverValue(player -> player.position().toString()));
        row.put("remaining", "Creature binding, destination safety, cross-dimension Otherwhere gating, dropped veil behavior, acquisition and persistence");
    }

    private void soaringBroom(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        readItemGuide(context, "rite_infuse_brew_soaring", row);
        look(context, new Vec3(0.5, 104, 12));
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try {
            waitServer(context, player -> player.hasEffect(com.kadamitas.warlockery.registry.ModEffects.SOARING.getHolder().orElseThrow()),
                60, "Native drinking must apply Soaring");
        } finally {
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        }
        check(serverValue(player -> player.getEffect(com.kadamitas.warlockery.registry.ModEffects.SOARING.getHolder().orElseThrow())
            .getDuration() >= 143_950 && player.getMainHandItem().is(Items.GLASS_BOTTLE)),
            "Soaring lasts two hours and drinking returns the real glass bottle");
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("ingredient_broom_enchanted").get()));
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        flyBroom(context, Identifier.parse("warlockery:ingredient_broom_enchanted"), row);
        row.put("observed", "Native drink applied two-hour Soaring and returned bottle; full mounted flight and the stronger steering response followed.");
        row.put("remaining", "Full two-hour expiry, acquisition and persistence");
    }

    private void flyBroom(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) throws Exception {
        server(player -> {
            player.getInventory().setItem(1, new ItemStack(ModItems.ALL.get("ingredient_book_circle_magic").get()));
            for (BlockPos pos : BlockPos.betweenClosed(-96, 99, -96, 96, 99, 96))
                player.level().setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_2);
        look(context, new Vec3(0.5, 105, 8));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, "rite_infuse_broom");
        final int pages = context.computeOnClient(client -> {
            try {
                var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width,
                    client.gui.screen().height), "rite_infuse_broom")).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context,
                net.minecraft.network.chat.Component.translatable("screen.warlockery.manual.next").getString());
            screenshot(context, "broom-instructions-" + page);
        }
        closeScreen(context);
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        look(context, new Vec3(0.5, 101.62, 20));
        final Vec3 launch = serverValue(Entity::position);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.getVehicle() instanceof com.kadamitas.warlockery.entity.BroomEntity
            && player.getMainHandItem().isEmpty(), 40, "Normal native use must mount the enchanted broom and transfer its item");
        context.waitFor(client -> client.player.getVehicle() instanceof com.kadamitas.warlockery.entity.BroomEntity, 40);
        screenshot(context, "broom-mounted");
        final AtomicReference<ServerPlayer> observedRider = new AtomicReference<>(serverValue(player -> player));
        final double[] steering = {Double.NaN, Double.NaN, 0.0, 0.0};
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            final ServerPlayer rider = observedRider.get();
            if (rider == null || rider.level().getServer() != server
                || !(rider.getVehicle() instanceof com.kadamitas.warlockery.entity.BroomEntity broom)) return;
            final double delta = net.minecraft.util.Mth.wrapDegrees(rider.getYRot() - steering[0]);
            if (rider.getYRot() == steering[1] && Math.abs(delta) > 5 && Math.abs(delta) < 85) {
                steering[2] += net.minecraft.util.Mth.wrapDegrees(broom.getYRot() - steering[0]) / delta;
                steering[3]++;
            }
            steering[0] = broom.getYRot();
            steering[1] = rider.getYRot();
        });
        try {
            context.getInput().holdKey(GLFW.GLFW_KEY_SPACE);
            context.waitTicks(10);
            row.put("takeoff_client", context.computeOnClient(client -> "jump=" + client.options.keyJump.isDown()
                + ",screen=" + client.gui.screen() + ",vehicle=" + client.player.getVehicle()));
            row.put("takeoff_server", serverValue(player -> player.getVehicle() instanceof com.kadamitas.warlockery.entity.BroomEntity broom
                ? "position=" + broom.position() + ",velocity=" + broom.getDeltaMovement() + ",input=" + broom.getControlInput()
                    + ",stack=" + broom.getBroomStack() + ",ground=" + broom.onGround()
                : "vehicle=" + player.getVehicle()));
            writeReport();
            waitServer(context, player -> player.getVehicle() != null && player.getVehicle().getY() >= launch.y + 3,
                60, "Space must lift the mounted broom at least three blocks");
            context.getInput().releaseKey(GLFW.GLFW_KEY_SPACE);
            final Vec3 airborne = serverValue(player -> player.getVehicle().position());
            context.getInput().holdKey(GLFW.GLFW_KEY_W);
            waitServer(context, player -> player.getVehicle().getZ() >= airborne.z + 6, 60,
                "Forward input must fly the broom through the world");
            context.runOnClient(client -> { client.player.setYRot(-90); client.player.setXRot(0); });
            final Vec3 turnStart = serverValue(player -> player.getVehicle().position());
            waitServer(context, player -> player.getVehicle().getX() >= turnStart.x + 6, 70,
                "Changing view direction while flying must steer the broom");
            final double observedTorque = serverValue(player -> steering[3] > 0 ? steering[2] / steering[3] : Double.NaN);
            final double expectedTorque = serverValue(player -> player.hasEffect(
                com.kadamitas.warlockery.registry.ModEffects.SOARING.getHolder().orElseThrow()) ? 0.22 : 0.08);
            check(Math.abs(observedTorque - expectedTorque) < 0.001,
                "Observed native steering must match the normal or Soaring response: " + observedTorque);
            row.put("observed_steering_fraction_per_server_tick", observedTorque);
            context.getInput().releaseKey(GLFW.GLFW_KEY_W);
            screenshot(context, "broom-airborne-and-steered");
            context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_ALT);
            waitServer(context, player -> player.getVehicle() instanceof com.kadamitas.warlockery.entity.BroomEntity broom
                && broom.isGliding(), 30, "Native Glide Down key must reach the server");
            final double glideHeight = serverValue(player -> player.getVehicle().getY());
            waitServer(context, player -> player.getVehicle().getY() < glideHeight - 0.25, 40,
                "Glide Down must descend while keeping the rider mounted");
            context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_ALT);
            screenshot(context, "broom-gliding");
            context.runOnClient(client -> client.player.setXRot(75));
            context.getInput().holdKey(GLFW.GLFW_KEY_W);
            waitServer(context, player -> player.getVehicle() != null && player.getVehicle().onGround(), 120,
                "Looking down and flying forward must allow a controlled landing");
            context.getInput().releaseKey(GLFW.GLFW_KEY_W);
            screenshot(context, "broom-landed");
            context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
            waitServer(context, player -> !player.isPassenger()
                && player.getMainHandItem().is(ModItems.ALL.get(id.getPath()).get()), 40,
                "Native dismount must return the same usable broom");
            check(serverValue(player -> player.getMainHandItem().getCount() == 1
                && player.getMainHandItem().getDamageValue() > 0
                && !player.getAbilities().mayfly && !player.getAbilities().flying),
                "Flight must spend durability, preserve one broom and leave Survival flight abilities disabled");
            screenshot(context, "broom-dismounted-item-returned");
            row.put("flight_behavior", "Native mount, Space takeoff, forward travel, steering, Glide Down descent, controlled landing, Shift dismount, item return and durability verified");
            row.put("flight_fixture", "Staged Survival player, enchanted broom, Circle Magic book and level landing area; no vehicle spawn, mount, velocity, control packet, teleport or landing outcome injected");
        } finally {
            observedRider.set(null);
            for (int key : new int[] {GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_LEFT_SHIFT})
                context.getInput().releaseKey(key);
        }
    }

    private void clearGlyphs(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) {
        final boolean enchanted = id.getPath().equals("ingredient_broom_enchanted");
        final int radius = enchanted ? 2 : 0;
        final var glyph = ModBlocks.ALL.get("circleglyphritual").get();
        server(player -> {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                player.level().setBlockAndUpdate(TARGET.offset(x, 0, z), glyph.defaultBlockState());
            }
            player.level().setBlockAndUpdate(TARGET.offset(radius + 1, 0, 0), glyph.defaultBlockState());
        });
        row.put("staged_prerequisite", "Pre-drawn " + ((radius * 2 + 1) * (radius * 2 + 1))
            + " ritual glyphs in the clearing square, plus one outside the declared radius");
        world.getConnection().waitForClientboundPackets();
        if (enchanted) context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
        try {
            if (enchanted) context.waitFor(client -> client.player.isCrouching(), 30);
            look(context, new Vec3(0.5, 100.01, 0.5));
            context.waitFor(client -> client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && hit.getBlockPos().equals(TARGET), 30);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            waitServer(context, player -> BlockPos.betweenClosedStream(TARGET.offset(-radius, 0, -radius), TARGET.offset(radius, 0, radius))
                .allMatch(pos -> player.level().getBlockState(pos).isAir()), 30, "Native broom use must remove every glyph inside its clearing radius");
        } finally { context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); }
        check(serverValue(player -> player.level().getBlockState(TARGET.offset(radius + 1, 0, 0)).is(glyph)
            && player.getMainHandItem().getDamageValue() == 1 && player.getMainHandItem().getCount() == 1),
            "Broom must preserve the outside glyph, remain held and spend exactly one durability for the clearing action");
        row.put("cleared_radius", radius);
        row.put("outside_glyph_preserved", true);
        row.put("remaining", enchanted ? "Soaring interaction, collision recovery, durability exhaustion, empty clearing, acquisition and persistence"
            : "Other glyph colors, empty clearing, acquisition and persistence");
    }

    private void bucket(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) {
        final var fluid = switch (id.getPath()) {
            case "bucketbrew" -> ModFluids.COLORED_BREW_WATER_SOURCE.get();
            case "bucketerosionbrew" -> ModFluids.EROSION_SOURCE.get();
            case "bucketspirit" -> ModFluids.SPIRIT_SOURCE.get();
            case "buckethollowtears" -> ModFluids.HOLLOW_TEARS_SOURCE.get();
            default -> throw new AssertionError(id);
        };
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getFluidState(TARGET).getType() == fluid
            && player.level().getFluidState(TARGET).isSource(), 25, "Native bucket use must place the intended source fluid");
        check(serverValue(player -> player.getMainHandItem().is(Items.BUCKET)), "Pouring must leave one empty bucket");
        world.getConnection().waitForClientboundPackets();
        look(context, Vec3.atCenterOf(TARGET));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.getMainHandItem().is(BuiltInRegistries.ITEM.getValue(id))
            && player.getMainHandItem().getCount() == 1 && !player.level().getFluidState(TARGET).isSource(),
            25, "Native empty-bucket use must collect the placed source into the original filled bucket");
        row.put("round_trip_fluid", BuiltInRegistries.FLUID.getKey(fluid).toString());
        row.put("remaining", "Fluid world/entity effects and survival source acquisition");
    }

    private void specialFood(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) {
        final String name = id.getPath();
        final UUID nearby = name.equals("ingredient_creeper_heart") ? stageMob(context, "minecraft:cow", 0.5, 100, -0.5) : null;
        server(player -> player.getFoodData().setFoodLevel(4));
        world.getConnection().waitForClientboundPackets();
        look(context, new Vec3(0.5, 104, 8));
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try { waitServer(context, player -> name.equals("ingredient_warm_blood") ? player.getMainHandItem().is(Items.GLASS_BOTTLE)
            : name.equals("ingredient_redstone_soup") ? player.getMainHandItem().is(Items.BOWL) : player.getMainHandItem().isEmpty(),
            80, "Native special-food consumption must complete with the intended remainder"); }
        finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
        final boolean benefit = serverValue(player -> switch (name) {
            case "ingredient_apple_wormy" -> player.getFoodData().getFoodLevel() == 4 && player.hasEffect(MobEffects.POISON);
            case "ingredient_berries_rowan" -> player.getFoodData().getFoodLevel() == 5;
            case "ingredient_artichoke" -> player.getFoodData().getFoodLevel() >= 19 && player.hasEffect(MobEffects.HUNGER)
                && player.getEffect(MobEffects.HUNGER).getAmplifier() == 2;
            case "ingredient_warm_blood" -> player.hasEffect(MobEffects.HUNGER) && player.getEffect(MobEffects.HUNGER).getAmplifier() == 1;
            case "ingredient_redstone_soup" -> player.getFoodData().getFoodLevel() == 6 && !player.hasEffect(MobEffects.HEALTH_BOOST);
            case "ingredient_creeper_heart" -> {
                final Entity cow = player.level().getEntity(nearby);
                yield player.getFoodData().getFoodLevel() == 5 && (cow == null
                    || cow instanceof LivingEntity living && living.getHealth() < living.getMaxHealth());
            }
            default -> false;
        });
        check(benefit, "Native special food must produce its exact nutrition/status/explosion outcome");
        row.put("staged_prerequisite", nearby == null ? "Human player at hunger 4"
            : "Human at hunger 4 and one live cow within explosion range; no damage or explosion invoked by test");
        row.put("food_level", serverValue(player -> player.getFoodData().getFoodLevel()));
        row.put("observed_effects", serverValue(player -> player.getActiveEffects().stream().map(Object::toString).toList()));
        if (name.equals("ingredient_redstone_soup")) {
            server(player -> {
                player.getFoodData().setFoodLevel(20);
                player.setHealth(10);
                player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
                player.inventoryMenu.broadcastChanges();
            });
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(3);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            try { waitServer(context, player -> player.getMainHandItem().is(Items.BOWL)
                && player.hasEffect(MobEffects.HEALTH_BOOST) && player.getEffect(MobEffects.HEALTH_BOOST).getAmplifier() == 1
                && player.getHealth() >= 18, 80, "A full-fed player must be able to drink Redstone Soup and receive its documented health benefit"); }
            finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
            row.put("full_hunger_health_benefit", "Health Boost II and eight health restored");
        }
        row.put("remaining", name.equals("ingredient_warm_blood") ? "Actual vampire reserve restoration and acquisition"
            : name.equals("ingredient_creeper_heart") ? "Bramble Colossus health upgrade and acquisition" : "Survival acquisition");
    }

    private void summonItem(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) {
        final boolean seed = id.getPath().equals("ingredient_bramble_colossus_seed");
        final boolean toggle = id.getPath().equals("ingredient_fool_skull");
        final var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("warlockery", SUMMONING_ITEMS.get(id.getPath())));
        if (!seed) look(context, new Vec3(0.5, 103, 8));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getEntities((Entity) null, AREA, entity -> entity.getType() == type).size() == 1,
            40, "Native summoning item must create exactly one intended creature");
        final UUID summoned = serverValue(player -> player.level().getEntities((Entity) null, AREA, entity -> entity.getType() == type).getFirst().getUUID());
        check(serverValue(player -> {
            final Entity entity = player.level().getEntity(summoned);
            return entity != null && (seed ? com.kadamitas.warlockery.entity.CreatureBehaviorState.isOwnedBy(entity, player.getUUID())
                : com.kadamitas.warlockery.data.WarlockeryEntityData.get(entity).getStringOr("WarlockerySummoningOwner", "").equals(player.getUUID().toString()));
        }), "Summoned creature must be bound to the invoking player");
        check(serverValue(player -> toggle ? player.getMainHandItem().getCount() == 1 : player.getMainHandItem().isEmpty()),
            "Summoning item must apply its exact reusable/consumable rule");
        row.put("summoned_uuid", summoned.toString());
        row.put("summoned_type", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        if (toggle) {
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            look(context, new Vec3(0.5, 108, 8));
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            waitServer(context, player -> player.level().getEntity(summoned) == null, 30, "Second skull use must dismiss its owned steed");
            check(serverValue(player -> player.getMainHandItem().getCount() == 1), "Dismissing must retain the reusable skull");
            row.put("native_dismissal", true);
        }
        row.put("remaining", "Summoned creature abilities and acquisition");
    }

    private void captureBat(final ClientGameTestContext context, final Map<String, Object> row) {
        final UUID captured = stageMob(context, "minecraft:bat", 0.5, 100.5, 0.2);
        look(context, new Vec3(0.5, 100.7, 0.2));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getEntity(captured) == null && BatBallItem.captured(player.getMainHandItem()) == 1,
            30, "Native bat-ball use must remove the actual target and store one captured bat");
        world.getConnection().waitForClientboundPackets();
        look(context, new Vec3(0.5, 104, 8));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> BatBallItem.captured(player.getMainHandItem()) == 0
            && player.level().getEntities((Entity) null, AREA, entity -> entity.getType() == net.minecraft.world.entity.EntityTypes.BAT).size() == 1,
            30, "Native release must spawn one real bat and clear the captured count");
        check(serverValue(player -> player.getMainHandItem().getCount() == 1), "Capture/release must preserve the ball");
        row.put("staged_prerequisite", "One live stationary bat; no captured component prewritten");
        row.put("captured_then_released", captured.toString());
        row.put("remaining", "Capacity boundary and acquisition");
    }

    private void shear(final ClientGameTestContext context, final Map<String, Object> row) {
        final UUID target = stageMob(context, "minecraft:sheep", 0.5, 100, 0.2);
        look(context, new Vec3(0.5, 100.6, 0.2));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getEntity(target) instanceof net.minecraft.world.entity.animal.sheep.Sheep sheep && sheep.isSheared(),
            30, "Native Boline interaction must shear the staged sheep");
        check(serverValue(player -> player.getMainHandItem().getDamageValue() == 1), "Shearing spends one Boline durability");
        row.put("staged_prerequisite", "One live unshorn stationary sheep");
        row.put("sheared_uuid", target.toString());
        row.put("remaining", "Plant harvesting and acquisition");
    }

    private void wolfsbane(final ClientGameTestContext context, final Map<String, Object> row) {
        final UUID target = stageMob(context, "warlockery:werewolf", 0.5, 100, 0.2);
        look(context, new Vec3(0.5, 100.8, 0.2));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> player.level().getEntity(target) instanceof LivingEntity living
            && living.hasEffect(MobEffects.WEAKNESS) && living.getEffect(MobEffects.WEAKNESS).getAmplifier() == 1
            && living.hasEffect(MobEffects.SLOWNESS) && player.getMainHandItem().isEmpty(),
            30, "Native Wolfsbane use must weaken the tagged werewolf and consume the plant");
        row.put("staged_prerequisite", "One living stationary werewolf; no statuses seeded");
        row.put("affected_uuid", target.toString());
        row.put("remaining", "Human refusal, transformed-player target and acquisition");
    }

    private void doll(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) {
        final String name = id.getPath();
        final boolean remote = Set.of("doll", "hexing_doll", "blood_link_doll").contains(name);
        final UUID target = remote ? stageMob(context, "minecraft:cow", 0.5, 100, 0.2) : serverValue(Entity::getUUID);
        look(context, remote ? new Vec3(0.5, 100.6, 0.2) : new Vec3(0.5, 104, 8));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        waitServer(context, player -> SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targetId().equals(target)).isPresent(),
            30, "Native doll use must establish the intended self or live-target binding");
        row.put("bound_uuid", target.toString());
        row.put("staged_prerequisite", remote ? "One living stationary cow; no doll binding prewritten" : "Unbound doll; player native self-use");
        row.put("ability_status", "NOT_RUN");
        world.getConnection().waitForClientboundPackets();
        if (name.equals("hexing_doll")) {
            look(context, new Vec3(0.5, 105, 8));
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            waitServer(context, player -> player.level().getEntity(target) instanceof LivingEntity living
                && living.getHealth() <= living.getMaxHealth() - 4 && player.getMainHandItem().getDamageValue() == 1,
                30, "Native remote doll prick must damage the bound cow and spend one charge");
            row.put("ability_status", "PARTIAL");
            row.put("tested_ability", "Remote prick");
        } else if (name.equals("tool_mending_doll") || name.equals("armor_mending_doll")) {
            final boolean armor = name.equals("armor_mending_doll");
            server(player -> {
                final ItemStack damaged = new ItemStack(armor ? Items.IRON_CHESTPLATE : Items.IRON_PICKAXE);
                damaged.setDamageValue(20);
                if (armor) player.setItemSlot(EquipmentSlot.CHEST, damaged);
                else player.setItemSlot(EquipmentSlot.OFFHAND, damaged);
                player.inventoryMenu.broadcastChanges();
            });
            waitServer(context, player -> player.getItemBySlot(armor ? EquipmentSlot.CHEST : EquipmentSlot.OFFHAND).getDamageValue() <= 16
                && player.getMainHandItem().getDamageValue() >= 2, 60, "The natively bound doll must repair the staged equipment across two scheduled cycles while spending charges");
            row.put("ability_status", "PARTIAL");
            row.put("tested_ability", armor ? "Worn armor repair" : "Held tool repair");
            row.put("additional_prerequisite", "One damaged iron item equipped after binding; no repair method called");
        }
        check(serverValue(player -> player.getMainHandItem().getCount() == 1), "Native doll binding or ability must retain its usable stack");
        row.put("remaining", name.equals("doll") ? "Template recipe consumer and acquisition"
            : "Other doll-specific hazard/protection/link/hex actions, powered shelf use and acquisition; binding alone is not an ability pass");
    }

    private UUID stageMob(final ClientGameTestContext context, final String typeId, final double x, final double y, final double z) {
        final UUID id = serverValue(player -> {
            final Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(typeId)).create(player.level(), EntitySpawnReason.COMMAND);
            check(entity instanceof LivingEntity, "Fixture must create a living " + typeId);
            entity.setPos(x, y, z);
            if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
            player.level().addFreshEntity(entity);
            return entity.getUUID();
        });
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.level.getEntities((Entity) null, AREA, entity -> entity.getUUID().equals(id)).size() == 1, 30);
        return id;
    }

    private void waitServer(final ClientGameTestContext context, final Predicate<ServerPlayer> condition,
        final int ticks, final String failure) {
        for (int tick = 0; tick < ticks; tick++) {
            if (serverValue(condition::test)) return;
            context.waitTicks(1);
        }
        check(serverValue(condition::test), failure);
    }

    private static void look(final ClientGameTestContext context, final Vec3 target) {
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(2);
    }

    private static void closeScreen(final ClientGameTestContext context) {
        if (context.computeOnClient(client -> client.gui.screen() != null)) {
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitFor(client -> client.gui.screen() == null, 30);
        }
    }

    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }

    private <T> T serverValue(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> value = new AtomicReference<>();
        server(player -> value.set(action.apply(player)));
        return value.get();
    }

    private void screenshot(final ClientGameTestContext context, final String name) throws Exception {
        ManualClientAcceptance.saveScreenshot(context, evidence, String.format("%04d-%s", screenshots.size(), name), screenshots);
    }

    private void writeReport() throws Exception {
        if (evidence == null) return;
        final Map<String, Long> counts = results.values().stream().collect(Collectors.groupingBy(
            row -> row.get("status").toString(), LinkedHashMap::new, Collectors.counting()));
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("passed", false);
        report.put("all_items_complete", false);
        report.put("selected_scenarios_failed", failures.size());
        report.put("item_count", results.size());
        report.put("status_counts", counts);
        report.put("started_at", started);
        report.put("updated_at", System.currentTimeMillis());
        report.put("pid", ProcessHandle.current().pid());
        report.put("test_class_sha256", classHash(AllItemsClientAcceptance.class));
        report.put("runtime_item_registration_class_sha256", classHash(ModItems.class));
        report.put("execution", "Rendered Fabric client; actual native mouse/key item interactions with server outcome reads. "
            + "This is an incremental manifest, not a complete all-item verification receipt. PARTIAL means only the named scenario passed.");
        report.put("items", results);
        report.put("failures", failures);
        report.put("screenshots", screenshots);
        Files.writeString(evidence.resolve("all-items.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static String classHash(final Class<?> type) throws Exception {
        try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (input == null) return "UNAVAILABLE";
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        }
    }

    private static final class PendingFixture extends RuntimeException {
        private PendingFixture(final String reason) { super(reason); }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
