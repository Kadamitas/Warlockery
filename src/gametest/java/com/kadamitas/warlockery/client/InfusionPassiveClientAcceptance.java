package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Stages prerequisites; only native input and ordinary server ticks trigger passive outcomes. */
public final class InfusionPassiveClientAcceptance implements FabricClientGameTest {
    private static final Vec3 START = new Vec3(.5, 100, -1.5);
    private static final List<String> CASES = List.of("light_retaliation", "imp_contract_proximity", "earth_fall", "earth_crouched_fall", "grave_nourishment");
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;
    private String active;

    @Override public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("infusion-passives").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (String id : CASES) results.put(id, new LinkedHashMap<>(Map.of("status", "NOT_RUN")));
            for (String id : CASES) {
                active = id;
                row().put("status", "RUNNING");
                row().put("excluded", List.of("survival infusion acquisition", "save/reload and cross-dimension persistence",
                    "zero-reserve refusal", "other damage types", "thrall-attributed Grave kills", "Imp contracts beyond binding"));
                write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context);
                        readBook(context, guideFor(id));
                        switch (id) {
                            case "light_retaliation" -> light(context);
                            case "imp_contract_proximity" -> imp(context);
                            case "earth_fall" -> earth(context, false);
                            case "earth_crouched_fall" -> earth(context, true);
                            case "grave_nourishment" -> grave(context);
                            default -> throw new AssertionError(id);
                        }
                        screenshot(context, id + "-outcome");
                        row().put("status", "PASSED");
                    } catch (Throwable failure) {
                        row().put("status", "FAILED"); row().put("failure", failure.toString()); failures.add(id + ": " + failure);
                        try { screenshot(context, id + "-failure"); } catch (Exception ignored) { }
                    } finally {
                        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
                        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
                        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
                        write(false);
                    }
                } finally { world = null; }
            }
            write(true);
            check(failures.isEmpty(), String.join("; ", failures));
            System.out.println("WARLOCKERY_INFUSION_PASSIVES_PASSED " + evidence);
        } catch (Exception failure) { throw new AssertionError("Passive evidence: " + evidence, failure); }
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
            final var clock = player.level().dimensionType().defaultClock().orElseThrow();
            player.level().clockManager().setTotalTicks(clock, 18000L);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-6, 98, -6), new BlockPos(6, 110, 55)))
                player.level().setBlockAndUpdate(pos, pos.getY() == 98 ? Blocks.BEDROCK.defaultBlockState()
                    : pos.getY() == 99 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        });
        position(context, START);
        row().put("fixture", "Fresh Survival world; staged flat stone over bedrock, night, no natural regeneration, supplied infusion except native Imp contract. Live attacks, native punches and walking falls trigger all measured outcomes.");
    }

    private void light(final ClientGameTestContext context) {
        supply(context, ItemStack.EMPTY);
        final float baseline = receiveZombieHit(context);
        position(context, START);
        server(player -> { player.setHealth(20); player.damageCooldownTime = 0; MagicPathState.grantPermanent(player, MagicPath.LIGHT); });
        final int before = reserve(MagicPath.LIGHT);
        final float infused = receiveZombieHit(context);
        check(baseline > 0 && Math.abs(infused - baseline * .6F) < .02F, "Light reduces the same ordinary zombie hit to sixty percent");
        check(reserve(MagicPath.LIGHT) == before - 4, "One retaliating hit spends exactly four Light reserve");
        row().put("control_damage", baseline); row().put("infused_damage", infused); row().put("reserve_before", before); row().put("reserve_after", reserve(MagicPath.LIGHT));
    }

    private float receiveZombieHit(final ClientGameTestContext context) {
        final boolean infused = value(player -> MagicPathState.has(player, MagicPath.LIGHT));
        final Mob zombie = value(player -> {
            final Mob spawned = mob(player, "minecraft:zombie", new Vec3(.5, 100, -.4));
            spawned.setNoAi(false); spawned.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            spawned.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3); spawned.setTarget(player);
            return spawned;
        });
        final float before = value(ServerPlayer::getHealth);
        await(context, player -> player.getHealth() < before, 100, "A normal zombie AI attack reaches the player");
        final float damage = value(player -> {
            zombie.setNoAi(true);
            check(player.getLastHurtByMob() == zombie, "Measured damage belongs to the actual zombie attack");
            if (infused) {
                check(zombie.hasEffect(MobEffects.GLOWING), "Attacker glows after retaliation");
                check(zombie.hasEffect(MobEffects.SLOWNESS) && zombie.getEffect(MobEffects.SLOWNESS).getAmplifier() == 3,
                    "Attacker receives Slowness IV from the actual hit");
            }
            return before - player.getHealth();
        });
        aim(context, zombie);
        try { screenshot(context, active + (infused ? "-retaliating-hit" : "-control-hit")); } catch (Exception failure) { throw new AssertionError(failure); }
        server(player -> zombie.discard());
        return damage;
    }

    private void imp(final ClientGameTestContext context) {
        final Mob imp = value(player -> {
            player.experienceLevel = 25;
            final Mob spawned = mob(player, "warlockery:imp", new Vec3(-1.2, 100, .4));
            check(CreatureBehaviorState.owner(spawned).isEmpty(), "Imp begins unowned"); return spawned;
        });
        check(!value(player -> MagicPathState.has(player, MagicPath.IMP)), "No Imp infusion is injected before binding");
        supply(context, new ItemStack(ModItems.ALL.get("ingredient_contract").get()));
        aim(context, imp); context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        await(context, player -> CreatureBehaviorState.isOwnedBy(imp, player.getUUID()), 40, "Native contract binds the imp");
        check(value(player -> player.experienceLevel == 0 && player.getMainHandItem().isEmpty()), "Binding consumes exactly twenty-five levels and one contract");
        await(context, player -> MagicPathState.has(player, MagicPath.IMP), 40, "Owned imp proximity grants its timed infusion naturally");
        final Mob victim = value(player -> mob(player, "minecraft:cow", new Vec3(.5, 100, .5)));
        supply(context, ItemStack.EMPTY); final int before = reserve(MagicPath.IMP); final float health = value(player -> victim.getHealth());
        aim(context, victim); context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        await(context, player -> victim.getHealth() < health && victim.isOnFire(), 30, "Native bare-hand hit ignites the creature");
        check(reserve(MagicPath.IMP) == before - 2, "Native punch pays exactly two Imp reserve");
        row().put("punch_reserve_before", before); row().put("punch_reserve_after", reserve(MagicPath.IMP));
        try { screenshot(context, active + "-native-burning-victim"); } catch (Exception failure) { throw new AssertionError(failure); }
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); });
        // The supplied imp has NoAI, so this ordinary walk really leaves its proximity volume.
        context.getInput().holdKeyFor(com.mojang.blaze3d.platform.InputConstants.KEY_W, 220);
        check(value(player -> player.getZ() - imp.getZ() > 33), "Native walking leaves the bound imp's thirty-two-block area");
        row().put("distance_after_walk", value(player -> player.distanceTo(imp)));
        await(context, player -> !MagicPathState.has(player, MagicPath.IMP), 85, "Timed Imp infusion expires after leaving its owner-bound imp");
        row().put("timed_infusion_expired", true);
    }

    private void earth(final ClientGameTestContext context, final boolean crouched) {
        server(player -> {
            MagicPathState.grantPermanent(player, MagicPath.OVERWORLD);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-1, 107, -3), new BlockPos(1, 107, -1)))
                player.level().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        });
        position(context, new Vec3(.5, 108, -.5));
        final Mob cow = value(player -> mob(player, "minecraft:cow", new Vec3(1.5, 100, 1.2)));
        final float cowBefore = value(player -> cow.getHealth()); final float before = value(ServerPlayer::getHealth);
        final int mana = reserve(MagicPath.OVERWORLD);
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(20); });
        context.getInput().holdKeyFor(com.mojang.blaze3d.platform.InputConstants.KEY_W, 8);
        await(context, player -> !player.onGround() && player.getY() < 107.8, 30, "Native walking leaves the staged eight-block ledge");
        // Begin crouching only after leaving the ledge, since edge-sneaking prevents a fall.
        if (crouched) context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT);
        try {
            await(context, player -> reserveOn(player, MagicPath.OVERWORLD) < mana, 65, "A genuinely damaging landing activates Earth cushioning");
            check(reserve(MagicPath.OVERWORLD) == mana - 4, "Exactly one landing spends four Earth reserve");
            check(value(player -> player.getHealth() == before), "Earth cancels actual fall damage");
            final long broken = value(player -> BlockPos.betweenClosedStream(new BlockPos(-3, 99, -2), new BlockPos(3, 99, 6))
                .filter(pos -> player.level().getBlockState(pos).isAir()).count());
            check(broken == 1, "Landing breaks precisely one formerly solid stone block above the bedrock catch floor");
            if (crouched) await(context, player -> cow.getHealth() < cowBefore, 20, "Native crouched landing explosion hurts the nearby control cow");
            else check(value(player -> cow.getHealth() == cowBefore), "Ordinary Earth landing does not damage the nearby cow");
            row().put("stone_blocks_broken", broken); row().put("health_before", before); row().put("health_after", value(ServerPlayer::getHealth));
            row().put("cow_health_before", cowBefore); row().put("cow_health_after", value(player -> cow.getHealth()));
            row().put("reserve_before", mana); row().put("reserve_after", reserve(MagicPath.OVERWORLD));
        } finally { if (crouched) context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT); }
    }

    private void grave(final ClientGameTestContext context) {
        server(player -> {
            MagicPathState.grantPermanent(player, MagicPath.GRAVE); MagicPathState.spend(player, MagicPath.GRAVE, 20);
            player.setHealth(10); player.getFoodData().setFoodLevel(8);
        });
        final Mob cow = value(player -> { final Mob spawned = mob(player, "minecraft:cow", new Vec3(.5, 100, .5)); spawned.setHealth(1); return spawned; });
        supply(context, new ItemStack(Items.DIAMOND_SWORD));
        final int mana = reserve(MagicPath.GRAVE); final int food = value(player -> player.getFoodData().getFoodLevel());
        final float health = value(ServerPlayer::getHealth);
        aim(context, cow); context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        await(context, player -> !cow.isAlive(), 30, "Native sword attack kills the supplied living cow");
        check(value(player -> cow.getLastHurtByMob() == player), "The nourishing kill is attributed to the real player");
        check(value(player -> player.getHealth() == health + 4 && player.getFoodData().getFoodLevel() == food + 4), "Actual nourishing kill restores four health and four food");
        check(reserve(MagicPath.GRAVE) == mana + 4, "Actual nourishing kill restores exactly four Grave reserve");
        row().put("health_before", health); row().put("health_after", value(ServerPlayer::getHealth));
        row().put("food_before", food); row().put("food_after", value(player -> player.getFoodData().getFoodLevel()));
        row().put("reserve_before", mana); row().put("reserve_after", reserve(MagicPath.GRAVE));
    }

    private static Mob mob(final ServerPlayer player, final String id, final Vec3 point) {
        final Mob mob = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)).create(player.level(), EntitySpawnReason.COMMAND);
        check(mob != null, "Staged mob creates"); mob.setNoAi(true); mob.setPersistenceRequired();
        mob.snapTo(point.x, point.y, point.z, 0, 0); check(player.level().addFreshEntity(mob), "Staged mob enters world"); return mob;
    }
    private void supply(final ClientGameTestContext context, final ItemStack stack) {
        server(player -> { player.getInventory().setItem(0, stack); player.getInventory().setSelectedSlot(0); player.containerMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1); context.waitTicks(3);
    }
    private void aim(final ClientGameTestContext context, final Mob mob) {
        final Vec3 target = value(player -> mob.position().add(0, mob.getBbHeight() * .65, 0));
        context.runOnClient(client -> {
            final Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        }); context.waitTicks(3);
        check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(mob.getUUID())), "Native pointer targets the intended living creature");
    }
    private void position(final ClientGameTestContext context, final Vec3 point) {
        server(player -> { player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance(); player.teleportTo(point.x, point.y, point.z); });
        world.getConnection().waitForChunksRender(); world.getConnection().waitForClientboundPackets();
        await(context, player -> player.connection.hasClientLoaded() && !awaitingTeleport(player) && player.position().distanceTo(point) < .15, 100, "Server acknowledges staged position before input");
        context.waitFor(client -> client.player != null && client.player.position().distanceTo(point) < .2);
        context.waitTicks(3);
    }
    private static boolean awaitingTeleport(final ServerPlayer player) {
        try { final var field = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient"); field.setAccessible(true); return field.get(player.connection) != null; }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static String guideFor(final String id) {
        return switch (id) {
            case "light_retaliation" -> "infusion_passives";
            case "imp_contract_proximity" -> "imp_attunement";
            case "earth_fall", "earth_crouched_fall" -> "infusion_passives";
            default -> "ingredient_brew_grave";
        };
    }
    private void readBook(final ClientGameTestContext context, final String id) throws Exception {
        final ManualProfile profile = ManualProfile.profiles().stream().filter(book -> book.sections().contains(id)).findFirst().orElseThrow();
        supply(context, new ItemStack(ModItems.ALL.get(profile.id()).get()));
        context.runOnClient(client -> client.player.setXRot(-70)); context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT); context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, id);
        final String body = context.computeOnClient(client -> ManualArticleCatalog.article(profile, id).body().getString());
        final int pages = context.computeOnClient(client -> {
            try { final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class); method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), id)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        check(!body.isBlank() && pages > 0, "Indexed guide has readable instructions");
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expectedPage = page;
            check(context.computeOnClient(client -> {
                try {
                    final var section = ManualScreen.class.getDeclaredField("selectedSection"); section.setAccessible(true);
                    final var currentPage = ManualScreen.class.getDeclaredField("bodyPage"); currentPage.setAccessible(true);
                    return id.equals(section.get(client.gui.screen())) && currentPage.getInt(client.gui.screen()) == expectedPage;
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            }), "Native book controls show the intended guide page");
            screenshot(context, active + "-guide-" + page);
        }
        row().put("guide", Map.of("section", id, "book", profile.id(), "body", body, "pages_read", pages));
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
    }
    private Map<String, Object> row() { return results.get(active); }
    private int reserve(final MagicPath path) { return value(player -> reserveOn(player, path)); }
    private static int reserveOn(final ServerPlayer player, final MagicPath path) { return MagicPathState.reserve(player, path); }
    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T value(final Function<ServerPlayer, T> action) { return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer())); }
    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> condition, final int ticks, final String message) {
        for (int tick = 0; tick < ticks && !value(condition::test); tick++) context.waitTicks(1);
        check(value(condition::test), message); world.getConnection().waitForClientboundPackets();
    }
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots); }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
    private void write(final boolean complete) throws Exception {
        final Map<String, Object> report = new LinkedHashMap<>(); report.put("completed", complete); report.put("scenarios", results);
        report.put("all_selected_scenarios_passed", complete && failures.isEmpty()); report.put("all_passives_complete", false);
        report.put("screenshots", screenshots); report.put("failures", failures);
        final var gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(evidence.resolve("infusion-passives.json"), gson.toJson(report));
        for (var entry : results.entrySet()) Files.writeString(evidence.resolve(entry.getKey() + ".json"), gson.toJson(entry.getValue()));
    }
}
