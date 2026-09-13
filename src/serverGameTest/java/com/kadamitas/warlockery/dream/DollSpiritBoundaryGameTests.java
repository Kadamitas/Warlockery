package com.kadamitas.warlockery.dream;

import com.kadamitas.warlockery.block.entity.DollShelfBlockEntity;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.item.DollKind;
import com.kadamitas.warlockery.item.SympatheticBinding;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.util.GameTestCleanup;
import com.kadamitas.warlockery.util.GameTestMockPlayers;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Real players, recipes, item binding and doll runtime entry points; no FakeServerPlayer or injected doll result. */
public final class DollSpiritBoundaryGameTests {
    private DollSpiritBoundaryGameTests() { }

    public static void craftedDollsKeepTheirRealmAcrossShelvesSavingAndWaking(final GameTestHelper helper) {
        final ServerPlayer player = player(helper, 2);
        final ItemStack physical = craft(helper, player, DollKind.HEX_GUARD, Items.AMETHYST_SHARD, Items.MILK_BUCKET);
        bindSelf(helper, player, physical);
        player.getInventory().clearContent();
        final BlockPos shelfPos = new BlockPos(6, 2, 2);
        helper.setBlock(shelfPos.below(), Blocks.STONE);
        helper.setBlock(shelfPos, ModBlocks.ALL.get("doll_shelf").get());
        final DollShelfBlockEntity shelf = helper.getBlockEntity(shelfPos, DollShelfBlockEntity.class);
        shelf.setItem(0, physical);
        helper.assertTrue(DollItem.tryBlockHex(player), "Physical shelf guard works before dream entry");
        final int physicalWear = physical.getDamageValue();
        enter(helper, player);
        helper.assertTrue(!DollItem.tryBlockHex(player) && physical.getDamageValue() == physicalWear,
            "The same loaded physical shelf cannot intercept or spend a charge for the spirit self");
        player.getInventory().setItem(0, physical.copy());
        helper.assertTrue(!DollItem.tryBlockHex(player), "Transporting a physical doll into the dream does not change its origin");
        player.getInventory().clearContent();
        final ItemStack spirit = craft(helper, player, DollKind.HEX_GUARD, Items.AMETHYST_SHARD, Items.MILK_BUCKET);
        bindSelf(helper, player, spirit);
        helper.assertTrue(DollItem.tryBlockHex(player) && spirit.getDamageValue() == 1,
            "A doll actually crafted and self-bound inside intercepts a hex and spends its own charge");
        final var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        final var encoded = ItemStack.CODEC.encodeStart(ops, spirit).getOrThrow();
        final ItemStack restored = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        shelf.clearContent();
        helper.assertTrue(SpiritWorldRuntime.wake(player, SpiritWorldRules.WakeCause.RETURN_PORTAL), "Dreamer returns normally");
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, restored);
        helper.assertTrue(SympatheticBinding.read(restored).orElseThrow().targets(player), "Identity binding survives serialization and waking");
        helper.assertTrue(!DollItem.tryBlockHex(player) && restored.getDamageValue() == 1,
            "The saved spirit-made doll cannot protect the same player outside");
        helper.assertTrue(restored.getItem().use(player.level(), player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL,
            "Rebinding outside cannot rewrite a spirit doll's origin");
        helper.succeed();
    }

    public static void remoteDollHexesAndDamageLinksCannotCrossTheDream(final GameTestHelper helper) {
        final ServerPlayer attacker = player(helper, 2);
        final ServerPlayer target = player(helper, 4);
        final ItemStack physicalHex = craft(helper, attacker, DollKind.HEXING, Items.FERMENTED_SPIDER_EYE, Items.BONE);
        bindOther(helper, attacker, target, physicalHex);
        final ItemStack physicalLink = craft(helper, attacker, DollKind.BLOOD_LINK, Items.REDSTONE,
            ModItems.ALL.get("ingredient_drop_of_luck").get());
        bindOther(helper, attacker, target, physicalLink);
        enter(helper, target);
        attacker.getInventory().clearContent();
        attacker.setItemInHand(InteractionHand.MAIN_HAND, physicalHex);
        final float targetHealth = target.getHealth();
        helper.assertTrue(physicalHex.getItem().use(attacker.level(), attacker, InteractionHand.MAIN_HAND) == InteractionResult.FAIL,
            "An outside hexing doll refuses the online bound spirit rather than resolving by UUID alone");
        helper.assertTrue(target.getHealth() == targetHealth && physicalHex.getDamageValue() == 0,
            "Rejected remote hex neither harms the spirit nor wears the outside doll");
        attacker.getInventory().clearContent();
        attacker.setItemInHand(InteractionHand.MAIN_HAND, physicalLink);
        final float attackerHealth = attacker.getHealth();
        attacker.hurtServer(attacker.level(), attacker.damageSources().magic(), 4.0F);
        helper.assertTrue(attacker.getHealth() < attackerHealth && target.getHealth() == targetHealth
            && physicalLink.getDamageValue() == 0, "Outside damage is not transferred to the spirit through a Blood Link");

        enter(helper, attacker);
        final ItemStack spiritHex = craft(helper, attacker, DollKind.HEXING, Items.FERMENTED_SPIDER_EYE, Items.BONE);
        bindOther(helper, attacker, target, spiritHex);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, spiritHex);
        helper.assertTrue(spiritHex.getItem().use(attacker.level(), attacker, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS,
            "A spirit-made hexing doll can act on another target inside");
        helper.assertTrue(target.getHealth() < targetHealth && spiritHex.getDamageValue() == 1,
            "The same-side spirit hex causes real damage and charge consumption");
        helper.assertTrue(SpiritWorldRuntime.wake(attacker, SpiritWorldRules.WakeCause.RETURN_PORTAL), "Hex caster returns normally");
        attacker.getInventory().clearContent();
        attacker.setItemInHand(InteractionHand.MAIN_HAND, spiritHex);
        final float afterHex = target.getHealth();
        helper.assertTrue(spiritHex.getItem().use(attacker.level(), attacker, InteractionHand.MAIN_HAND) == InteractionResult.FAIL
            && target.getHealth() == afterHex && spiritHex.getDamageValue() == 1,
            "Moving the same spirit doll outside does not let its outside holder reach into the dream");
        SpiritWorldRuntime.wake(target, SpiritWorldRules.WakeCause.RETURN_PORTAL);
        helper.succeed();
    }

    public static void lethalProtectionAndMendingStayOnTheirOwnSide(final GameTestHelper helper) {
        final ServerPlayer first = player(helper, 2);
        final ItemStack physicalDeath = craft(helper, first, DollKind.DEATH_GUARD, Items.GHAST_TEAR, Items.GOLDEN_APPLE);
        bindSelf(helper, first, physicalDeath);
        enter(helper, first);
        first.setItemInHand(InteractionHand.MAIN_HAND, physicalDeath);
        first.setHealth(1.0F);
        first.hurtServer(first.level(), first.damageSources().magic(), 40.0F);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(physicalDeath.getDamageValue() == 0 && !SpiritWorldState.active(first),
                "A transported physical Death Guard cannot prevent the spirit's normal fatal-damage wake");

            final ServerPlayer second = player(helper, 4);
            final ItemStack physicalTool = craft(helper, second, DollKind.TOOL_MENDING, Items.IRON_INGOT, Items.LAPIS_LAZULI);
            bindSelf(helper, second, physicalTool);
            final ItemStack physicalArmor = craft(helper, second, DollKind.ARMOR_MENDING, Items.IRON_INGOT, Items.LEATHER);
            bindSelf(helper, second, physicalArmor);
            enter(helper, second);
            final ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
            tool.setDamageValue(40);
            final ItemStack armor = new ItemStack(Items.IRON_HELMET);
            armor.setDamageValue(40);
            second.setItemInHand(InteractionHand.MAIN_HAND, tool);
            second.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, armor);
            helper.assertTrue(!DollItem.tryMendBoundEquipment(physicalTool, second.level(), second)
                && !DollItem.tryMendBoundEquipment(physicalArmor, second.level(), second)
                && tool.getDamageValue() == 40 && armor.getDamageValue() == 40,
                "Physical mending dolls cannot repair the spirit's held or worn equipment");
            final ItemStack spiritTool = craft(helper, second, DollKind.TOOL_MENDING, Items.IRON_INGOT, Items.LAPIS_LAZULI);
            bindSelf(helper, second, spiritTool);
            final ItemStack spiritArmor = craft(helper, second, DollKind.ARMOR_MENDING, Items.IRON_INGOT, Items.LEATHER);
            bindSelf(helper, second, spiritArmor);
            second.setItemInHand(InteractionHand.MAIN_HAND, tool);
            helper.assertTrue(DollItem.tryMendBoundEquipment(spiritTool, second.level(), second)
                && DollItem.tryMendBoundEquipment(spiritArmor, second.level(), second)
                && tool.getDamageValue() < 40 && armor.getDamageValue() < 40
                && spiritTool.getDamageValue() == 1 && spiritArmor.getDamageValue() == 1,
                "Spirit-made mending dolls repair real equipment and spend their own charges inside");
            final ItemStack spiritDeath = craft(helper, second, DollKind.DEATH_GUARD, Items.GHAST_TEAR, Items.GOLDEN_APPLE);
            bindSelf(helper, second, spiritDeath);
            second.setHealth(1.0F);
            second.hurtServer(second.level(), second.damageSources().magic(), 40.0F);
            helper.assertTrue(second.isAlive() && SpiritWorldState.active(second) && spiritDeath.getDamageValue() == 1,
                "A spirit-made Death Guard really intercepts lethal damage without waking its owner");
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(SpiritWorldState.active(second), "A protected spirit is not queued for a delayed fatal wake");
                SpiritWorldRuntime.wake(second, SpiritWorldRules.WakeCause.RETURN_PORTAL);
                helper.succeed();
            });
        });
    }

    private static ItemStack craft(final GameTestHelper helper, final ServerPlayer player, final DollKind kind,
        final Item second, final Item third) {
        final CraftingInput input = CraftingInput.of(3, 1, List.of(
            new ItemStack(ModItems.ALL.get("doll").get()), new ItemStack(second), new ItemStack(third)));
        final var recipe = player.level().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, player.level()).orElseThrow();
        final ItemStack result = recipe.value().assemble(input);
        helper.assertTrue(result.getItem() instanceof DollItem doll && doll.kind() == kind, "Actual ingredients craft the required doll");
        result.onCraftedBy(player, 1);
        return result;
    }

    private static void bindSelf(final GameTestHelper helper, final ServerPlayer player, final ItemStack doll) {
        player.setItemInHand(InteractionHand.MAIN_HAND, doll);
        helper.assertTrue(doll.getItem().use(player.level(), player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS
            && DollItem.isBoundTo(doll, player), "Ordinary self-use binds the crafted doll");
    }

    private static void bindOther(final GameTestHelper helper, final ServerPlayer source, final ServerPlayer target, final ItemStack doll) {
        helper.assertTrue(doll.getItem().interactLivingEntity(doll, source, target, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS
            && DollItem.isBoundTo(doll, target), "Ordinary entity use binds the doll's target");
    }

    private static void enter(final GameTestHelper helper, final ServerPlayer player) {
        helper.assertTrue(SpiritWorldRuntime.enter(player, false).entered()
            && SpiritWorldRuntime.isSpiritWorld(player.level(), player), "Actual dream entry succeeds");
    }

    private static ServerPlayer player(final GameTestHelper helper, final int x) {
        final var server = helper.getLevel().getServer();
        final UUID id = UUID.randomUUID();
        final ServerPlayer player = new ServerPlayer(server, helper.getLevel(),
            new GameProfile(id, "doll_" + id.toString().substring(0, 8)), ClientInformation.createDefault());
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final EmbeddedChannel channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        server.getConnection().getConnections().add(connection);
        GameTestCleanup.add(helper, passed -> {
            server.getConnection().getConnections().remove(connection);
            channel.finishAndReleaseAll();
        });
        GameTestMockPlayers.autoDisconnect(helper, player);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL);
        final BlockPos pos = helper.absolutePos(new BlockPos(x, 2, 2));
        helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
        player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        SpiritWorldRuntime.useGameTestDestination(player);
        return player;
    }
}
