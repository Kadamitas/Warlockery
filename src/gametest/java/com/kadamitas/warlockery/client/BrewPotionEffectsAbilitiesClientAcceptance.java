package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.brew.BrewItem;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.registry.ModItems;
import java.awt.image.BufferedImage;
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
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

public final class BrewPotionEffectsAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final Set<String> EXPECTED_IDS = Set.of(
        "brew_absorption", "brew_blindness", "brew_damage_boost", "brew_fast_movement",
        "brew_fire_resistance", "brew_floating", "brew_fortune", "brew_frogs_leg", "brew_fullness",
        "brew_harm", "brew_heal", "brew_health_boost", "brew_ink", "brew_invisible", "brew_jump",
        "brew_night_vision", "brew_paralysis", "brew_poison", "brew_regeneration", "brew_slow_fall",
        "brew_slow_movement", "brew_swim_speed", "brew_water_breathing", "brew_weakness", "brew_wither",
        "ingredient_brew_congealed_spirit", "ingredient_brew_ink", "ingredient_brew_soul_anguish");
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private volatile Observation observation;
    private TestSingleplayerContext world;
    private Path evidence;
    private long started;

    @Override
    public void runTest(final ClientGameTestContext context) {
        started = System.currentTimeMillis();
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("brew-potion-effects-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                final Observation current = observation;
                if (current != null && current.player.level().getServer() == server) current.tick();
            });
            ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                final Observation current = observation;
                if (current != null && current.player.level() == level) current.loaded(entity);
            });
            final List<Identifier> ids = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals("warlockery"))
                .filter(id -> BuiltInRegistries.ITEM.getValue(id) instanceof BrewItem item
                    && item.kind().behaviors().isEmpty() && !item.kind().effects().isEmpty()).sorted().toList();
            check(ids.stream().map(Identifier::getPath).collect(Collectors.toSet()).equals(EXPECTED_IDS),
                "Native effect-only BrewItem registry must match the exact 28-ID reviewed census");
            final String configured = System.getProperty("warlockery.brewPotionEffectsIds", "");
            final Set<String> selected = configured.isBlank() ? ids.stream().map(Identifier::toString).collect(Collectors.toSet())
                : Arrays.stream(configured.split(",")).map(String::strip)
                    .map(id -> id.contains(":") ? id : "warlockery:" + id).collect(Collectors.toSet());
            check(!selected.isEmpty() && ids.stream().map(Identifier::toString).collect(Collectors.toSet()).containsAll(selected),
                "Selected IDs must belong to the actual effect-only registry census");
            for (Identifier id : ids) {
                final BrewKind kind = ((BrewItem) BuiltInRegistries.ITEM.getValue(id)).kind();
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", selected.contains(id.toString()) ? "NOT_RUN" : "NOT_SELECTED");
                row.put("kind", kind.id()); row.put("expected_effects", kind.effects());
                row.put("canonical_guide", "brew_entry_" + kind.id());
                row.put("acquisition_status", "NOT_RUN"); row.put("persistence_status", "NOT_RUN");
                row.put("ability_complete", false);
                results.put(id.toString(), row);
            }
            write(false);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (Identifier id : ids) {
                if (!selected.contains(id.toString())) continue;
                final Map<String, Object> row = results.get(id.toString());
                row.put("status", "RUNNING"); write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        runBrew(context, id, row);
                        row.put("status", Boolean.TRUE.equals(row.get("behavior_observed")) ? "PASSED" : "PARTIAL");
                    } catch (Throwable failure) {
                        row.put("status", "FAILED"); row.put("failure", failure.toString());
                        failures.add(id + ": " + failure);
                        try { screenshot(context, id.getPath() + "-failure"); }
                        catch (Throwable capture) { row.put("screenshot_failure", capture.toString()); }
                    } finally {
                        release(context);
                        final Observation current = observation;
                        if (current != null) row.put("passive_observation", serverValue(player -> current.report()));
                        observation = null; row.put("finished_at", System.currentTimeMillis()); write(false);
                    }
                } catch (Throwable failure) {
                    row.put("status", "FAILED"); row.put("failure", failure.toString());
                    failures.add(id + ": " + failure); write(false);
                } finally { observation = null; world = null; }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_BREW_POTION_EFFECTS_ABILITIES_FINISHED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Native potion behavior evidence: " + evidence, failure);
        }
    }

    private void runBrew(final ClientGameTestContext context, final Identifier id, final Map<String, Object> row) throws Exception {
        final BrewKind kind = ((BrewItem) BuiltInRegistries.ITEM.getValue(id)).kind();
        server(player -> {
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.removeAllEffects();
            player.getAbilities().invulnerable = false; player.onUpdateAbilities();
            player.setHealth(20); player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(5);
            final var server = player.level().getServer(); server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            player.level().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            player.level().getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-12, 94, -12), new BlockPos(20, 119, 28)))
                player.level().setBlockAndUpdate(pos, pos.getY() <= 99 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            reset(player);
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets();
        readBook(context, id, kind, row);
        server(player -> observation = new Observation(player, id,
            cow(player, new Vec3(2.5, 100, .5)), cow(player, new Vec3(14.5, 100, .5))));
        final String family = kind.id();
        double baseline = 0;
        double baselineMining = 0;
        if (Set.of("fast_movement", "slow_movement", "paralysis", "ink", "blindness").contains(family)) {
            baseline = travel(context, family.equals("blindness"), false, 20);
            check(baseline > 1, "Untreated native walking/sprinting must really move the player");
            if (family.equals("paralysis")) baselineMining = mine(context, 35);
        }
        if (Set.of("jump", "frogs_leg").contains(family)) baseline = jump(context);
        if (Set.of("damage_boost", "weakness").contains(family)) baseline = melee(context);
        if (family.equals("swim_speed")) { buildSwimLane(); baseline = travel(context, true, true, 20); }
        if (family.equals("slow_fall")) {
            context.waitTicks(80);
            baseline = fall(context, false);
            check(baseline > 0, "The untreated baseline must complete a real damaging fall before the brew is applied");
            row.put("untreated_fall_minimum_velocity", serverValue(player -> observation.minimumVelocityY));
        }
        if (family.equals("absorption")) baseline = cactus(context, false);
        if (family.equals("night_vision") || family.equals("blindness")) {
            visionScene(context, family.equals("night_vision"));
            screenshot(context, id.getPath() + "-vision-before");
            row.put("render_luminance_before", luminance(evidence.resolve(id.getPath() + "-vision-before.png")));
        }
        row.put("untreated_baseline", baseline);
        row.put("untreated_mining_ticks", baselineMining);
        server(player -> {
            reset(player); player.setHealth(family.equals("heal") || family.equals("regeneration") ? 10 : 20);
            if (family.equals("fullness")) { player.getFoodData().setFoodLevel(4); player.getFoodData().setSaturation(0); }
            if (family.equals("health_boost")) player.setHealth(19);
            if (family.equals("regeneration") || family.equals("heal")) {
                observation.target.setHealth(10); observation.control.setHealth(10);
            }
            if (family.equals("swim_speed")) player.teleportTo(.5, 100, -8.5);
            observation.playerHealthBefore = player.getHealth(); observation.targetHealthBefore = observation.target.getHealth();
            observation.controlHealthBefore = observation.control.getHealth();
            player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
            player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        if (family.equals("invisible")) {
            check(serverValue(player -> !player.isInvisible() && !observation.target.isInvisible() && !observation.control.isInvisible()),
                "Untreated player and both cows must be visible before the matched visual trial");
            invisibilityFrame(context, id, "before", row);
        }
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(90); }); context.waitTicks(2);
        screenshot(context, id.getPath() + "-before-native-throw");
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        await(context, player -> observation.finishedImpact(), 100, "An owned brew projectile must fly and finish its native collision");
        check(serverValue(player -> observation.projectiles.size() == 1 && player.getMainHandItem().isEmpty()),
            "Exactly one native throw consumes exactly one brew");
        await(context, player -> kind.effects().stream().allMatch(effect -> switch (effect.effect()) {
            case "minecraft:instant_health" -> player.getHealth() > observation.playerHealthBefore;
            case "minecraft:instant_damage" -> player.getHealth() < observation.playerHealthBefore;
            case "minecraft:saturation" -> player.getFoodData().getFoodLevel() > 4;
            default -> BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effect.effect()))
                .map(holder -> player.hasEffect(holder) && player.getEffect(holder).getAmplifier() >= effect.amplifier()).orElse(false);
        }), 20, "Actual native splash must apply every declared effect before its consequences are checked");
        row.put("staged_prerequisites", "Fresh Survival arena, supplied exact brew and book, normal ticking cows outside and inside splash range, matched terrain and before/after input trials. Initial health, air, food and positions are setup. No effects under test, damage, impact, travel, mining or healing outcomes are invoked or injected directly.");
        verify(context, id, kind, baseline, baselineMining, row);
        row.putIfAbsent("behavior_observed", true);
        row.put("remaining", family.equals("fortune")
            ? "Fishing probability distribution is not statistically measured by a single catch. Acquisition, delivery variants, expiry and persistence are also untested."
            : "Acquisition, other delivery variants, full expiry, multiplayer and persistence. Vision screenshots require human review in addition to measured rendering changes.");
        screenshot(context, id.getPath() + "-after-native-behavior");
    }

    private void verify(final ClientGameTestContext context, final Identifier id, final BrewKind kind,
        final double baseline, final double baselineMining, final Map<String, Object> row) throws Exception {
        switch (kind.id()) {
            case "heal", "harm", "fullness" -> {
                row.put("native_health", serverValue(ServerPlayer::getHealth));
                row.put("native_food", serverValue(player -> player.getFoodData().getFoodLevel()));
                check(serverValue(player -> observation.control.getHealth() == observation.controlHealthBefore),
                    "Independent cow outside the splash remains unchanged");
            }
            case "regeneration" -> {
                await(context, player -> observation.target.getHealth() >= 12 && player.getHealth() >= 12, 110,
                    "Regeneration must restore real missing health on normal ticks");
                check(serverValue(player -> observation.control.getHealth() == 10), "Untreated injured cow must not regenerate");
            }
            case "poison", "wither" -> {
                await(context, player -> observation.target.getHealth() < observation.targetHealthBefore - 1, 100,
                    "Poison or Wither must repeatedly remove real health on normal ticks");
                check(serverValue(player -> observation.control.getHealth() == observation.controlHealthBefore),
                    "Independent untreated cow must not suffer damage");
                if (kind.id().equals("poison")) {
                    server(player -> observation.target.setHealth(2));
                    context.waitTicks(55);
                    check(serverValue(player -> observation.target.isAlive() && observation.target.getHealth() == 1),
                        "Poison's native damage stops at one health rather than killing");
                }
            }
            case "health_boost" -> {
                check(serverValue(player -> player.getMaxHealth() > 20), "Health Boost must increase real capacity");
                server(player -> {
                    player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(20);
                    player.level().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, true, player.level().getServer());
                });
                await(context, player -> player.getHealth() > 20, 100,
                    "Ordinary Survival regeneration must fill health above the untreated twenty-point capacity");
                row.put("health_above_vanilla_capacity", serverValue(ServerPlayer::getHealth));
            }
            case "absorption" -> {
                check(baseline > 0, "Untreated cactus must really damage health");
                final double healthLoss = cactus(context, true);
                check(healthLoss == 0, "The matched cactus contact must spend absorption while preserving ordinary health");
                row.put("protected_cactus_health_loss", healthLoss);
            }
            case "damage_boost", "weakness" -> {
                final double damage = melee(context); row.put("brewed_native_melee_damage", damage);
                check(baseline > 0 && (kind.id().equals("damage_boost") ? damage > baseline + 2 : damage < baseline - 2),
                    "The same fully cooled native sword attack must deal more/less real target damage after this brew");
            }
            case "fast_movement", "slow_movement", "paralysis", "ink" -> {
                final double distance = travel(context, false, false, 20); row.put("brewed_native_walk_distance", distance);
                check(kind.id().equals("fast_movement") ? distance > baseline * 1.2 : distance < baseline * .85,
                    "Matched native movement input must change actual distance in the intended direction");
                if (kind.id().equals("paralysis")) {
                    final double mining = mine(context, 35); row.put("brewed_mining_ticks", mining);
                    check(baselineMining > 0 && mining < 0, "Native hand mining breaks untreated dirt but fails to break matched dirt under Paralysis");
                }
                if (kind.id().equals("ink")) {
                    check(context.computeOnClient(client -> client.player.hasEffect(MobEffects.BLINDNESS)),
                        "Ink must also reach the actual rendered client's blindness state");
                    row.put("blindness_render_review", "Captured after native use; fog comparison is exercised separately by brew_blindness.");
                }
            }
            case "jump", "frogs_leg" -> {
                final double height = jump(context); row.put("brewed_native_jump_height", height);
                check(height > baseline + .45, "Native Space input must produce a higher real jump than the untreated baseline");
            }
            case "floating" -> {
                final double initial = 100;
                await(context, player -> player.getY() > initial + 1.2, 55,
                    "Levitation must lift the standing player without jump or movement input");
                check(serverValue(player -> observation.control.getY() == 100), "Untreated cow remains grounded");
                row.put("passive_player_rise", serverValue(player -> player.getY() - initial));
            }
            case "slow_fall" -> {
                final double descent = fall(context, true); row.put("brewed_fall_damage", descent);
                final double fallingSpeed = serverValue(player -> observation.minimumVelocityY);
                row.put("brewed_fall_minimum_velocity", fallingSpeed);
                check(fallingSpeed > ((Number) row.get("untreated_fall_minimum_velocity")).doubleValue() * .7,
                    "Slow Falling must reduce the measured descent speed compared with the actual untreated fall");
                check(baseline > 0 && descent == 0, "Native matching falls damage untreated player but do no damage with Slow Falling");
            }
            case "fire_resistance" -> {
                server(player -> {
                    player.level().setBlockAndUpdate(observation.target.blockPosition(), Blocks.CAMPFIRE.defaultBlockState());
                    player.level().setBlockAndUpdate(observation.control.blockPosition(), Blocks.CAMPFIRE.defaultBlockState());
                });
                context.waitTicks(60);
                check(serverValue(player -> observation.target.getHealth() == observation.targetHealthBefore
                    && observation.control.getHealth() < observation.controlHealthBefore),
                    "Matched native campfire contact damages untreated cow and leaves brewed cow unharmed");
            }
            case "water_breathing" -> {
                server(player -> {
                    pool(player, 2); pool(player, 14);
                    observation.target.teleportTo(2.5, 96, .5); observation.control.teleportTo(14.5, 96, .5);
                    observation.target.setAirSupply(5); observation.control.setAirSupply(5);
                });
                context.waitTicks(80);
                check(serverValue(player -> observation.target.isUnderWater() && observation.control.isUnderWater()),
                    "Both independently staged subjects must remain genuinely submerged");
                check(serverValue(player -> observation.target.getHealth() == observation.targetHealthBefore
                    && observation.control.getHealth() < observation.controlHealthBefore),
                    "Normal underwater ticks drown untreated cow but protect brewed cow from actual health loss");
            }
            case "swim_speed" -> {
                final double distance = travel(context, true, true, 20); row.put("brewed_native_swim_distance", distance);
                check(baseline > .25 && distance > baseline * 1.2, "Matched native submerged swimming must travel farther with Dolphin's Grace");
            }
            case "night_vision", "blindness" -> {
                if (kind.id().equals("blindness")) {
                    final double distance = travel(context, true, false, 20);
                    row.put("blinded_attempted_sprint_distance", distance);
                    check(distance < baseline * .9, "Blindness must prevent the native sprint that worked before brewing");
                }
                visionScene(context, kind.id().equals("night_vision"));
                screenshot(context, id.getPath() + "-vision-after");
                final double after = luminance(evidence.resolve(id.getPath() + "-vision-after.png"));
                final double before = ((Number) row.get("render_luminance_before")).doubleValue();
                row.put("render_luminance_after", after);
                check(kind.id().equals("night_vision") ? after > before + 10 : after < before - 10,
                    "Same fixed-camera world pixels must brighten with Night Vision or darken with Blindness");
            }
            case "invisible" -> {
                await(context, player -> observation.target.isInvisible() && !observation.control.isInvisible(), 40,
                    "Normal entity synchronization must make the brewed target invisible while the untreated control remains visible");
                check(serverValue(player -> observation.target.isInvisible() && !observation.control.isInvisible()),
                    "Native invisibility must alter the treated entity visibility while untreated entity remains visible");
                world.getConnection().waitForClientboundPackets();
                check(context.computeOnClient(client -> {
                    final Entity target = client.level.getEntity(observation.target.getId());
                    final Entity control = client.level.getEntity(observation.control.getId());
                    return target != null && target.isInvisible() && control != null && !control.isInvisible();
                }), "The real client receives the treated invisible and independent visible entities");
                invisibilityFrame(context, id, "after", row);
                row.put("render_review", "Matched first-person target and third-person player frames before and after the native throw. Review visible cow/player geometry disappearing while arena stays aligned; cow particles are not evidence of visible body. Human screenshot review remains pending.");
                row.put("behavior_observed", false);
            }
            case "fortune" -> {
                check(serverValue(player -> player.getLuck() > 0), "Native Luck must change the actual player luck attribute");
                row.put("actual_player_luck", serverValue(ServerPlayer::getLuck));
                fishWithLuck(context, row);
            }
            default -> throw new AssertionError("Unmapped effect-only BrewKind: " + kind.id());
        }
    }

    private void fishWithLuck(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> {
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 95, 0), new BlockPos(8, 99, 20))) {
                final boolean edge = pos.getY() == 95 || Math.abs(pos.getX()) == 8 || pos.getZ() == 20;
                player.level().setBlockAndUpdate(pos, edge ? Blocks.BEDROCK.defaultBlockState() : Blocks.WATER.defaultBlockState());
            }
            player.teleportTo(.5, 100, -2.5);
            player.setDeltaMovement(Vec3.ZERO);
            player.getInventory().setItem(0, new ItemStack(Items.FISHING_ROD));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(10); });
        context.waitTicks(5);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        await(context, player -> player.fishing != null && player.fishing.isOpenWaterFishing(), 80,
            "Native rod casting must put a real fishing hook into the open-water pool");
        await(context, player -> player.fishing != null && (int) field(player.fishing, "nibble") > 0, 1100,
            "Normal fishing ticks must produce a real bite without changing the fishing timer or loot");
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        await(context, player -> player.fishing == null && player.getMainHandItem().getDamageValue() > 0, 40,
            "Native reeling must retrieve the biting hook and wear the rod");
        await(context, player -> player.getInventory().getNonEquipmentItems().stream()
            .anyMatch(stack -> !stack.isEmpty() && !stack.is(Items.FISHING_ROD))
            || !player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                player.getBoundingBox().inflate(24)).isEmpty(), 60,
            "Normal fishing loot must actually appear or be collected while the brew's Luck is active");
        check(serverValue(player -> player.getLuck() > 0), "Luck must remain active when the fishing loot is generated");
        row.put("fishing_outcome", "Native cast, natural bite, native reel, rod wear and actual fishing loot while Luck remains active. A single catch does not establish the probability distribution.");
    }

    private double travel(final ClientGameTestContext context, final boolean sprint, final boolean water, final int ticks) {
        server(player -> {
            reset(player); if (water) player.teleportTo(.5, 97, .5);
            player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(5);
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); }); context.waitTicks(8);
        if (water) check(serverValue(ServerPlayer::isUnderWater), "Native swim input begins actually submerged");
        final Vec3 before = serverValue(ServerPlayer::position);
        if (sprint) context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL);
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
        try { context.waitTicks(ticks); }
        finally { context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W); context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL); }
        final Vec3 after = serverValue(ServerPlayer::position);
        return Math.sqrt(Math.pow(after.x - before.x, 2) + Math.pow(after.z - before.z, 2));
    }

    private double jump(final ClientGameTestContext context) {
        server(BrewPotionEffectsAbilitiesClientAcceptance::reset); world.getConnection().waitForClientboundPackets();
        context.waitTicks(8); check(serverValue(ServerPlayer::onGround), "Jump trial starts on real ground");
        server(player -> observation.resetMotion());
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_SPACE);
        context.waitTicks(2);
        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_SPACE);
        context.waitTicks(34);
        return serverValue(player -> observation.maxY - 100);
    }

    private double fall(final ClientGameTestContext context, final boolean protectedFall) {
        server(player -> { player.setHealth(20); player.teleportTo(.5, 109, -5.5); player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance(); observation.resetMotion(); });
        world.getConnection().waitForClientboundPackets();
        await(context, player -> !player.onGround() && player.getY() > 104, 40,
            "The teleported fall trial must actually become airborne before waiting for landing");
        await(context, ServerPlayer::onGround, protectedFall ? 220 : 80, "Native gravity must carry the staged falling player to the arena floor");
        context.waitTicks(3);
        final double damage = 20 - serverValue(ServerPlayer::getHealth);
        server(BrewPotionEffectsAbilitiesClientAcceptance::reset); context.waitTicks(12);
        return damage;
    }

    private double melee(final ClientGameTestContext context) {
        final UUID target = serverValue(player -> {
            reset(player); player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD)); player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges(); return cow(player, new Vec3(.5, 100, 2.5)).getUUID();
        });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(25);
        look(context, serverValue(player -> player.level().getEntity(target).getBoundingBox().getCenter()));
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT); context.waitTicks(5);
        final double damage = serverValue(player -> 40 - ((Mob) player.level().getEntity(target)).getHealth());
        server(player -> player.level().getEntity(target).discard()); return damage;
    }

    private double mine(final ClientGameTestContext context, final int ticks) {
        final BlockPos block = new BlockPos(0, 100, 2);
        server(player -> { reset(player); player.getInventory().setItem(0, ItemStack.EMPTY); player.inventoryMenu.broadcastChanges(); player.level().setBlockAndUpdate(block, Blocks.DIRT.defaultBlockState()); });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(4); look(context, Vec3.atCenterOf(block));
        int broken = -1; context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        try {
            for (int i = 1; i <= ticks; i++) {
                context.waitTicks(1);
                if (serverValue(player -> player.level().getBlockState(block).isAir())) { broken = i; break; }
            }
        } finally { context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT); }
        server(player -> player.level().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState())); return broken;
    }

    private double cactus(final ClientGameTestContext context, final boolean absorbed) {
        server(player -> {
            player.level().setBlockAndUpdate(new BlockPos(0, 99, -4), Blocks.SAND.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(0, 100, -4), Blocks.CACTUS.defaultBlockState());
            player.teleportTo(.5, 100, -5.1); player.setDeltaMovement(Vec3.ZERO); player.setHealth(20);
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); }); context.waitTicks(14);
        final float before = serverValue(ServerPlayer::getAbsorptionAmount);
        if (absorbed) check(before > 0, "Native Absorption provides real expendable extra health");
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
        try { await(context, player -> absorbed ? player.getAbsorptionAmount() < before : player.getHealth() < 20, 45,
            "Native walking must contact cactus and spend the appropriate health pool"); }
        finally { context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W); }
        final double damage = 20 - serverValue(ServerPlayer::getHealth);
        server(player -> { player.level().setBlockAndUpdate(new BlockPos(0, 100, -4), Blocks.AIR.defaultBlockState()); reset(player); });
        context.waitTicks(12); return damage;
    }

    private void buildSwimLane() {
        server(player -> {
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-3, 94, -3), new BlockPos(3, 101, 26))) {
                final boolean edge = pos.getY() == 94 || Math.abs(pos.getX()) == 3 || pos.getZ() == -3 || pos.getZ() == 26;
                player.level().setBlockAndUpdate(pos, edge ? Blocks.BEDROCK.defaultBlockState() : Blocks.WATER.defaultBlockState());
            }
        });
    }

    private static void pool(final ServerPlayer player, final int x) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x - 1, 94, -1), new BlockPos(x + 1, 99, 1)))
            player.level().setBlockAndUpdate(pos, pos.getY() == 94 ? Blocks.BEDROCK.defaultBlockState() : Blocks.WATER.defaultBlockState());
    }

    private void visionScene(final ClientGameTestContext context, final boolean dark) {
        server(player -> {
            reset(player);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-10, 100, 18), new BlockPos(10, 107, 19)))
                player.level().setBlockAndUpdate(pos, Blocks.QUARTZ_BLOCK.defaultBlockState());
            if (dark) {
                for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-10, 99, -10), new BlockPos(10, 108, 20))) {
                    if (pos.getY() == 108 || pos.getY() == 99 || pos.getX() == -10 || pos.getX() == 10 || pos.getZ() == -10)
                        player.level().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                }
                player.level().getServer().getCommands().performPrefixedCommand(player.level().getServer().createCommandSourceStack(), "time set 18000");
            }
        });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); }); context.waitTicks(12);
    }

    private static double luminance(final Path path) throws Exception {
        final BufferedImage image = ImageIO.read(path.toFile());
        double sum = 0; int count = 0;
        for (int y = image.getHeight() / 4; y < image.getHeight() / 2; y++)
            for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x++) {
                final int rgb = image.getRGB(x, y);
                sum += .2126 * (rgb >> 16 & 255) + .7152 * (rgb >> 8 & 255) + .0722 * (rgb & 255); count++;
            }
        return sum / count;
    }

    private void readBook(final ClientGameTestContext context, final Identifier id, final BrewKind kind, final Map<String, Object> row) throws Exception {
        final String section = "brew_entry_" + kind.id();
        final ManualProfile profile = ManualProfile.profiles().stream().filter(value -> value.sections().contains(section)).findFirst()
            .orElseThrow(() -> new AssertionError("Missing canonical player-book section " + section));
        server(player -> { player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(profile.id()).get()));
            player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.player.setXRot(-75)); context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, section);
        final String text = context.computeOnClient(client -> ManualArticleCatalog.article(profile, section).body().getString());
        check(!text.isBlank(), "Canonical guide must contain readable player instructions");
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection"))
                && (int) field(client.gui.screen(), "bodyPage") == expected), "Native page input reaches each exact canonical guide page");
            screenshot(context, id.getPath() + "-canonical-book-" + page);
        }
        row.put("book", profile.id()); row.put("book_status", "ALL_PAGES_REACHED"); row.put("book_pages_read", pages); row.put("book_text", text);
        row.put("guide_mapping", "Exact registry item maps to BrewKind " + kind.id() + " and its canonical guide. Shared alias behavior does not establish alias acquisition.");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
    }

    private void invisibilityFrame(final ClientGameTestContext context, final Identifier id, final String phase,
        final Map<String, Object> row) throws Exception {
        world.getConnection().waitForClientboundPackets();
        final Vec3 targetPoint = new Vec3(2.5, 100.7, .5);
        check(context.computeOnClient(client -> client.options.getCameraType().isFirstPerson()),
            "Matched target capture begins in first person");
        look(context, targetPoint);
        screenshot(context, id.getPath() + "-invisibility-target-" + phase);
        // Native perspective toggle: first-person -> rear third-person -> front third-person -> first-person.
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F5); context.waitTicks(3);
        try {
            check(!context.computeOnClient(client -> client.options.getCameraType().isFirstPerson()),
                "Native perspective key reaches third person");
            screenshot(context, id.getPath() + "-invisibility-player-thirdperson-" + phase);
            row.put("invisibility_" + phase + "_view", context.computeOnClient(client -> Map.of(
                "player_position", client.player.position().toString(), "yaw", client.player.getYRot(),
                "pitch", client.player.getXRot(), "player_invisible", client.player.isInvisible())));
        } finally {
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F5); context.waitTicks(2);
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F5); context.waitTicks(2);
        }
        check(context.computeOnClient(client -> client.options.getCameraType().isFirstPerson()),
            "Restore first person before the native throw or next trial");
    }

    private static Mob cow(final ServerPlayer player, final Vec3 point) {
        final Mob cow = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:cow")).create(player.level(), EntitySpawnReason.COMMAND);
        cow.setPos(point.x, point.y, point.z); cow.setNoAi(true); cow.setPersistenceRequired();
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40); cow.setHealth(40); player.level().addFreshEntity(cow); return cow;
    }

    private static void reset(final ServerPlayer player) {
        player.teleportTo(.5, 100, .5); player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance(); player.setSprinting(false);
    }

    private static final class Observation {
        final ServerPlayer player; final Identifier item; final Mob target; final Mob control;
        final Set<String> projectiles = new LinkedHashSet<>();
        int ticks; double maxY; double minimumVelocityY; float playerHealthBefore; float targetHealthBefore; float controlHealthBefore;
        Observation(final ServerPlayer player, final Identifier item, final Mob target, final Mob control) {
            this.player = player; this.item = item; this.target = target; this.control = control; resetMotion();
        }
        void resetMotion() { maxY = player.getY(); minimumVelocityY = 0; }
        void tick() { ticks++; maxY = Math.max(maxY, player.getY()); minimumVelocityY = Math.min(minimumVelocityY, player.getDeltaMovement().y); }
        void loaded(final Entity entity) {
            if (entity instanceof AbstractThrownPotion potion && potion.getOwner() == player && potion.getItem().is(BuiltInRegistries.ITEM.getValue(item)))
                projectiles.add(entity.getUUID().toString());
        }
        boolean finishedImpact() { return !projectiles.isEmpty() && projectiles.stream().allMatch(id -> player.level().getEntity(UUID.fromString(id)) == null); }
        Map<String, Object> report() {
            final Map<String, Object> report = new LinkedHashMap<>();
            report.put("native_projectile_uuids", List.copyOf(projectiles)); report.put("server_ticks", ticks);
            report.put("target_uuid", target.getUUID().toString()); report.put("independent_control_uuid", control.getUUID().toString());
            report.put("target_health", target.getHealth()); report.put("control_health", control.getHealth());
            report.put("target_air", target.getAirSupply()); report.put("control_air", control.getAirSupply());
            report.put("player_health", player.getHealth()); report.put("player_position", player.position().toString());
            report.put("maximum_player_y", maxY); report.put("minimum_vertical_velocity", minimumVelocityY);
            report.put("actual_player_effects", player.getActiveEffects().stream().map(Object::toString).toList());
            report.put("actual_target_effects", target.getActiveEffects().stream().map(Object::toString).toList());
            report.put("target_invisible", target.isInvisible());
            report.put("control_invisible", control.isInvisible());
            report.put("actual_control_effects", control.getActiveEffects().stream().map(Object::toString).toList()); return report;
        }
    }

    private static void look(final ClientGameTestContext context, final Vec3 target) {
        context.runOnClient(client -> {
            final Vec3 difference = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-difference.x, difference.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(difference.y, Math.sqrt(difference.x * difference.x + difference.z * difference.z))));
        }); context.waitTicks(3);
    }

    private static void release(final ClientGameTestContext context) {
        for (int key : List.of(com.mojang.blaze3d.platform.InputConstants.KEY_W, com.mojang.blaze3d.platform.InputConstants.KEY_SPACE, com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL)) context.getInput().releaseKey(key);
        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT); context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
    }

    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> condition, final int ticks, final String message) {
        for (int i = 0; i < ticks && !serverValue(condition::test); i++) context.waitTicks(1);
        check(serverValue(condition::test), message);
    }
    private static Object field(final Object object, final String name) {
        try { final var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T serverValue(final Function<ServerPlayer, T> action) { return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer())); }
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots); }
    private static String classHash(final Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing loaded class bytes: " + type);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }
    private void write(final boolean completed) throws Exception {
        if (evidence == null) return;
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", completed); report.put("selected_scenarios_without_failure", completed && failures.isEmpty());
        report.put("all_censused_behaviors_passed", completed && results.size() == 28
            && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status")))) ;
        report.put("all_item_contracts_complete", false); report.put("started_at", started); report.put("updated_at", System.currentTimeMillis());
        report.put("execution", "Rendered native Fabric client, exact books and normal input potion throws. Before/after player input trials and independent untreated hazard subjects. Outcomes are observed from natural ticks; no direct effect/damage/healing/impact calls.");
        report.put("class_sha256", Map.of("test", classHash(BrewPotionEffectsAbilitiesClientAcceptance.class), "brew_item", classHash(BrewItem.class)));
        report.put("registered_item_count", results.size()); report.put("items", results); report.put("failures", failures); report.put("screenshots", screenshots);
        final Path temporary = evidence.resolve("brew-potion-effects-abilities.json.tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temporary, evidence.resolve("brew-potion-effects-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
