package com.kadamitas.warlockery.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.util.ItemIngredient;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public final class CraftingCatalogAcceptance implements FabricClientGameTest {
    private final List<Map<String, Object>> results = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();
    private Path evidence;

    @Override public void runTest(ClientGameTestContext context) {
        evidence = Path.of(System.getProperty("warlockery.clientEvidence")).resolve("crafting").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(evidence);
            context.getInput().resizeWindow(1280, 800);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
            try (var world = context.worldBuilder().create()) {
                world.getConnection().waitForChunksRender();
                var recipes = world.getServer().computeOnServer(server -> server.getRecipeManager().getRecipes().stream()
                    .filter(holder -> (holder.id().identifier().getNamespace().equals("warlockery")
                        || holder.id().identifier().toString().equals("minecraft:cauldron")) && !holder.value().isSpecial())
                    .map(holder -> holder.id().identifier().toString()).sorted().toList());
                for (String id : recipes) {
                    String selectedIds = System.getProperty("warlockery.craftingIds", "");
                    if (!selectedIds.isBlank() && java.util.Arrays.stream(selectedIds.split(",")).noneMatch(value ->
                        value.equals(id) || value.equals(Identifier.parse(id).getPath())
                            || value.equals("kettle") && id.equals("minecraft:cauldron"))) continue;
                    JsonObject recipe;
                    try (var input = CraftingCatalogAcceptance.class.getResourceAsStream("/data/" + Identifier.parse(id).getNamespace() + "/recipe/" + Identifier.parse(id).getPath() + ".json")) {
                        if (input == null) throw new AssertionError("Missing packaged recipe " + id);
                        recipe = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                    }
                    String type = recipe.get("type").getAsString();
                    boolean cooking = type.equals("minecraft:smelting") || type.equals("minecraft:blasting");
                    if (!cooking && !type.equals("minecraft:crafting_shaped") && !type.equals("minecraft:crafting_shapeless"))
                        throw new AssertionError("Missing native recipe scenario for " + type + ": " + id);
                    String scope = System.getProperty("warlockery.craftingKinds", "all");
                    if (scope.equals("smelting") && !cooking || scope.equals("crafting") && cooking) continue;
                    var row = new LinkedHashMap<String, Object>();
                    row.put("id", id); row.put("status", "NOT_RUN"); results.add(row);
                }
                write(false);
                for (var row : results) {
                    String id = (String) row.get("id");
                    row.put("status", "RUNNING"); write(false);
                    try {
                        JsonObject recipe;
                        try (var input = CraftingCatalogAcceptance.class.getResourceAsStream("/data/" + Identifier.parse(id).getNamespace() + "/recipe/" + Identifier.parse(id).getPath() + ".json")) {
                            recipe = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                        }
                        String[] grid = new String[9];
                        String recipeType = recipe.get("type").getAsString();
                        boolean cooking = recipeType.equals("minecraft:smelting") || recipeType.equals("minecraft:blasting");
                        if (recipe.get("type").getAsString().equals("minecraft:crafting_shaped")) {
                            var pattern = recipe.getAsJsonArray("pattern");
                            for (int y = 0; y < pattern.size(); y++) {
                                String line = pattern.get(y).getAsString();
                                for (int x = 0; x < line.length(); x++) if (line.charAt(x) != ' ')
                                    grid[y * 3 + x] = recipe.getAsJsonObject("key").get(String.valueOf(line.charAt(x))).getAsString();
                            }
                        } else if (cooking) {
                            grid[0] = recipe.get("ingredient").getAsString();
                        } else {
                            var ingredients = recipe.getAsJsonArray("ingredients");
                            for (int i = 0; i < ingredients.size(); i++) grid[i] = ingredients.get(i).getAsString();
                        }
                        List<ItemStack> inputs = world.getServer().computeOnServer(server -> {
                            var stacks = new ArrayList<ItemStack>();
                            for (String value : grid) stacks.add(value == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.stream()
                                .map(ItemStack::new).filter(ItemIngredient.parse(value).orElseThrow()::matches).findFirst().orElseThrow());
                            return stacks;
                        });
                        world.getServer().runOnServer(server -> {
                            var player = world.getConnection().getServerPlayer();
                            player.closeContainer();
                            player.setGameMode(GameType.SURVIVAL);
                            player.getInventory().clearContent();
                            player.teleportTo(0.5, 80, -2.5);
                            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                                player.level().setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
                                for (int y = 80; y < 84; y++) player.level().setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                            }
                            player.level().setBlockAndUpdate(new BlockPos(0, 80, 0), (recipeType.equals("minecraft:blasting")
                                ? Blocks.BLAST_FURNACE : cooking ? Blocks.FURNACE : Blocks.CRAFTING_TABLE).defaultBlockState());
                            for (int slot = 0; slot < inputs.size(); slot++) player.getInventory().setItem(9 + slot, inputs.get(slot).copy());
                            if (cooking) player.getInventory().setItem(10, new ItemStack(net.minecraft.world.item.Items.COAL));
                            player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges();
                        });
                        world.getConnection().waitForClientboundPackets();
                        world.getConnection().waitForChunksRender();
                        context.getInput().lookAt(new BlockPos(0, 80, 0));
                        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
                        if (cooking) context.waitFor(client -> client.player.containerMenu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu);
                        else context.waitForScreen(CraftingScreen.class);
                        for (int i = 0; i < inputs.size(); i++) if (!inputs.get(i).isEmpty()) {
                            int inventorySlot = 9 + i;
                            int menuSlot = context.computeOnClient(client -> java.util.stream.IntStream.range(0, client.player.containerMenu.slots.size())
                                .filter(slot -> client.player.containerMenu.getSlot(slot).container == client.player.getInventory()
                                    && client.player.containerMenu.getSlot(slot).getContainerSlot() == inventorySlot).findFirst().orElseThrow());
                            clickSlot(context, menuSlot); clickSlot(context, cooking ? 0 : i + 1);
                        }
                        if (cooking) {
                            int fuelSlot = context.computeOnClient(client -> java.util.stream.IntStream.range(0, client.player.containerMenu.slots.size())
                                .filter(slot -> client.player.containerMenu.getSlot(slot).container == client.player.getInventory()
                                    && client.player.containerMenu.getSlot(slot).getContainerSlot() == 10).findFirst().orElseThrow());
                            clickSlot(context, fuelSlot); clickSlot(context, 1);
                        }
                        var output = recipe.getAsJsonObject("result");
                        var expectedItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(output.get("id").getAsString()));
                        int expectedCount = output.has("count") ? output.get("count").getAsInt() : 1;
                        int resultSlot = cooking ? 2 : 0;
                        context.waitFor(client -> client.player.containerMenu.getSlot(resultSlot).getItem().is(expectedItem)
                            && client.player.containerMenu.getSlot(resultSlot).getItem().getCount() == expectedCount, cooking ? 1000 : 60);
                        ManualClientAcceptance.saveScreenshot(context, evidence, Identifier.parse(id).getPath() + "-result", screenshots);
                        clickSlot(context, resultSlot);
                        ManualClientAcceptance.check(world.getServer().computeOnServer(server -> {
                            var menu = world.getConnection().getServerPlayer().containerMenu;
                            return menu.getCarried().is(expectedItem) && menu.getCarried().getCount() == expectedCount
                                && (cooking ? menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty()
                                    : java.util.stream.IntStream.range(0, 9).allMatch(slot -> inputs.get(slot).isEmpty()
                                        || !menu.getSlot(slot + 1).getItem().is(inputs.get(slot).getItem())));
                        }), "Native result click must produce output and consume each input for " + id);
                        int targetSlot = context.computeOnClient(client -> java.util.stream.IntStream.range(0, client.player.containerMenu.slots.size())
                            .filter(slot -> client.player.containerMenu.getSlot(slot).container == client.player.getInventory()
                                && client.player.containerMenu.getSlot(slot).getContainerSlot() == 0).findFirst().orElseThrow());
                        clickSlot(context, targetSlot);
                        world.getServer().waitFor(server -> world.getConnection().getServerPlayer().getInventory().getItem(0).is(expectedItem));
                        row.put("output", output.toString()); row.put("status", "CRAFTED_AND_COLLECTED");
                    } catch (Throwable failure) {
                        row.put("status", "FAIL"); row.put("failure", failure.toString());
                        ManualClientAcceptance.saveScreenshot(context, evidence, Identifier.parse(id).getPath() + "-failure", screenshots);
                    }
                    if (context.computeOnClient(client -> client.gui.screen() != null)) {
                        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE); context.waitFor(client -> client.gui.screen() == null);
                    }
                    write(false);
                }
            }
            write(true);
            ManualClientAcceptance.check(!results.isEmpty() && results.stream().allMatch(row -> row.get("status").equals("CRAFTED_AND_COLLECTED")),
                "Every crafting recipe must succeed; see " + evidence);
        } catch (Throwable failure) { throw new AssertionError("Crafting acceptance: " + evidence, failure); }
    }

    private static void clickSlot(ClientGameTestContext context, int index) {
        int[] point = context.computeOnClient(client -> {
            var slot = client.player.containerMenu.getSlot(index);
            return new int[] {(int) field(client.gui.screen(), "leftPos") + slot.x + 8, (int) field(client.gui.screen(), "topPos") + slot.y + 8};
        });
        ManualClientAcceptance.click(context, point[0], point[1]);
    }

    private void write(boolean completed) throws Exception {
        Files.writeString(evidence.resolve("coverage.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
            "completed", completed, "recipes", results, "screenshots", screenshots,
            "fixture", "Fresh world, staged table/furnace and declared input alternatives. Actual workstation use, native slot placement, result click and inventory collection. Furnace recipes also consume coal through normal ticking. Special recipes are outside this suite.",
            "selected_scope", System.getProperty("warlockery.craftingKinds", "all"))));
    }

    private static Object field(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        throw new AssertionError("Missing field " + name);
    }
}
