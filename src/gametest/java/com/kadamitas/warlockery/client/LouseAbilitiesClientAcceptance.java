package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.ParasyticLouseItem;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class LouseAbilitiesClientAcceptance implements FabricClientGameTest {
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> checks = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("louse-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            report.put("fixture", "Fresh Survival worlds with a book, empty louse, vanilla potions, stone floor and cactus staged. "
                + "A normally ticking adult zombie with helmet and a target supplies the harmful-effect attack. "
                + "Book navigation, louse loading and walking into the cactus use native input. "
                + "No loaded louse, damage, potion injection or attack outcome is staged.");
            for (final boolean harmful : new boolean[] {false, true}) {
                final String scenario = harmful ? "harmful_attacker" : "beneficial_wearer";
                final Map<String, Object> row = new LinkedHashMap<>();
                report.put(scenario, row);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context);
                        readGuide(context, scenario, row);
                        load(context, harmful, row);
                        context.getInput().pressKey(GLFW.GLFW_KEY_2);
                        context.waitTicks(25);
                        check(serverValue(player -> player.getHealth() == player.getMaxHealth()
                            && player.getActiveEffects().isEmpty() && loaded(player)),
                            "A loaded louse in an unselected hotbar slot waits for injury");
                        cactus(context, row);
                        if (harmful) {
                            check(serverValue(player -> loaded(player) && !player.hasEffect(MobEffects.SLOWNESS)),
                                "Environmental damage retains a harmful charge without dosing the wearer");
                            attacker(context, row);
                        } else {
                            check(serverValue(player -> player.hasEffect(MobEffects.SPEED) && !loaded(player)),
                                "Normal cactus injury injects the beneficial potion into its wearer and empties the charge");
                        }
                        check(serverValue(player -> player.getInventory().getItem(0).is(ModItems.ALL.get("louse").get())
                            && player.getInventory().getItem(0).getCount() == 1), "Injection preserves the reusable louse item");
                        row.put("final", serverValue(LouseAbilitiesClientAcceptance::playerState));
                        screenshot(context, scenario + "-injected");
                        row.put("status", "PASSED");
                        checks.add(scenario + ": real potion loading, second-potion refusal, ordinary damage routing and reusable item verified");
                    } catch (Throwable failure) {
                        row.put("status", "FAILED"); row.put("failure", failure.toString());
                        row.put("last_player", serverValue(LouseAbilitiesClientAcceptance::playerState));
                        try { screenshot(context, scenario + "-failure"); } catch (Throwable capture) { failure.addSuppressed(capture); }
                        throw failure;
                    } finally {
                        context.getInput().releaseKey(GLFW.GLFW_KEY_W);
                        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                        write(false);
                    }
                }
            }
            write(true);
            System.out.println("WARLOCKERY_LOUSE_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            report.put("failure", failure.toString());
            try { write(false); } catch (Throwable capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Louse native evidence: " + evidence, failure);
        }
    }

    private void stage(final ClientGameTestContext context) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            server.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.removeAllEffects();
            player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20);
            for (final BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 99, -8), new BlockPos(8, 106, 8)))
                player.level().setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.teleportTo(.5, 100, -2.5); player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForChunksRender();
        world.getConnection().waitForClientboundPackets();
    }

    private void readGuide(final ClientGameTestContext context, final String scenario, final Map<String, Object> row) throws Exception {
        final String section = "louse";
        final ManualProfile profile = ManualProfile.profiles().stream().filter(value -> value.sections().contains(section)).findFirst().orElseThrow();
        server(player -> { player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get(profile.id()).get())); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        useAir(context);
        context.waitForScreen(ManualScreen.class);
        ManualClientAcceptance.selectSection(context, section);
        row.put("guide_text", context.computeOnClient(client -> ManualArticleCatalog.article(profile, section).body().getString()));
        final int pages = context.computeOnClient(client -> {
            try {
                final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                method.setAccessible(true);
                return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        check(pages > 0, "The actual Parasytic Louse guide has readable pages");
        for (int page = 0; page < pages; page++) {
            if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
            final int expected = page;
            check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection"))
                && (int) field(client.gui.screen(), "bodyPage") == expected), "Native book buttons visit each louse instruction page");
            screenshot(context, scenario + "-guide-" + (page + 1));
        }
        row.put("guide_pages", pages);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
    }

    private void load(final ClientGameTestContext context, final boolean harmful, final Map<String, Object> row) {
        final ItemStack potion = PotionContents.createItemStack(Items.POTION, harmful ? Potions.SLOWNESS : Potions.SWIFTNESS);
        final var expected = potion.get(DataComponents.POTION_CONTENTS).getAllEffects().iterator().next();
        final String effectId = BuiltInRegistries.MOB_EFFECT.getKey(expected.getEffect().value()).toString();
        server(player -> {
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(ModItems.ALL.get("louse").get()));
            player.setItemSlot(EquipmentSlot.OFFHAND, potion.copy()); player.inventoryMenu.broadcastChanges();
        });
        world.getConnection().waitForClientboundPackets();
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        check(serverValue(player -> !loaded(player)), "The supplied louse starts empty");
        useAir(context);
        await(context, LouseAbilitiesClientAcceptance::loaded, 30, "Native air use loads the opposite-hand potion into the louse");
        check(serverValue(player -> {
            final var data = player.getInventory().getItem(0).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            return effectId.equals(data.getStringOr("WarlockeryLouseEffect", ""))
                && data.getIntOr("WarlockeryLouseDuration", -1) == expected.getDuration()
                && data.getIntOr("WarlockeryLouseAmplifier", -1) == expected.getAmplifier()
                && player.getOffhandItem().isEmpty() && bottles(player) == 1 && player.getActiveEffects().isEmpty();
        }), "Loading preserves exact potion effect, strength and duration, consumes one potion and returns one bottle without drinking it");
        row.put("loaded", serverValue(LouseAbilitiesClientAcceptance::playerState));
        server(player -> { player.setItemSlot(EquipmentSlot.OFFHAND, potion.copy()); player.inventoryMenu.broadcastChanges(); });
        world.getConnection().waitForClientboundPackets();
        useAir(context);
        final String refused = context.computeOnClient(client -> Component.translatable("message.warlockery.louse.already_loaded").getString());
        context.waitFor(client -> field(client.gui.hud, "overlayMessageString") instanceof Component message
            && message.getString().equals(refused), 40);
        check(serverValue(player -> loaded(player) && player.getOffhandItem().is(Items.POTION)
            && player.getOffhandItem().getCount() == 1 && bottles(player) == 1 && player.getActiveEffects().isEmpty()),
            "An already loaded louse refuses the second potion without consuming or drinking it");
    }

    private void cactus(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> {
            player.level().setBlockAndUpdate(new BlockPos(0, 98, -4), Blocks.STONE.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(0, 99, -4), Blocks.SAND.defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(0, 100, -4), Blocks.CACTUS.defaultBlockState());
            player.teleportTo(.5, 100, -5.1); player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); }); context.waitTicks(2);
        final float before = serverValue(ServerPlayer::getHealth);
        context.getInput().holdKey(GLFW.GLFW_KEY_W);
        try { await(context, player -> player.getHealth() < before, 50, "Native forward movement causes ordinary cactus damage"); }
        finally { context.getInput().releaseKey(GLFW.GLFW_KEY_W); }
        row.put("after_environmental_damage", serverValue(LouseAbilitiesClientAcceptance::playerState));
        server(player -> { player.level().setBlockAndUpdate(new BlockPos(0, 100, -4), Blocks.AIR.defaultBlockState()); player.setDeltaMovement(Vec3.ZERO); });
    }

    private void attacker(final ClientGameTestContext context, final Map<String, Object> row) {
        server(player -> { player.teleportTo(.5, 100, .5); player.setDeltaMovement(Vec3.ZERO); });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(25);
        final float before = serverValue(ServerPlayer::getHealth);
        final UUID attackerId = serverValue(player -> {
            final var created = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:zombie"))
                .create(player.level(), EntitySpawnReason.COMMAND);
            check(created instanceof Mob, "The staged attacker is a live vanilla mob");
            final Mob mob = (Mob) created;
            mob.setPos(.5, 100, 2.5); mob.setPersistenceRequired();
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            if (mob instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie) zombie.setBaby(false);
            mob.setTarget(player); player.level().addFreshEntity(mob); return mob.getUUID();
        });
        row.put("attacker_uuid", attackerId.toString());
        try {
            await(context, player -> player.getHealth() < before && player.level().getEntity(attackerId) instanceof Mob mob
                && mob.hasEffect(MobEffects.SLOWNESS), 160, "A normally ticking zombie attack triggers the retained harmful charge against that attacker");
        } finally {
            row.put("attacker", serverValue(player -> {
                final var entity = player.level().getEntity(attackerId);
                if (!(entity instanceof Mob mob)) return Map.of("present", false);
                return Map.of("present", true, "position", mob.position().toString(), "health", mob.getHealth(),
                    "effects", mob.getActiveEffects().stream().map(Object::toString).toList(),
                    "target", mob.getTarget() == null ? "none" : mob.getTarget().getStringUUID());
            }));
        }
        check(serverValue(player -> !loaded(player) && !player.hasEffect(MobEffects.SLOWNESS)
            && player.getLastHurtByMob() != null && player.getLastHurtByMob().getUUID().equals(attackerId)),
            "The actual living attacker receives the harmful effect while the wearer remains undosed and the charge empties");
    }

    private static boolean loaded(final ServerPlayer player) {
        return !player.getInventory().getItem(0).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            .getStringOr("WarlockeryLouseEffect", "").isEmpty();
    }
    private static int bottles(final ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            if (player.getInventory().getItem(slot).is(Items.GLASS_BOTTLE)) count += player.getInventory().getItem(slot).getCount();
        return count;
    }
    private static Map<String, Object> playerState(final ServerPlayer player) {
        return Map.of("tick", player.level().getGameTime(), "health", player.getHealth(),
            "position", player.position().toString(), "effects", player.getActiveEffects().stream().map(Object::toString).toList(),
            "louse_data", player.getInventory().getItem(0).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().toString(),
            "offhand", player.getOffhandItem().toString(), "glass_bottles", bottles(player));
    }
    private static void useAir(final ClientGameTestContext context) {
        context.runOnClient(client -> client.player.setXRot(-75)); context.waitTicks(2);
        check(context.computeOnClient(client -> client.hitResult != null
            && client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS), "The native use ray points into clear air");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(2);
    }
    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> condition, final int ticks, final String message) {
        for (int elapsed = 0; elapsed < ticks && !serverValue(condition::test); elapsed += 2) context.waitTicks(2);
        check(serverValue(condition::test), message);
    }
    private void server(final Consumer<ServerPlayer> action) { world.getServer().runOnServer(server -> action.accept(world.getConnection().getServerPlayer())); }
    private <T> T serverValue(final Function<ServerPlayer, T> action) { return world.getServer().computeOnServer(server -> action.apply(world.getConnection().getServerPlayer())); }
    private void screenshot(final ClientGameTestContext context, final String name) throws Exception { ManualClientAcceptance.saveScreenshot(context, evidence, name, screenshots); }
    private static Object field(final Object object, final String name) {
        try { final var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static String classHash(final Class<?> type) throws Exception {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing class bytes " + type);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }
    private void write(final boolean passed) throws Exception {
        report.put("passed", passed); report.put("checks", checks); report.put("screenshots", screenshots);
        report.put("class_sha256", Map.of("test", classHash(LouseAbilitiesClientAcceptance.class), "item", classHash(ParasyticLouseItem.class)));
        Files.writeString(evidence.resolve("louse-abilities.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
