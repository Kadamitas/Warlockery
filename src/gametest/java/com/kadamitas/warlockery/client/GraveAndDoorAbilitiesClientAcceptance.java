package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.kadamitas.warlockery.block.RunedDoorBlock;
import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.entity.CorpseEntity;
import com.kadamitas.warlockery.item.InfusedBrewItem;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.item.RowanKeyState;
import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathRuntime;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class GraveAndDoorAbilitiesClientAcceptance implements FabricClientGameTest {
    private static final BlockPos FIRST_DOOR = new BlockPos(0, 100, 1);
    private static final BlockPos SECOND_DOOR = new BlockPos(4, 100, 1);
    private static final BlockPos KEY_CHEST = new BlockPos(8, 100, 0);
    private final Map<String, Map<String, Object>> results = new LinkedHashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private TestSingleplayerContext world;
    private Path evidence;

    @Override
    public void runTest(final ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence", ".codex-local/client-acceptance"))
            .resolve("grave-and-door-abilities").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            for (final String scenario : List.of("grave", "rowan_door_and_keyring")) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", "RUNNING"); results.put(scenario, row); write(false);
                try (var created = context.worldBuilder().create()) {
                    world = created;
                    try {
                        stage(context);
                        if (scenario.equals("grave")) grave(context, row); else doors(context, row);
                        row.put("status", "PASSED");
                    } catch (Throwable failure) {
                        row.put("status", "FAILED"); row.put("failure", failure.toString());
                        row.put("failure_trace", java.util.Arrays.stream(failure.getStackTrace()).map(Object::toString).toList());
                        failures.add(scenario + ": " + failure);
                        try { screenshot(context, scenario + "-failure"); } catch (Throwable ignored) { }
                    } finally {
                        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                        context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
                        write(false);
                    }
                } finally { world = null; }
            }
            write(true);
            check(failures.isEmpty(), String.join("\n", failures));
            System.out.println("WARLOCKERY_GRAVE_AND_DOOR_ABILITIES_PASSED " + evidence);
        } catch (Throwable failure) {
            try { write(false); Files.writeString(evidence.resolve("failure.txt"), failure.toString()); }
            catch (Exception capture) { failure.addSuppressed(capture); }
            throw new AssertionError("Native Grave and Rowan evidence: " + evidence, failure);
        }
    }

    private void stage(final ClientGameTestContext context) {
        world.getServer().runOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
        });
        server(player -> {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent(); player.removeAllEffects();
            player.setHealth(player.getMaxHealth()); player.setAbsorptionAmount(0);
            player.getFoodData().setFoodLevel(20);
            for (final BlockPos pos : BlockPos.betweenClosed(new BlockPos(-8, 99, -8), new BlockPos(12, 108, 8))) {
                final boolean roofOrFloor = pos.getY() == 99 || pos.getY() == 108;
                player.level().setBlockAndUpdate(pos, roofOrFloor ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            player.teleportTo(.5, 100, -1.5); player.setDeltaMovement(Vec3.ZERO);
        });
        world.getConnection().waitForChunksRender();
        world.getConnection().waitForClientboundPackets();
    }

    private void grave(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        readGuides(context, List.of("ingredient_brew_grave", "arcane_focus"), row);
        server(player -> { player.getInventory().clearContent(); player.inventoryMenu.broadcastChanges(); });
        check(serverValue(player -> !MagicPathState.has(player, MagicPath.GRAVE) && !player.hasEffect(MobEffects.NIGHT_VISION)),
            "The fresh untreated player has neither Grave attunement nor Night Vision");
        supply(context, 0, item("ingredient_brew_grave"));
        lookAir(context);
        screenshot(context, "grave-before-native-drinking");
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        try {
            await(context, player -> MagicPathState.has(player, MagicPath.GRAVE) && count(player, item("ingredient_brew_grave")) == 0,
                70, "Finishing the real drink consumes it and grants Grave attunement");
        } finally { context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
        check(serverValue(player -> MagicPathState.selected(player).orElseThrow() == MagicPath.GRAVE
            && MagicPathState.reserve(player, MagicPath.GRAVE) == 120), "The new Grave path starts selected with full reserve");
        await(context, player -> player.hasEffect(MobEffects.NIGHT_VISION), 30, "Normal path ticks provide passive Night Vision");
        check(serverValue(player -> player.getEffect(MobEffects.NIGHT_VISION).getDuration() <= 240),
            "Passive Night Vision has its short refresh duration before the active Focus power");
        supply(context, 0, item("arcane_focus"));
        useAir(context);
        await(context, player -> MagicPathState.reserve(player, MagicPath.GRAVE) == 116
            && player.hasEffect(MobEffects.NIGHT_VISION) && player.getEffect(MobEffects.NIGHT_VISION).getDuration() > 1_000,
            30, "Air use spends four reserve and grants the distinct one-minute Night Vision power");
        screenshot(context, "grave-native-air-focus");

        final UUID bodyId = spawn("warlockery:corpse", new Vec3(.5, 100, 1));
        server(player -> mob(player, bodyId).setTarget(player));
        useEntity(context, bodyId, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        await(context, player -> player.getStringUUID().equals(WarlockeryEntityData.get(mob(player, bodyId)).getStringOr("WarlockeryGraveOwner", ""))
            && mob(player, bodyId).getTarget() == null && mob(player, bodyId).isPersistenceRequired()
            && MagicPathState.reserve(player, MagicPath.GRAVE) == 106, 30,
            "Native Focus interaction binds and calms the actual undead for ten reserve");
        screenshot(context, "grave-native-bound-undead");
        final BlockPos command = new BlockPos(3, 99, 2);
        final Vec3 destination = new Vec3(3.5, 100, 2.5);
        moveFixturePlayer(context, new Vec3(3.5, 100, -.5));
        final Vec3 start = serverValue(player -> mob(player, bodyId).position());
        final int directives = serverValue(player -> ((CorpseEntity) mob(player, bodyId)).corpseCounters().graveDirectivesReceived);
        server(player -> mob(player, bodyId).setNoAi(false));
        useBlock(context, new Vec3(3.5, 99.999, 2.5), command);
        await(context, player -> MagicPathState.reserve(player, MagicPath.GRAVE) == 101
            && ((CorpseEntity) mob(player, bodyId)).corpseCounters().graveDirectivesReceived == directives + 1,
            30, "A real block click spends five reserve and delivers one native Grave directive");
        row.put("command_start", start.toString());
        row.put("command_destination", destination.toString());
        await(context, player -> mob(player, bodyId).position().distanceToSqr(start) > 1
            && mob(player, bodyId).position().distanceToSqr(destination) < start.distanceToSqr(destination) - 1,
            160, "The bound undead actually walks toward the clicked destination under its normal AI");
        row.put("command_observed_position", serverValue(player -> mob(player, bodyId).position().toString()));
        screenshot(context, "grave-native-command-movement");
        server(player -> mob(player, bodyId).setNoAi(true));

        moveFixturePlayer(context, new Vec3(-3.5, 100, -3.5));
        final UUID cowId = spawn("minecraft:cow", new Vec3(-3.5, 100, -1.5));
        useEntity(context, cowId, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        awaitOverlay(context, "message.warlockery.magic.invalid_target", Component.translatable("magic_path.warlockery.grave"));
        check(serverValue(player -> WarlockeryEntityData.get(mob(player, cowId)).getStringOr("WarlockeryGraveOwner", "").isEmpty()
            && MagicPathState.reserve(player, MagicPath.GRAVE) == 101), "An ordinary living animal refuses the bond without spending reserve");
        server(player -> {
            player.setHealth(10); player.getFoodData().setFoodLevel(10); player.getFoodData().setSaturation(0);
            mob(player, cowId).setHealth(1);
        });
        supply(context, 0, Items.DIAMOND_SWORD);
        context.waitTicks(22);
        useEntity(context, cowId, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        await(context, player -> (player.level().getEntity(cowId) == null || !mob(player, cowId).isAlive())
            && player.getHealth() == 14 && player.getFoodData().getFoodLevel() >= 13
            && MagicPathState.reserve(player, MagicPath.GRAVE) == 105, 30,
            "A native nourishing kill restores two hearts, food and four Grave reserve");
        screenshot(context, "grave-native-nourishing-kill");
        row.put("verified", List.of("native drinking and full initial reserve", "passive Night Vision", "distinct air Focus power and cost",
            "real undead binding and target clearing", "normal AI command movement and cost", "living-target refusal", "native nourishing kill"));
        row.put("fixture", "Fresh Survival world and roofed stone arena. Real Body and cow are initially held with NoAI for reliable pointer targeting; the bound Body uses normal AI and gravity for its movement trial, then is held again to isolate the separate nourishing-kill trial. Missing player health/food and a one-health cow are staged before a real sword strike. Attunement, effects, ownership, directives, movement, reserve changes and healing are never injected.");
        row.put("not_run", List.of("infusion recipe acquisition", "two-hour expiry", "save/load", "all vanilla undead", "nourishment through a follower kill"));
    }

    private void doors(final ClientGameTestContext context, final Map<String, Object> row) throws Exception {
        readGuides(context, List.of("rowanwooddoor", "ingredient_door_key", "ingredient_door_keyring"), row);
        server(player -> {
            player.getInventory().clearContent(); player.inventoryMenu.broadcastChanges();
            player.level().setBlockAndUpdate(KEY_CHEST, Blocks.CHEST.defaultBlockState());
        });
        placeDoor(context, FIRST_DOOR);
        final RowanKeyState.Door first = serverValue(player -> door(player, FIRST_DOOR));
        check(serverValue(player -> count(player, item("ingredient_door_key")) == 1 && carries(player, first)),
            "Native placement grants one key bound to this position and dimension");
        select(context, 8);
        toggleDoor(context, FIRST_DOOR.above(), true);
        toggleDoor(context, FIRST_DOOR, false);
        screenshot(context, "rowan-native-first-key-and-empty-hand-permission");

        moveFixturePlayer(context, new Vec3(4.5, 100, -1.5));
        placeDoor(context, SECOND_DOOR);
        final RowanKeyState.Door second = serverValue(player -> door(player, SECOND_DOOR));
        check(serverValue(player -> count(player, item("ingredient_door_key")) == 2 && carries(player, second)),
            "The second native placement grants a different real key");
        chestTransfer(context, second, true);
        check(serverValue(player -> !carries(player, second) && carries(player, first)), "Native chest transfer removes only the second permission");
        moveFixturePlayer(context, new Vec3(4.5, 100, -1.5));
        select(context, keySlot(first));
        useDoor(context, SECOND_DOOR);
        awaitOverlay(context, "message.warlockery.rowan_door.locked");
        check(serverValue(player -> doorClosed(player, SECOND_DOOR) && !carries(player, second)
            && RowanKeyState.read(player.getMainHandItem()).doors().equals(List.of(first))),
            "A key bound elsewhere cannot open or rebind to the second door");
        supply(context, 0, item("ingredient_door_keyring"));
        useAir(context);
        awaitOverlay(context, "message.warlockery.rowan_key.missing_bound_key");
        check(serverValue(player -> player.getOffhandItem().isEmpty()
            && player.getMainHandItem().is(item("ingredient_door_keyring"))
            && RowanKeyState.read(player.getMainHandItem()).doors().isEmpty() && carries(player, first)),
            "An empty opposite hand refuses merging and preserves the empty ring and existing key");
        useDoor(context, SECOND_DOOR.above());
        awaitOverlay(context, "message.warlockery.rowan_door.locked");
        check(serverValue(player -> doorClosed(player, SECOND_DOOR) && RowanKeyState.read(player.getMainHandItem()).doors().isEmpty()),
            "An empty keyring cannot learn an unknown door by clicking it");
        screenshot(context, "rowan-native-wrong-key-and-empty-ring-refusal");

        mergeKey(context, first, 1);
        check(serverValue(player -> count(player, item("ingredient_door_key")) == 0), "The first opposite-hand source key was consumed in Survival");
        moveFixturePlayer(context, new Vec3(.5, 100, -1.5));
        select(context, 8);
        toggleDoor(context, FIRST_DOOR.above(), true); toggleDoor(context, FIRST_DOOR, false);
        chestTransfer(context, second, false);
        mergeKey(context, second, 2);
        check(serverValue(player -> count(player, item("ingredient_door_key")) == 0
            && RowanKeyState.read(player.getInventory().getItem(0)).doors().containsAll(List.of(first, second))),
            "The second native merge consumes its key and retains the ring's first door");
        for (final BlockPos position : List.of(FIRST_DOOR, SECOND_DOOR)) {
            moveFixturePlayer(context, new Vec3(position.getX() + .5, 100, -1.5));
            select(context, 8);
            toggleDoor(context, position.above(), true); toggleDoor(context, position, false);
        }
        screenshot(context, "rowan-native-two-door-keyring");
        row.put("verified", List.of("two native placements grant two exact keys", "inventory permission with empty hand",
            "upper and lower halves remain synchronized", "wrong bound key refuses reassignment", "empty source refuses without consuming the ring",
            "empty ring refuses unknown door with a fresh native message",
            "opposite-hand native merges consume both sources", "receiving ring retains both permissions"));
        row.put("fixture", "Door and keyring items and an empty storage chest are supplied. Both doors and all bound keys are created by native placement. Native pickup and destination-slot clicks temporarily store the second key for refusal checks; native F-key hand swaps and air use perform both merges. No binding, door OPEN value or item component is injected.");
        row.put("not_run", List.of("crafting acquisition", "redstone closure", "unauthorized breaking drops", "duplicate source merge", "save/load"));
    }

    private void placeDoor(final ClientGameTestContext context, final BlockPos pos) {
        supply(context, 0, item("rowanwooddoor"));
        useBlock(context, new Vec3(pos.getX() + .5, pos.getY() - .001, pos.getZ() + .5), pos.below());
        await(context, player -> player.level().getBlockState(pos).getBlock() instanceof RunedDoorBlock
            && player.level().getBlockState(pos.above()).getBlock() instanceof RunedDoorBlock, 30, "Native placement creates both Rowan door halves");
        check(serverValue(player -> count(player, item("rowanwooddoor")) == 0 && doorClosed(player, pos)), "One Survival door item is consumed and starts closed");
    }

    private void useDoor(final ClientGameTestContext context, final BlockPos pos) {
        final Vec3 point = context.computeOnClient(client -> {
            final var bounds = client.level.getBlockState(pos).getShape(client.level, pos).bounds();
            return bounds.getCenter().add(pos.getX(), pos.getY(), pos.getZ());
        });
        useBlock(context, point, pos);
    }

    private void toggleDoor(final ClientGameTestContext context, final BlockPos half, final boolean open) {
        check(serverValue(player -> player.getMainHandItem().isEmpty()), "Door permission is tested with an empty active hand");
        useDoor(context, half);
        final BlockPos lower = half.getY() == 101 ? half.below() : half;
        await(context, player -> player.level().getBlockState(lower).getValue(DoorBlock.OPEN) == open
            && player.level().getBlockState(lower.above()).getValue(DoorBlock.OPEN) == open, 30,
            "Native use of either half synchronizes the whole door");
    }

    private void chestTransfer(final ClientGameTestContext context, final RowanKeyState.Door key, final boolean deposit) {
        moveFixturePlayer(context, new Vec3(8.5, 100, -2.5));
        select(context, 8);
        useBlock(context, Vec3.atCenterOf(KEY_CHEST), KEY_CHEST);
        context.waitFor(client -> client.gui.screen() != null && client.player.containerMenu != client.player.inventoryMenu);
        final int slot = context.computeOnClient(client -> java.util.stream.IntStream.range(0, client.player.containerMenu.slots.size())
            .filter(index -> {
                final var candidate = client.player.containerMenu.getSlot(index);
                return (candidate.container == client.player.getInventory()) == deposit && RowanKeyState.read(candidate.getItem()).opens(key);
            }).findFirst().orElseThrow());
        clickContainerSlot(context, slot);
        await(context, player -> RowanKeyState.read(player.containerMenu.getCarried()).opens(key), 30,
            "Native pickup places the exact bound key on the cursor");
        final int destination = context.computeOnClient(client -> java.util.stream.IntStream.range(0, client.player.containerMenu.slots.size())
            .filter(index -> {
                final var candidate = client.player.containerMenu.getSlot(index);
                return (candidate.container == client.player.getInventory()) != deposit && candidate.getItem().isEmpty()
                    && (deposit || candidate.getContainerSlot() < 9);
            }).findFirst().orElseThrow());
        clickContainerSlot(context, destination);
        await(context, player -> player.containerMenu.getCarried().isEmpty()
            && RowanKeyState.read(player.containerMenu.getSlot(destination).getItem()).opens(key)
            && carries(player, key) != deposit, 30, "Native destination click stores the real key and empties the cursor");
        close(context);
        await(context, player -> player.containerMenu == player.inventoryMenu && carries(player, key) != deposit, 30,
            "The acknowledged transfer persists after the chest closes");
    }

    private static void clickContainerSlot(final ClientGameTestContext context, final int slot) {
        final int[] point = context.computeOnClient(client -> {
            final var selected = client.player.containerMenu.getSlot(slot);
            return new int[] {(int) field(client.gui.screen(), "leftPos") + selected.x + 8,
                (int) field(client.gui.screen(), "topPos") + selected.y + 8};
        });
        ManualClientAcceptance.click(context, point[0], point[1]);
    }

    private void mergeKey(final ClientGameTestContext context, final RowanKeyState.Door source, final int expectedDoors) {
        check(serverValue(player -> player.getOffhandItem().isEmpty()), "The receiving trial starts with an empty off hand");
        select(context, keySlot(source));
        context.getInput().pressKey(GLFW.GLFW_KEY_F);
        await(context, player -> RowanKeyState.read(player.getOffhandItem()).opens(source), 30, "Native hand swap moves the generated key into the opposite hand");
        select(context, 0); useAir(context);
        await(context, player -> player.getOffhandItem().isEmpty() && player.getMainHandItem().is(item("ingredient_door_keyring"))
            && RowanKeyState.read(player.getMainHandItem()).doors().size() == expectedDoors
            && RowanKeyState.read(player.getMainHandItem()).opens(source), 30, "Native air use merges the bound key and consumes its source");
    }

    private int keySlot(final RowanKeyState.Door door) {
        return serverValue(player -> java.util.stream.IntStream.range(0, 9)
            .filter(slot -> player.getInventory().getItem(slot).is(item("ingredient_door_key"))
                && RowanKeyState.read(player.getInventory().getItem(slot)).opens(door)).findFirst().orElseThrow());
    }

    private static boolean doorClosed(final ServerPlayer player, final BlockPos lower) {
        return !player.level().getBlockState(lower).getValue(DoorBlock.OPEN) && !player.level().getBlockState(lower.above()).getValue(DoorBlock.OPEN);
    }
    private static RowanKeyState.Door door(final ServerPlayer player, final BlockPos pos) {
        return new RowanKeyState.Door(player.level().dimension().identifier(), pos);
    }
    private static boolean carries(final ServerPlayer player, final RowanKeyState.Door door) {
        return player.getInventory().contains(stack -> RowanKeyState.read(stack).opens(door));
    }

    private UUID spawn(final String id, final Vec3 pos) {
        return serverValue(player -> {
            final var entity = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)).create(player.level(), EntitySpawnReason.COMMAND);
            check(entity instanceof Mob, "The prerequisite registry ID creates a real mob: " + id);
            final Mob mob = (Mob) entity;
            mob.snapTo(pos.x, pos.y, pos.z); mob.setNoAi(true); mob.setPersistenceRequired();
            check(player.level().addFreshEntity(mob), "The staged mob joins the actual world");
            return mob.getUUID();
        });
    }
    private static Mob mob(final ServerPlayer player, final UUID id) { return (Mob) player.level().getEntity(id); }
    private void useEntity(final ClientGameTestContext context, final UUID id, final int button) {
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> java.util.stream.StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
            .anyMatch(entity -> entity.getUUID().equals(id)));
        look(context, serverValue(player -> mob(player, id).position().add(0, mob(player, id).getBbHeight() * .55, 0)));
        check(context.computeOnClient(client -> client.hitResult instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(id)),
            "Native pointer targets the exact staged creature");
        context.getInput().pressMouse(button); context.waitTicks(3);
    }

    private void readGuides(final ClientGameTestContext context, final List<String> sections, final Map<String, Object> row) throws Exception {
        final List<Map<String, Object>> guides = new ArrayList<>();
        for (final String section : sections) {
            final ManualProfile profile = ManualProfile.profiles().stream().filter(candidate -> candidate.sections().contains(section)).findFirst().orElseThrow();
            supply(context, 0, item(profile.id())); lookAir(context);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitForScreen(ManualScreen.class);
            selectGuide(context, section);
            final String text = context.computeOnClient(client -> ManualArticleCatalog.article(profile, section).body().getString());
            final int pages = context.computeOnClient(client -> {
                try {
                    final var method = ManualScreen.class.getDeclaredMethod("bodyPages", ManualLayout.class, String.class);
                    method.setAccessible(true);
                    return ((List<?>) method.invoke(client.gui.screen(), ManualLayout.calculate(client.gui.screen().width, client.gui.screen().height), section)).size();
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            check(!text.isBlank() && pages > 0, "The actual indexed book has readable instructions");
            for (int page = 0; page < pages; page++) {
                if (page > 0) ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.next").getString());
                final int expected = page;
                check(context.computeOnClient(client -> section.equals(field(client.gui.screen(), "selectedSection"))
                    && (int) field(client.gui.screen(), "bodyPage") == expected), "Native book input reaches every instruction page");
                screenshot(context, section + "-guide-" + page);
            }
            guides.add(Map.of("book", profile.id(), "section", section, "text", text, "pages_read", pages));
            close(context);
        }
        row.put("guides_read", guides);
    }

    private static void selectGuide(final ClientGameTestContext context, final String section) {
        final ManualProfile profile = context.computeOnClient(client -> (ManualProfile) field(client.gui.screen(), "manual"));
        final String title = context.computeOnClient(client -> Component.translatable(profile.translatedSectionTitleKey(section)).getString());
        ManualClientAcceptance.search(context, title);
        if (!context.computeOnClient(client -> (boolean) field(client.gui.screen(), "chapterIndex"))) {
            ManualClientAcceptance.clickButton(context, Component.translatable("screen.warlockery.manual.table_of_contents").getString());
        }
        ManualClientAcceptance.clickButton(context, Component.translatable(profile.chapterFor(section).titleKey()).getString());
        final int[] point = context.computeOnClient(client -> {
            @SuppressWarnings("unchecked")
            final List<String> filtered = (List<String>) field(client.gui.screen(), "filteredSections");
            final List<String> sections = profile.sectionsInChapter((String) field(client.gui.screen(), "selectedChapter"), filtered);
            final int visibleIndex = sections.indexOf(section) - (int) field(client.gui.screen(), "sectionOffset");
            final var entries = client.gui.screen().children().stream()
                .filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                .filter(widget -> widget.visible && widget.active && widget.getClass().getSimpleName().equals("ManualNavigationButton"))
                .toList();
            check(visibleIndex >= 0 && visibleIndex < entries.size(), "The exact guide section is visible: " + section);
            final var entry = entries.get(visibleIndex);
            check(entry.getMessage().getString().replace("▶ ", "").equals(title), "The indexed guide button matches its translated title");
            return new int[] {entry.getX() + entry.getWidth() / 2, entry.getY() + entry.getHeight() / 2};
        });
        ManualClientAcceptance.click(context, point[0], point[1]);
        context.waitFor(client -> section.equals(field(client.gui.screen(), "selectedSection"))
            && (int) field(client.gui.screen(), "bodyPage") == 0, 30);
    }

    private void moveFixturePlayer(final ClientGameTestContext context, final Vec3 position) {
        server(player -> { player.teleportTo(position.x, position.y, position.z); player.setDeltaMovement(Vec3.ZERO); });
        world.getConnection().waitForClientboundPackets(); context.waitTicks(3);
    }
    private void supply(final ClientGameTestContext context, final int slot, final Item item) {
        server(player -> { player.getInventory().setItem(slot, new ItemStack(item)); player.inventoryMenu.broadcastChanges(); });
        select(context, slot);
    }
    private void select(final ClientGameTestContext context, final int slot) {
        world.getConnection().waitForClientboundPackets(); context.getInput().pressKey(GLFW.GLFW_KEY_1 + slot); context.waitTicks(2);
    }
    private static void look(final ClientGameTestContext context, final Vec3 point) {
        context.runOnClient(client -> {
            final Vec3 delta = point.subtract(client.player.getEyePosition());
            client.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        }); context.waitTicks(2);
    }
    private static void lookAir(final ClientGameTestContext context) {
        context.runOnClient(client -> client.player.setXRot(-80)); context.waitTicks(2);
        check(context.computeOnClient(client -> client.hitResult == null || client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS),
            "Air-use input has no block or entity under the pointer");
    }
    private static void useAir(final ClientGameTestContext context) {
        lookAir(context); context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(3);
    }
    private static void useBlock(final ClientGameTestContext context, final Vec3 point, final BlockPos expected) {
        look(context, point);
        check(context.computeOnClient(client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(expected)),
            "Native pointer targets the intended block: " + expected);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT); context.waitTicks(3);
    }
    private void await(final ClientGameTestContext context, final Predicate<ServerPlayer> condition, final int ticks, final String message) {
        for (int remaining = ticks; remaining > 0 && !serverValue(condition::test); remaining -= 2) context.waitTicks(2);
        check(serverValue(condition::test), message);
    }
    private static void awaitOverlay(final ClientGameTestContext context, final String key, final Object... arguments) {
        final String expected = context.computeOnClient(client -> Component.translatable(key, arguments).getString());
        context.waitFor(client -> field(client.gui.hud, "overlayMessageString") instanceof Component message
            && message.getString().equals(expected), 40);
    }
    private static void close(final ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
    }
    private static Object field(final Object object, final String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) try {
            final var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
        } catch (NoSuchFieldException ignored) { } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        throw new AssertionError("Missing observed field " + name);
    }
    private static Item item(final String id) { return ModItems.ALL.get(id).get(); }
    private static int count(final ServerPlayer player, final Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) if (player.getInventory().getItem(slot).is(item)) total += player.getInventory().getItem(slot).getCount();
        return total;
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
    private void write(final boolean finished) throws Exception {
        if (evidence == null) return;
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("completed", finished);
        report.put("all_selected_scenarios_passed", finished && failures.isEmpty() && results.size() == 2
            && results.values().stream().allMatch(row -> "PASSED".equals(row.get("status"))));
        report.put("all_item_contracts_complete", false);
        report.put("execution", "Rendered Fabric development-classpath client; native item use, drinking, entity targeting, movement, combat, placement, book pages and container clicks. Prerequisites are staged; outcomes are observed from normal runtime.");
        report.put("class_sha256", Map.of("test", classHash(GraveAndDoorAbilitiesClientAcceptance.class),
            "infused_brew", classHash(InfusedBrewItem.class), "magic_runtime", classHash(MagicPathRuntime.class), "rowan_door", classHash(RunedDoorBlock.class)));
        report.put("scenarios", results); report.put("failures", failures); report.put("screenshots", screenshots);
        final Path temporary = evidence.resolve("grave-and-door-abilities.json.tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.move(temporary, evidence.resolve("grave-and-door-abilities.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
