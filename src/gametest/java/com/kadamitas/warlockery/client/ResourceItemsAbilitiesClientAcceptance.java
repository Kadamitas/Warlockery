package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.LeechChestMemory;
import com.kadamitas.warlockery.block.entity.DollShelfBlockEntity;
import com.kadamitas.warlockery.block.entity.MagicMachineBlockEntity;
import com.kadamitas.warlockery.crafting.MachineProfiles;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.ModBlocks;
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
import java.util.function.Predicate;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;
import com.kadamitas.warlockery.item.MirrorState;

import com.kadamitas.warlockery.item.*;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
public final class ResourceItemsAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final List<String> DEVICES = List.of("ingredient_attuned_stone", "ingredient_attuned_stone_charged",
        "ingredient_bone_needle", "ingredient_creeper_heart", "ingredient_graveyard_dust", "ingredient_artichoke",
        "ingredient_subdued_spirit", "ingredient_subdued_spirit_village", "mutator", "seedsdreamroot", "ingredient_rock");
    private static final BlockPos DEVICE = new BlockPos(0, 100, 0);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private volatile ResourceObservation observation;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("resource-items-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                final var current = observation;
                if (current != null && current.player.level() == level) current.loaded(entity);
            });
            final String configured = System.getProperty("warlockery.resourceItemIds", "");
            final Set<String> requested = configured.isBlank() ? Set.copyOf(DEVICES) : Arrays.stream(configured.split(","))
                .map(String::strip).map(id -> id.replaceFirst("^warlockery:", "")).collect(Collectors.toSet());
            check(!requested.isEmpty() && DEVICES.containsAll(requested), "Requested resource IDs must have an implemented native scenario");
            for (String id : DEVICES) check(ModItems.ALL.containsKey(id) && ResourceUtilityItemFactory.supports(id), "Registered resource factory ID exists: " + id);
            for (String id : DEVICES) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", requested.contains(id) ? "NOT_RUN" : "NOT_SELECTED");
                row.put("acquisition_status", "NOT_RUN");
                row.put("persistence_status", "NOT_RUN");
                results.put(id, row);
            }
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (String id : DEVICES) {
                if (!requested.contains(id)) continue;
                final Map<String, Object> row = results.get(id);
                row.put("status", "RUNNING");
                write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context, row);
                        readGuides(context, id, row);
                        switch (id) {
                            case "ingredient_attuned_stone", "ingredient_attuned_stone_charged" -> stone(context, id, row);
                            case "ingredient_bone_needle" -> needle(context, row);
                            case "ingredient_creeper_heart" -> heart(context, row);
                            case "ingredient_graveyard_dust" -> dust(context, row);
                            case "ingredient_artichoke" -> artichoke(context, row);
                            case "ingredient_subdued_spirit", "ingredient_subdued_spirit_village" -> village(context, id, row);
                            case "mutator" -> mutator(context, row);
                            case "seedsdreamroot" -> bulb(context, row);
                            case "ingredient_rock" -> rock(context, row);
                            default -> throw new UnsupportedOperationException(id);
                        }
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        fail(id, row, failure);
                        try { screenshot(context, id + "-failure-in-world"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseKey(GLFW.GLFW_KEY_W);
                        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                        context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
                        if (observation != null) row.put("last_runtime_observation", serverValue(player -> observation.report()));
                        observation = null;
                        write(false);
                    }
                } catch (Throwable failure) {
                    fail(id, row, failure);
                } finally { observation = null; world = null; }
                write(false);
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_RESOURCE_ITEMS_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Native device evidence: " + evidence, failure);
        }
    }

    private void stage(final ClientGameTestContext context, final Map<String, Object> row) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.HARD, true);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            player.setAbsorptionAmount(0);
            player.getFoodData().setFoodLevel(20);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 99, -8), new BlockPos(12, 108, 8)))
                player.level().setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.teleportTo(0.5, 100, -2.5);
            player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForChunksRender();
        world.getConnection().waitForClientboundPackets();
        row.put("fixture", "Fresh disposable Survival world, stone platform and supplies staged. Native item actions invoke resource behavior. Specific scenario prerequisites are disclosed separately; no resource outcome or item binding is injected.");
    }


    private void stone(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        server(player -> {
            for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++)
                player.level().setBlockAndUpdate(DEVICE.offset(x, 0, z), ModBlocks.ALTAR.get().defaultBlockState());
        });
        context.waitTicks(41);
        check(serverValue(player -> altar(player).isMultiblockValid()), "Actual six-block altar forms before power transfer");
        supply(context, 0, item(id));
        final int initial = id.endsWith("_charged") ? 2000 : 0;
        check(serverValue(player -> AttunedStoneItem.storedPower(player.getMainHandItem()) == initial), "Registry variant has expected initial reserve");
        alignAltarWindow(context);
        server(player -> { altar(player).consumePower(altar(player).availablePower()); altar(player).receivePower(500); });
        if (initial == 0) {
            use(context, new Vec3(.5, 100.65, .05), DEVICE);
            await(context, player -> AttunedStoneItem.storedPower(player.getMainHandItem()) == 250, 12, "Native normal use withdraws exactly 250");
            check(serverValue(player -> altar(player).getPower() == 250), "Withdrawal conserves actual altar power");
        } else {
            use(context, new Vec3(.5, 100.65, .05), DEVICE);
            check(serverValue(player -> AttunedStoneItem.storedPower(player.getMainHandItem()) == 2000 && altar(player).getPower() == 500),
                "Full charged stone rejects withdrawal without moving power");
        }
        close(context);
        alignAltarWindow(context);
        final int beforeAltar = serverValue(player -> altar(player).getPower());
        final int beforeStone = serverValue(player -> AttunedStoneItem.storedPower(player.getMainHandItem()));
        context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT); context.waitTicks(3);
        try { use(context, new Vec3(.5, 100.65, .05), DEVICE); }
        finally { context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); }
        await(context, player -> AttunedStoneItem.storedPower(player.getMainHandItem()) == beforeStone - 250, 12,
            "Native crouch use deposits exactly 250");
        check(serverValue(player -> altar(player).getPower() == beforeAltar + 250), "Deposit conserves actual altar power");
        row.put("checked", List.of("registry initial reserve", "withdrawal/full-capacity rejection", "crouch deposit", "exact 250 power conservation"));
        row.put("fixture_power", "Valid altar assembled from six staged blocks; initial reserve loaded through receivePower. Actions run between natural 40-tick recharge boundaries.");
        row.put("not_run", List.of("dropped Spirit World locator", "ritual escrow rejection", "persistence"));
        screenshot(context, id + "-power-transfer");
    }
    private AltarBlockEntity altar(final ServerPlayer player) { return (AltarBlockEntity) player.level().getBlockEntity(DEVICE); }
    private void alignAltarWindow(final ClientGameTestContext context) {
        for (int i = 0; i < 45 && serverValue(player -> player.level().getGameTime() % 40) > 5; i++) context.waitTicks(1);
        check(serverValue(player -> player.level().getGameTime() % 40) <= 5, "Transfer window excludes periodic altar recharge");
    }
    private void needle(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final UUID cow = spawn("minecraft:cow", .5, 100, .5);
        supply(context, 0, item("ingredient_bone_needle"));
        useAir(context);
        check(serverValue(player -> count(player, item("ingredient_bone_needle")) == 1 && living(player, cow).getHealth() == 10),
            "Needle without offhand doll consumes nothing and causes no damage");
        final Item doll = ModItems.ALL.values().stream().map(java.util.function.Supplier::get)
            .filter(candidate -> candidate instanceof DollItem value && value.kind() == DollKind.HEXING).findFirst().orElseThrow();
        supply(context, 0, doll);
        useEntity(context, cow);
        await(context, player -> SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targetId().equals(cow)).isPresent(), 30,
            "Native doll interaction binds the actual cow");
        context.getInput().pressKey(GLFW.GLFW_KEY_F); context.waitTicks(3);
        await(context, player -> player.getOffhandItem().is(doll), 20, "Native swap places bound hex doll in offhand");
        supply(context, 0, item("ingredient_bone_needle"));
        final float health = serverValue(player -> living(player, cow).getHealth());
        useAir(context);
        await(context, player -> count(player, item("ingredient_bone_needle")) == 0 && living(player, cow).getHealth() == health - 1, 30,
            "Native needle action deals exactly one remote magic damage and consumes one needle");
        row.put("checked", List.of("missing-doll gate", "native binding and offhand swap", "one consumed needle", "exact remote damage"));
        row.put("not_run", List.of("protective fetish rejection", "unloaded target"));
        screenshot(context, "bone-needle-remote-damage");
    }
    private void heart(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final UUID tree = spawn("warlockery:bramble_colossus", .5, 100, .5);
        server(player -> { living(player, tree).getAttribute(Attributes.MAX_HEALTH).setBaseValue(30); living(player, tree).setHealth(12); });
        supply(context, 0, item("ingredient_creeper_heart"));
        useEntity(context, tree);
        await(context, player -> living(player, tree).getMaxHealth() == 100 && living(player, tree).getHealth() == 100
            && count(player, item("ingredient_creeper_heart")) == 0, 30, "Native feeding raises colossus maximum to 100, fully heals and consumes one heart");
        supply(context, 0, item("ingredient_creeper_heart")); useEntity(context, tree);
        check(serverValue(player -> living(player, tree).getMaxHealth() == 100 && count(player, item("ingredient_creeper_heart")) == 1),
            "Already empowered colossus does not consume another heart");
        server(player -> living(player, tree).teleportTo(10.5, 100, .5));
        final UUID cow = spawn("minecraft:cow", 1.5, 100, -2.5);
        eat(context, item("ingredient_creeper_heart"));
        await(context, player -> living(player, cow).getHealth() < 10, 30, "Actually eating heart triggers an explosion damaging a nearby animal");
        check(serverValue(player -> player.level().getBlockState(new BlockPos(0, 99, -2)).is(Blocks.STONE)),
            "Explosion preserves nearby platform blocks");
        row.put("checked", List.of("colossus 100 maximum/full heal", "cap gate", "native eating explosion damages nearby entity", "non-destructive terrain mode"));
        row.put("fixture", "Fresh mobs; colossus initial maximum 30 and health 12 staged to expose the upgrade, no resulting health injected.");
        screenshot(context, "creeper-heart-native-outcomes");
    }
    private void dust(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final UUID ghost = spawn("warlockery:spectre", .5, 100, .5);
        server(player -> { living(player, ghost).getAttribute(Attributes.MAX_HEALTH).setBaseValue(20); living(player, ghost).setHealth(10); });
        supply(context, 0, item("ingredient_graveyard_dust")); useEntity(context, ghost);
        check(serverValue(player -> count(player, item("ingredient_graveyard_dust")) == 1 && living(player, ghost).getMaxHealth() == 20),
            "Unowned spectral target rejects dust without consumption");
        server(player -> check(CreatureBehaviorState.bind(living(player, ghost), player.getUUID()), "Stage owned spectral prerequisite"));
        useEntity(context, ghost);
        await(context, player -> living(player, ghost).getMaxHealth() == 22 && living(player, ghost).getHealth() == 12
            && count(player, item("ingredient_graveyard_dust")) == 0, 30, "Native dust adds exactly two maximum/current health to owned spectral target");
        server(player -> { living(player, ghost).getAttribute(Attributes.MAX_HEALTH).setBaseValue(50); living(player, ghost).setHealth(40); });
        supply(context, 0, item("ingredient_graveyard_dust")); useEntity(context, ghost);
        check(serverValue(player -> count(player, item("ingredient_graveyard_dust")) == 1 && living(player, ghost).getMaxHealth() == 50),
            "Spectral maximum 50 cap prevents further consumption");
        row.put("fixture", "Spectre and owner relation staged as prerequisites; unowned gate tested first. Initial health/max and later cap boundary are staged, each dust outcome comes only from native entity use.");
        row.put("checked", List.of("unowned gate", "owned spectral +2 maximum/+2 heal", "exact consumption", "50 maximum cap"));
        screenshot(context, "graveyard-dust-owned-upgrade");
    }
    private void artichoke(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        supply(context, 0, item("ingredient_artichoke"));
        server(player -> { player.getFoodData().setFoodLevel(8); player.getFoodData().setSaturation(0); });
        eat(context, item("ingredient_artichoke"));
        await(context, player -> player.getFoodData().getFoodLevel() == 20 && player.hasEffect(MobEffects.HUNGER), 20,
            "Native globe eating restores food and applies its hunger cost");
        check(serverValue(player -> player.getEffect(MobEffects.HUNGER).getAmplifier() == 2
            && player.getEffect(MobEffects.HUNGER).getDuration() >= 700 && player.getEffect(MobEffects.HUNGER).getDuration() <= 720),
            "Twelve restored hunger points cause Hunger III for 720 ticks, allowing elapsed observation ticks");
        row.put("checked", List.of("food 8 to 20", "one globe consumed", "Hunger III, 12 x 60 ticks"));
        screenshot(context, "artichoke-hunger-cost");
    }
    private void village(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        server(player -> player.level().setBlockAndUpdate(DEVICE, Blocks.BELL.defaultBlockState()));
        supply(context, 0, item("ingredient_subdued_spirit"));
        context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT); context.waitTicks(3);
        check(serverValue(player -> !player.level().isVillage(DEVICE)), "Fresh isolated bell is outside a village before fixture claim");
        use(context, new Vec3(.5, 100.5, .5), DEVICE);
        check(serverValue(player -> count(player, item("ingredient_subdued_spirit")) == 1
            && count(player, item("ingredient_subdued_spirit_village")) == 0), "Bell outside a village rejects capture without consumption");
        server(player -> {
            final BlockPos home = DEVICE.offset(3, 0, 0);
            final var poi = player.level().getPoiManager();
            poi.add(home, player.level().registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(PoiTypes.HOME));
            check(poi.take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(home), home, 1).isPresent(), "Stage occupied home POI prerequisite");
        });
        await(context, player -> player.level().isVillage(DEVICE), 80, "Actual vanilla occupied-POI village distance becomes active");
        use(context, new Vec3(.5, 100.5, .5), DEVICE);
        await(context, player -> count(player, item("ingredient_subdued_spirit")) == 0
            && count(player, item("ingredient_subdued_spirit_village")) == 1, 30, "Native village bell captures one spirit into village variant");
        context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); context.waitTicks(2);
        final int slot = serverValue(player -> {
            for (int i = 0; i < 9; i++) if (player.getInventory().getItem(i).is(item("ingredient_subdued_spirit_village"))) return i;
            throw new AssertionError("Captured spirit absent from hotbar");
        });
        sync(context, slot);
        check(serverValue(player -> VillageSpiritItem.readVillage(player.getMainHandItem()).filter(binding -> binding.position().equals(DEVICE)
            && binding.dimension().equals(player.level().dimension().identifier().toString())).isPresent()), "Captured binding records exact real bell and dimension");
        server(player -> player.teleportTo(.5, 100, -7.5)); world.getConnection().waitForClientboundPackets(); context.waitTicks(2);
        useAir(context);
        final int distance = serverValue(player -> Math.round((float) Math.sqrt(player.blockPosition().distSqr(DEVICE))));
        final String expected = context.computeOnClient(client -> Component.translatable("message.warlockery.village_spirit.distance", distance).getString());
        context.waitTicks(3);
        final String message = context.computeOnClient(client -> {
            final Object value = field(client.gui.hud, "overlayMessageString"); return value instanceof Component text ? text.getString() : String.valueOf(value);
        });
        check(message.equals(expected), "Native bound spirit use shows correct actual distance; actual=" + message);
        row.put("checked", List.of("non-village bell rejection", "native capture", "exact source bell/dimension binding", "native bound-distance overlay"));
        row.put("fixture", "Bell block and occupied HOME POI staged using vanilla POI API; this tests real isVillage gating, not villager settlement construction. No village-spirit binding is injected.");
        row.put("canonical_action_chain", "ingredient_subdued_spirit -> native bell capture -> ingredient_subdued_spirit_village; repeated chain verifies either selected registry ID.");
        row.put("not_run", List.of("other-dimension message", "dropped raw-spirit locator", "persistent village binding"));
        screenshot(context, id + "-village-distance");
    }
    private void mutator(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        supply(context, 0, item("mutator"));
        server(player -> player.level().setBlockAndUpdate(DEVICE, Blocks.DIRT.defaultBlockState()));
        hitTransform(context, Blocks.MYCELIUM, 1);
        hitTransform(context, Blocks.DIRT, 2);
        server(player -> {
            for (final BlockPos wall : List.of(DEVICE.above().north(), DEVICE.above().south(),
                DEVICE.above().east(), DEVICE.above().west())) {
                player.level().setBlockAndUpdate(wall, Blocks.GLASS.defaultBlockState());
            }
            player.level().setBlockAndUpdate(DEVICE.above(), Blocks.WATER.defaultBlockState());
        });
        hitTransform(context, Blocks.CLAY, 3);
        hitTransform(context, Blocks.DIRT, 4);
        row.put("checked", List.of("dry dirt -> mycelium -> dirt", "water-immersed dirt -> clay -> dirt", "one durability each native mining action"));
        row.put("not_run", List.of("advanced mutation structures/right-click resolver"));
        screenshot(context, "mutating-sprig-four-transformations");
    }
    private void hitTransform(final ClientGameTestContext context, final net.minecraft.world.level.block.Block expected, final int damage) {
        look(context, new Vec3(.5, 100.45, .05));
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(DEVICE)), "Native attack ray hits transformation input");
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        try { await(context, player -> player.level().getBlockState(DEVICE).is(expected), 80, "Native mining transforms the actual block into " + expected); }
        finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); }
        check(serverValue(player -> player.getMainHandItem().getDamageValue() == damage), "Each transformation uses exactly one durability");
    }
    private void bulb(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        server(player -> player.level().setBlockAndUpdate(DEVICE.below(), Blocks.FARMLAND.defaultBlockState()));
        supply(context, 0, item("seedsdreamroot"));
        use(context, new Vec3(.5, 99.9374, .5), DEVICE.below());
        await(context, player -> player.level().getBlockState(DEVICE).is(ModBlocks.ALL.get("dreamroot").get())
            && count(player, item("seedsdreamroot")) == 0, 30, "Native bulb planting consumes one seed and produces dreamroot crop");
        server(player -> observation = new ResourceObservation(player));
        supply(context, 0, item("seedsdreamroot"));
        context.runOnClient(client -> { client.player.setYRot(180); client.player.setXRot(-10); }); context.waitTicks(2);
        context.getInput().pressKey(GLFW.GLFW_KEY_Q);
        await(context, player -> count(player, item("seedsdreamroot")) == 0 && observation.droppedSeed != null, 20,
            "Native Q drops the actual bulb item");
        final long start = serverValue(player -> player.level().getGameTime());
        context.waitTicks(35);
        check(serverValue(player -> observation.created.stream().noneMatch(value -> value.equals("warlockery:dreamroot"))), "Bulb does not wake before native item age reaches 60");
        await(context, player -> observation.created.stream().filter(value -> value.equals("warlockery:dreamroot")).count() == 1, 100,
            "Dropped bulb wakes exactly one actual Dreamroot through normal item ticks");
        check(serverValue(player -> player.level().getEntity(observation.droppedSeed) == null), "Successful waking removes consumed ground bulb");
        row.put("elapsed_ticks_after_drop_observed", serverValue(player -> player.level().getGameTime() - start));
        row.put("checked", List.of("native farmland planting", "native Q drop", "delayed 60-tick wake", "one exact dreamroot", "ground bulb consumed"));
        row.put("not_run", List.of("large stack batching", "world creature quota gate", "dreamroot combat"));
        screenshot(context, "dreamroot-bulb-native-wake");
    }
    private void rock(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        server(player -> observation = new ResourceObservation(player));
        supply(context, 0, item("ingredient_rock"));
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(-25); }); context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        await(context, player -> observation.ownedProjectiles.size() == 1 && count(player, item("ingredient_rock")) == 0, 20,
            "Native rock use consumes one and launches an actual owned projectile");
        check(serverValue(player -> observation.ownedProjectiles.values().stream().allMatch(type -> type.equals("minecraft:snowball"))),
            "Rock intentionally uses vanilla snowball projectile, not an invented damaging rock entity");
        row.put("checked", List.of("native throw", "one consumed rock", "one player-owned vanilla snowball projectile"));
        row.put("not_run", List.of("collision/knockback", "blaze-specific vanilla damage"));
        screenshot(context, "rock-native-projectile");
    }
    private UUID spawn(final String id, final double x, final double y, final double z) {
        return serverValue(player -> {
            final Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)).create(player.level(), EntitySpawnReason.COMMAND);
            check(entity instanceof Mob, "Fixture registry ID is an actual mob: " + id);
            final Mob mob = (Mob) entity; mob.snapTo(x, y, z); mob.setNoAi(true); mob.setNoGravity(true); mob.setPersistenceRequired();
            check(player.level().addFreshEntity(mob), "Fixture entity enters actual world"); return mob.getUUID();
        });
    }
    private static LivingEntity living(final ServerPlayer player, final UUID id) { return (LivingEntity) player.level().getEntity(id); }
    private void useEntity(final ClientGameTestContext context, final UUID id) {
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> java.util.stream.StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false).anyMatch(entity -> entity.getUUID().equals(id)));
        final Vec3 point = serverValue(player -> living(player, id).position().add(0, living(player, id).getBbHeight() * .55, 0));
        look(context, point);
        check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(id)), "Native pointer hits exact prerequisite creature");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(3);
    }
    private void eat(final ClientGameTestContext context, final Item expected) {
        context.runOnClient(client -> client.player.setXRot(-75)); context.waitTicks(2);
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try { await(context, player -> count(player, expected) == 0, 55, "Native held use completes eating and consumes one item"); }
        finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
    }
    private static void look(final ClientGameTestContext context, final Vec3 point) {
        context.runOnClient(client -> {
            final Vec3 delta = point.subtract(client.player.getEyePosition());
            client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        }); context.waitTicks(2);
    }
    private static void useAir(final ClientGameTestContext context) {
        context.runOnClient(client -> client.player.setXRot(-70)); context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(3);
    }
    private static final class ResourceObservation {
        final ServerPlayer player;
        final List<String> created = new ArrayList<>();
        final Map<String, String> ownedProjectiles = new LinkedHashMap<>();
        UUID droppedSeed;
        ResourceObservation(final ServerPlayer player) { this.player = player; }
        void loaded(final Entity entity) {
            created.add(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            if (entity instanceof Projectile projectile && projectile.getOwner() == player)
                ownedProjectiles.put(entity.getUUID().toString(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            if (entity instanceof ItemEntity drop && drop.getItem().is(item("seedsdreamroot"))) droppedSeed = drop.getUUID();
        }
        Map<String, Object> report() { return Map.of("loaded_entity_types", List.copyOf(created), "owned_projectiles", Map.copyOf(ownedProjectiles),
            "observer", "Passive actual entity-load events; no outcome injection"); }
    }
    private void readGuides(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final List<String> requested = List.of(id, switch (id) {
            case "seedsdreamroot" -> "plant_dreamroot";
            case "ingredient_artichoke" -> "plant_artichoke";
            default -> "gestures";
        }).stream().distinct().toList();
        final List<Map<String, Object>> guides = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        for (String section : requested) {
            final var found = ManualProfile.profiles().stream().filter(profile -> profile.sections().contains(section)).findFirst();
            if (found.isEmpty()) { missing.add(section); continue; }
            final var profile = found.orElseThrow();
            supply(context, 0, item(profile.id()));
            context.runOnClient(client -> client.player.setXRot(-70));
            context.waitTicks(2); context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitForScreen(ManualScreen.class);
            ManualClientAcceptance.selectSection(context, section);
            final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, section).body().getString());
            final int pages = context.computeOnClient(client -> {
                try {
                    final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                    method.setAccessible(true);
                    return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            check(!body.isBlank() && pages > 0, "Available guide has actual readable pages");
            for (int page = 0; page < pages; page++) {
                if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
                final int expected = page;
                check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection"))
                    && (int) field(client.gui.screen(), "bodyPage") == expected), "Native book paging visits each instruction page");
                screenshot(context, id + "-guide-" + section + "-" + page);
            }
            guides.add(Map.of("book", profile.id(), "section", section, "text", body, "pages_read", pages));
            close(context);
        }
        row.put("guides_read", guides);
        row.put("missing_indexed_guidance", missing);
        row.put("guidance_complete", missing.isEmpty());
    }

    private void place(final ClientGameTestContext context, final Item item, final BlockPos target, final String expectedId,
        final boolean crouch) {
        supply(context, 0, item);
        if (crouch) { context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT); context.waitTicks(3); }
        try { use(context, new Vec3(target.getX() + 0.5, target.getY() - 0.001, target.getZ() + 0.5), target.below()); }
        finally { if (crouch) { context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); context.waitTicks(2); } }
        final Identifier expected = expectedId.contains(":") ? Identifier.parse(expectedId) : Identifier.fromNamespaceAndPath("warlockery", expectedId);
        await(context, player -> BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(target).getBlock()).equals(expected), 30,
            "Native placement produces the expected device at " + target);
        check(serverValue(player -> player.getInventory().getItem(0).isEmpty()), "Survival placement consumes exactly the one staged block item");
    }

    private void supply(final ClientGameTestContext context, final int slot, final Item item) {
        server(player -> { player.getInventory().setItem(slot, new ItemStack(item)); player.inventoryMenu.broadcastChanges(); });
        sync(context, slot);
    }

    private void sync(final ClientGameTestContext context, final int hotbar) {
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1 + hotbar); context.waitTicks(2);
    }

    private static void use(final ClientGameTestContext context, final Vec3 point, final BlockPos expected) {
        context.runOnClient(client -> {
            final Vec3 delta = point.subtract(client.player.getEyePosition());
            client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        });
        context.waitTicks(2);
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(expected)),
            "Native pointer targets the intended support/device: " + expected);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(2);
    }

    private static void clickPlayerSlot(final ClientGameTestContext context, final int index) {
        final int slot = context.computeOnClient(client -> java.util.stream.IntStream.range(0, client.player.containerMenu.slots.size())
            .filter(candidate -> client.player.containerMenu.getSlot(candidate).container == client.player.getInventory()
                && client.player.containerMenu.getSlot(candidate).getContainerSlot() == index).findFirst().orElseThrow());
        clickSlot(context, slot);
    }

    private static void clickSlot(final ClientGameTestContext context, final int index) {
        final int[] point = context.computeOnClient(client -> {
            final var slot = client.player.containerMenu.getSlot(index);
            return new int[] {(int) field(client.gui.screen(), "leftPos") + slot.x + 8,
                (int) field(client.gui.screen(), "topPos") + slot.y + 8};
        });
        ManualClientAcceptance.click(context, point[0], point[1]); context.waitTicks(2);
    }

    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> predicate, final int ticks, final String message) {
        for (int remaining = ticks; remaining > 0 && !serverValue(predicate::test); remaining -= 2) context.waitTicks(2);
        check(serverValue(predicate::test), message);
    }

    private static void close(final ClientGameTestContext context) {
        if (context.computeOnClient(client -> client.gui.screen() != null)) {
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitTicks(3);
        }
    }

    private static Object field(final Object object, final String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) try {
            final var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
        } catch (NoSuchFieldException ignored) { } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        throw new AssertionError("Missing observed field " + name);
    }
    private static Item item(final String id) { return ModItems.ALL.get(id).get(); }
    private static int count(final ServerPlayer player, final Item item) {
        int result = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) if (player.getInventory().getItem(slot).is(item)) result += player.getInventory().getItem(slot).getCount();
        return result;
    }
    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T serverValue(final Function<ServerPlayer, T> action) { return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer())); }
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots); }
    private void fail(final String id, final Map<String, Object> row, final Throwable failure) {
        row.put("status", "FAILED"); row.put("failure", failure.toString()); failures.add(id + ": " + failure);
    }
    private static String classHash(final Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing loaded class bytes " + type);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }
    private void write(final boolean finished) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", finished);
        report.put("selected_native_scenarios_passed", finished && failures.isEmpty());
        report.put("all_eleven_native_scenarios_passed", finished && results.size() == DEVICES.size()
            && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("execution", "Native rendered Fabric development-classpath client; staged prerequisites, real mouse/key item/block/GUI interactions, real block, entity, food, power and item behavior. Scenario prerequisites explicitly staged; no resulting resource behavior/components injected.");
        final Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("test", classHash(ResourceItemsAbilitiesClientAcceptance.class));
        for (String id : DEVICES) hashes.put(id, classHash(item(id).getClass()));
        report.put("class_sha256", hashes);
        report.put("devices", results); report.put("failures", failures); report.put("screenshots", screenshots);
        final Path pending = evidence.resolve("resource-items-abilities.json.tmp");
        Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(pending, evidence.resolve("resource-items-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean value, final String message) { if (!value) throw new AssertionError(message); }
}
