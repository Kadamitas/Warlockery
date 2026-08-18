package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.item.BiomeNoteState;
import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class BiomeRitualGameTests {
    private BiomeRitualGameTests() {
    }

    public static void biomeBookCapturePersistsAndNames(final GameTestHelper helper) {
        final BlockPos sample = helper.absolutePos(new BlockPos(1, 1, 1));
        final Identifier sampledBiome = helper.getLevel().getBiome(sample).unwrapKey().orElseThrow().identifier();
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        final ItemStack book = new ItemStack(ModItems.ALL.get("ingredient_book_biomes").get());
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        player.setShiftKeyDown(true);
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(sample), Direction.UP, sample, false);

        helper.assertTrue(
            book.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
            "crouch-use must bind the Book of Biomes to the sampled block's biome"
        );
        player.setShiftKeyDown(false);
        helper.assertValueEqual(BiomeNoteState.read(book).orElseThrow(), sampledBiome, "captured biome");
        helper.assertValueEqual(BiomeNoteState.read(book.copy()).orElseThrow(), sampledBiome, "copied biome");
        helper.assertValueEqual(
            book.getItem().getName(book),
            Component.translatable("item.warlockery.biome_book.recorded", BiomeNoteState.displayName(sampledBiome)),
            "dynamic book name"
        );
        helper.succeed();
    }

    public static void climateShiftUsesBoundBookTarget(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final ItemStack staleBook = new ItemStack(ModItems.ALL.get("bookbiomes2").get());
        BiomeNoteState.write(staleBook, Identifier.fromNamespaceAndPath("missing_mod", "forgotten_biome"));
        final ItemEntity staleOffering = drop(helper, center, staleBook);
        helper.assertTrue(
            RitualManager.nearbyRecordedBiome(helper.getLevel(), center).isEmpty(),
            "a removed mod biome must not satisfy the ritual"
        );
        helper.assertFalse(
            RitualManager.actionEnvironmentRequirement(RitualAction.CLIMATE_SHIFT, 16, helper.getLevel(), center)
                .orElseThrow().met(),
            "a stale biome book must leave the climate rite unready"
        );
        staleOffering.discard();

        final ItemStack book = new ItemStack(ModItems.ALL.get("bookbiomes2").get());
        BiomeNoteState.write(book, Biomes.DESERT.identifier());
        drop(helper, center, book);
        final ChunkPos centerChunk = ChunkPos.containing(center);
        final int minX = centerChunk.getMinBlockX();
        final int maxX = centerChunk.getMaxBlockX();
        final int minZ = centerChunk.getMinBlockZ();
        final int maxZ = centerChunk.getMaxBlockZ();
        final int sampleY = center.getY();
        final List<BiomeSample> neighboringBiomes = List.of(
            new BlockPos(minX - 1, sampleY, minZ + 8),
            new BlockPos(maxX + 1, sampleY, minZ + 8),
            new BlockPos(minX + 8, sampleY, minZ - 1),
            new BlockPos(minX + 8, sampleY, maxZ + 1),
            new BlockPos(minX - 1, sampleY, minZ - 1),
            new BlockPos(minX - 1, sampleY, maxZ + 1),
            new BlockPos(maxX + 1, sampleY, minZ - 1),
            new BlockPos(maxX + 1, sampleY, maxZ + 1)
        ).stream().map(position -> new BiomeSample(
            position,
            storedBiome(helper.getLevel(), position)
        )).toList();

        helper.assertValueEqual(
            RitualManager.nearbyRecordedBiome(helper.getLevel(), center).orElseThrow(),
            Biomes.DESERT.identifier(),
            "ritual target biome"
        );
        RitualManager.INSTANCE.complete(
            helper.getLevel(),
            center,
            null,
            Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "climate_change"),
            0
        );

        for (int y = helper.getLevel().getMinY(); y < helper.getLevel().getMaxY(); y += 4) {
            for (int xOffset = 0; xOffset < 16; xOffset += 4) {
                for (int zOffset = 0; zOffset < 16; zOffset += 4) {
                    final BlockPos quartCell = new BlockPos(minX + xOffset, y, minZ + zOffset);
                    helper.assertValueEqual(
                        storedBiome(helper.getLevel(), quartCell),
                        Optional.of(Biomes.DESERT),
                        "selected chunk quart biome"
                    );
                }
            }
        }
        neighboringBiomes.forEach(sample -> helper.assertValueEqual(
            storedBiome(helper.getLevel(), sample.position()),
            sample.biome(),
            "an unempowered cast must not rewrite any adjacent chunk quart cell"
        ));
        helper.succeed();
    }

    public static void climateShiftEmpowermentAndStarsAreOptionalAndCapped(final GameTestHelper helper) {
        final BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        final ServerPlayer player = connectedSurvivalPlayer(helper);
        player.snapTo(Vec3.atCenterOf(center));
        for (int offset = 0; offset < 4; offset++) {
            final Mob mage = circleMage(helper, center.offset(offset - 2, 0, 2));
            CreatureBehaviorState.bind(mage, player.getUUID());
        }
        drop(helper, center, new ItemStack(ModItems.ALL.get("ingredient_seer_stone").get()));
        final ItemEntity stars = drop(helper, center, new ItemStack(Items.NETHER_STAR, 5));

        final BiomeShiftPlan plan = RitualManager.climateShiftPlan(helper.getLevel(), center, player);
        helper.assertTrue(plan.empowered(), "a Seer Stone and five participants must empower the cast");
        helper.assertValueEqual(plan.netherStars(), 3, "capped Nether Star count");
        helper.assertValueEqual(plan.chunkRadius(), 4, "empowered plus three-star radius");
        RitualManager.consumeClimateNetherStars(helper.getLevel(), center, plan.netherStars());
        helper.assertValueEqual(stars.getItem().getCount(), 2, "unused Nether Stars");
        helper.succeed();
    }

    private static ItemEntity drop(final GameTestHelper helper, final BlockPos position, final ItemStack stack) {
        final ItemEntity entity = new ItemEntity(
            helper.getLevel(), position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5, stack
        );
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static Mob circleMage(final GameTestHelper helper, final BlockPos position) {
        final Mob mage = (Mob) ModEntities.ALL.get("circle_mage").get()
            .create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        if (mage == null) {
            throw new IllegalStateException("Circle Mage entity creation failed");
        }
        mage.setPos(Vec3.atBottomCenterOf(position));
        mage.setPersistenceRequired();
        helper.getLevel().addFreshEntity(mage);
        return mage;
    }

    private static Optional<ResourceKey<Biome>> storedBiome(final ServerLevel level, final BlockPos position) {
        return level.getChunkAt(position).getNoiseBiome(
            QuartPos.fromBlock(position.getX()),
            QuartPos.fromBlock(position.getY()),
            QuartPos.fromBlock(position.getZ())
        ).unwrapKey();
    }

    private static ServerPlayer connectedSurvivalPlayer(final GameTestHelper helper) {
        final ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        final CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private record BiomeSample(BlockPos position, Optional<ResourceKey<Biome>> biome) {
    }
}
