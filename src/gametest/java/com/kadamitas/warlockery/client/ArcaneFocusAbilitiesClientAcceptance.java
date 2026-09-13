package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.item.InfernalPactEffects;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.magic.InfernalPower;
import com.kadamitas.warlockery.magic.MagicConstructData;
import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathProfile;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.magic.MagicPathRules.ActionKind;
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
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class ArcaneFocusAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final Vec3 START = new Vec3(0.5, 100, -2);
    private static final Vec3 TARGET = new Vec3(0.5, 100, 0.5);
    private static final BlockPos WORK = new BlockPos(2, 99, -1);
    private static final AABB AREA = new AABB(-22, 96, -22, 23, 118, 23);
    private static final List<String> CASES = List.of(
        "presentation", "unattuned", "empty_reserve", "cycle_paths",
        "imp_self", "imp_ignite", "imp_protect", "imp_evaporate",
        "infernal_enthrall", "infernal_sacrifice_heal", "infernal_attack", "infernal_command",
        "infernal_explosion", "infernal_projectile", "infernal_web", "infernal_fire", "infernal_speed",
        "infernal_teleport", "infernal_leaping", "infernal_flight", "infernal_aquatic", "infernal_undead",
        "grave_self", "grave_bind", "grave_command", "light_self", "light_prison", "light_wall",
        "otherwhere_short", "otherwhere_block", "otherwhere_recall", "otherwhere_lift",
        "overworld_shockwave", "overworld_knockback", "overworld_disarm", "overworld_raise",
        "overworld_transmute", "overworld_launch", "overworld_pull", "overworld_invalid_target",
        "sky_self", "sky_target", "sky_updraft");
    private final Map<String, Map<String, Object>> cases = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private final Map<String, Object> guides = new LinkedHashMap<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private String activeCase;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("arcane-focus-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            for (String id : CASES) cases.put(id, new LinkedHashMap<>(Map.of("status", "NOT_RUN")));
            final String requested = System.getProperty("warlockery.focusCases", "").trim();
            final Set<String> selected = requested.isEmpty() ? Set.copyOf(CASES)
                : Set.copyOf(Arrays.stream(requested.split(",")).map(String::trim).toList());
            check(CASES.containsAll(selected), "Only known focus cases may be selected");
            try (var created = context.worldBuilder().create()) {
                world = created;
                world.getConnection().waitForChunksRender();
                activeCase = CASES.getFirst();
                reset(context, null);
                if (!selected.equals(Set.of("presentation"))) {
                readBook(context, "focus");
                readBook(context, "arcane_focus");
                readBook(context, "arcane_focus_targets");
                readBook(context, "ingredient_brew_grave");
                readBook(context, "infusion_passives");
                readBook(context, "imp_attunement");
                readBook(context, "infernal_sacrifices");
                readBook(context, "infernal_sacrifices_movement");
                }
                for (String id : CASES) {
                    if (!selected.contains(id)) continue;
                    activeCase = id;
                    try {
                        reset(context, pathFor(id));
                        exercise(context, id);
                        screenshot(context, id + "-native-outcome");
                        cases.get(id).put("status", "PASSED");
                    } catch (Throwable failure) {
                        cases.get(id).put("status", "FAILED");
                        cases.get(id).put("failure", failure.toString());
                        failures.add(id + ": " + failure);
                        try { screenshot(context, id + "-failure"); } catch (Throwable capture) { failure.addSuppressed(capture); }
                    }
                    writeReport();
                }
            }
            writeReport();
            if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
            System.out.println("WARLOCKERY_ARCANE_FOCUS_ABILITIES_PASS " + evidence);
        } catch (Throwable failure) {
            failures.add(failure.toString());
            try { screenshot(context, "suite-failure"); } catch (Throwable capture) { failure.addSuppressed(capture); }
            try { writeReport(); } catch (Exception writing) { failure.addSuppressed(writing); }
            throw new AssertionError("Arcane Focus evidence: " + evidence, failure);
        }
    }

    private void exercise(final ClientGameTestContext context, final String id) throws Exception {
        switch (id) {
            case "presentation" -> presentation(context);
            case "unattuned" -> {
                final Map<String, Integer> before = value(player -> {
                    final Map<String, Integer> snapshot = new LinkedHashMap<>();
                    for (MagicPath path : MagicPath.values()) snapshot.put(path.id(), MagicPathState.reserve(player, path));
                    return snapshot;
                });
                final Vec3 positionBefore = value(ServerPlayer::position);
                final String expected = context.computeOnClient(client ->
                    Component.translatable("message.warlockery.magic.not_attuned").getString());
                air(context, false, -45);
                context.waitFor(client -> field(client.gui.hud, "overlayMessageString") instanceof Component message
                    && message.getString().equals(expected), 60);
                check(value(player -> MagicPathState.active(player).isEmpty() && MagicPathState.selected(player).isEmpty()
                    && java.util.Arrays.stream(MagicPath.values()).allMatch(path -> MagicPathState.reserve(player, path) == before.get(path.id()))),
                    "Unattuned refusal neither grants a path nor changes any reserve");
                check(value(player -> player.getActiveEffects().isEmpty() && !player.getAbilities().flying
                    && player.position().distanceTo(positionBefore) < 0.2),
                    "Unattuned self-use produces no spell effect, flight or teleport");
                cases.get(id).put("refusal_overlay", expected);
                cases.get(id).put("reserves_unchanged", before);
                note("Native unattuned self-use displays the exact dormant Focus guidance and leaves paths, reserves and spell state unchanged.");
            }
            case "empty_reserve" -> {
                server(player -> check(MagicPathState.spend(player, MagicPath.LIGHT, MagicPath.LIGHT.maximumReserve()), "Drain real reserve"));
                air(context, false, -45); context.waitTicks(10);
                check(value(player -> !player.hasEffect(MobEffects.INVISIBILITY) && MagicPathState.reserve(player, MagicPath.LIGHT) == 0),
                    "Zero reserve refuses invisibility without spending");
                note("Native personal use refuses concealment at zero reserve.");
            }
            case "cycle_paths" -> {
                server(player -> { MagicPathState.grantPermanent(player, MagicPath.IMP); MagicPathState.grantPermanent(player, MagicPath.LIGHT); });
                final Map<String, Integer> before = value(this::reserves);
                air(context, true, -45);
                await(context, player -> MagicPathState.selected(player).orElse(null) == MagicPath.IMP, "Cycle Light to Imp");
                air(context, true, -45);
                await(context, player -> MagicPathState.selected(player).orElse(null) == MagicPath.LIGHT, "Cycle back to Light");
                check(value(this::reserves).equals(before), "Cycling spends no reserve");
                note("Native crouch-air cycles both ways without spending reserve.");
            }
            case "imp_self" -> paid(context, ActionKind.SELF, () -> air(context, false, -45),
                player -> player.getEffect(MobEffects.FIRE_RESISTANCE) != null && player.getEffect(MobEffects.FIRE_RESISTANCE).getDuration() > 500,
                "Personal Imp use grants long Fire Resistance beyond the short passive");
            case "imp_ignite", "imp_protect" -> {
                final UUID target = spawn(EntityTypes.COW, TARGET);
                final boolean protect = id.equals("imp_protect");
                paid(context, ActionKind.TARGET, () -> entity(context, target, protect), player -> protect
                    ? living(player, target).hasEffect(MobEffects.FIRE_RESISTANCE) : living(player, target).isOnFire(),
                    protect ? "Native alternate grants target Fire Resistance" : "Native target use ignites the creature");
            }
            case "imp_evaporate" -> {
                server(player -> {
                    player.level().setBlockAndUpdate(WORK.east(), Blocks.WATER.defaultBlockState());
                    player.level().setBlockAndUpdate(WORK.east().above(), Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
                });
                paid(context, ActionKind.WORLD, () -> block(context, WORK, false),
                    player -> player.level().getBlockState(WORK.east()).isAir() && player.level().getBlockState(WORK.east().above()).isAir(),
                    "Imp removes nearby water and the waterlogged fence itself");
            }
            case "infernal_enthrall" -> enthrall(context, spawn(EntityTypes.COW, TARGET));
            case "infernal_explosion", "infernal_projectile", "infernal_web", "infernal_fire", "infernal_speed",
                 "infernal_teleport", "infernal_leaping", "infernal_flight", "infernal_aquatic", "infernal_undead" ->
                infernalPower(context, InfernalPower.valueOf(id.substring("infernal_".length()).toUpperCase(java.util.Locale.ROOT)));
            case "infernal_sacrifice_heal" -> {
                final UUID target = spawn(EntityTypes.COW, TARGET);
                enthrall(context, target);
                final int before = value(player -> MagicPathState.reserve(player, MagicPath.INFERNAL));
                entity(context, target, true);
                await(context, player -> player.level().getEntity(target) == null && MagicPathState.lastPower(player) == InfernalPower.HEALING,
                    "Second crouch-use sacrifices the owned cow and acquires Healing");
                check(value(player -> MagicPathState.reserve(player, MagicPath.INFERNAL))
                    == Math.min(MagicPath.INFERNAL.maximumReserve(), before + 24) - MagicPathProfile.forPath(MagicPath.INFERNAL).targetCost(),
                    "Sacrifice replenishes reserve and pays target cost");
                server(player -> { player.setHealth(8); player.getFoodData().setFoodLevel(8); });
                paid(context, ActionKind.SELF, () -> air(context, false, -45), player -> player.getHealth() >= 16 && player.getFoodData().getFoodLevel() >= 14,
                    "Native personal use activates the power acquired through native sacrifice");
            }
            case "infernal_attack" -> {
                final UUID thrall = spawn(EntityTypes.ZOMBIE, TARGET);
                enthrall(context, thrall);
                final UUID enemy = spawn(EntityTypes.COW, new Vec3(2, 100, 0));
                paid(context, ActionKind.TARGET, () -> entity(context, enemy, false), player -> {
                    final Mob mob = (Mob) living(player, thrall);
                    return mob.getTarget() != null && mob.getTarget().getUUID().equals(enemy);
                }, "Native normal use commands the natively enthralled zombie to attack");
            }
            case "infernal_command", "grave_command" -> {
                final UUID target = spawn(EntityTypes.ZOMBIE, TARGET);
                if (id.startsWith("infernal")) enthrall(context, target); else bindGrave(context, target);
                server(player -> ((Mob) living(player, target)).setNoAi(false));
                paid(context, ActionKind.WORLD, () -> block(context, WORK, false), player -> {
                    final var path = ((Mob) living(player, target)).getNavigation().getPath();
                    return path != null && path.getTarget().distSqr(WORK.above()) <= 4;
                }, "Native block use sets owned undead navigation toward the clicked position");
            }
            case "grave_self" -> paid(context, ActionKind.SELF, () -> air(context, false, -45),
                player -> player.getEffect(MobEffects.NIGHT_VISION) != null && player.getEffect(MobEffects.NIGHT_VISION).getDuration() > 1000,
                "Native Grave self use grants long Night Vision beyond the passive");
            case "grave_bind" -> bindGrave(context, spawn(EntityTypes.ZOMBIE, TARGET));
            case "light_self" -> paid(context, ActionKind.SELF, () -> air(context, false, -45), player -> player.hasEffect(MobEffects.INVISIBILITY),
                "Native Light self use grants Invisibility");
            case "light_prison" -> {
                final UUID target = spawn(EntityTypes.COW, TARGET);
                paid(context, ActionKind.TARGET, () -> entity(context, target, false), player -> living(player, target).hasEffect(MobEffects.GLOWING)
                    && living(player, target).hasEffect(MobEffects.SLOWNESS) && MagicConstructData.get(player.level()).activeConstructs() == 1
                    && barriers(player) > 0, "Native Light creature use creates the temporary prison and restraints");
                screenshot(context, id + "-active"); context.waitTicks(205);
                check(value(player -> MagicConstructData.get(player.level()).activeConstructs() == 0 && barriers(player) == 0), "Prison expires and restores blocks");
                note("Actual 205-tick expiration verified; barrier cells are invisible in ordinary Survival rendering.");
            }
            case "light_wall" -> {
                paid(context, ActionKind.WORLD, () -> block(context, WORK, false), player -> MagicConstructData.get(player.level()).activeConstructs() == 1
                    && barriers(player) == 9, "Native Light block use builds a nine-block wall");
                screenshot(context, id + "-active"); context.waitTicks(605);
                check(value(player -> MagicConstructData.get(player.level()).activeConstructs() == 0 && barriers(player) == 0), "Wall expires and restores blocks");
                note("Actual 605-tick expiration verified; barrier cells are invisible in ordinary Survival rendering.");
            }
            case "otherwhere_short" -> paid(context, ActionKind.SELF, () -> air(context, false, 6),
                player -> player.getZ() > 6 && player.getY() >= 99.9, "Native air use teleports forward onto supported ground");
            case "otherwhere_block" -> paid(context, ActionKind.WORLD, () -> block(context, WORK, false),
                player -> player.position().distanceToSqr(Vec3.atBottomCenterOf(WORK.above())) < 1, "Native block-face use reaches safe adjacent space");
            case "otherwhere_recall" -> {
                final BlockPos origin = value(ServerPlayer::blockPosition);
                paid(context, ActionKind.WORLD, () -> block(context, WORK, true), player -> MagicPathState.recall(player)
                    .filter(recall -> recall.position().equals(origin)).isPresent(), "Native crouch-block stores the actual caster position");
                position(context, new Vec3(8.5, 100, -2));
                paid(context, ActionKind.SELF, () -> air(context, true, -45), player -> player.position().distanceToSqr(Vec3.atBottomCenterOf(origin.above())) < 2,
                    "Native crouch-air with only Otherwhere active returns to the saved point");
            }
            case "otherwhere_lift" -> {
                final UUID target = spawn(EntityTypes.COW, TARGET);
                paid(context, ActionKind.TARGET, () -> entity(context, target, false), player -> player.getY() > 107 && living(player, target).getY() > 107,
                    "Native target use raises both actual entities eight blocks");
            }
            case "overworld_shockwave" -> {
                final UUID target = spawn(EntityTypes.COW, TARGET);
                final float health = value(player -> living(player, target).getHealth());
                paid(context, ActionKind.SELF, () -> air(context, false, -45), player -> living(player, target).getHealth() <= health - 4,
                    "Native shockwave damages a nearby creature");
            }
            case "overworld_knockback" -> {
                final UUID target = spawn(EntityTypes.ZOMBIE, TARGET);
                server(player -> living(player, target).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE)));
                paid(context, ActionKind.TARGET, () -> entity(context, target, false), player -> living(player, target).getDeltaMovement().horizontalDistanceSqr() > 0.05
                    || living(player, target).position().distanceToSqr(TARGET) > 0.1, "Native use repels metal-armored creature");
            }
            case "overworld_disarm" -> {
                final UUID target = spawn(EntityTypes.ZOMBIE, TARGET);
                server(player -> living(player, target).setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)));
                paid(context, ActionKind.TARGET, () -> entity(context, target, true), player -> living(player, target).getMainHandItem().isEmpty()
                    && player.level().getEntitiesOfClass(ItemEntity.class, AREA, item -> item.getItem().is(Items.IRON_SWORD)).size() == 1,
                    "Native alternate removes held metal and creates its dropped item");
            }
            case "overworld_raise" -> paid(context, ActionKind.WORLD, () -> block(context, WORK, false),
                player -> player.level().getBlockState(WORK.above()).is(Blocks.STONE) && player.level().getBlockState(WORK.above(3)).is(Blocks.STONE),
                "Native block use raises three matching earth blocks");
            case "overworld_transmute" -> {
                server(player -> player.level().setBlockAndUpdate(WORK, Blocks.IRON_ORE.defaultBlockState()));
                paid(context, ActionKind.WORLD, () -> block(context, WORK, false), player -> player.level().getBlockState(WORK).is(Blocks.STONE)
                    && !player.level().getEntitiesOfClass(ItemEntity.class, AREA, item -> item.getItem().is(Items.RAW_IRON)).isEmpty(),
                    "Native ore use releases raw material and replaces ore with Stone");
            }
            case "overworld_launch" -> paid(context, ActionKind.WORLD, () -> block(context, WORK, true),
                player -> player.level().getBlockState(WORK).isAir()
                    && !player.level().getEntitiesOfClass(FallingBlockEntity.class, AREA, entity -> entity.getDeltaMovement().lengthSqr() > 0.05).isEmpty(),
                "Native crouch-earth use launches a real moving falling-block projectile");
            case "overworld_pull" -> {
                final UUID metal = value(player -> {
                    player.level().setBlockAndUpdate(WORK, Blocks.OAK_PLANKS.defaultBlockState());
                    final ItemEntity drop = new ItemEntity(player.level(), 10.5, 100.2, -2, new ItemStack(Items.IRON_INGOT));
                    drop.setDeltaMovement(Vec3.ZERO); player.level().addFreshEntity(drop); return drop.getUUID();
                });
                try {
                    paid(context, ActionKind.WORLD, () -> block(context, WORK, true), player -> {
                        final Entity drop = player.level().getEntity(metal);
                        return drop != null && drop.getX() < 9.5 && drop.distanceTo(player) < 9.0;
                    }, "Native crouch-non-earth use pulls an ingot from beyond passive magnet range");
                } finally {
                    cases.get(id).put("drop_after", value(player -> {
                        final Entity drop = player.level().getEntity(metal);
                        return drop == null ? "picked up or removed" : Map.of("position", drop.position().toString(),
                            "velocity", drop.getDeltaMovement().toString(), "distance_to_player", drop.distanceTo(player));
                    }));
                }
            }
            case "overworld_invalid_target" -> {
                final UUID target = spawn(EntityTypes.COW, TARGET);
                final int before = value(player -> MagicPathState.reserve(player, MagicPath.OVERWORLD));
                final float health = value(player -> living(player, target).getHealth());
                entity(context, target, false); context.waitTicks(10);
                check(value(player -> MagicPathState.reserve(player, MagicPath.OVERWORLD) == before && living(player, target).getHealth() == health),
                    "Invalid unarmored target refuses control without spending reserve or hurting creature");
                note("Actual invalid-target refusal checked with no metal on the cow.");
            }
            case "sky_self" -> {
                final float health = value(ServerPlayer::getHealth);
                paid(context, ActionKind.SELF, () -> air(context, false, -45),
                    player -> player.getY() > 104 && player.hasEffect(MobEffects.SLOW_FALLING), "Native Sky self use lifts the actual player over four blocks");
                context.runOnClient(client -> client.player.setXRot(60));
                context.waitTicks(2);
                screenshot(context, "sky_self-airborne");
                int ticks = 0;
                double peak = value(ServerPlayer::getY);
                while (ticks++ < 600 && !value(player -> player.onGround() && Math.abs(player.getY() - START.y) < 0.1)) {
                    context.waitTicks(1);
                    peak = Math.max(peak, value(ServerPlayer::getY));
                }
                cases.get(id).put("flight", Map.of("peak_y", peak, "landing_y", value(ServerPlayer::getY),
                    "health_before", health, "health_after", value(ServerPlayer::getHealth), "ticks", ticks,
                    "on_ground", value(ServerPlayer::onGround), "client_position", context.computeOnClient(client -> client.player.position().toString()),
                    "velocity", value(player -> player.getDeltaMovement().toString())));
                check(value(player -> player.onGround() && player.getHealth() == health && Math.abs(player.getY() - START.y) < 0.1),
                    "Sky launch returns to the platform without fall injury");
            }
            case "sky_target" -> {
                final UUID target = spawn(EntityTypes.COW, TARGET);
                paid(context, ActionKind.TARGET, () -> entity(context, target, false), player -> living(player, target).hasEffect(MobEffects.LEVITATION)
                    && living(player, target).hasEffect(MobEffects.SLOW_FALLING), "Native target use levitates creature with safe descent");
            }
            case "sky_updraft" -> {
                final UUID target = spawn(EntityTypes.COW, TARGET);
                paid(context, ActionKind.WORLD, () -> block(context, WORK, false), player -> living(player, target).hasEffect(MobEffects.LEVITATION)
                    && player.hasEffect(MobEffects.LEVITATION), "Native block updraft affects both nearby living entities");
            }
            default -> throw new AssertionError("Unimplemented case " + id);
        }
    }

    private void enthrall(final ClientGameTestContext context, final UUID target) {
        paid(context, ActionKind.TARGET, () -> entity(context, target, true), player -> player.getStringUUID().equals(
            WarlockeryEntityData.get(living(player, target)).getStringOr(InfernalPactEffects.OWNER_KEY, "")), "Native Infernal enthrallment acquires ownership");
    }
    private void bindGrave(final ClientGameTestContext context, final UUID target) {
        paid(context, ActionKind.TARGET, () -> entity(context, target, false), player -> player.getStringUUID().equals(
            WarlockeryEntityData.get(living(player, target)).getStringOr("WarlockeryGraveOwner", ""))
                && WarlockeryEntityData.get(living(player, target)).getLongOr("WarlockeryGraveExpiration", 0) > player.level().getGameTime(),
            "Native Grave creature use binds actual undead with live expiration");
    }
    private void paid(final ClientGameTestContext context, final ActionKind kind, final Runnable input,
                      final Predicate<ServerPlayer> outcome, final String assertion) {
        final MagicPath path = value(player -> MagicPathState.selected(player).orElseThrow());
        final int before = value(player -> MagicPathState.reserve(player, path));
        final int cost = MagicPathProfile.forPath(path).cost(kind);
        input.run();
        try {
            await(context, player -> MagicPathState.reserve(player, path) == before - cost && outcome.test(player), assertion);
        } finally {
            cases.get(activeCase).put("player_after", value(player -> Map.of("position", player.position().toString(),
                "velocity", player.getDeltaMovement().toString(), "on_ground", player.onGround(),
                "slow_falling", player.hasEffect(MobEffects.SLOW_FALLING))));
            cases.get(activeCase).put("client_position_after", context.computeOnClient(client -> client.player.position().toString()));
        }
        @SuppressWarnings("unchecked") final List<Object> uses = (List<Object>) cases.get(activeCase).computeIfAbsent("native_uses", ignored -> new ArrayList<>());
        uses.add(Map.of("path", path.id(), "action", kind.name(), "reserve_before", before,
            "reserve_after", value(player -> MagicPathState.reserve(player, path)), "assertion", assertion));
    }
    private void reset(final ClientGameTestContext context, final MagicPath path) {
        if (context.computeOnClient(client -> client.gui.screen() != null)) {
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
        }
        server(player -> {
            player.level().getEntities((Entity) null, AREA, entity -> entity != player).forEach(Entity::discard);
            player.level().getDataStorage().set(MagicConstructData.TYPE, new MagicConstructData());
            WarlockeryEntityData.get(player).remove("WarlockeryMagicPaths");
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(false);
            player.getAbilities().flying = false; player.getAbilities().invulnerable = false; player.onUpdateAbilities();
            player.removeAllEffects(); player.clearFire(); player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(5);
            player.getInventory().clearContent(); player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("arcane_focus").get()));
            for (BlockPos pos : BlockPos.betweenClosed(-20, 99, -20, 20, 114, 20)) {
                player.level().setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance(); player.teleportTo(START.x, START.y, START.z);
            if (path != null) MagicPathState.grantPermanent(player, path);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets(); world.getConnection().waitForChunksRender();
        context.getInput().pressKey(GLFW.GLFW_KEY_1); context.waitTicks(5);
        cases.get(activeCase).put("initial_path", path == null ? "none" : path.id());
    }
    private UUID spawn(final EntityType<?> type, final Vec3 pos) {
        return value(player -> {
            final Entity entity = type.create(player.level(), EntitySpawnReason.TRIGGERED);
            check(entity instanceof Mob, "Fixture target is a live mob");
            final Mob mob = (Mob) entity;
            mob.setNoAi(true); mob.setPersistenceRequired(); mob.setPos(pos);
            if (type == EntityTypes.ZOMBIE) mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            check(player.level().addFreshEntity(mob), "Fixture target spawns"); return mob.getUUID();
        });
    }
    private void entity(final ClientGameTestContext context, final UUID target, final boolean secondary) {
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> client.level.getEntities((Entity) null, AREA, entity -> entity.getUUID().equals(target)).size() == 1, 40);
        secondary(context, secondary, () -> {
            look(context, value(player -> living(player, target).getBoundingBox().getCenter()));
            check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(target)),
                "Native focus pointer hits intended creature");
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        });
    }
    private void block(final ClientGameTestContext context, final BlockPos pos, final boolean secondary) {
        world.getConnection().waitForClientboundPackets();
        secondary(context, secondary, () -> {
            look(context, new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5));
            check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)),
                "Native focus pointer hits intended block");
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        });
    }
    private void air(final ClientGameTestContext context, final boolean secondary, final float pitch) {
        secondary(context, secondary, () -> {
            context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(pitch); }); context.waitTicks(3);
            check(context.computeOnClient(client -> client.hitResult != null && client.hitResult.getType() == HitResult.Type.MISS),
                "Native personal focus use aims at empty air");
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        });
    }
    private void secondary(final ClientGameTestContext context, final boolean secondary, final Runnable action) {
        await(context, player -> player.connection.hasClientLoaded() && !awaitingTeleport(player),
            "Native connection and teleport acknowledgement are ready before Focus input");
        context.waitTicks(5);
        if (secondary) context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
        try { context.waitTicks(2); action.run(); context.waitTicks(2); }
        finally { if (secondary) context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT); }
    }
    private static void look(final ClientGameTestContext context, final Vec3 target) {
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        }); context.waitTicks(3);
    }
    private void position(final ClientGameTestContext context, final Vec3 pos) {
        server(player -> { player.setDeltaMovement(Vec3.ZERO); player.teleportTo(pos.x, pos.y, pos.z); });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
    }
    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> outcome, final String message) {
        for (int tick = 0; tick < 60 && !value(outcome::test); tick++) context.waitTicks(1);
        check(value(outcome::test), message + "; current reserves=" + value(this::reserves));
        world.getConnection().waitForClientboundPackets();
    }
    private static boolean awaitingTeleport(final ServerPlayer player) {
        try {
            final var field = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");
            field.setAccessible(true);
            return field.get(player.connection) != null;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot observe native teleport acknowledgement", failure);
        }
    }
    private static LivingEntity living(final ServerPlayer player, final UUID id) {
        final Entity entity = player.level().getEntity(id);
        if (!(entity instanceof LivingEntity living)) throw new AssertionError("Live fixture target missing: " + id);
        return living;
    }
    private static long barriers(final ServerPlayer player) {
        return BlockPos.betweenClosedStream(-5, 100, -5, 6, 104, 6).filter(pos -> player.level().getBlockState(pos).is(Blocks.BARRIER)).count();
    }
    private Map<String, Integer> reserves(final ServerPlayer player) {
        final Map<String, Integer> result = new LinkedHashMap<>();
        MagicPathState.active(player).forEach(path -> result.put(path.id(), MagicPathState.reserve(player, path))); return result;
    }
    private static MagicPath pathFor(final String id) {
        if (id.equals("presentation")) return MagicPath.LIGHT;
        if (id.equals("unattuned") || id.equals("cycle_paths")) return null;
        if (id.equals("empty_reserve")) return MagicPath.LIGHT;
        return MagicPath.require(id.substring(0, id.indexOf('_')));
    }
    private void presentation(final ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> client.player.setXRot(15));
        waitForHud(context, 120);
        screenshot(context, "focus-first-person-full-reserve");
        context.getInput().pressKey(GLFW.GLFW_KEY_2);
        context.waitFor(client -> client.player.getMainHandItem().isEmpty());
        context.waitTicks(3);
        check(!manaVisible(context), "Mana bar disappears when the Focus is put away");
        screenshot(context, "focus-put-away-no-mana-bar");
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.waitFor(client -> client.player.getMainHandItem().is(ModItems.ALL.get("arcane_focus").get()));
        context.getInput().pressKey(GLFW.GLFW_KEY_F);
        context.waitFor(client -> client.player.getOffhandItem().is(ModItems.ALL.get("arcane_focus").get()));
        check(manaVisible(context), "Holding the Focus in the off hand also shows the mana bar");
        screenshot(context, "focus-off-hand-mana-bar");
        context.getInput().pressKey(GLFW.GLFW_KEY_F);
        context.waitFor(client -> client.player.getMainHandItem().is(ModItems.ALL.get("arcane_focus").get()));
        paid(context, ActionKind.SELF, () -> air(context, false, -65), player -> player.hasEffect(MobEffects.INVISIBILITY),
            "Normal casting still spends reserve and applies its power");
        final String overlay = context.computeOnClient(client -> {
            final Object text = field(client.gui.hud, "overlayMessageString");
            return text instanceof Component component ? component.getString() : "";
        });
        check(overlay.isBlank(), "Successful casting shows no confirmation overlay");
        server(player -> {
            player.removeEffect(MobEffects.INVISIBILITY);
            check(MagicPathState.spend(player, MagicPath.LIGHT, 52), "Stage half reserve for visual comparison");
        });
        context.runOnClient(client -> client.player.setXRot(15));
        waitForHud(context, 60);
        screenshot(context, "focus-first-person-half-reserve-quiet");
        server(player -> check(MagicPathState.spend(player, MagicPath.LIGHT, 60), "Stage empty reserve for visual comparison"));
        waitForHud(context, 0);
        screenshot(context, "focus-first-person-empty-reserve");
        server(player -> {
            MagicPathState.grantPermanent(player, MagicPath.IMP);
            MagicPathState.grantPermanent(player, MagicPath.LIGHT);
        });
        air(context, true, -65);
        await(context, player -> MagicPathState.selected(player).orElseThrow() == MagicPath.IMP, "Native crouch-use changes the selected path");
        context.waitFor(client -> {
            final Object text = field(client.gui.hud, "overlayMessageString");
            return text instanceof Component component && component.getString().equals(Component.translatable("magic_path.warlockery.imp").getString());
        });
        context.runOnClient(client -> client.player.setXRot(15));
        screenshot(context, "focus-switch-name-only");
        context.getInput().pressKey(GLFW.GLFW_KEY_E);
        context.waitForScreen(net.minecraft.client.gui.screens.inventory.InventoryScreen.class);
        screenshot(context, "focus-inventory-model");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
        context.getInput().pressKey(GLFW.GLFW_KEY_F5);
        context.getInput().pressKey(GLFW.GLFW_KEY_F5);
        context.runOnClient(client -> client.player.setXRot(0));
        context.waitTicks(4);
        screenshot(context, "focus-third-person-front");
        context.getInput().pressKey(GLFW.GLFW_KEY_F5);
        note("Native cast has no success overlay; switching shows only the path name. Full/half/empty HUD and first-person/inventory/third-person Focus model captured. Half and empty resources are staged presentation inputs, not claimed gameplay recharge.");
    }
    private void waitForHud(final ClientGameTestContext context, final int resource) {
        context.waitFor(client -> {
            try {
                final var field = SupernaturalStatusOverlay.class.getDeclaredField("snapshot");
                field.setAccessible(true);
                return ((com.kadamitas.warlockery.network.ModNetwork.SupernaturalSnapshot) field.get(null)).magicResource() == resource;
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        context.waitTicks(2);
    }
    private boolean manaVisible(final ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            try {
                final var method = SupernaturalStatusOverlay.class.getDeclaredMethod("meters", net.minecraft.client.Minecraft.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(null, client)).stream().map(PlayerResourceHudModel.Meter.class::cast)
                    .anyMatch(meter -> meter.kind() == PlayerResourceHudModel.Kind.MANA);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }
    private void note(final String message) { cases.get(activeCase).put("evidence", message); }
    private void readBook(final ClientGameTestContext context, final String id) throws Exception {
        final ManualProfile profile = ManualProfile.profiles().stream()
            .filter(book -> book.sections().contains(id)).findFirst().orElseThrow();
        server(player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(profile.id()).get()));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        await(context, player -> player.connection.hasClientLoaded() && !awaitingTeleport(player),
            "Connection is ready before opening the infusion guide");
        context.waitFor(client -> client.player.getMainHandItem().is(ModItems.ALL.get(profile.id()).get()));
        context.runOnClient(client -> client.player.setXRot(-70));
        context.waitTicks(6);
        guides.put(id + "_opening", context.computeOnClient(client -> Map.of("book", profile.id(),
            "held_item_class", client.player.getMainHandItem().getItem().getClass().getName(),
            "hit", String.valueOf(client.hitResult), "screen", String.valueOf(client.gui.screen()))));
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try {
            context.waitForScreen(ManualScreen.class);
        } catch (Throwable failure) {
            screenshot(context, id + "-opening-failure");
            throw failure;
        }
        try {
            ManualClientAcceptance.selectSection(context, id);
        } catch (Throwable failure) {
            screenshot(context, id + "-selection-failure");
            throw failure;
        }
        final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, id).body().getString());
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(),
                    ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), id)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        check(!body.isBlank() && pages > 0, "Focus guide contains instructions");
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> id.equals(field(client.gui.screen(), "selectedSection"))
                && expected == (int) field(client.gui.screen(), "bodyPage")), "Native buttons visit each Focus instruction page");
            screenshot(context, id + "-book-" + page);
        }
        guides.put(id, Map.of("book", profile.id(), "body", body, "pages_read", pages));
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
    }
    private void infernalPower(final ClientGameTestContext context, final InfernalPower power) throws Exception {
        final EntityType<?> species = switch (power) {
            case EXPLOSION -> EntityTypes.CREEPER;
            case PROJECTILE -> EntityTypes.SKELETON;
            case WEB -> EntityTypes.SPIDER;
            case FIRE -> EntityTypes.BLAZE;
            case SPEED -> EntityTypes.HORSE;
            case HEALING -> EntityTypes.COW;
            case TELEPORT -> EntityTypes.ENDERMAN;
            case LEAPING -> EntityTypes.RABBIT;
            case FLIGHT -> EntityTypes.BAT;
            case AQUATIC -> EntityTypes.SQUID;
            case UNDEAD -> EntityTypes.ZOMBIE;
        };
        final UUID sacrifice = spawn(species, TARGET);
        enthrall(context, sacrifice);
        final int before = value(player -> MagicPathState.reserve(player, MagicPath.INFERNAL));
        entity(context, sacrifice, true);
        await(context, player -> player.level().getEntity(sacrifice) == null && MagicPathState.lastPower(player) == power,
            "Native sacrifice acquires " + power.id());
        check(value(player -> MagicPathState.reserve(player, MagicPath.INFERNAL))
            == Math.min(MagicPath.INFERNAL.maximumReserve(), before + 24) - MagicPathProfile.forPath(MagicPath.INFERNAL).targetCost(),
            "Sacrifice pays its cost after replenishing reserve");
        cases.get(activeCase).put("sacrifice", Map.of("species", species.toString(), "power", power.id()));
        context.waitTicks(22);
        switch (power) {
            case EXPLOSION, PROJECTILE, FIRE -> {
                final UUID victim = spawn(EntityTypes.COW, new Vec3(0.5, 100, power == InfernalPower.EXPLOSION ? 4 : 7.5));
                final float health = value(player -> living(player, victim).getHealth());
                paid(context, ActionKind.SELF, () -> air(context, false, power == InfernalPower.EXPLOSION ? 0 : 6), player -> {
                    final Entity entity = player.level().getEntity(victim);
                    return entity == null || entity instanceof LivingEntity living && living.getHealth() < health;
                }, "Native " + power.id() + " deals actual damage to a creature beyond Focus interaction reach");
            }
            case WEB -> paid(context, ActionKind.SELF, () -> air(context, false, 6),
                player -> BlockPos.betweenClosedStream(-2, 99, -2, 2, 104, 18)
                    .anyMatch(pos -> player.level().getBlockState(pos).is(Blocks.COBWEB)), "Native web power places a real cobweb along the aim");
            case SPEED -> paid(context, ActionKind.SELF, () -> air(context, false, -45),
                player -> player.getEffect(MobEffects.SPEED) != null && player.getEffect(MobEffects.SPEED).getAmplifier() == 3,
                "Native horse power grants Speed IV above its passive Speed II");
            case TELEPORT -> paid(context, ActionKind.SELF, () -> air(context, false, -45),
                player -> player.position().distanceToSqr(START) > 1 && player.getY() >= 99.9,
                "Native Enderman power relocates the actual player onto supported ground");
            case LEAPING -> {
                paid(context, ActionKind.SELF, () -> air(context, false, -45),
                    player -> player.getEffect(MobEffects.JUMP_BOOST) != null && player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() == 2,
                    "Native rabbit power grants Jump Boost III above its passive Jump Boost I");
                context.getInput().holdKeyFor(GLFW.GLFW_KEY_SPACE, 2);
                double peak = value(ServerPlayer::getY);
                for (int tick = 0; tick < 60; tick++) {
                    context.waitTicks(1);
                    peak = Math.max(peak, value(ServerPlayer::getY));
                }
                cases.get(activeCase).put("jump", Map.of("peak_y", peak, "final_y", value(ServerPlayer::getY),
                    "client_boost", context.computeOnClient(client -> client.player.getJumpBoostPower()),
                    "server_boost", value(ServerPlayer::getJumpBoostPower),
                    "key_down", context.computeOnClient(client -> client.options.keyJump.isDown())));
                check(peak > 103, "Actual jump rises over three blocks with Rabbit power");
            }
            case FLIGHT -> paid(context, ActionKind.SELF, () -> air(context, false, -45),
                player -> player.hasEffect(MobEffects.NIGHT_VISION) && player.hasEffect(MobEffects.SLOW_FALLING),
                "Native bat power grants Night Vision alongside its passive Slow Falling");
            case AQUATIC -> {
                final UUID victim = spawn(EntityTypes.COW, new Vec3(0.5, 100, 7.5));
                paid(context, ActionKind.SELF, () -> air(context, false, 6),
                    player -> living(player, victim).hasEffect(MobEffects.BLINDNESS)
                        && player.hasEffect(MobEffects.WATER_BREATHING) && player.hasEffect(MobEffects.DOLPHINS_GRACE),
                    "Native squid power blinds the aimed creature while its aquatic passives remain active");
            }
            case UNDEAD -> paid(context, ActionKind.SELF, () -> air(context, false, -45),
                player -> player.getEffect(MobEffects.RESISTANCE) != null && player.getEffect(MobEffects.RESISTANCE).getAmplifier() == 1
                    && player.getEffect(MobEffects.STRENGTH) != null && player.getEffect(MobEffects.STRENGTH).getAmplifier() == 1,
                "Native zombie power grants Resistance II and Strength II");
            case HEALING -> throw new AssertionError("Healing has its own injured-player native scenario");
        }
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
    private void server(final Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer()));
    }
    private <T> T value(final Function<ServerPlayer, T> action) {
        final AtomicReference<T> result = new AtomicReference<>(); server(player -> result.set(action.apply(player))); return result.get();
    }
    private void writeReport() throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("item", "warlockery:arcane_focus");
        report.put("available_modes", Arrays.stream(MagicPath.values()).map(MagicPath::id).toList());
        report.put("guides_read_in_game", guides);
        report.put("fixture", "A rendered Survival client invokes native mouse/key item use. Platform, individual infusion/full reserve, target entities/equipment, water/ore and distant ingot are staged prerequisites. Reset clears prior path and construct state. No dispatch function, outcome effect, ownership, recall point, command path or output is injected. Acquisition rites and Imp pact acquisition are not claimed.");
        report.put("cases", cases); report.put("screenshots", screenshots); report.put("failures", failures);
        report.put("passed", failures.isEmpty() && cases.values().stream().anyMatch(row -> row.get("status").equals("PASSED")));
        report.put("all_cases_passed", failures.isEmpty() && cases.values().stream().allMatch(row -> row.get("status").equals("PASSED")));
        report.put("remaining_scope", List.of("Infernal passive climbing, lightning recharge, fire/water protection and full effect expiry", "Otherwhere paired-player recall and cross-dimension travel", "Grave specialized Corpse directives and nourishing kills", "Other automatic path effects, reserve recharge, death persistence and multiplayer networking", "Survival acquisition of paths and Arcane Focus recipe"));
        Files.writeString(evidence.resolve("arcane-focus-abilities.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
