package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.DreamWeaverBlock;
import com.kadamitas.warlockery.block.DreamWeaverMode;
import com.kadamitas.warlockery.block.FetishBindingState;
import com.kadamitas.warlockery.block.FetishBlock;
import com.kadamitas.warlockery.block.FetishMode;
import com.kadamitas.warlockery.block.StatueBlock;
import com.kadamitas.warlockery.block.StatueWardData;
import com.kadamitas.warlockery.block.entity.WolfTrapBlockEntity;
import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.dream.SpiritManifestationState;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import com.kadamitas.warlockery.transformation.SupernaturalProgression;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import com.kadamitas.warlockery.transformation.WerewolfShape;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Native Dream Weaver sleep rewards, bound Scarecrow modes, both statue wards, Wolf Altar, Wolf Trap and
 * Trent Effigy; every scenario pairs a success case with an untreated or refused control.
 */
public final class DreamAndBoundDevicesClientAcceptance implements FabricClientGameTest {
    private static final List<String> SCENARIOS = List.of(
        "dream_weaver_restoration", "dream_weaver_fasting", "dream_weaver_fleet_foot", "dream_weaver_intensity",
        "dream_weaver_iron_arm", "dream_weaver_nightmares", "dream_weaver_nightmares_protected",
        "scarecrow_disorientation", "scarecrow_ghost_walking", "scarecrow_sentinel", "scarecrow_shrieking", "scarecrow_voodoo_protection",
        "broken_hexes_statue", "occluded_summons_statue", "wolfaltar", "wolftrap", "trent");
    private static final BlockPos DEVICE = new BlockPos(0, 100, 0);
    private static final BlockPos BED_FOOT = new BlockPos(0, 100, 4);
    private static final BlockPos BED_HEAD = new BlockPos(0, 100, 5);
    private static final BlockPos NEAR_WEAVER = new BlockPos(3, 100, 1);
    private static final BlockPos FAR_WEAVER = new BlockPos(-10, 100, -2);
    private static final Vec3 START = new Vec3(.5, 100, -2.5);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private final List<String> guideDeviations = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private volatile Observation observation;
    private String scenario = "setup";

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("dream-and-bound-devices").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                final var current = observation;
                if (current != null && current.player.level() == level) current.loaded(entity);
            });
            final String configured = System.getProperty("warlockery.dreamDeviceIds", "");
            final Set<String> requested = configured.isBlank() ? Set.copyOf(SCENARIOS) : Arrays.stream(configured.split(","))
                .map(String::strip).collect(Collectors.toSet());
            check(!requested.isEmpty() && SCENARIOS.containsAll(requested), "Requested scenarios must exist: " + configured);
            for (String id : SCENARIOS) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", requested.contains(id) ? "NOT_RUN" : "NOT_SELECTED");
                row.put("not_run", notRun(id));
                results.put(id, row);
            }
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (String id : SCENARIOS) {
                if (!requested.contains(id)) continue;
                final Map<String, Object> row = results.get(id);
                scenario = id; row.put("status", "RUNNING"); write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context, row);
                        readGuides(context, guides(id), row);
                        if (id.startsWith("dream_weaver_")) dreamWeaver(context, id, row);
                        else if (id.startsWith("scarecrow_")) scarecrow(context, id, row);
                        else switch (id) {
                            case "broken_hexes_statue" -> brokenHexesStatue(context, row);
                            case "occluded_summons_statue" -> occludedSummonsStatue(context, row);
                            case "wolfaltar" -> wolfAltar(context, row);
                            case "wolftrap" -> wolfTrap(context, row);
                            case "trent" -> trent(context, row);
                            default -> throw new UnsupportedOperationException(id);
                        }
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        fail(id, row, failure);
                        try { screenshot(context, id + "-failure"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
                        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
                        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
                        if (observation != null) row.put("entity_observation", serverValue(player -> observation.report()));
                        observation = null; write(false);
                    }
                } catch (Throwable failure) { fail(id, row, failure); }
                finally { observation = null; world = null; }
                write(false);
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_DREAM_AND_BOUND_DEVICES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), stack(failure)); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Dream and bound device evidence: " + evidence, failure);
        }
    }

    private static List<String> guides(final String id) {
        if (id.startsWith("dream_weaver_")) {
            final String mode = id.equals("dream_weaver_nightmares_protected") ? "nightmares" : id.substring("dream_weaver_".length());
            return List.of("device_dream_weaver", "fetish_dream_weaver_" + mode);
        }
        if (id.startsWith("scarecrow_")) return List.of("fetish_scarecrow", "rite_bind_fetish");
        return switch (id) {
            case "broken_hexes_statue" -> List.of("fetish_statue_broken_hexes");
            case "occluded_summons_statue" -> List.of("fetish_statue_occluded_summons");
            case "wolfaltar" -> List.of("werewolf_level_2");
            case "wolftrap" -> List.of("wolftrap", "werewolf_level_1");
            default -> List.of("fetish_trent_effigy", "rite_bind_trent");
        };
    }

    private static List<String> notRun(final String id) {
        if (id.startsWith("dream_weaver_")) return List.of("crafting acquisition", "forced early wake (<100 ticks) refusal",
            "two-nightmare blindness stacking", "intensity amplification of a neighbouring weave", "corruption of a helpful weave by a nightmare weave", "save/reload persistence");
        if (id.startsWith("scarecrow_")) return List.of("Rite of Binding acquisition (bound item is staged)", "robe dye recolouring",
            "crouch-focus mode cycling", "unbound zombie attraction", "fetish-immune creatures", "armed/armoured player as threat", "save/reload persistence");
        return switch (id) {
            case "broken_hexes_statue" -> List.of("ritual hex refusal at 64 blocks", "non-owner break protection", "ward removal on destruction");
            case "occluded_summons_statue" -> List.of("actual ritual cast refusal (read-only ward observation only)", "non-owner toggle refusal", "ward removal on destruction");
            case "wolfaltar" -> List.of("curse acquisition (werewolf form is staged)", "levels 3-10 offerings", "wolf-form requirement at level 2", "horn reward");
            case "wolftrap" -> List.of("sheep-bait werewolf luring over 60 seconds", "lured werewolf capture", "release of a captured werewolf mob", "save/reload persistence");
            default -> List.of("Rite of Binding: Trent Effigy", "biome-shaped Ent variants", "flower and seed offerings (sapling only)");
        };
    }

    private void stage(final ClientGameTestContext context, final Map<String, Object> row) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.removeAllEffects();
            player.setHealth(20); player.setAbsorptionAmount(0); player.getFoodData().setFoodLevel(20); player.clearFire();
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-14, 98, -8), new BlockPos(20, 108, 12))) {
                final boolean wall = pos.getY() <= 101 && (pos.getX() == -14 || pos.getX() == 20 || pos.getZ() == -8 || pos.getZ() == 12);
                player.level().setBlockAndUpdate(pos, pos.getY() <= 99 || wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            command(player, "time set 14000");
            player.teleportTo(START.x, START.y, START.z); player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets();
        row.put("fixture", "Fresh disposable Survival world at night, two-block stone platform with walls, mob spawning and natural regeneration off. Devices are placed and operated by native mouse/key input; beds, creatures, moon time and supernatural prerequisites are staged; outcomes arise only from normal ticks.");
    }

    // ---------------------------------------------------------------- dream weavers

    private void dreamWeaver(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final boolean protectedCase = id.equals("dream_weaver_nightmares_protected");
        final DreamWeaverMode mode = DreamWeaverMode.valueOf((protectedCase ? "nightmares" : id.substring("dream_weaver_".length())).toUpperCase(java.util.Locale.ROOT));
        server(player -> {
            final var bed = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:red_bed"));
            player.level().setBlockAndUpdate(BED_FOOT, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
            player.level().setBlockAndUpdate(BED_HEAD, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
            if (protectedCase) player.level().setBlockAndUpdate(new BlockPos(1, 100, 6),
                com.kadamitas.warlockery.registry.ModBlocks.ALL.get("somniancotton").get().defaultBlockState());
        });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
        if (protectedCase) check(serverValue(player -> player.level().getBlockState(new BlockPos(1, 100, 6))
            .is(com.kadamitas.warlockery.block.WitchcraftCompatibilityTags.DREAM_PROTECTIVE_PLANTS)), "Staged Wispy Cotton counts as a dream-protective plant within four blocks of the bed");
        // Control: the same weave placed twelve blocks from the bed is outside the documented eight-block reach.
        position(context, new Vec3(FAR_WEAVER.getX() + .5, 100, FAR_WEAVER.getZ() - 2.5));
        place(context, new ItemStack(item(mode.itemId())), FAR_WEAVER, "dreamcatcher");
        check(serverValue(player -> player.level().getBlockState(FAR_WEAVER).getValue(DreamWeaverBlock.MODE) == mode), "Placed variant carries its own mode: " + mode);
        final Map<String, Object> control = sleep(context, "control", mode);
        row.put("control_sleep", control);
        check(!(Boolean) control.get("wake_message_seen") && ((List<?>) control.get("effects")).isEmpty()
            && (int) control.get("food_after") == (int) control.get("food_before"), "A full sleep with the weave twelve blocks away grants nothing: " + control);
        row.put("control_status", "PASSED");
        screenshot(context, id + "-control-far-weaver");
        // Success: a second weave within eight blocks of the bed.
        position(context, new Vec3(NEAR_WEAVER.getX() + .5, 100, NEAR_WEAVER.getZ() - 2.5));
        place(context, new ItemStack(item(mode.itemId())), NEAR_WEAVER, "dreamcatcher");
        check(serverValue(player -> player.level().getBlockState(NEAR_WEAVER).getValue(DreamWeaverBlock.MODE) == mode), "Near weave carries mode " + mode);
        emptyHand(context); use(context, Vec3.atCenterOf(NEAR_WEAVER), NEAR_WEAVER);
        inspectEmptyHand(context, row, id, translated(context, "message.warlockery.dream_weaver.ready", Component.translatable("dream_weaver_mode.warlockery." + mode.getSerializedName())),
            "device_dream_weaver: \"An empty hand reports the selected mode\"; DreamWeaverBlock.useItemOn (src/main/java/com/kadamitas/warlockery/block/DreamWeaverBlock.java:48-56) answers an empty stack with wrong_focus and returns FAIL before useWithoutItem can run");
        final Map<String, Object> success = sleep(context, "success", mode);
        row.put("success_sleep", success);
        check(protectedCase ? (Boolean) success.get("protected_message_seen") : (Boolean) success.get("wake_message_seen"),
            "Rendered chat shows the weave's wake message: " + success.get("chat"));
        final Map<String, Object> reward = new LinkedHashMap<>();
        switch (mode) {
            case RESTORATION -> reward.putAll(effect(context, MobEffects.REGENERATION, 1, 600));
            case FASTING -> {
                final int before = (int) success.get("food_before"), after = (int) success.get("food_after");
                check(after > before, "Fasting weave feeds the sleeper: " + before + " -> " + after);
                reward.put("food_before", before); reward.put("food_after", after); reward.put("saturation_after", success.get("saturation_after"));
            }
            case FLEET_FOOT -> reward.putAll(effect(context, MobEffects.SPEED, 1, 2400));
            case INTENSITY -> reward.putAll(effect(context, MobEffects.NIGHT_VISION, 0, 300));
            case IRON_ARM -> reward.putAll(effect(context, MobEffects.HASTE, 1, 2400));
            case NIGHTMARES -> reward.putAll(protectedCase ? effect(context, MobEffects.ABSORPTION, 1, 2400) : effect(context, MobEffects.WEAKNESS, 1, 2400));
        }
        if (protectedCase) check((Boolean) success.get("protected_message_seen"), "Protected nightmare reports the guarded dream");
        row.put("reward", reward); row.put("success_status", "PASSED");
        row.put("verified", "Native bed sleep of at least 100 ticks with the " + mode + " weave twelve blocks away granted nothing; the same sleep with a natively placed weave within eight blocks produced the documented reward " + reward + ".");
        screenshot(context, id + "-after-wake");
    }

    private Map<String, Object> effect(final ClientGameTestContext context, final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> type,
        final int amplifier, final int duration) {
        final MobEffectInstance instance = serverValue(player -> player.getEffect(type));
        check(instance != null, "Expected effect " + type.getRegisteredName() + " after waking");
        check(instance.getAmplifier() == amplifier && instance.getDuration() >= duration - 60 && instance.getDuration() <= duration,
            "Effect " + type.getRegisteredName() + " has amplifier " + amplifier + " and about " + duration + " ticks; actual " + instance);
        return Map.of("effect", type.getRegisteredName(), "amplifier", instance.getAmplifier(), "duration", instance.getDuration());
    }

    private Map<String, Object> sleep(final ClientGameTestContext context, final String label, final DreamWeaverMode mode) throws Exception {
        server(player -> { command(player, "time set 14000"); player.removeAllEffects(); player.setHealth(20); player.setAbsorptionAmount(0); player.getFoodData().setFoodLevel(6); });
        position(context, new Vec3(.5, 100, 2.5)); emptyHand(context); clearChat(context);
        final int foodBefore = serverValue(player -> player.getFoodData().getFoodLevel());
        use(context, new Vec3(.5, 100.3, 4.5), BED_FOOT);
        await(context, ServerPlayer::isSleeping, 30, "Native bed use puts the player to sleep (" + label + ")");
        int longest = 0;
        for (int tick = 0; tick < 300 && serverValue(ServerPlayer::isSleeping); tick += 2) { longest = Math.max(longest, serverValue(ServerPlayer::getSleepTimer)); context.waitTicks(2); }
        check(serverValue(player -> !player.isSleeping()), "Normal sleep ends by itself within the night skip (" + label + ")");
        check(longest >= 100, "Sleep timer reached the documented 100-tick threshold before the natural wake; longest=" + longest);
        context.waitTicks(4); closeScreen(context);
        final Component modeName = Component.translatable("dream_weaver_mode.warlockery." + mode.getSerializedName());
        final String wake = translated(context, "message.warlockery.dream_weaver.wake", modeName);
        final String guarded = translated(context, "message.warlockery.dream_weaver.protected", modeName);
        final List<String> chat = chat(context);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("longest_sleep_timer", longest); result.put("food_before", foodBefore);
        result.put("food_after", serverValue(player -> player.getFoodData().getFoodLevel()));
        result.put("saturation_after", serverValue(player -> player.getFoodData().getSaturationLevel()));
        result.put("effects", serverValue(player -> player.getActiveEffects().stream().map(MobEffectInstance::toString).toList()));
        result.put("wake_message_seen", chat.contains(wake));
        result.put("protected_message_seen", chat.contains(guarded));
        result.put("chat", chat);
        return result;
    }

    // ---------------------------------------------------------------- scarecrows

    private void scarecrow(final ClientGameTestContext context, final String id, final Map<String, Object> row) throws Exception {
        final FetishMode mode = FetishMode.valueOf(id.substring("scarecrow_".length()).toUpperCase(java.util.Locale.ROOT));
        final ItemStack bound = new ItemStack(item("scarecrow")); FetishBindingState.write(bound, mode);
        row.put("staged_item", "Scarecrow with bound mode " + mode + " written by the Rite of Binding's own item state (rite itself not run)");
        server(player -> observation = new Observation(player));
        place(context, bound, DEVICE, "scarecrow");
        check(serverValue(player -> {
            final var state = player.level().getBlockState(DEVICE);
            return state.getValue(FetishBlock.BOUND) && state.getValue(FetishBlock.ENABLED) && state.getValue(FetishBlock.MODE) == mode;
        }), "Native placement of the bound item yields an enabled, bound " + mode + " fetish");
        emptyHand(context); use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        inspectEmptyHand(context, row, id, translated(context, "message.warlockery.fetish.ready", Component.translatable("fetish_mode.warlockery." + mode.getSerializedName())),
            "fetish_scarecrow: \"use an empty hand to read its state\"; FetishBlock.useItemOn (src/main/java/com/kadamitas/warlockery/block/FetishBlock.java:90-107) answers an empty stack with wrong_focus and returns FAIL before useWithoutItem can run");
        row.put("placement_status", "PASSED");
        switch (mode) {
            case DISORIENTATION -> disorientation(context, row);
            case GHOST_WALKING -> ghostWalking(context, row);
            case SENTINEL -> sentinel(context, row);
            case SHRIEKING -> shrieking(context, row);
            case VOODOO_PROTECTION -> hexProtection(context, row, "scarecrow", null);
        }
        screenshot(context, id + "-outcome");
    }

    private void disorientation(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final UUID near = serverValue(player -> mob(player, "minecraft:zombie", new Vec3(4.5, 100, .5)).getUUID());
        final UUID far = serverValue(player -> mob(player, "minecraft:zombie", new Vec3(16.5, 100, .5)).getUUID());
        await(context, player -> hasEffects(player, near), 60, "Monster within ten blocks receives Nausea and Slowness III from the active fetish");
        check(serverValue(player -> !living(player, far).hasEffect(MobEffects.NAUSEA) && !living(player, far).hasEffect(MobEffects.SLOWNESS)
            && !player.hasEffect(MobEffects.NAUSEA)), "Control zombie sixteen blocks away and the empty-handed player are untouched");
        row.put("near_zombie", serverValue(player -> living(player, near).getActiveEffects().stream().map(MobEffectInstance::toString).toList()));
        row.put("affected_status", "PASSED");
        screenshot(context, "scarecrow_disorientation-affected");
        // Refusal control: disable the fetish with the focus, clear the effects, and confirm they are not reapplied.
        supply(context, 0, new ItemStack(item("arcane_focus")));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        await(context, player -> !player.level().getBlockState(DEVICE).getValue(FetishBlock.ENABLED), 20, "Native focus use disables the fetish");
        awaitOverlay(context, translated(context, "message.warlockery.fetish.disabled", Component.translatable("fetish_mode.warlockery.disorientation")));
        server(player -> living(player, near).removeAllEffects());
        context.waitTicks(60);
        check(serverValue(player -> !hasEffects(player, near)), "A disabled fetish no longer disorients the nearby monster");
        row.put("disabled_status", "PASSED");
        row.put("verified", "Enabled bound fetish gave the nearby zombie Nausea and Slowness III while the distant control stayed clean; after a native focus toggle disabled it, sixty ticks passed without any reapplication.");
    }
    private static boolean hasEffects(final ServerPlayer player, final UUID id) {
        final var target = living(player, id);
        return target.hasEffect(MobEffects.NAUSEA) && target.hasEffect(MobEffects.SLOWNESS) && target.getEffect(MobEffects.SLOWNESS).getAmplifier() == 2;
    }

    private void ghostWalking(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        row.put("staged_prerequisite", "An active manifestation state (return point at the player's own position, empty stored inventory) with a short expiration is staged; the fetish's sustain is the observed outcome.");
        position(context, new Vec3(2.5, 100, .5));
        final long staged = serverValue(player -> {
            final long expiration = player.level().getServer().getTickCount() + 60;
            SpiritManifestationState.begin(player, player.level().dimension().identifier(), player.getX(), player.getY(), player.getZ(), 0F, 0F, List.of(), 0);
            SpiritManifestationState.grant(player, expiration); return expiration;
        });
        await(context, player -> SpiritManifestationState.expiration(player) > staged, 40, "Ghost Walking fetish sustains a manifested player inside its area");
        final long sustained = serverValue(SpiritManifestationState::expiration);
        row.put("in_area", Map.of("staged_expiration", staged, "sustained_expiration", sustained, "extension_ticks", sustained - staged));
        server(SpiritManifestationState::clear);
        position(context, new Vec3(16.5, 100, .5));
        final long control = serverValue(player -> {
            final long expiration = player.level().getServer().getTickCount() + 60;
            SpiritManifestationState.begin(player, player.level().dimension().identifier(), player.getX(), player.getY(), player.getZ(), 0F, 0F, List.of(), 0);
            SpiritManifestationState.grant(player, expiration); return expiration;
        });
        context.waitTicks(30);
        final long after = serverValue(SpiritManifestationState::expiration);
        server(SpiritManifestationState::clear);
        check(after == control, "Outside the ten-block area the manifestation is not sustained: " + control + " -> " + after);
        row.put("outside_area", Map.of("staged_expiration", control, "after_30_ticks", after));
        row.put("verified", "The fetish extended a manifested player's expiration while inside its area and left it unchanged sixteen blocks away.");
    }

    private void sentinel(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        context.waitTicks(45);
        check(serverValue(player -> observation.golems.isEmpty()), "No sentinel appears while no threat is present");
        row.put("no_threat_control", "45 ticks with the enabled fetish and no monster: no Iron Golem loaded");
        final UUID zombie = serverValue(player -> mob(player, "minecraft:zombie", new Vec3(4.5, 100, .5)).getUUID());
        await(context, player -> !observation.golems.isEmpty(), 60, "A monster within ten blocks makes the fetish summon a sentinel");
        final Map<String, Object> golem = serverValue(player -> Map.copyOf(observation.golems.values().iterator().next()));
        check(Boolean.TRUE.equals(golem.get("sentinel_flag")) && zombie.toString().equals(golem.get("target"))
            && translated(context, "entity.warlockery.fetish_sentinel").equals(golem.get("name")), "Summoned Iron Golem is the named Spectral Sentinel targeting the threat: " + golem);
        context.waitTicks(45);
        check(serverValue(player -> observation.golems.size() == 1), "Only one sentinel exists while it lives");
        row.put("sentinel", golem); row.put("verified", "Enabled Sentinel fetish summoned nothing without a threat, then one named Spectral Sentinel golem targeting the staged zombie, and no duplicate.");
    }

    private void shrieking(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final BlockPos lamp = DEVICE.east();
        server(player -> player.level().setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState()));
        context.waitTicks(45);
        check(serverValue(player -> !player.level().getBlockState(DEVICE).getValue(FetishBlock.ALARM) && !player.level().getBlockState(lamp).getValue(RedstoneLampBlock.LIT)),
            "Without a threat the fetish emits no alarm and the adjacent lamp stays dark");
        row.put("no_threat_control", "45 ticks: alarm=false, lamp unlit");
        final UUID zombie = serverValue(player -> mob(player, "minecraft:zombie", new Vec3(4.5, 100, .5)).getUUID());
        await(context, player -> player.level().getBlockState(DEVICE).getValue(FetishBlock.ALARM) && player.level().getBlockState(lamp).getValue(RedstoneLampBlock.LIT), 60,
            "A monster within ten blocks raises the alarm and the real redstone signal lights the adjacent lamp");
        emptyHand(context); use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        inspectEmptyHand(context, row, "scarecrow_shrieking", translated(context, "message.warlockery.fetish.alarm", Component.translatable("fetish_mode.warlockery.shrieking")),
            "fetish_scarecrow empty-hand state reading; FetishBlock.useItemOn (FetishBlock.java:90-107) answers an empty stack with wrong_focus before useWithoutItem can report the alarm");
        screenshot(context, "scarecrow_shrieking-alarm-lamp");
        server(player -> player.level().getEntity(zombie).discard());
        await(context, player -> !player.level().getBlockState(DEVICE).getValue(FetishBlock.ALARM) && !player.level().getBlockState(lamp).getValue(RedstoneLampBlock.LIT), 60,
            "Removing the threat clears the alarm and darkens the lamp");
        row.put("verified", "Shrieking fetish stayed silent without a threat, raised its alarm and powered an adjacent redstone lamp when a zombie approached, reported the alarm on empty-hand use, and cleared after the threat left.");
    }

    /** Bone Needle + natively bound Hexing Doll: an unprotected control prick lands, a protected prick is refused. */
    private void hexProtection(final ClientGameTestContext context, final Map<String, Object> row, final String protector, final String statueId) throws Exception {
        final UUID cow = serverValue(player -> mob(player, "minecraft:cow", new Vec3(3.5, 100, .5)).getUUID());
        if (statueId != null) {
            // Control first: with no ward placed the needle lands.
            bindDollAndSwap(context, cow);
            prick(context, cow, 39F, 1, "message.warlockery.bone_needle.success", row, "control_before_statue");
            position(context, START);
            place(context, new ItemStack(item(statueId)), DEVICE, statueId);
            check(serverValue(player -> player.level().getBlockState(DEVICE).getValue(StatueBlock.ACTIVE)
                && StatueWardData.get(player.level()).protectsHex(living(player, cow).blockPosition())), "Native statue placement registers an active hex ward covering the cow");
            emptyHand(context); use(context, Vec3.atCenterOf(DEVICE), DEVICE);
            awaitOverlay(context, translated(context, "message.warlockery.statue.hex_ward_active"));
            prick(context, cow, 39F, 2, "message.warlockery.bone_needle.protected", row, "protected_by_statue");
        } else {
            // Scarecrow already placed: protected prick first, then move the cow beyond ten blocks for the control.
            bindDollAndSwap(context, cow);
            prick(context, cow, 40F, 2, "message.warlockery.bone_needle.protected", row, "protected_by_fetish");
            server(player -> living(player, cow).snapTo(16.5, 100, .5));
            context.waitTicks(3);
            prick(context, cow, 39F, 1, "message.warlockery.bone_needle.success", row, "control_beyond_radius");
        }
        row.put("verified", "A Bone Needle with a natively bound Hexing Doll harmed the cow only when no " + protector + " protection covered it; under protection the needle was refused, kept, and the cow's health did not change.");
    }

    private void bindDollAndSwap(final ClientGameTestContext context, final UUID cow) {
        position(context, new Vec3(2.5, 100, -1.0));
        supply(context, 0, new ItemStack(item("hexing_doll")));
        aimEntity(context, cow);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(3);
        await(context, player -> SympatheticBinding.read(player.getMainHandItem()).filter(binding -> binding.targetId().equals(cow)).isPresent(), 20,
            "Native doll use on the cow binds the Hexing Doll to it");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F); context.waitTicks(3);
        check(serverValue(player -> SympatheticBinding.read(player.getOffhandItem()).filter(binding -> binding.targetId().equals(cow)).isPresent()),
            "Native hand swap moves the bound doll to the offhand");
    }

    private void prick(final ClientGameTestContext context, final UUID cow, final float expectedHealth, final int expectedNeedles,
        final String messageKey, final Map<String, Object> row, final String label) throws Exception {
        supply(context, 0, new ItemStack(item("ingredient_bone_needle"), 2));
        context.runOnClient(client -> client.player.setXRot(-70)); context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(6);
        awaitOverlay(context, translated(context, messageKey));
        final float health = serverValue(player -> living(player, cow).getHealth());
        final int needles = serverValue(player -> count(player, item("ingredient_bone_needle")));
        check(health == expectedHealth && needles == expectedNeedles, label + ": expected cow health " + expectedHealth + " and " + expectedNeedles + " needles, actual " + health + "/" + needles);
        row.put(label, Map.of("cow_health", health, "needles_remaining", needles, "overlay", overlay(context)));
        screenshot(context, "hex-protection-" + label);
    }

    // ---------------------------------------------------------------- statues

    private void brokenHexesStatue(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        hexProtection(context, row, "Statue of Broken Hexes", "broken_hexes_statue");
        row.put("guide_note", "The indexed section fetish_statue_broken_hexes renders manual.warlockery.conjuration.broken_hexes_ward (ward, no cleansing on touch), matching StatueBlock; the unused lang entry manual.warlockery.conjuration.fetish_statue_broken_hexes still claims touching severs hexes.");
    }

    private void occludedSummonsStatue(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        final BlockPos near = DEVICE.offset(5, 0, 5), far = DEVICE.offset(70, 0, 0);
        place(context, new ItemStack(item("occluded_summons_statue")), DEVICE, "occluded_summons_statue");
        check(serverValue(player -> !player.level().getBlockState(DEVICE).getValue(StatueBlock.ACTIVE) && !StatueWardData.get(player.level()).occludesSummoning(near)),
            "A freshly placed statue is quiet: inactive state and no summoning occlusion");
        row.put("placed_inactive", true);
        emptyHand(context); use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        await(context, player -> player.level().getBlockState(DEVICE).getValue(StatueBlock.ACTIVE), 20, "Native empty-hand use awakens the ward");
        awaitOverlay(context, translated(context, "message.warlockery.statue.occlusion_active"));
        check(serverValue(player -> StatueWardData.get(player.level()).occludesSummoning(near) && !StatueWardData.get(player.level()).occludesSummoning(far)
            && ((StatueBlock) player.level().getBlockState(DEVICE).getBlock()).occludes(player.level().getBlockState(DEVICE))),
            "Active ward occludes rituals within sixty-four blocks but not at seventy");
        row.put("active", Map.of("occludes_at_7_blocks", true, "occludes_at_70_blocks", false));
        screenshot(context, "occluded_summons_statue-active");
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        await(context, player -> !player.level().getBlockState(DEVICE).getValue(StatueBlock.ACTIVE), 20, "Second native use quiets the ward");
        awaitOverlay(context, translated(context, "message.warlockery.statue.occlusion_inactive"));
        check(serverValue(player -> !StatueWardData.get(player.level()).occludesSummoning(near)), "Quieted ward no longer occludes");
        row.put("verified", "Native placement registered an inactive ward; empty-hand use toggled occlusion on (read-only ward data covers seven blocks, not seventy) and off again with the documented overlay messages.");
        screenshot(context, "occluded_summons_statue-inactive");
    }

    // ---------------------------------------------------------------- wolf altar, wolf trap, trent

    private void wolfAltar(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        place(context, new ItemStack(item("wolfaltar")), DEVICE, "wolfaltar");
        supply(context, 0, new ItemStack(Items.GOLD_INGOT, 3));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        awaitOverlay(context, translated(context, "message.warlockery.werewolf_progression.curse_required"));
        check(serverValue(player -> count(player, Items.GOLD_INGOT) == 3 && SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF) == 0),
            "A mortal is refused and keeps every ingot");
        row.put("mortal_control", Map.of("overlay", overlay(context), "gold_kept", 3, "level", 0));
        server(player -> SupernaturalState.setForm(player, SupernaturalForm.WEREWOLF));
        check(serverValue(player -> SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF) == 1), "Staged curse starts the werewolf path at level 1");
        supply(context, 0, new ItemStack(Items.GOLD_INGOT, 2));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE); context.waitTicks(4);
        check(serverValue(player -> count(player, Items.GOLD_INGOT) == 2 && SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF) == 1),
            "Two ingots are refused without consumption");
        row.put("short_offering_control", Map.of("overlay", overlay(context), "gold_kept", 2, "level", 1));
        screenshot(context, "wolfaltar-refusals");
        clearChat(context);
        supply(context, 0, new ItemStack(Items.GOLD_INGOT, 3));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        await(context, player -> SupernaturalProgression.level(player, SupernaturalProgression.Path.WEREWOLF) == 2, 20, "Three native ingots complete the Gilded Leash trial");
        check(serverValue(player -> count(player, Items.GOLD_INGOT) == 0 && count(player, item("mooncharm")) == 1), "Trial consumes the three ingots and grants the Moon Charm");
        awaitChat(context, translated(context, "message.warlockery.progression.level_up", Component.translatable("path.warlockery.werewolf"), 2));
        row.put("success", Map.of("overlay", overlay(context), "level", 2, "gold_consumed", 3, "moon_charm", 1));
        row.put("verified", "Mortal and two-ingot uses were refused with ingots kept; a staged level-1 werewolf offering three gold ingots natively advanced to level 2, consumed the ingots and received the Moon Charm.");
        screenshot(context, "wolfaltar-level-2");
    }

    private void wolfTrap(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        place(context, new ItemStack(item("wolftrap")), DEVICE, "wolftrap");
        server(player -> {
            player.level().setBlockAndUpdate(DEVICE.east(), com.kadamitas.warlockery.registry.ModBlocks.ALL.get("wolfaltar").get().defaultBlockState());
            command(player, "time set 18000");
        });
        emptyHand(context); clearChat(context);
        use(context, new Vec3(.5, 100.1, .5), DEVICE);
        awaitChat(context, translated(context, "message.warlockery.wolftrap.armed"));
        await(context, player -> trap(player).getDisplay().armed(), 20, "Native empty-hand use arms the trap");
        row.put("display_after_arming", serverValue(player -> trap(player).getDisplay().toString()));
        // Control: a mortal walks over the armed trap under the full moon.
        walkAcross(context, player -> player.getZ() > 2.0, 100, "Mortal crosses the armed trap");
        check(serverValue(player -> trap(player).getDisplay().capturedName().isEmpty() && player.getHealth() == 20 && !player.hasEffect(MobEffects.SLOWNESS)),
            "Armed trap ignores a mortal player");
        row.put("mortal_control", "Crossed the armed trap: no capture, no damage, no slowness");
        // Success: a staged werewolf is forced into wolf shape by the full moon, then crosses.
        server(player -> SupernaturalState.setForm(player, SupernaturalForm.WEREWOLF));
        await(context, player -> SupernaturalProgression.werewolfShape(player) == WerewolfShape.WOLF, 60, "The full moon forces the wolf shape through normal ticks");
        check(serverValue(player -> trap(player).getDisplay().fullMoon()), "Trap reports the full moon night");
        walkAcross(context, player -> !trap(player).getDisplay().capturedName().isEmpty(), 120, "Transformed werewolf crossing the armed trap is captured");
        final Map<String, Object> captured = serverValue(player -> Map.of("captured", trap(player).getDisplay().capturedName(), "health", player.getHealth(),
            "slowness", player.hasEffect(MobEffects.SLOWNESS) ? player.getEffect(MobEffects.SLOWNESS).getAmplifier() : -1, "position", player.position().toString()));
        check(serverValue(player -> player.getName().getString().equals(trap(player).getDisplay().capturedName()) && player.getHealth() < 20
            && player.hasEffect(MobEffects.SLOWNESS) && player.getEffect(MobEffects.SLOWNESS).getAmplifier() == 10
            && player.position().distanceTo(new Vec3(.5, 100.1, .5)) < 1.0), "Capture names the player, deals damage, paralyzes with Slowness XI and holds them on the trap: " + captured);
        row.put("captured", captured);
        screenshot(context, "wolftrap-captured");
        clearChat(context);
        use(context, new Vec3(.5, 100.1, .5), DEVICE);
        awaitChat(context, translated(context, "message.warlockery.wolftrap.released"));
        check(serverValue(player -> trap(player).getDisplay().capturedName().isEmpty() && !trap(player).getDisplay().armed()), "Native use releases the captive and disarms the trap");
        row.put("verified", "Native arming, a mortal control crossing without effect, a naturally moon-forced wolf-shaped werewolf captured on crossing (damage, Slowness XI, held on the trap) and native release.");
        screenshot(context, "wolftrap-released");
    }
    private static WolfTrapBlockEntity trap(final ServerPlayer player) { return (WolfTrapBlockEntity) player.level().getBlockEntity(DEVICE); }

    private void walkAcross(final ClientGameTestContext context, final Predicate<ServerPlayer> outcome, final int ticks, final String message) {
        position(context, START);
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); });
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
        try { await(context, outcome, ticks, message); }
        finally { context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W); context.waitTicks(3); }
    }

    private void trent(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        server(player -> observation = new Observation(player));
        place(context, new ItemStack(item("trent")), DEVICE, "trent");
        // A non-offering that cannot be placed as a block: a refused block item (e.g. cobblestone) falls through to ordinary block placement and is consumed that way.
        supply(context, 0, new ItemStack(Items.IRON_INGOT));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        awaitOverlay(context, translated(context, "message.warlockery.trent_effigy.missing_sapling"));
        context.waitTicks(10);
        check(serverValue(player -> count(player, Items.IRON_INGOT) == 1 && observation.ents.isEmpty()), "Iron ingot is refused, kept, and wakes nothing; actual ingots=" + serverValue(player -> count(player, Items.IRON_INGOT)) + " ents=" + serverValue(player -> observation.ents.size()));
        row.put("refusal_note", "The effigy's FAIL result lets vanilla continue to the held item's own useOn; a block item such as cobblestone is then placed against the effigy and consumed normally, so the control uses a non-placeable item.");
        emptyHand(context); use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        awaitOverlay(context, translated(context, "message.warlockery.trent_effigy.missing_sapling"));
        row.put("refusal_control", Map.of("iron_ingot_kept", 1, "empty_hand_overlay", overlay(context), "ents", 0));
        screenshot(context, "trent-refused");
        supply(context, 0, new ItemStack(Items.OAK_SAPLING));
        use(context, Vec3.atCenterOf(DEVICE), DEVICE);
        await(context, player -> !observation.ents.isEmpty(), 20, "A native sapling offering wakes an Ent");
        awaitOverlay(context, translated(context, "message.warlockery.trent_effigy.awakened"));
        check(serverValue(player -> count(player, Items.OAK_SAPLING) == 0), "The valid offering is consumed");
        final Map<String, Object> ent = serverValue(player -> Map.copyOf(observation.ents.getFirst()));
        check(new Vec3((double) ent.get("x"), (double) ent.get("y"), (double) ent.get("z")).distanceTo(Vec3.atCenterOf(DEVICE.above())) < 2.0, "Ent appears above the effigy: " + ent);
        row.put("ent", ent);
        row.put("verified", "An iron ingot and an empty hand were refused with the documented message and no Ent; one native oak sapling was consumed and a real Ent entity loaded above the effigy.");
        screenshot(context, "trent-awakened");
    }

    // ---------------------------------------------------------------- observation

    private static final class Observation {
        final ServerPlayer player;
        final Map<UUID, Map<String, Object>> golems = new LinkedHashMap<>();
        final List<Map<String, Object>> ents = new ArrayList<>();
        Observation(final ServerPlayer player) { this.player = player; }
        void loaded(final Entity entity) {
            if (entity instanceof IronGolem golem) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("uuid", golem.getStringUUID()); row.put("name", golem.getDisplayName().getString());
                row.put("target", golem.getTarget() == null ? "" : golem.getTarget().getStringUUID());
                row.put("sentinel_flag", WarlockeryEntityData.get(golem).getBooleanOr("WarlockeryFetishSentinel", false));
                row.put("position", golem.position().toString());
                golems.put(golem.getUUID(), row);
            }
            if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("warlockery:ent")) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("uuid", entity.getStringUUID()); row.put("x", entity.getX()); row.put("y", entity.getY()); row.put("z", entity.getZ());
                ents.add(row);
            }
        }
        Map<String, Object> report() { return Map.of("iron_golems", Map.copyOf(golems), "ents", List.copyOf(ents), "observer", "Passive server entity-load event; no behavior injection"); }
    }

    // ---------------------------------------------------------------- shared helpers

    private void readGuides(final ClientGameTestContext context, final List<String> requested, final Map<String, Object> row) throws Exception {
        final List<Map<String, Object>> guides = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        for (String section : requested) {
            final var found = ManualProfile.profiles().stream().filter(profile -> profile.sections().contains(section)).findFirst();
            if (found.isEmpty()) { missing.add(section); continue; }
            final var profile = found.orElseThrow();
            supply(context, 0, new ItemStack(item(profile.id())));
            context.runOnClient(client -> client.player.setXRot(-70)); context.waitTicks(2);
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitForScreen(ManualScreen.class);
            ManualClientAcceptance.selectSection(context, section);
            final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, section).body().getString());
            final int pages = context.computeOnClient(client -> {
                try {
                    final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class); method.setAccessible(true);
                    return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            check(!body.isBlank() && pages > 0, "Guide " + section + " has readable pages");
            for (int page = 0; page < pages; page++) {
                if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
                final int expected = page;
                check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection")) && (int) field(client.gui.screen(), "bodyPage") == expected),
                    "Native book paging visits each page of " + section);
                screenshot(context, "guide-" + section + "-" + page);
            }
            guides.add(Map.of("book", profile.id(), "section", section, "text", body, "pages_read", pages));
            closeScreen(context);
        }
        row.put("guides_read", guides); row.put("missing_indexed_guidance", missing); row.put("guidance_complete", missing.isEmpty());
        server(player -> { player.getInventory().setItem(0, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges(); });
    }

    private void place(final ClientGameTestContext context, final ItemStack stack, final BlockPos target, final String expectedId) {
        supply(context, 0, stack);
        use(context, new Vec3(target.getX() + .5, target.getY() - .001, target.getZ() + .5), target.below());
        final Identifier expected = Identifier.fromNamespaceAndPath("warlockery", expectedId);
        await(context, player -> BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(target).getBlock()).equals(expected), 30, "Native placement produces " + expectedId + " at " + target);
        check(serverValue(player -> player.getInventory().getItem(0).isEmpty()), "Survival placement consumes exactly the staged block item");
    }
    private static Mob mob(final ServerPlayer player, final String id, final Vec3 point) {
        final Mob mob = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)).create(player.level(), EntitySpawnReason.COMMAND);
        check(mob != null, "Fixture entity exists: " + id);
        mob.snapTo(point.x, point.y, point.z); mob.setNoAi(true); mob.setPersistenceRequired();
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40); mob.setHealth(40);
        check(player.level().addFreshEntity(mob), "Fixture entity joins the world"); return mob;
    }
    private static net.minecraft.world.entity.LivingEntity living(final ServerPlayer player, final UUID id) {
        return (net.minecraft.world.entity.LivingEntity) player.level().getEntity(id);
    }
    private static void command(final ServerPlayer player, final String command) {
        player.level().getServer().getCommands().performPrefixedCommand(player.level().getServer().createCommandSourceStack(), command);
    }
    private void aimEntity(final ClientGameTestContext context, final UUID id) {
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> java.util.stream.StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false).anyMatch(entity -> entity.getUUID().equals(id)), 40);
        look(context, serverValue(player -> player.level().getEntity(id).getBoundingBox().getCenter()));
        check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(id)), "Native pointer targets the intended creature");
    }
    private void supply(final ClientGameTestContext context, final int slot, final ItemStack stack) {
        server(player -> { player.getInventory().setItem(slot, stack); player.inventoryMenu.broadcastChanges(); }); sync(context, slot);
    }
    private void emptyHand(final ClientGameTestContext context) {
        server(player -> { player.getInventory().setItem(8, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges(); }); sync(context, 8);
        check(serverValue(player -> player.getMainHandItem().isEmpty()), "Main hand is actually empty");
    }
    private void sync(final ClientGameTestContext context, final int slot) {
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1 + slot); context.waitTicks(2);
    }
    private void position(final ClientGameTestContext context, final Vec3 point) {
        server(player -> { player.teleportTo(point.x, point.y, point.z); player.setDeltaMovement(Vec3.ZERO); });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
    }
    private static void look(final ClientGameTestContext context, final Vec3 point) {
        context.runOnClient(client -> {
            final Vec3 delta = point.subtract(client.player.getEyePosition());
            client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        }); context.waitTicks(2);
    }
    private static void use(final ClientGameTestContext context, final Vec3 point, final BlockPos expected) {
        look(context, point);
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(expected)),
            "Native pointer targets intended block " + expected + "; actual=" + context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit
                ? hit.getType() + " " + hit.getBlockPos() + " face=" + hit.getDirection() + " at=" + hit.getLocation() : String.valueOf(client.hitResult)));
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitTicks(2);
    }
    /** Guide-promised empty-hand reading; recorded as a guide deviation (not a scenario failure) when the coded block refuses it. */
    private void inspectEmptyHand(final ClientGameTestContext context, final Map<String, Object> row, final String id, final String expected, final String note) {
        for (int tick = 0; tick < 30 && !overlay(context).equals(expected); tick++) context.waitTicks(1);
        final String actual = overlay(context);
        final Map<String, Object> inspection = new LinkedHashMap<>();
        inspection.put("expected_overlay", expected); inspection.put("actual_overlay", actual);
        inspection.put("status", actual.equals(expected) ? "PASSED" : "GUIDE_DEVIATION");
        if (!actual.equals(expected)) { inspection.put("note", note); guideDeviations.add(id + ": expected '" + expected + "' from an empty hand, got '" + actual + "' (" + note + ")"); }
        row.put("empty_hand_inspection", inspection);
    }
    private static void closeScreen(final ClientGameTestContext context) {
        if (context.computeOnClient(client -> client.gui.screen() != null)) { context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE); context.waitTicks(3); }
    }
    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> predicate, final int ticks, final String message) {
        for (int remaining = ticks; remaining > 0 && !serverValue(predicate::test); remaining -= 2) context.waitTicks(2);
        check(serverValue(predicate::test), message);
    }
    private static String translated(final ClientGameTestContext context, final String key, final Object... arguments) {
        return context.computeOnClient(client -> Component.translatable(key, arguments).getString());
    }
    private static void clearChat(final ClientGameTestContext context) { context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false)); }
    private static List<String> chat(final ClientGameTestContext context) {
        return context.computeOnClient(client -> ((List<?>) field(client.gui.hud.getChat(), "allMessages")).stream()
            .map(message -> ((net.minecraft.client.multiplayer.chat.GuiMessage) message).content().getString()).toList());
    }
    private static void awaitChat(final ClientGameTestContext context, final String expected) {
        for (int tick = 0; tick < 40 && !chat(context).contains(expected); tick++) context.waitTicks(1);
        check(chat(context).contains(expected), "Rendered chat receives: " + expected + "; actual=" + chat(context));
    }
    private static String overlay(final ClientGameTestContext context) {
        return context.computeOnClient(client -> { final Object value = field(client.gui.hud, "overlayMessageString"); return value instanceof Component text ? text.getString() : String.valueOf(value); });
    }
    private static void awaitOverlay(final ClientGameTestContext context, final String expected) {
        for (int tick = 0; tick < 30 && !overlay(context).equals(expected); tick++) context.waitTicks(1);
        check(overlay(context).equals(expected), "Rendered overlay reports: " + expected + "; actual=" + overlay(context));
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
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context, evidence, name.startsWith(scenario) ? name : scenario + "-" + name, screenshots); }
    private static String stack(final Throwable failure) { final var text = new StringWriter(); failure.printStackTrace(new PrintWriter(text)); return text.toString(); }
    private void fail(final String id, final Map<String, Object> row, final Throwable failure) {
        row.put("status", "FAILED"); row.put("failure", stack(failure)); failures.add(id + ": " + failure);
    }
    private void write(final boolean finished) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", finished);
        report.put("all_selected_scenarios_passed", finished && failures.isEmpty()
            && results.values().stream().noneMatch(row -> "NOT_RUN".equals(row.get("status")) || "RUNNING".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("execution", "Native rendered Fabric development-classpath client; staged prerequisites, real mouse/key placement, bed use, item use and walking, normal server ticks. No reward, capture, ward, alarm or summon outcome is injected.");
        report.put("guide_deviations", guideDeviations);
        report.put("scenarios", results); report.put("failures", failures); report.put("screenshots", screenshots);
        final Path pending = evidence.resolve("dream-and-bound-devices.json.tmp");
        Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(pending, evidence.resolve("dream-and-bound-devices.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean value, final String message) { if (!value) throw new AssertionError(message); }
}
